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
    @Test void onlyAnExactBookVariantOccupiesItsCraftWorkspaceReservation() {
        var allocation=List.of(new CraftPortfolioInventory.Allocation(1,Set.of("ENCHANTMENT_QUICK_BITE_1")));
        var exact=com.goofy.goofyaddons.menu.SlotView.enchantedBook(54,true,0,"quick_bite",1,List.of(),"Enchanted Book");
        var higher=com.goofy.goofyaddons.menu.SlotView.enchantedBook(54,true,0,"quick_bite",2,List.of(),"Enchanted Book");
        var combined=new com.goofy.goofyaddons.menu.SlotView(54,true,0,false,"Enchanted Book","Enchanted Book",List.of(),
            com.goofy.goofyaddons.menu.SlotView.ENCHANTED_BOOK,Map.of("quick_bite",1,"sharpness",5),1,1);
        assertEquals(4,CraftPortfolioInventory.reserved(ProductionRunTest.menu(null,exact),allocation),
            "An owned exact output replaces its future slot reservation");
        assertEquals(5,CraftPortfolioInventory.reserved(ProductionRunTest.menu(null,higher),allocation));
        assertEquals(5,CraftPortfolioInventory.reserved(ProductionRunTest.menu(null,combined),allocation));
    }
}
