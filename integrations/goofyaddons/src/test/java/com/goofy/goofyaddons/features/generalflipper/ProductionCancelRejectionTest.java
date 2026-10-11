package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionCancelRejectionTest {
    private static final String ID="ENCHANTED_MYCELIUM_CUBE",NAME="Enchanted Mycelium Cube";
    private static final String REJECTION="[Bazaar] There was an error cancelling your order!";
    @TempDir Path dir;
    @BeforeEach void configure(){GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;}
    private static GeneralPosition buy(int units,double price) {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=units;p.unitCost=price;p.sellPrice=price;
        p.maximumBuyPrice=price*1.03;p.productionParent="parent-buy";p.tradeId="buy-child";p.stage=GeneralPosition.Stage.BUY_ORDER;
        p.productionBuy=true;p.productionBasket=true;p.purchasePriceKnown=true;p.submitted=true;return p;
    }
    private static GeneralPosition sale(){
        var p=new GeneralPosition();p.item=new GeneralItem("SELL_OUTPUT","Crafted Output");p.quantity=4;p.unitCost=130;p.sellPrice=130;
        p.minimumSellPrice=100;p.productionParent="parent-sale";p.tradeId="sale-child";p.saleEvent="sale-receipt";
        p.stage=GeneralPosition.Stage.SELL_ORDER;p.submitted=true;p.checkedAt=321;p.placedAt=999_900_000;return p;
    }
    private static MenuSnapshot menu(String title,int container,int buyStock,int sellStock,List<SlotView> controls){
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        if(buyStock>0)slots.set(54,new SlotView(54,true,0,false,NAME,NAME,List.of(),ID,null,buyStock,64));
        if(sellStock>0)slots.set(55,new SlotView(55,true,1,false,"Crafted Output","Crafted Output",List.of(),"SELL_OUTPUT",null,sellStock,64));
        controls.forEach(v->slots.set(v.index(),v));return new MenuSnapshot(container,title,true,slots);
    }
    private static MenuSnapshot orders(int container,int units,int filled,double price,int stock,String owner,boolean duplicate,boolean seller,int sold){
        var controls=new ArrayList<SlotView>();controls.add(SlotView.named(49,"Close",List.of()));
        if(units>0){var lore=List.of("Order amount: "+units,"Filled: "+filled+"/"+units,"Price per unit: "+price+" coins",
            "By: [MVP+] "+owner,filled>stock?"You have "+(filled-stock)+" items to claim!":"Click to view options!");
            controls.add(SlotView.named(11,"BUY "+NAME,lore));if(duplicate)controls.add(SlotView.named(12,"BUY "+NAME,lore));}
        if(seller)controls.add(SlotView.named(15,"SELL Crafted Output",List.of("Offer amount: 4","Filled: "+sold+"/4",
            "Price per unit: 130 coins","By: [MVP+] curedmc","Click to view options!")));
        return menu("Co-op Bazaar Orders",container,stock,0,controls);
    }
    private final class Market extends ConfiguredTestServices {
        final FakeWorld world;final List<String> events=new ArrayList<>();int acquired;double acquiredCost;
        Market(FakeWorld world){this.world=world;}
        public boolean productionOnly(){return true;}public double purse(){return 1000000;}
        public JsonObject latestQuotes(){return JsonParser.parseString("{\"lastUpdated\":"+world.clock()+",\"products\":{\"SELL_OUTPUT\":{\"sell_summary\":[{\"pricePerUnit\":100}],\"buy_summary\":[{\"pricePerUnit\":120}]}}}").getAsJsonObject();}
        public void event(String level,String name,Map<String,?> details){events.add(name);}
        public void acquire(GeneralPosition p){acquired=p.quantity;acquiredCost=p.cost();}
    }
    private final class Fixture {
        final FakeWorld world=new FakeWorld().username("curedmc");final RecordingActions actions=new RecordingActions();
        final JsonGeneralOrderRepository repo;final Market market=new Market(world);final GeneralFlipper engine;
        Fixture(String file,List<GeneralPosition> positions)throws Exception{
            repo=new JsonGeneralOrderRepository(()->dir.resolve(file));repo.save(positions);
            engine=new GeneralFlipper(world,actions,repo,market);
        }
        void tick(long millis){for(long i=0;i<millis;i+=60){engine.poll();engine.onTick();world.advance(60);}}
        void until(String step){for(int i=0;i<100 && !step.equals(engine.diagnosticState().get("step"));i++)tick(60);
            assertEquals(step,engine.diagnosticState().get("step"),engine.diagnosticState().toString());}
        long clicks(int slot){return actions.serverEffects().stream().filter(a->a.equals("click:"+slot)).count();}
        void beginBuy(int units,double price,boolean sibling){
            world.showing(orders(10,units,0,price,0,"curedmc",false,sibling,0));assertTrue(engine.recoverProductionBuy());
            until("CANCEL_DETAIL");world.showing(menu("Buy Order Options",11,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));
            tick(120);assertEquals(1,clicks(13));assertEquals("VERIFY_CANCEL",engine.diagnosticState().get("step"));
        }
    }

    @Test void explicitRejectionRetriesExactOrderAndPreservesSubmittedSiblingOffer()throws Exception{
        var f=new Fixture("retry.json",List.of(sale(),buy(1,133475.5)));f.beginBuy(1,133475.5,true);
        f.engine.onNotice(REJECTION);f.engine.onNotice(REJECTION); // Duplicate notice cannot authorize another retry.
        f.world.showing(orders(12,1,0,133475.5,0,"curedmc",false,true,0));f.until("CANCEL_DETAIL");
        f.world.showing(menu("Buy Order Options",13,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        assertEquals(2,f.clicks(13));f.engine.onNotice("[Bazaar] Cancelled! Refunded 133,475.5 coins from cancelling Buy Order!");
        f.world.showing(orders(14,0,0,0,0,"curedmc",false,true,0));f.tick(1600);
        assertTrue(f.engine.productionPosition().completed,f.engine.diagnosticState().toString());assertTrue(f.engine.consumeProduction());
        var sibling=f.repo.load().getFirst();assertEquals("parent-sale",sibling.productionParent);assertEquals(321,sibling.checkedAt);
        assertTrue(sibling.submitted);assertFalse(sibling.cancelRequested);assertNull(sibling.verifiedProceeds);
        assertFalse(f.market.capital().occupied(ID));assertTrue(f.market.capital().occupied("SELL_OUTPUT"));
        assertEquals(1,f.market.events.stream().filter("order.cancel_rejected"::equals).count());
        assertEquals(1,f.market.events.stream().filter("order.cancel_rejection_retry"::equals).count());
    }
    @Test void fillsDuringRejectedCancellationAreClaimedBeforeTheRemainderIsCancelled()throws Exception{
        var f=new Fixture("fill.json",List.of(buy(2,100)));f.beginBuy(2,100,false);f.engine.onNotice(REJECTION);
        f.world.showing(orders(12,2,1,100,0,"curedmc",false,false,0));f.until("CANCEL_DETAIL");f.tick(300);
        assertEquals(1,f.clicks(13)); // The newly claimed item has not reached inventory yet.
        f.world.showing(orders(13,2,1,100,1,"curedmc",false,false,0));f.tick(1000);
        f.world.showing(menu("Buy Order Options",14,1,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        assertEquals(2,f.clicks(13));f.engine.onNotice("[Bazaar] Cancelled buy order for 2x "+NAME+"!");
        f.world.showing(orders(15,0,0,0,1,"curedmc",false,false,0));f.tick(1600);
        var p=f.repo.load().getFirst();assertEquals(GeneralPosition.Stage.INVENTORY,p.stage);assertEquals(1,p.quantity);
        assertEquals(1,f.market.acquired);assertEquals(100,f.market.acquiredCost);assertFalse(p.cancelRequested);
    }
    @Test void delayedSuccessfulRefundAfterRejectionWinsWithoutAnotherCancellation()throws Exception{
        var f=new Fixture("late.json",List.of(buy(1,100)));f.beginBuy(1,100,false);f.engine.onNotice(REJECTION);
        f.engine.onNotice("[Bazaar] Cancelled! Refunded 100 coins from cancelling Buy Order!");
        f.world.showing(orders(12,0,0,0,0,"curedmc",false,false,0));f.tick(1600);
        assertTrue(f.repo.load().getFirst().completed);assertEquals(1,f.clicks(13));
        assertFalse(f.market.events.contains("order.cancel_rejection_retry"));assertEquals(0,f.market.capital().committed());
    }
    @Test void delayedSuccessfulReceiptDuringRetryReviewWinsBeforeAnotherOrderClick()throws Exception{
        for(boolean refund:List.of(true,false)){
            var f=new Fixture("review-receipt-"+refund+".json",List.of(buy(1,100)));f.beginBuy(1,100,false);f.engine.onNotice(REJECTION);
            f.world.showing(orders(12,1,0,100,0,"curedmc",false,false,0));f.until("ORDERS");
            long orderClicks=f.clicks(11);
            f.engine.onNotice(refund?"[Bazaar] Cancelled! Refunded 100 coins from cancelling Buy Order!"
                :"[Bazaar] Cancelled buy order for 1x "+NAME+"!");
            f.until("VERIFY_CANCEL");
            assertEquals(orderClicks,f.clicks(11));assertEquals(1,f.clicks(13));
            f.world.showing(orders(13,0,0,0,0,"curedmc",false,false,0));f.tick(1600);
            assertTrue(f.repo.load().getFirst().completed);assertFalse(f.market.capital().occupied(ID));assertEquals(1,f.clicks(13));
        }
    }
    @Test void genericTryLaterOrMissingAcknowledgementNeverAuthorizesAnotherCancel()throws Exception{
        var f=new Fixture("unknown.json",List.of(buy(1,100)));f.beginBuy(1,100,false);
        f.engine.onNotice("[Bazaar] Please try again later!");
        f.world.showing(orders(12,1,0,100,0,"curedmc",false,false,0));f.tick(8000);
        assertEquals(1,f.clicks(13));assertTrue(f.repo.load().getFirst().cancelRequested);assertFalse(f.repo.load().getFirst().completed);
        assertTrue(f.market.capital().occupied(ID));assertFalse(f.market.events.contains("order.cancel_rejection_retry"));
    }
    @Test void rejectedCancellationCannotRetryForeignDuplicateOrDifferentlyPricedOrders()throws Exception{
        for(int variant=0;variant<3;variant++){
            var f=new Fixture("unsafe-"+variant+".json",List.of(buy(1,100)));f.beginBuy(1,100,false);f.engine.onNotice(REJECTION);
            f.world.showing(orders(12,1,0,variant==2?101:100,0,variant==0?"OtherPlayer":"curedmc",variant==1,false,0));f.tick(4000);
            assertEquals(1,f.clicks(13));var p=f.repo.load().getFirst();assertTrue(p.cancelRequested);assertFalse(p.completed);
            assertTrue(f.market.capital().occupied(ID));assertTrue((Boolean)f.engine.diagnosticState().get("paused"));
        }
    }
    @Test void threeExplicitRejectionsStopWithoutDroppingOwnershipOrEscrow()throws Exception{
        var f=new Fixture("exhausted.json",List.of(buy(1,100)));f.beginBuy(1,100,false);
        for(int attempt=1;attempt<=3;attempt++){
            assertEquals(attempt,f.clicks(13));f.engine.onNotice(REJECTION);
            if(attempt==3)break;
            f.world.showing(orders(12+attempt*2,1,0,100,0,"curedmc",false,false,0));f.until("CANCEL_DETAIL");
            f.world.showing(menu("Buy Order Options",13+attempt*2,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        }
        f.tick(10000);assertEquals(3,f.clicks(13));var p=f.repo.load().getFirst();assertTrue(p.cancelRequested);assertTrue(p.submitted);assertFalse(p.completed);
        assertEquals(100,p.cost());assertTrue(f.market.capital().occupied(ID));assertTrue((Boolean)f.engine.diagnosticState().get("paused"));
    }
    @Test void stoppingRejectedRecoveryRetainsJournalAndNeedsFreshExplicitRecovery()throws Exception{
        var f=new Fixture("restart.json",List.of(buy(1,100)));f.beginBuy(1,100,false);f.engine.onNotice(REJECTION);f.engine.stop();
        var saved=f.repo.load().getFirst();assertTrue(saved.cancelRequested);assertTrue(saved.submitted);assertFalse(saved.completed);
        f.world.showing(orders(12,1,0,100,0,"curedmc",false,false,0));f.tick(4000);assertEquals(1,f.clicks(13));
        assertTrue(f.engine.recoverProductionBuy());f.until("CANCEL_DETAIL");
        f.world.showing(menu("Buy Order Options",13,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        assertEquals(2,f.clicks(13));assertTrue(f.market.capital().occupied(ID));assertFalse(f.repo.load().getFirst().completed);
    }
    @Test void sellRejectionKeepsAlreadyClaimedPartialSaleProceeds()throws Exception{
        var f=new Fixture("partial-sale.json",List.of(sale()));
        f.world.showing(orders(10,0,0,0,0,"curedmc",false,true,1));assertTrue(f.engine.resumeProductionSales());f.until("CANCEL_DETAIL");
        f.engine.onNotice("[Bazaar] Claimed 120 coins from selling 1x Crafted Output at 120 each!");f.tick(1000);
        f.world.showing(menu("Sell Order Options",11,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        assertEquals(1,f.clicks(13));f.engine.onNotice(REJECTION);
        f.world.showing(orders(12,0,0,0,0,"curedmc",false,true,1));f.until("CANCEL_DETAIL");
        f.world.showing(menu("Sell Order Options",13,0,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);assertEquals(2,f.clicks(13));
        f.engine.onNotice("[Bazaar] Cancelled sell order for 4x Crafted Output!");
        f.world.showing(menu("Co-op Bazaar Orders",14,0,3,List.of(SlotView.named(49,"Close",List.of()))));f.tick(1600);
        var p=f.repo.load().getFirst();assertEquals(GeneralPosition.Stage.INVENTORY,p.stage);assertEquals(3,p.quantity);
        assertEquals(1,p.productionSoldUnits);assertEquals(120.0,p.verifiedProceeds);assertFalse(p.settlementPending);assertFalse(p.cancelRequested);
    }
}
