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
    private long nextRefresh,generatedAt,generation;
    private String scope,error="Waiting for craft market data";
    private List<CraftFlipPlanner.Route> routes=List.of();
    private final Map<String,Integer> excluded=new LinkedHashMap<>();
    List<CraftFlipPlanner.Route> routes(){return routes;}
    String emptyReason(){
        if(!excluded.isEmpty())return "Main exclusion: "+excluded.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return error==null?"Waiting for priced routes that meet minimum profit and market depth":error;
    }
    void clear(){generation++;if(pending!=null)pending.cancel(true);pending=null;ah=null;routes=List.of();excluded.clear();scope=null;nextRefresh=0;}
    void refresh() {
        var world=new LiveWorld();long now=world.now();
        if(!world.inWorld()){if(scope!=null)clear();return;}
        var cfg=GoofyConfig.INSTANCE;
        var account=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.current();
        String current=world.username()+":"+(account==null?"unknown":account.profile())+":"+cfg.marketAnalysis.endpoint;
        if(!Objects.equals(scope,current)){clear();scope=current;}
        if(now<nextRefresh)return;nextRefresh=now+20000;
        var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
        if(market==null){com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch();routes=List.of();excluded.clear();error="Waiting for fresh Hypixel Bazaar data";return;}
        rebuild(now);
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
        var blocked=new HashSet<>(CapitalManager.INSTANCE.occupiedProducts());
        try {
            for(var job:manager.crafting().journal())if(job.account().equals(world.username())&&job.state()!=ProductionJobs.State.DONE
                    &&job.state()!=ProductionJobs.State.CANCELLED&&job.state()!=ProductionJobs.State.SELLING) {
                var recipe=RecipeCatalog.instance().byKey(job.recipeKey()).orElse(null);
                if(recipe!=null){blocked.add(recipe.outputId());blocked.addAll(recipe.ingredients().keySet());}
                else if(job.state()==ProductionJobs.State.REVIEW){routes=List.of();excluded.clear();error="Resolve production jobs marked REVIEW before automatic crafts";return;}
            }
            for(var job:manager.crafting().journal())if(job.account().equals(world.username())&&job.state()==ProductionJobs.State.SELLING) {
                String output=job.recipeKey().startsWith("auction:")?job.recipeKey().substring(job.recipeKey().lastIndexOf(':')+1):null;
                if(output!=null)blocked.add(output);
            }
            var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
            double purse=new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse(),budget=CapitalManager.INSTANCE.available(purse);
            if(cfg.craftFlips.maximumCapital>0)budget=Math.min(budget,cfg.craftFlips.maximumCapital);
            routes=CraftFlipPlanner.rank(RecipeCatalog.instance(),market,ah,world.menu(),manager.observedSkills(),manager.observedUnlocks(),blocked,
                budget,cfg.craftFlips.minimumProfit,cfg.bazaarTaxPercentage,cfg.craftFlips.maxBatches,cfg.craftFlips.venue,now,excluded);
            generatedAt=now;
        }catch(Exception unavailable){routes=List.of();excluded.clear();error="Production journal or account data unavailable";}
    }
    List<CraftFlipPlanner.Route> currentRoutes(){rebuild(System.currentTimeMillis());return routes;}
    Map<String,Object> view() {
        var result=new LinkedHashMap<String,Object>();result.put("generatedAt",generatedAt);result.put("error",error);result.put("excludedCandidateCounts",Map.copyOf(excluded));
        result.put("minimumProfit",GoofyConfig.INSTANCE.craftFlips.minimumProfit);result.put("maxBatches",GoofyConfig.INSTANCE.craftFlips.maxBatches);result.put("venue",GoofyConfig.INSTANCE.craftFlips.venue);
        result.put("rows",routes.stream().limit(100).map(r->r.describe(RecipeCatalog.instance())).toList());
        result.put("rankingNote","Score combines conservative net profit, estimated crafting work and liquidity; it is not realized coins/hour. AH listings remain unsold positions.");
        return result;
    }
}
