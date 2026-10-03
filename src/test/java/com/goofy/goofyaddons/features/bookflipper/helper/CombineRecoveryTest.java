package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.goofy.goofyaddons.features.bookflipper.helper.CombineRecovery.Action.*;

/** The bound that turns a 110-iteration spin into one re-read of the Bazaar. */
class CombineRecoveryTest {
    @Test void aBriefAbsenceIsJustTheMenuCatchingUp() {
        for (int tick = 1; tick < 10; tick++) {
            assertEquals(WAIT, CombineRecovery.decide(tick, 10, false), "tick " + tick);
        }
    }

    @Test void aPersistentAbsenceSendsTheTaskBackToTheBazaar() {
        assertEquals(RECONCILE, CombineRecovery.decide(10, 10, false));
        assertEquals(RECONCILE, CombineRecovery.decide(99, 10, false));
    }

    @Test void aSecondDivergenceOnTheSameTaskStopsInstead() {
        assertEquals(HALT, CombineRecovery.decide(10, 10, true));
    }

    @Test void havingReconciledBeforeDoesNotShortenTheSettleWindow() {
        // Otherwise one slow menu read after a reconcile halts a run that was fine.
        assertEquals(WAIT, CombineRecovery.decide(1, 10, true));
        assertEquals(WAIT, CombineRecovery.decide(9, 10, true));
    }

    @Test void itNeverWaitsForever() {
        for (boolean reconciled : new boolean[]{false, true}) {
            assertNotEquals(WAIT, CombineRecovery.decide(Integer.MAX_VALUE, 10, reconciled));
        }
    }
}
