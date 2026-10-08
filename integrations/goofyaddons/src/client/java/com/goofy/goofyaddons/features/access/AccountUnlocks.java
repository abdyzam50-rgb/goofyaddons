package com.goofy.goofyaddons.features.access;

import com.google.gson.*;
import com.goofy.goofyaddons.menu.LiveWorld;
import com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Private selected-profile lookup through the bundled companion; never enters shared telemetry. */
public final class AccountUnlocks {
    private final HttpClient http=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(Duration.ofSeconds(2));
    private Map<String,Integer> unlocks=Map.of();
    private String account;
    private long next,expires;
    private int generation;
    private CompletableFuture<?> pending;
    public boolean pending(){return pending!=null;}
    public void clear(){generation++;if(pending!=null)pending.cancel(true);pending=null;unlocks=Map.of();account=null;next=expires=0;}
    public Map<String,Integer> current(long now){return now<expires?unlocks:Map.of();}
    public void poll(String username,String endpoint,Map<String,Integer> skills,long now) {
        if(!Objects.equals(account,username)){clear();account=username;}
        if(username==null || !username.matches("[A-Za-z0-9_]{1,16}") || pending!=null || now<next)return;
        next=now+60000;int token=generation;
        try {
            var settings=new MarketAnalysisSettings();settings.endpoint=endpoint;settings.validate();
            var uri=URI.create(endpoint).resolve("/v1/profiles?username="+username);
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();
            pending=http.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),256*1024))
                .whenComplete((response,error)->new LiveWorld().onClientThread(()->{
                    if(token!=generation)return;pending=null;
                    try {
                        if(error!=null || response.statusCode()!=200)throw new IllegalStateException("Profile lookup unavailable");
                        long received=System.currentTimeMillis();var root=JsonParser.parseString(response.body()).getAsJsonObject();
                        unlocks=parse(root,username,skills,received);
                        expires=root.get("fetchedAt").getAsLong()+300000;next=expires;
                    }catch(RuntimeException failure){unlocks=Map.of();expires=0;}
                }));
        }catch(RuntimeException failure){unlocks=Map.of();expires=0;}
    }
    static Map<String,Integer> parse(JsonObject root,String username,Map<String,Integer> skills,long now) {
        if(!"goofy-profile/1".equals(root.get("protocol").getAsString()) || !username.equalsIgnoreCase(root.get("username").getAsString()))throw new IllegalArgumentException("Account mismatch");
        long at=root.get("fetchedAt").getAsLong();if(at>now+5000 || now-at>=300000)throw new IllegalArgumentException("Stale requirements");
        var selected=new ArrayList<JsonObject>();
        for(var raw:root.getAsJsonArray("profiles")){var p=raw.getAsJsonObject();if(p.get("selected").getAsBoolean())selected.add(p);}
        if(selected.size()!=1)throw new IllegalArgumentException("Selected profile is unverified");
        var profile=selected.getFirst();var stats=profile.getAsJsonObject("stats");
        // Cross-check the API's selected profile against the live skills menu.
        var apiSkills=stats.getAsJsonObject("skills");var enchanting=apiSkills.get("Enchanting");
        if(skills.get("enchanting")==null || enchanting==null || enchanting.getAsDouble()!=skills.get("enchanting"))throw new IllegalArgumentException("Selected profile does not match observed Enchanting");
        var unknown=new HashSet<String>();for(var item:profile.getAsJsonArray("unknown"))unknown.add(item.getAsString());
        var result=new HashMap<String,Integer>();
        if(!unknown.contains("collections"))copy(stats.getAsJsonObject("collections"),"",result);
        if(!unknown.contains("slayers"))copy(stats.getAsJsonObject("slayers")," Slayer",result);
        if(!unknown.contains("faction reputation"))copy(stats.getAsJsonObject("reputation")," Reputation",result);
        if(!unknown.contains("Heart of the Mountain")) {
            int level=stats.get("hotmTier").getAsInt();result.put("heartofthemountain",level);result.put("hotm",level);
        }
        return Map.copyOf(result);
    }
    private static void copy(JsonObject input,String suffix,Map<String,Integer> output) {
        for(var e:input.entrySet()) {
            double value=e.getValue().getAsDouble();if(!Double.isFinite(value) || value<0 || value>1000000 || value!=Math.floor(value))throw new IllegalArgumentException("Invalid unlock level");
            output.put(RouteRequirements.normalize(e.getKey()+suffix),(int)value);
        }
    }
}
