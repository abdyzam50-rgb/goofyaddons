package com.goofy.goofyaddons.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryUtilsTest {
    @Test void liveDragGuardAllowsEmptyCellsAndCompatiblePartialTopUps() {
        assertTrue(InventoryUtils.validDragTarget(0,64,8,true,true));
        assertTrue(InventoryUtils.validDragTarget(8,64,8,true,true));
        assertTrue(InventoryUtils.validDragTarget(56,64,8,true,true));
    }
    @Test void liveDragGuardRejectsForeignComponentsFullSlotsAndDisallowedPlacement() {
        assertFalse(InventoryUtils.validDragTarget(8,64,8,false,true));
        assertFalse(InventoryUtils.validDragTarget(8,64,8,true,false));
        assertFalse(InventoryUtils.validDragTarget(60,64,8,true,true));
        assertFalse(InventoryUtils.validDragTarget(1,1,1,true,true));
        assertFalse(InventoryUtils.validDragTarget(0,64,0,true,true));
    }
}
