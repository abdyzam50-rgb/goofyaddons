package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationTest {
    @TempDir Path dir;
    ProductionRecipe forge(){return new ProductionRecipe("forge:OUTPUT:0",ProductionRecipe.Kind.FORGE,"OUTPUT",1,Map.of("INPUT",2),List.of(),60,0,"HotM 2",null);}
    SlotView item(int i,String id,int n,List<String> lore){return new SlotView(i,i>=54,i>=54?i-54:i,false,id,id,lore,id,null,n,64);}
    MenuSnapshot menu(int id,String title,SlotView... actual) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:actual)slots.set(s.index(),s);return new MenuSnapshot(id,title,true,slots);
    }
    MenuSnapshot confirm(){return menu(2,"Confirm Process",item(10,"INPUT",2,List.of()),item(23,"OUTPUT",1,List.of()),
            SlotView.named(16,"Confirm",List.of("Click to confirm!")),item(54,"INPUT",2,List.of()));}
    @Test void forgeNavigationSearchesCategoriesAndStopsBeforeSubmitting() {
        var nav=new ForgeNavigation(forge(),"OUTPUT",0);var actions=new RecordingActions();
        nav.tick(menu(1,"The Forge",SlotView.named(10,"Slot #1",List.of("Click to forge!"))),actions,1000);
        nav.tick(menu(2,"Select Process",SlotView.named(11,"Refine Items",List.of()),SlotView.named(12,"Item Casting",List.of())),actions,1500);
        nav.tick(menu(3,"Item Casting",SlotView.named(48,"Go Back",List.of())),actions,2000);
        nav.tick(menu(4,"Select Process",SlotView.named(11,"Refine Items",List.of()),SlotView.named(12,"Item Casting",List.of())),actions,2500);
        nav.tick(menu(5,"Refine Items",item(10,"OUTPUT",1,List.of())),actions,3000);
        nav.tick(confirm(),actions,3500);
        assertEquals(ForgeNavigation.State.READY,nav.state(),nav.failure());
        assertEquals(List.of("click:10","click:12","click:48","click:11","click:10"),actions.serverEffects());
        assertFalse(actions.serverEffects().contains("click:16"));
    }
    @Test void occupiedForgeSlotAndConflictingConfirmationCannotAuthorizeNavigationOrSubmission() {
        var nav=new ForgeNavigation(forge(),"OUTPUT",0);var actions=new RecordingActions();
        nav.tick(menu(1,"The Forge",item(10,"OTHER",1,List.of("Time Remaining: 1m"))),actions,1000);
        assertEquals(ForgeNavigation.State.FAILED,nav.state());assertTrue(actions.serverEffects().isEmpty());
        assertFalse(ProductionMenus.forgeConfirmation(menu(2,"Confirm Process",item(10,"INPUT",3,List.of()),item(23,"OUTPUT",1,List.of())),forge()));
    }
    @Test void unchangedServerObservationCannotTriggerASecondCraftAction() {
        var r=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",2),
            Arrays.asList(new ProductionRecipe.Ingredient("INPUT",2),null,null,null,null,null,null,null,null),0,0,"",null);
        var source=menu(1,"Craft Item",item(54,"INPUT",2,List.of()));
        var confirmed=new MenuSnapshot(1,source.title(),true,source.slots(),source.carried(),7);
        var execute=new CraftingExecutor();var actions=new RecordingActions();execute.tick(r,confirmed,actions,Map.of(),1000);
        execute.tick(r,confirmed,actions,Map.of(),4000);execute.tick(r,confirmed,actions,Map.of(),7000);
        assertEquals(1,actions.serverEffects().size());
        assertEquals(CraftingExecutor.Result.BLOCKED,execute.tick(r,confirmed,actions,Map.of(),9500));
    }
    @Test void forgeSubmissionIsPersistedBeforeClickAndClaimRequiresServerInventoryProof()throws Exception {
        var jobs=new ProductionJobs(dir.resolve("jobs.json"));
        jobs.put(new ProductionJobs.Job("job",forge().key(),"account",ProductionJobs.State.PLANNED,1,0,0,0,100.0,null,null,null));
        var actions=new RecordingActions();var execute=new WorkstationExecutor(forge(),"job",jobs,null);
        assertEquals(WorkstationExecutor.Result.WAITING,execute.tick(confirm(),actions,Map.of(),"account",1000,0,1000));
        assertEquals(ProductionJobs.State.SUBMITTING,new ProductionJobs(dir.resolve("jobs.json")).find("job").orElseThrow().state());
        assertEquals(List.of("click:16"),actions.serverEffects());
        var working=menu(3,"The Forge",item(10,"OUTPUT",1,List.of("Time Remaining: 1m")));
        assertEquals(WorkstationExecutor.Result.SUBMITTED,execute.tick(working,actions,Map.of(),"account",1000,0,2000));
        assertEquals(62000,jobs.find("job").orElseThrow().readyAt());
        var complete=menu(3,"The Forge",item(10,"OUTPUT",1,List.of("Time Remaining: Completed!")));
        assertEquals(WorkstationExecutor.Result.WAITING,execute.tick(complete,actions,Map.of(),"account",1000,0,3000));
        assertEquals(ProductionJobs.State.CLAIMING,jobs.find("job").orElseThrow().state());
        assertEquals(List.of("click:16","click:10"),actions.serverEffects());
        assertEquals(WorkstationExecutor.Result.CLAIMED,execute.tick(menu(3,"The Forge",item(54,"OUTPUT",1,List.of())),actions,Map.of(),"account",1000,0,3500));
        assertEquals(ProductionJobs.State.OUTPUT_READY,jobs.find("job").orElseThrow().state());
    }
    @Test void katUsesExactPetAndActualDiscountedChargeThenVerifiesClaimedRarity()throws Exception {
        var recipe=new ProductionRecipe("katgrade:BLUE_WHALE;4:0",ProductionRecipe.Kind.KAT,"BLUE_WHALE;4",1,
            Map.of("BLUE_WHALE;3",1,"MATERIAL",8),List.of(),60,9000000,"","BLUE_WHALE;3");
        var pet=new ItemMetadata("pet-uuid","BLUE_WHALE","EPIC",100.0,null,null,0,null);
        var upgraded=new ItemMetadata("pet-uuid","BLUE_WHALE","LEGENDARY",100.0,null,null,0,null);
        var input=new SlotView(13,false,13,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,pet);
        var output=new SlotView(13,false,13,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,upgraded);
        var claimed=new SlotView(54,true,0,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,upgraded);
        var jobs=new ProductionJobs(dir.resolve("kat.json"));
        jobs.put(new ProductionJobs.Job("kat",recipe.key(),"account",ProductionJobs.State.PLANNED,1,-1,0,0,1000000.0,pet.uuid(),null,null));
        var actions=new RecordingActions();var execute=new WorkstationExecutor(recipe,"kat",jobs,pet);
        assertEquals(WorkstationExecutor.Result.WAITING,execute.tick(menu(1,"Pet Sitter",input,item(54,"MATERIAL",8,List.of()),
            SlotView.named(22,"Upgrade Pet",List.of("Cost: 4,500,000 coins","Click to upgrade!"))),actions,Map.of(),"account",20000000,4500000,1000));
        assertEquals(WorkstationExecutor.Result.SUBMITTED,execute.tick(menu(1,"Pet Sitter",input,
            SlotView.named(22,"Upgrading",List.of("Time Remaining: 10s"))),actions,Map.of(),"account",15500000,4500000,1500));
        assertEquals(5500000,jobs.find("kat").orElseThrow().costBasis());
        assertEquals(WorkstationExecutor.Result.WAITING,execute.tick(menu(1,"Pet Sitter",output,
            SlotView.named(22,"Collect Pet",List.of("Time Remaining: Completed!","Click to collect!"))),actions,Map.of(),"account",15500000,4500000,2000));
        assertEquals(WorkstationExecutor.Result.CLAIMED,execute.tick(menu(1,"Pet Sitter",claimed),actions,Map.of(),"account",15500000,4500000,2500));
        assertEquals(List.of("click:22","click:22"),actions.serverEffects());
    }
    @Test void interruptedSubmissionOrWrongAccountNeverReplaysAnIrreversibleClick()throws Exception {
        var jobs=new ProductionJobs(dir.resolve("jobs.json"));
        jobs.put(new ProductionJobs.Job("job",forge().key(),"account",ProductionJobs.State.SUBMITTING,1,0,0,0,null,null,null,null));
        var actions=new RecordingActions();var execute=new WorkstationExecutor(forge(),"job",jobs,null);
        assertEquals(WorkstationExecutor.Result.BLOCKED,execute.tick(confirm(),actions,Map.of(),"other",1000,0,1000));
        assertEquals(WorkstationExecutor.Result.BLOCKED,execute.tick(confirm(),actions,Map.of(),"account",1000,0,2000));assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void timerParsingDistinguishesPredictionsFromCompletedJobsAndIgnoresInventoryImpostors() {
        var m=menu(1,"The Forge",item(10,"OUTPUT",1,List.of("Time Remaining: 2d 3h 4m 5s")),item(11,"OTHER",1,List.of("Time Remaining: Completed!")),item(54,"OUTPUT",1,List.of("Time Remaining: Completed!")));
        var rows=ProductionMenus.forgeSlots(m);assertEquals(2,rows.size());assertEquals(183845,rows.getFirst().remainingSeconds());assertFalse(rows.getFirst().complete());assertTrue(rows.get(1).complete());
        assertTrue(ProductionMenus.forgeSlots(menu(1,"Storage",item(10,"OUTPUT",1,List.of("Time Remaining: Completed!")))).isEmpty());
    }
    @Test void binGateRejectsAuctionsExpiredListingsWrongPetUuidOrPrice() {
        var identity=new ItemMetadata("pet-uuid","BLUE_WHALE","EPIC",10.0,null,null,0,null);
        var listing=new ProductionMenus.BinListing("a".repeat(32),"BLUE_WHALE;3",identity,1,1000,1000,60000);
        var pet=new SlotView(13,false,13,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,identity);
        var view=menu(1,"BIN Auction View",pet,SlotView.named(31,"Buy Item",List.of("Price: 1,000 coins")));
        assertTrue(ProductionMenus.binPurchase(view,listing,2000,1000,2000));
        assertFalse(ProductionMenus.binPurchase(view,listing,40000,1000,2000));
        assertFalse(ProductionMenus.binPurchase(view,listing,2000,999,2000));
        assertFalse(ProductionMenus.binPurchase(menu(1,"Auction View",pet,SlotView.named(31,"Bid",List.of("Price: 1,000 coins"))),listing,2000,1000,2000));
        var other=new SlotView(13,false,13,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,new ItemMetadata("other","BLUE_WHALE","EPIC",10.0,null,null,0,null));
        assertFalse(ProductionMenus.binPurchase(menu(1,"BIN Auction View",other,SlotView.named(31,"Buy Item",List.of("Price: 1,000 coins"))),listing,2000,1000,2000));
        assertNull(ProductionMenus.exactCoins("Price: 1,000 coins\nCost: 2,000 coins"));
        assertNull(ProductionMenus.exactCoins("Price: 1M coins"));
    }
    @Test void exactBinPurchasePersistsIntentAndVerifiesItemPlusActualDebit()throws Exception {
        var identity=new ItemMetadata("pet-uuid","BLUE_WHALE","EPIC",10.0,null,null,0,null);
        var listing=new ProductionMenus.BinListing("a".repeat(32),"BLUE_WHALE;3",identity,1,1000,1000,60000);
        var jobs=new ProductionJobs(dir.resolve("bin.json"));jobs.put(new ProductionJobs.Job("buy","bin:pet","account",ProductionJobs.State.PLANNED,1,-1,0,0,null,identity.uuid(),listing.auctionUuid(),null));
        var pet=new SlotView(13,false,13,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,identity);
        var actions=new RecordingActions();var executor=new BinPurchaseExecutor(listing,jobs,"buy");
        executor.tick(menu(1,null),false,actions,"account",5000,1000,5000,1000);
        var view=menu(2,"BIN Auction View",pet,SlotView.named(31,"Buy Item",List.of("Price: 1,000 coins")));
        executor.tick(view,false,actions,"account",5000,1000,5000,1500);
        executor.tick(view,false,actions,"account",5000,1000,5000,1800);
        assertEquals(ProductionJobs.State.BUYING,new ProductionJobs(dir.resolve("bin.json")).find("buy").orElseThrow().state());
        var confirm=menu(3,"Confirm Purchase",pet,SlotView.named(11,"Confirm",List.of("Price: 1,000 coins")));
        executor.tick(confirm,false,actions,"account",5000,1000,5000,2000);
        executor.tick(confirm,false,actions,"account",5000,1000,5000,2100);
        var acquired=new SlotView(54,true,0,false,"Blue Whale","Blue Whale",List.of(),"PET",null,1,1,identity);
        assertEquals(BinPurchaseExecutor.Result.PURCHASED,executor.tick(menu(0,null,acquired),false,actions,"account",4000,1000,4000,2300));
        assertEquals(List.of("command:viewauction "+listing.auctionUuid(),"click:31","click:11"),actions.serverEffects());
        assertEquals(1000,jobs.find("buy").orElseThrow().costBasis());assertEquals(ProductionJobs.State.OUTPUT_READY,jobs.find("buy").orElseThrow().state());
    }
    @Test void restartedBinIntentNeverClicksThePurchaseControlAgain()throws Exception {
        var identity=new ItemMetadata("pet-uuid","BLUE_WHALE","EPIC",10.0,null,null,0,null);
        var listing=new ProductionMenus.BinListing("a".repeat(32),"BLUE_WHALE;3",identity,1,1000,1000,60000);
        var jobs=new ProductionJobs(dir.resolve("bin.json"));jobs.put(new ProductionJobs.Job("buy","bin:pet","account",ProductionJobs.State.BUYING,1,-1,0,0,null,identity.uuid(),listing.auctionUuid(),null));
        var actions=new RecordingActions();var executor=new BinPurchaseExecutor(listing,jobs,"buy");
        assertEquals(BinPurchaseExecutor.Result.BLOCKED,executor.tick(menu(1,null),false,actions,"account",5000,1000,5000,1500));assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void binCreationChecksActualItemQuantityAndPriceInsteadOfOnlyTitle() {
        var target=item(54,"OUTPUT",1,List.of());
        assertTrue(ProductionMenus.binCreation(menu(1,"Create BIN Auction",item(13,"OUTPUT",1,List.of()),SlotView.named(31,"Item price: 1,000 coins",List.of())),target,1000));
        assertFalse(ProductionMenus.binCreation(menu(1,"Create Auction",item(13,"OUTPUT",1,List.of()),SlotView.named(31,"Item price: 1,000 coins",List.of())),target,1000));
        assertFalse(ProductionMenus.binCreation(menu(1,"Create BIN Auction",item(13,"OTHER",1,List.of()),SlotView.named(31,"Item price: 1,000 coins",List.of())),target,1000));
    }
    @Test void productionPricingConsumesRealDepthAndNeverMarksResearchAsExecutable() {
        var market=JsonParser.parseString("""
            {"lastUpdated":1000,"products":{"INPUT":{"buy_summary":[{"pricePerUnit":10,"amount":1},{"pricePerUnit":20,"amount":1}]},
            "OUTPUT":{"buy_summary":[{"pricePerUnit":100,"amount":5}]}}}
            """).getAsJsonObject();
        var rows=ProductionPlanner.bazaar(market,List.of(forge()),2000,1000,1,1,10,Set.of(),Set.of(forge().key()));
        assertEquals(1,rows.size());assertEquals(31.2,rows.getFirst().inputCost(),1e-9);assertFalse(rows.getFirst().executable());
        assertTrue(ProductionPlanner.bazaar(market,List.of(forge()),2000,31,1,1,10,Set.of(),Set.of()).isEmpty());
        market.getAsJsonObject("products").getAsJsonObject("INPUT").getAsJsonArray("buy_summary").remove(1);
        assertTrue(ProductionPlanner.bazaar(market,List.of(forge()),2000,1000,1,1,10,Set.of(),Set.of()).isEmpty());
    }
    @Test void packetMirrorDoesNotAdoptLocalPredictionsAndRequiresFullInitialContent() {
        var store=new ConfirmedMenuStore();var before=menu(1,"Craft Item",item(54,"INPUT",2,List.of()));
        assertNull(store.read(1,"Craft Item"));store.content(1,before.slots(),before.carried());
        var confirmed=store.read(1,"Craft Item");var predicted=menu(1,"Craft Item",item(10,"INPUT",2,List.of()));
        assertFalse(CraftingExecutor.sameOwnedState(confirmed,predicted));
        assertEquals(2,store.read(1,"Craft Item").slot(54).count());
        store.slot(1,54,SlotView.empty(54,true,0));store.slot(1,10,item(10,"INPUT",2,List.of()));
        assertTrue(CraftingExecutor.sameOwnedState(store.read(1,"Craft Item"),predicted));
        assertTrue(store.read(1,"Craft Item").serverObservation()>confirmed.serverObservation());
        store.clear();assertNull(store.read(1,"Craft Item"));
    }
}
