package com.goofy.goofyaddons.features.access;

import com.google.gson.*;
import com.goofy.goofyaddons.menu.LiveWorld;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Private selected-profile lookup directly through the public website service; never enters shared telemetry. */
public final class AccountUnlocks {
    private static final URI PROFILE_SERVICE=URI.create("https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles");
    record Response(int status,JsonObject body) {}
    @FunctionalInterface interface Transport { CompletableFuture<Response> fetch(URI uri); }
    private final Transport transport;
    private final Consumer<Runnable> clientThread;
    private final LongSupplier clock;
    private Map<String,Integer> unlocks=Map.of();
    private Map<String,Integer> apiSkills=Map.of();
    private String account,profile;
    private long next,expires;
    private int generation;
    private int connectionFailures;
    private CompletableFuture<?> pending;
    private String failureCode="NOT_STARTED",failure="Account lookup has not started";
    private boolean collectionsAvailable;
    public AccountUnlocks() { this(liveTransport(),task->new LiveWorld().onClientThread(task),System::currentTimeMillis); }
    AccountUnlocks(Transport transport,Consumer<Runnable> clientThread,LongSupplier clock) {
        this.transport=transport;this.clientThread=clientThread;this.clock=clock;
    }
    private static Transport liveTransport() {
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
        return uri->http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
            HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),256*1024))
            .thenApply(response->new Response(response.statusCode(),JsonParser.parseString(response.body()).getAsJsonObject()));
    }
    public boolean pending(){return pending!=null;}
    public void clear(){generation++;if(pending!=null)pending.cancel(true);pending=null;unlocks=apiSkills=Map.of();account=profile=null;next=expires=0;connectionFailures=0;collectionsAvailable=false;failureCode="NOT_STARTED";failure="Account lookup has not started";}
    public String status(long now) {
        if(profile==null)return "Waiting for the current SkyBlock profile";
        if(pending())return "Fetching account prerequisites";
        if(now<expires)return collectionsAvailable?"Account prerequisites verified":"Profile loaded; collection data is unpublished. Enable Collection API in SkyBlock API settings";
        return failure!=null?failure:"Account prerequisite data expired; refreshing";
    }
    public Map<String,Object> diagnosticState(long now) {
        return Map.of("status",status(now),"failureCode",failureCode,"pending",pending(),"skills",skills(now).size(),"unlocks",current(now).size(),
            "collectionsAvailable",now<expires && collectionsAvailable,"source","PUBLIC_WEBSITE","expiresInMs",Math.max(0,expires-now),"retryInMs",Math.max(0,next-now));
    }
    private void fail(String code,String reason) {unlocks=apiSkills=Map.of();expires=0;collectionsAvailable=false;failureCode=code;failure=reason;}
    private void connectionFailure(Throwable error) {
        while(error instanceof java.util.concurrent.CompletionException && error.getCause()!=null)error=error.getCause();
        if(error instanceof JsonParseException || error instanceof IllegalStateException) {
            connectionFailures=0;fail("INVALID_RESPONSE","Account service returned an invalid profile response; retrying automatically");return;
        }
        long delay=switch(++connectionFailures){case 1->5000;case 2->15000;case 3->30000;default->60000;};
        next=clock.getAsLong()+delay;
        if(error instanceof java.net.ConnectException)
            fail("ACCOUNT_SERVICE_UNREACHABLE","Website account service connection failed; retrying automatically in "+delay/1000+" seconds");
        else if(error instanceof HttpTimeoutException)
            fail("PROFILE_TIMEOUT","Account lookup timed out; retrying automatically in "+delay/1000+" seconds");
        else fail("UNREACHABLE","Profile connection failed; retrying automatically in "+delay/1000+" seconds");
    }
    static String serviceFailure(String code,int status) {
        return switch(code) {
            case "HYPIXEL_FORBIDDEN" -> "Hypixel rejected profile access (HTTP 403). Update the Worker HYPIXEL_API_KEY secret and check SkyBlock profile permissions";
            case "HYPIXEL_UNAUTHORIZED" -> "Hypixel rejected profile access (HTTP 401). Update the Worker HYPIXEL_API_KEY secret";
            case "PROFILE_NOT_CONFIGURED" -> "Worker profile lookup is not configured; check its API key and profile rate limiter";
            case "PROFILE_RATE_LIMITED" -> "Account lookup is rate limited; retrying automatically";
            case "USERNAME_NOT_FOUND" -> "Minecraft username could not be resolved";
            case "USERNAME_SERVICE_UNAVAILABLE" -> "Minecraft username service is unavailable; retrying automatically";
            default -> "Profile lookup failed (HTTP "+status+"); check the Worker. Retrying automatically";
        };
    }
    public Map<String,Integer> current(long now){return now<expires?unlocks:Map.of();}
    public Map<String,Integer> skills(long now){return now<expires?apiSkills:Map.of();}
    public static Map<String,Integer> combinedSkills(Map<String,Integer> api,Map<String,Integer> observed) {
        var result=new HashMap<>(api);result.putAll(observed);return Map.copyOf(result);
    }
    public void poll(String username,String profileName,Map<String,Integer> skills,long now) {
        if(!Objects.equals(account,username) || !Objects.equals(profile,profileName)){clear();account=username;profile=profileName;}
        if(username==null || !username.matches("[A-Za-z0-9_]{1,16}") || profileName==null || pending!=null || now<next)return;
        next=now+60000;int token=generation;
        failure=null;failureCode="FETCHING";
        try {
            var uri=URI.create(PROFILE_SERVICE+"?username="+username);
            var request=transport.fetch(uri);pending=request;
            request.whenComplete((response,error)->clientThread.accept(()->{
                    if(token!=generation)return;pending=null;
                    try {
                        if(error!=null){connectionFailure(error);return;}
                        connectionFailures=0;
                        if(response.status()!=200) {
                            String code=response.body().has("failureCode")?response.body().get("failureCode").getAsString():"PROFILE_SERVICE_UNAVAILABLE";
                            fail(switch(code){case "HYPIXEL_FORBIDDEN","HYPIXEL_UNAUTHORIZED","PROFILE_NOT_CONFIGURED","PROFILE_RATE_LIMITED","USERNAME_NOT_FOUND","USERNAME_SERVICE_UNAVAILABLE"->code;default->"PROFILE_SERVICE_UNAVAILABLE";},serviceFailure(code,response.status()));return;
                        }
                        long received=clock.getAsLong();var root=response.body();
                        var parsed=parseProfile(root,username,profileName,skills,received);
                        unlocks=parsed.unlocks();apiSkills=parsed.skills();
                        expires=root.get("fetchedAt").getAsLong()+300000;next=expires;
                        collectionsAvailable=parsed.collectionsAvailable();failure=null;failureCode="VERIFIED";
                    }catch(RuntimeException invalid){
                        String reason=invalid.getMessage();
                        String safe=Set.of("Account mismatch","Stale requirements","Current profile is unverified","Invalid skill level","Invalid unlock level").contains(reason==null?"":reason)?reason:
                            reason!=null && reason.startsWith("Profile does not match observed")?"Published levels conflict with live skill observations":"Profile response is missing or has invalid progression fields";
                        fail("INVALID_PROFILE",safe+"; waiting for verified account data");
                    }
                }));
        }catch(RuntimeException invalid){pending=null;fail("UNREACHABLE","Account lookup could not start; check the website account service");}
    }
    record ProfileRequirements(Map<String,Integer> skills,Map<String,Integer> unlocks,boolean collectionsAvailable) {}
    static Map<String,Integer> parse(JsonObject root,String username,Map<String,Integer> skills,long now) {
        return parseProfile(root,username,null,skills,now).unlocks();
    }
    static ProfileRequirements parseProfile(JsonObject root,String username,String profileName,Map<String,Integer> skills,long now) {
        if(!"goofy-profile/1".equals(root.get("protocol").getAsString()) || !username.equalsIgnoreCase(root.get("username").getAsString()))throw new IllegalArgumentException("Account mismatch");
        long at=root.get("fetchedAt").getAsLong();if(at>now+5000 || now-at>=300000)throw new IllegalArgumentException("Stale requirements");
        var selected=new ArrayList<JsonObject>();
        for(var raw:root.getAsJsonArray("profiles")){var p=raw.getAsJsonObject();
            if(profileName==null?p.get("selected").getAsBoolean():profileName.equalsIgnoreCase(p.get("name").getAsString()))selected.add(p);}
        if(selected.size()!=1)throw new IllegalArgumentException("Current profile is unverified");
        var profile=selected.getFirst();var stats=profile.getAsJsonObject("stats");
        // The server-observed profile name is sufficient to use the API before visiting Skills.
        // Legacy callers without a live profile still need the Enchanting cross-check.
        var apiSkills=stats.getAsJsonObject("skills");var enchanting=apiSkills.get("Enchanting");
        if(profileName==null && (skills.get("enchanting")==null || enchanting==null || enchanting.getAsDouble()!=skills.get("enchanting")))throw new IllegalArgumentException("Selected profile does not match observed Enchanting");
        var unknown=new HashSet<String>();for(var item:profile.getAsJsonArray("unknown"))unknown.add(item.getAsString());
        var levels=new HashMap<String,Integer>();
        for(var e:apiSkills.entrySet()) {
            String key=RouteRequirements.normalize(e.getKey());
            if(unknown.contains(key))continue;
            double value=e.getValue().getAsDouble();
            if(!Double.isFinite(value) || value<0 || value>100 || value!=Math.floor(value))throw new IllegalArgumentException("Invalid skill level");
            if(skills.containsKey(key) && skills.get(key)!=(int)value)throw new IllegalArgumentException("Profile does not match observed "+e.getKey());
            levels.put(key,(int)value);
        }
        var result=new HashMap<String,Integer>();
        if(!unknown.contains("collections")) {
            copy(stats.getAsJsonObject("collections"),"",result);
            if(stats.has("collectionIds"))copy(stats.getAsJsonObject("collectionIds"),"",result);
        }
        if(!unknown.contains("slayers")) {
            var slayers=stats.getAsJsonObject("slayers");
            for(var e:slayers.entrySet()) {
                if(unknown.contains(e.getKey()+" Slayer"))continue;
                double level=e.getValue().getAsDouble();int maximum=e.getKey().equalsIgnoreCase("Vampire")?5:9;
                if(!Double.isFinite(level) || level<0 || level>maximum || level!=Math.floor(level))throw new IllegalArgumentException("Invalid Slayer level");
                result.put(RouteRequirements.normalize(e.getKey()+" Slayer"),(int)level);
            }
        }
        if(!unknown.contains("faction reputation"))copy(stats.getAsJsonObject("reputation")," Reputation",result);
        if(!unknown.contains("Heart of the Mountain")) {
            double value=stats.get("hotmTier").getAsDouble();
            if(!Double.isFinite(value) || value<0 || value>10 || value!=Math.floor(value))throw new IllegalArgumentException("Invalid Heart of the Mountain tier");
            int level=(int)value;result.put("heartofthemountain",level);result.put("hotm",level);
        }
        if(!unknown.contains("Catacombs") && stats.has("catacombsLevel") && !stats.get("catacombsLevel").isJsonNull()) {
            double level=stats.get("catacombsLevel").getAsDouble();
            if(!Double.isFinite(level)||level<0||level>50||level!=Math.floor(level))throw new IllegalArgumentException("Invalid Catacombs level");
            result.put("catacombs",(int)level);
        }
        if(stats.has("dungeonCompletions"))for(var e:stats.getAsJsonObject("dungeonCompletions").entrySet()) {
            if(!e.getKey().matches("(?:Master )?Catacombs Floor [0-7]"))continue;
            String type=e.getKey().startsWith("Master ")?"Master Catacombs":"Catacombs";
            if(unknown.contains(type+" floor completions"))continue;
            double completed=e.getValue().getAsDouble();
            if(completed!=0&&completed!=1)throw new IllegalArgumentException("Invalid dungeon completion");
            result.put(RouteRequirements.normalize(e.getKey()+" completed"),(int)completed);
        }
        return new ProfileRequirements(Map.copyOf(levels),Map.copyOf(result),!unknown.contains("collections"));
    }
    private static void copy(JsonObject input,String suffix,Map<String,Integer> output) {
        for(var e:input.entrySet()) {
            double value=e.getValue().getAsDouble();if(!Double.isFinite(value) || value<0 || value>1000000 || value!=Math.floor(value))throw new IllegalArgumentException("Invalid unlock level");
            output.put(RouteRequirements.normalize(e.getKey()+suffix),(int)value);
        }
    }
}
