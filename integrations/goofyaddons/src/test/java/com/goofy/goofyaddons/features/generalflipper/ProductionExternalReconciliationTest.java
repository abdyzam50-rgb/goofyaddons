package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** External handling is an explicit ownership handoff, never a synthetic cancellation or sale. */
class ProductionExternalReconciliationTest {
    private static final String CHILD="6acfc294-058f-46d7-b00a-6bbf153e4b9d",PREFIX="6acfc294";
    private static final String PRODUCT="ENCHANTED_MYCELIUM_CUBE",NAME="Enchanted Mycelium Cube";
    @TempDir Path dir;
    @BeforeEach void configure(){GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;}
    private static GeneralPosition child(){
        var p=new GeneralPosition();p.item=new GeneralItem(PRODUCT,NAME);p.tradeId=CHILD;
        p.productionParent="e5a941cd-e5ff-4fb7-8ddb-4c84b7da56aa";p.quantity=1;p.unitCost=133475.5;
        p.sellPrice=133475.4;p.maximumBuyPrice=137479.662;p.stage=GeneralPosition.Stage.BUY_ORDER;
        p.productionBuy=true;p.productionBasket=true;p.purchasePriceKnown=true;p.submitted=true;p.cancelRequested=true;
        p.placedAt=999_000_000;return p;
    }
    private static GeneralPosition fuming(){
        var p=new GeneralPosition();p.item=new GeneralItem("FUMING_POTATO_BOOK","Fuming Potato Book");
        p.tradeId="5836a6a7-55de-4b8c-b948-598f6df77d8e";p.quantity=20;p.unitCost=1200798.8;
        p.sellPrice=1346705.6;p.stage=GeneralPosition.Stage.BUY_ORDER;p.purchasePriceKnown=true;p.submitted=true;p.checkedAt=456;return p;
    }
    private static MenuSnapshot menu(int container,long observation,boolean cursor,int stock,List<SlotView> controls){
        var slots=new ArrayList<SlotView>();for(int i=0;i<54;i++)slots.add(SlotView.empty(i,false,i));
        for(int i=0;i<36;i++)slots.add(SlotView.empty(54+i,true,i));
        if(stock>0)slots.set(54,new SlotView(54,true,0,false,NAME,NAME,List.of(),PRODUCT,null,stock,64));
        controls.forEach(v->slots.set(v.index(),v));
        return new MenuSnapshot(container,"Co-op Bazaar Orders",cursor,slots,cursor?SlotView.empty(-1,false,-1):null,observation);
    }
    private static MenuSnapshot emptyOrders(int container,long observation){return menu(container,observation,true,0,List.of(SlotView.named(49,"Close",List.of())));}
    private static SlotView order(boolean sell,String owner){return SlotView.named(11,(sell?"SELL ":"BUY ")+NAME,
        List.of((sell?"Offer":"Order")+" amount: 1","Filled: 0/1","Price per unit: 133475.5 coins","By: [MVP+] "+owner,"Click to view options!"));}
    private static final class Services extends ConfiguredTestServices {
        String scope="Papaya (uuid-curedmc)";int acquisitions,sales,finished;
        public boolean productionOnly(){return true;}public String accountScope(){return scope;}public double purse(){return 100000000;}
        public void acquire(GeneralPosition p){acquisitions++;}public void sell(GeneralPosition p,int units,Double proceeds){sales++;}
        public void finished(GeneralPosition p){finished++;}
    }
    private final class Fixture {
        final FakeWorld world=new FakeWorld().username("curedmc").showingNothing();
        final RecordingActions actions=new RecordingActions();final Services services=new Services();
        final Path path;final JsonGeneralOrderRepository disk;final GeneralFlipper engine;boolean failWrite;
        Fixture(String name,List<GeneralPosition> positions)throws Exception{
            path=dir.resolve(name);disk=new JsonGeneralOrderRepository(()->path);disk.save(positions);
            var repo=new GeneralOrderRepository(){
                public List<GeneralPosition> load()throws Exception{return disk.load();}
                public void save(List<GeneralPosition> positions)throws Exception{
                    if(failWrite)throw new java.io.IOException("simulated durable write failure");disk.save(positions);
                }
            };
            engine=new GeneralFlipper(world,actions,repo,services);engine.restoreBudget();
        }
        void begin(){engine.beginExternalReconciliation(PREFIX);assertTrue(engine.inspectExternal(true).pending());}
        ProductionBazaarOrders.ExternalInspection settle(MenuSnapshot fresh){
            world.showing(fresh);assertTrue(engine.inspectExternal(true).pending());world.advance(800);return engine.inspectExternal(true);
        }
    }

