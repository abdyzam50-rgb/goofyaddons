package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.profit.ProfitLedger;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the production state machine against server/menu acknowledgements, including complete flips. */
class GeneralLoopTest {
    @TempDir Path dir;
    private static final String ID = "ENCHANTED_COAL", NAME = "Enchanted Coal";

    @BeforeEach void configure() {
        GoofyConfig.INSTANCE = new GoofyConfig();
        GoofyConfig.INSTANCE.minActionDelay = 51;
        GoofyConfig.INSTANCE.maxActionDelay = 52;
        GoofyConfig.INSTANCE.purseReserve = 0;
        var settings = GoofyConfig.INSTANCE.general;
        settings.items = List.of(new GeneralItem(ID, NAME));
        settings.minProfitPerBatch = 0;
        settings.minMarginPercentage = 2;
        settings.maxItemsPerOrder = 16;
        settings.maxActiveItems = 1;
    }

    private final class Market implements GeneralFlipper.Services {
        final FakeWorld world;
        final ProfitLedger ledger = new ProfitLedger();
        final CapitalManager capital = new CapitalManager() {
            // The simulated server updates its purse immediately; there is no real-time settle window.
            @Override public boolean purchaseSettling() { return false; }
        };
        double bid = 100, ask = 130, purse = 100000;
        boolean quotes = true;
        int sales;
        String productId=ID;
        Market(FakeWorld world) { this.world = world; capital.configure(100000, 0); }
        @Override public CapitalManager capital() { return capital; }
        @Override public double purse() { return purse; }
        @Override public JsonObject latestQuotes() {
            if (!quotes) return null;
            return JsonParser.parseString("{\"lastUpdated\":" + world.clock() + ",\"products\":{\"" + productId
                    + "\":{\"sell_summary\":[{\"pricePerUnit\":" + bid + "}],\"buy_summary\":[{\"pricePerUnit\":" + ask
                    + "}],\"quick_status\":{\"buyMovingWeek\":100000,\"sellMovingWeek\":100000}}}}").getAsJsonObject();
        }
        @Override public CompletableFuture<JsonObject> fetchQuotes() { return new CompletableFuture<>(); }
        @Override public void acquire(GeneralFlipper.Position p) {
            ledger.acquire(p.tradeId, "general", p.item.name(), p.tradeId + ":buy", p.quantity,
                    p.purchasePriceKnown ? p.cost() : null);
        }
        @Override public void sell(GeneralFlipper.Position p, int units, Double proceeds) {
            if (ledger.sell(p.tradeId, "general", p.item.name(), p.saleEvent, units, proceeds)) sales++;
        }
    }

