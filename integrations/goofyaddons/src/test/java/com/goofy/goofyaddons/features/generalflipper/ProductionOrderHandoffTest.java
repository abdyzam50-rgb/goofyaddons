package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionOrderHandoffTest {
    @TempDir Path dir;
    private MenuSnapshot inventory(String id,int units){
        var slots=new ArrayList<SlotView>();for(int i=0;i<36;i++)slots.add(SlotView.empty(i,true,i));
        if(units>0)slots.set(0,new SlotView(0,true,0,false,id,id,List.of(),id,null,units,64));
        return new MenuSnapshot(1,null,true,slots);
    }
    private ConfiguredTestServices services(){
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        return new ConfiguredTestServices(){public boolean productionOnly(){return true;}public double purse(){return 10000;}};
    }
    @Test void claimedIngredientIsHandedOffWithoutEnteringASellOffer()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var p=new GeneralPosition();p.item=new GeneralItem("INPUT","Input");p.quantity=2;p.unitCost=100;p.stage=GeneralPosition.Stage.INVENTORY;
        p.productionBuy=true;p.maximumBuyPrice=103;p.purchasePriceKnown=true;p.tradeId="input-order";repo.save(List.of(p));
        var world=new FakeWorld().showing(inventory("INPUT",2));var actions=new RecordingActions();
        var engine=new GeneralFlipper(world,actions,repo,services());engine.start();engine.poll();
        assertFalse(engine.needsMenu());engine.onTick();assertTrue(actions.serverEffects().isEmpty());
        assertTrue(engine.consumeProduction());assertTrue(repo.load().isEmpty());assertFalse(engine.hasRetainedPositions());
    }
    @Test void productionBuyUsesItsBasketCeilingInsteadOfOrdinaryItemFlipProfitFilters()throws Exception {
        var world=new FakeWorld().showing(inventory("INPUT",0));var actions=new RecordingActions();var services=services();
        var engine=new GeneralFlipper(world,actions,new JsonGeneralOrderRepository(()->dir.resolve("orders.json")),services);
        engine.start();assertTrue(engine.enqueueProduction("INPUT","Input",2,100,true,103));
        var active=GeneralFlipper.class.getDeclaredField("active");active.setAccessible(true);active.set(engine,engine.productionPosition());
        var field=GeneralFlipper.class.getDeclaredField("session");field.setAccessible(true);var ctx=(GeneralContext)field.get(engine);
        assertTrue(new GeneralBuy().acceptPrice(ctx,102));assertEquals(204,engine.productionPosition().cost());
        assertFalse(new GeneralBuy().acceptPrice(ctx,104));assertTrue(engine.productionFailed());
        assertTrue(engine.hasRetainedPositions());assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void queueRejectsExistingInventoryAndRetainedOrdersWithoutReplacingTheirEvidence()throws Exception {
        var world=new FakeWorld().showing(inventory("INPUT",1));var engine=new GeneralFlipper(world,new RecordingActions(),new JsonGeneralOrderRepository(()->dir.resolve("orders.json")),services());
        engine.start();assertFalse(engine.enqueueProduction("INPUT","Input",2,100,true,103));
        world.showing(inventory("INPUT",0));assertTrue(engine.enqueueProduction("INPUT","Input",2,100,true,103));
        String trade=engine.productionPosition().tradeId;
        assertFalse(engine.enqueueProduction("OTHER","Other",1,50,true,55));assertEquals(trade,engine.productionPosition().tradeId);
    }
    @Test void recoveryCancelsOnlyARetainedVerifiedIngredientOrderWithoutQueueingPurchases()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var p=new GeneralPosition();p.item=new GeneralItem("INPUT","Input");p.quantity=1280;p.unitCost=11717.8;
        p.stage=GeneralPosition.Stage.BUY_ORDER;p.productionBuy=true;p.maximumBuyPrice=12000;p.purchasePriceKnown=true;p.submitted=true;p.tradeId="retained";
        repo.save(List.of(p));var actions=new RecordingActions();
        var engine=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),actions,repo,services());
        assertTrue(engine.recoverProductionBuy());
        assertTrue(repo.load().getFirst().cancelRequested);assertEquals("retained",engine.productionPosition().tradeId);
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void recoveryRefusesUnsubmittedPurchasesUnknownCostsAndSellOrders()throws Exception {
        for(int variant=0;variant<3;variant++) {
            var path=dir.resolve("orders-"+variant+".json");
            var repo=new JsonGeneralOrderRepository(()->path);
            var p=new GeneralPosition();p.item=new GeneralItem("INPUT","Input");p.quantity=2;p.unitCost=100;p.maximumBuyPrice=103;
            p.stage=variant==0?GeneralPosition.Stage.PLANNED:variant==1?GeneralPosition.Stage.BUY_ORDER:GeneralPosition.Stage.SELL_ORDER;
            p.productionBuy=variant!=2;p.purchasePriceKnown=variant!=1;p.tradeId="retained";repo.save(List.of(p));
            var actions=new RecordingActions();var engine=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),actions,repo,services());
            assertFalse(engine.recoverProductionBuy());assertFalse(repo.load().getFirst().cancelRequested);assertTrue(actions.serverEffects().isEmpty());
        }
    }

    @Test void recoveryVerifiesRoundedOrderOwnershipAndRefundBeforeReleasingTheChild()throws Exception {
        var services=services();GoofyConfig.INSTANCE.general.maxReprices=0;
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var p=new GeneralPosition();p.item=new GeneralItem("INPUT","Input");p.quantity=1280;p.unitCost=100;
        p.stage=GeneralPosition.Stage.BUY_ORDER;p.productionBuy=true;p.maximumBuyPrice=103;p.purchasePriceKnown=true;p.submitted=true;p.tradeId="retained";
        var world=new FakeWorld();p.placedAt=world.clock();repo.save(List.of(p));
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        slots.set(49,SlotView.named(49,"Close",List.of()));
        slots.set(11,SlotView.named(11,"BUY Input",List.of("Order amount: 1,280x","Filled: 0/1.3k (0%)","Click to view options!")));
        world.showing(new MenuSnapshot(42,"Your Bazaar Orders",true,slots));
        var actions=new RecordingActions();var engine=new GeneralFlipper(world,actions,repo,services);
        assertTrue(engine.recoverProductionBuy());drive(engine,world,1400);
        assertFalse(engine.productionFailed());assertTrue(actions.serverEffects().contains("click:11"),actions.performed().toString());
        var options=new ArrayList<>(slots);options.set(11,SlotView.empty(11,false,11));options.set(13,SlotView.named(13,"Cancel Order",List.of()));
        world.showing(new MenuSnapshot(43,"Buy Order Options",true,options));drive(engine,world,180);
        assertFalse(engine.productionPosition().completed);
        engine.onNotice("[Bazaar] Cancelled! Refunded 128,000 coins from cancelling Buy Order!");
        var empty=new ArrayList<>(slots);empty.set(11,SlotView.empty(11,false,11));
        world.showing(new MenuSnapshot(44,"Your Bazaar Orders",true,empty));drive(engine,world,1800);
        assertFalse(engine.productionFailed());assertTrue(engine.productionPosition().completed);
        assertTrue(repo.load().getFirst().completed,"Evidence stays durable until parent/manual-review handoff");
        assertTrue(engine.consumeProduction());assertTrue(repo.load().isEmpty());
    }
    private void drive(GeneralFlipper engine,FakeWorld world,long millis) {
        for(long n=0;n<millis;n+=60){engine.poll();engine.onTick();world.advance(60);}
    }

}
