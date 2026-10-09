package com.goofy.goofyaddons.features.lifecycle;

import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Outcome;
import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradingLifecycleTest {
    private static TradingLifecycle running() {
        var lifecycle = new TradingLifecycle();
        assertEquals(Outcome.GRANTED, lifecycle.start(Source.MANUAL, 0));
        lifecycle.running();
        return lifecycle;
    }

    @Test void firstSafetyReasonStaysVisibleAndLaterOnesAreKeptBesideIt() {
        var lifecycle = running();
        lifecycle.block(Source.ENGINE, "Book claim quantity differs", 1);
        lifecycle.block(Source.ENGINE, "Cannot save book ownership journal", 2);
        assertTrue(lifecycle.paused());
        assertEquals("Book claim quantity differs", lifecycle.reason());
        assertTrue(lifecycle.diagnosticState().get("laterReasons").toString().contains("Cannot save book ownership journal"));
    }

    @Test void onlyAPersonClearsASafetyBlock() {
        for (Source automatic : new Source[] {Source.SCHEDULE, Source.TRANSFER, Source.REBOOT, Source.ENGINE}) {
            var lifecycle = running();
            lifecycle.block(Source.ENGINE, "Unexpected book confirmation type", 1);
            assertEquals(Outcome.DENIED, lifecycle.resume(automatic, 2), automatic.name());
            assertEquals(Outcome.DENIED, lifecycle.start(automatic, 3), automatic.name());
            assertTrue(lifecycle.blocked());
        }
        var lifecycle = running();
        lifecycle.block(Source.ENGINE, "Unexpected book confirmation type", 1);
        assertEquals(Outcome.GRANTED, lifecycle.resume(Source.REMOTE, 2));
        assertFalse(lifecycle.blocked());
        assertFalse(lifecycle.paused());
    }

    @Test void transferRestartNeedsAPlainTravelPause() {
        var lifecycle = running();
        assertEquals(Outcome.IGNORED, lifecycle.restartAfterTransfer(1), "nothing to restart while running");
        lifecycle.pause(Source.TRANSFER, 2);
        assertEquals(Outcome.GRANTED, lifecycle.restartAfterTransfer(3));
        lifecycle.block(Source.ENGINE, "Player contact received", 4);
        assertEquals(Outcome.DENIED, lifecycle.restartAfterTransfer(5));
    }

    @Test void stopWinsOverABlockButKeepsAReasonRecordedWhileStopped() {
        var lifecycle = running();
        lifecycle.block(Source.ENGINE, "Unexpected trading error", 1);
        assertEquals(Outcome.GRANTED, lifecycle.stop(Source.MANUAL, 2));
        assertFalse(lifecycle.started());
        assertFalse(lifecycle.blocked());

        lifecycle.block(Source.SCHEDULE, "Cannot arm scheduled sessions", 3);
        assertEquals(Outcome.IGNORED, lifecycle.stop(Source.SCHEDULE, 4));
        assertEquals("Cannot arm scheduled sessions", lifecycle.reason());
    }

    @Test void repeatedRequestsHaveDeterministicOutcomes() {
        var lifecycle = running();
        assertEquals(Outcome.IGNORED, lifecycle.start(Source.SCHEDULE, 1));
        assertEquals(Outcome.GRANTED, lifecycle.pause(Source.REBOOT, 2));
        assertEquals(Outcome.IGNORED, lifecycle.pause(Source.TRANSFER, 3));
        assertEquals(Outcome.GRANTED, lifecycle.resume(Source.REBOOT, 4));
        assertEquals(Outcome.IGNORED, lifecycle.resume(Source.MANUAL, 5));
        assertEquals(5, lifecycle.history().size() - 1);
    }

    @Test void refusalReplacesTheReasonWithoutStartingOrPausing() {
        var lifecycle = new TradingLifecycle();
        lifecycle.refuse(Source.MANUAL, "Config file rejected", 1);
        assertFalse(lifecycle.started());
        assertFalse(lifecycle.paused());
        assertEquals("Config file rejected", lifecycle.reason());
        assertEquals(Outcome.GRANTED, lifecycle.start(Source.MANUAL, 2), "a person may retry after fixing the file");
        assertEquals(Outcome.DENIED, lifecycle.start(Source.SCHEDULE, 3));
    }
}
