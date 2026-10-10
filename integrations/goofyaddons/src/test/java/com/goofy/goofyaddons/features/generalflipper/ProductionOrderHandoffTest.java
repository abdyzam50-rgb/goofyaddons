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
}
