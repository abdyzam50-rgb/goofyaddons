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
        Set<String> occupied=Set.of();
        Env()throws Exception{jobs=new ProductionJobs(dir.resolve("jobs.json"));}
        public ProductionJobs jobs(){return jobs;}
        public String account(){return "account";}
        public MenuSnapshot menu(){return menu;}
        public boolean signOpen(){return sign;}
        public GameActions actions(){return actions;}
        public double purse(){return purse;}
        public double spendable(){return purse;}
        public Map<String,Integer> skills(){return Map.of();}
        public Set<String> occupied(){return occupied;}
        public boolean buyingAllowed(){return buying;}
        public Double instantBuyCost(String id,int units){return units*100.0;}
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
}
