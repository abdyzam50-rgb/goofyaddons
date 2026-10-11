package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A buy order larger than the inventory is claimed in parts, with the run storing between claims. */
class ProductionStorageClaimTest {
    @TempDir Path dir;
    static final String ID="ENCHANTED_MITHRIL",NAME="Enchanted Mithril",PARENT="parent-big";

    private ConfiguredTestServices services(boolean storage){
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        return new ConfiguredTestServices(){
            public boolean productionOnly(){return true;}
            public boolean productionStorage(){return storage;}
            public double purse(){return 1e9;}
        };
    }
    private GeneralPosition filledOrder(FakeWorld world) {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=2560;p.unitCost=100;
        p.stage=GeneralPosition.Stage.BUY_ORDER;p.productionBuy=true;p.productionBasket=true;p.productionParent=PARENT;
        p.maximumBuyPrice=103;p.purchasePriceKnown=true;p.submitted=true;p.tradeId="big-order";p.placedAt=world.clock()-10_000;
        return p;
    }
    /** The orders menu with the order still showing these claimable units, and this many held. */
    private MenuSnapshot orders(int claimable,int held) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        slots.set(49,SlotView.named(49,"Close",List.of()));
        if(claimable>0)slots.set(11,new SlotView(11,false,11,false,"BUY "+NAME,"BUY "+NAME,
            List.of("Order amount: 2,560x","Filled: 2,560/2,560 (100%)","You have "+String.format(Locale.ROOT,"%,d",claimable)+" items to claim!"),ID,null,1,64));
        for(int i=0;held>0;i++){int n=Math.min(64,held);slots.set(54+i,new SlotView(54+i,true,i,false,NAME,NAME,List.of(),ID,null,n,64));held-=n;}
        return new MenuSnapshot(42,"Your Bazaar Orders",true,slots);
    }
    private void drive(GeneralFlipper engine,FakeWorld world,long millis) {
        for(long n=0;n<millis;n+=60){engine.poll();engine.onTick();world.advance(60);}
    }
    private GeneralFlipper engine(FakeWorld world,RecordingActions actions,JsonGeneralOrderRepository repo,boolean storage)throws Exception {
        var engine=new GeneralFlipper(world,actions,repo,services(storage));engine.start();
        var products=GeneralFlipper.class.getDeclaredField("products");products.setAccessible(true);
        products.set(engine,com.google.gson.JsonParser.parseString("{\""+ID+"\":{\"sell_summary\":[{\"pricePerUnit\":100}],\"buy_summary\":[{\"pricePerUnit\":200}]}}").getAsJsonObject());
        var quotesAt=GeneralFlipper.class.getDeclaredField("quotesAt");quotesAt.setAccessible(true);quotesAt.setLong(engine,world.clock());
        return engine;
    }

    @Test void claimsWhatFitsWaitsForStorageThenClaimsTheRestOfTheSameOrder()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        repo.save(List.of(filledOrder(world)));
        world.showing(orders(2560,0));
        var actions=new RecordingActions();var engine=engine(world,actions,repo,true);
        drive(engine,world,1400);
        assertFalse(engine.productionFailed());
        assertEquals(1,actions.serverEffects().stream().filter("click:11"::equals).count(),actions.performed().toString());
        // The server claimed 32 stacks, all the room the engine allows itself.
        world.showing(orders(512,2048));drive(engine,world,2000);
        assertFalse(engine.productionFailed());
        assertEquals(List.of(ID),engine.productionAwaitingStorage(PARENT));
        assertTrue(repo.load().getFirst().productionAwaitingStorage,"The wait survives a restart");
        int clicks=actions.serverEffects().size();
        drive(engine,world,5000);
        assertEquals(clicks,actions.serverEffects().size(),"Nothing is clicked while the run stores the claim");
        assertEquals(Set.of(ID),engine.productionStorable(PARENT));
        // The run verified 2,048 units into storage.
        world.showing(orders(512,0));
        assertTrue(engine.productionStored(PARENT,ID,2048));
        assertTrue(engine.productionAwaitingStorage(PARENT).isEmpty());
        drive(engine,world,1400);
        assertEquals(2,actions.serverEffects().stream().filter("click:11"::equals).count(),"The same order is claimed again");
        world.showing(orders(0,512));drive(engine,world,2500);
        assertFalse(engine.productionFailed());
        var p=engine.productionPosition(PARENT);
        assertEquals(GeneralPosition.Stage.INVENTORY,p.stage);assertEquals(2560,p.quantity);assertEquals(2048,p.productionStoredUnits);
        assertEquals(256000,p.cost());
        assertTrue(engine.consumeProduction(PARENT));
    }

    @Test void unitsHeldBeforeTheOrderLeaveTheBaselineWhenStored()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("baseline.json"));
        var p=filledOrder(world);p.productionInventoryBefore=10;p.productionAwaitingStorage=true;repo.save(List.of(p));
        world.showing(orders(512,2058));
        var engine=engine(world,new RecordingActions(),repo,true);
        world.showing(orders(512,0));
        assertTrue(engine.productionStored(PARENT,ID,2058));
        var saved=repo.load().getFirst();
        assertEquals(0,saved.productionInventoryBefore);assertEquals(2048,saved.productionStoredUnits);
        assertEquals(2048,saved.acquiredInventory(0));
    }

    @Test void storingRequiresAWaitingClaimAndNeverExceedsTheOrder()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("refuse.json"));
        var p=filledOrder(world);repo.save(List.of(p));world.showing(orders(2560,0));
        var engine=engine(world,new RecordingActions(),repo,true);
        assertFalse(engine.productionStored(PARENT,ID,64),"Not waiting for storage");
        engine.productionPosition(PARENT).productionAwaitingStorage=true;
        assertFalse(engine.productionStored(PARENT,ID,4000),"More than the order");
        assertFalse(engine.productionStored("another-parent",ID,64));
        assertEquals(0,engine.productionPosition(PARENT).productionStoredUnits);
    }

    @Test void serverFillingEveryFreeSlotIsAcceptedAsAPartialClaim()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("full.json"));
        repo.save(List.of(filledOrder(world)));world.showing(orders(2560,0));
        var engine=engine(world,new RecordingActions(),repo,true);
        drive(engine,world,1400);
        // The engine keeps four slots spare for itself; the server does not.
        world.showing(orders(256,2304));drive(engine,world,2000);
        assertFalse(engine.productionFailed());assertEquals(List.of(ID),engine.productionAwaitingStorage(PARENT));
    }

    @Test void inventoryShrinkingDuringAPartialClaimStopsForReview()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("shrink.json"));
        var p=filledOrder(world);p.productionInventoryBefore=64;repo.save(List.of(p));world.showing(orders(2560,64));
        var engine=engine(world,new RecordingActions(),repo,true);
        drive(engine,world,1400);
        world.showing(orders(2560,0));drive(engine,world,2000);
        assertTrue(engine.productionFailed());assertFalse(repo.load().getFirst().productionAwaitingStorage);
    }

    @Test void noRoomAtAllWaitsForStorageWithoutClicking()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("noroom.json"));
        var p=filledOrder(world);p.productionInventoryBefore=0;repo.save(List.of(p));
        var full=orders(2560,0);var slots=new ArrayList<>(full.slots());
        for(int i=0;i<34;i++)slots.set(54+i,new SlotView(54+i,true,i,false,"Cobblestone","Cobblestone",List.of(),"COBBLESTONE",null,64,64));
        world.showing(new MenuSnapshot(42,"Your Bazaar Orders",true,slots));
        var actions=new RecordingActions();var engine=engine(world,actions,repo,true);
        drive(engine,world,1400);
        assertFalse(engine.productionFailed());assertFalse(actions.serverEffects().contains("click:11"));
        assertEquals(List.of(ID),engine.productionAwaitingStorage(PARENT));
        assertTrue(engine.productionStored(PARENT,ID,0),"Recording nothing stored makes the order look again");
        assertTrue(engine.productionAwaitingStorage(PARENT).isEmpty());
    }

    @Test void withoutStorageAClaimLargerThanTheInventoryStillStops()throws Exception {
        var world=new FakeWorld();var repo=new JsonGeneralOrderRepository(()->dir.resolve("off.json"));
        repo.save(List.of(filledOrder(world)));world.showing(orders(2560,0));
        var actions=new RecordingActions();var engine=engine(world,actions,repo,false);
        drive(engine,world,1400);
        assertTrue(engine.productionFailed());assertFalse(actions.serverEffects().contains("click:11"));
    }

    @Test void journalRejectsStorageFieldsOnPositionsThatAreNotProductionBuys() {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=10;p.unitCost=1;p.stage=GeneralPosition.Stage.INVENTORY;
        p.productionStoredUnits=5;
        assertThrows(IllegalArgumentException.class,()->GeneralPosition.validated(List.of(p)));
        p.productionBuy=true;p.productionParent=PARENT;p.maximumBuyPrice=2;
        assertEquals(1,GeneralPosition.validated(List.of(p)).size());
        p.productionStoredUnits=11;
        assertThrows(IllegalArgumentException.class,()->GeneralPosition.validated(List.of(p)));
    }
}
