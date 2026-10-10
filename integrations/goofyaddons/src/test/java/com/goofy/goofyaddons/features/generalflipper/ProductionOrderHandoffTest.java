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
        assertFalse(new GeneralBuy().acceptPrice(ctx,104));assertFalse(engine.productionFailed());
        assertTrue(engine.productionPosition().completed);assertNotNull(engine.productionPosition().rejectedReason);
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
        verifiedCancellation(true,0);
    }
    @Test void outbidIngredientCancellationHandsBackVerifiedRefundInsteadOfPlacingAStandaloneFlip()throws Exception {
        verifiedCancellation(false,0);
    }
    @Test void decoratedGemstonePartialFillIsClaimedAndCancelledForItsParent()throws Exception {
        verifiedCancellation(false,35);
    }
    private void verifiedCancellation(boolean recovery,int filled)throws Exception {
        var services=services();
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var p=new GeneralPosition();p.item=new GeneralItem("FINE_AMBER_GEM","Fine Amber Gem");p.quantity=1280;p.unitCost=100;
        p.stage=GeneralPosition.Stage.BUY_ORDER;p.productionBuy=true;p.maximumBuyPrice=103;p.purchasePriceKnown=true;p.submitted=true;p.tradeId="retained";
        var world=new FakeWorld();p.placedAt=world.clock()-61_000;repo.save(List.of(p));
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        slots.set(49,SlotView.named(49,"Close",List.of()));
        slots.set(11,SlotView.named(11,"BUY ✧ Fine Amber Gemstone",List.of("Order amount: 1,280x","Filled: "+filled+"/1.3k (2.7%)",filled>0?"You have "+filled+" items to claim!":"Click to view options!")));
        // Describe a readable gemstone icon with its item identity and stack limit.
        var order=slots.get(11);slots.set(11,new SlotView(11,false,11,false,order.customName(),order.hoverName(),order.loreLines(),"FINE_AMBER_GEM",null,1,64));
        world.showing(new MenuSnapshot(42,"Your Bazaar Orders",true,slots));
        var actions=new RecordingActions();var engine=new GeneralFlipper(world,actions,repo,services);
        if(recovery)assertTrue(engine.recoverProductionBuy());
        else {
            engine.start();
            var products=GeneralFlipper.class.getDeclaredField("products");products.setAccessible(true);
            products.set(engine,com.google.gson.JsonParser.parseString("{\"FINE_AMBER_GEM\":{\"sell_summary\":[{\"pricePerUnit\":102}],\"buy_summary\":[{\"pricePerUnit\":200}]}}").getAsJsonObject());
            var quotesAt=GeneralFlipper.class.getDeclaredField("quotesAt");quotesAt.setAccessible(true);quotesAt.setLong(engine,world.clock());
        }
        drive(engine,world,1400);
        assertFalse(engine.productionFailed());assertTrue(actions.serverEffects().contains("click:11"),actions.performed().toString());
        if(filled>0) {
            var claimed=new ArrayList<>(slots);
            claimed.set(54,new SlotView(54,true,0,false,"✧ Fine Amber Gemstone","✧ Fine Amber Gemstone",List.of(),"FINE_AMBER_GEM",null,filled,64));
            claimed.set(11,new SlotView(11,false,11,false,"BUY ✧ Fine Amber Gemstone","BUY ✧ Fine Amber Gemstone",
                List.of("Order amount: 1,280x","Filled: 35/1.3k (2.7%)","Click to view options!"),"FINE_AMBER_GEM",null,1,64));
            world.showing(new MenuSnapshot(42,"Your Bazaar Orders",true,claimed));drive(engine,world,1400);
            assertFalse(engine.productionFailed());
            assertEquals(2,actions.serverEffects().stream().filter("click:11"::equals).count(),"Claim first, then open the same owned order's options");
        }
        var options=new ArrayList<>(slots);options.set(11,SlotView.empty(11,false,11));options.set(13,SlotView.named(13,"Cancel Order",List.of()));
        if(filled>0)options.set(54,new SlotView(54,true,0,false,"✧ Fine Amber Gemstone","✧ Fine Amber Gemstone",List.of(),"FINE_AMBER_GEM",null,filled,64));
        world.showing(new MenuSnapshot(43,"Buy Order Options",true,options));drive(engine,world,180);
        assertFalse(engine.productionPosition().completed);
        engine.onNotice("[Bazaar] Cancelled! Refunded "+String.format(java.util.Locale.ROOT,"%,d",(1280-filled)*100)+" coins from cancelling Buy Order!");
        var empty=new ArrayList<>(slots);empty.set(11,SlotView.empty(11,false,11));
        if(filled>0)empty.set(54,options.get(54));
        world.showing(new MenuSnapshot(44,"Your Bazaar Orders",true,empty));drive(engine,world,1800);
        assertFalse(engine.productionFailed());if(filled==0)assertTrue(engine.productionPosition().completed);
        else {assertEquals(GeneralPosition.Stage.INVENTORY,engine.productionPosition().stage);assertEquals(filled,engine.productionPosition().quantity);assertEquals(filled*100,engine.productionPosition().cost());}
        assertEquals(filled==0,repo.load().getFirst().completed,"Evidence stays durable until parent/manual-review handoff");
        assertEquals(0,engine.productionPosition().reprices,"No replacement before parent budget/profit check");
        assertTrue(engine.consumeProduction());assertTrue(repo.load().isEmpty());
    }
    @Test void ingredientOutbidWaitsForCooldownAndFreshDataButIgnoresStandaloneRepriceLimit()throws Exception {
        var services=services();GoofyConfig.INSTANCE.general.maxReprices=0;
        var world=new FakeWorld().showing(inventory("INPUT",0));
        var engine=new GeneralFlipper(world,new RecordingActions(),new JsonGeneralOrderRepository(()->dir.resolve("outbid.json")),services);
        engine.start();assertTrue(engine.enqueueProduction("parent-first","INPUT","Input",2,100,true,103));
        var p=engine.productionPosition();p.placedAt=world.clock();p.stage=GeneralPosition.Stage.BUY_ORDER;p.submitted=true;
        var active=GeneralFlipper.class.getDeclaredField("active");active.setAccessible(true);active.set(engine,p);
        var products=GeneralFlipper.class.getDeclaredField("products");products.setAccessible(true);
        products.set(engine,com.google.gson.JsonParser.parseString("{\"INPUT\":{\"sell_summary\":[{\"pricePerUnit\":102}]}}").getAsJsonObject());
        var quotesAt=GeneralFlipper.class.getDeclaredField("quotesAt");quotesAt.setAccessible(true);quotesAt.setLong(engine,world.clock());
        var field=GeneralFlipper.class.getDeclaredField("session");field.setAccessible(true);var ctx=(GeneralContext)field.get(engine);
        assertFalse(ctx.shouldReprice(false),"No rapid cancellation during the cooldown");
        world.advance(60_001);assertFalse(ctx.shouldReprice(false),"Stale quotes cannot trigger cancellation");
        quotesAt.setLong(engine,world.clock());assertTrue(ctx.shouldReprice(false));
        p.cancelRequested=true;assertFalse(ctx.shouldReprice(false),"Manual recovery is cancellation only");p.cancelRequested=false;
        assertFalse(ctx.shouldReprice(true),"Craft offers retain their sale-floor lifecycle");
        products.set(engine,com.google.gson.JsonParser.parseString("{\"INPUT\":{\"sell_summary\":[{\"pricePerUnit\":100}]}}").getAsJsonObject());
        assertFalse(ctx.shouldReprice(false),"An unchanged top bid is not an outbid");
    }

    private void drive(GeneralFlipper engine,FakeWorld world,long millis) {
        for(long n=0;n<millis;n+=60){engine.poll();engine.onTick();world.advance(60);}
    }

    @Test void multipleCraftParentsKeepSeparateDurableOrdersAndConsumeOnlyTheirOwnClaim()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var world=new FakeWorld().showing(inventory("INPUT_A",0));var actions=new RecordingActions();
        var engine=new GeneralFlipper(world,actions,repo,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT_A","Input A",2,100,true,103));
        assertTrue(engine.enqueueProduction("parent-second","INPUT_B","Input B",3,100,true,103));
        assertFalse(engine.enqueueProduction("parent-first","INPUT_C","Input C",1,100,true,103));
        assertFalse(engine.enqueueProduction("parent-third","INPUT_A","Input A",1,100,true,103));
        assertFalse(engine.enqueueProduction("bad!","INPUT_C","Input C",1,100,true,103));
        assertEquals(500,engine.productionCommitment("parent-first")+engine.productionCommitment("parent-second"));
        var saved=repo.load();for(var p:saved){p.stage=GeneralPosition.Stage.BUY_ORDER;p.submitted=true;}repo.save(saved);
        var restored=new GeneralFlipper(world,actions,repo,services());restored.restoreBudget();
        assertFalse(restored.orphanProductionOrders(Set.of("parent-first","parent-second")));
        assertTrue(restored.orphanProductionOrders(Set.of("parent-first")));
        var second=restored.productionPosition("parent-second");second.stage=GeneralPosition.Stage.INVENTORY;
        assertFalse(restored.consumeProduction("parent-first"));
        assertTrue(restored.consumeProduction("parent-second"));
        assertEquals(1,repo.load().size());assertEquals("parent-first",repo.load().getFirst().productionParent);
        assertNull(restored.productionPosition("parent-second"));assertNotNull(restored.productionPosition("parent-first"));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void aFailedSiblingCannotBeReportedAsTheOtherCraftsFailedClaim()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var engine=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),new RecordingActions(),repo,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT_A","Input A",2,100,true,103));
        assertTrue(engine.enqueueProduction("parent-second","INPUT_B","Input B",2,100,true,103));
        var active=GeneralFlipper.class.getDeclaredField("active");active.setAccessible(true);active.set(engine,engine.productionPosition("parent-second"));
        var field=GeneralFlipper.class.getDeclaredField("session");field.setAccessible(true);((GeneralContext)field.get(engine)).fail("Uncertain second claim");
        assertTrue(engine.productionFailed("parent-second"));assertFalse(engine.productionFailed("parent-first"));
        assertEquals("Uncertain second claim",engine.activity());
    }

    @Test void corruptDuplicateParentLinksAreRejectedAndLegacyOrdersRemainOrphans()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("orders.json"));
        var engine=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),new RecordingActions(),repo,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT_A","Input A",2,100,true,103));
        assertTrue(engine.enqueueProduction("parent-second","INPUT_B","Input B",2,100,true,103));
        var saved=repo.load();saved.get(1).productionParent="parent-first";
        assertThrows(IllegalArgumentException.class,()->GeneralPosition.validated(saved));
        var legacy=saved.getFirst();legacy.productionParent=null;
        assertDoesNotThrow(()->GeneralPosition.validated(List.of(legacy)));
        repo.save(List.of(legacy));
        var restored=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),new RecordingActions(),repo,services());
        assertTrue(restored.orphanProductionOrders(Set.of("parent-first")));
    }

    @Test void craftMenuHandoffClosesBeforeRequestingOrdersAndPreservesTheTrackedBuy()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("handoff.json"));
        var world=new FakeWorld().showing(new MenuSnapshot(40,"Craft Item",true,inventory("INPUT",0).slots()));
        var actions=new RecordingActions();var engine=new GeneralFlipper(world,actions,repo,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT","Input",2,100,true,103));
        drive(engine,world,2000);
        assertTrue(actions.performed().contains("close"));assertTrue(actions.serverEffects().isEmpty());assertFalse(engine.productionFailed());
        world.showingNothing();drive(engine,world,400);
        assertTrue(actions.serverEffects().contains("command:managebazaarorders"));
        assertNotNull(engine.productionPosition("parent-first"));assertFalse(engine.productionPosition("parent-first").submitted);
    }
    @Test void delegatedCraftTimeDoesNotExpireUnstartedNavigationButSubmittedVerificationStillTimesOut()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("yield.json"));
        var world=new FakeWorld().showingNothing();var engine=new GeneralFlipper(world,new RecordingActions(),repo,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT","Input",2,100,true,103));
        drive(engine,world,60);world.advance(40000);engine.yieldProductionMenu();engine.onTick();
        assertFalse(engine.productionFailed(),"A delegated craft may hold the menu longer than the navigation timeout");
        var step=GeneralFlipper.class.getDeclaredField("step");step.setAccessible(true);step.set(engine,GeneralFlipper.Step.VERIFY_ORDER);
        var since=GeneralFlipper.class.getDeclaredField("stepSince");since.setAccessible(true);since.setLong(engine,world.clock());
        engine.productionPosition("parent-first").submitted=true;world.advance(40000);engine.yieldProductionMenu();engine.onTick();
        assertTrue(engine.productionFailed("parent-first"),"Submitted order verification must retain its deadline");
        assertNotNull(repo.load().getFirst());
    }
    private MenuSnapshot outputRecoveryMenu(int units,boolean hasSellOrder) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        slots.set(49,SlotView.named(49,"Close",List.of()));
        if(units>0)slots.set(54,new SlotView(54,true,0,false,"Soulflow","Soulflow",List.of(),"SOULFLOW",null,units,64));
        if(hasSellOrder)slots.set(11,SlotView.named(11,"SELL Soulflow",List.of("Order amount: 10x","Filled: 0/10")));
        return new MenuSnapshot(42,"Your Bazaar Orders",true,slots);
    }
    private GeneralPosition unsubmittedOutput() {
        var p=new GeneralPosition();p.item=new GeneralItem("SOULFLOW","Soulflow");p.quantity=10;p.unitCost=26496.5;
        p.stage=GeneralPosition.Stage.INVENTORY;p.minimumSellPrice=25000;p.productionParent="parent-output";p.tradeId="output-position";
        return p;
    }
    @Test void mixedIngredientAndUnsubmittedOutputRecoveryVerifiesStockWithoutSellingIt()throws Exception {
        var repo=new JsonGeneralOrderRepository(()->dir.resolve("mixed.json"));var output=unsubmittedOutput();
        var input=new GeneralPosition();input.item=new GeneralItem("INPUT","Input");input.quantity=2;input.unitCost=100;
        input.stage=GeneralPosition.Stage.INVENTORY;input.productionBuy=true;input.purchasePriceKnown=true;input.maximumBuyPrice=103;
        input.productionParent="parent-input";input.tradeId="input-position";repo.save(List.of(input,output));
        var actions=new RecordingActions();var engine=new GeneralFlipper(new FakeWorld().showing(outputRecoveryMenu(10,false)),actions,repo,services());
        assertTrue(engine.recoverProductionBuy());assertTrue(engine.consumeProduction());
        var world=new FakeWorld().showing(outputRecoveryMenu(10,false));
        engine=new GeneralFlipper(world,actions,repo,services());assertTrue(engine.recoverProductionBuy());drive(engine,world,1600);
        assertFalse(engine.productionFailed());assertTrue(engine.productionPosition().completed);
        assertFalse(engine.productionPosition().submitted);assertNull(engine.productionPosition().verifiedProceeds);
        assertFalse(engine.productionPosition().purchasePriceKnown,"The sell quote is not promoted to an input cost");
        assertTrue(repo.load().getFirst().completed,"Verification stays durable until the controller archives recovery evidence");
        assertTrue(actions.serverEffects().isEmpty(),actions.performed().toString());
        assertTrue(engine.consumeProduction());assertTrue(repo.load().isEmpty());
    }
    @Test void outputRecoveryRefusesChangedInventoryOrAnExistingOrderWithoutClicking()throws Exception {
        for(boolean existing:List.of(false,true)) {
            var repo=new JsonGeneralOrderRepository(()->dir.resolve("mismatch-"+existing+".json"));repo.save(List.of(unsubmittedOutput()));
            var world=new FakeWorld().showing(outputRecoveryMenu(existing?10:9,existing));var actions=new RecordingActions();
            var engine=new GeneralFlipper(world,actions,repo,services());assertTrue(engine.recoverProductionBuy());drive(engine,world,1600);
            assertTrue(engine.productionFailed());assertFalse(repo.load().getFirst().completed);assertTrue(actions.serverEffects().isEmpty());
        }
    }
    @Test void submittedIngredientPriceFailureStillRequiresReconciliation()throws Exception {
        var engine=new GeneralFlipper(new FakeWorld().showing(inventory("INPUT",0)),new RecordingActions(),new JsonGeneralOrderRepository(()->dir.resolve("submitted-price.json")),services());engine.start();
        assertTrue(engine.enqueueProduction("parent-first","INPUT","Input",2,100,true,103));
        var p=engine.productionPosition();p.stage=GeneralPosition.Stage.BUY_ORDER;p.submitted=true;
        var active=GeneralFlipper.class.getDeclaredField("active");active.setAccessible(true);active.set(engine,p);
        var field=GeneralFlipper.class.getDeclaredField("session");field.setAccessible(true);
        assertFalse(new GeneralBuy().acceptPrice((GeneralContext)field.get(engine),104));
        assertTrue(engine.productionFailed("parent-first"));assertFalse(p.completed);assertNull(p.rejectedReason);
    }
}
