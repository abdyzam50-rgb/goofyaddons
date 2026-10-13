package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompactorProductionTest {
    @TempDir Path dir;
    class Env implements ProductionRun.Environment,GameActions {
        final ProductionJobs jobs;int raw=320,outputs;boolean open,active,queued,ignored,convertOnInstall,cursorWhenQueued,laggedOutputs,refreshed;int opens,seedCrafts;Map<Integer,String> filters=Map.of();
        final List<String> effects=new ArrayList<>();
        Env()throws Exception{jobs=new ProductionJobs(dir.resolve("jobs.json"));}
        public ProductionJobs jobs(){return jobs;}
        public String account(){return "test";}
        public boolean signOpen(){return false;}
        public double purse(){return 100000;}
        public double spendable(){return 100000;}
        public Map<String,Integer> skills(){return Map.of();}
        public Set<String> occupied(){return Set.of();}
        public boolean buyingAllowed(){return true;}
        public Double instantBuyCost(String id,int units){return (double)units;}
        public Double instantSellValue(String id,int units){return (double)units*1000;}
        public String name(String id){return id;}
        public GameActions actions(){return this;}
        public boolean craftQueued(){return queued;}
        public String queueCraft(String id,int batches){
            assertEquals(1,batches);seedCrafts++;queued=true;try{jobs.put(new ProductionJobs.Job("seed","crafting:ENCHANTED_GOLD:0","test",ProductionJobs.State.PLANNED,1,-1,0,0,null,null,null,null));}catch(Exception e){throw new RuntimeException(e);}return "seed";
        }
        public String queueListing(String output,long price,double fee){return null;}
        public boolean listingQueued(){return false;}
        public MenuSnapshot menu(){
            var m=CompactorClearanceTest.menu(open?"Personal Compactor 7000":refreshed?"Craft Item":null,active,filters,filters);
            var slots=new ArrayList<>(m.slots());
            if(raw>0)slots.set(36,new SlotView(36,true,9,false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,raw,64));
            if(outputs>0)slots.set(37,new SlotView(37,true,10,false,"Enchanted Gold","Enchanted Gold",List.of(),"ENCHANTED_GOLD",null,laggedOutputs?Math.max(1,outputs-1):outputs,64));
            if(cursorWhenQueued&&queued)return new MenuSnapshot(0,"Craft Item",false,slots,
                new SlotView(-1,false,-1,false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,1,64));
            return new MenuSnapshot(open?1:refreshed?2:0,m.title(),true,slots);
        }
        public boolean openCompactor(SlotView device){opens++;open=true;refreshed=false;return true;}
        public void click(int slot,boolean shift){effects.add("click:"+slot);if(ignored)return;
            if(slot==37){assertFalse(active,"Disable conversion while installing the seed filter");filters=Map.of(0,"ENCHANTED_GOLD");if(convertOnInstall&&active){outputs+=raw/160;raw%=160;}}else if(slot==31){active=!active;if(active&&convertOnInstall){outputs+=raw/160;raw%=160;}}else if(slot==10)filters=Map.of();
        }
        public void closeMenu(){open=false;refreshed=false;}
        public void command(String command){effects.add(command);if(command.equals("craft"))refreshed=true;}
        public void message(String message){}
        public boolean writeSign(String text){return false;}
        void finishSeed()throws Exception{raw-=160;outputs++;queued=false;jobs.put(jobs.find("seed").orElseThrow().completedBatch().withState(ProductionJobs.State.OUTPUT_READY,null));}
    }
    @Test void seedInstallsOnceEnablesConvertsAndClearsFilterBeforeFinishing()throws Exception {
        var env=new Env();var recipe=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        assertTrue(CompactorProduction.supported(recipe,RecipeCatalog.instance()));
        var journal=new ArrayList<String>();
        var bulk=new CompactorProduction(env,recipe,2,CompactorProduction.available(env.menu()),(reason,cost)->journal.add(reason));
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<20000&&result.step()!=ProductionLoop.Step.DONE;now+=500) {
            result=bulk.tick(true,now);assertTrue(result.step()==ProductionLoop.Step.PENDING||result.step()==ProductionLoop.Step.DONE,result.reason());
            if(env.queued)env.finishSeed();
            if(!env.open&&env.active&&env.filters.equals(Map.of(0,"ENCHANTED_GOLD"))&&env.raw>=160){env.raw-=160;env.outputs++;}
        }
        assertEquals(ProductionLoop.Step.DONE,result.step(),result.reason());assertEquals(2,env.outputs);assertEquals(0,env.raw);
        assertTrue(env.filters.isEmpty());assertEquals(1,env.seedCrafts);assertEquals(2,env.opens);
        assertEquals(List.of("click:37","click:31","craft","click:10"),env.effects);
        assertTrue(journal.stream().anyMatch(r->r.startsWith("Installing")));
    }
    @Test void conversionDuringFilterInstallationPreservesSeedAndRemainingBudget()throws Exception {
        var env=new Env();env.active=true;env.convertOnInstall=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<20000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.DONE,result.step(),result.reason());assertEquals(2,env.outputs);
        assertTrue(bulk.remainingInputs().isEmpty());assertFalse(bulk.active());assertTrue(env.filters.isEmpty());
    }

    @Test void parentWaitsForSeedChildWhileItsCraftingCursorIsOccupied()throws Exception {
        var env=new Env();env.cursorWhenQueued=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,1000).step());assertTrue(env.queued);
        assertFalse(env.menu().cursorEmpty());
        for(long now=1200;now<=2200;now+=200)assertEquals(ProductionLoop.Step.PENDING,bulk.tick(false,now).step());
        assertEquals(0,env.opens);assertTrue(env.effects.isEmpty());
        env.finishSeed();
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,2400).step());
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,2600).step());assertEquals(1,env.opens);
    }

    @Test void filterAcknowledgementClosesMenuBeforeReconcilingLaggedConvertedOutputs()throws Exception {filterReceiptRecovery(true);}
    @Test void permanentlyMissingConvertedOutputsNeverAuthorizeAnotherPurchase()throws Exception {filterReceiptRecovery(false);}
    private void filterReceiptRecovery(boolean resolves)throws Exception {
        var env=new Env();env.active=true;env.convertOnInstall=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        for(long now=1000;now<6000;now+=500){
            var result=bulk.tick(true,now);assertEquals(ProductionLoop.Step.PENDING,result.step(),result.reason());
            if(env.queued)env.finishSeed();
            if(env.filters.size()==1&&env.open)env.laggedOutputs=true;
            if(!env.open&&env.filters.size()==1)break;
        }
        assertFalse(env.open,"Close the acknowledged filter GUI even while output packets lag");assertTrue(env.laggedOutputs);
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,6500).step());
        if(resolves)env.laggedOutputs=false;
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=7000;now<20000&&result.step()==ProductionLoop.Step.PENDING;now+=500)result=bulk.tick(true,now);
        assertEquals(resolves?ProductionLoop.Step.DONE:ProductionLoop.Step.UNCERTAIN,result.step(),result.reason());
        assertEquals(resolves,env.filters.isEmpty());assertEquals(2,env.outputs);
        assertTrue(env.effects.contains("craft"),"Request a fresh server inventory container rather than trust local close state");
        assertTrue(env.effects.stream().noneMatch(e->e.startsWith("bz")),"Never buy while setup counts are short");
        if(!resolves)assertTrue(result.reason().contains("raw-equivalent"));
    }

    @Test void unacknowledgedInstallationIsNotClickedTwiceAndKeepsSeed()throws Exception {
        var env=new Env();env.ignored=true;env.active=false;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<20000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.UNCERTAIN,result.step());assertEquals(List.of("click:37"),env.effects);assertEquals(1,env.outputs);assertEquals(160,env.raw);
    }
    @Test void requiresFundingForAllRemainingInputsBeforeBuyingTheSeedLot()throws Exception {
        var env=new Env();env.raw=0;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,1024,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=bulk.tick(true,1000);
        assertEquals(ProductionLoop.Step.BLOCKED,result.step());assertTrue(result.reason().contains("capital"));
        assertEquals(163840,bulk.remainingInputs().get("GOLD_INGOT"));assertTrue(env.effects.isEmpty());assertEquals(0,env.seedCrafts);assertEquals(0,env.opens);
    }

    @Test void excludesMixedUnstackableAndNonEnchantedRecipes() {
        var catalog=RecipeCatalog.instance();
        for(String id:List.of("DWARVEN_COMPACTOR","ENCHANTED_EYE_OF_ENDER","HOTSPOT_BAIT"))assertFalse(CompactorProduction.supported(catalog.forOutput(id).getFirst(),catalog));
    }
}
