package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookRecoveryCheckTest {
    private final Book book=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private final BookJournal.Position saved=new BookJournal.Position(book,10000);
    private static final class Actions implements GameActions {
        List<String> commands=new ArrayList<>();int clicks;
        public void click(int slot,boolean shift){clicks++;}public void closeMenu(){}public void command(String text){commands.add(text);}
        public void message(String text){}public boolean writeSign(String text){throw new AssertionError("recovery cannot write signs");}
    }
    private MenuSnapshot menu(int page,SlotView... extra) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        slots.set(8,SlotView.named(8,"Loaded",List.of()));slots.set(35,SlotView.named(35,"Loaded",List.of()));
        for(var slot:extra) {if(slot.index()==slots.size())slots.add(slot);else slots.set(slot.index(),slot);}
        return new MenuSnapshot(page,page==0?null:page<3?"Ender Chest ("+page+"/3)":"Your Bazaar Orders",true,slots);
    }
    private BookRecoveryCheck.Result scan(BookRecoveryCheck check,Actions actions,int page,long start,SlotView... extra) {
        var view=menu(page,extra);
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(view,actions,"ec","ec 2","Tester",start));
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(view,actions,"ec","ec 2","Tester",start+1499));
        return check.tick(view,actions,"ec","ec 2","Tester",start+1500);
    }
    @Test void completedEmptyInventoryStorageAndOrdersVerifyStaleRecordsWithoutAnyItemClicks() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        for(int page=0;page<3;page++)assertEquals(BookRecoveryCheck.Result.WAITING,scan(check,actions,page,1000+page*2000));
        assertEquals(BookRecoveryCheck.Result.VERIFIED,scan(check,actions,3,7000));
        assertTrue(check.present().isEmpty());assertEquals(0,actions.clicks);
        assertEquals(Set.of(0,1,2,3),check.snapshots().keySet());
    }
    @Test void actualStorageBooksAndCompletedSellOrdersAreRetained() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000);scan(check,actions,1,3000,SlotView.enchantedBook(10,false,10,"overload",4,List.of(),"Overload IV"));
        scan(check,actions,2,5000);
        assertEquals(BookRecoveryCheck.Result.VERIFIED,scan(check,actions,3,7000,SlotView.named(11,"SELL Overload V",List.of("Filled: 1/1"))));
        assertEquals(Set.of(book.id()),check.present());assertEquals(0,actions.clicks);
    }
    @Test void inventoryIncludingOffhandIsCheckedBeforeOpeningStorage() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000,SlotView.enchantedBook(90,true,40,"overload",1,List.of(),"Overload I"));
        scan(check,actions,1,3000);scan(check,actions,2,5000);scan(check,actions,3,7000);
        assertEquals(Set.of(book.id()),check.present());
    }
    @Test void paginatedOrdersNeverEstablishAbsence() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000);scan(check,actions,1,3000);scan(check,actions,2,5000);
        assertEquals(BookRecoveryCheck.Result.BLOCKED,scan(check,actions,3,7000,SlotView.named(12,"Next Page",List.of())));
        assertTrue(check.reason().contains("paginated"));
    }
    @Test void partialAndChangingMenusAndOccupiedCursorDoNotFinishVerification() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        var base=menu(0);var occupied=new MenuSnapshot(0,null,false,base.slots());
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(occupied,actions,"ec","ec 2","Tester",1000));
        assertTrue(actions.commands.isEmpty());
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(base,actions,"ec","ec 2","Tester",2000));
        var changed=menu(0,SlotView.enchantedBook(54,true,0,"overload",1,List.of(),"Overload I"));
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(changed,actions,"ec","ec 2","Tester",3500));
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(changed,actions,"ec","ec 2","Tester",4999));
        check.tick(changed,actions,"ec","ec 2","Tester",5000);assertEquals(Set.of(book.id()),check.present());
    }
    @Test void missingMenusTimeOutWithoutClearingAnything() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        assertEquals(BookRecoveryCheck.Result.WAITING,check.tick(null,actions,"ec","ec 2","Tester",1000));
        assertEquals(BookRecoveryCheck.Result.BLOCKED,check.tick(null,actions,"ec","ec 2","Tester",46001));assertEquals(0,actions.clicks);
    }
    @Test void coOpIconsDoNotTurnSomeoneElsesOrderIntoOurOwnership() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000);scan(check,actions,1,3000);scan(check,actions,2,5000);
        var icon=SlotView.enchantedBook(11,false,11,"overload",1,List.of("By: SomeoneElse"),"BUY Overload I");
        var base=menu(3,icon);var coOp=new MenuSnapshot(3,"Co-op Bazaar Orders",true,base.slots());
        check.tick(coOp,actions,"ec","ec 2","Tester",7000);
        assertEquals(BookRecoveryCheck.Result.VERIFIED,check.tick(coOp,actions,"ec","ec 2","Tester",8500));
        assertTrue(check.present().isEmpty());
    }
    @Test void unreadableCoOpCreatorKeepsTheJournalUnverified() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000);scan(check,actions,1,3000);scan(check,actions,2,5000);
        var base=menu(3,SlotView.named(11,"BUY Overload I",List.of()));
        var coOp=new MenuSnapshot(3,"Co-op Bazaar Orders",true,base.slots());
        check.tick(coOp,actions,"ec","ec 2","Tester",7000);
        assertEquals(BookRecoveryCheck.Result.BLOCKED,check.tick(coOp,actions,"ec","ec 2","Tester",8500));
        assertTrue(check.reason().contains("creator"));
    }

    @Test void opensEachStoragePageAndOrdersOnceWithoutItemClicks() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        scan(check,actions,0,1000);
        check.tick(menu(0),actions,"ec","ec 2","Tester",2600);
        scan(check,actions,1,3000);
        check.tick(menu(0),actions,"ec","ec 2","Tester",4600);
        scan(check,actions,2,5000);
        check.tick(menu(0),actions,"ec","ec 2","Tester",6600);
        assertEquals(BookRecoveryCheck.Result.VERIFIED,scan(check,actions,3,7000));
        assertEquals(List.of("ec","ec 2","managebazaarorders"),actions.commands);assertEquals(0,actions.clicks);
    }
    @Test void absentEnchantmentMetadataCannotProveAnOwnedBookIsMissing() {
        var check=new BookRecoveryCheck(List.of(saved));var actions=new Actions();
        var unreadable=new SlotView(54,true,0,false,"Enchanted Book","Enchanted Book",List.of(),SlotView.ENCHANTED_BOOK,Map.of(),1,1);
        assertEquals(BookRecoveryCheck.Result.BLOCKED,scan(check,actions,0,1000,unreadable));
        assertTrue(check.reason().contains("Unreadable"));
    }

}
