package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSettleTest {
    @Test void aFreshlyOpenedContainerIsNeverImmediatelyTrusted() {
        MenuSettle settle = new MenuSettle();
        assertFalse(settle.settled(7, 1000));
        assertFalse(settle.settled(7, 1749));
        assertTrue(settle.settled(7, 1750));
    }

    @Test void aDifferentContainerRestartsTheWindow() {
        MenuSettle settle = new MenuSettle();
        assertFalse(settle.settled(7, 1000));
        assertTrue(settle.settled(7, 2000));
        assertFalse(settle.settled(8, 2000), "a new container must re-settle");
        assertFalse(settle.settled(8, 2749));
        assertTrue(settle.settled(8, 2750));
    }

    @Test void resetForcesTheNextObservationToSettleAgain() {
        MenuSettle settle = new MenuSettle();
        assertFalse(settle.settled(7, 1000));
        assertTrue(settle.settled(7, 2000));
        settle.reset();
        assertEquals(-1, settle.container());
        assertFalse(settle.settled(7, 2000));
        assertTrue(settle.settled(7, 2750));
    }

    @Test void theObservedContainerIsReportedForDiagnostics() {
        MenuSettle settle = new MenuSettle();
        assertEquals(-1, settle.container());
        settle.settled(42, 1000);
        assertEquals(42, settle.container());
    }
}
