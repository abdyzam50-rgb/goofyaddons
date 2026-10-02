package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        boolean settlementPending;
        double cost() { return quantity * unitCost; }
    }

    private final com.goofy.goofyaddons.menu.GameWorld world;
    private final java.util.function.Supplier<Path> statePath;
    private final com.goofy.goofyaddons.menu.GameActions actions;
    /** The menu as observed once this tick; every decision below reads this, not the game. */
    private com.goofy.goofyaddons.menu.MenuSnapshot view = new com.goofy.goofyaddons.menu.MenuSnapshot(0, null, true, List.of());
    private final CapitalManager capital = CapitalManager.INSTANCE;
    private final List<Position> positions = new ArrayList<>();
    private final java.util.Map<String, Long> cooldownUntil = new java.util.HashMap<>();
    private boolean loaded;
    /** JSON last written to disk; set only after a successful atomic replace. */
    private String persisted;
    private boolean running;
    private boolean paused;
    private boolean blocked;
    private CompletableFuture<JsonObject> request;
    private int generation;
    private JsonObject products;
    private final com.goofy.goofyaddons.features.MenuObservationStability confirmationStability=new com.goofy.goofyaddons.features.MenuObservationStability();
    private final com.goofy.goofyaddons.features.MenuRecheck menuRecheck=new com.goofy.goofyaddons.features.MenuRecheck();
    private boolean reopeningOrders;
    private String recheckReason;
    private long quotesAt;
    private long nextPoll;
    // Recomputed once per tick by refreshSnapshot(); read by the scheduler and the HUD,
    // neither of which may recompute the market or advance quote state themselves.
    private List<GeneralCalculator.Candidate> snapshotCandidates = List.of();
    private double snapshotPurse = -1;
    private boolean snapshotFresh;
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
    private boolean reopenedCancelOptions;
    private Double claimedProceeds;
    private final com.goofy.goofyaddons.features.MenuSettle ordersSettle=new com.goofy.goofyaddons.features.MenuSettle();
    private long stepSince;
    private long nextAction;
    private long lastCommand;

    public GeneralFlipper() {
        // Resolved lazily: touching FabricLoader at construction would make this engine
        // unconstructable outside a running game, including in a test.
        this(new com.goofy.goofyaddons.menu.LiveWorld(), new com.goofy.goofyaddons.menu.LiveActions(),
                () -> FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-general-orders.json"));
    }

    /** The seam: a test supplies a menu to observe, a store to use, and records what the engine does. */
    GeneralFlipper(com.goofy.goofyaddons.menu.GameWorld world, com.goofy.goofyaddons.menu.GameActions actions,
                   java.util.function.Supplier<Path> statePath) {
        this.world = world;
        this.actions = actions;
        this.statePath = statePath;
        ChatHook.onMessage("[Bazaar]", this::onNotice);
    }

    /** One observation per tick. Decisions inside a tick then share a single view. */
    private void observe() {
        var observed = world.menu();
        view = observed == null ? new com.goofy.goofyaddons.menu.MenuSnapshot(0, null, true, List.of()) : observed;
    }

    @Override public String name() { return "GeneralFlipper"; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean canYield() { return active == null; }
    @Override public void yieldMenu() {
        if (active == null) actions.closeMenu();
    }

    public boolean hasStateError() { return blocked; }
    public java.util.Map<String,Object> diagnosticState() {
        var state=new java.util.LinkedHashMap<String,Object>();
        state.put("step",step==null?"none":step.name());state.put("stepAgeMs",stepSince==0?0:System.currentTimeMillis()-stepSince);
        state.put("blocked",blocked);state.put("claimPending",claimPending);state.put("receipt",receipt);
        state.put("claimUnits",claimUnits);state.put("inventoryBefore",inventoryBefore);state.put("expectedClaim",expectedClaim);
        state.put("ordersContainer",ordersSettle.container());state.put("selling",selling);
        var retained=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(Position position:positions) {
            var details=new java.util.LinkedHashMap<String,Object>();
            details.put("trade",position.tradeId);details.put("item",position.item.id());details.put("units",position.quantity);
            details.put("stage",position.stage);details.put("cost",position.cost());details.put("sellPrice",position.sellPrice);
            details.put("settlementPending",position.settlementPending);
            details.put("submitted",position.submitted);details.put("cancelRequested",position.cancelRequested);details.put("reprices",position.reprices);
            retained.add(details);
        }
        state.put("positions",retained);return state;
    }
    public String retainedItem() {
        return positions.isEmpty() ? "No retained general orders" : "Retained: "+positions.getFirst().quantity+"x "+positions.getFirst().item.name();
    }
    public boolean hasRetainedPositions() { return !positions.isEmpty(); }
    public String taskItem() {
        return active == null ? "No item selected" : active.item.name();
    }
    /** Pure reader: the HUD calls this every frame, so it must not recompute or mutate. */
    public String activity() {
        if (active != null) return "General: " + step.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (!snapshotFresh) return "Waiting for price data";
        if (!positions.isEmpty()) return "Waiting for general orders";
        if (capital.available(snapshotPurse) <= 0) return "Capital limit / purse reserve reached";
        return snapshotCandidates.isEmpty() ? "No flips meet the configured filters" : "Selecting eligible flips";
    }

    public void restoreBudget() {
        if (!loaded) load();
        for (Position position : positions) capital.restore(OWNER, position.item.id(), position.cost(),
                position.stage == Stage.PLANNED);
    }

    @Override public void start() {
        restoreBudget();
        if (blocked) { actions.message("General flipper is blocked; resolve the logged order-state error first."); return; }
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
        clearSnapshot();
        save();
    }

    @Override public void pause() {
        paused = true;
        invalidateRequest();
        active = null;
        clearSnapshot();
        save();
    }

    /** A snapshot only describes a running engine; never let a stale one be read back. */
    private void clearSnapshot() {
        snapshotCandidates = List.of();
        snapshotFresh = false;
        snapshotPurse = -1;
    }
    @Override public void resume() { if (running) start(); }

    private void invalidateRequest() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
    }

    @Override public void poll() {
        if (!running || paused || blocked) return;
        observe();
        refreshSnapshot();
        if (request != null || System.currentTimeMillis() < nextPoll) return;
        nextPoll = System.currentTimeMillis() + settings().refreshSeconds * 1000L;
        int run = generation;
        try {
            request = BazaarApi.fetch();
            request.whenComplete((root, error) -> world.onClientThread(() -> {
                if (generation != run || !running || paused) return;
                try {
                    if (error != null) throw new IllegalStateException("Bazaar request failed", error);
                    long updated = TradingSafety.sourceTime(root, System.currentTimeMillis());
                    if(updated<quotesAt) return;
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
        return snapshotFresh && positions.size() < settings().maxActiveItems && !capital.purchaseSettling()
                && !snapshotCandidates.isEmpty();
    }

    @Override public void onTick() {
        if (!running || paused || blocked || !world.inWorld()) return;
        observe();
        if (active == null) selectWork();
        if (active == null || paused || blocked) return;
        long now = System.currentTimeMillis();
        if (now - stepSince > 30000) { fail("Menu/transaction timed out; retained the tracked position for recovery."); return; }
        if (now < nextAction) return;
        nextAction = now + com.goofy.goofyaddons.utils.ActionDelay.next();
        try {
            if(reopeningOrders) {
                command("managebazaarorders");
                if(!ordersReady()) {
                    if(view.title()!=null) recheckOrders(recheckReason);
                    return;
                }
                reopeningOrders=false;
            }
            if((step==Step.ORDERS || step==Step.VERIFY_ORDER || step==Step.VERIFY_CANCEL || step==Step.VERIFY_SALE)
                    && view.title()!=null && !TradingSafety.ordersTitle(view.title())) {
                recheckOrders("unexpected-verification-menu");return;
            }
            if((step==Step.ORDERS || step==Step.VERIFY_ORDER || step==Step.VERIFY_CANCEL || step==Step.VERIFY_SALE)
                    && TradingSafety.ordersTitle(view.title()) && !ordersReady()) {
                recheckOrders("orders-not-loaded");return;
            }
            switch (step) {
                case OPEN_ORDERS -> {
                    command("managebazaarorders");
                    if (TradingSafety.ordersTitle(view.title())) transition(Step.ORDERS);
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
                    if (world.signEditorOpen()) transition(Step.SIGN);
                    else if (menu("How many")) {
                        int custom = find("Custom Amount", false);
                        if (custom < 0) custom = find("Custom", false);
                        if (custom >= 0) { click(custom); transition(Step.SIGN); }
                    } else if (priceMenu()) transition(Step.PRICE);
                }
                case SIGN -> {
                    if (world.signEditorOpen()) {
                        if (!actions.writeSign(Integer.toString(active.quantity))) {
                            fail("Could not write the order amount onto the sign; no order submitted."); return;
                        }
                        transition(Step.PRICE);
                    } else if (priceMenu()) transition(Step.PRICE);
                }
                case PRICE -> choosePrice();
                case CONFIRM -> {
                    if (!menu("Confirm")) return;
                    if(!TradingSafety.confirmationTitle(view.title(),selling)) {
                        fail("Unexpected confirmation type; no order submitted.");return;
                    }
                    if (!freshQuotes()) { fail("Quotes expired before order confirmation."); return; }
                    if (!selling && active.quantity > capacityFor(active.item.id())) {
                        fail("Not enough inventory capacity for this buy order."); return;
                    }
                    int confirm = 13;
                    if (!loadedSlot(confirm)) return;
                    if(!confirmationStability.ready(view.containerId(),
                            view.title()+"\n"+view.slot(confirm).hoverName()+"\n"+lore(confirm),
                            !lore(confirm).isBlank(),now)) return;
                    double expectedPrice=selling?active.sellPrice:active.unitCost;
                    if(!com.goofy.goofyaddons.features.ConfirmationCheck.matches(view.title(),selling,
                            view.slot(confirm).hoverName(),lore(confirm),active.item.name(),active.quantity,expectedPrice)) {
                        fail("Confirmation item, quantity or price is unreadable or differs; no order submitted.");return;
                    }
                    if(selling) {
                        if(itemCount(active.item.id())!=active.quantity || !profitableSale(expectedPrice)) {
                            fail("Inventory or net margin changed before sale confirmation; no order submitted.");return;
                        }
                    } else if(!com.goofy.goofyaddons.features.ConfirmationCheck.buyAllowed(expectedPrice,active.quantity,currentAsk(),
                            GoofyConfig.INSTANCE.bazaarTaxPercentage,settings().minMarginPercentage,settings().minProfitPerBatch,settings().maxCoinsPerItem)
                            || !capital.resize(OWNER,active.item.id(),active.cost(),new ScoreboardUtils().getPurse())) {
                        fail("Price or available capital changed before buy confirmation; no order submitted.");return;
                    }
                    active.submitted = true;
                    if (selling) active.saleEvent = java.util.UUID.randomUUID().toString();
                    active.placedAt = now;
                    if (!save()) return; // Persist intent before the irreversible click.
                    click(confirm);
                    Diagnostics.event("INFO","order.submitted",java.util.Map.of("trade",active.tradeId,"item",active.item.id(),"units",active.quantity,"side",selling?"SELL":"BUY","unitPrice",selling?active.sellPrice:active.unitCost,"cost",active.cost()));
                    transition(Step.VERIFY_ORDER);
                    actions.closeMenu();
                }
                case VERIFY_ORDER -> {
                    if(now-stepSince<2000) return; // Allow escrow/setup to reach the server before opening orders.
                    command("managebazaarorders");
                    if (!ordersReady()) return;
                    if (ambiguousOrders()) return;
                    int order = findOrder(selling);
                    if (order >= 0) {
                        if (!orderMatchesPosition(order)) return;
                        Diagnostics.event("INFO","order.verified",java.util.Map.of("trade",active.tradeId,"item",active.item.id(),"units",active.quantity,"side",selling?"SELL":"BUY"));
                        active.stage = selling ? Stage.SELL_ORDER : Stage.BUY_ORDER;
                        if (!selling) capital.purchased(OWNER, active.item.id());
                        finishWork();
                    } else if (!selling && itemCount(active.item.id()) >= active.quantity) {
                        capital.purchased(OWNER, active.item.id());
                        active.stage = Stage.INVENTORY;
                        if(!recordAcquisition()) return;
                        finishWork();
                    } else {
                        recheckOrders("placement-not-visible");
                    }
                }
                case VERIFY_SALE -> {
                    command("managebazaarorders");
                    if (!ordersReady() || ambiguousOrders()) return;
                    if (TradingSafety.saleComplete(claimPending, receipt, findOrder(true) < 0, itemCount(active.item.id()))) {
                        if(!recordSale(claimUnits, claimedProceeds)) return;
                        completePosition();
                    } else {
                        recheckOrders("sale-not-reconciled");
                    }
                }
            }
        } catch (Exception failure) {
            Diagnostics.failure("general.transaction_failed",failure);
            LOGGER.error("General transaction failed", failure);
            fail("General transaction failed; position retained. Check logs before restarting.");
        }
    }

    private void selectWork() {
        long now = System.currentTimeMillis();
        for (Position position : positions) {
            if(position.settlementPending) {
                active=position;
                fail("A settlement was interrupted; reconcile the retained order and profit records before restarting.");
                return;
            }
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
        if (!snapshotFresh || positions.size() >= settings().maxActiveItems || capital.purchaseSettling()) return;
        // Reserving capital stays on a live purse read; only ranking uses the snapshot.
        double purse = new ScoreboardUtils().getPurse();
        for (GeneralCalculator.Candidate candidate : snapshotCandidates) {
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
        if(buy<0 && sell<0 && active.stage!=Stage.PLANNED && (active.stage!=Stage.INVENTORY || inventory==0)
                && recheckOrders("tracked-order-absent")) return;
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
            actions.closeMenu();
            transition(Step.OPEN_PRODUCT);
            return;
        }
        if (buy >= 0 && sell >= 0) { fail("Both buy and sell orders exist for one tracked item; manual reconciliation required."); return; }
        if (buy >= 0) {
            if (!orderMatchesPosition(buy)) return;
            active.stage = Stage.BUY_ORDER;
            if (active.heldSince == 0) active.heldSince = active.placedAt;
            capital.purchased(OWNER, active.item.id());
            int claimable = OrderLore.claimable(lore(buy), inventory);
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
            reopenedCancelOptions=false;
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
                active.settlementPending=true;
                claimUnits = active.quantity;
                claimedProceeds = null;
                purseBefore = new ScoreboardUtils().getPurse();
                if (!save()) return;
                click(sell); // Claim completed sale, never sell arbitrary inventory.
                transition(Step.VERIFY_SALE);
                actions.closeMenu();
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
                reopenedCancelOptions=false;
                active.settlementPending=soldUnits>0;
                if(!save()) return;
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
            active.cancelRequested=false;
            if(!recordAcquisition()) return;
            capital.restore(OWNER, active.item.id(), active.cost(), false);
            if (!freshQuotes() || !profitableSale(currentAsk())) { finishWork(); return; }
            selling = true;
            actions.closeMenu();
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
                actions.closeMenu();
                transition(Step.VERIFY_CANCEL);
                return;
            }
        }
        // Partial buy claims leave the order in the list. Reopen its options
        // only after the expected inventory delta and the server's options hint.
        if (!reopenedCancelOptions && ordersReady() && !ambiguousOrders()) {
            int order=findOrder(selling);
            boolean mayOpen=order>=0 && (selling
                    ? OrderLore.canOpenSellOptionsAfterClaim(lore(order),cancelSoldUnits,claimedProceeds!=null)
                    : OrderLore.canOpenOptionsAfterClaim(lore(order),inventoryBefore,itemCount(active.item.id()),expectedClaim));
            if(mayOpen) {
                if(!orderMatchesPosition(order)) return;
                Diagnostics.event("INFO","order.claim_then_open_options",java.util.Map.of("trade",active.tradeId,"inventoryBefore",inventoryBefore,"inventoryNow",itemCount(active.item.id()),"expectedClaim",expectedClaim));
                reopenedCancelOptions=true;
                click(order);
                return;
            }
        }
        // A fully filled order can be claimed directly without an order detail screen.
        if (ordersReady() && itemCount(active.item.id()) > inventoryBefore && findOrder(selling) < 0) {
            actions.closeMenu();
            transition(Step.VERIFY_CANCEL);
        }
    }

    private void verifyCancellation() {
        int count = itemCount(active.item.id());
        long elapsed = System.currentTimeMillis() - stepSince;
        boolean itemsArrived = count > inventoryBefore && count >= inventoryBefore + expectedClaim;
        if (expectedClaim > 0 && count < inventoryBefore + expectedClaim) {
            command("managebazaarorders");
            if(ordersReady()) recheckOrders("cancel-inventory-not-visible");
            return;
        }
        if (!itemsArrived && !receipt) {
            command("managebazaarorders");
            if(ordersReady()) recheckOrders("cancel-outcome-not-visible");
            return;
        }
        if (elapsed < 500) return;
        // Reopen orders to verify cancellation before creating a replacement.
        command("managebazaarorders");
        if (!ordersReady() || ambiguousOrders()) return;
        if(findOrder(selling)>=0) {recheckOrders("cancel-not-visible");return;}
        if (selling && cancelSoldUnits > 0) {
            if(!recordSale(cancelSoldUnits, claimedProceeds)) return;
            cancelSoldUnits = 0;
            active.settlementPending=false;
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
        if (!selling && !recordAcquisition()) return;
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
        if (!world.inWorld() || products == null) return List.of();
        return GeneralCalculator.calculate(products, settings(), GoofyConfig.INSTANCE.bazaarTaxPercentage,
                capital.available(snapshotPurse), TradingSafety.conservativeCapacity(view.emptyInventorySlots(), 4));
    }

    private double currentAsk() {
        JsonObject product = products == null ? null : products.getAsJsonObject(active.item.id());
        return product == null ? -1 : GeneralCalculator.topPrice(product, "buy_summary");
    }
    /** Pure: whether the quotes this engine already adopted are usable right now. */
    private boolean freshQuotes() {
        return products != null && TradingSafety.fresh(quotesAt, System.currentTimeMillis());
    }

    /** Adopting a newer snapshot mutates engine state, so it belongs on the tick path only. */
    private void adoptLatestQuotes() {
        JsonObject latest=BazaarApi.latestFresh();
        if(latest==null) return;
        long updated=latest.get("lastUpdated").getAsLong();
        if(updated>quotesAt) {products=latest.getAsJsonObject("products");quotesAt=updated;}
    }

    /**
     * One market evaluation and one purse read per tick. Everything that used to
     * recompute these per query - needsMenu() on every scheduler pass and activity()
     * on every rendered frame - now reads the result.
     */
    private void refreshSnapshot() {
        adoptLatestQuotes();
        snapshotPurse = new ScoreboardUtils().getPurse();
        snapshotFresh = freshQuotes();
        snapshotCandidates = snapshotFresh ? candidates() : List.of();
    }
    private GeneralSettings settings() { return GoofyConfig.INSTANCE.general; }
    private boolean priceMenu() { return menu(selling ? "At what price" : "How much do you want to pay"); }
    // Now strips formatting codes, matching BazaarFlipper. A code inside the label used
    // to make a known menu unrecognisable, which surfaced as a step timeout.
    private boolean menu(String title) {
        return com.goofy.goofyaddons.utils.MenuText.titleContains(view.title(), title);
    }
    private boolean loadedSlot(int slot) { return view.loaded(slot); }
    private void click(int slot) { if (loadedSlot(slot)) actions.click(slot, false); }
    private void transition(Step next) {
        Diagnostics.event("INFO","general.transition",java.util.Map.of("from",step==null?"none":step.name(),"to",next.name(),"item",taskItem()));
        confirmationStability.reset();menuRecheck.reset();reopeningOrders=false;
        step = next; stepSince = System.currentTimeMillis(); lastCommand = 0;
        ordersSettle.reset();
    }
    private void command(String text) {
        long now = System.currentTimeMillis();
        if (view.title() == null && now - lastCommand > 1500) {
            actions.command(text);
            lastCommand = now;
        }
    }

    private boolean recheckOrders(String reason) {
        recheckReason=reason;
        var decision=menuRecheck.missing(step+":"+active.tradeId,System.currentTimeMillis());
        if(decision==com.goofy.goofyaddons.features.MenuRecheck.Decision.REOPEN) {
            Diagnostics.event("WARN","order.observation_recheck",java.util.Map.of("reason",reason,"trade",active.tradeId==null?"legacy":active.tradeId,
                    "attempt",menuRecheck.attempts(),"step",step.name(),"context",Diagnostics.detailedSnapshot()));
            actions.closeMenu();ordersSettle.reset();lastCommand=0;
            reopeningOrders=true;
        }
        return decision!=com.goofy.goofyaddons.features.MenuRecheck.Decision.EXHAUSTED;
    }

    private int findOrder(boolean sell) { return find((sell ? "SELL " : "BUY ") + active.item.name(), true); }
    private boolean ordersReady() {
        if (!TradingSafety.ordersTitle(view.title())) return false;
        if (!ordersSettle.settled(view.containerId(), System.currentTimeMillis())) return false;
        if (find("Go Back", false) < 0 && find("Close", true) < 0) return false;
        if (find("Next Page", false) >= 0 || find("Previous Page", false) >= 0) {
            fail("Orders span multiple pages; automatic ownership checks are blocked."); return false;
        }
        return true;
    }
    private boolean ambiguousOrders() {
        if (TradingSafety.ambiguousOrders(view.containerHoverNames(), active.item.name())) {
            fail("Duplicate or paginated orders; manual reconciliation required."); return true;
        }
        return false;
    }
    private boolean orderMatchesPosition(int slot) {
        String tooltip=lore(slot);
        Integer total=OrderLore.total(tooltip);
        if(total==null && recheckOrders("order-fields-unreadable")) return false;
        if (com.goofy.goofyaddons.utils.MenuText.titleContains(view.title(),"Co-op Bazaar Orders")) {
            OrderLore.Creator creator=OrderLore.creator(tooltip,world.username());
            if(creator==OrderLore.Creator.UNREADABLE && recheckOrders("order-creator-unreadable")) return false;
            if(creator!=OrderLore.Creator.OWN) {
                fail("Co-op order belongs to another player or its creator is unreadable; position retained.");return false;
            }
        }
        if (!TradingSafety.orderQuantityMatches(active.quantity,total)) {
            var evidence=new java.util.LinkedHashMap<String,Object>();
            evidence.put("trade",active.tradeId);evidence.put("slot",slot);evidence.put("expected",active.quantity);evidence.put("parsedTotal",total);evidence.put("lore",tooltip);
            Diagnostics.event("ERROR","order.quantity_rejected",evidence);
            fail("Order quantity is unreadable or differs from tracked ownership; position retained."); return false;
        }
        return true;
    }
    private int capacityFor(String id) {
        // Policy stays here; the three observations come from the snapshot.
        return TradingSafety.conservativeCapacity(view.emptyInventorySlots(), 4) * view.stackLimitFor(id)
                + view.partialStackSpace(id);
    }
    private int find(String text, boolean exact) { return view.firstByHoverName(text, exact); }
    private String lore(int slot) { return view.loreAt(slot); }
    private double unitPrice(int slot) {
        Double price=TradeReceipts.unitPrice(com.goofy.goofyaddons.utils.Chat.strip(lore(slot)));
        return price==null ? -1 : price;
    }
    private int itemCount(String id) { return view.countInInventory(id); }

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
    private boolean recordAcquisition() {
        if (active.tradeId == null) { active.tradeId=java.util.UUID.randomUUID().toString(); if(!save()) return false; }
        ProfitTracker.INSTANCE.acquire(active.tradeId, OWNER, active.item.name(), active.tradeId + ":buy",
                active.quantity, active.purchasePriceKnown ? active.cost() : null);
        return true;
    }
    private boolean recordSale(int units, Double proceeds) {
        if (active.tradeId == null) { active.tradeId=java.util.UUID.randomUUID().toString(); if(!save()) return false; }
        if (active.saleEvent == null) { active.saleEvent=active.tradeId+":legacy-sale:"+active.placedAt; if(!save()) return false; }
        ProfitTracker.INSTANCE.sell(active.tradeId, OWNER, active.item.name(), active.saleEvent, units, proceeds);
        return true;
    }
    private void finishWork() {
        if (active != null && positions.contains(active)) active.checkedAt = System.currentTimeMillis();
        save();
        active = null;
        actions.closeMenu();
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
        Diagnostics.event("ERROR","general.transaction_blocked",java.util.Map.of("reason",message,"context",Diagnostics.detailedSnapshot()));
        actions.message(message);
        LOGGER.error(message);
        paused = true;
        invalidateRequest();
        save();
        active = null;
        actions.closeMenu();
        FeatureManager.INSTANCE.safetyPause(message);
    }

    private Path statePath() { return statePath.get(); }
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
            Diagnostics.failure("general.state_load_failed",failure);
            LOGGER.error("Cannot read general order state; file preserved", failure);
        }
    }
    private boolean save() {
        if (!loaded || blocked) return !blocked;
        String json = GSON.toJson(positions);
        // Every call site used to write the file. A skip here only happens when the
        // file already holds exactly this state, so persist-before-click still holds.
        if (json.equals(persisted)) return true;
        Path temp = null;
        try {
            Files.createDirectories(statePath().getParent());
            temp = Files.createTempFile(statePath().getParent(), "general-orders-", ".tmp");
            Files.writeString(temp, json);
            Files.move(temp, statePath(), StandardCopyOption.REPLACE_EXISTING);
            persisted = json;
            return true;
        } catch (Exception failure) {
            blocked = true;
            Diagnostics.failure("general.state_save_failed",failure);
            LOGGER.error("Unable to persist general order state; trading blocked", failure);
            FeatureManager.INSTANCE.safetyPause("Cannot persist general order state.");
            return false;
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }
}
