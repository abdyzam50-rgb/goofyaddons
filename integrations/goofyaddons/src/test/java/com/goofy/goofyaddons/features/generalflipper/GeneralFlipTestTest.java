package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.menu.FakeWorld;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.RecordingActions;
import com.goofy.goofyaddons.menu.SlotView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** A player-started general flip test: one product, its own buy and sell modes, outside the profit filters. */
class GeneralFlipTestTest {
    @TempDir Path dir;
    static final GeneralItem COAL = new GeneralItem("ENCHANTED_COAL", "Enchanted Coal");

    @BeforeEach void config() {
        GoofyConfig.INSTANCE = new GoofyConfig();
        GoofyConfig.INSTANCE.minActionDelay = 51; GoofyConfig.INSTANCE.maxActionDelay = 52;
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
    }

    /** Quotes where every strategy loses money, so only a test may trade them. */
    static final class Services extends ConfiguredTestServices {
        JsonObject quotes = JsonParser.parseString("""
                {"lastUpdated":NOW,"products":{"ENCHANTED_COAL":{
                  "sell_summary":[{"pricePerUnit":100.0,"amount":100000,"orders":5}],
                  "buy_summary":[{"pricePerUnit":101.0,"amount":100000,"orders":5}],
                  "quick_status":{"buyMovingWeek":1000000,"sellMovingWeek":1000000}}}}"""
                .replace("NOW", Long.toString(System.currentTimeMillis()))).getAsJsonObject();
        final List<String> events = new ArrayList<>(), acquired = new ArrayList<>(), sold = new ArrayList<>();
        @Override public double purse() { return 100_000_000; }
        @Override public JsonObject latestQuotes() { return quotes; }
        @Override public void acquire(GeneralPosition position) { acquired.add(position.item.id()); }
        @Override public void sell(GeneralPosition position, int units, Double proceeds) { sold.add(position.item.id() + ":" + units); }
        @Override public void event(String level, String type, java.util.Map<String, ?> details) { events.add(type); }
    }

