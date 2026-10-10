package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Market discovery cannot click. The foreground production feature consumes locally verified plans. */
final class CraftFlipSelection {
    private static final HttpClient HTTP=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(Duration.ofSeconds(2));
    private JsonObject ah;
    private CompletableFuture<?> pending;
    private long nextRefresh,generatedAt,generation,lastMarketSource;
    private final CraftRankingRefresh rankingRefresh=new CraftRankingRefresh();
    private CompletableFuture<?> bazaarPending;
    private String scope,error="Waiting for craft market data";
    private List<CraftFlipPlanner.Route> routes=List.of();
    private final Map<String,Integer> excluded=new LinkedHashMap<>();
    List<CraftFlipPlanner.Route> routes(){return routes;}
    boolean calculated(){return generatedAt>0;}
    String emptyReason(){
        if(!excluded.isEmpty())return "Main exclusion: "+excluded.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return error==null?"Waiting for priced routes that meet minimum profit and market depth":error;
    }
    void clear(){generation++;if(pending!=null)pending.cancel(true);pending=null;ah=null;routes=List.of();excluded.clear();scope=null;nextRefresh=0;generatedAt=0;lastMarketSource=0;rankingRefresh.clear();bazaarPending=null;}
    void refresh() {
        var world=new LiveWorld();long now=world.now();
        if(!world.inWorld()){if(scope!=null)clear();return;}
        var cfg=GoofyConfig.INSTANCE;
        var account=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.current();
        String current=world.username()+":"+(account==null?"unknown":account.profile())+":"+cfg.marketAnalysis.endpoint;
        if(!Objects.equals(scope,current)){clear();scope=current;}
        var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
        // Fresh data arriving after a request must bypass the periodic poll deadline.
        if(market!=null && rankingRefresh.due(now,market.get("lastUpdated").getAsLong(),cfg))rebuild(now);
        if(now<nextRefresh)return;nextRefresh=now+20000;
        if(market==null) {
            routes=List.of();excluded.clear();generatedAt=0;error="Waiting for fresh Hypixel Bazaar data";
            if(bazaarPending==null) {
                long epoch=generation;
                var request=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch();bazaarPending=request;
                request.whenComplete((value,failure)->world.onClientThread(()->{
                    if(epoch!=generation)return;bazaarPending=null;
                    if(failure!=null){error="Hypixel Bazaar refresh failed; retrying";return;}
                    if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()!=null) {
                        rebuild(System.currentTimeMillis());nextRefresh=0;
                    }
                }));
            }
            return;
        }
        if(pending!=null)return;
        long epoch=generation;
        try {
            var settings=new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings();settings.endpoint=cfg.marketAnalysis.endpoint;settings.validate();
            var uri=URI.create(settings.endpoint).resolve("/v1/crafts/market");
            var request=HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),2*1024*1024));
            pending=request;
            request.whenComplete((response,failure)->
                net.minecraft.client.Minecraft.getInstance().execute(()->{
                    if(epoch!=generation)return;pending=null;
                    try {
                        if(failure!=null||response.statusCode()!=200)throw new IllegalArgumentException();
                        var value=JsonParser.parseString(response.body()).getAsJsonObject();
                        if(!"goofy-craft-market/1".equals(value.get("protocol").getAsString())||value.getAsJsonArray("rows").size()>10000)throw new IllegalArgumentException();
                        ah=value;error=value.has("error")&&!value.get("error").isJsonNull()?value.get("error").getAsString():null;
                    }catch(RuntimeException unavailable){error="AH craft discovery unavailable; Bazaar craft analysis remains available";ah=null;}
                    rebuild(System.currentTimeMillis());
                }));
        }catch(RuntimeException unavailable){error="Craft companion endpoint invalid";}
    }
    private void rebuild(long now) {
        var manager=FeatureManager.INSTANCE;var cfg=GoofyConfig.INSTANCE;var world=new LiveWorld();
        var observedMarket=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
        rankingRefresh.attempted(now,observedMarket==null?0:observedMarket.get("lastUpdated").getAsLong(),cfg);
        var blocked=new HashSet<>(CapitalManager.INSTANCE.occupiedProducts());
        try {
            if(!manager.production().queued() && manager.production().hasRetainedOrders()){
                routes=List.of();excluded.clear();generatedAt=0;error="Resolve retained craft Bazaar orders before starting another production run";return;
            }
            for(var job:manager.crafting().journal())if(job.account().equals(world.username())&&job.state()!=ProductionJobs.State.DONE
                    &&job.state()!=ProductionJobs.State.CANCELLED&&job.state()!=ProductionJobs.State.SELLING) {
                var recipe=RecipeCatalog.instance().byKey(job.recipeKey()).orElse(null);
                if(recipe!=null){blocked.add(recipe.outputId());blocked.addAll(recipe.ingredients().keySet());}
                else if(job.state()==ProductionJobs.State.REVIEW){routes=List.of();excluded.clear();generatedAt=0;error="Resolve production jobs marked REVIEW before automatic crafts";return;}
            }
            for(var job:manager.crafting().journal())if(job.account().equals(world.username())&&job.state()==ProductionJobs.State.SELLING) {
                String output=job.recipeKey().startsWith("auction:")?job.recipeKey().substring(job.recipeKey().lastIndexOf(':')+1):null;
                if(output!=null)blocked.add(output);
            }
            var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
            if(market==null){routes=List.of();excluded.clear();generatedAt=0;error="Waiting for fresh Hypixel Bazaar data";return;}
            double purse=new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse(),budget=CapitalManager.INSTANCE.available(purse);
            if(cfg.craftFlips.maximumCapital>0)budget=Math.min(budget,cfg.craftFlips.maximumCapital);
            routes=CraftFlipPlanner.rank(RecipeCatalog.instance(),market,ah,world.menu(),manager.observedSkills(),manager.observedUnlocks(),blocked,
                budget,cfg.craftFlips.minimumProfit,cfg.bazaarTaxPercentage,cfg.craftFlips.maxBatches,cfg.craftFlips.venue,now,excluded);
            generatedAt=now;lastMarketSource=market.get("lastUpdated").getAsLong();
            if(error!=null && error.startsWith("Waiting for"))error=null;
            com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.ranking_updated",Map.of(
                "marketAt",lastMarketSource,"eligibleRoutes",routes.stream().filter(CraftFlipPlanner.Route::eligible).count(),
                "pricedRoutes",routes.size(),"excludedCandidateCounts",Map.copyOf(excluded),"budget",budget));
        }catch(Exception unavailable){routes=List.of();excluded.clear();generatedAt=0;error="Production journal or account data unavailable";com.goofy.goofyaddons.diagnostics.Diagnostics.failure("production.ranking_failed",unavailable);}
    }
    List<CraftFlipPlanner.Route> currentRoutes(){rebuild(System.currentTimeMillis());return routes;}
    Map<String,Object> view() {
        var result=new LinkedHashMap<String,Object>();result.put("generatedAt",generatedAt);result.put("calculated",calculated());result.put("error",error);result.put("excludedCandidateCounts",Map.copyOf(excluded));
        result.put("minimumProfit",GoofyConfig.INSTANCE.craftFlips.minimumProfit);result.put("maxBatches",GoofyConfig.INSTANCE.craftFlips.maxBatches);result.put("venue",GoofyConfig.INSTANCE.craftFlips.venue);
        result.put("rows",routes.stream().limit(100).map(r->r.describe(RecipeCatalog.instance())).toList());
        result.put("rankingNote","Score combines conservative net profit, estimated crafting work and liquidity; it is not realized coins/hour. AH listings remain unsold positions.");
        return result;
    }
}
