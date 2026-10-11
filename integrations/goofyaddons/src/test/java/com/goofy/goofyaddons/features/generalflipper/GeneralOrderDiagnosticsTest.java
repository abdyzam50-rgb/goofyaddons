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

class GeneralOrderDiagnosticsTest {
    private static final String ID="ENCHANTED_MYCELIUM_CUBE",NAME="Enchanted Mycelium Cube";
    @TempDir Path dir;
    @BeforeEach void configure(){GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;}

    private static GeneralPosition buy() {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=1;p.unitCost=133475.5;p.sellPrice=133475.4;
        p.stage=GeneralPosition.Stage.BUY_ORDER;p.tradeId="buy-child";p.productionParent="parent-buy";
        p.productionBuy=true;p.productionBasket=true;p.productionInventoryBefore=2;
        p.purchasePriceKnown=true;p.maximumBuyPrice=137479.662;p.submitted=true;p.cancelRequested=true;
        return p;
    }
    private static GeneralPosition sale() {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=4;p.unitCost=100;p.sellPrice=130;
        p.stage=GeneralPosition.Stage.SELL_ORDER;p.tradeId="sale-child";p.productionParent="parent-sale";
        p.saleEvent="sale-event";p.minimumSellPrice=100;p.purchasePriceKnown=true;p.submitted=true;p.placedAt=999900000;
        return p;
    }
    private static MenuSnapshot menu(String title,int container,int stock,List<SlotView> controls) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        if(stock>0)slots.set(54,new SlotView(54,true,0,false,NAME,NAME,List.of(),ID,null,stock,64));
        controls.forEach(s->slots.set(s.index(),s));return new MenuSnapshot(container,title,true,slots);
    }
    private record Event(String type,Map<String,?> data) {}
    private final class Fixture {
        final FakeWorld world=new FakeWorld().username("curedmc");
        final RecordingActions recorded=new RecordingActions();final List<Event> events=new ArrayList<>();
        final JsonGeneralOrderRepository repo;final GeneralFlipper engine;
        int stock=2,sold=-1,container=10;
        final GameActions actions=new GameActions() {
            public void click(int slot,boolean shift){recorded.click(slot,shift);}
            public void closeMenu(){recorded.closeMenu();world.showingNothing();}
            public void command(String text){recorded.command(text);if(text.equals("managebazaarorders"))showOrders();}
            public void message(String text){recorded.message(text);}
            public boolean writeSign(String text){return recorded.writeSign(text);}
        };
        Fixture(String name,GeneralPosition position)throws Exception {
            stock=position.productionBuy?position.productionInventoryBefore:0;
            repo=new JsonGeneralOrderRepository(()->dir.resolve(name));repo.save(List.of(position));
            var services=new ConfiguredTestServices() {
                public boolean productionOnly(){return true;}
                public double purse(){return 1000000;}
                public JsonObject latestQuotes(){return JsonParser.parseString("{\"lastUpdated\":"+world.clock()+",\"products\":{\""+ID+"\":{\"sell_summary\":[{\"pricePerUnit\":100}],\"buy_summary\":[{\"pricePerUnit\":120}]}}}").getAsJsonObject();}
                public void event(String level,String type,Map<String,?> data){events.add(new Event(type,data));}
                public void acquire(GeneralPosition p){fail("Diagnostics must not invent an acquisition");}
                public void sell(GeneralPosition p,int units,Double proceeds){fail("Diagnostics must not invent sale proceeds");}
            };
            engine=new GeneralFlipper(world,actions,repo,services);
        }
        void showOrders() {
            var controls=new ArrayList<SlotView>();controls.add(SlotView.named(49,"Close",List.of()));
            if(sold>=0)controls.add(SlotView.named(11,"SELL "+NAME,List.of("Offer amount: 4","Filled: "+sold+"/4",
                "Price per unit: 130 coins","By: [MVP+] curedmc","Click to view options!")));
            controls.add(SlotView.named(15,"SELL Fuming Potato Book",List.of("Offer amount: 20","Filled: 0/20","By: [MVP+] curedmc","Click to view options!")));
            world.showing(menu("Co-op Bazaar Orders",container++,stock,controls));
        }
        void tick(long milliseconds){for(long i=0;i<milliseconds;i+=60){engine.poll();engine.onTick();world.advance(60);}}
        void until(String step){for(int i=0;i<150&&!step.equals(engine.diagnosticState().get("step"));i++)tick(60);
            assertEquals(step,engine.diagnosticState().get("step"),engine.diagnosticState().toString());}
        Event event(String type){return events.stream().filter(e->e.type().equals(type)).reduce((a,b)->b).orElseThrow();}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Object value){return (List<Map<String,Object>>)value;}

    @Test void absentRecoveryExportsTheExactOrdersSnapshotBeforeEachReopenAndSafetyClose()throws Exception {
        var f=new Fixture("absent.json",buy());f.showOrders();assertTrue(f.engine.recoverProductionBuy());f.tick(14000);
        assertTrue((Boolean)f.engine.diagnosticState().get("paused"));assertNull(f.world.menu().title());
        for(var event:f.events.stream().filter(e->Set.of("order.observation_recheck","general.transaction_blocked").contains(e.type())).toList()) {
            var context=map(event.data().get("context"));var state=map(context.get("orderState"));var menu=map(context.get("orderMenu"));
            assertEquals("buy-child",state.get("activeTrade"));assertEquals(2,state.get("targetInventoryTotal"));
            assertEquals(0,state.get("targetAcquiredInventory"));assertEquals(-1,state.get("buyOrderSlot"));assertEquals(-1,state.get("sellOrderSlot"));
            assertFalse((Boolean)state.get("receipt"));assertEquals(0,state.get("cancelSoldUnits"));
            assertEquals("Co-op Bazaar Orders",menu.get("title"));assertTrue((Integer)menu.get("containerId")>0);
            assertTrue(rows(menu.get("slots")).stream().anyMatch(s->"SELL Fuming Potato Book".equals(s.get("name"))));
            var inventory=rows(menu.get("inventory")).getFirst();assertEquals(ID,inventory.get("productId"));assertEquals(2,inventory.get("count"));
            assertFalse((Boolean)rows(state.get("positions")).getFirst().get("hasConfirmedCancelRefund"));
        }
        assertEquals(3,f.events.stream().filter(e->e.type().equals("order.observation_recheck")).count());
        assertEquals("ORDERS",map(map(f.event("general.transaction_blocked").data().get("context")).get("orderState")).get("step"));
        var saved=f.repo.load().getFirst();assertTrue(saved.submitted);assertTrue(saved.cancelRequested);
        assertNull(saved.confirmedCancelRefund);assertEquals(133475.5,saved.cost());
    }

    @Test void retainedRefundEvidenceExportsItsPresenceAndExactValue()throws Exception {
        var p=buy();p.confirmedCancelRefund=133475.5;var f=new Fixture("refund.json",p);f.engine.restoreBudget();
        var row=rows(f.engine.diagnosticState().get("positions")).getFirst();
        assertEquals(133475.5,row.get("confirmedCancelRefund"));assertTrue((Boolean)row.get("hasConfirmedCancelRefund"));
        assertTrue(f.events.isEmpty());assertTrue(f.recorded.serverEffects().isEmpty());
    }

    @Test void changedSellFillFailurePreservesClaimedProceedsAndUnreturnedUnitsInThePreCloseEvidence()throws Exception {
        var f=new Fixture("partial-sale.json",sale());f.sold=1;f.showOrders();assertTrue(f.engine.resumeProductionSales());f.until("CANCEL_DETAIL");
        f.engine.onNotice("[Bazaar] Claimed 120 coins from selling 1x "+NAME+" at 120 each!");
        f.world.showing(menu("Sell Order Options",30,0,List.of(SlotView.named(13,"Cancel Order",List.of()))));f.tick(120);
        assertEquals("VERIFY_CANCEL",f.engine.diagnosticState().get("step"));
        f.engine.onNotice("[Bazaar] There was an error cancelling your order!");f.sold=2;f.showOrders();f.tick(4000);
        var context=map(f.event("general.transaction_blocked").data().get("context"));var state=map(context.get("orderState"));
        assertEquals("sale-child",state.get("activeTrade"));assertEquals(1,state.get("cancelSoldUnits"));
        assertEquals(120.0,state.get("claimedProceeds"));assertTrue((Boolean)state.get("hasClaimedProceeds"));
        assertEquals(3,state.get("expectedClaim"));assertFalse((Boolean)state.get("receipt"));assertEquals(0,state.get("targetInventoryTotal"));
        assertEquals("Co-op Bazaar Orders",map(context.get("orderMenu")).get("title"));assertNull(f.world.menu().title());
        var saved=f.repo.load().getFirst();assertTrue(saved.settlementPending);assertNull(saved.verifiedProceeds);assertEquals(4,saved.quantity);
    }
}
