package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Regression evidence for selling one crafted batch through multiple repriced offers. */
class ProductionSellRepriceTest {
    @TempDir Path dir;

    private static GeneralPosition sale(int remaining) {
        var p=new GeneralPosition();p.item=new GeneralItem("OUTPUT","Output");p.quantity=remaining;p.unitCost=100;
        p.sellPrice=110;p.minimumSellPrice=101;p.stage=GeneralPosition.Stage.SELL_ORDER;p.submitted=true;
        p.productionParent="parent-repriced";p.tradeId="sale-trade";p.saleEvent="first-offer";
        return p;
    }

    @Test void repricingFloorProtectsWholeCraftProfitAfterTaxAndPartialProceeds() {
        double floor=ProductionSaleAccounting.minimumPrice(900,100,10,null,false,1,90);
        assertEquals(1000/(10*.99),floor,0.000001);
        assertEquals(700/(7*.99),ProductionSaleAccounting.minimumPrice(900,100,7,300.0,false,1,90),0.000001);
        assertEquals(110,ProductionSaleAccounting.minimumPrice(900,100,7,300.0,false,1,110));
        assertEquals(Double.POSITIVE_INFINITY,ProductionSaleAccounting.minimumPrice(900,100,7,null,true,1,90));
        assertEquals(Double.POSITIVE_INFINITY,ProductionSaleAccounting.minimumPrice(900,100,7,300.0,false,100,90));
    }
    @Test void partialClaimAndFinalClaimHandOffAllOriginalUnitsAndProceeds() {
        var p=sale(10);
        ProductionSaleAccounting.record(p,3,300.0);
        p.quantity=7;p.saleEvent="replacement-offer";
        var waiting=ProductionBazaarOrders.basketResult(List.of(p),false,"Replacement offer waiting");
        assertFalse(waiting.done());assertEquals(10,waiting.units());assertEquals(300.0,waiting.proceeds());
        ProductionSaleAccounting.record(p,7,630.0);p.completed=true;
        var complete=ProductionBazaarOrders.basketResult(List.of(p),false,"All sales verified");
        assertTrue(complete.done());assertEquals(10,complete.units());assertEquals(930.0,complete.proceeds());
        assertEquals(0,complete.cost());
    }

    @Test void repeatingOneReceiptDoesNotCountItsCoinsOrUnitsTwice() {
        var p=sale(10);
        ProductionSaleAccounting.record(p,3,300.0);
        ProductionSaleAccounting.record(p,3,300.0);
        assertEquals(3,p.productionSoldUnits);assertEquals(300.0,p.verifiedProceeds);
        p.quantity=7;p.saleEvent="replacement-offer";
        ProductionSaleAccounting.record(p,7,630.0);
        ProductionSaleAccounting.record(p,7,630.0);
        assertEquals(10,p.productionSoldUnits);assertEquals(930.0,p.verifiedProceeds);
    }

    @Test void unknownPartialReceiptCannotBeMaskedByLaterConfirmedCoins() {
        var p=sale(10);
        ProductionSaleAccounting.record(p,3,null);
        p.quantity=7;p.saleEvent="replacement-offer";
        ProductionSaleAccounting.record(p,7,630.0);p.completed=true;
        var result=ProductionBazaarOrders.basketResult(List.of(p),false,"Final receipt verified");
        assertTrue(result.done());assertEquals(10,result.units());assertNull(result.proceeds());
        assertTrue(p.productionProceedsUnknown);
    }

    @Test void restartAfterPartialRelistKeepsOriginalQuantityAndSaleFloor() throws Exception {
        var path=dir.resolve("repriced.json");var repository=new JsonGeneralOrderRepository(()->path);
        var p=sale(10);ProductionSaleAccounting.record(p,3,300.0);
        p.quantity=7;p.saleEvent="replacement-offer";p.reprices=1;repository.save(List.of(p));
        var restored=new JsonGeneralOrderRepository(()->path).load().getFirst();
        assertEquals(7,restored.quantity);assertEquals(3,restored.productionSoldUnits);
        assertEquals(300.0,restored.verifiedProceeds);assertEquals(101,restored.minimumSellPrice);
        assertEquals("first-offer",restored.productionSaleEvent);assertEquals("replacement-offer",restored.saleEvent);
        assertEquals(List.of(new ProductionBazaarOrders.SavedSale("parent-repriced","OUTPUT",10)),
            ProductionBazaarOrders.resumableSales(List.of(restored)));
        ProductionSaleAccounting.record(restored,7,630.0);restored.completed=true;
        var result=ProductionBazaarOrders.basketResult(List.of(restored),false,"All sales verified");
        assertEquals(10,result.units());assertEquals(930.0,result.proceeds());
    }

