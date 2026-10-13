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
        final ProductionJobs jobs;int raw=320,outputs;boolean open,active,queued,ignored,convertOnInstall,cursorWhenQueued,laggedOutputs,refreshed,controlCursor,sackMenu,autoToSack,dropGfs;int sackBalance;int opens,seedCrafts;Map<Integer,String> filters=Map.of();
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
            if(sackMenu){
                var lore=new ArrayList<>(List.of("Instantly sell everything in your","sacks on the Bazaar."));
                lore.add(sackBalance==0?"Your sacks are empty!":sackBalance+"x Enchanted Gold Ingot for 100 coins");
                slots.set(31,SlotView.named(31,"Sell Sacks Now",lore));
            }
            if(raw>0)slots.set(36,new SlotView(36,true,9,false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,raw,64));
            if(outputs>0)slots.set(37,new SlotView(37,true,10,false,"Enchanted Gold","Enchanted Gold",List.of(),"ENCHANTED_GOLD",null,laggedOutputs?Math.max(1,outputs-1):outputs,64));
            if(controlCursor)return new MenuSnapshot(1,m.title(),false,slots,
                new SlotView(-1,false,-1,false,"Compactor Currently ON!","Compactor Currently ON!",List.of(),null,null,1,64));
            if(cursorWhenQueued&&queued)return new MenuSnapshot(0,"Craft Item",false,slots,
                new SlotView(-1,false,-1,false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,1,64));
            return new MenuSnapshot(sackMenu?3:open?1:refreshed?2:0,sackMenu?"Bazaar ➜ Mining":m.title(),true,slots);
        }
        public boolean openCompactor(SlotView device){opens++;open=true;refreshed=false;return true;}
        public void click(int slot,boolean shift){assertFalse(sackMenu,"Never click Sell Sacks Now");effects.add("click:"+slot);if(ignored)return;
            if(slot==37){assertFalse(active,"Disable conversion while installing the seed filter");filters=Map.of(0,"ENCHANTED_GOLD");if(convertOnInstall&&active){outputs+=raw/160;raw%=160;}}else if(slot==31){active=!active;if(active&&convertOnInstall){if(autoToSack)sackBalance+=raw/160;else outputs+=raw/160;raw%=160;}}else if(slot==10)filters=Map.of();
        }
        public void closeMenu(){open=false;refreshed=false;sackMenu=false;}
        public void command(String command){effects.add(command);if(command.equals("craft"))refreshed=true;
            if(command.equals("bz"))sackMenu=true;
            if(command.startsWith("gfs ")&&!dropGfs){int n=Integer.parseInt(command.split(" ")[2]);assertTrue(n<=sackBalance);sackBalance-=n;outputs+=n;}}
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
        assertEquals(List.of("bz","click:37","click:31","craft","click:10"),env.effects);
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
        long base=1000;
        while(!env.queued&&base<10000){assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,base).step());base+=500;}assertTrue(env.queued);
        assertFalse(env.menu().cursorEmpty());
        for(long now=base;now<=base+1000;now+=200)assertEquals(ProductionLoop.Step.PENDING,bulk.tick(false,now).step());
        assertEquals(0,env.opens);assertEquals(List.of("bz"),env.effects);
        env.finishSeed();
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,base+1500).step());
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,base+2000).step());assertEquals(1,env.opens);
    }

    @Test void filterAcknowledgementClosesMenuBeforeReconcilingLaggedConvertedOutputs()throws Exception {filterReceiptRecovery(true);}
    @Test void permanentlyMissingConvertedOutputsNeverAuthorizeAnotherPurchase()throws Exception {filterReceiptRecovery(false);}
    private void filterReceiptRecovery(boolean resolves)throws Exception {
        var env=new Env();env.active=true;env.convertOnInstall=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        for(long now=1000;now<10000;now+=500){
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
        assertTrue(env.effects.stream().noneMatch(e->e.startsWith("bz ")),"Never buy while setup counts are short");
        if(!resolves)assertTrue(result.reason().contains("reconcile"));
    }

    @Test void ownToggleCursorWaitsForAcknowledgementWithoutRepeatingTheClick()throws Exception {
        var env=new Env();env.active=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        long now=1000;
        while(!env.effects.contains("click:31")&&now<12000){assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,now).step());if(env.queued)env.finishSeed();now+=500;}
        assertEquals(List.of("bz","click:31"),env.effects);env.controlCursor=true;
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,now).step());
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,now+500).step());assertEquals(List.of("bz","click:31"),env.effects);
        env.controlCursor=false;
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,now+1000).step());
        assertEquals(ProductionLoop.Step.PENDING,bulk.tick(true,now+1500).step());assertEquals(List.of("bz","click:31","click:37"),env.effects);
    }
    @Test void persistentOwnControlCursorStopsAfterItsDeadlineWithoutRepeating()throws Exception {
        var env=new Env();env.active=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        for(long now=1000;now<12000&&!env.effects.contains("click:31");now+=500){bulk.tick(true,now);if(env.queued)env.finishSeed();}
        env.controlCursor=true;
        var result=bulk.tick(true,15000);
        assertEquals(ProductionLoop.Step.UNCERTAIN,result.step());assertTrue(result.reason().contains("cursor"));assertEquals(List.of("bz","click:31"),env.effects);
    }

    @Test void unacknowledgedInstallationIsNotClickedTwiceAndKeepsSeed()throws Exception {
        var env=new Env();env.ignored=true;env.active=false;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<20000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.UNCERTAIN,result.step());assertEquals(List.of("bz","click:37"),env.effects);assertEquals(1,env.outputs);assertEquals(160,env.raw);
    }
    @Test void requiresFundingForAllRemainingInputsBeforeBuyingTheSeedLot()throws Exception {
        var env=new Env();env.raw=0;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,1024,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<10000&&result.step()==ProductionLoop.Step.PENDING;now+=500)result=bulk.tick(true,now);
        assertEquals(ProductionLoop.Step.BLOCKED,result.step());assertTrue(result.reason().contains("capital"));
        assertEquals(163840,bulk.remainingInputs().get("GOLD_INGOT"));assertEquals(List.of("bz"),env.effects);assertEquals(0,env.seedCrafts);assertEquals(0,env.opens);
    }

    @Test void sackOutputsAreRetainedUntilFinishedThenOnlyTheOwnedIncreaseIsWithdrawn()throws Exception {
        var env=new Env();env.sackBalance=100;env.autoToSack=true;env.convertOnInstall=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var journal=new ArrayList<String>();var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->journal.add(reason));
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<30000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.DONE,result.step(),result.reason());assertEquals(2,env.outputs);assertEquals(100,env.sackBalance);
        assertEquals(1,env.effects.stream().filter(e->e.equals("gfs ENCHANTED_GOLD 1")).count());
        assertTrue(env.filters.isEmpty());assertTrue(journal.stream().anyMatch(e->e.contains("owned sack outputs: 1")));
    }
    @Test void ignoredSackWithdrawalIsNotReissuedAndDoesNotConsumeOldStock()throws Exception {
        var env=new Env();env.sackBalance=100;env.autoToSack=true;env.convertOnInstall=true;env.dropGfs=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<40000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.UNCERTAIN,result.step());assertTrue(result.reason().contains("not acknowledged"));
        assertEquals(101,env.sackBalance);assertEquals(1,env.outputs);
        assertEquals(1,env.effects.stream().filter(e->e.startsWith("gfs ")).count());
    }

    @Test void purchasedLotsReconcileSackDeltasAndPreserveExistingStockThroughFinalWithdrawal()throws Exception {
        class Lots extends Env {
            int page;boolean sign;double cash=100000;final List<Integer> purchases=new ArrayList<>();
            Lots()throws Exception{raw=0;sackBalance=100;autoToSack=true;convertOnInstall=true;}
            public String name(String id){return RecipeCatalog.instance().name(id);}
            public double purse(){return cash;}
            public double spendable(){return cash;}
            public boolean signOpen(){return sign;}
            public MenuSnapshot menu(){
                var base=super.menu();var slots=new ArrayList<>(base.slots());
                for(int n=0;n<slots.size();n++){var v=slots.get(n);if(v.inPlayerInventory()&&"GOLD_INGOT".equals(v.customId()))slots.set(n,SlotView.empty(v.index(),true,v.containerSlot()));}
                int left=raw;
                for(int n=0;n<slots.size()&&left>0;n++){var v=slots.get(n);if(v.inPlayerInventory()&&v.empty()&&v.containerSlot()<36){int take=Math.min(64,left);slots.set(n,new SlotView(v.index(),true,v.containerSlot(),false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,take,64));left-=take;}}
                assertEquals(0,left,"Input overflow");
                if(page>0){
                    for(int n=0;n<36;n++)slots.set(n,SlotView.empty(n,false,n));
                    if(page==1){slots.set(13,new SlotView(13,false,13,false,"Gold Ingot","Gold Ingot",List.of(),"GOLD_INGOT",null,1,64));slots.set(10,SlotView.named(10,"Buy Instantly",List.of("Gold Ingot","Price per unit: 1 coins")));}
                    else slots.set(16,SlotView.named(16,"Custom Amount",List.of()));
                    return new MenuSnapshot(10+page,page==1?"Gold Ingot":"How many do you want?",true,slots);
                }
                return new MenuSnapshot(base.containerId(),base.title(),base.cursorEmpty(),slots,base.carried());
            }
            public void command(String cmd){if(cmd.equals("bz Gold Ingot")){effects.add(cmd);page=1;}else super.command(cmd);}
            public void click(int slot,boolean shift){if(page==1&&slot==10){page=2;return;}if(page==2&&slot==16){sign=true;return;}super.click(slot,shift);}
            public boolean writeSign(String text){int amount=Integer.parseInt(text);purchases.add(amount);raw+=amount;cash-=amount;page=0;sign=false;if(active&&!filters.isEmpty()){sackBalance+=raw/160;raw%=160;}return true;}
        }
        var env=new Lots();var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var costs=new ArrayList<Double>();var bulk=new CompactorProduction(env,r,16,CompactorProduction.available(env.menu()),(reason,cost)->costs.add(cost));
        var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<100000&&result.step()==ProductionLoop.Step.PENDING;now+=500){result=bulk.tick(true,now);if(env.queued)env.finishSeed();}
        assertEquals(ProductionLoop.Step.DONE,result.step(),result.reason());assertEquals(16,env.outputs);assertEquals(100,env.sackBalance);
        assertEquals(2560,env.purchases.stream().mapToInt(Integer::intValue).sum());assertTrue(env.purchases.size()>1);
        assertEquals(2560,costs.getLast());assertEquals(97440,env.cash);
        assertEquals(1,env.effects.stream().filter(e->e.equals("gfs ENCHANTED_GOLD 15")).count());assertTrue(env.filters.isEmpty());
    }

    @Test void unrelatedSackIncreaseCannotBeClaimedAsThisRunsOutput()throws Exception {
        var env=new Env();env.sackBalance=100;env.autoToSack=true;env.convertOnInstall=true;
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_GOLD").getFirst();
        var bulk=new CompactorProduction(env,r,2,CompactorProduction.available(env.menu()),(reason,cost)->{});
        boolean altered=false;var result=ProductionLoop.Outcome.PENDING;
        for(long now=1000;now<30000&&result.step()==ProductionLoop.Step.PENDING;now+=500){
            result=bulk.tick(true,now);if(env.queued)env.finishSeed();
            if(env.sackBalance==101&&!altered){env.sackBalance++;altered=true;}
        }
        assertEquals(ProductionLoop.Step.UNCERTAIN,result.step());assertTrue(result.reason().contains("reconcile"));
        assertEquals(102,env.sackBalance);assertTrue(env.effects.stream().noneMatch(e->e.startsWith("gfs")));
    }

    @Test void excludesMixedUnstackableAndNonEnchantedRecipes() {
        var catalog=RecipeCatalog.instance();
        for(String id:List.of("DWARVEN_COMPACTOR","ENCHANTED_EYE_OF_ENDER","HOTSPOT_BAIT"))assertFalse(CompactorProduction.supported(catalog.forOutput(id).getFirst(),catalog));
    }
}
