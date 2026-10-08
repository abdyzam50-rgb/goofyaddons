package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookPopulationTest {
    private final Book route = new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private SlotView book(int index,int level,boolean inventory) {
        return SlotView.enchantedBook(index,inventory,inventory?index-54:index,"overload",level,List.of(),"Enchanted Book");
    }
    private MenuSnapshot menu(int id,SlotView... books) {
        var slots = new ArrayList<SlotView>();
        for (int i=0;i<90;i++) slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for (var book : books) slots.set(book.index(),book);
        return new MenuSnapshot(id,"Ender Chest (1/3)",true,slots);
    }
    private void stable(InventoryMemory memory,MenuSnapshot menu,int page,long now) {
        memory.observe(menu,page,now);memory.observe(menu,page,now+200);
    }
    private void baseline(InventoryMemory memory,SlotView... books) {
        stable(memory,menu(1,books),1,1000);stable(memory,menu(2,books),2,1500);
    }
    @Test void lossIsClassifiedOnlyAfterBothPagesAreRefreshed() {
        var memory = new InventoryMemory();baseline(memory,book(54,1,true));
        var entry = new BookList(route,1,0);entry.slot=0;var policy=new BookPopulation();
        stable(memory,menu(3),0,2000);
        assertEquals(1,policy.inspect(List.of(entry),List.of(route),memory).inspectPage());
        stable(memory,menu(4),1,3000);
        assertEquals(2,policy.inspect(List.of(entry),List.of(route),memory).inspectPage());
        stable(memory,menu(5),2,4000);
        var result=policy.inspect(List.of(entry),List.of(route),memory);
        assertEquals(List.of(entry),result.missing());assertTrue(result.found().isEmpty());
    }
    @Test void laterReturnIsANewFoundBookAndNeverRestoresTheOldEntry() {
        var memory=new InventoryMemory();baseline(memory,book(54,1,true));
        var task=new Task(route,false,false);task.assignBook(route,1,0,1);var old=task.bookList.getFirst();old.slot=0;
        var policy=new BookPopulation();stable(memory,menu(3),0,2000);
        policy.inspect(task.bookList,List.of(route),memory);
        stable(memory,menu(4),1,3000);policy.inspect(task.bookList,List.of(route),memory);
        stable(memory,menu(5),2,4000);
        var loss=policy.inspect(task.bookList,List.of(route),memory);assertEquals(List.of(old),loss.missing());
        assertEquals(1,task.loseBook(old));assertEquals(16,task.getAmountToOrder());policy.reset();
        stable(memory,menu(6,book(55,1,true)),0,6000);
        assertEquals(1,policy.inspect(task.bookList,List.of(route),memory).inspectPage());
        stable(memory,menu(7,book(55,1,true)),1,7000);policy.inspect(task.bookList,List.of(route),memory);
        stable(memory,menu(8,book(55,1,true)),2,8000);
        var gift=policy.inspect(task.bookList,List.of(route),memory).found().getFirst();
        assertNotSame(old,gift);assertTrue(gift.found);assertEquals(1,gift.slot);
        assertTrue(task.acceptFound(gift));assertEquals(15,task.getAmountToOrder());assertFalse(task.bookList.contains(old));
        assertFalse(policy.inspect(task.bookList,List.of(route),memory).changed(),"the same observed gift is not adopted twice");
    }
    @Test void aFoundLevelWithNoExistingModelEntryIsStillDetected() {
        var memory=new InventoryMemory();baseline(memory);
        stable(memory,menu(3,book(54,3,true)),0,2000);var policy=new BookPopulation();
        assertEquals(1,policy.inspect(List.of(),List.of(route),memory).inspectPage());
        stable(memory,menu(4,book(54,3,true)),1,3000);policy.inspect(List.of(),List.of(route),memory);
        stable(memory,menu(5,book(54,3,true)),2,4000);
        var result=policy.inspect(List.of(),List.of(route),memory);
        assertEquals(1,result.found().size());assertEquals(3,result.found().getFirst().level);
        var task=new Task(route,false,false);assertTrue(task.acceptFound(result.found().getFirst()));
        assertEquals(12,task.getAmountToOrder());
    }
    @Test void losingAHigherLevelBookRestoresItsEquivalentInputRequirementExactlyOnce() {
        var task=new Task(route,false,false);task.assignBook(route,4,1,1);var entry=task.bookList.getFirst();
        assertEquals(8,task.getAmountToOrder());assertEquals(8,task.loseBook(entry));
        assertEquals(16,task.getAmountToOrder());assertEquals(0,task.loseBook(entry));assertEquals(16,task.getAmountToOrder());
    }
    @Test void extraCopiesRemainSeparateFromTheTrackedCopy() {
        var memory=new InventoryMemory();baseline(memory,book(54,1,true));
        var tracked=new BookList(route,1,0);tracked.slot=0;
        stable(memory,menu(3,book(54,1,true),book(55,1,true)),0,2000);var policy=new BookPopulation();
        policy.inspect(List.of(tracked),List.of(route),memory);
        stable(memory,menu(4,book(54,1,true),book(55,1,true)),1,3000);policy.inspect(List.of(tracked),List.of(route),memory);
        stable(memory,menu(5,book(54,1,true),book(55,1,true)),2,4000);
        var result=policy.inspect(List.of(tracked),List.of(route),memory);
        assertTrue(result.missing().isEmpty());assertEquals(1,result.found().size());assertEquals(1,result.found().getFirst().slot);
    }
    @Test void aCompletedTaskDoesNotOverfillWhenGivenABlessing() {
        var task=new Task(route,false,false);task.assignBook(route,5,0,1);
        assertFalse(task.acceptFound(new BookList(route,1,0)));assertEquals(0,task.getAmountToOrder());assertEquals(1,task.bookList.size());
    }
    @Test void pendingTransferOrBusyCursorCannotDeclareALoss() {
        var memory=new InventoryMemory();baseline(memory,book(54,1,true));
        var entry=new BookList(route,1,0);entry.slot=0;
        memory.beginMove(new InventoryMemory.Address(0,0),1,1800);stable(memory,menu(3),1,2000);
        assertFalse(new BookPopulation().inspect(List.of(entry),List.of(route),memory).changed());
        memory.finishMove();var base=menu(3);
        var busy=new MenuSnapshot(3,base.title(),false,base.slots(),book(-1,1,false));stable(memory,busy,1,3000);
        assertFalse(new BookPopulation().inspect(List.of(entry),List.of(route),memory).changed());
    }
    @Test void aStaleClosedPageIsInspectedBeforeItsContentsBecomeFoundInventory() {
        var memory=new InventoryMemory();stable(memory,menu(1,book(10,1,false)),1,1000);
        stable(memory,menu(2),2,2000);stable(memory,menu(3),0,50000);
        var policy=new BookPopulation();assertEquals(1,policy.inspect(List.of(),List.of(route),memory).inspectPage());
        stable(memory,menu(4),1,51000);
        assertTrue(policy.inspect(List.of(),List.of(route),memory).found().isEmpty(),"a vanished cached book is not a gift");
    }
    @Test void lowerLevelExtrasAreHandledEvenWhenTheCurrentRouteBuysHigherInputs() {
        var memory=new InventoryMemory();baseline(memory,book(54,1,true));
        var extra=new BookList(route,1,0);extra.slot=0;
        var higher=new Book(route.id(),3,5,route.name(),0,0);var policy=new BookPopulation();
        stable(memory,menu(3),0,2000);policy.inspect(List.of(extra),List.of(higher),memory);
        stable(memory,menu(4),1,3000);policy.inspect(List.of(extra),List.of(higher),memory);
        stable(memory,menu(5),2,4000);
        assertEquals(List.of(extra),policy.inspect(List.of(extra),List.of(higher),memory).missing());
    }
}