    @Test void unknownReceiptStatusSurvivesRestartAndFurtherSale() throws Exception {
        var path=dir.resolve("unknown.json");var repository=new JsonGeneralOrderRepository(()->path);
        var p=sale(10);ProductionSaleAccounting.record(p,3,null);
        p.quantity=7;p.saleEvent="replacement-offer";repository.save(List.of(p));
        var restored=new JsonGeneralOrderRepository(()->path).load().getFirst();
        assertTrue(restored.productionProceedsUnknown);assertNull(restored.verifiedProceeds);
        ProductionSaleAccounting.record(restored,7,630.0);
        assertEquals(10,restored.productionSoldUnits);assertNull(restored.verifiedProceeds);
    }

    @Test void legacyCompletedOfferKeepsItsVerifiedQuantityAndProceeds() {
        var p=sale(10);p.completed=true;p.verifiedProceeds=1000.0;
        var result=ProductionBazaarOrders.basketResult(List.of(p),false,"Legacy completed sale");
        assertTrue(result.done());assertEquals(10,result.units());assertEquals(1000.0,result.proceeds());
    }

    @Test void completedReceiptCanResumeAlongsideLiveOfferAfterParentHandoffWasInterrupted() {
        var completed=sale(10);completed.completed=true;completed.verifiedProceeds=1000.0;completed.settlementPending=true;
        var waiting=sale(7);waiting.item=new GeneralItem("OTHER","Other");waiting.productionParent="parent-waiting";
        assertEquals(List.of(new ProductionBazaarOrders.SavedSale("parent-repriced","OUTPUT",10),
            new ProductionBazaarOrders.SavedSale("parent-waiting","OTHER",7)),
            ProductionBazaarOrders.resumableSales(List.of(completed,waiting)));
        completed.verifiedProceeds=null;assertTrue(ProductionBazaarOrders.resumableSales(List.of(completed,waiting)).isEmpty());
        completed.verifiedProceeds=1000.0;completed.submitted=false;
        assertTrue(ProductionBazaarOrders.resumableSales(List.of(completed,waiting)).isEmpty(),"Unsold output recovery is not a sale receipt");
    }

