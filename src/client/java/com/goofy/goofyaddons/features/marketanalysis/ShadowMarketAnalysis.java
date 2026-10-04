package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.features.generalflipper.GeneralCalculator;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Observes recommendations beside the legacy selectors. Deliberately has no GameActions dependency. */
public final class ShadowMarketAnalysis {
    public interface Environment {
        GoofyConfig config(); long now(); JsonObject quotes(); MenuSnapshot inventory(); double purse();
        CapitalManager capital(); void onClientThread(Runnable work);
        default void refreshQuotes() {}
        default Set<String> excludedProducts() { return Set.of(); }
    }
    private final Environment env;
    private final MarketAnalysisClient.Transport transport;
    private final BiConsumer<String,Map<String,?>> events;
    private CompletableFuture<JsonObject> pending;
    private MarketAnalysisProtocol.Report report;
    private Map<String,Object> comparison=Map.of();
    private PipelinePlanner.Plan pipeline;
    private long nextPoll;
    private int generation;
    private GoofyConfig previousConfig;
    private TradingMode mode;
    private String endpoint, status="DISABLED", lastError;

    public ShadowMarketAnalysis() {
        this(new Environment() {
            private final GameWorld world=new LiveWorld();
            private boolean refreshing;
            @Override public GoofyConfig config() { return GoofyConfig.INSTANCE; }
            @Override public Set<String> excludedProducts() { return com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().excluded(); }
            @Override public long now() { return world.now(); }
            @Override public JsonObject quotes() { return BazaarApi.latestFresh(); }
            @Override public void refreshQuotes() {
                if(refreshing)return;refreshing=true;
                try { BazaarApi.fetch().whenComplete((quotes,error)->world.onClientThread(()->refreshing=false)); }
                catch(RuntimeException error) { refreshing=false;throw error; }
            }
            @Override public MenuSnapshot inventory() { return world.menu(); }
            @Override public double purse() { return new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse(); }
            @Override public CapitalManager capital() { return CapitalManager.INSTANCE; }
            @Override public void onClientThread(Runnable work) { world.onClientThread(work); }
        }, new MarketAnalysisClient()::request, (type,data)->Diagnostics.event(type.endsWith("unavailable")?"WARN":"INFO",type,data));
    }
    public ShadowMarketAnalysis(Environment env, MarketAnalysisClient.Transport transport, BiConsumer<String,Map<String,?>> events) {
        this.env=env;this.transport=transport;this.events=events;
    }

