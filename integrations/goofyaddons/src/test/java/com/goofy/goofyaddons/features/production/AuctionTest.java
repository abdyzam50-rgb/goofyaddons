package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionTest {
    @TempDir Path dir;
    ItemMetadata identity=new ItemMetadata("exact-item-uuid",null,null,null,null,null,null,"minecraft:diamond_sword");
    SlotView item(int index,boolean inventory,String id,int count) {
        return new SlotView(index,inventory,inventory?index-54:index,false,id,id,List.of(),id,Map.of("sharpness",5),count,64,identity);
    }
    SlotView expected(){return item(54,true,"OUTPUT",1);}
    MenuSnapshot menu(int id,String title,SlotView... actual) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:actual)slots.set(s.index(),s);return new MenuSnapshot(id,title,true,slots);
    }
    ProductionJobs jobs()throws Exception {
        var jobs=new ProductionJobs(dir.resolve("jobs.json"));jobs.put(new ProductionJobs.Job("sell","auction:prepare:OUTPUT","account",ProductionJobs.State.OUTPUT_READY,1,-1,0,0,null,null,null,null));return jobs;
    }
    MenuSnapshot form(int id,long price) {
        return menu(id,"Create BIN Auction",item(13,false,"OUTPUT",1),SlotView.named(31,"Item price: "+price+" coins",List.of()),
            SlotView.named(29,"Create BIN Auction",List.of()),SlotView.named(33,"Duration: 2 days",List.of()));
    }
    void advance(BinListingExecutor executor,RecordingActions actions,MenuSnapshot menu,long at){executor.tick(menu,false,actions,"account",10000,10000,at);}
    @Test void directCreationAndExistingAuctionPathBothWorkWithoutClickingExistingListings()throws Exception {
        var jobs=jobs();var actions=new RecordingActions();var executor=new BinListingExecutor(expected(),1000,jobs,"sell");
        var root=menu(1,"Auction House",SlotView.named(15,"Manage Auctions",List.of()),expected());
        advance(executor,actions,root,1000);advance(executor,actions,root,1100);
        var manage=menu(2,"Manage Auctions",item(10,false,"EXISTING_BOW",1),SlotView.named(24,"Create Auction",List.of()),expected());
        advance(executor,actions,manage,1500);
        var bidding=menu(3,"Create Auction",SlotView.named(48,"Switch to BIN",List.of()),expected());
        advance(executor,actions,bidding,1800);advance(executor,actions,bidding,1900);
        var blank=menu(4,"Create BIN Auction",SlotView.named(13,"Click an item in your inventory!",List.of()),expected());
        advance(executor,actions,blank,2200);advance(executor,actions,blank,2400);
        advance(executor,actions,form(4,1000),2600);advance(executor,actions,form(4,1000),2800);
        assertEquals(BinListingExecutor.Result.PREPARED,executor.tick(form(4,1000),false,actions,"account",10000,10000,3000));
        assertEquals(List.of("click:15","click:24","click:48","shiftclick:54"),actions.serverEffects());
        assertEquals(ProductionJobs.State.REVIEW,jobs.find("sell").orElseThrow().state());
        assertNull(jobs.find("sell").orElseThrow().costBasis());
    }
    @Test void ignoredReversibleNavigationRetriesBoundedlyButCannotClickTheExistingBow()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var root=menu(1,"Manage Auctions",item(10,false,"EXISTING_BOW",1),SlotView.named(24,"Create Auction",List.of()),expected());
        advance(executor,actions,root,1000);advance(executor,actions,root,1100);
        advance(executor,actions,root,2000);advance(executor,actions,root,5000);
        assertEquals(List.of("click:24","click:24"),actions.serverEffects());
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(root,false,actions,"account",10000,10000,17000));
    }
    @Test void emptyAuctionHouseCanCreateDirectlyWithoutVisitingManageAuctions()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var root=menu(1,"Auction House",SlotView.named(15,"Create Auction",List.of()),expected());
        advance(executor,actions,root,1000);advance(executor,actions,root,1100);
        var bidding=menu(2,"Create Auction",SlotView.named(48,"Switch to BIN",List.of()),expected());
        advance(executor,actions,bidding,1300);advance(executor,actions,bidding,1400);
        var blank=menu(3,"Create BIN Auction",expected());
        advance(executor,actions,blank,1600);advance(executor,actions,blank,1800);
        advance(executor,actions,form(3,1000),2000);advance(executor,actions,form(3,1000),2200);
        assertEquals(BinListingExecutor.Result.PREPARED,executor.tick(form(3,1000),false,actions,"account",10000,10000,2400));
        assertEquals(List.of("click:15","click:48","shiftclick:54"),actions.serverEffects());
    }
    @Test void itemTransferIsNeverRepeatedWhenServerFailsToMoveIt()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var blank=menu(1,"Create BIN Auction",expected());
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        advance(executor,actions,blank,5000);
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(blank,false,actions,"account",10000,10000,12000));
        assertEquals(List.of("shiftclick:54"),actions.serverEffects());
    }
    @Test void writesThePriceOnceAndRequiresTheExactServerReportedPriceBeforePreparing()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var blank=menu(1,"Create BIN Auction",expected());
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        advance(executor,actions,form(1,500),1500);advance(executor,actions,form(1,500),1600);
        executor.tick(form(1,500),true,actions,"account",10000,10000,1700);
        executor.tick(form(1,500),true,actions,"account",10000,10000,1800);
        advance(executor,actions,form(1,500),2000);
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(form(1,500),false,actions,"account",10000,10000,2100));
        assertEquals(BinListingExecutor.Result.PREPARED,executor.tick(form(1,1000),false,actions,"account",10000,10000,2200));
        assertEquals(List.of("shiftclick:54","click:31","sign:1000"),actions.serverEffects());
    }
    void reachConfirmation(BinListingExecutor executor,RecordingActions actions) {
        var blank=menu(1,"Create BIN Auction",expected());
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        advance(executor,actions,form(1,1000),1500);advance(executor,actions,form(1,1000),1600);advance(executor,actions,form(1,1000),1700);
    }
    MenuSnapshot confirmation(String title,long price,long fee) {
        return menu(2,title,item(13,false,"OUTPUT",1),SlotView.named(11,"Confirm",List.of("Price: "+price+" coins","Creation fee: "+fee+" coins")));
    }
    @Test void publicationSavesIntentThenRequiresNewSellerListingAndExactFeeDebit()throws Exception {
        var jobs=jobs();var executor=new BinListingExecutor(expected(),1000,jobs,"sell",true,50);var actions=new RecordingActions();
        reachConfirmation(executor,actions);
        var confirm=confirmation("Confirm BIN Auction",1000,50);
        advance(executor,actions,confirm,2000);advance(executor,actions,confirm,2500);
        assertEquals(ProductionJobs.State.LISTING,new ProductionJobs(dir.resolve("jobs.json")).find("sell").orElseThrow().state());
        var listed=new SlotView(10,false,10,false,"OUTPUT","OUTPUT",List.of("Buy it now: 1,000 coins"),"OUTPUT",Map.of("sharpness",5),1,64,identity);
        var manage=menu(3,"Manage Auctions",listed,SlotView.named(24,"Create Auction",List.of()));
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(manage,false,actions,"account",10000,10000,2800));
        assertEquals(BinListingExecutor.Result.LISTED,executor.tick(manage,false,actions,"account",9950,9950,3000));
        assertEquals(ProductionJobs.State.SELLING,jobs.find("sell").orElseThrow().state());
        assertNull(jobs.find("sell").orElseThrow().costBasis());
        assertEquals(List.of("shiftclick:54","click:29","click:11"),actions.serverEffects());
    }
    @Test void wrongConfirmationModePriceOrFeeCannotPublish()throws Exception {
        for(var confirm:List.of(confirmation("Confirm Auction",1000,50),confirmation("Confirm BIN Auction",999,50),confirmation("Confirm BIN Auction",1000,51))) {
            var journal=jobs();var executor=new BinListingExecutor(expected(),1000,journal,"sell",true,50);var actions=new RecordingActions();reachConfirmation(executor,actions);
            assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(confirm,false,actions,"account",10000,10000,2000));
            assertFalse(actions.serverEffects().contains("click:11"));
        }
    }
    @Test void restartedListingIntentOrUnrelatedAccountCannotReplayPlacement()throws Exception {
        var journal=jobs();journal.put(journal.find("sell").orElseThrow().withState(ProductionJobs.State.LISTING,"Interrupted"));
        var actions=new RecordingActions();var executor=new BinListingExecutor(expected(),1000,journal,"sell");
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(menu(1,"Create BIN Auction",expected()),false,actions,"account",1000));
        assertTrue(actions.serverEffects().isEmpty());
        var other=new BinListingExecutor(expected(),1000,jobs(),"sell");
        assertEquals(BinListingExecutor.Result.BLOCKED,other.tick(menu(1,"Create BIN Auction",expected()),false,actions,"other",1000));
    }
    @Test void occupiedSellSlotAndAmbiguousCreateControlsAreNotAdopted()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var occupied=menu(1,"Create BIN Auction",item(13,false,"OTHER",1),expected());
        advance(executor,actions,occupied,1000);advance(executor,actions,occupied,1100);
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(occupied,false,actions,"account",1200));assertTrue(actions.serverEffects().isEmpty());
        executor=new BinListingExecutor(expected(),1000,jobs(),"sell");
        var ambiguous=menu(1,"Manage Auctions",SlotView.named(23,"Create Auction",List.of()),SlotView.named(24,"Create BIN Auction",List.of()),expected());
        advance(executor,actions,ambiguous,1000);
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(ambiguous,false,actions,"account",1100));assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void missingDurationOrAmbiguousFeeCannotAuthorizePublication()throws Exception {
        assertNull(ProductionMenus.auctionDuration("Duration: 2 days\nDuration: 1 hour"));
        assertNull(ProductionMenus.listingFee("Creation fee: 50 coins\nCreation fee: 60 coins"));
        assertEquals(172800,ProductionMenus.auctionDuration("Duration: 2 days"));
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell",true,50);var actions=new RecordingActions();
        var blank=menu(1,"Create BIN Auction",expected());
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        var missing=menu(1,"Create BIN Auction",item(13,false,"OUTPUT",1),SlotView.named(31,"Item price: 1000 coins",List.of()),SlotView.named(29,"Create BIN Auction",List.of()));
        advance(executor,actions,missing,1500);advance(executor,actions,missing,1600);
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(missing,false,actions,"account",10000,10000,1700));
        assertFalse(actions.serverEffects().contains("click:29"));
    }
    @Test void unacknowledgedPublicationCannotProduceAnotherFinalClickOrASaleReceipt()throws Exception {
        var jobs=jobs();var executor=new BinListingExecutor(expected(),1000,jobs,"sell",true,50);var actions=new RecordingActions();reachConfirmation(executor,actions);
        var confirm=confirmation("Confirm BIN Auction",1000,50);
        advance(executor,actions,confirm,2000);advance(executor,actions,confirm,7000);
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(confirm,false,actions,"account",9950,9950,17000));
        assertEquals(1,actions.serverEffects().stream().filter(s->s.equals("click:11")).count());
        assertEquals(ProductionJobs.State.REVIEW,jobs.find("sell").orElseThrow().state());
    }
    @Test void packetGateIncludesSellSlotAndMenuCaptureExcludesInventoryAndUuids() {
        var before=form(1,1000);var wrong=menu(1,"Create BIN Auction",item(13,false,"OTHER",1));
        assertFalse(AuctionFeature.sameOwnedState(before,wrong));
        var captured=AuctionCommands.describe(menu(1,"Manage Auctions",item(10,false,"OUTPUT",1),item(54,true,"PRIVATE_INVENTORY",1)));
        assertTrue(captured.contains("Manage Auctions"));assertTrue(captured.contains("OUTPUT"));
        assertFalse(captured.contains("PRIVATE_INVENTORY"));assertFalse(captured.contains("exact-item-uuid"));
    }
}
