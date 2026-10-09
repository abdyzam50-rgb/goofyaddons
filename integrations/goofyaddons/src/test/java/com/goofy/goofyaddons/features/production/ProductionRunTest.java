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
    @TempDir Path dir;

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
        Map<String,Integer> unlocks=Map.of();boolean requirementsPending;
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
        public Double instantBuyCost(String id,int units){return unquoted.contains(id)?null:units*100.0;}
        public Double instantSellValue(String id,int units){return units*1000.0;}
        public String name(String id){return catalog().name(id);}
        public String queueCraft(String output,int batches){
            try{String id="craft-"+crafts.size();jobs.put(new ProductionJobs.Job(id,"craft:OUTPUT:0","account",ProductionJobs.State.PLANNED,batches,-1,0,0,null,null,null,null));
                crafts.add(output+"x"+batches);craftQueued=true;return id;}catch(Exception e){return null;}
        }
        public boolean craftQueued(){return craftQueued;}
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
    @Test void eyeIngredientsAreCraftedFromRodsInJournaledChunksBeforeFinalCraft()throws Exception {
        var env=new Env();env.unlocks=Map.of("enderpearl",6);
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",32),item(55,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,0,0);
        assertFalse(run.wantsMenu());run.tick(false,0);
        assertEquals(List.of("BLAZE_POWDERx16"),env.crafts);assertEquals(Stage.PROCURE,run.stage());
        assertEquals(ProductionJobs.State.PROCESSING,env.jobs.find(run.jobId()).orElseThrow().state());
        assertTrue(run.lockedProducts().contains("BLAZE_ROD"));
        env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.OUTPUT_READY);
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",16),item(55,"ENCHANTED_ENDER_PEARL",16),item(56,"BLAZE_POWDER",32));
        run.tick(false,1);run.tick(false,2);assertEquals(List.of("BLAZE_POWDERx16","BLAZE_POWDERx16"),env.crafts);
        env.craftQueued=false;env.finish("craft-1",ProductionJobs.State.OUTPUT_READY);
        env.menu=ProductionRunTest.menu(null,item(55,"ENCHANTED_ENDER_PEARL",16),item(56,"BLAZE_POWDER",64));
        run.tick(false,3);run.tick(false,4);assertEquals(Stage.PROCESS,run.stage());
        run.tick(false,5);assertEquals("ENCHANTED_EYE_OF_ENDERx1",env.crafts.getLast());assertTrue(env.actions.serverEffects().isEmpty());
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
    @Test void failedIntermediateCraftRequiresReviewAndCannotStartTheFinalCraft()throws Exception {
        var env=new Env();env.unlocks=Map.of("enderpearl",6);
        env.menu=ProductionRunTest.menu(null,item(54,"BLAZE_ROD",32),item(55,"ENCHANTED_ENDER_PEARL",16));
        var run=ProductionRun.start(env,RecipeCatalog.instance(),"ENCHANTED_EYE_OF_ENDER",ProductionRecipe.Kind.CRAFT,1,-1,5000,100);
        run.tick(false,0);env.craftQueued=false;env.finish("craft-0",ProductionJobs.State.REVIEW);
        assertEquals(Step.UNCERTAIN,run.tick(false,1));assertEquals(Stage.REVIEW,run.stage());
        assertEquals(List.of("BLAZE_POWDERx16"),env.crafts);assertTrue(env.listings.isEmpty());
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
}
