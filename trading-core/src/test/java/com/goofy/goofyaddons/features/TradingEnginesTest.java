package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TradingEnginesTest {
    private static class Engine implements Feature {
        boolean busy;
        public String name() { return "test"; }
        public void start() {}
        public void stop() {}
        public void pause() {}
        public void resume() {}
        public void onTick() {}
        public boolean isRunning() { return true; }
        public boolean canYield() { return !busy; }
    }

    @Test void queuedAuctionCannotPreemptAnInFlightBookTransaction() {
        var books = new Engine(); var general = new Engine();
        var crafting = new Engine(); var auction = new Engine();
        var engines = new TradingEngines(books, general, crafting, auction);
        var scheduler = new MenuScheduler();
        assertSame(books, scheduler.select(engines.enabled(TradingMode.BOOKS, false, false)));
        books.busy = true;
        assertSame(books, scheduler.select(engines.enabled(TradingMode.BOOKS, true, true)));
        books.busy = false;
        // A boundary allows the queued work to acquire the menu on the next selection.
        scheduler.reset();
        assertSame(auction, scheduler.select(engines.enabled(TradingMode.BOOKS, true, true)));
    }

    @Test void modesFilterAutomaticEnginesWithoutDroppingQueuedProduction() {
        var books = new Engine(); var general = new Engine();
        var crafting = new Engine(); var auction = new Engine();
        var engines = new TradingEngines(books, general, crafting, auction);
        assertEquals(List.of(auction, crafting, books), engines.enabled(TradingMode.BOOKS, true, true));
        assertEquals(List.of(auction, crafting, general), engines.enabled(TradingMode.GENERAL, true, true));
        assertEquals(List.of(books, general), engines.enabled(TradingMode.BOTH, false, false));
        assertThrows(IllegalArgumentException.class, () -> new TradingEngines(books, books, crafting, auction));
    }
}
