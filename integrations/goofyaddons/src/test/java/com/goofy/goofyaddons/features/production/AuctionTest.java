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
    MenuSnapshot captured(String name,int id)throws Exception {
        var raw=CompactorClearanceTest.captured(name);
        return menu(id,raw.title(),raw.slots().toArray(SlotView[]::new));
    }
    @Test void capturedCoopRootAndEmptyBinFormNavigateAndInsertOnce()throws Exception {
        var actions=new RecordingActions();var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");
        var root=captured("auction-coop-root.json",1);var slots=new ArrayList<>(root.slots());slots.set(54,expected());root=new MenuSnapshot(1,root.title(),true,slots);
        advance(executor,actions,root,1000);advance(executor,actions,root,1100);
        var blank=captured("auction-create-bin-empty.json",2);slots=new ArrayList<>(blank.slots());slots.set(54,expected());blank=new MenuSnapshot(2,blank.title(),true,slots);
        advance(executor,actions,blank,1600);advance(executor,actions,blank,1800);
        assertEquals(List.of("click:15","shiftclick:54"),actions.serverEffects());
    }
    @Test void delayedCreateControlAndConfirmationLoadWithoutRepeatingTheTransfer()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell",true,50);var a=new RecordingActions();
        var loading=menu(1,"Auction House",expected());
        advance(executor,a,loading,1000);advance(executor,a,loading,1100);
        assertTrue(a.serverEffects().isEmpty());
        advance(executor,a,menu(1,"Auction House",expected(),SlotView.named(15,"Create BIN Auction",List.of())),4000);
        var blank=menu(2,"Create BIN Auction",expected());
        advance(executor,a,blank,5000);advance(executor,a,blank,5100);
        advance(executor,a,form(2,1000),5200);advance(executor,a,form(2,1000),5300);advance(executor,a,form(2,1000),5400);
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(menu(3,"Confirm BIN Auction"),false,a,"account",10000,10000,5500));
        advance(executor,a,confirmation("Confirm BIN Auction",1000,50),8000);
        assertEquals(List.of("click:15","shiftclick:54","click:29","click:11"),a.serverEffects());
    }
    @Test void capturedCompactConfirmationBindsToVerifiedFormAndOwnBinViewProvesPublication()throws Exception {
        var target=new SlotView(54,true,0,false,"Aspect of the End","Aspect of the End",List.of(),"ASPECT_OF_THE_END",Map.of(),1,1,identity);
        var journal=new ProductionJobs(dir.resolve("aote.json"));journal.put(new ProductionJobs.Job("aote","auction:prepare:ASPECT_OF_THE_END","account",ProductionJobs.State.OUTPUT_READY,1,-1,0,0,null,null,null,null));
        var executor=new BinListingExecutor(target,56000,journal,"aote",true,605);var actions=new RecordingActions();
        var blank=menu(1,"Create BIN Auction",target);
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        var sell=new SlotView(13,false,13,false,target.hoverName(),target.hoverName(),List.of(),target.customId(),target.enchantments(),1,1,identity);
        var form=menu(1,"Create BIN Auction",sell,SlotView.named(31,"Item price: 56,000 coins",List.of()),
            SlotView.named(29,"Create BIN Auction",List.of()),SlotView.named(33,"Duration: 6 Hours",List.of()));
        advance(executor,actions,form,1500);advance(executor,actions,form,1600);advance(executor,actions,form,1700);
        var confirmation=captured("auction-confirm-bin-compact.json",2);
        advance(executor,actions,confirmation,2000);assertEquals(ProductionJobs.State.LISTING,journal.find("aote").orElseThrow().state());
        assertEquals(List.of("shiftclick:54","click:29","click:11"),actions.serverEffects());
        var view=captured("auction-own-bin-view.json",3);var actual=view.slot(13);var slots=new ArrayList<>(view.slots());
        slots.set(13,new SlotView(13,false,13,false,actual.hoverName(),actual.hoverName(),actual.loreLines(),actual.customId(),Map.of(),1,1,identity));
        view=new MenuSnapshot(3,view.title(),true,slots);
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(view,false,actions,"account",10000,10000,2400));
        var wrongSlots=new ArrayList<>(view.slots());var listed=view.slot(13);
        wrongSlots.set(13,new SlotView(13,false,13,false,listed.hoverName(),listed.hoverName(),listed.loreLines(),listed.customId(),Map.of(),1,1,
            new ItemMetadata("different-uuid",null,null,null,null,null,null,"minecraft:diamond_sword")));
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(new MenuSnapshot(3,view.title(),true,wrongSlots),false,actions,"account",9395,9395,2500));
        wrongSlots=new ArrayList<>(view.slots());wrongSlots.set(13,new SlotView(13,false,13,false,listed.hoverName(),listed.hoverName(),
            List.of("Buy it now: 56,000 coins"),listed.customId(),Map.of(),1,1,identity));
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(new MenuSnapshot(3,view.title(),true,wrongSlots),false,actions,"account",9395,9395,2600));
        assertEquals(BinListingExecutor.Result.LISTED,executor.tick(view,false,actions,"account",9395,9395,2700));
        assertEquals(ProductionJobs.State.SELLING,journal.find("aote").orElseThrow().state());assertNull(journal.find("aote").orElseThrow().costBasis());
        assertEquals(List.of("shiftclick:54","click:29","click:11"),actions.serverEffects());
    }
    @Test void compactConfirmationCannotOverrideWrongNamePriceOrChangedInventory()throws Exception {
        var target=new SlotView(54,true,0,false,"Aspect of the End","Aspect of the End",List.of(),"ASPECT_OF_THE_END",Map.of(),1,1,identity);
        var sell=new SlotView(13,false,13,false,target.hoverName(),target.hoverName(),List.of(),target.customId(),Map.of(),1,1,identity);
        var form=menu(1,"Create BIN Auction",sell,SlotView.named(31,"Item price: 56,000 coins",List.of()));
        var confirm=captured("auction-confirm-bin-compact.json",2);
        assertTrue(ProductionMenus.compactBinPublication(confirm,target,56000,form));
        assertFalse(ProductionMenus.compactBinPublication(confirm,target,56001,form));assertFalse(ProductionMenus.compactBinPublication(confirm,target,56000,null));
        assertFalse(ProductionMenus.compactBinPublication(new MenuSnapshot(1,confirm.title(),true,confirm.slots()),target,56000,form));
        var slots=new ArrayList<>(confirm.slots());slots.set(11,SlotView.named(11,"Confirm BIN Auction",List.of("Selling: Aspect of the Void","Cost: 605 coins","Click to confirm!")));
        assertFalse(ProductionMenus.compactBinPublication(new MenuSnapshot(2,confirm.title(),true,slots),target,56000,form));
        slots=new ArrayList<>(confirm.slots());slots.set(54,item(54,true,"OTHER",1));
        assertFalse(ProductionMenus.compactBinPublication(new MenuSnapshot(2,confirm.title(),true,slots),target,56000,form));
        slots=new ArrayList<>(confirm.slots());slots.set(11,SlotView.named(11,"Confirm BIN Auction",List.of("Selling: Aspect of the End","Price: 1 coins","Cost: 605 coins","Click to confirm!")));
        assertFalse(ProductionMenus.compactBinPublication(new MenuSnapshot(2,confirm.title(),true,slots),target,56000,form));
    }
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
    @Test void expectedPriceSignIsWrittenOnceBeforeCheckingTheReturnedFormCursor()throws Exception {
        var executor=new BinListingExecutor(expected(),1000,jobs(),"sell");var actions=new RecordingActions();
        var blank=menu(1,"Create BIN Auction",expected());
        advance(executor,actions,blank,1000);advance(executor,actions,blank,1100);advance(executor,actions,blank,1200);
        advance(executor,actions,form(1,500),1500);advance(executor,actions,form(1,500),1600);
        var sign=new MenuSnapshot(1,"Edit Sign Message",false,form(1,500).slots());
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(sign,true,actions,"account",10000,10000,1700));
        executor.tick(sign,true,actions,"account",10000,10000,1800);
        assertEquals(List.of("shiftclick:54","click:31","sign:1000"),actions.serverEffects());
        var returned=new MenuSnapshot(1,"Create BIN Auction",false,form(1,1000).slots());
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(returned,false,actions,"account",10000,10000,2000));
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(returned,false,actions,"account",10000,10000,17000));
        assertFalse(actions.performed().contains("click:29"));
    }
    @Test void salePriceUsesFreshCoflnetReferenceOrValidatesTheExplicitPriceBeforeCreating() {
        var quote=new AuctionPricing.Quote("ASPECT_OF_THE_END",56000,57000L,1000);
        assertEquals(55999,AuctionFeature.salePrice(quote,"ASPECT_OF_THE_END",1,true,1100));
        assertEquals(56000,AuctionFeature.salePrice(quote,"ASPECT_OF_THE_END",56000,false,1100));
        assertThrows(IllegalArgumentException.class,()->AuctionFeature.salePrice(quote,"OTHER",56000,true,1100));
        assertThrows(IllegalArgumentException.class,()->AuctionFeature.salePrice(quote,"ASPECT_OF_THE_END",56000,true,400000));
        assertThrows(IllegalArgumentException.class,()->AuctionFeature.salePrice(quote,"ASPECT_OF_THE_END",1,false,1100));
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
        assertEquals(BinListingExecutor.Result.WAITING,executor.tick(missing,false,actions,"account",10000,10000,1700));
        assertEquals(BinListingExecutor.Result.BLOCKED,executor.tick(missing,false,actions,"account",10000,10000,16700));
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
