package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A paused craft portfolio may keep unrelated ingredient orders while an output offer settles. */
class ProductionMixedOrderRecoveryTest {
    @TempDir Path dir;
    private static GeneralPosition sale() {
        var p=new GeneralPosition();p.item=new GeneralItem("SELL_OUTPUT","Crafted Output");p.quantity=6;p.unitCost=110;p.sellPrice=110;
        p.minimumSellPrice=100;p.productionParent="parent-sale";p.tradeId="sale-child";p.saleEvent="sale-receipt";
        p.stage=GeneralPosition.Stage.SELL_ORDER;p.submitted=true;p.checkedAt=321;return p;
    }
    private static GeneralPosition buy() {
        var p=new GeneralPosition();p.item=new GeneralItem("BUY_INPUT","Ingredient Input");p.quantity=2;p.unitCost=100;
        p.productionParent="parent-buy";p.tradeId="buy-child";p.stage=GeneralPosition.Stage.BUY_ORDER;
        p.productionBuy=true;p.productionBasket=true;p.purchasePriceKnown=true;p.maximumBuyPrice=103;p.submitted=true;p.checkedAt=456;return p;
    }
    private static ConfiguredTestServices services() {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        return new ConfiguredTestServices(){public boolean productionOnly(){return true;}public double purse(){return 100000;}};
    }
    private static MenuSnapshot menu(String title,List<SlotView> buttons) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        buttons.forEach(slot->slots.set(slot.index(),slot));return new MenuSnapshot(42,title,true,slots);
    }
    private static MenuSnapshot orders(boolean sale,boolean buy,int sold) {
        var buttons=new ArrayList<SlotView>();buttons.add(SlotView.named(49,"Close",List.of()));
        if(sale)buttons.add(SlotView.named(11,"SELL Crafted Output",List.of("Offer amount: 6","Filled: "+sold+"/6",
            sold>0?"You have coins to claim!":"Click to view options!")));
        if(buy)buttons.add(SlotView.named(12,"BUY Ingredient Input",List.of("Order amount: 2","Filled: 0/2","Click to view options!")));
        return menu("Your Bazaar Orders",buttons);
    }
    private static void drive(GeneralFlipper engine,FakeWorld world,long millis) {
        for(long elapsed=0;elapsed<millis;elapsed+=60){world.advance(60);engine.poll();engine.onTick();}
    }

    @Test void intactMixedPortfolioReturnsOnlySubmittedSellParentsForRestoration() {
        var sale=sale();var buy=buy();
        assertEquals(List.of(new ProductionBazaarOrders.SavedSale("parent-sale","SELL_OUTPUT",6)),
            ProductionBazaarOrders.resumableSales(List.of(buy,sale)));
        buy.cancelRequested=true;assertTrue(ProductionBazaarOrders.resumableSales(List.of(buy,sale)).isEmpty());
        buy.cancelRequested=false;buy.settlementPending=true;
        assertTrue(ProductionBazaarOrders.resumableSales(List.of(buy,sale)).isEmpty());
        buy.settlementPending=false;buy.productionParent=null;
        assertTrue(ProductionBazaarOrders.resumableSales(List.of(buy,sale)).isEmpty(),"Unknown parent ownership cannot join automatic restoration");
    }

    @Test void restoredSaleClaimsItsReceiptWithoutAdvancingOrAcknowledgingIngredientSibling() throws Exception {
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("sale-resume.json"));repository.save(List.of(buy(),sale()));
        var world=new FakeWorld().showing(orders(true,true,6));var actions=new RecordingActions();var svc=services();
        var engine=new GeneralFlipper(world,actions,repository,svc);assertTrue(engine.resumeProductionSales());
        assertEquals("parent-sale",engine.productionMenuParent());drive(engine,world,1200);
        assertTrue(actions.serverEffects().contains("click:11"));assertFalse(actions.serverEffects().contains("click:12"));
        engine.onNotice("[Bazaar] Claimed 600 coins from selling 6x Crafted Output at 100 each!");
        world.showing(orders(false,true,0));drive(engine,world,1600);
        var result=ProductionBazaarOrders.basketResult(engine.productionPositions().stream().filter(p->"parent-sale".equals(p.productionParent)).toList(),false,"Verified sale");
        assertTrue(result.done(),engine.diagnosticState().toString());assertEquals(6,result.units());assertEquals(600.0,result.proceeds());
        assertTrue(engine.consumeProduction("parent-sale"));
        var remaining=repository.load().getFirst();assertEquals("parent-buy",remaining.productionParent);assertFalse(remaining.cancelRequested);
        assertEquals(456,remaining.checkedAt);assertEquals(2,remaining.quantity);assertEquals(100,remaining.unitCost);
        assertTrue(svc.capital().occupied("BUY_INPUT"));assertFalse(engine.needsMenu(),"A restored sale cannot take a lease for its orphaned ingredient sibling");
    }

    @Test void explicitBuyRecoveryVerifiesRefundAndLeavesSubmittedSellOfferIntact() throws Exception {
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("buy-recovery.json"));repository.save(List.of(sale(),buy()));
        var world=new FakeWorld().showing(orders(true,true,0));var actions=new RecordingActions();var svc=services();
        var engine=new GeneralFlipper(world,actions,repository,svc);assertTrue(engine.recoverProductionBuy());
        assertEquals("parent-buy",engine.productionPosition().productionParent);assertEquals("parent-buy",engine.productionMenuParent());
        assertTrue(engine.productionRecoveryRemaining());drive(engine,world,1200);
        assertTrue(actions.serverEffects().contains("click:12"));assertFalse(actions.serverEffects().contains("click:11"));
        world.showing(menu("Buy Order Options",List.of(SlotView.named(13,"Cancel Order",List.of()))));drive(engine,world,120);
        engine.onNotice("[Bazaar] Cancelled! Refunded 200 coins from cancelling Buy Order!");
        world.showing(orders(true,false,0));drive(engine,world,1600);
        assertTrue(engine.productionPosition().completed,engine.diagnosticState().toString());assertTrue(engine.consumeProduction());
        assertFalse(engine.productionRecoveryRemaining());assertNull(engine.productionPosition());assertFalse(engine.needsMenu());
        var remaining=repository.load().getFirst();assertEquals("parent-sale",remaining.productionParent);assertEquals(6,remaining.quantity);
        assertEquals(321,remaining.checkedAt);assertTrue(remaining.submitted);assertFalse(remaining.cancelRequested);assertFalse(remaining.completed);
        assertNull(remaining.verifiedProceeds);assertTrue(svc.capital().occupied("SELL_OUTPUT"));
        assertTrue(engine.resumeProductionSales());assertEquals("parent-sale",engine.productionMenuParent());
    }

    @Test void unresolvedSellCancellationOrSettlementBlocksMixedRecoveryWithoutMutatingBuy() throws Exception {
        for(int variant=0;variant<3;variant++) {
            var sale=sale();if(variant==0)sale.cancelRequested=true;else if(variant==1)sale.settlementPending=true;else sale.completed=true;
            var journal=dir.resolve("unsafe-"+variant+".json");
            var repository=new JsonGeneralOrderRepository(()->journal);repository.save(List.of(sale,buy()));
            var actions=new RecordingActions();var engine=new GeneralFlipper(new FakeWorld().showing(orders(true,true,0)),actions,repository,services());
            assertFalse(engine.resumeProductionSales());assertFalse(engine.recoverProductionBuy());
            assertFalse(repository.load().get(1).cancelRequested);assertTrue(actions.serverEffects().isEmpty());
        }
    }
}
