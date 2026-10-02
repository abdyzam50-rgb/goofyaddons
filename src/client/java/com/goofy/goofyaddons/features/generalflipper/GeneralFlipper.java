package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ordinary-item buy orders and sell offers. All state and clicks run on the client thread. */
public class GeneralFlipper implements Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralFlipper.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OWNER = "general";
    enum Stage { PLANNED, BUY_ORDER, INVENTORY, SELL_ORDER, RECONCILE }
    enum Step { OPEN_ORDERS, ORDERS, CANCEL_DETAIL, VERIFY_CANCEL, OPEN_PRODUCT,
        PRODUCT, QUANTITY, SIGN, PRICE, CONFIRM, VERIFY_ORDER, VERIFY_SALE }

    static class Position {
        GeneralItem item;
        int quantity;
        double unitCost;
        double sellPrice;
        Stage stage;
        boolean submitted;
        boolean cancelRequested;
        int reprices;
        long placedAt;
        long heldSince;
        long checkedAt;
        String tradeId;
        String saleEvent;
        boolean purchasePriceKnown;
        double cost() { return quantity * unitCost; }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final CapitalManager capital = CapitalManager.INSTANCE;
    private final List<Position> positions = new ArrayList<>();
    private final java.util.Map<String, Long> cooldownUntil = new java.util.HashMap<>();
    private boolean loaded;
    private boolean running;
    private boolean paused;
    private boolean blocked;
    private CompletableFuture<JsonObject> request;
    private int generation;
    private JsonObject products;
    private long quotesAt;
    private long nextPoll;
    private Position active;
    private Step step;
    private boolean selling;
    private int inventoryBefore;
    private int expectedClaim;
    private double purseBefore;
    private boolean receipt;
    private boolean claimPending;
    private int claimUnits;
    private int cancelSoldUnits;
    private Double claimedProceeds;
    private int ordersContainer = -1;
    private long ordersSeenAt;
    private long stepSince;
    private long nextAction;
    private long lastCommand;

    public GeneralFlipper() {
        ChatHook.onMessage("[Bazaar]", this::onNotice);
    }

    @Override public String name() { return "GeneralFlipper"; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean canYield() { return active == null; }
    @Override public void yieldMenu() {
        if (active == null && minecraft.player != null && minecraft.screen != null) minecraft.player.closeContainer();
    }

    public boolean hasStateError() { return blocked; }
    public String taskItem() {
        return active == null ? "No item selected" : active.item.name();
    }
    public String activity() {
        if (active != null) return "General: " + step.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (!freshQuotes()) return "Waiting for price data";
        if (!positions.isEmpty()) return "Waiting for general orders";
        if (capital.available(new ScoreboardUtils().getPurse()) <= 0) return "Capital limit / purse reserve reached";
        return candidates().isEmpty() ? "No flips meet the configured filters" : "Selecting eligible flips";
    }

    public void restoreBudget() {
        if (!loaded) load();
        for (Position position : positions) capital.restore(OWNER, position.item.id(), position.cost(),
                position.stage == Stage.PLANNED);
    }

    @Override public void start() {
        restoreBudget();
        if (blocked) { ChatUtils.clientMessage("General flipper is blocked; resolve the logged order-state error first."); return; }
        running = true;
        paused = false;
        nextPoll = 0;
        for (Position position : positions) {
            position.checkedAt = 0;
            capital.restore(OWNER, position.item.id(), position.cost(), position.stage == Stage.PLANNED);
        }
    }

    @Override public void stop() {
        running = false;
        paused = false;
        invalidateRequest();
        // Submitted orders and acquired inventory remain owned across stops.
        positions.removeIf(position -> {
            if (position.stage == Stage.PLANNED && !position.submitted) {
                capital.release(OWNER, position.item.id());
                return true;
            }
            position.checkedAt = 0;
            return false;
        });
        active = null;
        save();
    }

    @Override public void pause() {
        paused = true;
        invalidateRequest();
        active = null;
        save();
    }
    @Override public void resume() { if (running) start(); }

    private void invalidateRequest() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
    }

    @Override public void poll() {
        if (!running || paused || blocked || request != null || System.currentTimeMillis() < nextPoll) return;
        nextPoll = System.currentTimeMillis() + settings().refreshSeconds * 1000L;
        int run = generation;
        try {
            request = BazaarApi.fetch();
            request.whenComplete((root, error) -> minecraft.execute(() -> {
                if (generation != run || !running || paused) return;
                try {
                    if (error != null) throw new IllegalStateException("Bazaar request failed", error);
                    long updated = TradingSafety.sourceTime(root, System.currentTimeMillis());
                    products = root.getAsJsonObject("products");
                    quotesAt = updated;
                } catch (Exception failure) { LOGGER.warn("General quotes unavailable; retrying", failure); }
                finally { request = null; }
            }));
        } catch (Exception failure) {
            request = null;
            LOGGER.warn("General quotes unavailable; retrying", failure);
        }
    }

    @Override public boolean needsMenu() {
        if (!running || paused || blocked) return false;
        if (active != null) return true;
        long now = System.currentTimeMillis();
        if (positions.stream().anyMatch(position -> position.stage == Stage.RECONCILE || now - position.checkedAt >= settings().refreshSeconds * 1000L)) return true;
        return freshQuotes() && positions.size() < settings().maxActiveItems && !capital.purchaseSettling()
                && !candidates().isEmpty();
    }

    @Override public void onTick() {
        if (!running || paused || blocked || minecraft.player == null || minecraft.level == null) return;
        if (active == null) selectWork();
        if (active == null) return;
        long now = System.currentTimeMillis();
        if (now - stepSince > 30000) { fail("Menu/transaction timed out; retained the tracked position for recovery."); return; }
        if (now < nextAction) return;
        nextAction = now + GoofyConfig.INSTANCE.minActionDelay;
        try {
            switch (step) {
                case OPEN_ORDERS -> {
                    command("managebazaarorders");
                    if (menu("Bazaar")) transition(Step.ORDERS);
                }
                case ORDERS -> inspectOrders();
                case CANCEL_DETAIL -> cancelDetail();
                case VERIFY_CANCEL -> verifyCancellation();
                case OPEN_PRODUCT -> {
                    command("bz " + active.item.name());
                    if (menu("Bazaar") || menu(active.item.name())) transition(Step.PRODUCT);
                }
                case PRODUCT -> openProduct();
                case QUANTITY -> {
                    if (minecraft.screen instanceof AbstractSignEditScreen) transition(Step.SIGN);
                    else if (menu("How many")) {
                        int custom = find("Custom Amount", false);
                        if (custom < 0) custom = find("Custom", false);
                        if (custom >= 0) { click(custom); transition(Step.SIGN); }
                    } else if (priceMenu()) transition(Step.PRICE);
                }
                case SIGN -> {
                    if (minecraft.screen instanceof AbstractSignEditScreen sign) {
                        Field messages = AbstractSignEditScreen.class.getDeclaredField("messages");
                        messages.setAccessible(true);
                        ((String[]) messages.get(sign))[0] = Integer.toString(active.quantity);
                        minecraft.setScreen(null);
                        transition(Step.PRICE);
                    } else if (priceMenu()) transition(Step.PRICE);
                }
                case PRICE -> choosePrice();
                case CONFIRM -> {
                    if (!menu("Confirm")) return;
                    if (!freshQuotes()) { fail("Quotes expired before order confirmation."); return; }
                    if (!selling && active.quantity > capacityFor(active.item.id())) {
                        fail("Not enough inventory capacity for this buy order."); return;
                    }
                    int confirm = 13;
                    if (!loadedSlot(confirm)) return;
                    active.submitted = true;
                    if (selling) active.saleEvent = java.util.UUID.randomUUID().toString();
                    active.placedAt = now;
                    if (!save()) return; // Persist intent before the irreversible click.
                    click(confirm);
                    transition(Step.VERIFY_ORDER);
                    minecraft.player.closeContainer();
                }
                case VERIFY_ORDER -> {
                    command("managebazaarorders");
                    if (!ordersReady()) return;
                    if (ambiguousOrders()) return;
                    int order = findOrder(selling);
                    if (order >= 0) {
                        if (!orderMatchesPosition(order)) return;
                        active.stage = selling ? Stage.SELL_ORDER : Stage.BUY_ORDER;
                        if (!selling) capital.purchased(OWNER, active.item.id());
                        finishWork();
                    } else if (!selling && itemCount(active.item.id()) >= active.quantity) {
                        capital.purchased(OWNER, active.item.id());
                        active.stage = Stage.INVENTORY;
                        recordAcquisition();
                        finishWork();
                    }
                }
                case VERIFY_SALE -> {
                    command("managebazaarorders");
                    if (!ordersReady() || ambiguousOrders()) return;
                    if (TradingSafety.saleComplete(claimPending, receipt, findOrder(true) < 0, itemCount(active.item.id()))) {
                        recordSale(claimUnits, claimedProceeds);
                        completePosition();
                    }
                }
            }
        } catch (Exception failure) {
            LOGGER.error("General transaction failed", failure);
            fail("General transaction failed; position retained. Check logs before restarting.");
        }
    }

    private void selectWork() {
        long now = System.currentTimeMillis();
        for (Position position : positions) {
            if (position.stage == Stage.RECONCILE || now - position.checkedAt >= settings().refreshSeconds * 1000L) {
                active = position;
                selling = position.stage == Stage.INVENTORY || position.stage == Stage.SELL_ORDER;
                receipt = false;
                claimPending = false;
                cancelSoldUnits = 0;
                claimedProceeds = null;
                transition(Step.OPEN_ORDERS);
                return;
            }
        }
        if (!freshQuotes() || positions.size() >= settings().maxActiveItems || capital.purchaseSettling()) return;
        double purse = new ScoreboardUtils().getPurse();
        for (GeneralCalculator.Candidate candidate : candidates()) {
            if (itemCount(candidate.item().id()) > 0 || capital.occupied(candidate.item().id())
                    || System.currentTimeMillis() < cooldownUntil.getOrDefault(candidate.item().id(), 0L)) continue;
            if (!capital.reserve(OWNER, candidate.item().id(), candidate.cost(), purse)) continue;
            Position position = new Position();
            position.item = candidate.item();
            position.tradeId = java.util.UUID.randomUUID().toString();
            position.quantity = candidate.quantity();
            position.unitCost = candidate.bid();
            position.sellPrice = candidate.ask();
            position.stage = Stage.PLANNED;
            positions.add(position);
            active = position;
            selling = false;
            receipt = false;
            if (save()) transition(Step.OPEN_ORDERS);
            return;
        }
    }

    private void inspectOrders() {
        if (!ordersReady() || ambiguousOrders()) return;
        int buy = findOrder(false);
        int sell = findOrder(true);
        int inventory = itemCount(active.item.id());
        if (active.stage != Stage.PLANNED) {
            long held = active.heldSince > 0 ? active.heldSince : active.placedAt;
            if (TradingSafety.holdingLimit(held, System.currentTimeMillis(), settings().maxHoldingSeconds,
                    active.unitCost, freshQuotes() ? currentAsk() * (1 - GoofyConfig.INSTANCE.bazaarTaxPercentage / 100) : -1,
                    settings().maxDrawdownPercentage)) {
                fail("Holding age/drawdown limit reached for " + active.item.name() + ". Position retained for review."); return;
            }
        }
        if (active.stage == Stage.PLANNED && !active.submitted) {
            if (buy >= 0 || sell >= 0 || inventory > 0) {
                capital.release(OWNER, active.item.id());
                positions.remove(active);
                fail("Pre-existing order/inventory for " + active.item.name() + "; remove it or remove this item from the allowlist.");
                return;
            }
            selling = false;
            minecraft.player.closeContainer();
            transition(Step.OPEN_PRODUCT);
            return;
        }
        if (buy >= 0 && sell >= 0) { fail("Both buy and sell orders exist for one tracked item; manual reconciliation required."); return; }
        if (buy >= 0) {
            if (!orderMatchesPosition(buy)) return;
            active.stage = Stage.BUY_ORDER;
            if (active.heldSince == 0) active.heldSince = active.placedAt;
            capital.purchased(OWNER, active.item.id());
            int claimable = OrderLore.claimable(lore(buy), inventory > 0);
            boolean cancel = claimable > 0 || inventory > 0 || shouldReprice(false)
                    || System.currentTimeMillis() - active.placedAt >= settings().orderTimeoutSeconds * 1000L;
            if (!cancel) { finishWork(); return; }
            selling = false;
            inventoryBefore = inventory;
            expectedClaim = claimable;
            if (claimable > capacityFor(active.item.id())) {
                fail("Insufficient inventory space to claim the tracked buy order."); return;
            }
            purseBefore = new ScoreboardUtils().getPurse();
            receipt = false;
            click(buy);
            transition(Step.CANCEL_DETAIL);
            return;
        }
        if (sell >= 0) {
            if (!orderMatchesPosition(sell)) return;
            active.stage = Stage.SELL_ORDER;
            OrderLore.Fill filled = OrderLore.fill(lore(sell));
            int soldUnits = filled == null ? -1 : filled.filled();
            if (soldUnits >= active.quantity) {
                receipt = false;
                claimPending = true;
                claimUnits = active.quantity;
                claimedProceeds = null;
                purseBefore = new ScoreboardUtils().getPurse();
                if (!save()) return;
                click(sell); // Claim completed sale, never sell arbitrary inventory.
                transition(Step.VERIFY_SALE);
                minecraft.player.closeContainer();
                return;
            }
            if (soldUnits >= 0 && shouldReprice(true) && active.reprices < settings().maxReprices && freshQuotes()
                    && profitableSale(currentAsk())) {
                selling = true;
                inventoryBefore = inventory;
                expectedClaim = Math.max(0, active.quantity - soldUnits);
                cancelSoldUnits = soldUnits;
                claimedProceeds = null;
                if (expectedClaim > capacityFor(active.item.id())) {
                    fail("Insufficient inventory space to cancel the sell offer."); return;
                }
                purseBefore = new ScoreboardUtils().getPurse();
                receipt = false;
                click(sell);
                transition(Step.CANCEL_DETAIL);
                return;
            }
            finishWork();
            return;
        }
        if (inventory > 0) {
            if (inventory > active.quantity) { fail("Inventory exceeds tracked quantity; manual reconciliation required."); return; }
            active.quantity = inventory;
            active.stage = Stage.INVENTORY;
            recordAcquisition();
            capital.restore(OWNER, active.item.id(), active.cost(), false);
            if (!freshQuotes() || !profitableSale(currentAsk())) { finishWork(); return; }
            selling = true;
            minecraft.player.closeContainer();
            transition(Step.OPEN_PRODUCT);
        } else if (active.stage == Stage.SELL_ORDER || active.cancelRequested) {
            fail("Tracked sell/cancel position is absent; ownership is uncertain. Position retained for manual reconciliation.");
        } else if (System.currentTimeMillis() - active.placedAt > 5000) {
            fail("Tracked buy order and inventory are both missing; manual reconciliation required.");
        }
    }

    private void cancelDetail() {
        if (menu("Order")) {
            int cancel = find("Cancel Order", false);
            if (cancel >= 0) {
                active.cancelRequested = true;
                if (!save()) return;
                click(cancel);
                minecraft.player.closeContainer();
                transition(Step.VERIFY_CANCEL);
                return;
            }
        }
        // A fully filled order can be claimed directly without an order detail screen.
        if (itemCount(active.item.id()) > inventoryBefore && findOrder(selling) < 0) {
            minecraft.player.closeContainer();
            transition(Step.VERIFY_CANCEL);
        }
    }

    private void verifyCancellation() {
        int count = itemCount(active.item.id());
        long elapsed = System.currentTimeMillis() - stepSince;
        boolean itemsArrived = count > inventoryBefore && count >= inventoryBefore + expectedClaim;
        if (expectedClaim > 0 && count < inventoryBefore + expectedClaim) return;
        if (!itemsArrived && !receipt) return;
        if (elapsed < 500) return;
        // Reopen orders to verify cancellation before creating a replacement.
        command("managebazaarorders");
        if (!ordersReady() || ambiguousOrders() || findOrder(selling) >= 0) return;
        if (selling && cancelSoldUnits > 0) {
            recordSale(cancelSoldUnits, claimedProceeds);
            cancelSoldUnits = 0;
        }
        if (count == 0) {
            if (!selling && active.reprices < settings().maxReprices && freshQuotes()) {
                JsonObject product = products.getAsJsonObject(active.item.id());
                double bid = product == null ? -1 : GeneralCalculator.topPrice(product, "sell_summary");
                double net = currentAsk() * (1 - GoofyConfig.INSTANCE.bazaarTaxPercentage / 100) - bid;
                if (bid > 0 && net * active.quantity >= settings().minProfitPerBatch
                        && net / bid * 100 >= settings().minMarginPercentage
                        && bid * active.quantity <= settings().maxCoinsPerItem
                        && capital.resize(OWNER, active.item.id(), bid * active.quantity, new ScoreboardUtils().getPurse())) {
                    active.unitCost = bid;
                    active.reprices++;
                    active.stage = Stage.PLANNED;
                    active.submitted = false;
                    active.cancelRequested = false;
                    finishWork();
                    return;
                }
            }
            completePosition();
            return;
        }
        if (count > active.quantity) { fail("Inventory exceeds tracked quantity; manual reconciliation required."); return; }
        active.quantity = count;
        active.cancelRequested = false;
        active.stage = Stage.INVENTORY;
        if (!selling) recordAcquisition();
        if (selling) active.reprices++;
        capital.restore(OWNER, active.item.id(), active.cost(), false);
        finishWork();
    }

    private void openProduct() {
        int create = find(selling ? "Create Sell Offer" : "Create Buy Order", false);
        if (menu(active.item.name()) && create >= 0) {
            if (selling) {
                int quantity = itemCount(active.item.id());
                if (quantity <= 0 || quantity > active.quantity) { fail("Tracked inventory quantity does not match."); return; }
                active.quantity = quantity;
            }
            click(create);
            transition(Step.QUANTITY);
            return;
        }
        if (menu("Bazaar")) {
            int item = find(active.item.name(), true);
            if (item >= 0) click(item);
        }
    }

    private void choosePrice() {
        if (!priceMenu() || !loadedSlot(12) || !freshQuotes()) return;
        double price = unitPrice(12);
        if (price <= 0) { fail("Unable to read the transaction price."); return; }
        if (selling) {
            if (!profitableSale(price)) { active.stage = Stage.INVENTORY; finishWork(); return; }
            active.sellPrice = price;
        } else {
            double net = currentAsk() * (1 - GoofyConfig.INSTANCE.bazaarTaxPercentage / 100) - price;
            if (net <= 0 || net / price * 100 < settings().minMarginPercentage
                    || net * active.quantity < settings().minProfitPerBatch
                    || price * active.quantity > settings().maxCoinsPerItem
                    || !capital.resize(OWNER, active.item.id(), price * active.quantity, new ScoreboardUtils().getPurse())) {
                capital.release(OWNER, active.item.id());
                positions.remove(active);
                finishWork();
                return;
            }
            active.unitCost = price;
            active.purchasePriceKnown = true;
        }
        click(12); // Current top order price, matching the calculator.
        transition(Step.CONFIRM);
    }

    private boolean profitableSale(double price) {
        double net = price * (1 - GoofyConfig.INSTANCE.bazaarTaxPercentage / 100) - active.unitCost;
        // Partial batches retain their unit margin; do not require a full-batch minimum.
        return Double.isFinite(net) && net > 0 && net / active.unitCost * 100 >= settings().minMarginPercentage;
    }

    private boolean shouldReprice(boolean sell) {
        if (!freshQuotes() || active.reprices >= settings().maxReprices
                || System.currentTimeMillis() - active.placedAt < settings().repriceCooldownSeconds * 1000L) return false;
        JsonObject product = products.getAsJsonObject(active.item.id());
        if (product == null) return false;
        double price = GeneralCalculator.topPrice(product, sell ? "buy_summary" : "sell_summary");
        return sell ? price > 0 && price < active.sellPrice - 0.01 : price > active.unitCost + 0.01;
    }

    private List<GeneralCalculator.Candidate> candidates() {
        if (minecraft.player == null || products == null) return List.of();
        int empty = 0;
        for (Slot slot : minecraft.player.containerMenu.slots) {
            if (slot.container == minecraft.player.getInventory() && slot.getItem().isEmpty()) empty++;
        }
        return GeneralCalculator.calculate(products, settings(), GoofyConfig.INSTANCE.bazaarTaxPercentage,
                capital.available(new ScoreboardUtils().getPurse()), TradingSafety.conservativeCapacity(empty, 4));
    }

    private double currentAsk() {
        JsonObject product = products == null ? null : products.getAsJsonObject(active.item.id());
        return product == null ? -1 : GeneralCalculator.topPrice(product, "buy_summary");
    }
    private boolean freshQuotes() { return products != null && TradingSafety.fresh(quotesAt, System.currentTimeMillis()); }
    private GeneralSettings settings() { return GoofyConfig.INSTANCE.general; }
    private boolean priceMenu() { return menu(selling ? "At what price" : "How much do you want to pay"); }
    private boolean menu(String title) { return minecraft.screen != null && minecraft.screen.getTitle().getString().contains(title); }
    private boolean loadedSlot(int slot) { return slot >= 0 && slot < minecraft.player.containerMenu.slots.size()
            && minecraft.player.containerMenu.slots.get(slot).hasItem(); }
    private void click(int slot) { if (loadedSlot(slot)) InventoryUtils.clickSlot(slot, false); }
    private void transition(Step next) {
        step = next; stepSince = System.currentTimeMillis(); lastCommand = 0;
        ordersContainer = -1; ordersSeenAt = 0;
    }
    private void command(String text) {
        long now = System.currentTimeMillis();
        if (minecraft.screen == null && now - lastCommand > 1500) {
            minecraft.player.connection.sendCommand(text);
            lastCommand = now;
        }
    }

    private int findOrder(boolean sell) { return find((sell ? "SELL " : "BUY ") + active.item.name(), true); }
    private boolean ordersReady() {
        if (minecraft.screen == null) return false;
        String title = minecraft.screen.getTitle().getString();
        if (!TradingSafety.ordersTitle(title)) return false;
        int id = minecraft.player.containerMenu.containerId;
        long now = System.currentTimeMillis();
        if (ordersContainer != id) { ordersContainer = id; ordersSeenAt = now; return false; }
        if (now - ordersSeenAt < 750 || find("Go Back", false) < 0 && find("Close", true) < 0) return false;
        if (find("Next Page", false) >= 0 || find("Previous Page", false) >= 0) {
            fail("Orders span multiple pages; automatic ownership checks are blocked."); return false;
        }
        return true;
    }
    private boolean ambiguousOrders() {
        int end = Math.max(0, minecraft.player.containerMenu.slots.size() - 36);
        List<String> names = minecraft.player.containerMenu.slots.subList(0, end).stream()
                .map(slot -> slot.getItem().getHoverName().getString().replaceAll("§.", "")).toList();
        if (TradingSafety.ambiguousOrders(names, active.item.name())) {
            fail("Duplicate or paginated orders; manual reconciliation required."); return true;
        }
        return false;
    }
    private boolean orderMatchesPosition(int slot) {
        OrderLore.Fill fill = OrderLore.fill(lore(slot));
        if (!TradingSafety.orderQuantityMatches(active.quantity, fill == null ? null : fill.total())) {
            fail("Order quantity is unreadable or differs from tracked ownership; position retained."); return false;
        }
        return true;
    }
    private int capacityFor(String id) {
        int empty = 0;
        int partial = 0;
        int stackLimit = 1;
        for (Slot slot : minecraft.player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            boolean matches = data != null && id.equals(data.copyTag().getStringOr("id", ""));
            if (matches) stackLimit = Math.max(1, Math.min(64, stack.getMaxStackSize()));
            if (slot.container != minecraft.player.getInventory() || slot.getContainerSlot() >= 36) continue;
            if (stack.isEmpty()) empty++;
            else if (matches) partial += Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }
        return TradingSafety.conservativeCapacity(empty, 4) * stackLimit + partial;
    }
    private int find(String text, boolean exact) {
        int end = Math.max(0, minecraft.player.containerMenu.slots.size() - 36);
        for (int i = 0; i < end; i++) {
            ItemStack item = minecraft.player.containerMenu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            String name = item.getHoverName().getString().replaceAll("§.", "");
            if (exact ? name.equals(text) : name.contains(text)) return i;
        }
        return -1;
    }
    private String lore(int slot) {
        ItemLore lore = minecraft.player.containerMenu.slots.get(slot).getItem().get(DataComponents.LORE);
        return lore == null ? "" : String.join("\n", lore.lines().stream().map(line -> line.getString()).toList());
    }
    private double unitPrice(int slot) {
        Matcher match = Pattern.compile("Unit price:\\s*([\\d,.]+)").matcher(lore(slot));
        return match.find() ? Double.parseDouble(match.group(1).replace(",", "")) : -1;
    }
    private int itemCount(String id) {
        int count = 0;
        for (Slot slot : minecraft.player.containerMenu.slots) {
            if (slot.container != minecraft.player.getInventory()) continue;
            CustomData data = slot.getItem().get(DataComponents.CUSTOM_DATA);
            if (data != null && id.equals(data.copyTag().getStringOr("id", ""))) count += slot.getItem().getCount();
        }
        return count;
    }

    private void onNotice(String message) {
        if (!running || paused || active == null || !message.contains(active.item.name())) return;
        if (step == Step.VERIFY_SALE && claimPending) {
            Double coins = TradeReceipts.saleProceeds(message, active.item.name(), claimUnits);
            if (coins != null) { receipt = true; claimedProceeds = coins; }
        }
        if (selling && cancelSoldUnits > 0 && (step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL)) {
            Double coins = TradeReceipts.saleProceeds(message, active.item.name(), cancelSoldUnits);
            if (coins != null) claimedProceeds = coins;
        }
        if ((step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL) && active.cancelRequested
                && TradingSafety.cancellationReceipt(message, active.item.name())) receipt = true;
    }
    private void recordAcquisition() {
        if (active.tradeId == null) active.tradeId = java.util.UUID.randomUUID().toString();
        ProfitTracker.INSTANCE.acquire(active.tradeId, OWNER, active.item.name(), active.tradeId + ":buy",
                active.quantity, active.purchasePriceKnown ? active.cost() : null);
    }
    private void recordSale(int units, Double proceeds) {
        if (active.tradeId == null) active.tradeId = java.util.UUID.randomUUID().toString();
        if (active.saleEvent == null) active.saleEvent = active.tradeId + ":legacy-sale:" + active.placedAt;
        ProfitTracker.INSTANCE.sell(active.tradeId, OWNER, active.item.name(), active.saleEvent, units, proceeds);
    }
    private void finishWork() {
        if (active != null && positions.contains(active)) active.checkedAt = System.currentTimeMillis();
        save();
        active = null;
        if (minecraft.player != null && minecraft.screen != null) minecraft.player.closeContainer();
    }
    private void completePosition() {
        if (active.stage == Stage.BUY_ORDER || active.stage == Stage.PLANNED) {
            cooldownUntil.put(active.item.id(), System.currentTimeMillis() + settings().orderTimeoutSeconds * 1000L);
        }
        capital.release(OWNER, active.item.id());
        positions.remove(active);
        finishWork();
    }
    private void fail(String message) {
        ChatUtils.clientMessage(message);
        LOGGER.error(message);
        paused = true;
        invalidateRequest();
        save();
        active = null;
        if (minecraft.player != null && minecraft.screen != null) minecraft.player.closeContainer();
        FeatureManager.INSTANCE.safetyPause(message);
    }

    private Path statePath() { return FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-general-orders.json"); }
    private void load() {
        loaded = true;
        try {
            if (!Files.exists(statePath())) return;
            Position[] saved = GSON.fromJson(Files.readString(statePath()), Position[].class);
            if (saved == null) throw new IllegalArgumentException("Missing order state");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Position position : saved) {
                if (position == null || position.item == null || position.item.id() == null || position.item.name() == null
                        || position.quantity <= 0 || position.quantity > 4096 || !Double.isFinite(position.unitCost)
                        || position.unitCost <= 0 || !Double.isFinite(position.cost())
                        || !position.item.id().matches("[A-Z0-9_]+") || position.item.id().startsWith("ENCHANTMENT_")
                        || position.item.name().isBlank()
                        || position.stage == null || !ids.add(position.item.id())) {
                    throw new IllegalArgumentException("Invalid saved order position");
                }
            }
            positions.addAll(List.of(saved));
        } catch (Exception failure) {
            blocked = true;
            LOGGER.error("Cannot read general order state; file preserved", failure);
        }
    }
    private boolean save() {
        if (!loaded || blocked) return !blocked;
        Path temp = null;
        try {
            Files.createDirectories(statePath().getParent());
            temp = Files.createTempFile(statePath().getParent(), "general-orders-", ".tmp");
            Files.writeString(temp, GSON.toJson(positions));
            Files.move(temp, statePath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception failure) {
            blocked = true;
            LOGGER.error("Unable to persist general order state; trading blocked", failure);
            FeatureManager.INSTANCE.safetyPause("Cannot persist general order state.");
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }
}