    static MenuSnapshot inventory(int coal) {
        var slots = new ArrayList<SlotView>();
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(i, false, i));
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(36 + i, true, i));
        if (coal > 0) slots.set(36, new SlotView(36, true, 0, false, COAL.id(), COAL.name(), List.of(), COAL.id(), null, coal, 64));
        return new MenuSnapshot(1, null, true, slots);
    }

    Path state;
    GeneralFlipper engine(FakeWorld world, RecordingActions actions, Services services) {
        state = dir.resolve("general-orders.json");
        return new GeneralFlipper(world, actions, new JsonGeneralOrderRepository(() -> state), services);
    }

    @Test void anUnprofitableBuyAndSellTestStartsOneSavedTestPositionAndPicksNothingElse() throws Exception {
        var services = new Services(); var world = new FakeWorld().showing(inventory(0)); var actions = new RecordingActions();
        var engine = engine(world, actions, services);
        assertNull(engine.startTest(COAL, 16, true, true, GeneralFlipper.TestPart.BOTH));
        assertTrue(engine.isRunning()); assertTrue(engine.testing()); assertFalse(engine.testFinished());
        var saved = Files.readString(state);
        assertTrue(saved.contains("\"test\": \"BOTH\""), saved);
        var reloaded = new JsonGeneralOrderRepository(() -> state).load();
        assertEquals(1, reloaded.size()); assertEquals(16, reloaded.getFirst().quantity); assertTrue(reloaded.getFirst().instantBuy);
        assertTrue(reloaded.getFirst().maximumBuyPrice >= 101 * 1.05 - 1e-9, "a buy may follow the quote by 5%");
        assertTrue(services.events.contains("general.test_started"));
        assertNotNull(engine.startTest(COAL, 16, true, true, GeneralFlipper.TestPart.BOTH), "a running engine refuses a second test");
    }

    @Test void refusalsExplainAndChangeNothing() throws Exception {
        var services = new Services(); var world = new FakeWorld().showing(inventory(0));
        var engine = engine(world, new RecordingActions(), services);
        assertTrue(engine.startTest(COAL, 1, true, true, GeneralFlipper.TestPart.SELL).contains("Put the Enchanted Coal"));
        assertTrue(engine.startTest(new GeneralItem("UNKNOWN_ITEM", "Unknown"), 1, true, true, GeneralFlipper.TestPart.BOTH).contains("No fresh Bazaar quote"));
        assertTrue(engine.startTest(COAL, 5000, true, true, GeneralFlipper.TestPart.BUY).contains("between 1 and 4096"));
        var held = engine(new FakeWorld().showing(inventory(3)), new RecordingActions(), services);
        assertTrue(held.startTest(COAL, 1, true, true, GeneralFlipper.TestPart.BOTH).contains("already hold"));
        assertFalse(engine.isRunning()); assertFalse(Files.exists(state));
    }

    @Test void aSellTestOwnsEveryHeldUnitAndKeepsItOutOfTheProfitLedger() throws Exception {
        var services = new Services(); var world = new FakeWorld().showing(inventory(40)); var actions = new RecordingActions();
        var engine = engine(world, actions, services);
        assertNull(engine.startTest(COAL, 0, false, false, GeneralFlipper.TestPart.SELL));
        var position = new JsonGeneralOrderRepository(() -> state).load().getFirst();
        assertEquals(40, position.quantity); assertEquals(GeneralPosition.Stage.INVENTORY, position.stage); assertFalse(position.purchasePriceKnown);
        assertEquals(90.9, position.minimumSellPrice, 1e-9, "a test never sells far under the quote it started from");
    }

    @Test void aBoughtBuyTestFinishesWithTheUnitsLeftInTheInventory() throws Exception {
        var services = new Services(); var world = new FakeWorld().showing(inventory(0)); var actions = new RecordingActions();
        var engine = engine(world, actions, services);
        assertNull(engine.startTest(COAL, 8, false, true, GeneralFlipper.TestPart.BUY));
        var positions = field(engine, "positions", List.class);
        var position = (GeneralPosition) positions.getFirst();
        // As after a verified claim: the units are held and recorded.
        position.stage = GeneralPosition.Stage.INVENTORY; position.purchasePriceKnown = true; position.unitCost = 100;
        world.showing(inventory(8));
        engine.onTick();
        assertTrue(engine.testFinished()); assertTrue(positions.isEmpty());
        assertTrue(services.acquired.isEmpty() && services.sold.isEmpty(), "a one-sided test stays out of the profit ledger");
        assertTrue(actions.performed().stream().anyMatch(a -> a.startsWith("message:General test: bought 8 Enchanted Coal")), actions.performed().toString());
        assertTrue(actions.serverEffects().isEmpty(), "finishing a bought test clicks nothing");
    }

    @Test void aTestBuyOrderSkipsTheProfitFilterButNotItsPriceCeilingOrCapital() throws Exception {
        var services = new Services(); var world = new FakeWorld().showing(inventory(0));
        var engine = engine(world, new RecordingActions(), services);
        assertNull(engine.startTest(COAL, 16, false, false, GeneralFlipper.TestPart.BOTH));
        var position = (GeneralPosition) field(engine, "positions", List.class).getFirst();
        set(engine, "active", position);
        var session = field(engine, "session", GeneralContext.class);
        // 100.1 loses money after tax against a 101 exit, so a normal flip would drop the position.
        assertTrue(new GeneralBuy().acceptPrice(session, 100.1));
        assertEquals(100.1, position.unitCost, 1e-9);
        assertFalse(new GeneralBuy().acceptPrice(session, 200), "far above the quote it started from");
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var f = GeneralFlipper.class.getDeclaredField(name); f.setAccessible(true); return (T) f.get(owner);
    }
    static void set(Object owner, String name, Object value) throws Exception {
        var f = GeneralFlipper.class.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
}