    private static MenuSnapshot menu(String title, int inventory, List<SlotView> buttons) {
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(SlotView.empty(i, false, i));
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(27 + i, true, i));
        if (inventory > 0) slots.set(27, new SlotView(27, true, 0, false, NAME, NAME, List.of(), ID, null, inventory, 64));
        for (var button : buttons) slots.set(button.index(), button);
        return new MenuSnapshot(title == null ? 0 : 42, title, true, slots);
    }
    private static MenuSnapshot orders(boolean selling, int total, int filled, int inventory) {
        var buttons = new ArrayList<SlotView>();
        buttons.add(SlotView.named(26, "Close", List.of()));
        if (total > 0) buttons.add(SlotView.named(11, (selling ? "SELL " : "BUY ") + NAME,
                List.of("Order amount: " + total, "Filled: " + filled + "/" + total,
                        filled > 0 ? "You have " + filled + " items to claim!" : "Click to view options!")));
        return menu("Your Bazaar Orders", inventory, buttons);
    }
    private void seed(FakeWorld world, String stage, int quantity, long age) throws Exception {
        Files.writeString(dir.resolve("orders.json"), """
                [{"item":{"id":"ENCHANTED_COAL","name":"Enchanted Coal"},"quantity":Q,"unitCost":100,
                "sellPrice":130,"stage":"STAGE","submitted":true,"placedAt":NOW,"heldSince":NOW,
                "checkedAt":0,"tradeId":"trade-1","purchasePriceKnown":true,"saleEvent":"sale-1"}]
                """.replace("STAGE", stage).replace("Q", "" + quantity).replace("NOW", "" + (world.clock() - age))
                .replace("\"submitted\":true", "\"submitted\":" + !stage.equals("PLANNED")));
    }
    private GeneralFlipper engine(FakeWorld world, GameActions actions, Market market) {
        var engine = new GeneralFlipper(world, actions, () -> dir.resolve("orders.json"), market);
        engine.start();
        return engine;
    }
    private static void drive(GeneralFlipper engine, FakeWorld world, long duration) {
        for (long i = 0; i < duration; i += 60) {
            engine.poll(); engine.onTick(); world.advance(60);
        }
    }
    private static String stage(GeneralFlipper engine) {
        return (String)engine.diagnosticState().get("step");
    }
    private static long clicks(RecordingActions actions, int slot) {
        return actions.serverEffects().stream().filter(a -> a.equals("click:" + slot)).count();
    }

    @Test void repeatsCompleteBuyClaimSellAndSettlementCycles() throws Exception {
        FakeWorld world = new FakeWorld().showing(menu(null, 0, List.of()));
        Market market = new Market(world);
        List<String> effects = new ArrayList<>();
        GeneralFlipper[] ref = new GeneralFlipper[1];
        GameActions server = new GameActions() {
            int inventory, buy, sell;
            boolean selling;
            void show(String title, SlotView... buttons) { world.showing(menu(title, inventory, List.of(buttons))); }
            @Override public void command(String text) {
                effects.add("command:" + text);
                if (text.equals("managebazaarorders")) world.showing(orders(sell > 0, Math.max(buy, sell), Math.max(buy, sell), inventory));
                else show("Bazaar ➜ " + NAME, SlotView.named(11, "Create Buy Order", List.of()), SlotView.named(12, "Create Sell Offer", List.of()));
            }
            @Override public void click(int slot, boolean shift) {
                effects.add("click:" + slot);
                String title = world.menu().title();
                if (title.equals("Your Bazaar Orders")) {
                    if (buy > 0) { inventory = buy; buy = 0; world.showing(orders(false, 0, 0, inventory)); }
                    else if (sell > 0) {
                        int sold = sell; sell = 0;
                        ref[0].onNotice("[Bazaar] Claimed " + (sold * 130 * 0.9875) + " coins from selling " + sold + "x " + NAME + " at 130 each!");
                        world.showing(orders(false, 0, 0, inventory));
                    }
                } else if (title.startsWith("Bazaar")) {
                    selling = slot == 12;
                    if (selling) show("At what price are you selling?", SlotView.named(12, "Best offer", List.of("Unit price: 130 coins")));
                    else show("How many do you want?", SlotView.named(16, "Custom Amount", List.of()));
                } else if (title.startsWith("How many")) world.signOpen(true);
                else if (title.startsWith("At what price") || title.startsWith("How much")) {
                    show(selling ? "Confirm Sell Offer" : "Confirm Buy Order", SlotView.named(13, NAME,
                            List.of("Item: " + NAME, "Amount: 16", "Unit price: " + (selling ? 130 : 100) + " coins")));
                } else if (title.startsWith("Confirm")) {
                    // The durable submission intent must exist before a server-changing click.
                    try { assertTrue(Files.readString(dir.resolve("orders.json")).contains("\"submitted\": true")); }
                    catch (Exception e) { throw new AssertionError(e); }
                    if (selling) { sell = inventory; inventory = 0; } else buy = 16;
                }
            }
            @Override public void closeMenu() { world.showing(menu(null, inventory, List.of())); }
            @Override public void message(String text) { fail(text); }
            @Override public boolean writeSign(String text) {
                assertEquals("16", text);
                world.signOpen(false);
                show("How much do you want to pay?", SlotView.named(12, "Top order", List.of("Unit price: 100 coins")));
                return true;
            }
        };
        ref[0] = engine(world, server, market);
        for (int i = 0; i < 3000 && market.sales < 2; i++) drive(ref[0], world, 60);
        assertEquals(2, market.sales, "the engine should automatically start and finish a second flip");
        assertFalse(ref[0].hasRetainedPositions());
        assertEquals(0, market.capital.committed());
        assertEquals(908, market.ledger.summary().profit(), 0.000001);
        assertEquals(0, market.ledger.summary().incomplete());
        assertEquals("[]", Files.readString(dir.resolve("orders.json")));
        assertEquals(4, effects.stream().filter(a -> a.equals("click:13")).count(), "one submission per side per cycle");
    }

    @Test void completedBuyTransitionsToInventoryWithoutACancellationReceipt() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 16, 8, 0));
        seed(world, "BUY_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        assertEquals(1, clicks(actions, 11));
        // The rest filled while the first claim was travelling to the server.
        world.showing(orders(false, 0, 0, 16));
        drive(engine, world, 1600);
        assertTrue(Files.readString(dir.resolve("orders.json")).contains("\"stage\": \"INVENTORY\""));
        assertEquals(1, clicks(actions, 11), "an acknowledged claim must not repeat");
    }

    @Test void unchangedBuyClaimRetriesAfterSlowdownButStopsAfterInventoryChanges() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 16, 16, 0));
        seed(world, "BUY_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        var engine = engine(world, actions, new Market(world));
        drive(engine, world, 1200);
        engine.onSlowdown("Please slow down!");
        drive(engine, world, 1400);
        assertEquals(1, clicks(actions, 11));
        drive(engine, world, 1800);
        assertEquals(2, clicks(actions, 11));
        world.showing(orders(false, 16, 16, 1));
        drive(engine, world, 4000);
        assertEquals(2, clicks(actions, 11), "partial inventory packets must suppress retries");
    }

    @Test void claimRetriesAreBounded() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 16, 16, 0));
        seed(world, "BUY_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        var engine = engine(world, actions, new Market(world));
        drive(engine, world, 20000);
        assertEquals(4, clicks(actions, 11), "one original claim and at most three retries");
    }

    @Test void delayedSaleReceiptSettlesOnceWithoutRepeatedGuiVisits() throws Exception {
        GoofyConfig.INSTANCE.general.items = List.of(); // Isolate settlement from selecting the next trade.
        FakeWorld world = new FakeWorld().showing(orders(true, 16, 16, 0));
        seed(world, "SELL_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        assertEquals("VERIFY_SALE", stage(engine));
        actions.clear();
        world.showing(orders(false, 0, 0, 0));
        drive(engine, world, 5500);
        assertTrue(engine.hasRetainedPositions());
        assertEquals(0, market.sales);
        assertTrue(actions.serverEffects().isEmpty(), "receipt grace must not repeatedly reopen or claim");
        engine.onNotice("[Bazaar] Claimed 2054 coins from selling 16x Enchanted Coal at 130 each!");
        drive(engine, world, 120);
        assertEquals(1, market.sales);
        assertFalse(engine.hasRetainedPositions());
        engine.onNotice("[Bazaar] Claimed 2054 coins from selling 16x Enchanted Coal at 130 each!");
        assertEquals(1, market.sales);
    }

    @Test void missingSaleReceiptNeverReleasesOwnership() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(true, 16, 16, 0));
        seed(world, "SELL_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        world.showing(orders(false, 0, 0, 0));
        drive(engine, world, 11000);
        assertEquals(0, market.sales);
        assertTrue(engine.hasRetainedPositions());
        assertEquals(1600, market.capital.committed());
        assertTrue(actions.performed().stream().anyMatch(a -> a.startsWith("message:Sale claim")));
    }

    @Test void heldStockCanSellBelowEntryMarginAndWithExpiredApiQuotes() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 0, 0, 16));
        seed(world, "INVENTORY", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world); market.ask = 101;
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        assertTrue(List.of("OPEN_PRODUCT", "PRODUCT").contains(stage(engine)));
        world.showing(menu("Bazaar ➜ " + NAME, 16, List.of(SlotView.named(12, "Create Sell Offer", List.of()))));
        drive(engine, world, 180);
        world.showing(menu("At what price are you selling?", 16, List.of(SlotView.named(12, "Best offer", List.of("Unit price: 101 coins")))));
        market.quotes = false; world.advance(61000);
        // Expire API data without ageing the current menu step beyond its deadline.
        engine.stop(); engine.start();
        world.showing(orders(false, 0, 0, 16));
        drive(engine, world, 1200);
        world.showing(menu("Bazaar ➜ " + NAME, 16, List.of(SlotView.named(12, "Create Sell Offer", List.of()))));
        drive(engine, world, 180);
        world.showing(menu("At what price are you selling?", 16, List.of(SlotView.named(12, "Best offer", List.of("Unit price: 101 coins")))));
        drive(engine, world, 180);
        assertEquals("CONFIRM", stage(engine));
        world.showing(menu("Confirm Sell Offer", 16, List.of(SlotView.named(13, NAME,
                List.of("Item: " + NAME, "Amount: 16", "Unit price: 101 coins")))));
        drive(engine, world, 1100);
        assertEquals(1, clicks(actions, 13), "held stock should exit despite a small loss and stale API quotes");
    }

    @Test void heldStockStillRespectsDrawdownAtTheLivePriceMenu() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 0, 0, 16));
        seed(world, "INVENTORY", 16, 0);
        RecordingActions actions = new RecordingActions();
        var engine = engine(world, actions, new Market(world));
        drive(engine, world, 1200);
        world.showing(menu("Bazaar ➜ " + NAME, 16, List.of(SlotView.named(12, "Create Sell Offer", List.of()))));
        drive(engine, world, 180);
        actions.clear();
        world.showing(menu("At what price are you selling?", 16, List.of(SlotView.named(12, "Best offer", List.of("Unit price: 80 coins")))));
        drive(engine, world, 180);
        assertEquals(0, clicks(actions, 12));
        assertTrue(engine.hasRetainedPositions());
    }

    @Test void alreadyCompletedSalesCanBeClaimedEvenAfterMarketDrawdown() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(true, 16, 16, 0));
        seed(world, "SELL_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world); market.ask = 50;
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        assertEquals(1, clicks(actions, 11), "already-earned proceeds are safe to claim");
        assertEquals("VERIFY_SALE", stage(engine));
    }
    @Test void partialBuyClaimCancelsRemainderAndSellsOnlyAcknowledgedUnits() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 16, 4, 0));
        seed(world, "BUY_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        world.showing(menu("Your Bazaar Orders", 4, List.of(
                SlotView.named(26, "Close", List.of()), SlotView.named(11, "BUY " + NAME,
                        List.of("Order amount: 16", "Filled: 4/16", "Click to view options!")))));
        drive(engine, world, 900);
        assertEquals(2, clicks(actions, 11), "claim then open acknowledged order options");
        world.showing(menu("Buy Order Options", 4, List.of(SlotView.named(13, "Cancel Order", List.of()))));
        drive(engine, world, 120);
        assertEquals(1, clicks(actions, 13));
        engine.onNotice("[Bazaar] Cancelled buy order for 16x Enchanted Coal!");
        world.showing(orders(false, 0, 0, 4));
        drive(engine, world, 1000);
        String state = Files.readString(dir.resolve("orders.json"));
        assertTrue(state.contains("\"quantity\": 4"), state);
        assertTrue(state.contains("\"stage\": \"INVENTORY\""), state);
        assertEquals(400, market.capital.committed());
    }

    @Test void partiallySoldOfferRecordsProceedsAndReturnsOnlyUnsoldUnitsForRepricing() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(true, 16, 5, 0));
        seed(world, "SELL_ORDER", 16, 61000);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world); market.ask = 120;
        market.ledger.acquire("trade-1", "general", NAME, "trade-1:buy", 16, 1600.0);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        engine.onNotice("[Bazaar] Claimed 641.875 coins from selling 5x Enchanted Coal at 130 each!");
        world.showing(menu("Your Bazaar Orders", 0, List.of(
                SlotView.named(26, "Close", List.of()), SlotView.named(11, "SELL " + NAME,
                        List.of("Order amount: 16", "Filled: 5/16", "Click to view options!")))));
        drive(engine, world, 900);
        assertEquals(2, clicks(actions, 11));
        world.showing(menu("Sell Order Options", 0, List.of(SlotView.named(13, "Cancel Order", List.of()))));
        drive(engine, world, 120);
        engine.onNotice("[Bazaar] Cancelled sell order for 16x Enchanted Coal!");
        world.showing(orders(false, 0, 0, 11));
        drive(engine, world, 1000);
        String state = Files.readString(dir.resolve("orders.json"));
        assertTrue(state.contains("\"quantity\": 11"), state);
        assertTrue(state.contains("\"settlementPending\": false"), state);
        assertEquals(1, market.sales);
        assertEquals(141.875, market.ledger.summary().profit(), 0.000001);
        assertEquals(1100, market.capital.committed());
    }

    @Test void closedMenuAfterBuyClaimIsReopenedToVerifyTheInventory() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 16, 16, 0));
        seed(world, "BUY_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        var engine = engine(world, actions, new Market(world));
        drive(engine, world, 1200);
        actions.clear();
        world.showing(menu(null, 16, List.of()));
        drive(engine, world, 180);
        assertTrue(actions.serverEffects().contains("command:managebazaarorders"));
        world.showing(orders(false, 0, 0, 16));
        drive(engine, world, 1600);
        assertTrue(Files.readString(dir.resolve("orders.json")).contains("\"stage\": \"INVENTORY\""));
    }

    @Test void saleReceiptWithRemainingInventoryCannotSettleOrReleaseCapital() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(true, 16, 16, 0));
        seed(world, "SELL_ORDER", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        engine.onNotice("[Bazaar] Claimed 2054 coins from selling 16x Enchanted Coal at 130 each!");
        world.showing(orders(false, 0, 0, 1));
        drive(engine, world, 5500);
        assertEquals(0, market.sales);
        assertEquals(1600, market.capital.committed());
        assertTrue(engine.hasRetainedPositions());
    }

    @Test void transientUnreadablePurseWaitsAtBuyPriceAndConfirmationWithoutDroppingThePosition() throws Exception {
        FakeWorld world = new FakeWorld().showing(orders(false, 0, 0, 0));
        seed(world, "PLANNED", 16, 0);
        RecordingActions actions = new RecordingActions();
        Market market = new Market(world);
        var engine = engine(world, actions, market);
        drive(engine, world, 1200);
        world.showing(menu("Bazaar ➜ " + NAME, 0, List.of(SlotView.named(11, "Create Buy Order", List.of()))));
        drive(engine, world, 180);
        world.showing(menu("How much do you want to pay?", 0, List.of(SlotView.named(12, "Top order", List.of("Unit price: 100 coins")))));
        market.purse = -1;
        drive(engine, world, 1800);
        assertEquals(0, clicks(actions, 12));
        assertTrue(engine.hasRetainedPositions());
        market.purse = 100000;
        drive(engine, world, 120);
        assertEquals(1, clicks(actions, 12));
        world.showing(menu("Confirm Buy Order", 0, List.of(SlotView.named(13, NAME,
                List.of("Item: " + NAME, "Amount: 16", "Unit price: 100 coins")))));
        market.purse = -1;
        drive(engine, world, 1800);
        assertEquals(0, clicks(actions, 13));
        assertTrue(engine.hasRetainedPositions());
        market.purse = 100000;
        drive(engine, world, 120);
        assertEquals(1, clicks(actions, 13));
    }

    private SlotView icon(String id,String name) {
        return new SlotView(13,false,13,false,name,name,List.of(),id,null,1,64);
    }
    private void capturedSuperCompactor(boolean search) throws Exception {
        String id="SUPER_COMPACTOR_3000",name="Super Compactor 3000";
        GoofyConfig.INSTANCE.general.items=List.of(new GeneralItem(id,name));
        GoofyConfig.INSTANCE.general.maxItemsPerOrder=31;
        GoofyConfig.INSTANCE.general.maxCoinsPerItem=8_000_000;
        FakeWorld world=new FakeWorld().showing(orders(false,0,0,0));
        var actions=new RecordingActions();var market=new Market(world);
        market.productId=id;market.bid=148058.9;market.ask=219892.7;market.purse=91935802;
        market.capital.configure(35_000_000,15_000_000);
        var product=menu("Compactors ➜ Super Compactor 30",0,List.of(icon(id,name),
                SlotView.named(15,"Create Buy Order",List.of(name,"Top Orders:","Click to setup Buy Order!")),
                SlotView.named(16,"Create Sell Offer",List.of(name,"None in inventory!"))));
        GameActions server=new GameActions() {
            public void click(int slot,boolean shift){actions.click(slot,shift);if(slot==11)world.showing(product);}
            public void command(String text){actions.command(text);}public void closeMenu(){actions.closeMenu();}
            public void message(String text){actions.message(text);}public boolean writeSign(String text){return actions.writeSign(text);}
        };
        var engine=engine(world,server,market);drive(engine,world,1200);
        if(search) {
            world.showing(menu("Bazaar ➜ \"Super Compactor 3000\"",0,List.of(
                    new SlotView(11,false,11,false,name,name,List.of(),id,null,1,64))));
        } else world.showing(product);
        drive(engine,world,360);
        if(search)assertEquals(1,clicks(actions,11));
        assertEquals(1,clicks(actions,15));assertEquals(0,clicks(actions,16));assertEquals("QUANTITY",stage(engine));
        assertEquals(0,market.sales);assertTrue(engine.hasRetainedPositions());
    }
    @Test void capturedTruncatedSuperCompactorTitleContinuesAfterSearch() throws Exception {
        capturedSuperCompactor(true);
    }
    @Test void directTruncatedProductPageDoesNotRepeatSearchOrWaitForTheFullTitle() throws Exception {
        capturedSuperCompactor(false);
    }
    @Test void truncatedSellPageUsesTheSameVerifiedIdentityWithoutBuying() throws Exception {
        FakeWorld world=new FakeWorld().showing(orders(false,0,0,16));seed(world,"INVENTORY",16,0);
        var actions=new RecordingActions();var market=new Market(world);var engine=engine(world,actions,market);
        drive(engine,world,1200);
        world.showing(menu("Mining ➜ Enchanted Co",16,List.of(icon(ID,NAME),
                SlotView.named(15,"Create Buy Order",List.of(NAME)),SlotView.named(16,"Create Sell Offer",List.of(NAME)))));
        drive(engine,world,360);assertEquals(1,clicks(actions,16));assertEquals(0,clicks(actions,15));
        assertEquals("QUANTITY",stage(engine));
    }
    @Test void conflictingProductIdentityOrControlLoreNeverOpensAnOrder() throws Exception {
        FakeWorld world=new FakeWorld().showing(orders(false,0,0,0));
        var actions=new RecordingActions();var market=new Market(world);var engine=engine(world,actions,market);
        drive(engine,world,1200);
        world.showing(menu("Bazaar ➜ "+NAME,0,List.of(icon("OTHER_PRODUCT",NAME),SlotView.named(15,"Create Buy Order",List.of(NAME)))));
        drive(engine,world,240);assertEquals(0,clicks(actions,15));assertEquals(0,clicks(actions,13));
        world.showing(menu("Mining ➜ Enchanted Co",0,List.of(icon(ID,NAME),SlotView.named(15,"Create Buy Order",List.of("Other product")))));
        drive(engine,world,240);assertEquals(0,clicks(actions,15));
        world.showing(menu("Mining ➜ Enchanted Co",0,List.of(SlotView.named(15,"Create Buy Order",List.of(NAME)))));
        drive(engine,world,240);assertEquals(0,clicks(actions,15));
        world.showing(menu("Mining ➜ Enchanted Co",0,List.of(icon(ID,NAME),SlotView.named(15,"Create Buy Order",List.of(NAME)))));
        drive(engine,world,240);assertEquals(1,clicks(actions,15));assertEquals("QUANTITY",stage(engine));
    }
}
