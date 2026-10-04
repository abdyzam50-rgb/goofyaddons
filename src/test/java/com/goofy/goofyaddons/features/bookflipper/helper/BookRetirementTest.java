package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookRetirementTest {
    final Book book=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    final Task task=new Task(book,false,false,"cleanup-trade");
    final BookRetirement flow=new BookRetirement();
    final RecordingActions actions=new RecordingActions();
    final InventoryMemory memory=new InventoryMemory();
    final List<String> receipts=new ArrayList<>();
    boolean saved=true;
    final BookRetirement.Receipts ledger=new BookRetirement.Receipts(){
        public void acquired(Task t,int n,double price,String event){receipts.add("acquired:"+n+":"+price);}
        public void sold(Task t,int n,double proceeds,String event){receipts.add("sold:"+n+":"+proceeds);}
        public boolean checkpoint(){return saved;}
    };
    MenuSnapshot menu(int id,String title,int size,SlotView... content) {
        var slots=new ArrayList<SlotView>();int end=size-36;
        for(int i=0;i<size;i++)slots.add(SlotView.empty(i,i>=end,i>=end?i-end:i));
        if(title!=null)slots.set(end-1,SlotView.named(end-1,"Loaded",List.of()));
        for(var slot:content)slots.set(slot.index(),slot);
        return new MenuSnapshot(id,title,true,slots);
    }
    SlotView held(int slot,int level,int containerSlot) {return SlotView.enchantedBook(slot,true,containerSlot,"overload",level,List.of(),"Enchanted Book");}
    BookRetirement.Result tick(MenuSnapshot m,long now){memory.observe(m,BookTransfer.pageMatches(m.title(),"ec")?1:0,now);return flow.tick(task,m,actions,memory,"ec","ec 2","LocalTest",now,ledger);}
    void advanceToItem() {
        assertEquals(0,task.assignBook(book,1,0,1));task.retire();
        var orders=menu(1,"Bazaar Orders",72,held(36,1,0));
        tick(orders,0);tick(orders,1600);tick(orders,1700);
        tick(menu(2,null,46,held(10,1,0)),2800);
        var search=menu(3,"Bazaar ➜ \"Overload\"",90,SlotView.named(10,"Overload I",List.of()),held(54,1,0));
        tick(search,3900);
    }
    MenuSnapshot item(boolean present) {return menu(4,"Overload ➜ Overload I",72,
        SlotView.named(13,"Overload I",List.of()),SlotView.named(11,"Sell Instantly",List.of()),
        present?held(36,1,0):SlotView.empty(36,true,0));}
    @Test void exactInstantSaleNeedsReceiptAndInventoryDisappearanceWithoutRepeatingClicks() {
        advanceToItem();tick(item(true),5000);tick(item(true),6600);
        assertTrue(actions.serverEffects().contains("click:11"));
        tick(item(false),8000);tick(item(false),9600);assertTrue(receipts.isEmpty());
        flow.receipt(task,"[Bazaar] Sold 1x Overload II for 900 coins!");tick(item(false),10000);assertTrue(receipts.isEmpty());
        flow.receipt(task,"[Bazaar] Sold 2x Overload I for 900 coins!");tick(item(false),11000);assertTrue(receipts.isEmpty());
        flow.receipt(task,"[Bazaar] Sold 1x Overload I for 900 coins!");
        tick(item(false),12000);tick(item(false),13600);assertEquals(List.of("sold:1:900.0"),receipts);assertTrue(task.bookList.isEmpty());
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("click:11")).count());
        assertEquals(BookRetirement.Result.COMPLETE,tick(menu(5,null,46),15000));
    }
    @Test void receiptWithoutRemovalDoesNotReleaseHoldings() {
        advanceToItem();tick(item(true),5000);tick(item(true),6600);
        flow.receipt(task,"[Bazaar] Sold 1x Overload I for 900 coins!");
        tick(item(true),9000);assertEquals(1,task.bookList.size());assertTrue(receipts.isEmpty());
    }
    @Test void unrelatedSameLevelInventoryAndWrongProductNeverAuthorizeSale() {
        advanceToItem();var extra=menu(4,"Overload ➜ Overload I",72,SlotView.named(13,"Overload I",List.of()),
                SlotView.named(11,"Sell Instantly",List.of()),held(36,1,0),held(37,1,1));
        tick(extra,5000);assertEquals(BookRetirement.Result.BLOCKED,tick(extra,6600));
        assertFalse(actions.serverEffects().contains("click:11"));
        flow.reset();actions.clear();
        var wrong=menu(4,"Overload ➜ Overload II",72,SlotView.named(13,"Overload II",List.of()),SlotView.named(11,"Sell Instantly",List.of()),held(36,1,0));
        tick(wrong,7000);assertFalse(actions.serverEffects().contains("click:11"));
    }
    @Test void cancellationWaitsForANewSettledOrderListWithNoMatchingOrder() {
        task.retire();var order=SlotView.named(19,"BUY Overload I",List.of("Filled: 0/16","Price per unit: 100 coins"));
        var orders=menu(1,"Bazaar Orders",72,order);
        tick(orders,0);tick(orders,1600);assertTrue(actions.serverEffects().contains("click:19"));
        var options=menu(2,"Order options",72,SlotView.named(11,"Cancel Order",List.of()));
        tick(options,2700);tick(options,4300);assertTrue(actions.serverEffects().contains("click:11"));
        tick(orders,5400);tick(orders,7000);assertEquals(BookRetirement.Result.WAITING,tick(orders,8000));
        var absent=menu(3,"Bazaar Orders",72);tick(absent,9000);tick(absent,10600);
        assertEquals(BookRetirement.Result.COMPLETE,tick(absent,10700));
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("click:11")).count());
    }
    @Test void coOpOtherOwnersAreIgnoredAndUnreadableOwnersBlock() {
        task.retire();var order=SlotView.named(19,"BUY Overload I",List.of("Filled: 0/16","By: SomeoneElse"));
        var orders=menu(1,"Co-op Bazaar Orders",72,order);tick(orders,0);tick(orders,1600);
        assertEquals(BookRetirement.Result.COMPLETE,tick(orders,1700));assertTrue(actions.serverEffects().isEmpty());
        flow.reset();var unknown=menu(2,"Co-op Bazaar Orders",72,SlotView.named(19,"BUY Overload I",List.of("Filled: 0/16")));
        tick(unknown,2000);assertEquals(BookRetirement.Result.BLOCKED,tick(unknown,3600));
    }
    @Test void paginationDuplicateOrdersAndUnpersistedIntentNeverClick() {
        task.retire();var pages=menu(1,"Bazaar Orders",72,SlotView.named(25,"Next Page",List.of()));
        tick(pages,0);assertEquals(BookRetirement.Result.BLOCKED,tick(pages,1600));assertTrue(actions.serverEffects().isEmpty());
        flow.reset();saved=false;
        var orders=menu(2,"Bazaar Orders",72,SlotView.named(19,"BUY Overload I",List.of("Filled: 0/16")));
        tick(orders,2000);assertEquals(BookRetirement.Result.BLOCKED,tick(orders,3600));assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void aFilledSellOfferUsesNormalProceedsCollectionAndTimeoutDoesNotReplay() {
        task.retire();var orders=menu(1,"Bazaar Orders",72,SlotView.named(19,"SELL Overload V",List.of("Filled: 1/1")));
        tick(orders,0);assertEquals(BookRetirement.Result.COLLECT_SALE,tick(orders,1600));assertTrue(actions.serverEffects().isEmpty());
        flow.reset();advanceToItem();tick(item(true),5000);tick(item(true),6600);
        assertEquals(BookRetirement.Result.BLOCKED,tick(item(true),200000));
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("click:11")).count());
    }
    @Test void orphanHoldingsNeverCancelAnUntrackedLiveOrder() {
        task.markOrphanCleanup();var orders=menu(1,"Bazaar Orders",72,
                SlotView.named(19,"BUY Overload I",List.of("Filled: 0/16")));
        tick(orders,0);assertEquals(BookRetirement.Result.BLOCKED,tick(orders,1600));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void partialBuyClaimIsObservedBeforeCancelAndNoDuplicateClaimIsSubmitted() {
        task.retire();var order=SlotView.named(19,"BUY Overload I",List.of("Filled: 2/16","Price per unit: 100 coins","You have 2 items to claim!"));
        var orders=menu(1,"Bazaar Orders",72,order);tick(orders,0);tick(orders,1600);
        tick(orders,2700);tick(orders,4300);
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("click:19")).count());
        var claimed=menu(2,"Bazaar Orders",72,SlotView.named(19,"BUY Overload I",List.of("Filled: 2/16","Price per unit: 100 coins","Click to view options!")),held(36,1,0),held(37,1,1));
        tick(claimed,5400);tick(claimed,7000);
        assertEquals(2,task.bookList.size());assertEquals(List.of("acquired:2:100.0"),receipts);
        var options=menu(3,"Order options",72,SlotView.named(11,"Cancel Order",List.of()),held(36,1,0),held(37,1,1));
        tick(options,8100);tick(options,9700);assertTrue(actions.serverEffects().contains("click:11"));
        assertTrue(receipts.stream().noneMatch(s->s.startsWith("sold:")));
    }
    @Test void retiredRouteDoesNotResumeOnAChangedTaskIdentity() {
        task.retire();tick(menu(1,"Bazaar Orders",72),0);
        var other=new Task(book,false,false,"other-trade");
        assertEquals(BookRetirement.Result.BLOCKED,flow.tick(other,menu(1,"Bazaar Orders",72),actions,memory,"ec","ec 2","LocalTest",2000,ledger));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void wrongProductAtTheCorrectLevelTitleNeverClicksInstantSell() {
        advanceToItem();var wrong=menu(4,"Overload ➜ Overload I",72,
                SlotView.named(13,"Overload II",List.of()),SlotView.named(11,"Sell Instantly",List.of()),held(36,1,0));
        tick(wrong,5000);assertEquals(BookRetirement.Result.BLOCKED,tick(wrong,6600));
        assertFalse(actions.serverEffects().contains("click:11"));
    }
    @Test void storageRetrievalRequiresObservedArrivalBeforeSelling() {
        task.assignBook(book,1,1,1);task.retire();task.bookList.getFirst().slot=10;
        var orders=menu(1,"Bazaar Orders",72);tick(orders,0);tick(orders,1600);tick(orders,1700);
        tick(menu(2,null,46),2800);
        var stored=menu(3,"Ender Chest (1/3)",90,SlotView.named(8,"Loaded",List.of()),
                SlotView.enchantedBook(10,false,10,"overload",1,List.of(),"Enchanted Book"));
        tick(stored,3900);tick(stored,5500);
        assertTrue(actions.serverEffects().contains("shiftclick:10"));
        var gap=menu(3,"Ender Chest (1/3)",90,SlotView.named(8,"Loaded",List.of()));
        tick(gap,6600);tick(gap,8200);assertEquals(1,task.bookList.getFirst().location);
        var arrived=menu(3,"Ender Chest (1/3)",90,SlotView.named(8,"Loaded",List.of()),held(54,1,0));
        tick(arrived,9300);tick(arrived,10900);assertEquals(0,task.bookList.getFirst().location);
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("shiftclick:10")).count());
        assertTrue(receipts.isEmpty());
    }
}
