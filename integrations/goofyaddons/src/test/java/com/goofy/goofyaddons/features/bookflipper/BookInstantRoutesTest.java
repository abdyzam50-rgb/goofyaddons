package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookInstantRoutesTest {
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    static MenuSnapshot menu(String title,SlotView... actual){
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var slot:actual)slots.set(slot.index(),slot);return new MenuSnapshot(title==null?3:title.contains("How many")?2:1,title,true,slots);
    }
    static SlotView book(int slot,int level){return SlotView.enchantedBook(slot,slot>=54,slot>=54?slot-54:slot,"overload",level,List.of(),"Overload "+(level==4?"IV":"V"));}
    static class Harness {
        final Task task=new Task(new Book("ENCHANTMENT_OVERLOAD",4,5,"Overload",0,0),true,true);
        final RecordingActions actions=new RecordingActions();final CapitalManager capital=new CapitalManager();
        final List<Task> tasks=new ArrayList<>(List.of(task));final List<Double> costs=new ArrayList<>(),sales=new ArrayList<>();
        MenuSnapshot menu=menu(null);double purse=5000;long now=1000;boolean sign;int checkpoints;String failure;
        final BookContext ctx;
        Harness(){
            capital.configure(10000,0);capital.reserve("books",task.getBook().id(),208,purse);task.setReservedUnitCost(104);
            var quotes=JsonParser.parseString("{\"products\":{\"ENCHANTMENT_OVERLOAD_4\":{\"buy_summary\":[{\"pricePerUnit\":100,\"amount\":100}]},\"ENCHANTMENT_OVERLOAD_5\":{\"sell_summary\":[{\"pricePerUnit\":300,\"amount\":100}]}}}").getAsJsonObject();
            var services=proxy(BookServices.class,(p,m,a)->switch(m.getName()){case "latestQuotes"->quotes;case "purse"->purse;case "observedSkills"->Map.of("enchanting",60);default->null;});
            var accounting=proxy(BookAccounting.class,(p,m,a)->{if(m.getName().equals("acquire"))costs.add((Double)a[5]);if(m.getName().equals("sell"))sales.add((Double)a[5]);return null;});
            ctx=proxy(BookContext.class,(p,m,a)->switch(m.getName()){
                case "activeTask","taskInState"->task;case "now"->now;case "services"->services;case "accounting"->accounting;
                case "capital"->capital;case "menu"->menu;case "signOpen"->sign;case "actions"->actions;case "tasks"->tasks;
                case "checkpoint"->{checkpoints++;yield true;}case "bookPriceAllowed"->true;
                case "safetyHalt"->{failure=(String)a[0];yield null;}default->null;
            });
        }
        void advance(MenuSnapshot next){menu=next;now+=100;}
    }
    @Test void instantBookBuyProvesExactBooksAndDebitBeforeAssigningInputs(){
        var h=new Harness();var buy=new BookBuy();
        h.menu=menu("Overload IV",book(13,4),SlotView.named(10,"Buy Instantly",List.of("Overload IV","Price per unit: 100 coins")));
        buy.tick(h.ctx);h.advance(menu("How many do you want?",SlotView.named(16,"Custom Amount",List.of())));buy.tick(h.ctx);
        h.advance(menu(null));h.sign=true;buy.tick(h.ctx);h.sign=false;
        assertEquals(1,h.checkpoints);assertTrue(h.costs.isEmpty());assertEquals(2,h.task.getAmountToOrder());
        h.advance(menu(null,book(54,4),book(55,4)));h.purse=-1;buy.tick(h.ctx);assertTrue(h.costs.isEmpty());
        h.now+=100;h.purse=4800;buy.tick(h.ctx);
        assertNull(h.failure);assertEquals(List.of(200.0),h.costs);assertEquals(0,h.task.getAmountToOrder());assertEquals(2,h.task.bookList.size());
        assertEquals(Task.BookState.COMBINE,h.task.getBookState());assertEquals(1,h.actions.serverEffects().stream().filter(s->s.startsWith("sign:")).count());
    }
    @Test void instantBookSaleRecordsVerifiedProceedsWithoutCreatingAnOffer(){
        var h=new Harness();h.task.assignBook(h.task.getBook(),5,0,1);h.task.setBookState(Task.BookState.SELL);var sell=new BookSell();
        h.menu=menu("Overload V",book(13,5),book(54,5),SlotView.named(11,"Sell Instantly",List.of("Overload V","Price per unit: 300 coins")));
        sell.sell(h.ctx);assertTrue(h.sales.isEmpty());assertTrue(h.actions.serverEffects().contains("click:11"));
        h.advance(menu(null));h.purse=-1;sell.sell(h.ctx);assertTrue(h.sales.isEmpty());
        h.now+=100;h.purse=5296.25;sell.sell(h.ctx);assertNull(h.failure);assertEquals(List.of(296.25),h.sales);assertTrue(h.tasks.isEmpty());
    }
}
