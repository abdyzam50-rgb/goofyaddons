package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookRecoveryPlanTest {
    private final Book book=new Book("ENCHANTMENT_ULTIMATE_WISDOM",1,5,"Wisdom",0,0);
    private final BookPosition saved=new BookPosition(book,1600,"saved-trade");
    private MenuSnapshot menu(int page,SlotView... extra) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var slot:extra){if(slot.index()==slots.size())slots.add(slot);else slots.set(slot.index(),slot);}
        return new MenuSnapshot(page,page==0?null:page<3?"Ender Chest ("+page+"/3)":"Your Bazaar Orders",true,slots);
    }
    private Map<Integer,MenuSnapshot> pages(SlotView... inventory) {
        var pages=new HashMap<Integer,MenuSnapshot>();for(int i=0;i<4;i++)pages.put(i,menu(i,inventory));return pages;
    }
    private SlotView physical(int index,boolean inventory,int level) {
        return SlotView.enchantedBook(index,inventory,inventory?index-54:index,"ultimate_wisdom",level,List.of(),book.getRomanLevel(level));
    }
    private SlotView order(boolean sell,String filled,int quantity) {
        return SlotView.named(11,(sell?"SELL ":"BUY ")+book.getRomanLevel(sell?5:1),
                List.of("Order amount: "+quantity+"x","Filled: "+filled,"Price per unit: 100 coins"));
    }
    private BookRecoveryPlan plan(Map<Integer,MenuSnapshot> pages) {return BookRecoveryPlan.build(List.of(saved),pages,"Tester");}
    @Test void completedInventoryBookResumesSellingWithoutAnotherPurchase() {
        var p=plan(pages(physical(54,true,5)));var task=p.routes().getFirst().task();
        assertEquals(Task.BookState.ANVIL,task.getBookState());assertEquals(0,task.getAmountToOrder());
        assertEquals("saved-trade",task.getProfitTradeId());assertEquals(16,p.routes().getFirst().acquiredUnits());
        assertEquals(0,task.bookList.getFirst().slot);assertTrue(p.extras().isEmpty());
    }
    @Test void twoStoredIntermediateBooksResumeCombiningAndKeepTheirExactSlots() {
        var pages=pages();pages.put(1,menu(1,physical(10,false,4)));pages.put(2,menu(2,physical(12,false,4)));
        var p=plan(pages);var task=p.routes().getFirst().task();
        assertEquals(Task.BookState.ANVIL,task.getBookState());assertEquals(0,task.getAmountToOrder());
        assertEquals(List.of(10,12),task.bookList.stream().map(b->b.slot).toList());
        assertEquals(List.of(1,2),task.bookList.stream().map(b->b.location).toList());
    }
    @Test void partialHoldingsReduceTheRemainingPurchaseRequirement() {
        var task=plan(pages(physical(54,true,4))).routes().getFirst().task();
        assertEquals(8,task.getAmountToOrder());assertEquals(Task.BookState.SELECTED,task.getBookState());
        assertEquals(Task.ActionSchedule.SELECTED_STORE_BUYORDER,task.actionSchedule);
    }
    @Test void partialBuyOrderEntersClaimCancelFlowBeforeAnyNewPurchase() {
        var pages=pages(physical(54,true,1));pages.put(3,menu(3,physical(54,true,1),order(false,"4/16",16)));
        var p=plan(pages);assertEquals(Task.BookState.OUTBID,p.routes().getFirst().task().getBookState());
        assertEquals(15,p.routes().getFirst().task().getAmountToOrder());assertEquals(1,p.routes().getFirst().acquiredUnits());
        assertEquals(100.0,p.routes().getFirst().orderPrice());
    }
    @Test void completedSellOfferResumesCollectionAndUnfilledOfferResumesMonitoring() {
        for(var filled:List.of("0/1","1/1")) {
            var pages=pages();pages.put(3,menu(3,order(true,filled,1)));var p=plan(pages);var task=p.routes().getFirst().task();
            assertEquals(filled.equals("1/1")?Task.BookState.REPLACE_SELL:Task.BookState.VERIFY_ORDER,task.getBookState());
            assertTrue(task.awaitingSale());assertTrue(task.bookList.isEmpty());assertEquals(16,p.routes().getFirst().acquiredUnits());
        }
    }
    @Test void extrasAreTrackedSeparatelyRatherThanPretendingTheyAreNewZeroCostInputs() {
        var p=plan(pages(physical(54,true,5),physical(55,true,4)));
        assertEquals(1,p.extras().size());assertEquals(1,p.extras().getFirst().slot);assertFalse(p.extras().getFirst().found);
        assertEquals(0,p.routes().getFirst().task().getAmountToOrder());
    }
    @Test void legacyPositionGetsAnIdentityWhileStalePositionsProduceNoTasks() {
        var p=BookRecoveryPlan.build(List.of(new BookPosition(book,1600)),pages(physical(54,true,5)),"Tester");
        assertFalse(p.routes().getFirst().task().getProfitTradeId().isBlank());assertTrue(plan(pages()).routes().isEmpty());
    }
    @Test void duplicateOrMixedBuySellOrdersDoNotProduceAnExecutablePlan() {
        var pages=pages();var buy=order(false,"0/16",16);
        var duplicate=SlotView.named(12,buy.hoverName(),buy.loreLines());pages.put(3,menu(3,buy,duplicate));
        assertThrows(IllegalStateException.class,()->plan(pages));
        pages.put(3,menu(3,buy,SlotView.named(12,order(true,"0/1",1).hoverName(),order(true,"0/1",1).loreLines())));
        assertThrows(IllegalStateException.class,()->plan(pages));
    }
    @Test void unsupportedAmountMissingPriceAndWrongOrderLevelKeepRecoveryBlocked() {
        var pages=pages();
        for(var slot:List.of(order(true,"0/2",2),SlotView.named(11,"BUY Wisdom I",List.of("Filled: 0/16")),
                SlotView.named(11,"BUY Wisdom II",List.of("Filled: 0/8","Price per unit: 100 coins")))) {
            pages.put(3,menu(3,slot));assertThrows(IllegalStateException.class,()->plan(pages));
        }
    }
    @Test void changedInventoryAndOffhandHoldingsCannotBeBlindlyAdopted() {
        var changed=pages(physical(54,true,5));changed.put(3,menu(3));assertThrows(IllegalStateException.class,()->plan(changed));
        var offhand=pages();offhand.put(0,menu(0,SlotView.enchantedBook(90,true,40,"ultimate_wisdom",5,List.of(),"Wisdom V")));assertThrows(IllegalStateException.class,()->plan(offhand));
    }
    @Test void foreignCoopOrderIsIgnoredButUnreadableOwnershipBlocksAdoption() {
        var pages=pages();var other=SlotView.named(11,"BUY Wisdom I",List.of("By: SomeoneElse"));
        var base=menu(3,other);pages.put(3,new MenuSnapshot(3,"Co-op Bazaar Orders",true,base.slots()));
        assertTrue(plan(pages).routes().isEmpty());
        base=menu(3,order(false,"0/16",16));pages.put(3,new MenuSnapshot(3,"Co-op Bazaar Orders",true,base.slots()));
        assertThrows(IllegalStateException.class,()->plan(pages));
    }
    @Test void additionalFullyHeldCyclesFinishWithoutPurchasesWhilePartialExtrasWait() {
        var a=new BookList(book,4,1);a.slot=10;
        var b=new BookList(book,4,2);b.slot=12;
        assertNull(BookRecoveryPlan.completedExtraCycle(book,List.of(a),100));
        var task=BookRecoveryPlan.completedExtraCycle(book,List.of(a,b),100);
        assertNotNull(task);assertEquals(0,task.getAmountToOrder());assertEquals(Task.BookState.ANVIL,task.getBookState());
        assertEquals(List.of(10,12),task.bookList.stream().map(h->h.slot).toList());
        assertTrue(task.recovered());assertNotEquals("saved-trade",task.getProfitTradeId());
    }
    @Test void theSameStoragePageCannotBeCountedTwice() {
        var pages=pages();pages.put(2,menu(1,physical(10,false,4)));
        assertThrows(IllegalStateException.class,()->plan(pages));
    }
    @Test void restartingARetiringPositionPreservesItsExitIntentAndStaleClock() {
        var position=new BookPosition(book,1600,"saved-trade",true,1000,false);
        var plan=BookRecoveryPlan.build(List.of(position),pages(physical(54,true,1)),"Tester");
        var task=plan.routes().getFirst().task();assertTrue(task.retiring());assertEquals(1000,task.progressAt());
        assertEquals("saved-trade",task.getProfitTradeId());assertEquals(1,task.bookList.size());
        var orphan=new BookPosition(book,1600,"saved-trade",true,1000,true);
        assertTrue(BookRecoveryPlan.build(List.of(orphan),pages(physical(54,true,1)),"Tester").routes().getFirst().task().orphanCleanup());
    }
}
