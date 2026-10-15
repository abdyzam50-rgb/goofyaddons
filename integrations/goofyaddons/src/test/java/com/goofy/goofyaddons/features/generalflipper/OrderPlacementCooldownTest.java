package com.goofy.goofyaddons.features.generalflipper;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderPlacementCooldownTest {
    @TempDir Path dir;
    static void field(Object target,String name,Object value)throws Exception {
        var f=GeneralFlipper.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    @Test void rejectedBuyKeepsItsBudgetAndWaitsWithoutClaimingSuccess()throws Exception { rejected(false); }
    @Test void rejectedCraftSaleKeepsItsInventoryAndWaitsWithoutClaimingSuccess()throws Exception { rejected(true); }
    void rejected(boolean sale)throws Exception {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        var world=new FakeWorld();var actions=new RecordingActions();
        var services=new ConfiguredTestServices(){public boolean productionOnly(){return sale;}};
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var engine=new GeneralFlipper(world,actions,repo,services);engine.start();
        var p=new GeneralPosition();p.item=new GeneralItem("ENCHANTED_COAL","Enchanted Coal");
        p.quantity=16;p.unitCost=100;p.sellPrice=130;p.tradeId="cooldown-test";p.submitted=true;
        p.stage=sale?GeneralPosition.Stage.INVENTORY:GeneralPosition.Stage.PLANNED;p.placedAt=world.now();
        var trade=new GeneralTrade();trade.selling=sale;
        field(engine,"positions",new ArrayList<>(List.of(p)));field(engine,"active",p);
        field(engine,"trade",trade);field(engine,"step",GeneralFlipper.Step.VERIFY_ORDER);
        services.capital().reserve("general",p.item.id(),1600,100000);
        engine.onNotice("[Bazaar] Placing orders is on cooldown for up to 1 minute!");
        assertFalse(p.submitted);assertEquals(sale?GeneralPosition.Stage.INVENTORY:GeneralPosition.Stage.PLANNED,p.stage);
        assertFalse(engine.hasStateError());assertEquals("none",engine.diagnosticState().get("activeTrade"));
        assertEquals(1600,services.capital().committed());assertTrue(services.capital().orderCooldownRemaining(world.now())>=60000);
        assertEquals(1,repo.load().size());assertFalse(repo.load().getFirst().submitted);
        assertFalse(engine.needsMenu());
    }
}
