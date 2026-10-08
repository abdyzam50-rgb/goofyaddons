package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CombinedTradingTest {
    private static class Engine implements Feature {
        boolean running = true;
        boolean busy;
        boolean work = true;
        public String name() { return "fake"; }
        public void start() { running = true; }
        public void stop() { running = false; }
        public void pause() {}
        public void resume() {}
        public void onTick() {}
        public boolean isRunning() { return running; }
        public boolean canYield() { return !busy; }
        public boolean needsMenu() { return work; }
    }

    @Test void bothEnginesReceiveTurnsButTransactionsRetainExclusiveOwnership() {
        MenuScheduler scheduler = new MenuScheduler();
        Engine books = new Engine();
        Engine general = new Engine();
        List<Feature> engines = List.of(books, general);
        assertSame(books, scheduler.select(engines));
        books.busy = true;
        assertFalse(scheduler.canSwitch());
        assertSame(books, scheduler.select(engines));
        books.busy = false;
        assertTrue(scheduler.canSwitch());
        assertSame(general, scheduler.select(engines));
        assertSame(books, scheduler.select(engines));
    }

    @Test void WaitingEngineDoesNotPreventOtherEngineFromWorking() {
        MenuScheduler scheduler = new MenuScheduler();
        Engine books = new Engine(); books.work = false;
        Engine general = new Engine();
        assertSame(general, scheduler.select(List.of(books, general)));
        general.work = false;
        assertNull(scheduler.select(List.of(books, general)));
    }

    @Test void StoppedOwnerCanBeRemovedAndModesCycleInOrder() {
        MenuScheduler scheduler = new MenuScheduler();
        Engine books = new Engine(); Engine general = new Engine();
        scheduler.select(List.of(books, general));
        books.busy = true; books.stop();
        assertTrue(scheduler.canSwitch());
        assertSame(general, scheduler.select(List.of(general)));
        assertEquals(TradingMode.GENERAL, TradingMode.BOOKS.next());
        assertEquals(TradingMode.BOTH, TradingMode.GENERAL.next());
        assertEquals(TradingMode.BOOKS, TradingMode.BOTH.next());
    }

    @Test void EnginesShareTheSamePurseReservationsAndCapitalCap() {
        CapitalManager capital = new CapitalManager();
        capital.configure(100, 20);
        assertTrue(capital.reserve("books", "WISE", 60, 100));
        assertEquals(20, capital.available(100));
        assertFalse(capital.reserve("general", "SUGAR", 30, 100));
        assertTrue(capital.reserve("general", "SUGAR", 20, 100));
        assertEquals(0, capital.available(100));
        capital.purchased("books", "WISE");
        // The purchase is already deducted in the new 40-coin purse.
        assertEquals(0, capital.available(40));
        capital.release("general", "SUGAR");
        assertEquals(20, capital.available(40));
    }

    @Test void ExistingPositionsAndQuoteChangesCannotStealOtherOwnersCapital() {
        CapitalManager capital = new CapitalManager();
        capital.configure(100, 0);
        capital.restore("general", "SUGAR", 70, false);
        assertEquals(30, capital.available(100));
        assertFalse(capital.reserve("books", "SUGAR", 10, 100));
        assertTrue(capital.reserve("books", "WISE", 25, 100));
        assertFalse(capital.resize("books", "WISE", 40, 100));
        assertEquals(25, capital.cost("books", "WISE"));
        capital.release("books", "SUGAR");
        assertTrue(capital.owns("general", "SUGAR"));
    }

    @Test void RecheckingAPlacedOrderDoesNotRestartThePurchaseSettleWindow() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(10_000);
        CapitalManager capital = new CapitalManager(now::get);
        capital.configure(100, 0);
        assertFalse(capital.purchaseSettling());
        assertTrue(capital.reserve("general", "SUGAR", 50, 100));
        capital.purchased("general", "SUGAR");
        assertTrue(capital.purchaseSettling());
        now.addAndGet(1000);
        assertFalse(capital.purchaseSettling());
        // Periodic order inspections call purchased again for the same placed order.
        capital.purchased("general", "SUGAR");
        assertFalse(capital.purchaseSettling());
        assertEquals(50, capital.cost("general", "SUGAR"));
    }
}