    @Test void immutableOriginalEvidencePrecedesRemovalAndOrdinaryFundingIsPreserved()throws Exception{
        var f=new Fixture("exact.json",List.of(fuming(),child()));var before=Files.readString(f.path);
        f.engine.beginExternalReconciliation(PREFIX);
        assertTrue(f.engine.inspectExternal(false).pending());assertTrue(f.actions.serverEffects().isEmpty(),"No menu action without its lease");
        assertTrue(f.engine.inspectExternal(true).pending());
        var result=f.settle(emptyOrders(12,10));assertFalse(result.failed());assertFalse(result.pending());var evidence=result.evidence();
        assertEquals(CHILD,evidence.child());assertEquals("e5a941cd-e5ff-4fb7-8ddb-4c84b7da56aa",evidence.parent());
        assertEquals(PRODUCT,evidence.product());assertEquals("curedmc",evidence.account());assertEquals(1,evidence.units());
        assertEquals(133475.5,evidence.unitCost());assertEquals(133475.5,evidence.cost());assertNull(evidence.confirmedCancelRefund());
        assertEquals(before,Files.readString(f.path),"Preparation never edits ownership before the review archive is durable");
        assertEquals(24149451.5,f.services.capital().committed());
        assertTrue(f.engine.finishExternalReconciliation(evidence));assertFalse(f.engine.finishExternalReconciliation(evidence));
        var remaining=f.disk.load();assertEquals(1,remaining.size());var ordinary=remaining.getFirst();
        assertEquals("FUMING_POTATO_BOOK",ordinary.item.id());assertEquals(20,ordinary.quantity);assertEquals(456,ordinary.checkedAt);
        assertTrue(ordinary.submitted);assertFalse(ordinary.cancelRequested);assertEquals(24015976.0,f.services.capital().committed());
        assertFalse(f.services.capital().occupied(PRODUCT));assertTrue(f.services.capital().occupied("FUMING_POTATO_BOOK"));
        assertEquals(0,f.services.acquisitions);assertEquals(0,f.services.sales);assertEquals(0,f.services.finished);
        assertEquals(List.of("command:managebazaarorders"),f.actions.serverEffects());
    }
    @Test void staleContainersAndUnsettledServerObservationsCannotAuthorizeTheHandoff()throws Exception{
        var f=new Fixture("stale.json",List.of(child()));f.world.showing(emptyOrders(11,100));f.begin();
        f.world.showing(emptyOrders(11,101));f.world.advance(900);assertTrue(f.engine.inspectExternal(true).pending());
        f.world.showing(emptyOrders(12,100));assertTrue(f.engine.inspectExternal(true).pending(),"New ID with old server evidence is not fresh");
        f.world.showing(emptyOrders(12,102));assertTrue(f.engine.inspectExternal(true).pending());f.world.advance(700);
        f.world.showing(emptyOrders(12,103));assertTrue(f.engine.inspectExternal(true).pending());f.world.advance(700);
        assertTrue(f.engine.inspectExternal(true).pending());f.world.advance(60);
        assertNotNull(f.engine.inspectExternal(true).evidence());assertEquals(1,f.disk.load().size());
    }
    @Test void liveMenusWithoutPacketCountersRequireANewStableCompleteContainer()throws Exception{
        var f=new Fixture("live-seam.json",List.of(child()));f.world.showing(emptyOrders(11,-1));f.begin();
        f.world.advance(900);assertTrue(f.engine.inspectExternal(true).pending(),"The old container cannot prove fresh absence");
        f.world.showing(emptyOrders(12,-1));assertTrue(f.engine.inspectExternal(true).pending());f.world.advance(700);
        f.world.showing(menu(12,-1,true,0,List.of(SlotView.named(49,"Close",List.of("newly loaded control")))));
        assertTrue(f.engine.inspectExternal(true).pending(),"Even without a packet counter, changed contents reset settlement");
        f.world.advance(760);var evidence=f.engine.inspectExternal(true).evidence();assertNotNull(evidence);
        assertEquals(-1,evidence.serverObservation());assertTrue(f.engine.finishExternalReconciliation(evidence));
    }
    @Test void ownedBuySellUnreadableCoopCursorInventoryAndPaginationRefuseWithoutMutation()throws Exception{
        for(int variant=0;variant<6;variant++){
            var f=new Fixture("unsafe-"+variant+".json",List.of(fuming(),child()));f.begin();
            var controls=new ArrayList<SlotView>();controls.add(SlotView.named(49,"Close",List.of()));
            if(variant==0)controls.add(order(false,"curedmc"));if(variant==1)controls.add(order(true,"curedmc"));
            if(variant==2)controls.add(SlotView.named(11,"BUY "+NAME,List.of("Order amount: 1","Filled: 0/1")));
            if(variant==5)controls.add(SlotView.named(44,"Next Page",List.of()));
            var check=f.settle(menu(12,10,variant!=3,variant==4?1:0,controls));assertTrue(check.failed(),"variant "+variant);
            assertNull(check.evidence());assertEquals(2,f.disk.load().size());assertEquals(24149451.5,f.services.capital().committed());
            assertEquals(List.of("command:managebazaarorders"),f.actions.serverEffects());
        }
    }
    @Test void foreignCoopOrderCanRemainButItsOwnershipMustBeReadable()throws Exception{
        var f=new Fixture("foreign.json",List.of(child()));f.begin();
        var check=f.settle(menu(12,10,true,0,List.of(order(false,"OtherPlayer"),SlotView.named(49,"Close",List.of()))));
        assertNotNull(check.evidence());assertTrue(f.engine.finishExternalReconciliation(check.evidence()));
        assertEquals(List.of("command:managebazaarorders"),f.actions.serverEffects(),"No foreign co-op order is clicked");
    }
    @Test void exactUniquePrefixKnownIngredientOwnershipAndStoppedStateAreRequired()throws Exception{
        var f=new Fixture("guards.json",List.of(fuming(),child()));
        assertThrows(IllegalArgumentException.class,()->f.engine.beginExternalReconciliation("6acfc29"));
        assertThrows(IllegalArgumentException.class,()->f.engine.beginExternalReconciliation("parent-no-match"));
        assertThrows(IllegalStateException.class,()->f.engine.beginExternalReconciliation("5836a6a7"));
        f.engine.start();assertThrows(IllegalStateException.class,()->f.engine.beginExternalReconciliation(PREFIX));f.engine.stop();
        f.services.scope=null;assertThrows(IllegalStateException.class,()->f.engine.beginExternalReconciliation(PREFIX));
        assertEquals(2,f.disk.load().size());
        var a=child();var b=child();b.item=new GeneralItem("OTHER_INPUT","Other Input");b.tradeId="6acfc294-ffffffff";
        var ambiguous=new Fixture("ambiguous.json",List.of(a,b));
        assertThrows(IllegalArgumentException.class,()->ambiguous.engine.beginExternalReconciliation(PREFIX));
        for(int variant=0;variant<4;variant++){
            var p=child();if(variant==0)p.purchasePriceKnown=false;if(variant==1)p.settlementPending=true;
            if(variant==2)p.cancelRequested=false;if(variant==3)p.submitted=false;
            var unsafe=new Fixture("identity-"+variant+".json",List.of(p));
            assertThrows(IllegalStateException.class,()->unsafe.engine.beginExternalReconciliation(PREFIX));
        }
    }
    @Test void captureOrAccountChangingAfterTheReviewWasSavedRetainsTheOriginalOrder()throws Exception{
        for(int variant=0;variant<3;variant++){
            var f=new Fixture("changed-"+variant+".json",List.of(child()));f.begin();var evidence=f.settle(emptyOrders(12,10)).evidence();
            assertNotNull(evidence);
            if(variant==0)f.services.scope="Mango (uuid-curedmc)";
            if(variant==1)f.world.showing(emptyOrders(12,11));
            if(variant==2)f.world.showing(menu(12,10,true,1,List.of(SlotView.named(49,"Close",List.of()))));
            assertFalse(f.engine.finishExternalReconciliation(evidence));assertEquals(1,f.disk.load().size());
            assertTrue(f.services.capital().occupied(PRODUCT));
        }
    }
    @Test void failedJournalRemovalRollsBackOwnershipAndNeverReleasesCapital()throws Exception{
        var f=new Fixture("rollback.json",List.of(fuming(),child()));f.begin();var evidence=f.settle(emptyOrders(12,10)).evidence();
        f.failWrite=true;assertFalse(f.engine.finishExternalReconciliation(evidence));
        assertEquals(2,f.disk.load().size());assertTrue(f.engine.hasStateError());assertEquals(24149451.5,f.services.capital().committed());
        assertTrue(f.services.capital().occupied(PRODUCT));assertEquals(0,f.services.sales);assertEquals(0,f.services.acquisitions);
        var retained=(List<?>)f.engine.diagnosticState().get("positions");assertEquals(2,retained.size());
    }
    @Test void navigationTimesOutAndStoppingCancelsOnlyTheTransientInspector()throws Exception{
        var f=new Fixture("timeout.json",List.of(child()));f.begin();
        for(int i=0;i<2;i++){f.world.advance(5001);assertTrue(f.engine.inspectExternal(true).pending());}
        f.world.advance(5001);assertTrue(f.engine.inspectExternal(true).failed());assertEquals(1,f.disk.load().size());
        assertEquals(3,f.actions.serverEffects().stream().filter("command:managebazaarorders"::equals).count());
        f.engine.beginExternalReconciliation(PREFIX);f.engine.stop();assertTrue(f.engine.inspectExternal(true).failed());
        assertTrue(f.disk.load().getFirst().cancelRequested);assertTrue(f.services.capital().occupied(PRODUCT));
    }
}
