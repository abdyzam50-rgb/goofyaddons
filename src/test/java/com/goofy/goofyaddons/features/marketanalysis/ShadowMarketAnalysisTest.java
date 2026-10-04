package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ShadowMarketAnalysisTest {
    private static final class Env implements ShadowMarketAnalysis.Environment {
        GoofyConfig cfg=MarketAnalysisProtocolTest.config();long clock=MarketAnalysisProtocolTest.NOW;
        JsonObject quotes=MarketAnalysisProtocolTest.market(clock);
        int quoteRefreshes;
        public void refreshQuotes(){quoteRefreshes++;}
        CapitalManager capital=new CapitalManager();
        Env() {capital.configure(10000,0);}
        public GoofyConfig config(){return cfg;}public long now(){return clock;}public JsonObject quotes(){return quotes;}
        public double purse(){return 10000;}public CapitalManager capital(){return capital;}
        public void onClientThread(Runnable work){work.run();}
        public MenuSnapshot inventory(){return new MenuSnapshot(0,null,true,java.util.stream.IntStream.range(0,36).mapToObj(i->SlotView.empty(i,true,i)).toList());}
    }
    private static final class Bridge implements MarketAnalysisClient.Transport {
        JsonObject packet;int requests;CompletableFuture<JsonObject> reply=new CompletableFuture<>();
        public CompletableFuture<JsonObject> request(String endpoint,JsonObject body){packet=body;requests++;return reply;}
        void complete(long now){var r=MarketAnalysisProtocolTest.response(now);r.addProperty("requestId",packet.get("requestId").getAsString());reply.complete(r);}
    }
    @Test void automaticHeadReplansAfterReservationAndExpiresWithForecast() {
        var env=new Env();env.cfg.marketAnalysis.automaticSelection=true;env.cfg.general.items=List.of();env.cfg.books=List.of();
        var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});
        observer.poll(TradingMode.BOTH);bridge.complete(env.clock);
        assertNotNull(observer.automaticHeadReport());
        assertEquals("ENCHANTED_COAL",observer.automaticHeadReport().rows().getFirst().inputId());
        assertTrue(env.capital.reserve("general","ENCHANTED_COAL",1000,10000));
        assertNull(observer.automaticHeadReport());
        env.capital.release("general","ENCHANTED_COAL");env.clock+=61000;
        assertNull(observer.automaticHeadReport());
    }
    @Test void disabledDoesNothingAndShadowRepliesDoNotChangeCapitalOrWatchlists() {
        var env=new Env();var bridge=new Bridge();List<String> events=new ArrayList<>();
        var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->events.add(type));
        env.cfg.marketAnalysis.enabled=false;observer.poll(TradingMode.BOTH);assertEquals(0,bridge.requests);
        env.cfg.marketAnalysis.enabled=true;var books=List.copyOf(env.cfg.books);var items=List.copyOf(env.cfg.general.items);
        observer.poll(TradingMode.BOTH);bridge.complete(env.clock);
        assertEquals("READY",observer.diagnosticState().get("status"));assertEquals(0,env.capital.committed());
        assertEquals(books,env.cfg.books);assertEquals(items,env.cfg.general.items);
        assertEquals(false,observer.diagnosticState().get("executionAuthority"));assertTrue(events.contains("market.shadow_recommendations"));
    }
    @Test void requestIsThrottledAndUnavailableCompanionDoesNotTouchTradingState() {
        var env=new Env();var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});
        observer.poll(TradingMode.BOTH);for(int i=0;i<100;i++)observer.poll(TradingMode.BOTH);assertEquals(1,bridge.requests);
        bridge.reply.completeExceptionally(new IllegalStateException("offline"));
        assertEquals("UNAVAILABLE",observer.diagnosticState().get("status"));assertEquals(0,env.capital.committed());
        observer.poll(TradingMode.BOTH);assertEquals(1,bridge.requests);
        env.clock+=19999;observer.poll(TradingMode.BOTH);assertEquals(1,bridge.requests);
        env.clock++;bridge.reply=new CompletableFuture<>();observer.poll(TradingMode.BOTH);assertEquals(2,bridge.requests);
    }
    @Test void stopAndConfigurationReloadDiscardInFlightReplies() {
        var env=new Env();var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});
        observer.poll(TradingMode.BOTH);observer.stop();assertTrue(bridge.reply.isCancelled());assertFalse(observer.diagnosticState().containsKey("report"));
        bridge.reply=new CompletableFuture<>();observer.poll(TradingMode.BOTH);env.cfg=MarketAnalysisProtocolTest.config();bridge.complete(env.clock);
        assertEquals("DISCARDED",observer.diagnosticState().get("status"));assertFalse(observer.diagnosticState().containsKey("report"));
    }
    @Test void staleReportsDisappearAndOccupiedRoutesAndBudgetArePassedToTheCalculator() {
        var env=new Env();env.capital.restore("general","SOME_ITEM",2500,false);
        var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});observer.poll(TradingMode.BOTH);
        var c=bridge.packet.getAsJsonObject("constraints");assertEquals(7500,c.get("availableCapital").getAsDouble());
        assertTrue(c.getAsJsonArray("excludedProducts").toString().contains("SOME_ITEM"));assertEquals(2,c.get("generalSlots").getAsInt());
        bridge.complete(env.clock);env.clock+=61000;assertEquals("STALE",observer.diagnosticState().get("status"));
        assertFalse(observer.diagnosticState().containsKey("report"));
    }
    @Test void malformedResponseAndFailingDiagnosticSinkCannotEscapeToTheScheduler() {
        var env=new Env();var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{throw new IllegalStateException("diagnostics unavailable");});
        assertDoesNotThrow(()->observer.poll(TradingMode.BOTH));assertDoesNotThrow(()->bridge.reply.complete(new JsonObject()));
        assertEquals("UNAVAILABLE",observer.diagnosticState().get("status"));assertEquals(0,env.capital.committed());
    }
    @Test void missingQuotesTriggerSharedRefreshInsteadOfWaitingForever() {
        var env=new Env();env.quotes=null;var bridge=new Bridge();
        var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});
        observer.poll(TradingMode.BOTH);assertEquals(1,env.quoteRefreshes);assertEquals(0,bridge.requests);
        observer.poll(TradingMode.BOTH);assertEquals(1,env.quoteRefreshes);
        env.clock+=20000;env.quotes=MarketAnalysisProtocolTest.market(env.clock);
        observer.poll(TradingMode.BOTH);assertEquals(1,bridge.requests);
    }

    @Test void pipelineRevalidatesAReportWhenTheExecutorReservesItsProduct() {
        var env=new Env();env.cfg.general.items=List.of(new com.goofy.goofyaddons.features.generalflipper.GeneralItem("ENCHANTED_COAL","Enchanted Coal"));
        var bridge=new Bridge();var observer=new ShadowMarketAnalysis(env,bridge,(type,data)->{});
        observer.poll(TradingMode.BOTH);bridge.complete(env.clock);
        var plan=(PipelinePlanner.Plan)observer.diagnosticState().get("pipeline");assertEquals(1,plan.next().size());
        assertEquals(0,env.capital.committed());assertTrue(env.capital.reserve("general","ENCHANTED_COAL",100,10000));
        observer.poll(TradingMode.BOTH);plan=(PipelinePlanner.Plan)observer.diagnosticState().get("pipeline");
        assertTrue(plan.next().isEmpty());assertEquals(1,bridge.requests);assertEquals(100,env.capital.committed());
    }
}
