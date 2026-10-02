package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.menu.FakeWorld;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.RecordingActions;
import com.goofy.goofyaddons.menu.SlotView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first tests that run an engine and assert what it did to the world.
 *
 * <p>Most of this codebase's safety rules are of the form "do not click", and until the
 * seam existed none of them could be checked. These drive real ticks against a described
 * menu and assert on {@link RecordingActions#serverEffects()}, which excludes menu closes
 * and player messages so "changed nothing on the server" is expressible.
 */
class GeneralFlipperDrivingTest {
    @TempDir Path dir;

    @BeforeEach void configureWithFastDeterministicDelays() {
        GoofyConfig.INSTANCE = new GoofyConfig();
        // A fixed inter-action delay, so a tick loop advances without sleeping on a range.
        GoofyConfig.INSTANCE.minActionDelay = 51;
        GoofyConfig.INSTANCE.maxActionDelay = 52;
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
    }

    private GeneralFlipper engine(FakeWorld world, RecordingActions actions) throws Exception {
        Path state = dir.resolve("general-orders.json");
        // placedAt/heldSince must be recent: an epoch timestamp trips the holding-age
        // limit on the first tick, and the engine then fails before reaching the
        // order-identity checks these tests are about.
        long now = System.currentTimeMillis();
        Files.writeString(state, ("""
                [{"item":{"id":"ENCHANTED_COAL","name":"Enchanted Coal"},"quantity":16,"unitCost":100.0,
                  "sellPrice":130.0,"stage":"BUY_ORDER","submitted":true,"cancelRequested":false,"reprices":0,
                  "placedAt":NOW,"heldSince":NOW,"checkedAt":0,"tradeId":"trade-1","purchasePriceKnown":true,
                  "settlementPending":false}]""").replace("NOW", Long.toString(now)));
        GeneralFlipper engine = new GeneralFlipper(world, actions, () -> state);
        engine.start();
        return engine;
    }

    /** A menu of {@code containerSlots} container slots followed by 36 empty player slots. */
    private static MenuSnapshot menu(String title, int containerSlots, List<SlotView> placed) {
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) slots.add(SlotView.empty(i, false, i));
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(containerSlots + i, true, i));
        for (SlotView slot : placed) slots.set(slot.index(), slot);
        return new MenuSnapshot(42, title, true, slots);
    }

    /**
     * Whether the engine told the player it was stopping.
     *
     * <p>Needed because "no clicks" does not distinguish rejecting a bad order from
     * quietly accepting it: accepting one and moving on also performs no server action,
     * it just closes the menu. Only the safety message separates the two.
     */
    private static boolean raisedSafetyStop(RecordingActions actions) {
        return actions.performed().stream().anyMatch(action -> action.startsWith("message:"));
    }

    private static void tick(GeneralFlipper engine, int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            engine.onTick();
            Thread.sleep(60); // clears the fixed 51ms inter-action delay
        }
    }

    /**
     * Ticks for a wall-clock span. Needed because an orders list is only "ready" after
     * the same container has been observed for 750ms, and a transition restarts that
     * window, so a short tick count never reaches the decisions worth asserting on.
     */
    private static void drive(GeneralFlipper engine, long millis) throws InterruptedException {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            engine.onTick();
            Thread.sleep(60);
        }
    }

    @Test void constructionTouchesNothingAndAStoppedEngineActsOnNothing() {
        RecordingActions actions = new RecordingActions();
        GeneralFlipper engine = new GeneralFlipper(new FakeWorld().showingNothing(), actions,
                () -> dir.resolve("unused.json"));
        assertFalse(engine.isRunning());
        for (int i = 0; i < 5; i++) engine.onTick();
        assertEquals(List.of(), actions.performed(), "a stopped engine does nothing at all");
    }

    @Test void anEngineOutOfTheWorldActsOnNothing() {
        RecordingActions actions = new RecordingActions();
        GeneralFlipper engine = new GeneralFlipper(new FakeWorld().inWorld(false), actions,
                () -> dir.resolve("unused.json"));
        engine.onTick();
        engine.poll();
        assertEquals(List.of(), actions.performed());
    }

    @Test void aTrackedPositionIsLoadedAndTheEngineStarts() throws Exception {
        GeneralFlipper engine = engine(new FakeWorld().showingNothing(), new RecordingActions());
        assertTrue(engine.isRunning(), "a readable state file must not block startup");
        assertTrue(engine.hasRetainedPositions(), "the tracked order should have been restored");
        assertEquals("Retained: 16x Enchanted Coal", engine.retainedItem());
    }

    @Test void theWrongMenuNeverProducesAClick() throws Exception {
        // The engine wants its orders list; it is shown a product page instead.
        RecordingActions actions = new RecordingActions();
        FakeWorld world = new FakeWorld().showing(menu("Bazaar \u279c Enchanted Coal", 27,
                List.of(SlotView.named(11, "Create Buy Order", List.of("§7Click!")))));
        GeneralFlipper engine = engine(world, actions);

        drive(engine, 2500);

        assertEquals(List.of(), actions.serverEffects(),
                "an unexpected menu must never be clicked, however many ticks pass");
    }

    @Test void anOrdersListThatNeverSettlesIsNeverActedOn() throws Exception {
        // Correct title, but the menu is missing its Close/Go Back control, so it is
        // never "ready". The engine must wait rather than click into a half-loaded list.
        RecordingActions actions = new RecordingActions();
        FakeWorld world = new FakeWorld().showing(menu("Your Bazaar Orders", 27,
                List.of(SlotView.named(11, "BUY Enchanted Coal", List.of("§7Order amount: §a16", "§7Filled: §a0§7/§a16")))));
        GeneralFlipper engine = engine(world, actions);

        drive(engine, 2500);

        assertEquals(List.of(), actions.serverEffects(),
                "a list without its controls is not loaded, so nothing may be clicked");
    }

    @Test void aPaginatedOrdersListIsNeverActedOn() throws Exception {
        // Pagination means ownership cannot be established, so the engine must stop.
        RecordingActions actions = new RecordingActions();
        FakeWorld world = new FakeWorld().showing(menu("Your Bazaar Orders", 27, List.of(
                SlotView.named(11, "BUY Enchanted Coal", List.of("§7Order amount: §a16")),
                SlotView.named(26, "Close", List.of()),
                SlotView.named(25, "Next Page", List.of()))));
        GeneralFlipper engine = engine(world, actions);

        drive(engine, 2500);

        assertEquals(List.of(), actions.serverEffects(), "paginated orders must never be clicked");
    }

    @Test void anOrderWhoseAmountDiffersFromTheTrackedPositionIsNeverClicked() throws Exception {
        // The A06-shaped hazard on the general side: same name, wrong amount.
        RecordingActions actions = new RecordingActions();
        FakeWorld world = new FakeWorld().showing(menu("Your Bazaar Orders", 27, List.of(
                SlotView.named(11, "BUY Enchanted Coal", List.of("§7Order amount: §a64", "§7Filled: §a0§7/§a64")),
                SlotView.named(26, "Close", List.of()))));
        GeneralFlipper engine = engine(world, actions);

        drive(engine, 2500);

        assertEquals(List.of(), actions.serverEffects(),
                "an order of 64 is not this position's order of 16, so it must not be touched");
        assertTrue(raisedSafetyStop(actions),
                "the engine must refuse the mismatched order, not quietly adopt it and move on");
    }

    @Test void aCoopOrderBelongingToSomeoneElseIsNeverClicked() throws Exception {
        RecordingActions actions = new RecordingActions();
        FakeWorld world = new FakeWorld().username("GoofyPlayer").showing(menu("Co-op Bazaar Orders", 27, List.of(
                SlotView.named(11, "BUY Enchanted Coal",
                        List.of("§7Order amount: §a16", "§7Filled: §a0§7/§a16", "§7By: §bSomeoneElse")),
                SlotView.named(26, "Close", List.of()))));
        GeneralFlipper engine = engine(world, actions);

        drive(engine, 2500);

        assertEquals(List.of(), actions.serverEffects(),
                "another player's co-op order must never be claimed or cancelled");
        assertTrue(raisedSafetyStop(actions),
                "the engine must refuse someone else's order, not quietly adopt it");
    }

    @Test void anEngineWithNothingOnScreenAsksForItsOrdersAndClicksNothing() throws Exception {
        RecordingActions actions = new RecordingActions();
        GeneralFlipper engine = engine(new FakeWorld().showingNothing(), actions);

        tick(engine, 3);

        assertTrue(actions.performed().contains("command:managebazaarorders"),
                "with no menu open the engine should ask for its orders list");
        assertEquals(List.of("command:managebazaarorders"), actions.serverEffects(),
                "asking for the list is the only thing it may do without seeing one");
    }
}
