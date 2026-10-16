package com.goofy.goofyaddons.features.bookflipper;

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

/** A player-started book flip test: one configured book, its own buy and sell modes, no profit filter at entry. */
class BookFlipTestTest {
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
    static class Services implements BookServices {
        double exitAsk=500;
        final CapitalManager capital=new CapitalManager();final Accounting accounting=new Accounting();
        final FakeWorld world;int fetches;String pauseReason;
        Services(FakeWorld world) {this.world=world;capital.configure(1_000_000,0);}
        public BookSettings settings() {return new BookSettings(List.of(BOOK),1.25,0,1_000_000,2,"ec","ec 2",false,false,900,180,21600,15,3,60);}
        public CapitalManager capital() {return capital;}
        public BookAccounting accounting() {return accounting;}
        public double purse() {return 100000;}
        public int actionDelay() {return 50;}
        public JsonObject latestQuotes() {
            return JsonParser.parseString("{\"lastUpdated\":"+world.now()+",\"products\":{\"ENCHANTMENT_ULTIMATE_WISDOM_1\":{\"sell_summary\":[{\"pricePerUnit\":100,\"amount\":100000,\"orders\":3}],\"buy_summary\":[{\"pricePerUnit\":110,\"amount\":100000,\"orders\":3}],\"quick_status\":{\"buyMovingWeek\":100000,\"sellMovingWeek\":100000}},\"ENCHANTMENT_ULTIMATE_WISDOM_5\":{\"buy_summary\":[{\"pricePerUnit\":"+exitAsk+",\"amount\":1000,\"orders\":3}],\"sell_summary\":[{\"pricePerUnit\":"+(exitAsk-100)+",\"amount\":1000,\"orders\":3}]}}}").getAsJsonObject();
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

    @SuppressWarnings("unchecked")
    static <T> T get(BazaarFlipper engine,String name)throws Exception{var f=BazaarFlipper.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(engine);}

    @Test void anUnprofitableBookTestCreatesOnlyItsOwnTask() throws Exception {
        var world=new FakeWorld().showing(menu(0));var actions=new RecordingActions();
        var services=new Services(world);var engine=new BazaarFlipper(world,actions,new Store(),services);
        // 16 Wisdom I cost about 1,600 coins and Wisdom V sells for 500: a normal flip never starts.
        assertNull(engine.startTest("ultimate_wisdom",false,true));
        assertTrue(engine.isRunning());assertFalse(engine.testFinished());assertTrue(engine.testLabel().contains("→"));
        for(int i=0;i<6;i++){engine.onTick();world.advance(100);}
        List<Task> tasks=get(engine,"taskList");
        assertEquals(1,tasks.size(),"only the test book is worked");
        assertTrue(tasks.getFirst().test());assertFalse(tasks.getFirst().instaBuy);assertTrue(tasks.getFirst().instaSell);
        assertTrue(services.capital.owns("books",BOOK.id()));
        assertFalse(engine.testFinished());
        tasks.clear();
        assertTrue(engine.testFinished(),"the test ends once its task leaves the engine");
        engine.endTest();assertFalse(engine.testFinished());
    }

    @Test void aTestBuySkipsTheProfitTargetButNotItsPriceCeiling() throws Exception {
        var world=new FakeWorld().showing(menu(0));var services=new Services(world);
        var engine=new BazaarFlipper(world,new RecordingActions(),new Store(),services);
        var check=BazaarFlipper.class.getDeclaredMethod("bookPriceAllowed",Task.class,double.class,boolean.class);check.setAccessible(true);
        var normal=new Task(BOOK,false,false);normal.setReservedUnitCost(100);
        var test=new Task(BOOK,false,false);test.setReservedUnitCost(100);test.markTest();
        assertFalse((boolean)check.invoke(engine,normal,100.1,false),"a normal flip refuses an entry that loses money");
        assertTrue((boolean)check.invoke(engine,test,104.9,false));
        assertFalse((boolean)check.invoke(engine,test,106.0,false),"more than 5% above the quote it started from");
        assertTrue((boolean)check.invoke(engine,test,400.0,true),"held books may always exit");
    }

    @Test void refusalsNameTheConfiguredBooksAndChangeNothing() throws Exception {
        var world=new FakeWorld().showing(menu(0));var services=new Services(world);
        var engine=new BazaarFlipper(world,new RecordingActions(),new Store(),services);
        var refused=engine.startTest("sharpness",true,true);
        assertTrue(refused.contains("configured: Wisdom"),refused);
        assertFalse(engine.isRunning());assertEquals(0,services.capital.committed());
        var store=new Store();store.data=List.of(new BookPosition(BOOK,1600));
        var held=new BazaarFlipper(world,new RecordingActions(),store,new Services(world));
        assertTrue(held.startTest("ultimate_wisdom",true,true).contains("recovered"));
    }
}
