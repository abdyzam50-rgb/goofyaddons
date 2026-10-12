package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.production.ProductionStorage.Move;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStorageTest {
    @TempDir Path dir;
    @Test void fullPageWithAnOwnedCompatiblePartialStackStillAcceptsADeposit()throws Exception{
        var world=new World().page("ec 1",1);world.put("ec 1",0,"INPUT",32);
        var ledger=new ProductionStorage(dir.resolve("partial.json"));ledger.move("partial","ec 1","INPUT",32);
        var pages=ProductionStorage.pages("ec 1");var scan=Move.scan(ledger,pages);
        var result=Move.Result.WAITING;for(long now=1000;now<10000&&result==Move.Result.WAITING;now+=100)result=scan.tick(world.menu(),world,now);
        assertEquals(Move.Result.DONE,result);assertEquals(0,ledger.emptySlots(pages));world.hold(0,"INPUT",32);world.commands.clear();
        // The persisted capacity remembers potential partial room after a restart.
        ledger=new ProductionStorage(dir.resolve("partial.json"));var move=Move.deposit("partial",ledger,pages,Set.of("INPUT"));
        result=Move.Result.WAITING;for(long now=10000;now<20000&&result==Move.Result.WAITING;now+=100)result=move.tick(world.menu(),world,now);
        assertEquals(Move.Result.DONE,result);assertEquals(64,ledger.stored("partial","INPUT"));assertEquals(List.of("ec 1"),world.commands);
    }
    @Test void fullPageIsSkippedAndWholeDepositUsesOnePageWithEnoughRoom()throws Exception {
        var world=new World().page("ec 1",4).page("ec 2",6).page("ec 3",6);
        for(int i=0;i<4;i++)world.put("ec 1",i,"COBBLE",64);
        world.put("ec 2",0,"COBBLE",64);world.put("ec 2",1,"COBBLE",64);world.put("ec 2",2,"COBBLE",64);world.put("ec 2",3,"COBBLE",64);
        for(int i=0;i<3;i++)world.hold(i,"INPUT",64);
        var ledger=new ProductionStorage(null);ledger.observe("ec 1",0,1000);ledger.observe("ec 2",2,1000);ledger.observe("ec 3",6,1000);
        var move=Move.deposit("whole-lot",ledger,ProductionStorage.pages("ec 1,ec 2,ec 3"),Set.of("INPUT"));
        var result=Move.Result.WAITING;for(long now=1000;now<10000&&result==Move.Result.WAITING;now+=100)result=move.tick(world.menu(),world,now);
        assertEquals(Move.Result.DONE,result);assertEquals(List.of("ec 3"),world.commands);
        assertEquals(192,ledger.stored("whole-lot","INPUT"));assertEquals(192,world.stored("ec 3","INPUT"));assertEquals(0,world.stored("ec 2","INPUT"));
    }
    @Test void aCachedFullPageIsRecheckedAfterItsCapacityExpires()throws Exception {
        var world=new World().page("ec 1",4);world.hold(0,"INPUT",64);
        var ledger=new ProductionStorage(null);ledger.observe("ec 1",0,1000);
        var move=Move.deposit("refresh",ledger,ProductionStorage.pages("ec 1"),Set.of("INPUT"));
        var result=Move.Result.WAITING;for(long now=121001;now<130000&&result==Move.Result.WAITING;now+=100)result=move.tick(world.menu(),world,now);
        assertEquals(Move.Result.DONE,result);assertEquals(List.of("ec 1"),world.commands);assertEquals(64,ledger.stored("refresh","INPUT"));
    }
    @Test void scanningTheNextPageDoesNotInheritThePreviousPagesRetryDeadline()throws Exception {
        var old=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE;
        try {
            var cfg=new com.goofy.goofyaddons.config.GoofyConfig();cfg.minActionDelay=100;cfg.maxActionDelay=101;
            com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=cfg;
            var world=new World().page("ec 1",45).page("ec 2",45);var ledger=new ProductionStorage(null);
            var pages=ProductionStorage.pages("ec 1,ec 2");var scan=Move.scan(ledger,pages);
            var result=Move.Result.WAITING;
            for(long now=1000;now<=3000&&result==Move.Result.WAITING;now+=100)result=scan.tick(world.menu(),world,now);
            assertEquals(Move.Result.DONE,result);assertEquals(List.of("ec 1","ec 2"),world.commands);
            assertEquals(90,ledger.emptySlots(pages));assertEquals(0,world.clicks);
        }finally{com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=old;}
    }
    @Test void acknowledgedStorageClicksUseSharedDelayWithoutAnotherFixedSettle()throws Exception {
        var old=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE;
        try {
            var cfg=new com.goofy.goofyaddons.config.GoofyConfig();cfg.minActionDelay=1000;cfg.maxActionDelay=1001;
            com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=cfg;
            var world=new World().page("ec 1",4);world.hold(0,"INPUT",64);world.hold(1,"INPUT",64);
            var move=Move.deposit("timing",new ProductionStorage(null),ProductionStorage.pages("ec 1"),Set.of("INPUT"));
            move.tick(world.menu(),world,1000);move.tick(world.menu(),world,1100);move.tick(world.menu(),world,1500);
            assertEquals(1,world.clicks);
            move.tick(world.menu(),world,1501);move.tick(world.menu(),world,2499);assertEquals(1,world.clicks);
            move.tick(world.menu(),world,2500);assertEquals(2,world.clicks);
        }finally{com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=old;}
    }

    /** A tiny Hypixel: storage pages open by command and shift-clicks move whole stacks. */
    static final class World implements GameActions {
        final Map<String,String[]> pageIds=new LinkedHashMap<>();
        final Map<String,int[]> pageCounts=new LinkedHashMap<>();
        final Set<String> locked=new HashSet<>();
        final Set<String> single=new HashSet<>();
        final String[] invIds=new String[36];
        final int[] invCounts=new int[36];
        String open;
        int containerId;
        final List<String> commands=new ArrayList<>();
        int clicks;
        boolean dropClicks;
        World page(String command,int size){pageIds.put(command,new String[size]);pageCounts.put(command,new int[size]);return this;}
        void put(String page,int slot,String id,int n){pageIds.get(page)[slot]=id;pageCounts.get(page)[slot]=n;}
        void hold(int slot,String id,int n){invIds[slot]=id;invCounts[slot]=n;}
        int inventory(String id){int t=0;for(int i=0;i<36;i++)if(id.equals(invIds[i]))t+=invCounts[i];return t;}
        int stored(String page,String id){int t=0;var ids=pageIds.get(page);for(int i=0;i<ids.length;i++)if(id.equals(ids[i]))t+=pageCounts.get(page)[i];return t;}
        MenuSnapshot menu() {
            var slots=new ArrayList<SlotView>();int size=0;
            if(open!=null) {
                var ids=pageIds.get(open);size=ids.length;
                for(int i=0;i<size;i++)slots.add(slot(i,false,i,ids[i],pageCounts.get(open)[i]));
            }
            for(int i=0;i<36;i++)slots.add(slot(size+i,true,i,invIds[i],invCounts[i]));
            String title=open==null?null:open.startsWith("ec")?"Ender Chest ("+open.substring(3)+"/3)":"Jumbo Backpack (Slot #"+open.substring(9)+")";
            return new MenuSnapshot(containerId,title,true,slots);
        }
        private SlotView slot(int index,boolean inventory,int containerSlot,String id,int n) {
            return id==null?SlotView.empty(index,inventory,containerSlot):new SlotView(index,inventory,containerSlot,false,id,id,List.of(),id,null,n,single.contains(id)?1:64);
        }
        public void click(int slot,boolean shift) {
            clicks++;if(dropClicks || open==null)return;
            var ids=pageIds.get(open);var counts=pageCounts.get(open);
            if(slot<ids.length) {
                if(ids[slot]==null)return;
                int moved=insert(invIds,invCounts,ids[slot],counts[slot]);counts[slot]-=moved;if(counts[slot]==0)ids[slot]=null;
            } else {
                int i=slot-ids.length;if(invIds[i]==null)return;
                int moved=insert(ids,counts,invIds[i],invCounts[i]);invCounts[i]-=moved;if(invCounts[i]==0)invIds[i]=null;
            }
        }
        private int insert(String[] ids,int[] counts,String id,int n) {
            int left=n;
            int cap=single.contains(id)?1:64;
            for(int i=0;i<ids.length && left>0;i++)if(id.equals(ids[i])){int add=Math.min(cap-counts[i],left);counts[i]+=add;left-=add;}
            for(int i=0;i<ids.length && left>0;i++)if(ids[i]==null){ids[i]=id;counts[i]=Math.min(cap,left);left-=counts[i];}
            return n-left;
        }
        public void closeMenu(){open=null;}
        public void command(String text){commands.add(text);if(pageIds.containsKey(text) && !locked.contains(text)){open=text;containerId++;}}
        public void message(String text){}
        public boolean writeSign(String text){return false;}
    }

    static Move.Result run(Move move,World world,long[] clock,int limit) {
        Move.Result result=Move.Result.WAITING;
        for(int i=0;i<limit && result==Move.Result.WAITING;i++){result=move.tick(world.menu(),world,clock[0]);clock[0]+=500;}
        return result;
    }

    @Test void pagesParseTheDefaultStorageOrder() {
        var pages=ProductionStorage.pages(ProductionStorage.DEFAULT_PAGES);
        assertEquals(List.of("ec 1","ec 2","ec 3","backpack 1","backpack 2","backpack 3","backpack 4"),pages.stream().map(ProductionStorage.Page::command).toList());
        assertEquals(List.of("ec 2","backpack 9"),ProductionStorage.pages("/ec 2, nonsense, bp 9, ec 2").stream().map(ProductionStorage.Page::command).toList());
    }

    @Test void depositFillsPagesInOrderAndRecordsEveryUnit()throws Exception {
        var world=new World().page("ec 1",2).page("ec 2",3);
        for(int i=0;i<4;i++)world.hold(i,"MITHRIL",64);
        world.hold(5,"OTHER",10);
        var ledger=new ProductionStorage(dir.resolve("storage.json"));
        var move=Move.deposit("run-1",ledger,ProductionStorage.pages("ec 1,ec 2"),Set.of("MITHRIL"));
        assertEquals(Move.Result.DONE,run(move,world,new long[]{1000},200));
        assertEquals(0,world.inventory("MITHRIL"));assertEquals(10,world.inventory("OTHER"));
        assertEquals(128,world.stored("ec 1","MITHRIL"));assertEquals(128,world.stored("ec 2","MITHRIL"));
        assertEquals(256,ledger.stored("run-1","MITHRIL"));assertEquals(256,move.moved());
        var reloaded=new ProductionStorage(dir.resolve("storage.json"));
        assertEquals(128,reloaded.owned("ec 2","MITHRIL"),"The ledger survives a restart");
    }

    @Test void depositReportsNoSpaceWhenEveryPageIsFull()throws Exception {
        var world=new World().page("ec 1",1);world.put("ec 1",0,"COBBLE",64);
        world.hold(0,"MITHRIL",64);world.hold(1,"MITHRIL",64);
        var ledger=new ProductionStorage(null);
        assertEquals(Move.Result.NO_SPACE,run(Move.deposit("run-1",ledger,ProductionStorage.pages("ec 1"),Set.of("MITHRIL")),world,new long[]{1000},100));
        assertEquals(128,world.inventory("MITHRIL"));assertEquals(0,ledger.stored("run-1","MITHRIL"));
    }

    @Test void depositNeverMixesWithThePlayersOwnUnits()throws Exception {
        var world=new World().page("ec 1",5).page("ec 2",5);world.put("ec 1",0,"MITHRIL",3);
        world.hold(0,"MITHRIL",64);
        var ledger=new ProductionStorage(null);
        assertEquals(Move.Result.DONE,run(Move.deposit("run-1",ledger,ProductionStorage.pages("ec 1,ec 2"),Set.of("MITHRIL")),world,new long[]{1000},100));
        assertEquals(3,world.stored("ec 1","MITHRIL"));assertEquals(64,world.stored("ec 2","MITHRIL"));
        assertEquals(0,ledger.owned("ec 1","MITHRIL"));assertEquals(64,ledger.owned("ec 2","MITHRIL"));
    }

    @Test void lockedPageIsSkipped()throws Exception {
        var world=new World().page("ec 1",2).page("backpack 1",2);world.locked.add("ec 1");
        world.hold(0,"MITHRIL",64);
        var ledger=new ProductionStorage(null);
        var move=Move.deposit("run-1",ledger,ProductionStorage.pages("ec 1,backpack 1"),Set.of("MITHRIL"));
        assertEquals(Move.Result.DONE,run(move,world,new long[]{1000},100));
        assertEquals(64,world.stored("backpack 1","MITHRIL"));
        assertEquals(Set.of("ec 1"),move.skippedPages());
    }

    @Test void aScanThatSkipsALockedPageStillGivesThePlannerAFreeSpaceTotal()throws Exception {
        var world=new World().page("ec 1",2).page("backpack 1",2);world.locked.add("ec 1");
        var ledger=new ProductionStorage(null);var pages=ProductionStorage.pages("ec 1,backpack 1");
        assertEquals(-1,ledger.emptySlots(pages));
        assertEquals(Move.Result.DONE,run(Move.scan(ledger,pages),world,new long[]{1000},100));
        assertTrue(ledger.emptySlots(pages)>=0,"An unopened page must not leave storage unread, or every selection rescans");
    }

    @Test void withdrawBringsBackWhatTheRunNeedsAndUpdatesTheLedger()throws Exception {
        var world=new World().page("ec 1",4).page("ec 2",4);
        world.hold(0,"MITHRIL",64);world.hold(1,"MITHRIL",64);world.hold(2,"MITHRIL",64);
        var ledger=new ProductionStorage(null);var pages=ProductionStorage.pages("ec 1,ec 2");
        assertEquals(Move.Result.DONE,run(Move.deposit("run-1",ledger,pages,Set.of("MITHRIL")),world,new long[]{1000},100));
        var take=Move.withdraw("run-1",ledger,pages,Map.of("MITHRIL",100));
        assertEquals(Move.Result.DONE,run(take,world,new long[]{100000},100));
        assertEquals(128,world.inventory("MITHRIL"));assertEquals(64,ledger.stored("run-1","MITHRIL"));
        assertEquals(64,world.stored("ec 1","MITHRIL")+world.stored("ec 2","MITHRIL"));
    }

    @Test void changedPageContentsStopTheMoveBeforeAnyClick()throws Exception {
        var world=new World().page("ec 1",4);world.hold(0,"MITHRIL",64);
        var ledger=new ProductionStorage(null);var pages=ProductionStorage.pages("ec 1");
        assertEquals(Move.Result.DONE,run(Move.deposit("run-1",ledger,pages,Set.of("MITHRIL")),world,new long[]{1000},100));
        world.put("ec 1",0,"MITHRIL",10); // The player took some out by hand.
        int clicks=world.clicks;
        var take=Move.withdraw("run-1",ledger,pages,Map.of("MITHRIL",64));
        assertEquals(Move.Result.BLOCKED,run(take,world,new long[]{100000},100));
        assertTrue(take.failure().contains("should hold 64"),take.failure());assertEquals(clicks,world.clicks);
        assertEquals(64,ledger.stored("run-1","MITHRIL"));
    }

    @Test void unacknowledgedClickNeverChangesTheLedger()throws Exception {
        var world=new World().page("ec 1",4);world.hold(0,"MITHRIL",64);world.dropClicks=true;
        var ledger=new ProductionStorage(null);
        var move=Move.deposit("run-1",ledger,ProductionStorage.pages("ec 1"),Set.of("MITHRIL"));
        assertEquals(Move.Result.BLOCKED,run(move,world,new long[]{1000},100));
        assertEquals(0,ledger.stored("run-1","MITHRIL"));assertTrue(world.clicks<=3,"At most two retries: "+world.clicks);
    }

    @Test void scanRecordsEmptySlotsForPlanning()throws Exception {
        var world=new World().page("ec 1",4).page("backpack 2",3);world.put("ec 1",0,"COBBLE",64);
        var ledger=new ProductionStorage(null);var pages=ProductionStorage.pages("ec 1,backpack 2");
        assertEquals(-1,ledger.emptySlots(pages));
        assertEquals(Move.Result.DONE,run(Move.scan(ledger,pages),world,new long[]{1000},100));
        assertEquals(6,ledger.emptySlots(pages));assertNull(world.open);
    }
}
