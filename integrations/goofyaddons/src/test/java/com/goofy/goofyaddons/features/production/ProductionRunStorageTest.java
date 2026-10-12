package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders;
import com.goofy.goofyaddons.features.production.ProductionLoop.Stage;
import com.goofy.goofyaddons.features.production.ProductionLoop.Step;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs that buy more than the inventory holds park inputs in storage and craft in rounds. */
class ProductionRunStorageTest {
    @TempDir Path dir;

    static RecipeCatalog catalog() {
        var craft=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",2),
                Arrays.asList(new ProductionRecipe.Ingredient("INPUT",2),null,null,null,null,null,null,null,null),0,0,"",null);
        return new RecipeCatalog(List.of(craft),Map.of("INPUT","Input","OUTPUT","Output"));
    }

    class Env implements ProductionRun.Environment {
        final ProductionStorageTest.World world=new ProductionStorageTest.World().page("ec 1",45);
        final ProductionJobs jobs;final ProductionStorage storage;
        boolean craftQueued;
        final List<Integer> crafts=new ArrayList<>();final List<String> orders=new ArrayList<>();
        final List<String> awaiting=new ArrayList<>();final Map<String,Integer> recordedStorage=new HashMap<>();
        ProductionBazaarOrders.Result orderResult;int acknowledgements;
        Env()throws Exception{jobs=new ProductionJobs(dir.resolve("jobs.json"));storage=new ProductionStorage(dir.resolve("storage.json"));}
        public ProductionJobs jobs(){return jobs;}
        public String account(){return "account";}
        public MenuSnapshot menu(){return world.menu();}
        public boolean signOpen(){return false;}
        public GameActions actions(){return world;}
        public double purse(){return 1e9;}
        public double spendable(){return 1e9;}
        public Map<String,Integer> skills(){return Map.of();}
        public Set<String> occupied(){return Set.of();}
        public boolean buyingAllowed(){return true;}
        public Double instantBuyCost(String id,int units){return units*10.0;}
        public Double instantSellValue(String id,int units){return units*100.0;}
        public Double orderBuyCost(String id,int units){return units*10.0;}
        public Double offerSellValue(String id,int units){return units*100.0;}
        public String name(String id){return catalog().name(id);}
        public boolean queueIngredientOrders(List<ProductionBazaarOrders.IngredientOrder> basket){basket.forEach(o->orders.add(o.product()+":"+o.units()));return true;}
        public ProductionBazaarOrders.Result tickOrder(boolean owns){return orderResult;}
        public boolean acknowledgeOrder(){acknowledgements++;return true;}
        public ProductionStorage storage(){return storage;}
        public boolean storageEnabled(){return true;}
        public List<ProductionStorage.Page> storagePages(){return ProductionStorage.pages("ec 1");}
        public List<String> orderAwaitingStorage(){return List.copyOf(awaiting);}
        public Set<String> orderTracked(){return Set.of("INPUT");}
        public boolean orderStored(String product,int units){recordedStorage.merge(product,units,Integer::sum);awaiting.remove(product);return true;}
        public String queueCraft(String output,int batches){
            try{String id="craft-"+crafts.size();jobs.put(new ProductionJobs.Job(id,"craft:OUTPUT:0","account",ProductionJobs.State.PLANNED,batches,-1,0,0,null,null,null,null));
                crafts.add(batches);craftQueued=true;return id;}catch(Exception e){return null;}
        }
        public boolean craftQueued(){return craftQueued;}
        public String queueListing(String product,long price,double fee){return null;}
        public boolean listingQueued(){return false;}
        /** The crafting feature turns two inputs into one output per batch and journals it. */
        void finishCraft()throws Exception {
            int batches=crafts.getLast();take("INPUT",2*batches);give("OUTPUT",batches);craftQueued=false;
            jobs.put(jobs.find("craft-"+(crafts.size()-1)).orElseThrow().withState(ProductionJobs.State.DONE,null));
        }
        void give(String id,int units){
            for(int i=0;i<36 && units>0;i++)if(id.equals(world.invIds[i])){int add=Math.min(64-world.invCounts[i],units);world.invCounts[i]+=add;units-=add;}
            for(int i=0;i<36 && units>0;i++)if(world.invIds[i]==null){world.invIds[i]=id;world.invCounts[i]=Math.min(64,units);units-=world.invCounts[i];}
            assertEquals(0,units,"Test inventory overflowed");
        }
        void take(String id,int units){
            for(int i=35;i>=0 && units>0;i--)if(id.equals(world.invIds[i])){int cut=Math.min(world.invCounts[i],units);world.invCounts[i]-=cut;units-=cut;if(world.invCounts[i]==0)world.invIds[i]=null;}
            assertEquals(0,units,"Test inventory lacked "+id);
        }
        void junk(int slots){for(int i=0;i<slots;i++){world.invIds[35-i]="COBBLE";world.invCounts[35-i]=64;}}
    }

    static Step tickUntilSettled(ProductionRun run,long[] clock,int limit) {
        Step step=Step.PENDING;
        for(int i=0;i<limit;i++){step=run.tick(true,clock[0]);clock[0]+=500;if(step!=Step.PENDING || run.reason()==null || !run.reason().startsWith("Storing") && !run.reason().startsWith("Taking") && !run.reason().startsWith("Waiting to") && !run.reason().startsWith("Claimed what fits"))break;}
        return step;
    }

    static void advanceTo(ProductionRun run,Stage stage,long[] clock) {
        for(int i=0;i<5 && run.stage()!=stage;i++){run.tick(true,clock[0]);clock[0]+=500;}
        assertEquals(stage,run.stage(),run.reason());
    }

    @Test void claimThatFillsTheInventoryIsStoredThenTheRestOfTheSameOrderIsClaimed()throws Exception {
        var env=new Env();var catalog=catalog();long[] clock={1000};
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        assertEquals(Step.PENDING,run.tick(true,clock[0]));assertEquals(List.of("INPUT:128"),env.orders);
        // The engine claimed what fitted and now waits for the run to store it.
        env.give("INPUT",64);env.awaiting.add("INPUT");
        env.orderResult=new ProductionBazaarOrders.Result(false,false,128,1280,null,"Claiming in parts");
        assertTrue(run.wantsMenu());
        tickUntilSettled(run,clock,100);
        assertEquals(Map.of("INPUT",64),env.recordedStorage);assertEquals(64,env.storage.stored(run.jobId(),"INPUT"));
        assertEquals(0,ProductionStorage.Move.inventoryCount(env.world.menu(),"INPUT"));
        assertEquals(64,env.world.stored("ec 1","INPUT"));
        // The second claim lands in the now-free inventory and completes the order.
        env.give("INPUT",64);
        env.orderResult=new ProductionBazaarOrders.Result(true,false,128,1280,null,"All claims verified");
        assertEquals(Step.PENDING,run.tick(true,clock[0]+=500));assertEquals(1,env.acknowledgements);assertEquals(1280,run.spent());
        advanceTo(run,Stage.PROCESS,clock);
        assertEquals(List.of("INPUT:128"),env.orders,"Stored units count as held, so nothing more is bought");
    }
    @Test void loosePlayerItemsAreStoredBeforeAFundedOrderBasketIsSubmitted()throws Exception {
        var env=new Env();env.junk(26);long now=1000;
        var recipe=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",64),
            Arrays.asList(new ProductionRecipe.Ingredient("INPUT",64),null,null,null,null,null,null,null,null),0,0,"",null);
        var catalog=new RecipeCatalog(List.of(recipe),Map.of("INPUT","Input","OUTPUT","Output"));
        var run=ProductionRun.startCraft(env,catalog,recipe,16,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        assertEquals(Step.PENDING,run.tick(true,now));assertTrue(env.orders.isEmpty());
        for(int i=0;i<160&&env.orders.isEmpty();i++) {
            assertEquals(Step.PENDING,run.tick(true,now+=500),run.reason());
        }
        assertEquals(List.of("INPUT:1024"),env.orders);
        assertEquals(384,env.storage.stored(run.jobId(),"COBBLE"),"Only six stacks need to move");
        assertEquals(384,env.world.stored("ec 1","COBBLE"));
        assertEquals(39,env.storage.emptySlots(env.storagePages()),"Planning must retain the remaining capacity after the deposit");
        assertEquals(1280,ProductionStorage.Move.inventoryCount(env.world.menu(),"COBBLE"));
        assertEquals(0,run.spent());assertEquals(1,env.orders.size());
    }
    @Test void overflowingInstantPurchaseUsesStorageAndStashUntilTheOriginalAmountIsVerified()throws Exception {
        class DeliveryEnv extends Env {
            String market;boolean sign;double balance=1_000_000;int writes,stash;
            DeliveryEnv()throws Exception{}
            public double purse(){return balance;}
            public Set<String> orderTracked(){return Set.of();}
            public boolean signOpen(){return sign;}
            public MenuSnapshot menu(){
                if(world.open!=null||market==null)return world.menu();
                var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
                for(int i=0;i<36;i++)slots.add(world.invIds[i]==null?SlotView.empty(54+i,true,i):ProductionRunTest.item(54+i,world.invIds[i],world.invCounts[i]));
                if(market.equals("Input")) {
                    slots.set(13,ProductionRunTest.item(13,"INPUT",1));
                    slots.set(10,SlotView.named(10,"Buy Instantly",List.of("Input","Price per unit: 10 coins")));
                }else slots.set(16,SlotView.named(16,"Custom Amount",List.of()));
                return new MenuSnapshot(market.equals("Input")?500:501,market,true,slots);
            }
            public GameActions actions(){return new GameActions(){
                public void click(int slot,boolean shift){
                    if(world.open!=null){world.click(slot,shift);return;}
                    if(slot==10)market="Amount";else if(slot==16)sign=true;
                }
                public void closeMenu(){market=null;world.closeMenu();}
                public void command(String text){
                    world.command(text);
                    if(text.equals("bz Input"))market="Input";
                    if(text.equals("pickupstash")) {
                        int delivered=Math.min(stash,world.menu().emptyInventorySlots()*64);give("INPUT",delivered);stash-=delivered;
                    }
                }
                public void message(String text){}
                public boolean writeSign(String text){
                    writes++;assertEquals("4096",text);sign=false;market=null;balance-=40960;
                    give("INPUT",2048);stash=2048;return true;
                }
            };}
        }
        var env=new DeliveryEnv();
        var recipe=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",64),
            Arrays.asList(new ProductionRecipe.Ingredient("INPUT",64),null,null,null,null,null,null,null,null),0,0,"",null);
        var catalog=new RecipeCatalog(List.of(recipe),Map.of("INPUT","Input","OUTPUT","Output"));
        var run=ProductionRun.startCraft(env,catalog,recipe,64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.INSTANT_OFFER);
        long now=1000;
        for(int i=0;i<300&&run.stage()==Stage.PROCURE;i++)assertEquals(Step.PENDING,run.tick(true,now+=500),run.reason());
        assertEquals(Stage.PROCESS,run.stage(),run.reason());assertEquals(1,env.writes);assertEquals(0,env.stash);
        assertEquals(2,env.world.commands.stream().filter("pickupstash"::equals).count());
        assertEquals(40960,run.spent());
        assertEquals(4096,env.world.inventory("INPUT")+env.storage.stored(run.jobId(),"INPUT"));
        assertEquals(40960,env.jobs.find(run.jobId()).orElseThrow().costBasis());assertTrue(env.orders.isEmpty());
    }

    @Test void storedInputsAreCraftedInRoundsThatFitTheInventory()throws Exception {
        var env=new Env();var catalog=catalog();long[] clock={1000};
        env.junk(32);env.give("INPUT",28); // Four free slots after the inputs.
        env.world.put("ec 1",0,"INPUT",64);env.world.put("ec 1",1,"INPUT",36);
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        env.storage.move(run.jobId(),"ec 1","INPUT",100);
        advanceTo(run,Stage.PROCESS,clock);
        assertTrue(env.orders.isEmpty(),"Inventory and storage together cover every input");
        int rounds=0;Step step=Step.PENDING;
        for(int i=0;i<400 && run.stage()==Stage.PROCESS;i++) {
            step=run.tick(true,clock[0]);clock[0]+=500;
            assertNotEquals(Step.UNCERTAIN,step,run.reason());assertNotEquals(Step.BLOCKED,step,run.reason());
            if(env.craftQueued){rounds++;env.finishCraft();}
        }
        assertEquals(Stage.SELL,run.stage(),run.reason());
        assertTrue(rounds>=2,"Crafted in "+rounds+" rounds: "+env.crafts);
        assertEquals(64,env.crafts.stream().mapToInt(Integer::intValue).sum());
        assertEquals(64,ProductionStorage.Move.inventoryCount(env.world.menu(),"OUTPUT"));
        assertEquals(0,env.storage.stored(run.jobId(),"INPUT"));assertEquals(0,env.world.stored("ec 1","INPUT"));
    }

    @Test void fullStorageWaitsBetweenAttemptsAndAsksForReviewAfterSeveral()throws Exception {
        var env=new Env();var catalog=catalog();long[] clock={1000};
        for(int i=0;i<45;i++)env.world.put("ec 1",i,"COBBLE",64);
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        assertEquals(Step.PENDING,run.tick(true,clock[0]));
        env.give("INPUT",64);env.awaiting.add("INPUT");
        env.orderResult=new ProductionBazaarOrders.Result(false,false,128,1280,null,"Claiming in parts");
        Step step=tickUntilSettled(run,clock,100);
        assertEquals(Step.BLOCKED,step,run.reason());
        int opened=env.world.commands.size();
        for(int i=0;i<20;i++){env.awaiting.add("INPUT");assertEquals(Step.BLOCKED,run.tick(true,clock[0]+=500),run.reason());}
        assertEquals(opened,env.world.commands.size(),"No storage page is reopened during the wait: "+env.world.commands);
        for(int attempt=2;attempt<=ProductionRun.STORAGE_ATTEMPTS && step!=Step.UNCERTAIN;attempt++){
            clock[0]+=ProductionRun.STORAGE_RETRY_MS;env.awaiting.add("INPUT");step=tickUntilSettled(run,clock,100);
        }
        assertEquals(Step.UNCERTAIN,step,run.reason());
    }

    @Test void inputsMissingFromStorageStopTheRunInsteadOfBuyingAgain()throws Exception {
        var env=new Env();var catalog=catalog();long[] clock={1000};
        env.give("INPUT",28);
        var run=ProductionRun.startCraft(env,catalog,catalog.forOutput("OUTPUT").getFirst(),64,ProductionRun.SELL_ON_BAZAAR,0,BazaarStrategy.ORDER_OFFER);
        env.storage.move(run.jobId(),"ec 1","INPUT",100); // The ledger says 100, the page holds none.
        advanceTo(run,Stage.PROCESS,clock);
        Step step=Step.PENDING;
        for(int i=0;i<50 && step==Step.PENDING;i++){step=run.tick(true,clock[0]);clock[0]+=500;}
        assertEquals(Step.UNCERTAIN,step);assertTrue(run.reason().contains("should hold 100"),run.reason());
        assertTrue(env.orders.isEmpty());assertTrue(env.crafts.isEmpty());
    }
}
