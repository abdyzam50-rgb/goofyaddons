package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookTransferTest {
    private final Book route=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private SlotView book(int slot,boolean inventory) {
        return SlotView.enchantedBook(slot,inventory,inventory?slot-54:slot,"overload",1,List.of("Overload I"),"Enchanted Book");
    }
    private MenuSnapshot menu(int id,String title,SlotView... books) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++) slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var book:books) slots.set(book.index(),book);
        return new MenuSnapshot(id,title,true,slots);
    }
    @Test void sourceDisappearanceWaitsForDestinationWithoutRepeatedClicks() {
        var entry=new BookList(route,1,0);var transfer=new BookTransfer();var actions=new RecordingActions();
        transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(54,true)),actions,1000);
        for(int i=0;i<4;i++) assertEquals(BookTransfer.Result.WAITING,
                transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)"),actions,1500+i*100));
        assertEquals(0,entry.location);assertEquals(List.of("shiftclick:54"),actions.serverEffects());
        assertEquals(BookTransfer.Result.MOVED,transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(10,false)),actions,2500));
        assertEquals(1,entry.location);
    }
    @Test void storeThenRetrieveUsesTheSameVerifiedLocationModel() {
        var entry=new BookList(route,1,0);var transfer=new BookTransfer();var actions=new RecordingActions();
        transfer.tick(entry,2,"ec 2",menu(2,"Ender Chest (2/3)",book(54,true)),actions,1000);
        transfer.tick(entry,2,"ec 2",menu(2,"Ender Chest (2/3)",book(11,false)),actions,2000);
        transfer.tick(entry,0,"ec 2",menu(3,"Ender Chest (2/3)",book(11,false)),actions,3000);
        assertEquals(BookTransfer.Result.MOVED,transfer.tick(entry,0,"ec 2",menu(3,"Ender Chest (2/3)",book(55,true)),actions,4000));
        assertEquals(0,entry.location);assertEquals(List.of("shiftclick:54","shiftclick:11"),actions.serverEffects());
    }
    @Test void wrongPageAndContainerReplacementPreserveTheRecordedLocation() {
        var entry=new BookList(route,1,0);var actions=new RecordingActions();var transfer=new BookTransfer();
        assertEquals(BookTransfer.Result.BLOCKED,transfer.tick(entry,2,"ec 2",menu(1,"Ender Chest (1/3)",book(54,true)),actions,1000));
        assertTrue(actions.serverEffects().isEmpty());assertEquals(0,entry.location);
        transfer.reset();transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(54,true)),actions,2000);
        actions.clear();
        assertEquals(BookTransfer.Result.BLOCKED,transfer.tick(entry,1,"ec",menu(2,"Ender Chest (1/3)",book(10,false)),actions,3000));
        assertEquals(0,entry.location);assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void missingSourceNeverManufacturesACompletedTransfer() {
        var entry=new BookList(route,1,1);var actions=new RecordingActions();
        assertEquals(BookTransfer.Result.BLOCKED,new BookTransfer().tick(entry,0,"ec",menu(1,"Ender Chest (1/3)"),actions,1000));
        assertEquals(1,entry.location);assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void delayedTransferHasABoundedFailureAndRetainsOwnership() {
        var entry=new BookList(route,1,0);var transfer=new BookTransfer();var actions=new RecordingActions();
        transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(54,true)),actions,1000);
        assertEquals(BookTransfer.Result.BLOCKED,transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)"),actions,31000));
        assertEquals(0,entry.location);assertEquals(1,actions.serverEffects().size());
    }
    @Test void rejectedStoreRetriesItsOriginalSlotThenAcknowledgesArrival() {
        var entry=new BookList(route,1,0);var transfer=new BookTransfer();var actions=new RecordingActions();
        var unchanged=menu(1,"Ender Chest (1/3)",book(54,true));
        transfer.tick(entry,1,"ec",unchanged,actions,1000);actions.clear();
        transfer.tick(entry,1,"ec",unchanged,actions,4000);
        transfer.tick(entry,1,"ec",unchanged,actions,4800);
        assertEquals(List.of("shiftclick:54"),actions.serverEffects());assertEquals(0,entry.location);
        actions.clear();
        assertEquals(BookTransfer.Result.MOVED,transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(10,false)),actions,5000));
        assertTrue(actions.serverEffects().isEmpty());assertEquals(1,entry.location);
    }
    @Test void rejectedRetrievalRespectsSlowdownAndNeverSwitchesToAnotherSourceSlot() {
        var entry=new BookList(route,1,1);var transfer=new BookTransfer();var actions=new RecordingActions();
        var unchanged=menu(1,"Ender Chest (1/3)",book(10,false));
        transfer.tick(entry,0,"ec",unchanged,actions,1000);actions.clear();transfer.slowdown(3900);
        transfer.tick(entry,0,"ec",unchanged,actions,4000);transfer.tick(entry,0,"ec",unchanged,actions,4800);
        assertTrue(actions.serverEffects().isEmpty());
        transfer.tick(entry,0,"ec",unchanged,actions,5400);
        assertEquals(List.of("shiftclick:10"),actions.serverEffects());actions.clear();
        var rearranged=menu(1,"Ender Chest (1/3)",book(11,false));
        transfer.tick(entry,0,"ec",rearranged,actions,9000);transfer.tick(entry,0,"ec",rearranged,actions,10000);
        assertTrue(actions.serverEffects().isEmpty());assertEquals(1,entry.location);
    }
    @Test void partialDestinationPacketsDoNotAuthorizeAnotherStorageClick() {
        var entry=new BookList(route,1,0);var transfer=new BookTransfer();var actions=new RecordingActions();
        transfer.tick(entry,1,"ec",menu(1,"Ender Chest (1/3)",book(54,true)),actions,1000);actions.clear();
        var partial=menu(1,"Ender Chest (1/3)");
        transfer.tick(entry,1,"ec",partial,actions,4000);transfer.tick(entry,1,"ec",partial,actions,5000);
        assertTrue(actions.serverEffects().isEmpty());assertEquals(0,entry.location);
    }
    @Test void retryRetainsTheOriginalMemoryIntentAndBindsOnlyTheConfirmedDestination() {
        var entry=new BookList(route,1,0);entry.slot=1;
        var transfer=new BookTransfer();var actions=new RecordingActions();var memory=new InventoryMemory();
        var initial=menu(1,"Ender Chest (1/3)",book(54,true),book(55,true));
        memory.observe(initial,1,1000);memory.observe(initial,1,1200);
        transfer.tick(entry,1,"ec",initial,actions,1200,memory);
        var intent=memory.move();assertEquals(1,intent.source().slot());actions.clear();
        memory.observe(initial,1,4200);transfer.tick(entry,1,"ec",initial,actions,4200,memory);
        memory.observe(initial,1,5000);transfer.tick(entry,1,"ec",initial,actions,5000,memory);
        assertEquals(List.of("shiftclick:55"),actions.serverEffects());assertSame(intent,memory.move());
        var arrived=menu(1,"Ender Chest (1/3)",book(54,true),book(10,false));
        memory.observe(arrived,1,5200);memory.observe(arrived,1,5400);
        assertEquals(BookTransfer.Result.MOVED,transfer.tick(entry,1,"ec",arrived,actions,5400,memory));
        assertEquals(1,entry.location);assertEquals(10,entry.slot);assertNull(memory.move());
    }
}
