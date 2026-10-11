package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.production.ProductionLoop.Stage;
import com.goofy.goofyaddons.features.production.ProductionLoop.Step;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionRunTest {
    @Test void productionAcceptsFullSixtyFourBatchCraftPlanAndRejectsAboveTheExecutorLimit()throws Exception {
        var env=new Env();var catalog=catalog();var recipe=catalog.forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog,recipe,64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        assertEquals(64,run.outputUnits());assertEquals(64,env.jobs.find(run.jobId()).orElseThrow().batches());
        env.buying=true;assertEquals(Step.PENDING,run.tick(true,1000));assertEquals(List.of("BUY:INPUT:128"),env.orders);
        int jobs=env.jobs.all().size();
        assertThrows(IllegalArgumentException.class,()->ProductionRun.startCraft(env,catalog,recipe,65,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER));
        assertEquals(jobs,env.jobs.all().size(),"Rejected quantities must not create a parent or issue actions");
        assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void everyMissingIngredientIsOrderedBeforeWaitingForAnyFill()throws Exception {
        var env=new Env();env.buying=true;
        var recipe=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT_A",2,"INPUT_B",3),
            Arrays.asList(new ProductionRecipe.Ingredient("INPUT_A",2),new ProductionRecipe.Ingredient("INPUT_B",3),null,null,null,null,null,null,null),0,0,"",null);
        var run=ProductionRun.startCraft(env,new RecipeCatalog(List.of(recipe),Map.of()),recipe,3,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        assertEquals(Step.PENDING,run.tick(true,1000));assertEquals(Set.of("BUY:INPUT_A:6","BUY:INPUT_B:9"),new HashSet<>(env.orders));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,15,750,null,"Orders filling in parallel");
        run.tick(false,1100);assertEquals(2,env.orders.size());assertEquals(0,run.spent());assertEquals(0,env.orderAcknowledgements);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,15,750,null,"All claims verified");
        env.menu=menu(null,item(54,"INPUT_A",6),item(55,"INPUT_B",9));run.tick(false,1200);
        assertEquals(750,run.spent());assertEquals(1,env.orderAcknowledgements);assertTrue(env.crafts.isEmpty());
    }
    @Test void stoppedSubmittedSaleResumesOriginalParentWithoutBuyingOrCrafting()throws Exception {
        var env=new Env();var catalog=catalog();
        var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow().withCost(100.0,"Verified inputs").withState(ProductionJobs.State.REVIEW,"Production stopped; inspect inventory");env.jobs.put(job);
        var resumed=ProductionRun.resumeSale(env,catalog,job,"OUTPUT",1);
        assertEquals(original.jobId(),resumed.jobId());assertEquals(Stage.SELL,resumed.stage());assertEquals(100,resumed.spent());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,1,0,null,"Checking saved offer");
        assertEquals(Step.PENDING,resumed.tick(true,1000));assertTrue(env.orders.isEmpty());assertTrue(env.crafts.isEmpty());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,1,0,500.0,"Verified sale proceeds");
        assertEquals(Step.DONE,resumed.tick(true,1100));assertEquals(List.of(500.0),env.confirmedSales);
        assertTrue(env.confirmedCosts.isEmpty());assertEquals(1,env.orderAcknowledgements);
    }
    @Test void unsafeReviewOrMismatchedQuantityCannotResumeSavedSale()throws Exception {
        var env=new Env();var catalog=catalog();
        var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow().withState(ProductionJobs.State.REVIEW,"Production stopped; inspect inventory");
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job,"OUTPUT",2));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job,"OTHER",1));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job.withState(ProductionJobs.State.REVIEW,"Sale claim is uncertain"),"OUTPUT",1));
        assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void genericInterruptedSaleRequiresAnIntactSubmittedChildProof()throws Exception {
        var env=new Env();var catalog=catalog();
        var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow().withCost(100.0,"Verified inputs")
            .withState(ProductionJobs.State.REVIEW,"Interrupted operation; verify live inventory, workstation and listings");env.jobs.put(job);
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job,"OUTPUT",1));
        var resumed=ProductionRun.resumeSale(env,catalog,job,"OUTPUT",1,true);
        assertEquals(original.jobId(),resumed.jobId());assertEquals(Stage.SELL,resumed.stage());assertEquals(100,resumed.spent());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,1,0,null,"Checking saved offer");
        assertEquals(Step.PENDING,resumed.tick(true,1000));assertTrue(env.orders.isEmpty());assertTrue(env.crafts.isEmpty());
        assertTrue(env.confirmedCosts.isEmpty());assertTrue(env.confirmedSales.isEmpty());
    }
    @Test void submittedChildProofCannotBypassManualReviewAccountRecipeOrQuantityChecks()throws Exception {
        var env=new Env();var catalog=catalog();
        var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow()
            .withState(ProductionJobs.State.REVIEW,"Interrupted operation; verify live inventory, workstation and listings");
        var anotherAccount=new ProductionJobs.Job(job.id(),job.recipeKey(),"another-account",job.state(),job.batches(),job.workstationSlot(),
            job.submittedAt(),job.readyAt(),job.costBasis(),job.petUuid(),job.auctionUuid(),job.reason(),job.completedBatches());
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,anotherAccount,"OUTPUT",1,true));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job,"OTHER",1,true));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job,"OUTPUT",2,true));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog,job.withState(ProductionJobs.State.REVIEW,"Sale claim is uncertain"),"OUTPUT",1,true));
        assertTrue(env.orders.isEmpty());assertTrue(env.crafts.isEmpty());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void restoredSellOfferReleasesConsumedInputsWithoutAdoptingLaterSiblingSupplies()throws Exception {
        var env=new Env();var catalog=catalog();
        var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow().withCost(100.0,"Verified inputs").withState(ProductionJobs.State.LISTING,"Submitted sell offer");env.jobs.put(job);
        var seller=ProductionRun.resumeSale(env,catalog,job,"OUTPUT",1);
        assertEquals(Set.of("OUTPUT"),seller.lockedProducts());
        var siblingRecipe=new ProductionRecipe("craft:OTHER:0",ProductionRecipe.Kind.CRAFT,"OTHER",1,Map.of("INPUT",2),
            Arrays.asList(new ProductionRecipe.Ingredient("INPUT",2),null,null,null,null,null,null,null,null),0,0,"",null);
        var siblingCatalog=new RecipeCatalog(List.of(siblingRecipe),Map.of());
        env.occupied=seller.lockedProducts();env.buying=true;
        var sibling=ProductionRun.startCraft(env,siblingCatalog,siblingRecipe,1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        assertTrue(sibling.hasVerifiedInputBasis());assertEquals(Step.PENDING,sibling.tick(true,1000));
        assertEquals(List.of("BUY:INPUT:2"),env.orders);
        env.menu=menu(null,item(54,"INPUT",2));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,2,100,null,"Sibling claims verified");
        assertEquals(Step.PENDING,sibling.tick(false,1100));assertEquals(100,sibling.spent());
        assertEquals(Set.of("OUTPUT"),seller.lockedProducts(),"A seller must not adopt ingredients purchased later by another run");
        assertEquals(100,seller.spent());assertTrue(env.confirmedSales.isEmpty());
    }
    @Test void sellBoundaryRetainsLeftoverIngredientOwnershipAndUnknownInventoryStaysConservative()throws Exception {
        for(boolean readable:List.of(false,true)) {
            var env=new Env();var catalog=catalog();
            var original=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
            var job=env.jobs.find(original.jobId()).orElseThrow().withCost(100.0,"Verified inputs").withState(ProductionJobs.State.LISTING,"Submitted sell offer");env.jobs.put(job);
            env.menu=readable?menu(null,item(54,"INPUT",1)):null;
            var seller=ProductionRun.resumeSale(env,catalog,job,"OUTPUT",1);
            assertEquals(Set.of("INPUT","OUTPUT"),seller.lockedProducts());
            assertEquals(100,seller.spent());assertTrue(env.orders.isEmpty());assertTrue(env.crafts.isEmpty());
        }
    }
    @Test void procurementAllowsRoundingDustButStillBlocksARealCapitalShortfall()throws Exception {
        for(boolean shortfall:List.of(false,true)) {
            var env=new Env();env.buying=true;env.purse=shortfall?102.99:Math.nextDown(103.0);
            var catalog=catalog();var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
            assertEquals(shortfall?Step.BLOCKED:Step.PENDING,run.tick(true,1000));
            assertEquals(shortfall?List.of():List.of("BUY:INPUT:2"),env.orders);
            assertTrue(env.actions.serverEffects().isEmpty());
        }
    }
    @Test void mutationAndEssenceInputsBlockBeforeProcurementDespiteEmptyRecipeText()throws Exception {
        for(String input:List.of("CHORUS_FRUIT","ESSENCE_WITHER")) {
            var env=new Env();env.buying=true;env.unlocks=Map.of("catacombs",19);
            var grid=Arrays.asList(new ProductionRecipe.Ingredient(input,1),null,null,null,null,null,null,null,null);
            var recipe=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of(input,1),grid,0,0,"",null);
            var run=ProductionRun.start(env,new RecipeCatalog(List.of(recipe),Map.of()),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
            assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(run.reason().contains(input),run.reason());
            assertTrue(env.actions.serverEffects().isEmpty());assertTrue(env.crafts.isEmpty());
        }
    }

    @TempDir Path dir;
    @Test void activeCompactorConflictStopsBeforeBuyingAnyRawIngredients()throws Exception {
        var env=new Env();env.buying=true;env.unlocks=Map.of("goldingot",4);
        var device=PersonalCompactorsTest.device(54,true,7000,true,Map.of(0,"ENCHANTED_GOLD_BLOCK"));
        env.menu=ProductionRunTest.menu(null,device);
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_GOLD",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(run.reason().contains("Personal Compactor"),run.reason());
        assertTrue(env.actions.serverEffects().isEmpty());assertTrue(env.crafts.isEmpty());
    }

    static SlotView item(int i,String id,int n){return new SlotView(i,i>=54,i>=54?i-54:i,false,id,id,List.of(),id,null,n,64);}
    static MenuSnapshot menu(String title,SlotView... actual) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:actual)slots.set(s.index(),s);return new MenuSnapshot(1,title,true,slots);
    }
    static RecipeCatalog catalog() {
        var craft=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",2),
                Arrays.asList(new ProductionRecipe.Ingredient("INPUT",2),null,null,null,null,null,null,null,null),0,0,"",null);
        var forge=new ProductionRecipe("forge:REFINED:0",ProductionRecipe.Kind.FORGE,"REFINED",1,Map.of("INPUT",2),List.of(),60,0,"",null);
        return new RecipeCatalog(List.of(craft,forge),Map.of("INPUT","Input","OUTPUT","Output","REFINED","Refined"));
    }

    final class Env implements ProductionRun.Environment {
        ProductionJobs jobs;MenuSnapshot menu=ProductionRunTest.menu(null);boolean sign,buying,craftQueued,listingQueued;
        double purse=10_000;final RecordingActions actions=new RecordingActions();final List<String> crafts=new ArrayList<>(),listings=new ArrayList<>();
        Set<String> occupied=Set.of(),unquoted=Set.of();
        com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result orderResult;
        final List<String> orders=new ArrayList<>();int orderAcknowledgements;
        double orderUnit=50;final List<double[]> orderPrices=new ArrayList<>();
        public Double orderBuyCost(String id,int units){return units*orderUnit;}
        Set<String> unorderable=Set.of();
        public boolean orderable(String id){return !unorderable.contains(id);}
        public Double offerSellValue(String id,int units){return units*2000.0;}
        public boolean queueOrder(String id,int units,double price,boolean buy,double limit){orders.add((buy?"BUY":"SELL")+":"+id+":"+units);orderPrices.add(new double[]{price,limit});return true;}
        public boolean queueIngredientOrders(List<com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.IngredientOrder> basket){
            basket.forEach(o->queueOrder(o.product(),o.units(),o.price(),true,o.limit()));return true;
        }
        public com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result tickOrder(boolean owns){return orderResult;}
        public boolean acknowledgeOrder(){orderAcknowledgements++;return true;}
        final List<Double> confirmedCosts=new ArrayList<>(),confirmedSales=new ArrayList<>();
        public void outputConfirmed(String id,String output,int units,Double cost){confirmedCosts.add(cost);}
        public void saleConfirmed(String id,String output,int units,double proceeds){confirmedSales.add(proceeds);}
        Map<String,Integer> unlocks=Map.of("hotm",10);boolean requirementsPending;String procurementBlock,pinnedKey,craftActivity="Crafting";
        Env()throws Exception{jobs=new ProductionJobs(dir.resolve("jobs.json"));}
        public ProductionJobs jobs(){return jobs;}
        public String account(){return "account";}
        public MenuSnapshot menu(){return menu;}
        public boolean signOpen(){return sign;}
        public GameActions actions(){return actions;}
        public double purse(){return purse;}
        public double spendable(){return purse;}
        public Map<String,Integer> skills(){return Map.of();}
        public Map<String,Integer> unlocks(){return unlocks;}
        public boolean requirementsPending(){return requirementsPending;}
        public Set<String> occupied(){return occupied;}
        public boolean buyingAllowed(){return buying;}
        public String procurementBlock(){return procurementBlock;}
        public String queueCraftRecipe(String output,int batches,String key){pinnedKey=key;return queueCraft(output,batches);}
        public Double instantBuyCost(String id,int units){return unquoted.contains(id)?null:units*100.0;}
        public Double instantSellValue(String id,int units){return units*1000.0;}
        public String name(String id){return catalog().name(id);}
        public String queueCraft(String output,int batches){
            try{String id="craft-"+crafts.size();jobs.put(new ProductionJobs.Job(id,"craft:OUTPUT:0","account",ProductionJobs.State.PLANNED,batches,-1,0,0,null,null,null,null));
                crafts.add(output+"x"+batches);craftQueued=true;return id;}catch(Exception e){return null;}
        }
        public boolean craftQueued(){return craftQueued;}
        public String craftActivity(){return craftActivity;}
        public String queueListing(String product,long price,double fee){
            try{String id="list-"+listings.size();jobs.put(new ProductionJobs.Job(id,"auction:prepare:"+product,"account",ProductionJobs.State.OUTPUT_READY,1,-1,0,0,null,null,null,null));
                listings.add(product+"@"+price);listingQueued=true;return id;}catch(Exception e){return null;}
        }
        public boolean listingQueued(){return listingQueued;}
        void finish(String id,ProductionJobs.State state)throws Exception{jobs.put(jobs.find(id).orElseThrow().withState(state,null));}
    }

    @Test void craftingPrerequisitesAreCheckedBeforeBuyingAnyIngredients()throws Exception {
        var env=new Env();env.buying=true;env.requirementsPending=true;
        var base=catalog().forOutput("OUTPUT").getFirst();
        var recipe=new ProductionRecipe(base.key(),base.kind(),base.outputId(),base.outputCount(),base.ingredients(),base.grid(),0,0,"Diamond IV",null);
        var run=ProductionRun.start(env,new RecipeCatalog(List.of(recipe),Map.of()),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertEquals(com.goofy.goofyaddons.features.production.ProductionLoop.Step.PENDING,run.tick(true,1000));
        assertTrue(env.actions.serverEffects().isEmpty());assertTrue(run.reason().contains("prerequisites"));
        env.requirementsPending=false;env.unlocks=Map.of("diamond",3);
        assertEquals(com.goofy.goofyaddons.features.production.ProductionLoop.Step.BLOCKED,run.tick(true,2000));
        assertTrue(env.actions.serverEffects().isEmpty());assertTrue(run.reason().contains("Diamond"));
        env.unlocks=Map.of("diamond",4);
        assertEquals(com.goofy.goofyaddons.features.production.ProductionLoop.Step.PENDING,run.tick(true,3000));
        assertTrue(run.reason().contains("Buying"));
        run.tick(true,4000);assertFalse(env.actions.serverEffects().isEmpty());
    }
    @Test void delegatedCraftCapacityWaitAppearsInTheParentWithoutRepeatedQueueing()throws Exception {
        var env=new Env();env.menu=menu(null,item(54,"INPUT",2));
        var run=ProductionRun.startCraft(env,catalog(),catalog().forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0);
        run.tick(false,1000);run.tick(false,1100);assertEquals(List.of("OUTPUTx1"),env.crafts);
        env.craftActivity="Waiting for output stack size: Output";
        assertEquals(Step.PENDING,run.tick(false,1200));assertEquals(env.craftActivity,run.reason());
        assertEquals(Stage.PROCESS,run.stage());assertEquals(List.of("OUTPUTx1"),env.crafts);
        assertTrue(env.orders.isEmpty());assertTrue(env.confirmedCosts.isEmpty());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void eyeIngredientsAreCraftedFromRodsInJournaledChunksBeforeFinalCraft()throws Exception {
        var env=new Env();env.unlocks=Map.of("enderpearl",6);
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",32),item(55,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertFalse(run.wantsMenu());run.tick(false,0);
        assertEquals(List.of("BLAZE_POWDERx32"),env.crafts);assertEquals(Stage.PROCURE,run.stage());
        assertEquals(ProductionJobs.State.PROCESSING,env.jobs.find(run.jobId()).orElseThrow().state());
        assertTrue(run.lockedProducts().contains("BLAZE_ROD"));
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=ProductionRunTest.menu(null,item(55,"ENCHANTED_ENDER_PEARL",16),item(56,"BLAZE_POWDER",64));
        run.tick(false,1);run.tick(false,2);assertEquals(List.of("BLAZE_POWDERx32"),env.crafts);assertEquals(Stage.PROCESS,run.stage());
        run.tick(false,3);assertEquals("ENCHANTED_EYE_OF_ENDERx1",env.crafts.getLast());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void cleanCraftMenuStaysOwnedAcrossIntermediateAndFinalRecipeHandoffs() throws Exception {
        var env=new Env();env.unlocks=Map.of("enderpearl",6);
        env.menu=menu("Craft Item",item(54,"BLAZE_ROD",32),item(55,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertTrue(run.wantsMenu());run.tick(true,0);assertFalse(run.wantsMenu(),"The active child crafter must own the menu");
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=menu("Craft Item",item(55,"ENCHANTED_ENDER_PEARL",16),item(56,"BLAZE_POWDER",64));
        assertTrue(run.wantsMenu(),"Retain the clean menu while verifying the child");run.tick(true,1);run.tick(true,2);
        assertEquals(Stage.PROCESS,run.stage());assertTrue(run.wantsMenu(),"Retain it until the final craft is queued");run.tick(true,3);
        assertFalse(run.wantsMenu());assertEquals(List.of("BLAZE_POWDERx32","ENCHANTED_EYE_OF_ENDERx1"),env.crafts);
        assertTrue(env.actions.performed().isEmpty(),"A recipe handoff must not close or reopen the crafting GUI");
    }
    @Test void reusedCraftMenuClosesBeforeAuctionListingOnlyWithOwnership()throws Exception {
        var env=new Env();env.menu=menu(null,item(54,"INPUT",2));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,5000,100);
        run.tick(false,0);run.tick(false,1);
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=menu("Craft Item",item(54,"OUTPUT",1));run.tick(true,2);
        assertEquals(Stage.SELL,run.stage());assertTrue(run.wantsMenu());
        run.tick(false,3);assertTrue(env.actions.performed().isEmpty());
        run.tick(true,4);assertEquals(List.of("close"),env.actions.performed());assertTrue(env.listings.isEmpty());
        env.menu=menu(null,item(54,"OUTPUT",1));run.tick(false,5);
        assertEquals(List.of("OUTPUT@5000"),env.listings);
    }
    @Test void clearsUnrelatedFiltersBeforeAnyProductionPurchase()throws Exception {
        var env=new Env();env.buying=true;var filters=Map.of(0,"UNRELATED_RECIPE");
        env.menu=CompactorClearanceTest.menu("Personal Compactor 7000",false,filters,filters);
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertTrue(run.wantsMenu());run.tick(false,0);assertTrue(env.actions.performed().isEmpty());
        run.tick(true,0);assertEquals(List.of("click:10"),env.actions.performed());assertTrue(env.crafts.isEmpty());
        assertEquals(ProductionJobs.State.PROCESSING,env.jobs.find(run.jobId()).orElseThrow().state());
        run.tick(true,100);assertEquals(List.of("click:10"),env.actions.performed());
        env.menu=CompactorClearanceTest.menu("Personal Compactor 7000",false,Map.of(),filters);
        run.tick(true,600);assertEquals(List.of("click:10","close"),env.actions.performed());
        env.menu=CompactorClearanceTest.menu(null,false,Map.of(),filters);
        run.tick(true,1200);assertEquals(List.of("click:10","close"),env.actions.performed());
        env.menu=CompactorClearanceTest.menu(null,false,Map.of(),Map.of());
        run.tick(true,1800);run.tick(true,2400);
        assertEquals(List.of("click:10","command:bz Input"),env.actions.serverEffects());
    }
    @Test void marketMoveCannotStrandCompactorVerificationOrPermitANewPurchase()throws Exception {
        var env=new Env();env.buying=true;var filters=Map.of(0,"UNRELATED_RECIPE");
        env.menu=CompactorClearanceTest.menu("Personal Compactor 7000",false,filters,filters);
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        run.tick(true,0);assertEquals(List.of("click:10"),env.actions.performed());
        env.procurementBlock="Fresh market data unavailable";
        run.tick(true,100);assertTrue(run.wantsMenu());
        env.menu=CompactorClearanceTest.menu("Personal Compactor 7000",false,Map.of(),filters);
        run.tick(true,600);assertEquals(List.of("click:10","close"),env.actions.performed());
        env.menu=CompactorClearanceTest.menu(null,false,Map.of(),Map.of());
        assertEquals(Step.BLOCKED,run.tick(true,1800));assertFalse(run.wantsMenu());
        assertEquals(List.of("click:10"),env.actions.serverEffects());
        env.procurementBlock=null;run.tick(false,1900);assertTrue(run.wantsMenu());
        run.tick(true,2000);assertEquals(List.of("click:10","command:bz Input"),env.actions.serverEffects());
    }
    @Test void occupiedBaseIngredientsCannotBeConsumedByPreparation()throws Exception {
        var env=new Env();env.occupied=Set.of("BLAZE_ROD");
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",1));
        var grid=Arrays.asList(new ProductionRecipe.Ingredient("BLAZE_POWDER",2),null,null,null,null,null,null,null,null);
        var target=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("BLAZE_POWDER",2),grid,0,0,"",null);
        var recipes=new ArrayList<>(RecipeCatalog.instance().recipes());recipes.add(target);
        var run=ProductionRun.start(env,new RecipeCatalog(recipes,Map.of()),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertEquals(Step.BLOCKED,run.tick(false,0));assertTrue(run.reason().contains("BLAZE_ROD"));assertTrue(env.crafts.isEmpty());
    }
    @Test void missingPowderBuysRodsEvenWithoutAPowderQuote()throws Exception {
        var env=new Env();env.buying=true;env.unlocks=Map.of("enderpearl",6);env.unquoted=Set.of("BLAZE_POWDER");
        env.menu=ProductionRunTest.menu(null,item(54,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertTrue(run.wantsMenu());run.tick(true,0);assertTrue(run.reason().contains("32 BLAZE_ROD"),run.reason());
        run.tick(true,1);assertEquals(List.of("command:bz BLAZE_ROD"),env.actions.serverEffects());
        assertTrue(env.crafts.isEmpty());
    }
    @Test void preparationPreservesPaperNeededByTheFinalRecipe()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"PAPER",3),item(55,"LEATHER",1));
        var grid=Arrays.asList(new ProductionRecipe.Ingredient("BOOK",1),new ProductionRecipe.Ingredient("PAPER",3),null,null,null,null,null,null,null);
        var target=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("BOOK",1,"PAPER",3),grid,0,0,"",null);
        var recipes=new ArrayList<>(RecipeCatalog.instance().recipes());recipes.add(target);
        var run=ProductionRun.start(env,new RecipeCatalog(recipes,Map.of()),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertEquals(Step.BLOCKED,run.tick(false,0));assertTrue(run.reason().contains("SUGAR_CANE"),run.reason());
        assertTrue(env.crafts.isEmpty());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void failedIntermediateCraftRequiresReviewAndCannotStartTheFinalCraft()throws Exception {
        var env=new Env();env.unlocks=Map.of("enderpearl",6);
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",32),item(55,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,5000,100);
        run.tick(false,0);env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.REVIEW);
        assertEquals(Step.UNCERTAIN,run.tick(false,1));assertEquals(Stage.REVIEW,run.stage());
        assertEquals(List.of("BLAZE_POWDERx32"),env.crafts);assertTrue(env.listings.isEmpty());
    }

    @Test void craftRunFromHeldInputsCraftsThenListsAndRecordsEveryBoundary()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"INPUT",4));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,2,-1,5000,100);
        assertEquals(Step.PENDING,run.tick(false,0));
        assertEquals(Stage.PROCESS,run.stage());
        run.tick(false,1);
        assertEquals(List.of("OUTPUTx2"),env.crafts);
        run.tick(false,2);assertEquals(Stage.PROCESS,run.stage());
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        run.tick(false,3);assertEquals(Stage.SELL,run.stage());
        assertEquals(ProductionJobs.State.OUTPUT_READY,env.jobs.find(run.jobId()).orElseThrow().state());
        run.tick(false,4);assertEquals(List.of("OUTPUT@5000"),env.listings);
        env.listingQueued=false;env.finish("list-0",ProductionJobs.State.SELLING);
        assertEquals(Step.DONE,run.tick(false,5));
        assertEquals(ProductionJobs.State.DONE,env.jobs.find(run.jobId()).orElseThrow().state());
        assertTrue(env.actions.serverEffects().isEmpty());
    }

    @Test void missingInputsBlockWhileBuyingIsOff()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"INPUT",1));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertFalse(run.wantsMenu());
        assertEquals(Step.BLOCKED,run.tick(true,0));
        assertTrue(run.reason().contains("Missing 1 Input"),run.reason());
        assertTrue(env.actions.serverEffects().isEmpty());
        env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));
        run.tick(false,1);assertEquals(Stage.PROCESS,run.stage());
    }

    @Test void buyingOnlyClicksWhileOwningTheMenuAndRecordsIntentFirst()throws Exception {
        var env=new Env();env.buying=true;env.menu=ProductionRunTest.menu(null,item(54,"INPUT",1));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertTrue(run.wantsMenu());
        run.tick(false,0);run.tick(false,1);
        assertTrue(env.actions.serverEffects().isEmpty());
        run.tick(true,2);
        assertEquals(List.of("command:bz Input"),env.actions.serverEffects());
        env.menu=ProductionRunTest.menu("Input",item(13,"INPUT",1),SlotView.named(10,"Buy Instantly",List.of("Input","Price per unit: 100 coins")),item(54,"INPUT",1));
        run.tick(true,3);
        env.menu=ProductionRunTest.menu("How many do you want?",SlotView.named(16,"Custom Amount",List.of()),item(54,"INPUT",1));
        run.tick(true,4);
        env.sign=true;run.tick(true,5);env.sign=false;
        assertEquals(ProductionJobs.State.BUYING,env.jobs.find(run.jobId()).orElseThrow().state());
        env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));env.purse=9_900;
        run.tick(true,6);
        run.tick(false,7);
        assertEquals(Stage.PROCESS,run.stage());
        assertEquals(100.0,env.jobs.find(run.jobId()).orElseThrow().costBasis());
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.startsWith("sign:")).count());
    }

    @Test void failedCraftSendsTheRunToReview()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,5000,0);
        run.tick(false,0);run.tick(false,1);
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.REVIEW);
        assertEquals(Step.UNCERTAIN,run.tick(false,2));
        assertEquals(Stage.REVIEW,run.stage());
        assertEquals(ProductionJobs.State.REVIEW,env.jobs.find(run.jobId()).orElseThrow().state());
        assertTrue(env.listings.isEmpty());
    }

    @Test void forgeUnlocksAndSlotCapacityBlockBeforeProcurement()throws Exception {
        var env=new Env();env.buying=true;env.unlocks=Map.of();
        var run=ProductionRun.start(env,catalog(),"REFINED",ProductionRecipe.Kind.FORGE,1,2,0,0);
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(env.actions.serverEffects().isEmpty());
        env.unlocks=Map.of("hotm",2);
        assertEquals(Step.BLOCKED,run.tick(true,2000));assertTrue(run.reason().contains("slot"));assertTrue(env.actions.serverEffects().isEmpty());
        env.unlocks=Map.of("hotm",3);
        assertEquals(Step.PENDING,run.tick(true,3000));assertTrue(run.reason().contains("Buying"));
    }
    @Test void forgeCatalogThresholdIsHigherThanBasicWorkstationAccess()throws Exception {
        var env=new Env();env.buying=true;env.unlocks=Map.of("hotm",2);
        var r=new ProductionRecipe("forge:REFINED:0",ProductionRecipe.Kind.FORGE,"REFINED",1,Map.of("INPUT",2),List.of(),60,0,"HotM 6 & Wolf Slayer 3",null);
        var run=ProductionRun.start(env,new RecipeCatalog(List.of(r),Map.of()),"REFINED",ProductionRecipe.Kind.FORGE,1,0,0,0);
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(env.actions.serverEffects().isEmpty());
        env.unlocks=Map.of("hotm",6,"wolfslayer",2);assertEquals(Step.BLOCKED,run.tick(true,2000));
        env.unlocks=Map.of("hotm",6,"wolfslayer",3);assertEquals(Step.PENDING,run.tick(true,3000));
    }
    @Test void forgeRunWaitsForThePlayerToOpenTheForgeAndProvesSubmission()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));
        var run=ProductionRun.start(env,catalog(),"REFINED",ProductionRecipe.Kind.FORGE,1,0,0,0);
        run.tick(false,0);
        assertEquals(Stage.PROCESS,run.stage());
        assertFalse(run.wantsMenu());
        assertEquals(Step.PENDING,run.tick(false,1));
        assertEquals("Open The Forge to submit",run.reason());
        env.menu=ProductionRunTest.menu("The Forge",SlotView.named(10,"Slot #1",List.of("Click to forge!")),item(54,"INPUT",2));
        assertTrue(run.wantsMenu());
        run.tick(true,2);
        assertEquals(List.of("click:10"),env.actions.serverEffects());
    }

    @Test void occupiedOutputCannotStartARun()throws Exception {
        var env=new Env();env.occupied=Set.of("OUTPUT");
        assertThrows(IllegalArgumentException.class,()->ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,0,0));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.start(new Env(),catalog(),"REFINED",ProductionRecipe.Kind.FORGE,2,0,0,0));
    }

    @Test void testListingFeeCeilingFollowsTheAuctionHouseRateBands() {
        assertEquals(1_250,ProductionPlanner.listingFeeLimit(5_000));
        assertEquals(101_200,ProductionPlanner.listingFeeLimit(9_999_999));
        assertEquals(201_200,ProductionPlanner.listingFeeLimit(10_000_000));
        assertEquals(2_501_200,ProductionPlanner.listingFeeLimit(100_000_000));
        assertThrows(IllegalArgumentException.class,()->ProductionPlanner.listingFeeLimit(0));
    }

    @Test void bazaarSaleSellsTheCraftedOutputOnceAndProvesIt()throws Exception {
        var env=new Env();env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));
        var run=ProductionRun.start(env,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,ProductionRun.SELL_ON_BAZAAR,0);
        run.tick(false,0);run.tick(false,1);
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=ProductionRunTest.menu(null,item(54,"OUTPUT",1));
        run.tick(false,2);assertEquals(Stage.SELL,run.stage());
        assertTrue(run.wantsMenu());
        run.tick(false,3);assertTrue(env.actions.serverEffects().isEmpty(),"no click without the menu");
        run.tick(true,4);assertEquals(List.of("command:bz Output"),env.actions.serverEffects());
        env.menu=ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 990 coins")),item(54,"OUTPUT",1));
        run.tick(true,5);
        assertTrue(env.actions.serverEffects().contains("click:11"));
        assertEquals(ProductionJobs.State.LISTING,env.jobs.find(run.jobId()).orElseThrow().state());
        env.menu=ProductionRunTest.menu(null);env.purse+=978;
        assertEquals(Step.DONE,run.tick(true,6));
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.equals("click:11")).count());
        assertTrue(env.listings.isEmpty());
    }

    @Test void bazaarSaleRefusesWhenTheOutputIsAlreadyHeldOrThePriceIsLow()throws Exception {
        var held=new Env();held.menu=ProductionRunTest.menu(null,item(54,"INPUT",2),item(55,"OUTPUT",3));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.start(held,catalog(),"OUTPUT",ProductionRecipe.Kind.CRAFT,1,-1,ProductionRun.SELL_ON_BAZAAR,0));
        var actions=new RecordingActions();
        var sale=new BazaarInstantSell("OUTPUT","Output",1,970,r->{});
        var page=ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 900 coins")),item(54,"OUTPUT",1));
        assertEquals(BazaarInstantSell.Result.BLOCKED,sale.tick(page,actions,1000,0));
        assertTrue(actions.serverEffects().isEmpty());
        var confirm=new BazaarInstantSell("OUTPUT","Output",1,900,r->{});
        confirm.tick(ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 950 coins")),item(54,"OUTPUT",1)),actions,1000,0);
        assertEquals(BazaarInstantSell.Result.UNCERTAIN,confirm.tick(ProductionRunTest.menu("Confirm",item(54,"OUTPUT",1)),actions,1000,100));
    }

    @Test void bazaarSaleKnowsTheProductByItsIconWhenTheControlDoesNotNameIt() {
        var actions=new RecordingActions();
        var sale=new BazaarInstantSell("OUTPUT","Output",1,900,r->{});
        var page=ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Inventory: 1 item","","Amount: 1x","Total: 950 coins")),item(54,"OUTPUT",1));
        sale.tick(page,actions,1000,0);
        assertEquals(List.of("click:11"),actions.serverEffects());
        assertEquals(950.0,BazaarInstantSell.quotedProceeds("Amount: 2x\nPrice per unit: 475 coins",2));
        assertNull(BazaarInstantSell.quotedProceeds("Click to sell!",1));
    }

    @Test void bazaarSaleClicksThroughTheSearchResults() {
        var actions=new RecordingActions();
        var sale=new BazaarInstantSell("OUTPUT","Output",1,900,r->{});
        sale.tick(null,actions,1000,0);
        sale.tick(ProductionRunTest.menu("Bazaar \u279c \"Output\"",SlotView.named(12,"Output",List.of()),item(54,"OUTPUT",1)),actions,1000,100);
        assertEquals(List.of("command:bz Output","click:12"),actions.serverEffects());
        sale.tick(ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 950 coins")),item(54,"OUTPUT",1)),actions,1000,200);
        assertTrue(actions.serverEffects().contains("click:11"));
    }

    @Test void auctionPriceUndercutsTheLowestBinAndRefusesOutliersAndStaleQuotes() {
        long now=1_000_000_000L;
        var body=com.google.gson.JsonParser.parseString("{\"protocol\":\"goofy-ah-price/1\",\"item\":\"GOLDEN_TOOTH\",\"lowest\":5000,\"secondLowest\":5200,\"fetchedAt\":"+now+"}").getAsJsonObject();
        var quote=AuctionPricing.parse(body,"GOLDEN_TOOTH",now+1000);
        assertEquals(4999,AuctionPricing.listingPrice(quote));
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.parse(body,"OTHER",now));
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.parse(body,"GOLDEN_TOOTH",now+AuctionPricing.MAX_AGE_MS+1));
        var outlier=new AuctionPricing.Quote("GOLDEN_TOOTH",1000,5000L,now);
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.listingPrice(outlier));
        assertEquals(999,AuctionPricing.listingPrice(new AuctionPricing.Quote("GOLDEN_TOOTH",1000,null,now)));
    }
    @Test void automaticCraftPinsTheRankedRecipeAndPriceGuardStopsFurtherPurchases()throws Exception {
        var env=new Env();env.buying=true;env.procurementBlock="Market moved below profit target";
        var catalog=catalog();var recipe=catalog.forOutput("OUTPUT").stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var run=ProductionRun.startCraft(env,catalog,recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(env.actions.serverEffects().isEmpty());
        env.procurementBlock=null;env.menu=ProductionRunTest.menu(null,item(54,"INPUT",2));
        run.tick(false,1100);run.tick(false,1200);
        assertEquals(recipe.key(),env.pinnedKey);
    }

    @Test void marketBlockedCraftYieldsMenuAndBackgroundPollWakesItWithoutBuying()throws Exception {
        var env=new Env();env.buying=true;env.procurementBlock="Fresh input/output depth unavailable";
        var recipe=catalog().forOutput("OUTPUT").stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        assertTrue(run.wantsMenu());
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertFalse(run.wantsMenu());
        var lease=new CraftMenuLease<String>();
        assertEquals("other",lease.select(List.of("blocked","other"),id->id.equals("other")||run.wantsMenu(),false));
        assertEquals(Step.BLOCKED,run.tick(false,1100));assertFalse(run.wantsMenu());
        assertTrue(env.actions.serverEffects().isEmpty());
        env.procurementBlock=null;run.tick(false,1200);
        assertTrue(run.wantsMenu());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void ingredientWaitRetainsChildNavigationReason()throws Exception {
        var env=new Env();env.buying=true;
        var recipe=catalog().forOutput("OUTPUT").stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,0,0,null,"General: verify order");
        run.tick(false,1100);assertTrue(run.reason().contains("verify order"));
    }

    @Test void diagnosticObservationContainsOwnershipAndGatesWithoutAdvancingTheRun()throws Exception {
        var env=new Env();env.buying=true;env.procurementBlock="Fresh prices unavailable";
        var recipe=catalog().forOutput("OUTPUT").stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        run.tick(true,1000);var before=env.jobs.all();
        var state=run.diagnosticState();
        assertEquals(run.jobId(),state.get("job"));assertEquals("Fresh prices unavailable",state.get("procurementBlock"));
        assertEquals(true,state.get("waitingForProcurement"));assertEquals(false,state.get("wantsMenu"));
        assertEquals(Map.of("INPUT",2),state.get("missingPurchases"));
        assertEquals(before,env.jobs.all());assertTrue(env.actions.serverEffects().isEmpty());assertTrue(env.orders.isEmpty());
    }
    @Test void unrelatedPurseIncreaseCannotConfirmBoundedCraftSaleProfit() {
        var actions=new RecordingActions();
        var sale=new BazaarInstantSell("OUTPUT","Output",1,900,1100,reason->{});
        var page=ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 1000 coins")),item(54,"OUTPUT",1));
        sale.tick(page,actions,1000,0);
        assertEquals(BazaarInstantSell.Result.UNCERTAIN,sale.tick(ProductionRunTest.menu(null),actions,6000,100));
        assertEquals(0,sale.proceeds());
    }

    @Test void wholePurchaseBasketMustBeQuotedAndFundedBeforeTheFirstInputIsBought()throws Exception {
        var env=new Env();env.buying=true;env.purse=150;
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("A_INPUT",1));grid.set(1,new ProductionRecipe.Ingredient("Z_INPUT",1));
        var recipe=new ProductionRecipe("basket",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("A_INPUT",1,"Z_INPUT",1),grid,0,0,"",null);
        var run=ProductionRun.startCraft(env,new RecipeCatalog(List.of(recipe),Map.of()),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        assertEquals(Step.BLOCKED,run.tick(true,1000));assertTrue(run.reason().contains("All remaining"));assertTrue(env.actions.serverEffects().isEmpty());
        env.purse=1000;env.unquoted=Set.of("Z_INPUT");
        assertEquals(Step.BLOCKED,run.tick(true,1100));assertTrue(run.reason().contains("Z_INPUT"));assertTrue(env.actions.serverEffects().isEmpty());
        env.unquoted=Set.of();assertEquals(206,run.remainingPurchaseCost(),0.001);
        assertEquals(Step.PENDING,run.tick(true,1200));assertTrue(run.reason().contains("Buying"));
    }

    @Test void marketRecheckStopsNavigationBeforeSubmissionButUnstartedCancellationRequiresNoIntent()throws Exception {
        var env=new Env();env.buying=true;var recipe=catalog().forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        assertEquals(Step.PENDING,run.tick(true,1000));
        assertFalse(run.cancelUnstarted("changed")); // A buyer is already queued.
        env.procurementBlock="Market moved below profit target";
        assertEquals(Step.BLOCKED,run.tick(true,1100));assertTrue(env.actions.serverEffects().stream().noneMatch(x->x.contains("sign")||x.contains("click")));
        assertTrue(run.cancelUnstarted("Replanning an untouched run"));
        assertEquals(ProductionJobs.State.CANCELLED,env.jobs.find(run.jobId()).orElseThrow().state());
    }
    @Test void recordedProductionIntentCannotBeCancelledForAutomaticReselection()throws Exception {
        var env=new Env();var recipe=catalog().forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        env.jobs.put(env.jobs.find(run.jobId()).orElseThrow().withState(ProductionJobs.State.BUYING,"Purchase intent"));
        assertFalse(run.cancelUnstarted("market moved"));
        assertEquals(ProductionJobs.State.BUYING,env.jobs.find(run.jobId()).orElseThrow().state());
    }

    @Test void automaticBazaarRunBuysCraftsAndSellsWithActualReceiptBasisExactlyOnce()throws Exception {
        var env=new Env();env.buying=true;var recipe=catalog().forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        run.tick(true,1000);run.tick(true,1100);
        env.menu=menu("Input",item(13,"INPUT",1),SlotView.named(10,"Buy Instantly",List.of("Input","Price per unit: 100 coins")));
        run.tick(true,1200);
        env.menu=menu("How many do you want?",SlotView.named(16,"Custom Amount",List.of()));run.tick(true,1300);
        env.sign=true;run.tick(true,1400);env.sign=false;
        env.procurementBlock="Prices changed after submission"; // Verification cannot be skipped.
        env.menu=menu(null,item(54,"INPUT",2));env.purse=9800;run.tick(true,1500);
        assertEquals(200,run.spent(),0.001);assertFalse(run.cancelUnstarted("reselect"));
        env.procurementBlock=null;run.tick(false,1600);run.tick(false,1700);
        assertEquals(recipe.key(),env.pinnedKey);assertEquals(List.of("OUTPUTx1"),env.crafts);
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=menu(null,item(54,"OUTPUT",1));run.tick(false,1800);
        assertEquals(Stage.SELL,run.stage());assertEquals(List.of(200.0),env.confirmedCosts);
        run.tick(true,1900);
        env.menu=menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",List.of("Output","Price per unit: 990 coins")),item(54,"OUTPUT",1));run.tick(true,2000);
        env.menu=menu(null);env.purse+=978;assertEquals(Step.DONE,run.tick(true,2100));run.tick(true,2200);
        assertEquals(List.of(978.0),env.confirmedSales);
        assertEquals(ProductionJobs.State.DONE,env.jobs.find(run.jobId()).orElseThrow().state());
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.startsWith("sign:")).count());
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.equals("click:11")).count());
    }

    @Test void changedMarketCannotCloseOrSubmitAnOpenAmountEditor()throws Exception {
        var env=new Env();env.buying=true;var recipe=catalog().forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0);
        run.tick(true,1000);run.tick(true,1100);
        env.menu=menu("Input",item(13,"INPUT",1),SlotView.named(10,"Buy Instantly",List.of("Input","Price per unit: 100 coins")));run.tick(true,1200);
        env.menu=menu("How many do you want?",SlotView.named(16,"Custom Amount",List.of()));run.tick(true,1300);
        env.sign=true;env.procurementBlock="Market moved";int effects=env.actions.serverEffects().size();
        assertEquals(Step.BLOCKED,run.tick(true,1400));assertEquals(effects,env.actions.serverEffects().size());
        assertFalse(run.cancelUnstarted("reselect"));
        env.procurementBlock=null;run.tick(true,1500);
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.startsWith("sign:")).count());
    }

    @Test void bazaarSaleWaitsForAnObservedBalanceBeforeSubmittingOrCountingProceeds() {
        var actions=new RecordingActions();var intents=new ArrayList<String>();
        var sale=new BazaarInstantSell("OUTPUT","Output",1,900,1000,intents::add);
        var page=ProductionRunTest.menu("Output",item(13,"OUTPUT",1),SlotView.named(11,"Sell Instantly",
            List.of("Output","Price per unit: 950 coins")),item(54,"OUTPUT",1));
        assertEquals(BazaarInstantSell.Result.WAITING,sale.tick(page,actions,-1,1000));
        assertTrue(actions.serverEffects().isEmpty());assertTrue(intents.isEmpty());
        assertEquals(BazaarInstantSell.Result.WAITING,sale.tick(page,actions,1000,1100));
        assertEquals(List.of("click:11"),actions.serverEffects());
        assertEquals(BazaarInstantSell.Result.WAITING,sale.tick(ProductionRunTest.menu(null),actions,-1,1200));
        assertEquals(BazaarInstantSell.Result.SOLD,sale.tick(ProductionRunTest.menu(null),actions,1950,1300));
        assertEquals(950,sale.proceeds());assertEquals(1,intents.size());
        assertEquals(List.of("click:11"),actions.serverEffects());
    }

    @Test void buyOrdersHandOffVerifiedInputsToCraftThenOfferSettlementRecordsProfitOnlyAfterClaim()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();var recipe=catalog.forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog,recipe,1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        run.tick(true,1000);assertEquals(List.of("BUY:INPUT:2"),env.orders);assertTrue(env.crafts.isEmpty());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,2,100,null,"waiting");
        run.tick(false,1100);assertTrue(env.crafts.isEmpty());assertEquals(0,run.spent());
        env.menu=menu(null,item(54,"INPUT",2));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,2,100,null,"claimed");
        run.tick(true,1200);assertEquals(100,run.spent());assertEquals(100,env.jobs.find(run.jobId()).orElseThrow().costBasis());assertEquals(1,env.orderAcknowledgements);
        run.tick(true,1300);run.tick(false,1400);assertEquals(List.of("OUTPUTx1"),env.crafts);
        assertEquals(Set.of("INPUT","OUTPUT"),run.lockedProducts());
        env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);env.craftQueued=false;env.menu=menu(null,item(54,"OUTPUT",1));
        run.tick(false,1500);assertEquals(Stage.SELL,run.stage());assertEquals(List.of(100.0),env.confirmedCosts);
        assertEquals(Set.of("OUTPUT"),run.lockedProducts(),"Verified crafting releases only consumed inputs");
        run.tick(true,1600);assertEquals(List.of("BUY:INPUT:2","SELL:OUTPUT:1"),env.orders);assertTrue(env.confirmedSales.isEmpty());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,1,0,null,"waiting");
        run.tick(false,1700);assertTrue(env.confirmedSales.isEmpty());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,1,0,1975.0,"claimed");
        assertEquals(Step.DONE,run.tick(true,1800));assertEquals(List.of(1975.0),env.confirmedSales);assertEquals(2,env.orderAcknowledgements);
        assertEquals(Step.DONE,run.tick(true,1900));assertEquals(1,env.confirmedSales.size());
    }

    @Test void cancelledIngredientOrderRechecksProfitAndBuysOnlyMissingUnits()throws Exception {
        for(int filled:List.of(0,1)) {
            var env=new Env();env.buying=true;var catalog=catalog();
            var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
            run.tick(true,1000);assertEquals(List.of("BUY:INPUT:2"),env.orders);
            env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,filled,filled*50,null,"verified cancellation");
            if(filled>0)env.menu=menu(null,item(54,"INPUT",filled));
            run.tick(true,1100);assertEquals(filled*50,run.spent());assertEquals(1,env.orderAcknowledgements);
            env.procurementBlock="Replacement would miss minimum profit";
            assertEquals(Step.BLOCKED,run.tick(true,1200));assertEquals(1,env.orders.size());assertTrue(env.crafts.isEmpty());
            env.procurementBlock=null;run.tick(true,1300);
            assertEquals(List.of("BUY:INPUT:2","BUY:INPUT:"+(2-filled)),env.orders);
            assertEquals(filled*50,env.jobs.find(run.jobId()).orElseThrow().costBasis());
        }
    }

    @Test void aCheapIngredientOrderMayBidTheGuisTenthOfACoinAboveTheTopOrder()throws Exception {
        var env=new Env();env.buying=true;env.orderUnit=1.0;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);assertEquals(List.of("BUY:INPUT:2"),env.orders);
        assertEquals(1.0,env.orderPrices.getFirst()[0],1e-9);
        assertTrue(env.orderPrices.getFirst()[1]>=1.1,"Ceiling "+env.orderPrices.getFirst()[1]+" must allow the GUI's 1.1 bid");
        assertTrue(env.orderPrices.getFirst()[1]<1.11);
    }

    @Test void anInputWithoutAFillableBidIsInstantBoughtInsteadOfOrdered()throws Exception {
        var env=new Env();env.buying=true;env.unorderable=Set.of("INPUT");var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        assertEquals(Step.PENDING,run.tick(true,1000));
        assertTrue(env.orders.isEmpty(),"No buy order for an input whose bid never fills: "+env.orders);
        assertTrue(run.reason().contains("Buying"),run.reason());
    }

    @Test void verifiedEmptyCancellationCanRetireAStaleBudgetWithoutReview()throws Exception {
        var env=new Env();env.buying=true;var recipe=catalog().forOutput("OUTPUT").getFirst();
        var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);assertFalse(run.cancelUnstarted("order still pending"));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,0,0,null,"Refund verified");
        run.tick(true,1100);assertEquals(1,env.orderAcknowledgements);
        env.procurementBlock="Remaining ingredients exceed selected budget";
        assertEquals(Step.BLOCKED,run.tick(false,1200));
        assertTrue(run.cancelUnstarted("Recompute price, batch size and ranking"));
        assertEquals(ProductionJobs.State.CANCELLED,env.jobs.find(run.jobId()).orElseThrow().state());
        assertEquals(0,run.spent());assertTrue(env.actions.serverEffects().isEmpty());
    }
    @Test void partialFillOrUnexpectedHeldInputsCannotUseEmptyCancellationProof()throws Exception {
        for(boolean filled:List.of(false,true)) {
            var env=new Env();env.buying=true;var recipe=catalog().forOutput("OUTPUT").getFirst();
            var run=ProductionRun.startCraft(env,catalog(),recipe,1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
            run.tick(true,1000);
            env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,filled?1:0,filled?50:0,null,"Verified cancellation");
            env.menu=menu(null,item(54,"INPUT",1));run.tick(true,1100);
            assertFalse(run.cancelUnstarted("no unsafe reselection"));
        }
    }

    @Test void uncertainInputOrderNeverStartsCraftingOrPretendsToAcquireInputs()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,true,0,0,null,"Unconfirmed claim");
        assertEquals(Step.UNCERTAIN,run.tick(true,1100));assertTrue(env.crafts.isEmpty());assertEquals(0,run.spent());assertEquals(0,env.orderAcknowledgements);
        assertEquals(ProductionJobs.State.REVIEW,env.jobs.find(run.jobId()).orElseThrow().state());
    }

    @Test void oneCraftCanFinishWhileAnotherWaitsWithoutConsumingItsOrderOrCost()throws Exception {
        var first=new Env();first.buying=true;
        var second=new Env();second.jobs=first.jobs;second.buying=true;
        var aRecipe=CraftFlipPlannerTest.recipe("OUTPUT_A","INPUT_A",2,"");
        var bRecipe=CraftFlipPlannerTest.recipe("OUTPUT_B","INPUT_B",2,"");
        var catalog=new RecipeCatalog(List.of(aRecipe,bRecipe),Map.of());
        var a=ProductionRun.startCraft(first,catalog,aRecipe,1,0,0,BazaarStrategy.ORDER_INSTANT);
        var b=ProductionRun.startCraft(second,catalog,bRecipe,1,0,0,BazaarStrategy.ORDER_INSTANT);
        a.tick(true,1000);b.tick(true,1100);
        first.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(false,false,2,100,null,"waiting");
        second.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,2,100,null,"claimed");
        second.menu=menu(null,item(54,"INPUT_B",2));
        a.tick(false,1200);b.tick(false,1200);b.tick(false,1300);b.tick(false,1400);
        assertEquals(List.of("OUTPUT_Bx1"),second.crafts);assertTrue(first.crafts.isEmpty());
        second.craftQueued=false;second.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        second.menu=menu(null,item(54,"OUTPUT_B",1));
        assertEquals(Step.DONE,b.tick(false,1500));
        assertEquals(Stage.PROCURE,a.stage());assertEquals(0,first.orderAcknowledgements);
        assertEquals(100,b.spent());assertEquals(0,a.spent());
        assertEquals(ProductionJobs.State.DONE,first.jobs.find(b.jobId()).orElseThrow().state());
        assertEquals(ProductionJobs.State.BUYING,first.jobs.find(a.jobId()).orElseThrow().state());
    }

    @Test void unsubmittedPriceRejectionRetriesWithoutReviewOrAcquisition()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,0,0,null,"Price moved",true);
        assertEquals(Step.BLOCKED,run.tick(true,1100));assertEquals(0,run.spent());assertEquals(1,env.orderAcknowledgements);assertFalse(run.wantsMenu(),"Retry waits yield the GUI to other crafts");
        assertNotEquals(ProductionJobs.State.REVIEW,env.jobs.find(run.jobId()).orElseThrow().state());
        run.tick(true,30000);assertEquals(1,env.orders.size());assertTrue(env.crafts.isEmpty());
        env.procurementBlock="Fresh basket no longer profitable";assertEquals(Step.BLOCKED,run.tick(true,31100));assertEquals(1,env.orders.size());
        env.procurementBlock=null;run.tick(true,31200);assertEquals(2,env.orders.size());assertEquals(0,run.spent());
    }

    @Test void emptyPreflightRejectionImmediatelyAllowsAutomaticReselection()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,0,0,null,"GUI 483.9 exceeds ceiling 467.105",true);
        assertEquals(Step.BLOCKED,run.tick(false,1100));
        assertTrue(run.cancelUnstarted(run.reason()));
        assertEquals(ProductionJobs.State.CANCELLED,env.jobs.find(run.jobId()).orElseThrow().state());
        assertEquals(1,env.orderAcknowledgements);assertEquals(0,run.spent());assertTrue(env.actions.serverEffects().isEmpty());
    }

    @Test void rejectionAfterPartialAcquisitionPreservesInputsAndRetryDelay()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,1,50,null,"Partial claim verified");
        env.menu=menu(null,item(54,"INPUT",1));run.tick(true,1100);run.tick(true,1200);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,0,0,null,"Remaining input price moved",true);
        assertEquals(Step.BLOCKED,run.tick(false,1300));assertFalse(run.cancelUnstarted(run.reason()));
        assertEquals(50,run.spent());assertEquals(ProductionJobs.State.BUYING,env.jobs.find(run.jobId()).orElseThrow().state());
        assertEquals(Step.PENDING,run.tick(false,30000));assertEquals(2,env.orders.size());assertFalse(run.wantsMenu());
    }
    @Test void partialFillBudgetCanExpandOnlyBetweenVerifiedTransactions()throws Exception {
        var env=new Env();env.buying=true;var catalog=catalog();
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),1,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_INSTANT);
        assertFalse(run.canExpandProcurementBudget());run.tick(true,1000);assertFalse(run.canExpandProcurementBudget());
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,1,50,null,"Partial claim verified");
        env.menu=menu(null,item(54,"INPUT",1));run.tick(true,1100);
        assertTrue(run.canExpandProcurementBudget());assertEquals(50,run.spent());
        run.tick(true,1200);assertFalse(run.canExpandProcurementBudget(),"A new queued order must retain its original ceiling");
    }

    // ---- Bazaar trade tests ---------------------------------------------------------------
    static final String COMPACTOR="DWARVEN_COMPACTOR";
    @Test void dwarvenCompactorTradeTestInstantBuysOneThenInstantSellsItWithoutCrafting()throws Exception {
        var catalog=RecipeCatalog.instance();assertTrue(catalog.unstackable(COMPACTOR),"Dwarven Super Compactor takes a slot per unit");
        var env=new Env();env.buying=true;
        var run=ProductionRun.startTrade(env,catalog,COMPACTOR,1,ProductionRun.TradePart.BOTH,BazaarStrategy.INSTANT_INSTANT);
        assertTrue(run.isTradeTest());assertEquals(Set.of(COMPACTOR),run.lockedProducts());
        assertEquals("run:trade:"+COMPACTOR,env.jobs.find(run.jobId()).orElseThrow().recipeKey());
        run.tick(true,1000);run.tick(true,1100);
        assertTrue(env.actions.serverEffects().contains("command:bz "+COMPACTOR),env.actions.serverEffects().toString());
        env.menu=menu(COMPACTOR,item(13,COMPACTOR,1),SlotView.named(10,"Buy Instantly",List.of(COMPACTOR,"Price per unit: 100 coins")));
        run.tick(true,1200);assertTrue(env.actions.serverEffects().contains("click:10"));
        env.menu=menu("How many do you want?",SlotView.named(16,"Custom Amount",List.of()),
            SlotView.named(10,"Buy only one!",List.of(COMPACTOR,"","Amount: 1x","","Price: 100 coins")));
        run.tick(true,1300);
        assertEquals(ProductionJobs.State.BUYING,env.jobs.find(run.jobId()).orElseThrow().state(),"intent saved before the preset click");
        env.menu=menu(null,item(54,COMPACTOR,1));env.purse=9900;run.tick(true,1400);
        assertEquals(100,run.spent(),0.001);
        run.tick(true,1500);assertEquals(Stage.PROCESS,run.stage());
        run.tick(false,1600);assertEquals(Stage.SELL,run.stage());
        assertTrue(env.crafts.isEmpty(),"A trade test never crafts");assertTrue(env.orders.isEmpty());
        assertTrue(env.actions.performed().stream().anyMatch(m->m.startsWith("message:Trade test: bought 1 ")),env.actions.performed().toString());
        assertTrue(run.wantsMenu());run.tick(true,1700);
        env.menu=menu(COMPACTOR,item(13,COMPACTOR,1),SlotView.named(11,"Sell Instantly",List.of(COMPACTOR,"Price per unit: 990 coins")),item(54,COMPACTOR,1));
        run.tick(true,1800);assertTrue(env.actions.serverEffects().contains("click:11"));
        env.menu=menu(null);env.purse+=978;
        assertEquals(Step.DONE,run.tick(true,1900));run.tick(true,2000);
        assertEquals(ProductionJobs.State.DONE,env.jobs.find(run.jobId()).orElseThrow().state());
        assertEquals(1,env.actions.serverEffects().stream().filter(e->e.equals("click:11")).count());
        assertTrue(env.confirmedSales.isEmpty() && env.confirmedCosts.isEmpty(),"Test trades stay out of the craft profit ledger");
        assertTrue(env.actions.performed().stream().anyMatch(m->m.startsWith("message:Trade test: sold 1 ")&&m.contains("bought for 100.0, result 878.0")),env.actions.performed().toString());
    }
    @Test void tradeTestBuyOnlyPlacesTheBuyOrderItWasToldToAndKeepsTheUnits()throws Exception {
        var env=new Env();env.buying=true;env.unorderable=Set.of("INPUT");
        var run=ProductionRun.startTrade(env,catalog(),"INPUT",5,ProductionRun.TradePart.BUY,BazaarStrategy.ORDER_INSTANT);
        run.tick(true,1000);assertEquals(List.of("BUY:INPUT:5"),env.orders,"The player's buy-order choice is honoured even with a weak bid");
        env.menu=menu(null,item(54,"INPUT",5));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,5,250,null,"claimed");
        run.tick(true,1100);assertEquals(250,run.spent());
        run.tick(true,1200);run.tick(false,1300);
        assertEquals(Step.DONE,run.tick(false,1400));
        assertEquals(List.of("BUY:INPUT:5"),env.orders,"Buy-only never sells");assertTrue(env.crafts.isEmpty());
        assertEquals(ProductionJobs.State.DONE,env.jobs.find(run.jobId()).orElseThrow().state());
    }
    @Test void tradeTestSellOnlyOffersEveryHeldUnitAndReportsTheProceeds()throws Exception {
        var env=new Env();env.menu=menu(null,item(54,"OUTPUT",3));
        var run=ProductionRun.startTrade(env,catalog(),"OUTPUT",0,ProductionRun.TradePart.SELL,BazaarStrategy.INSTANT_OFFER);
        assertEquals(3,run.outputUnits());assertEquals(Stage.PROCESS,run.stage());
        run.tick(false,1000);assertEquals(Stage.SELL,run.stage());
        run.tick(true,1100);assertEquals(List.of("SELL:OUTPUT:3"),env.orders);
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,3,0,5900.0,"claimed");
        assertEquals(Step.DONE,run.tick(true,1200));
        assertTrue(env.confirmedSales.isEmpty());
        assertTrue(env.actions.performed().stream().anyMatch(m->m.startsWith("message:Trade test: sold 3 ")&&m.contains("sell offer")),env.actions.performed().toString());
    }
    @Test void tradeTestRefusesHeldUnitsForABuyAndSellNothingToSellOrMoreThanFits()throws Exception {
        var held=new Env();held.menu=menu(null,item(54,"OUTPUT",1));
        assertThrows(IllegalArgumentException.class,()->ProductionRun.startTrade(held,catalog(),"OUTPUT",1,ProductionRun.TradePart.BOTH,BazaarStrategy.INSTANT_INSTANT));
        var empty=new Env();
        assertThrows(IllegalArgumentException.class,()->ProductionRun.startTrade(empty,catalog(),"OUTPUT",0,ProductionRun.TradePart.SELL,BazaarStrategy.INSTANT_INSTANT));
        var tooMany=assertThrows(IllegalArgumentException.class,()->ProductionRun.startTrade(empty,RecipeCatalog.instance(),COMPACTOR,37,ProductionRun.TradePart.BUY,BazaarStrategy.INSTANT_INSTANT));
        assertTrue(tooMany.getMessage().contains("37 free inventory slots"),tooMany.getMessage());
        var occupied=new Env();occupied.occupied=Set.of("OUTPUT");
        assertThrows(IllegalArgumentException.class,()->ProductionRun.startTrade(occupied,catalog(),"OUTPUT",1,ProductionRun.TradePart.BUY,BazaarStrategy.INSTANT_INSTANT));
        assertTrue(empty.jobs.all().isEmpty(),"A refused test journals nothing");
    }
    @Test void stoppedTradeTestSellOfferResumesWithoutBuyingAgain()throws Exception {
        var env=new Env();env.menu=menu(null,item(54,"OUTPUT",2));
        var original=ProductionRun.startTrade(env,catalog(),"OUTPUT",0,ProductionRun.TradePart.SELL,BazaarStrategy.INSTANT_OFFER);
        var job=env.jobs.find(original.jobId()).orElseThrow().withState(ProductionJobs.State.REVIEW,"Production stopped; inspect inventory");env.jobs.put(job);
        var resumed=ProductionRun.resumeSale(env,catalog(),job,"OUTPUT",2);
        assertEquals(Stage.SELL,resumed.stage());assertTrue(resumed.isTradeTest());
        assertThrows(IllegalArgumentException.class,()->ProductionRun.resumeSale(env,catalog(),job,"OUTPUT",3));
        env.orderResult=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result(true,false,2,0,3900.0,"Verified sale proceeds");
        assertEquals(Step.DONE,resumed.tick(true,1000));assertTrue(env.orders.isEmpty());assertTrue(env.confirmedSales.isEmpty());
    }
}
