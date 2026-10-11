package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionWaitEvidenceTest {
    @Test void unchangedWaitReportsOnceAndProgressStartsANewWindow() {
        var evidence=new ProductionWaitEvidence();
        assertFalse(evidence.due("waiting order",0));assertFalse(evidence.due("waiting order",119999));
        assertTrue(evidence.due("waiting order",120000));assertFalse(evidence.due("waiting order",600000));
        assertFalse(evidence.due("claim verification",600001));
        assertTrue(evidence.due("claim verification",720001));
    }
}
