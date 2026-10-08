package com.goofy.goofyaddons.features.companion;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisClient;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.CompletableFuture;

/** Local trade-only feed, independent of publishing an account dashboard. No key in packets. */
public final class ContributorTelemetry {
    private static final Gson GSON=new Gson();
    private static CompletableFuture<JsonObject> pending;
    private static long next;
    private static volatile String status="Uploads off";
    private ContributorTelemetry() {}
    public static void register() {
        var transport=new MarketAnalysisClient();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!BundledCalculator.contributor().enabled()) {
                if(pending!=null)pending.cancel(true);pending=null;next=0;status="Uploads off";return;
            }
            long now=System.currentTimeMillis();
            if(pending!=null && !pending.isDone() || now<next)return;
            try {
                var tracker=ProfitTracker.INSTANCE;
                if(tracker.executionError()!=null){status="Local trade history unavailable";next=now+10000;return;}
                var body=packet(now,GSON.toJsonTree(tracker.executionSamples()));
                next=now+10000;
                pending=transport.publishExecutions(GoofyConfig.INSTANCE.marketAnalysis.endpoint,body)
                        .whenComplete((reply,failure)->status=failure==null?"Local trade feed connected":"Waiting for local calculator");
            } catch(RuntimeException ignored){next=now+10000;status="Local trade feed unavailable";}
        });
    }
    static JsonObject packet(long at,JsonElement samples) {
        var body=new JsonObject();body.addProperty("protocol","goofy-executions/1");body.addProperty("sentAt",at);body.add("executions",samples);return body;
    }
    public static String status(){return status;}
}
