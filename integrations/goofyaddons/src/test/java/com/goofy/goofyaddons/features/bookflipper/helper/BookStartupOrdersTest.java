package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookStartupOrdersTest {
    private final Book overload=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private final Book duplex=new Book("ENCHANTMENT_ULTIMATE_REITERATE",1,5,"Duplex",0,0);
    private Task task(Book book) {
        var task=new Task(book,false,false);task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK);return task;
    }
    private MenuSnapshot menu(int id,SlotView... entries) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<72;i++) slots.add(SlotView.empty(i,i>=36,i>=36?i-36:i));
        slots.set(35,SlotView.named(35,"Loaded",List.of()));
        for(var entry:entries) slots.set(entry.index(),entry);
        return new MenuSnapshot(id,"Co-op Bazaar Orders",true,slots);
    }
    @Test void oneSettledVisitAccountsForBothPhysicallyOwnedRoutesWithNoBuyOrder() {
        var scan=new BookStartupOrders();var a=task(overload);var b=task(duplex);
        a.assignBook(overload,1,0,2);b.assignBook(duplex,5,1,1);
        var tasks=List.of(a,b);var orders=menu(4);
        assertNull(scan.missingBuys(tasks,orders,1000));
        assertNull(scan.missingBuys(tasks,orders,1749));
        var absent=scan.missingBuys(tasks,orders,1750);
        assertEquals(tasks,absent);
        absent.forEach(BookStartupOrders::scheduleMissingBuy);
        assertEquals(Task.BookState.SELECTED,a.getBookState());
        assertEquals(Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER,a.actionSchedule);
        assertEquals(Task.BookState.ANVIL,b.getBookState());
        assertTrue(scan.missingBuys(tasks,orders,2000).isEmpty());
        assertEquals(2,a.bookList.size());assertEquals(1,b.bookList.size());
    }
    @Test void visibleBuyOrdersAreRetainedWhileAllOtherRoutesAreAccountedTogether() {
        var scan=new BookStartupOrders();var a=task(overload);var b=task(duplex);
        var orders=menu(4,SlotView.named(11,"BUY Overload I",List.of("Order amount: 1x")));
        scan.missingBuys(List.of(a,b),orders,1000);
        var absent=scan.missingBuys(List.of(a,b),orders,1750);
        assertEquals(List.of(b),absent);
        BookStartupOrders.scheduleMissingBuy(b);
        assertEquals(Task.BookState.BAZAAR_ORDER_CHECK,a.getBookState());
        assertEquals(Task.BookState.SELECTED,b.getBookState());
    }
    @Test void lateOrderPacketsAndNewContainersRestartStabilityWithoutReopening() {
        var scan=new BookStartupOrders();var a=task(overload);
        scan.missingBuys(List.of(a),menu(4),1000);
        var arrived=menu(4,SlotView.named(11,"BUY Overload I",List.of()));
        assertNull(scan.missingBuys(List.of(a),arrived,1600));
        assertNull(scan.missingBuys(List.of(a),arrived,2000));
        assertEquals(List.of(),scan.missingBuys(List.of(a),arrived,2350));
        assertNull(scan.missingBuys(List.of(a),menu(5),2500));
        assertEquals(List.of(a),scan.missingBuys(List.of(a),menu(5),3250));
    }
    @Test void duplicateOrPaginatedOrdersCannotAuthorizeMissingDecisions() {
        var a=task(overload);
        for(var orders:List.of(menu(4,SlotView.named(11,"BUY Overload I",List.of()),SlotView.named(12,"BUY Overload I",List.of())),
                menu(4,SlotView.named(11,"Next Page",List.of())))) {
            var scan=new BookStartupOrders();scan.missingBuys(List.of(a),orders,1000);
            assertNull(scan.missingBuys(List.of(a),orders,2000));
            assertEquals(Task.BookState.BAZAAR_ORDER_CHECK,a.getBookState());
        }
    }
    @Test void closedWrongUnloadedAndBusyMenusCannotMarkOrdersAbsent() {
        var scan=new BookStartupOrders();var tasks=List.of(task(overload));var complete=menu(4);
        var invalid=List.of(new MenuSnapshot(0,null,true,complete.slots()),
                new MenuSnapshot(4,"Overload ➜ Overload I",true,complete.slots()),
                new MenuSnapshot(4,"Co-op Bazaar Orders",true,List.of()),
                new MenuSnapshot(4,"Co-op Bazaar Orders",false,complete.slots()));
        for(var menu:invalid) {
            scan.missingBuys(tasks,complete,1000);
            assertNull(scan.missingBuys(tasks,menu,2000));
            assertNull(scan.missingBuys(tasks,complete,3000));
        }
    }
    @Test void singleInventoryBookStoresBeforeBuyingAndNoHoldingsBuyNormally() {
        var held=task(overload);held.assignBook(overload,1,0,1);
        BookStartupOrders.scheduleMissingBuy(held);
        assertEquals(Task.ActionSchedule.SELECTED_STORE_BUYORDER,held.actionSchedule);
        var empty=task(duplex);empty.actionSchedule=Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
        BookStartupOrders.scheduleMissingBuy(empty);
        assertEquals(Task.ActionSchedule.NONE,empty.actionSchedule);
        assertEquals(Task.BookState.SELECTED,empty.getBookState());
    }
    @Test void ordinaryWaitingTasksAreNotReclassifiedAndResetStartsANewVisit() {
        var a=task(overload);a.setBookState(Task.BookState.IN_BUY_ORDER);
        var scan=new BookStartupOrders();var tasks=List.of(a);var orders=menu(4);
        scan.missingBuys(tasks,orders,1000);
        assertTrue(scan.missingBuys(tasks,orders,1750).isEmpty());
        assertThrows(IllegalStateException.class,()->BookStartupOrders.scheduleMissingBuy(a));
        scan.reset();assertNull(scan.missingBuys(tasks,orders,2000));
    }
}
