package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.goofy.goofyaddons.features.bookflipper.helper.BookSellCancellation.Result.*;

class BookSellCancellationTest {
    final Book book=new Book("ENCHANTMENT_ULTIMATE_CHIMERA",1,3,"Chimera",0,0);
    final Task task=new Task(book,false,false);
    final BookSellCancellation flow=new BookSellCancellation();
    BookSellCancellationTest() {
        task.assignBook(book,3,0,1);task.setBookState(Task.BookState.REPLACE_SELL);
        flow.start(task,6,0);
    }
    MenuSnapshot menu(int id,boolean order,int books) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<72;i++)slots.add(SlotView.empty(i,i>=36,i>=36?i-36:i));
        slots.set(35,SlotView.named(35,"Loaded",List.of()));
        if(order)slots.set(11,SlotView.named(11,"SELL Chimera III",List.of()));
        for(int i=0;i<books;i++)slots.set(37+i,SlotView.enchantedBook(37+i,true,1+i,"ultimate_chimera",3,List.of(),"Enchanted Book"));
        return new MenuSnapshot(id,"Co-op Bazaar Orders",true,slots);
    }
    @Test void refundedOutputKeepsOriginalTradeWithoutNewAcquisitionOrExtra() {
        var original=task.bookList.getFirst();var id=task.getProfitTradeId();
        assertEquals(WAITING,flow.observe(menu(7,false,1),500));
        assertEquals(RETURNED,flow.observe(menu(7,false,1),2000));
        assertSame(original,task.bookList.getFirst());assertEquals(id,task.getProfitTradeId());
        assertEquals(1,original.slot);assertEquals(0,original.location);
        assertEquals(Task.BookState.SELL,task.getBookState());assertEquals(0,task.getAmountToOrder());
        var memory=new InventoryMemory();
        memory.observe(menu(7,false,1),0,2000);memory.observe(menu(7,false,1),0,2200);
        var population=new BookPopulation().inspect(task.bookList,List.of(book),memory);
        assertTrue(population.found().isEmpty());assertTrue(population.missing().isEmpty());
    }
    @Test void staleContainerAndStillPresentOfferCannotVerifyCancellation() {
        assertEquals(WAITING,flow.observe(menu(6,false,1),2000));
        assertEquals(WAITING,flow.observe(menu(7,true,1),2100));
        assertEquals(WAITING,flow.observe(menu(7,true,1),4000));
        assertEquals(Task.BookState.REPLACE_SELL,task.getBookState());
    }
    @Test void delayedInventoryPacketsRestartSettlingAndMissingOutputTimesOut() {
        assertEquals(WAITING,flow.observe(menu(7,false,0),500));
        assertEquals(WAITING,flow.observe(menu(7,false,0),2200));
        assertEquals(WAITING,flow.observe(menu(7,false,1),2300));
        assertEquals(WAITING,flow.observe(menu(7,false,1),3000));
        assertEquals(RETURNED,flow.observe(menu(7,false,1),3800));
        flow.reset();flow.start(task,8,4000);
        assertEquals(BLOCKED,flow.observe(menu(9,false,0),34000));assertEquals(1,task.bookList.size());
    }
    @Test void ambiguousCopiesNeverGetAdoptedOrReleaseOwnership() {
        assertEquals(WAITING,flow.observe(menu(7,false,2),500));
        assertEquals(BLOCKED,flow.observe(menu(7,false,2),2000));assertEquals(1,task.bookList.size());
    }
    @Test void cancellationStaysPendingUntilCallerCheckpointsAndResets() {
        assertThrows(IllegalStateException.class,()->flow.start(task,6,100));
        assertTrue(flow.pending());assertSame(task,flow.task());
        flow.reset();assertFalse(flow.pending());
    }
}
