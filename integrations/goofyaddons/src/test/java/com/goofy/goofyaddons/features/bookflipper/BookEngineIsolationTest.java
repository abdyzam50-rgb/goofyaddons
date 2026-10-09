package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol;
import com.goofy.goofyaddons.features.profit.ExecutionLedger;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class BookEngineIsolationTest {
    private static final Book BOOK=new Book("ENCHANTMENT_ULTIMATE_WISDOM",1,5,"Wisdom",0,0);
    private static class Store implements BookOrderRepository {
        List<BookPosition> data=List.of();int reads,writes,backups;
        boolean failReconcile,failExposed;
        public List<BookPosition> read() {reads++;return data;}
        public void backupVerified(List<BookPosition> expected) {assertEquals(data,expected);backups++;}
        public void reconcileVerified(List<BookPosition> expected,Set<String> present) throws Exception {
            assertEquals(data,expected);
            if(failReconcile)throw new java.io.IOException("Injected reconciliation failure");
            data=data.stream().filter(p->present.contains(p.book().id())).toList();
        }
        public void write(List<BookPosition> positions) {writes++;data=List.copyOf(positions);}
        public void writeTracked(List<BookPosition> positions,Set<String> exposed) throws Exception {
            if(failExposed&&!exposed.isEmpty())throw new java.io.IOException("Injected intent checkpoint failure");
            write(positions.stream().filter(p->exposed.contains(p.book().id())).toList());
        }
    }
    private static class Accounting implements BookAccounting {
        int effects;
        public void acquire(String id,String engine,String item,String event,int units,Double cost) {effects++;}
        public void sell(String id,String engine,String item,String event,int units,Double proceeds) {effects++;}
        public void recoverHoldings(String id,String engine,String item,int units) {effects++;}
        public void retire(String id) {effects++;}
        public void writeOff(String id,String engine,String item,String event,int units) {effects++;}
        public Double knownCost(String id,int units) {return null;}
        public Double openCost(String id) {return null;}
        public void beginExecution(String id,String engine,String input,String output,int units,int batch,long started,Double expectedProfit,ExecutionLedger.Forecast forecast) {effects++;}
    }
    private static class Services implements BookServices {
        final CapitalManager capital=new CapitalManager();final Accounting accounting=new Accounting();
        final FakeWorld world;int fetches;String pauseReason;
        Services(FakeWorld world) {this.world=world;capital.configure(1_000_000,0);}
        public BookSettings settings() {return new BookSettings(List.of(BOOK),1.25,0,1_000_000,2,"ec","ec 2",false,false,900,180,21600,15,3,60);}
        public CapitalManager capital() {return capital;}
        public BookAccounting accounting() {return accounting;}
        public double purse() {return 100000;}
        public int actionDelay() {return 50;}
        public JsonObject latestQuotes() {
            return JsonParser.parseString("{\"lastUpdated\":"+world.now()+",\"products\":{\"ENCHANTMENT_ULTIMATE_WISDOM_1\":{\"sell_summary\":[{\"pricePerUnit\":100}],\"buy_summary\":[{\"pricePerUnit\":110}],\"quick_status\":{\"buyMovingWeek\":100000,\"sellMovingWeek\":100000}},\"ENCHANTMENT_ULTIMATE_WISDOM_5\":{\"buy_summary\":[{\"pricePerUnit\":5000}],\"sell_summary\":[{\"pricePerUnit\":4000}]}}}").getAsJsonObject();
        }
        public CompletableFuture<JsonObject> fetchQuotes() {fetches++;return new CompletableFuture<>();}
        public Map<String,Integer> observedSkills() {return Map.of("enchanting",60);}
        public Set<String> excludedProducts() {return Set.of();}
        public MarketAnalysisProtocol.Report automaticReport() {return null;}
        public ExecutionLedger.Forecast executionForecast(String input,String output,int batch) {return null;}
        public void invalidateMarketReport() {}
        public void safetyPause(String reason) {pauseReason=reason;}
    }
    private static MenuSnapshot menu(int page) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        slots.set(8,SlotView.named(8,"Loaded",List.of()));slots.set(35,SlotView.named(35,"Loaded",List.of()));
        return new MenuSnapshot(page,page==0?null:page<3?"Ender Chest ("+page+"/3)":"Your Bazaar Orders",true,slots);
    }
    private static void scan(BazaarFlipper engine,FakeWorld world) {
        for(int page=0;page<4;page++) {
            world.showing(menu(page));engine.onTick();world.advance(1501);engine.onTick();world.advance(100);
        }
    }

    @Test void constructionAndStartupNeedNoGlobalConfigOrLiveEventRegistration() {
        var previous=GoofyConfig.INSTANCE;
        try {
            GoofyConfig.INSTANCE=null;
            var world=new FakeWorld().showing(menu(0));var actions=new RecordingActions();
            var store=new Store();var services=new Services(world);
            var engine=new BazaarFlipper(world,actions,store,services);
            assertEquals(0,store.reads);assertEquals(0,store.writes);assertEquals(0,services.fetches);
            engine.onTick();assertEquals(List.of(),actions.performed());
            engine.start();engine.onTick();world.advance(100);engine.onTick();
            assertTrue(engine.isRunning());assertEquals(1,store.reads);assertEquals(1,services.fetches);
            assertEquals(0,services.accounting.effects);assertEquals(List.of(),actions.serverEffects());
        } finally {GoofyConfig.INSTANCE=previous;}
    }

    @Test void invalidBatchCannotPartiallyAdoptOwnershipOrAffectAnotherEngine() {
        var world=new FakeWorld().showing(menu(0));var good=new Store();var bad=new Store();
        good.data=List.of(new BookPosition(BOOK,1600));
        bad.data=List.of(new BookPosition(BOOK,1600),new BookPosition(BOOK,-1));
        var firstServices=new Services(world);var secondServices=new Services(world);
        var first=new BazaarFlipper(world,new RecordingActions(),good,firstServices);
        var second=new BazaarFlipper(world,new RecordingActions(),bad,secondServices);
        assertTrue(first.restoreBudget());assertFalse(second.restoreBudget());
        assertEquals(1600,firstServices.capital.committed());assertEquals(0,secondServices.capital.committed());
        assertTrue(first.recoveryPending());assertEquals(0,bad.writes);assertEquals(2,bad.data.size());
    }

    @Test void fullyObservedEmptyStorageAndOrdersRetireStaleRecordsWithoutProfitOrItemClicks() {
        var world=new FakeWorld().showing(menu(0));var actions=new RecordingActions();
        var store=new Store();store.data=List.of(new BookPosition(BOOK,1600));
        var services=new Services(world);var engine=new BazaarFlipper(world,actions,store,services);
        engine.start();scan(engine,world);
        assertFalse(engine.recoveryPending());assertEquals(List.of(),store.data);
        assertEquals(1,store.backups);assertEquals(0,services.capital.committed());
        assertEquals(0,services.accounting.effects);assertEquals(List.of(),actions.serverEffects());
    }

    @Test void reconciliationFailureKeepsTheOriginalRecordsAndReservedCapital() {
        var world=new FakeWorld().showing(menu(0));var actions=new RecordingActions();
        var store=new Store();store.data=List.of(new BookPosition(BOOK,1600));store.failReconcile=true;
        var original=store.data;var services=new Services(world);
        var engine=new BazaarFlipper(world,actions,store,services);engine.start();scan(engine,world);
        assertEquals(original,store.data);assertEquals(0,store.writes);
        assertEquals(1600,services.capital.committed());assertNotNull(services.pauseReason);
        assertEquals(0,services.accounting.effects);assertEquals(List.of(),actions.serverEffects());
    }

    private static void field(BazaarFlipper engine,String name,Object value) throws Exception {
        var field=BazaarFlipper.class.getDeclaredField(name);field.setAccessible(true);field.set(engine,value);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void setupConfirmation(BazaarFlipper engine,FakeWorld world,Services services) throws Exception {
        var task=new Task(BOOK,false,false,"confirmed-trade");task.setReservedUnitCost(100);
        task.setBookState(Task.BookState.SELECTED);
        field(engine,"taskList",new ArrayList<>(List.of(task)));field(engine,"activeTask",task);
        var placement=BazaarFlipper.class.getDeclaredField("placement");placement.setAccessible(true);
        ((BookPlacement)placement.get(engine)).priceSelected(task,100.0,false,world.now());
        var state=BazaarFlipper.class.getDeclaredField("state");state.setAccessible(true);
        state.set(engine,Enum.valueOf((Class)state.getType(),"BAZAAR_NAVIGATION"));
        services.capital.reserve("books",BOOK.id(),1600,services.purse());
        var base=menu(3);var slots=new ArrayList<>(base.slots());
        slots.set(13,SlotView.named(13,"Wisdom I",List.of("Item: Wisdom I","Amount: 16","Unit price: 100 coins")));
        world.showing(new MenuSnapshot(4,"Confirm Buy Order",true,slots));
    }

    @Test void failedIntentCheckpointPreventsTheConfirmationClickAndRetainsTheTask() throws Exception {
        var world=new FakeWorld().showing(menu(0));var store=new Store();var services=new Services(world);
        var actions=new RecordingActions();var engine=new BazaarFlipper(world,actions,store,services);
        engine.start();setupConfirmation(engine,world,services);store.failExposed=true;
        for(int i=0;i<30&&services.pauseReason==null;i++){engine.onTick();world.advance(100);}
        assertNotNull(services.pauseReason);assertTrue(services.pauseReason.contains("journal"));
        assertTrue(engine.hasRetainedTasks());assertEquals(1600,services.capital.committed());
        assertEquals(0,services.accounting.effects);assertEquals(List.of(),actions.serverEffects());
    }

    @Test void successfulConfirmationPersistsOwnershipBeforeItsSingleClick() throws Exception {
        var world=new FakeWorld().showing(menu(0));var store=new Store();var services=new Services(world);
        var recorded=new RecordingActions();
        var actions=new GameActions() {
            @Override public void click(int slot,boolean shift) {
                if(slot==13)assertTrue(store.data.stream().anyMatch(p->p.tradeId().equals("confirmed-trade")));
                recorded.click(slot,shift);
            }
            public void closeMenu(){recorded.closeMenu();}
            public void command(String text){recorded.command(text);}
            public void message(String text){recorded.message(text);}
            public boolean writeSign(String text){return recorded.writeSign(text);}
        };
        var engine=new BazaarFlipper(world,actions,store,services);engine.start();setupConfirmation(engine,world,services);
        for(int i=0;i<30&&recorded.serverEffects().isEmpty()&&services.pauseReason==null;i++){engine.onTick();world.advance(100);}
        assertNull(services.pauseReason);assertEquals(List.of("click:13"),recorded.serverEffects());
        assertEquals(0,services.accounting.effects);assertTrue(engine.hasRetainedTasks());
    }
}
