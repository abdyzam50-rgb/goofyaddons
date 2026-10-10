package com.goofy.goofyaddons.features.production;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftPortfolioInventoryTest {
    @Test void ownedStacksReplaceFutureReservationsAndTheClaimBufferIsSharedOnce() {
        var menu=ProductionRunTest.menu(null,ProductionRunTest.item(54,"INPUT_A",64),ProductionRunTest.item(55,"INPUT_A",64),
            ProductionRunTest.item(56,"INPUT_A",64),ProductionRunTest.item(57,"INPUT_A",64),
            ProductionRunTest.item(58,"INPUT_B",64),ProductionRunTest.item(59,"INPUT_B",64));
        var allocations=List.of(new CraftPortfolioInventory.Allocation(12,Set.of("INPUT_A","OUTPUT_A")),
            new CraftPortfolioInventory.Allocation(8,Set.of("INPUT_B","OUTPUT_B")));
        assertEquals(18,CraftPortfolioInventory.reserved(menu,allocations));
        assertEquals(12,menu.emptyInventorySlots()-CraftPortfolioInventory.reserved(menu,allocations));
        assertEquals(4,CraftPortfolioInventory.reserved(menu,List.of()));
    }
    @Test void finishedCraftNeedsOnlyOutputSpaceAndUnrelatedItemsNeverReduceItsReservation() {
        var menu=ProductionRunTest.menu(null,ProductionRunTest.item(54,"OUTPUT_A",1),ProductionRunTest.item(55,"UNRELATED",64));
        assertEquals(4,CraftPortfolioInventory.reserved(menu,List.of(new CraftPortfolioInventory.Allocation(1,Set.of("OUTPUT_A")))));
        assertEquals(16,CraftPortfolioInventory.reserved(menu,List.of(new CraftPortfolioInventory.Allocation(12,Set.of("INPUT_B")))));
        assertEquals(36,CraftPortfolioInventory.reserved(null,List.of()));
    }
}
