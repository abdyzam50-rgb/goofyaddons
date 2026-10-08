package com.goofy.goofyaddons.features.transaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimEvidenceTest {
    @Test void exactIncreaseIsTheOnlyExactOutcome() {
        assertEquals(ClaimEvidence.Outcome.PENDING, ClaimEvidence.observe(3, 16, 18));
        assertEquals(ClaimEvidence.Outcome.EXACT, ClaimEvidence.observe(3, 16, 19));
        assertEquals(ClaimEvidence.Outcome.EXCESS, ClaimEvidence.observe(3, 16, 20));
    }
    @Test void arrivalNeedsSomeIncreaseAndAtLeastTheExpectedUnits() {
        assertFalse(ClaimEvidence.arrived(5, 0, 5), "an unchanged inventory is not an arrival");
        assertTrue(ClaimEvidence.arrived(5, 0, 6));
        assertFalse(ClaimEvidence.arrived(5, 4, 8));
        assertTrue(ClaimEvidence.arrived(5, 4, 9));
        assertTrue(ClaimEvidence.arrived(5, 4, 12));
    }
    @Test void outstandingOnlyWhileUnitsAreStillExpected() {
        assertFalse(ClaimEvidence.outstanding(5, 0, 0));
        assertTrue(ClaimEvidence.outstanding(5, 4, 8));
        assertFalse(ClaimEvidence.outstanding(5, 4, 9));
    }
}