    public void poll(TradingMode currentMode) {
        // Analysis failures are isolated from the scheduler's safety-pause boundary.
        try { pollObserved(currentMode); }
        catch(RuntimeException failure) { unavailable(failure); }
    }
    private void pollObserved(TradingMode currentMode) {
        GoofyConfig cfg=env.config();
        if(cfg==null || !cfg.marketAnalysis.enabled) {
            if(!status.equals("DISABLED")) stop(); status="DISABLED"; return;
        }
        if(previousConfig!=cfg || mode!=currentMode || !Objects.equals(endpoint,cfg.marketAnalysis.endpoint)) {
            stop(); previousConfig=cfg;mode=currentMode;endpoint=cfg.marketAnalysis.endpoint;nextPoll=0;
        }
        refreshPipeline();
        if(pending!=null || env.now()<nextPoll) return;
        nextPoll=env.now()+cfg.marketAnalysis.refreshSeconds*1000L;
        JsonObject market=env.quotes();
        if(market==null || !TradingSafety.fresh(market.get("lastUpdated").getAsLong(),env.now())) {status="WAITING_QUOTES";report=null;refreshPipeline();env.refreshQuotes();return;}
        PipelineAccount account=pipeline.account();
        if(!account.ready()){status="WAITING_ACCOUNT";report=null;refreshPipeline();return;}
        int capacity=account.inventoryCapacity();Set<String> excluded=account.excludedProducts();double available=account.available();
        JsonObject request=MarketAnalysisProtocol.request(UUID.randomUUID().toString(),market,cfg,mode,available,capacity,
                account.bookSlots(),account.generalSlots(),excluded);
        var context=new LinkedHashMap<String,Object>();
        context.put("mode",mode.name());context.put("availableCapital",available);context.put("inventoryCapacity",capacity);
        context.put("bookSlots",account.bookSlots());context.put("generalSlots",account.generalSlots());
        context.put("legacyTopBooks",mode==TradingMode.GENERAL?List.of():FlipCalculator.calculate(market.getAsJsonObject("products"),cfg.books,cfg.bazaarTaxPercentage,cfg.minNetProfit)
                .stream().limit(3).map(f->f.book().id()+":"+f.book().level()+":"+f.book().sellLevel()).toList());
        context.put("legacyTopGeneral",mode==TradingMode.BOOKS?List.of():GeneralCalculator.calculate(market.getAsJsonObject("products"),cfg.general,cfg.bazaarTaxPercentage,available,capacity)
                .stream().limit(3).map(f->f.item().id()).toList());
        context.put("excludedProducts",new TreeSet<>(excluded));
        comparison=Collections.unmodifiableMap(context);
        int run=generation;
        status="REQUESTING";
        pending=transport.request(endpoint,request);
        pending.whenComplete((response,error)->env.onClientThread(()->{
            if(run!=generation) return;
            pending=null;
            if(env.config()!=cfg || !cfg.marketAnalysis.enabled) {report=null;status="DISCARDED";return;}
            try {
                if(error!=null) throw new IllegalStateException("Local calculator unavailable",error);
                report=MarketAnalysisProtocol.parse(response,request,env.now());status="READY";lastError=null;refreshPipeline();
                var event=new LinkedHashMap<String,Object>(); event.put("mode","SHADOW");event.put("report",report);event.put("comparison",comparison);event.put("pipeline",pipeline);
                events.accept("market.shadow_recommendations",event);
            } catch(RuntimeException failure) {unavailable(failure);}
        }));
    }
    private void refreshPipeline() {
        var cfg=env.config();
        var account=PipelineAccount.capture(env.now(),mode,env.capital(),env.purse(),env.inventory(),cfg.maxActiveBooks,cfg.general.maxActiveItems,env.excludedProducts());
        pipeline=PipelinePlanner.build(account,report,env.now());
    }
    private void unavailable(RuntimeException failure) {
        report=null;pending=null;pipeline=null;status="UNAVAILABLE";
        Throwable detail=failure;
        while(detail.getCause()!=null && detail.getCause()!=detail)detail=detail.getCause();
        String message = detail.getMessage() == null ? detail.getClass().getSimpleName() : detail.getMessage();
        lastError=message.substring(0,Math.min(200,message.length()));
        try {events.accept("market.shadow_unavailable",Map.of("reason",message.substring(0,Math.min(200,message.length())),
                "error",failure.getClass().getSimpleName(),"mode","SHADOW"));}
        catch(RuntimeException ignored) { /* Diagnostic reporting must never affect trading. */ }
    }
    public void stop() {
        generation++;if(pending!=null) pending.cancel(true);pending=null;report=null;pipeline=null;comparison=Map.of();status="STOPPED";lastError=null;nextPoll=0;
    }
    public MarketAnalysisProtocol.Report latestReport() {
        return report!=null && env.config()==previousConfig && env.config().marketAnalysis.enabled
                && TradingSafety.fresh(report.marketAt(),env.now())?report:null;
    }
    public MarketAnalysisProtocol.Report automaticHeadReport() {
        if(!env.config().marketAnalysis.automaticSelection)return null;
        var fresh=latestReport();if(fresh==null)return null;
        refreshPipeline();
        if(pipeline.next().isEmpty())return null;
        return new MarketAnalysisProtocol.Report(fresh.marketAt(),fresh.dataAt(),fresh.generatedAt(),fresh.historyUsed(),
                fresh.historyStatus(),fresh.upstreamCommit(),fresh.total(),fresh.counts(),List.of(pipeline.next().getFirst().route()));
    }
    public Map<String,Object> diagnosticState() {
        var result=new LinkedHashMap<String,Object>();
        boolean enabled=env.config()!=null && env.config().marketAnalysis.enabled;
        boolean fresh=report!=null && TradingSafety.fresh(report.marketAt(),env.now());
        result.put("mode","SHADOW");result.put("enabled",enabled);result.put("status",!enabled?"DISABLED":report!=null&&!fresh?"STALE":status);
        result.put("automaticSelection",enabled && env.config().marketAnalysis.automaticSelection);
        if(lastError!=null)result.put("error",lastError);
        result.put("executionAuthority",false);result.put("comparison",comparison);
        if(fresh) result.put("report",report);
        if(pipeline!=null)result.put("pipeline",pipeline.status().equals("READY") && pipeline.expiresAt()<env.now()?
                PipelinePlanner.build(pipeline.account(),null,env.now()):pipeline);
        return Collections.unmodifiableMap(result);
    }
}
