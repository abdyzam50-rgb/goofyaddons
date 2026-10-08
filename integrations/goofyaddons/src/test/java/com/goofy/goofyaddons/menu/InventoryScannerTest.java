package com.goofy.goofyaddons.menu;

import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryScannerTest {
    private final Book route=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private MenuSnapshot menu(SlotView... content) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++) slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var slot:content) slots.set(slot.index(),slot);
        return new MenuSnapshot(1,"Bazaar",true,slots);
    }
    @Test void queriesUseTheSuppliedSnapshotAndFollowTheNextTickWithoutNativeReads() {
        var observed=new AtomicReference<>(menu(SlotView.named(12,"BUY Overload I",
                List.of("Unit price: 1,234.5 coins","Filled: 6/16","You have 2 items to claim!"))));
        var scanner=new InventoryScanner(observed::get);
        assertEquals(List.of(12),scanner.findContainer("BUY Overload I"));
        assertEquals(1234.5,scanner.getUnitPrice(12));assertEquals(2,scanner.checkOrder(12));
        assertTrue(scanner.isMenuLoaded(12));
        observed.set(menu());
        assertTrue(scanner.findContainer("BUY Overload I").isEmpty());
        assertEquals(0,scanner.checkOrder(12));assertEquals(0,scanner.getUnitPrice(12));
        assertFalse(scanner.isMenuLoaded(12));
    }
    @Test void nativeIdentityAndMainInventoryBoundariesMatchTheExistingQueryRules() {
        var held=SlotView.enchantedBook(81,true,27,"overload",2,List.of("Overload II"),"Book");
        var armor=SlotView.enchantedBook(89,true,36,"overload",2,List.of("Overload II"),"Book");
        var lookalike=new SlotView(82,true,28,false,"Book","Book",List.of("Overload II"),
                "ENCHANTED_BOOK",Map.of("overload",2,"power",5),1,1);
        var scanner=new InventoryScanner(()->menu(held,armor,lookalike,
                SlotView.named(13,"Other",List.of("Overload II"))));
        assertEquals(List.of(81,89),scanner.matchingBookInInventory(route));
        assertEquals(List.of(81,82),scanner.findLoreInv("Overload II"));
        assertTrue(scanner.findLoreContainer("Overload II").isEmpty());
        assertEquals(2,scanner.getLevel(81));assertEquals(-1,scanner.getLevel(0));
        assertEquals(33,scanner.getEmptyInventorySlots(),"the equipment slot is excluded along with the two occupied main slots");
    }
    @Test void absentObservationCannotProduceAnActionableMenuOrManufactureAnItem() {
        var scanner=new InventoryScanner(()->null);
        assertFalse(scanner.isMenuLoaded(35));assertEquals(-1,scanner.getLevel(12));
        assertTrue(scanner.findContainer("BUY Overload I").isEmpty());
        assertTrue(scanner.matchingBookInInventory(route).isEmpty());
        assertEquals(0,scanner.getEmptyInventorySlots());assertEquals(0,scanner.getEmptyContainerSlots());
        assertEquals(0,scanner.getUnitPrice(12));assertFalse(scanner.findMisMatch("Overload I"));
    }
}
