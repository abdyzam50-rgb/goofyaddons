package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The confirmation-time capital check used to halt with one message for four different causes,
 * so a field report could not say which fired. These pin each cause to its own name, and pin
 * that the diagnosis agrees with the decision {@link CapitalManager#resize} actually makes.
 */
class CapitalRefusalTest {
    private CapitalManager ledger(double limit, double reserve) {
        CapitalManager c = new CapitalManager();
        c.configure(limit, reserve);
        return c;
    }

    @Test void aFittingHoldIsNotRefused() {
        CapitalManager c = ledger(65_000_000, 15_000_000);
        assertTrue(c.reserve("books", "A", 10_000_000, 80_000_000));
        c.purchased("books", "A");
        assertNull(c.refusal("books", 12_000_000, 80_000_000));
    }

    @Test void aPurseUnderTheReserveNamesTheReserve() {
        CapitalManager c = ledger(65_000_000, 15_000_000);
        assertEquals("purse-below-reserve", c.refusal("A", 1_000_000, 14_999_999));
    }

    @Test void anUnreadablePurseNamesItself() {
        assertEquals("purse-unreadable", ledger(65_000_000, 15_000_000).refusal("A", 1_000_000, -1));
    }

    @Test void aHoldBeyondTheCapitalLimitNamesTheLimit() {
        CapitalManager c = ledger(20_000_000, 1_000_000);
        assertTrue(c.reserve("books", "A", 15_000_000, 100_000_000));
        c.purchased("books", "A");
        assertEquals("capital-limit-reached", c.refusal("B", 6_000_000, 100_000_000));
    }

    @Test void coinsStillPendingOnAnotherPositionNameThemselves() {
        CapitalManager c = ledger(300_000_000, 0);
        assertTrue(c.reserve("books", "A", 30_000_000, 40_000_000)); // placed but not yet settled
        assertEquals("purse-minus-pending-too-low", c.refusal("B", 20_000_000, 40_000_000));
    }

    @Test void theDiagnosisAgreesWithTheResizeDecision() {
        // Resizing an existing position must not count its own coins against itself, in either
        // the refusal diagnosis or the resize. Mutating one without the other breaks this.
        for (double hold : new double[]{5_000_000, 20_000_000, 40_000_000, 50_000_000, 62_000_000, 70_000_000}) {
            CapitalManager c = ledger(65_000_000, 15_000_000);
            assertTrue(c.reserve("books", "A", 20_000_000, 90_000_000));
            c.purchased("books", "A");
            String why = c.refusal("A", hold, 90_000_000);
            boolean resized = c.resize("books", "A", hold, 90_000_000);
            assertEquals(why == null, resized, "hold " + hold + " diagnosed " + why);
        }
    }

    @Test void resizingAPositionThatHasNotSettledYetDoesNotCountItsOwnPending() {
        // The field case: a book is outbid and re-ordered before the first order settled, so the
        // position is resized twice. Its own escrowed coins must not block the second resize.
        CapitalManager c = ledger(300_000_000, 15_000_000);
        assertTrue(c.reserve("books", "A", 30_000_000, 60_000_000)); // pending, not yet settled
        assertNull(c.refusal("A", 32_000_000, 60_000_000));
        assertTrue(c.resize("books", "A", 32_000_000, 60_000_000));
    }

    @Test void aRefusedResizeLeavesTheOldHoldInPlace() {
        CapitalManager c = ledger(65_000_000, 15_000_000);
        assertTrue(c.reserve("books", "A", 20_000_000, 90_000_000));
        c.purchased("books", "A");
        assertFalse(c.resize("books", "A", 70_000_000, 90_000_000));
        assertEquals(20_000_000, c.cost("books", "A"));
    }
}
