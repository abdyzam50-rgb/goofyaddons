package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryMemoryTest {
    private final Book route = new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private SlotView book(int index,int level,boolean inventory) {
        return SlotView.enchantedBook(index,inventory,inventory?index-54:index,"overload",level,List.of(),"Enchanted Book");
    }
    private MenuSnapshot menu(int id,SlotView... books) {
        var slots = new ArrayList<SlotView>();
        for(int i=0;i<90;i++) slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        slots.set(8,SlotView.named(8,"Storage",List.of()));
        for(var book:books) slots.set(book.index(),book);
        return new MenuSnapshot(id,"Ender Chest (1/3)",true,slots);
    }
    private void stable(InventoryMemory memory,MenuSnapshot menu,int page,long now) {
        memory.observe(menu,page,now); memory.observe(menu,page,now+200);
    }
    @Test void dashboardProjectionRetainsEmptyStorageSlotPositionsAcrossSnapshots() {
        var memory=new InventoryMemory();stable(memory,menu(1,book(12,1,false)),1,1000);
        stable(memory,menu(1,book(13,1,false)),1,2000);
        @SuppressWarnings("unchecked") var regions=(List<Map<String,Object>>)memory.diagnosticState().get("regions");
        var storage=regions.stream().filter(r->r.get("region").equals(1)).findFirst().orElseThrow();
        assertEquals(java.util.stream.IntStream.range(0,54).boxed().toList(),storage.get("currentSlots"));
        assertEquals(storage.get("currentSlots"),storage.get("previousSlots"));
        @SuppressWarnings("unchecked") var previous=(List<Map<String,Object>>)storage.get("previous");
        @SuppressWarnings("unchecked") var current=(List<Map<String,Object>>)storage.get("current");
        assertTrue(previous.stream().anyMatch(e->e.get("slot").equals(12)));
        assertTrue(current.stream().noneMatch(e->e.get("slot").equals(12)));
    }
    @Test void anvilPreviewAndActionButtonAreRecordedSeparatelyFromOwnedInputs() {
        var memory=new InventoryMemory();
        var content=menu(74,book(29,1,false),book(33,1,false),book(13,2,false),
                SlotView.named(22,"Combine Items",List.of("Click to combine!")));
        var anvil=new MenuSnapshot(74,"Anvil",true,content.slots());
        stable(memory,anvil,0,1000);
        assertEquals(2,memory.layout(-2).current().size());
        assertNotNull(memory.layout(-2).current().get(29));
        assertNotNull(memory.layout(-2).current().get(33));
        assertEquals(Map.of("overload",2),memory.layout(-3).current().get(13).enchantments());
        assertNotNull(memory.layout(-3).current().get(22));
        assertTrue(memory.matching(new BookList(route,2,0),0).isEmpty());
    }

    @Test void previousLayoutSurvivesRepeatedFramesAndRecordsExactChangedSlots() {
        var memory = new InventoryMemory();
        stable(memory,menu(1,book(54,2,true)),1,1000);
        var before = memory.layout(0);
        stable(memory,menu(1,book(12,2,false)),1,2000);
        memory.observe(menu(1,book(12,2,false)),1,2500);
        assertEquals(before.current(),memory.layout(0).previous());
        assertNotNull(memory.layout(0).previous().get(0));
        assertNull(memory.layout(0).current().get(0));
        assertEquals(12,memory.matching(new BookList(route,2,1),1).getFirst().slot());
        assertEquals(0,memory.changes(0).getFirst().address().slot());
        assertThrows(UnsupportedOperationException.class,()->memory.layout(0).current().put(0,null));
    }
    @Test void transientEmptyPacketsCannotReplaceTheConfirmedLayout() {
        var memory = new InventoryMemory();
        var held = menu(1,book(54,1,true)); stable(memory,held,1,1000);
        memory.observe(menu(1),1,2000);
        assertFalse(memory.fresh(0)); assertNotNull(memory.layout(0).current().get(0));
        memory.observe(held,1,2100); memory.observe(held,1,2300);
        assertTrue(memory.fresh(0)); assertNotNull(memory.layout(0).current().get(0));
        assertTrue(memory.changes(0).stream().noneMatch(c->c.after()==null));
    }
    @Test void closedPagesRetainTheirHistoryButLoseFreshness() {
        var memory = new InventoryMemory(); stable(memory,menu(1,book(12,1,false)),1,1000);
        var page = memory.layout(1);
        stable(memory,menu(2),2,2000);
        assertFalse(memory.fresh(1)); assertTrue(memory.fresh(2));
        assertEquals(page,memory.layout(1));
        memory.observe(null,0,3000); assertFalse(memory.fresh(0)); assertEquals(page,memory.layout(1));
    }
    @Test void aDifferentContainerMustSettleEvenIfTheContentsLookIdentical() {
        var memory = new InventoryMemory(); stable(memory,menu(1,book(54,1,true)),1,1000);
        memory.observe(menu(2,book(54,1,true)),1,2000);
        assertFalse(memory.fresh(0)); assertFalse(memory.fresh(1));
        memory.observe(menu(2,book(54,1,true)),1,2200); assertTrue(memory.fresh(1));
    }
    @Test void allMainInventoryItemsAreRememberedWithoutBeingMistakenForRouteBooks() {
        var memory = new InventoryMemory();
        var tool = new SlotView(55,true,1,false,"Pickaxe","Pickaxe",List.of(),"PICKAXE",Map.of(),1,1);
        stable(memory,menu(1,book(54,1,true),tool),1,1000);
        assertEquals("PICKAXE",memory.layout(0).current().get(1).id());
        assertEquals(1,memory.matching(new BookList(route,1,0),0).size());
    }
    @Test void cursorAndAnvilInputsAreRememberedButCannotAuthorizeReconciliation() {
        var memory = new InventoryMemory(); var base = menu(1,book(29,1,false));
        var anvil = new MenuSnapshot(1,"Anvil",false,base.slots(),book(-1,1,false));
        stable(memory,anvil,0,1000);
        assertNotNull(memory.layout(-1).current().get(0));
        assertNotNull(memory.layout(-2).current().get(29));
        assertFalse(memory.fresh(0));
    }
    @Test void bookUnexpectedlyReturnedFromStorageIsCorrectedAfterThePageIsObserved() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,2,1); tracked.slot=12;
        stable(memory,menu(1,book(12,2,false)),1,1000);
        stable(memory,menu(2,book(54,2,true)),0,2000);
        var locations = new BookLocations();
        assertEquals(1,locations.reconcile(List.of(tracked),memory,0).inspectPage());
        assertEquals(1,tracked.location,"cached page cannot prove the book left it");
        stable(memory,menu(3,book(54,2,true)),1,3000);
        assertEquals(1,locations.reconcile(List.of(tracked),memory,1).corrected());
        assertEquals(0,tracked.location); assertEquals(0,tracked.slot);
        assertNotNull(memory.layout(1).previous().get(12));
        assertNull(memory.layout(1).current().get(12));
    }
    @Test void unexpectedlyStoredBookIsFoundOnTheSecondPage() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,0); tracked.slot=0;
        stable(memory,menu(1,book(54,1,true)),1,1000);
        stable(memory,menu(2,book(54,1,true)),2,2000);
        stable(memory,menu(3),0,3000);
        var locations = new BookLocations();
        assertEquals(1,locations.reconcile(List.of(tracked),memory,0).inspectPage());
        stable(memory,menu(4),1,4000);
        assertEquals(2,locations.reconcile(List.of(tracked),memory,1).inspectPage());
        assertEquals(0,tracked.location);
        stable(memory,menu(5,book(17,1,false)),2,5000);
        assertEquals(1,locations.reconcile(List.of(tracked),memory,2).corrected());
        assertEquals(2,tracked.location); assertEquals(17,tracked.slot);
    }
    @Test void identicalCopiesKeepDistinctSlotsWhenOneReturnsFromStorage() {
        var memory = new InventoryMemory(); var a=new BookList(route,1,0);a.slot=0;
        var b=new BookList(route,1,1);b.slot=10;var c=new BookList(route,1,1);c.slot=11;
        stable(memory,menu(1,book(54,1,true),book(10,1,false),book(11,1,false)),1,1000);
        stable(memory,menu(1,book(54,1,true),book(55,1,true),book(11,1,false)),1,2000);
        assertEquals(1,new BookLocations().reconcile(List.of(a,b,c),memory,1).corrected());
        assertEquals(0,a.slot); assertEquals(1,b.slot); assertEquals(0,b.location);
        assertEquals(11,c.slot); assertEquals(1,c.location);
        assertEquals(3,new HashSet<>(List.of(new InventoryMemory.Address(a.location,a.slot),
                new InventoryMemory.Address(b.location,b.slot),new InventoryMemory.Address(c.location,c.slot))).size());
    }
    @Test void itemLossRemainsUnresolvedAfterBothPagesAreInspected() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,0);
        stable(memory,menu(1,book(54,1,true)),1,1000);
        stable(memory,menu(2),0,2000);
        stable(memory,menu(3),1,3000);
        stable(memory,menu(4),2,4000);
        assertTrue(new BookLocations().reconcile(List.of(tracked),memory,2).unresolved());
        assertEquals(0,tracked.location);
    }
    @Test void pendingTransferOwnsItsSourceEvenWhenOnlyTheSourcePacketArrives() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,0);tracked.slot=0;
        stable(memory,menu(1,book(54,1,true)),1,1000);
        memory.beginMove(new InventoryMemory.Address(0,0),1,1300);
        stable(memory,menu(1),1,2000);
        var result = new BookLocations().reconcile(List.of(tracked),memory,1);
        assertEquals(0,result.inspectPage());assertFalse(result.unresolved());assertEquals(0,tracked.location);
        assertEquals(0,memory.move().source().slot());assertEquals("ENCHANTED_BOOK",memory.move().item().id());
    }
    @Test void bookMovedBetweenStoragePagesIsRecoveredWithoutAnInventoryArrival() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,1);tracked.slot=10;
        stable(memory,menu(1,book(10,1,false)),1,1000);
        stable(memory,menu(2),2,2000);
        stable(memory,menu(3),1,3000);
        var locations = new BookLocations();
        assertEquals(2,locations.reconcile(List.of(tracked),memory,1).inspectPage());
        stable(memory,menu(4,book(16,1,false)),2,4000);
        assertEquals(1,locations.reconcile(List.of(tracked),memory,2).inspectPage());
        assertEquals(1,tracked.location,"the cached source page must be rechecked after the new destination observation");
        stable(memory,menu(5),1,5000);
        assertEquals(1,locations.reconcile(List.of(tracked),memory,1).corrected());
        assertEquals(2,tracked.location);assertEquals(16,tracked.slot);
    }

    @Test void duplicateNewCopyDoesNotMakeAStoredBookDisappearFromTheModel() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,1);tracked.slot=10;
        stable(memory,menu(1,book(10,1,false)),1,1000);
        stable(memory,menu(2),2,2000);
        stable(memory,menu(3,book(54,1,true),book(10,1,false)),1,3000);
        var locations = new BookLocations();
        assertEquals(2,locations.reconcile(List.of(tracked),memory,1).inspectPage());
        stable(memory,menu(4,book(54,1,true)),2,4000);
        assertTrue(locations.reconcile(List.of(tracked),memory,2).unresolved());
        assertEquals(1,tracked.location);assertEquals(10,tracked.slot);
    }
    @Test void productionTransferUsesTheBoundSourceSlotAndWaitsForConfirmedArrival() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,0);tracked.slot=1;
        var transfer = new BookTransfer(); var actions = new RecordingActions();
        var initial = menu(1,book(54,1,true),book(55,1,true)); stable(memory,initial,1,1000);
        assertEquals(BookTransfer.Result.WAITING,transfer.tick(tracked,1,"ec",initial,actions,1300,memory));
        assertEquals(List.of("shiftclick:55"),actions.serverEffects()); assertNotNull(memory.move());
        var sourceGone = menu(1,book(54,1,true)); stable(memory,sourceGone,1,2000);
        assertEquals(BookTransfer.Result.WAITING,transfer.tick(tracked,1,"ec",sourceGone,actions,2300,memory));
        var arrived = menu(1,book(54,1,true),book(14,1,false));memory.observe(arrived,1,2400);
        assertEquals(BookTransfer.Result.WAITING,transfer.tick(tracked,1,"ec",arrived,actions,2400,memory));
        memory.observe(arrived,1,2600);
        assertEquals(BookTransfer.Result.MOVED,transfer.tick(tracked,1,"ec",arrived,actions,2600,memory));
        assertEquals(1,tracked.location); assertEquals(14,tracked.slot); assertNull(memory.move());
        assertEquals(List.of("shiftclick:55"),actions.serverEffects());
    }

    @Test void transferIntentExistsBeforeTheServerClickAndSurvivesAFailedSend() {
        var memory = new InventoryMemory(); var tracked = new BookList(route,1,0);tracked.slot=0;
        var initial = menu(1,book(54,1,true));stable(memory,initial,1,1000);
        GameActions failedSend = new GameActions() {
            public void click(int slot,boolean shift) {
                assertNotNull(memory.move()); assertEquals(0,memory.move().source().slot());
                assertEquals(1,memory.move().destinationRegion());
                throw new IllegalStateException("connection failed");
            }
            public void closeMenu() {} public void command(String text) {} public void message(String text) {}
            public boolean writeSign(String text) { return false; }
        };
        var transfer = new BookTransfer();
        assertThrows(IllegalStateException.class,()->transfer.tick(tracked,1,"ec",initial,failedSend,1300,memory));
        assertTrue(transfer.pending());assertNotNull(memory.move());assertEquals(0,tracked.location);
    }
}
