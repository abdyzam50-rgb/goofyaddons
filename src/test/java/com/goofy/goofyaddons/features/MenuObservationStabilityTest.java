package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuObservationStabilityTest {
    @Test void incompleteOrChangingPreviewNeverBecomesReadyEarly() {
        var gate=new MenuObservationStability();
        assertFalse(gate.ready(1,"border loaded",false,0));
        assertFalse(gate.ready(1,"partial",true,100));
        assertFalse(gate.ready(1,"complete",true,500));
        assertFalse(gate.ready(1,"complete",true,1249));
        assertTrue(gate.ready(1,"complete",true,1250));
    }
    @Test void newContainerUnloadAndClockRollbackInvalidateReadiness() {
        var gate=new MenuObservationStability();
        assertFalse(gate.ready(1,"complete",true,1000));
        assertTrue(gate.ready(1,"complete",true,1750));
        assertFalse(gate.ready(2,"complete",true,1751));
        assertFalse(gate.ready(2,"complete",false,3000));
        assertFalse(gate.ready(2,"complete",true,3001));
        assertFalse(gate.ready(2,"complete",true,2000));
        gate.reset();assertFalse(gate.ready(2,"complete",true,4000));
    }
}