    @Test void legacyCompletedReceiptWithOldSettlementFlagDoesNotPauseWaitingSiblings() throws Exception {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("completed.json"));
        var completed=sale(10);completed.completed=true;completed.verifiedProceeds=1000.0;completed.settlementPending=true;
        repository.save(List.of(completed));
        var engine=new GeneralFlipper(new FakeWorld().showing(orders(0,0,0,false)),new RecordingActions(),repository,
            new ConfiguredTestServices(){public boolean productionOnly(){return true;}public double purse(){return 100000;}});
        engine.start();engine.poll();engine.onTick();
        assertFalse(engine.productionFailed());assertTrue(engine.consumeProduction("parent-repriced"));
    }

    @Test void offerClaimedByHandWhileStoppedSettlesFromItsReceipt() throws Exception {
        var engine=stoppedSale("[Bazaar] Claimed 653.4 coins from selling 6x Output at 110 each!");
        assertFalse(engine.productionFailed(),engine.diagnosticState().toString());
        var result=ProductionBazaarOrders.basketResult(engine.productionPositions(),false,"Verified");
        assertTrue(result.done());assertEquals(6,result.units());assertEquals(653.4,result.proceeds());
    }

    @Test void receiptForAnotherPriceOrQuantityStillNeedsReview() throws Exception {
        assertTrue(stoppedSale("[Bazaar] Claimed 594 coins from selling 6x Output at 100 each!").productionFailed());
        assertTrue(stoppedSale("[Bazaar] Claimed 544.5 coins from selling 5x Output at 110 each!").productionFailed());
        assertTrue(stoppedSale("[Bazaar] Claimed 653.4 coins from selling 6x Other at 110 each!").productionFailed());
    }

    @Test void receiptFromBeforeTheOfferWasPlacedIsIgnored() throws Exception {
        assertTrue(stoppedSale("[Bazaar] Claimed 653.4 coins from selling 6x Output at 110 each!",120000).productionFailed());
    }

    private GeneralFlipper stoppedSale(String receipt) throws Exception { return stoppedSale(receipt,0); }
    /** A restored six-unit offer whose receipt arrived while stopped; the orders menu no longer lists it. */
    private GeneralFlipper stoppedSale(String receipt,long receiptAge) throws Exception {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        var world=new FakeWorld().showing(orders(0,0,0,false));
        var path=dir.resolve(java.util.UUID.randomUUID()+".json");var repository=new JsonGeneralOrderRepository(()->path);
        var offer=sale(6);offer.placedAt=world.clock()-60000;repository.save(List.of(offer));
        var engine=new GeneralFlipper(world,new RecordingActions(),repository,
            new ConfiguredTestServices(){public boolean productionOnly(){return true;}public double purse(){return 100000;}});
        world.advance(-receiptAge);engine.onNotice(receipt);world.advance(receiptAge);
        world.advance(30000);
        engine.start();drive(engine,world,12000);
        return engine;
    }

    @Test void unknownBasisAllowsOnlyOriginalInitialListing() {
        var p=sale(10);p.submitted=false;
        assertTrue(ProductionSaleAccounting.initialListing(p));
        p.submitted=true;assertFalse(ProductionSaleAccounting.initialListing(p));
        p.submitted=false;p.reprices=1;assertFalse(ProductionSaleAccounting.initialListing(p));
        p.reprices=0;p.productionSoldUnits=3;assertFalse(ProductionSaleAccounting.initialListing(p));
    }

    @Test void engineCancelsPartialOfferRelistsOnlyUnsoldStockAndSettlesAllReceipts() throws Exception {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        GoofyConfig.INSTANCE.general.items=List.of();GoofyConfig.INSTANCE.general.maxReprices=0;
        var world=new FakeWorld().showing(orders(10,3,0,false));var actions=new RecordingActions();
        var path=dir.resolve("engine.json");var repository=new JsonGeneralOrderRepository(()->path);
        var initial=sale(10);initial.placedAt=world.clock()-61000;repository.save(List.of(initial));
        var services=new ConfiguredTestServices(){
            public boolean productionOnly(){return true;}
            public double purse(){return 100000;}
            public com.google.gson.JsonObject latestQuotes(){return JsonParser.parseString(
                "{\"lastUpdated\":"+world.clock()+",\"products\":{\"OUTPUT\":{\"buy_summary\":[{\"pricePerUnit\":105}],\"sell_summary\":[{\"pricePerUnit\":100}]}}}").getAsJsonObject();}
        };
        var engine=new GeneralFlipper(world,actions,repository,services);engine.start();
        drive(engine,world,1200);
        assertTrue(actions.serverEffects().contains("click:11"),"An undercut owned offer should claim its partial coins first");
        engine.onNotice("[Bazaar] Claimed 300 coins from selling 3x Output at 100 each!");
        world.showing(orders(10,3,0,true));drive(engine,world,900);
        world.showing(menu("Sell Order Options",0,List.of(SlotView.named(13,"Cancel Order",List.of()))));drive(engine,world,120);
        assertTrue(repository.load().getFirst().cancelRequested,"Cancellation intent must be durable before its click");
        engine.onNotice("[Bazaar] Cancelled sell order for 10x Output!");
        world.showing(orders(0,0,7,false));drive(engine,world,1000);
        var partial=repository.load().getFirst();
        assertEquals(7,partial.quantity);assertEquals(3,partial.productionSoldUnits);assertEquals(300.0,partial.verifiedProceeds);
        assertFalse(partial.settlementPending);assertFalse(partial.completed);
        drive(engine,world,3000); // The next inventory visit verifies the emptied orders menu before entering a replacement.

        world.showing(menu("Output product",7,List.of(
            new SlotView(13,false,13,false,"Output","Output",List.of(),"OUTPUT",null,1,64),
            SlotView.named(15,"Create Sell Offer",List.of("Output")))));drive(engine,world,1200);
        world.showing(menu("At what price are you selling?",7,List.of(
            SlotView.named(12,"Best offer",List.of("Unit price: 105 coins")))));drive(engine,world,180);
        world.showing(menu("Confirm Sell Offer",7,List.of(
            SlotView.named(13,"Output",List.of("Item: Output","Amount: 7","Unit price: 105 coins")))));drive(engine,world,900);
        var replacement=repository.load().getFirst();assertTrue(replacement.submitted);
        assertNotEquals("first-offer",replacement.saleEvent);
        world.showing(orders(7,7,0,false));drive(engine,world,4000);
        world.advance(61000);drive(engine,world,1200);
        engine.onNotice("[Bazaar] Claimed 630 coins from selling 7x Output at 90 each!");
        world.showing(orders(0,0,0,false));drive(engine,world,1000);
        var complete=ProductionBazaarOrders.basketResult(engine.productionPositions(),false,"Verified");
        assertTrue(complete.done(),engine.diagnosticState().toString()+" actions="+actions.performed());assertEquals(10,complete.units());assertEquals(930.0,complete.proceeds());
        assertFalse(repository.load().getFirst().settlementPending,"Verified completion clears the saved pending-claim flag");
        assertTrue(engine.consumeProduction("parent-repriced"));assertTrue(repository.load().isEmpty());
    }

    private static MenuSnapshot menu(String title,int inventory,List<SlotView> buttons) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<27;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(27+i,true,i));
        if(inventory>0)slots.set(27,new SlotView(27,true,0,false,"Output","Output",List.of(),"OUTPUT",null,inventory,64));
        buttons.forEach(button->slots.set(button.index(),button));return new MenuSnapshot(42,title,true,slots);
    }
    private static MenuSnapshot orders(int total,int filled,int inventory,boolean options) {
        var buttons=new ArrayList<SlotView>();buttons.add(SlotView.named(26,"Close",List.of()));
        if(total>0)buttons.add(SlotView.named(11,"SELL Output",List.of("Offer amount: "+total,"Filled: "+filled+"/"+total,
            options?"Click to view options!":"You have coins to claim!")));
        return menu("Your Bazaar Orders",inventory,buttons);
    }
    private static void drive(GeneralFlipper engine,FakeWorld world,long millis) {
        for(long elapsed=0;elapsed<millis;elapsed+=60){world.advance(60);engine.poll();engine.onTick();}
    }
}
