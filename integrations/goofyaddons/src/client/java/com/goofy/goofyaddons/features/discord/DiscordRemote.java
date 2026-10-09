package com.goofy.goofyaddons.features.discord;

import com.google.gson.*;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.sessions.SessionScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Commands are read asynchronously and executed only on the Minecraft client thread. */
public final class DiscordRemote {
    public static final DiscordRemote INSTANCE=new DiscordRemote();
    private String session=UUID.randomUUID().toString();
    private final ArrayDeque<JsonObject> events=new ArrayDeque<>(),acks=new ArrayDeque<>();
    private final Set<String> processed=new LinkedHashSet<>();
    private final HttpClient http=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(Duration.ofSeconds(2));
    private CompletableFuture<JsonObject> request;
    private long nextPoll,logoutAt,loginAt,readyAt,lastJoin;
    private int joinAttempts;
    private ServerData remembered;
    private String key;
    private boolean wasEnabled;
    private long cancelledAt,contactAt;
    private String bridgeStatus="OFF";
    public String status(){return bridgeStatus;}
    private List<JsonObject> sentEvents=List.of(),sentAcks=List.of();
    private DiscordRemote() {}
    public boolean finishing(){return logoutAt>0;}
    public boolean active(){return logoutAt>0 || loginAt>0;}
    public void cancelPending(){logoutAt=loginAt=0;cancelledAt=System.currentTimeMillis();}
    public void event(String kind,String text) {
        if(!GoofyConfig.INSTANCE.discord.enabled)return;
        if(events.size()>=16)events.removeFirst();
        var event=new JsonObject();event.addProperty("id",UUID.randomUUID().toString());event.addProperty("kind",kind);event.addProperty("text",text.substring(0,Math.min(700,text.length())));events.addLast(event);
    }
    public void chat(String text) {
        var mc=Minecraft.getInstance();if(!GoofyConfig.INSTANCE.discord.enabled || mc.player==null)return;
        String kind=ChatContact.kind(text,mc.player.getName().getString());
        if(kind==null || kind.equals("staff")&&!GoofyConfig.INSTANCE.discord.alertStaff || kind.equals("mention")&&!GoofyConfig.INSTANCE.discord.alertMentions)return;
        event(kind,text);
        if(GoofyConfig.INSTANCE.discord.pauseOnContact && !FeatureManager.INSTANCE.canReloadConfig()) {
            contactAt=System.currentTimeMillis();logoutAt=loginAt=0;FeatureManager.INSTANCE.safetyPause(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE,"Player contact received; review the Discord alert and respond manually");
        }
    }
    public void tick() {
        var mc=Minecraft.getInstance();var manager=FeatureManager.INSTANCE;long now=System.currentTimeMillis();
        if(mc.getCurrentServer()!=null && mc.player!=null) {
            var current=mc.getCurrentServer();remembered=new ServerData(current.name,current.ip,current.type());remembered.copyFrom(current);
        }
        if(!GoofyConfig.INSTANCE.discord.enabled){
            if(wasEnabled){session=UUID.randomUUID().toString();processed.clear();wasEnabled=false;}
            bridgeStatus="OFF";request=null;key=null;logoutAt=loginAt=0;events.clear();acks.clear();return;
        }
        wasEnabled=true;
        if(logoutAt>0) {
            var logout=PlayerCommandPolicy.logout(now,logoutAt,manager.canReloadConfig() || manager.canRest(),
                mc.player==null || mc.player.containerMenu.getCarried().isEmpty());
            if(logout==PlayerCommandPolicy.Logout.DISCONNECT) {
                logoutAt=0;SessionScheduler.INSTANCE.manualStop(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE);mc.disconnect(new TitleScreen(),false);event("connection","Logged off by Discord request; saved positions retained.");
            }else if(logout==PlayerCommandPolicy.Logout.TIMED_OUT) {
                logoutAt=0;manager.safetyPause("Remote logout could not reach a verified transaction boundary");event("connection","Logout stopped at a safety pause; Minecraft remains connected.");
            }
        }
        if(loginAt>0) {
            if(now-loginAt>120000 || mc.gui.screen() instanceof DisconnectedScreen) {loginAt=0;event("connection","Remote reconnect did not reach SkyBlock; reconnect manually.");}
            else if(mc.player!=null && mc.level!=null && mc.gui.screen()==null) {
                if(com.goofy.goofyaddons.features.sessions.TransferRecovery.skyblockReady(mc)) {
                    if(readyAt==0)readyAt=now;
                    if(now-readyAt>=5000){loginAt=0;SessionScheduler.INSTANCE.manualStart(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE);event("connection","Reconnected; normal inventory and order recovery started.");}
                }else {
                    readyAt=0;
                    if(now-loginAt>8000 && joinAttempts<3 && now-lastJoin>15000){lastJoin=now;joinAttempts++;com.goofy.goofyaddons.diagnostics.Diagnostics.command(GoofyConfig.INSTANCE.restSchedule.joinCommand);}
                }
            }else readyAt=0;
        }
        if(request!=null && request.isDone()) {
            try {
                var reply=request.join();bridgeStatus="PAIRED";
                if(reply.has("delivery") && reply.get("delivery").isJsonObject() && !reply.getAsJsonObject("delivery").get("ready").getAsBoolean())bridgeStatus="PAIRED · DISCORD UNAVAILABLE";
                events.removeAll(sentEvents);acks.removeAll(sentAcks);
                if("goofy-discord/1".equals(reply.get("protocol").getAsString()))for(var value:reply.getAsJsonArray("commands"))run(value.getAsJsonObject(),now);
            }catch(RuntimeException ignored){bridgeStatus="COMPANION UNAVAILABLE";}finally{request=null;}
        }
        if(request!=null || now<nextPoll)return;nextPoll=now+2000;
        try {
            if(key==null) {
                var path=FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-discord.key");
                if(!Files.exists(path)){bridgeStatus="PAIRING KEY MISSING";return;}
                if(Files.size(path)>128){bridgeStatus="PAIRING KEY INVALID";return;}key=Files.readString(path).trim();
                if(!key.matches("[a-f0-9]{64}")){bridgeStatus="PAIRING KEY INVALID";key=null;return;}
            }
            var body=new JsonObject();body.addProperty("protocol","goofy-discord/1");body.addProperty("sessionId",session);body.addProperty("sentAt",now);
            body.addProperty("connected",mc.player!=null&&mc.level!=null);body.addProperty("state",manager.status());body.addProperty("mode",manager.modeLabel());
            if(mc.player!=null)body.addProperty("purse",new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse());
            var tracker=com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE;var summary=tracker.summary();
            if(tracker.error()==null){body.addProperty("profit",summary.profit());body.addProperty("perHour",summary.perHour());}
            sentEvents=List.copyOf(events);sentAcks=List.copyOf(acks);var gson=new Gson();body.add("events",gson.toJsonTree(sentEvents));body.add("acks",gson.toJsonTree(sentAcks));
            var endpoint=URI.create(GoofyConfig.INSTANCE.marketAnalysis.endpoint).resolve("/v1/control/exchange");
            var req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").header("X-Goofy-Control",key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            request=http.sendAsync(req,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),8192)).thenApply(res->{
                if(res.statusCode()!=200)throw new IllegalStateException("Control unavailable");return JsonParser.parseString(res.body()).getAsJsonObject();
            });
        }catch(Exception ignored){/* Optional bridge failures do not stop trading or expose secrets. */}
    }
    private void run(JsonObject command,long now) {
        if(!session.equals(command.get("sessionId").getAsString()) || command.get("expiresAt").getAsLong()<now)return;
        String id=command.get("id").getAsString();if(processed.contains(id))return;
        processed.add(id);if(processed.size()>256)processed.remove(processed.iterator().next());
        String result;
        try {
            var mc=Minecraft.getInstance();var manager=FeatureManager.INSTANCE;
            String action=command.get("action").getAsString();long issuedAt=command.get("issuedAt").getAsLong();
            if(PlayerCommandPolicy.superseded(action,issuedAt,cancelledAt,contactAt))
                throw new IllegalStateException("Cancelled by a newer local stop or contact alert; send a fresh request");
            result=switch(action) {
                case "stop" -> {logoutAt=loginAt=0;SessionScheduler.INSTANCE.manualStop(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE);yield "Trading and automatic reconnect stopped";}
                case "start" -> {if(mc.player==null || mc.level==null || mc.gui.screen()!=null)throw new IllegalStateException("Connect to SkyBlock and close menus first");logoutAt=loginAt=0;SessionScheduler.INSTANCE.manualStart(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE);yield "Start requested; current state: "+manager.status();}
                case "logout" -> {loginAt=0;logoutAt=now;yield "Waiting for the current transaction to finish before logging off";}
                case "login" -> {
                    if(mc.player!=null || mc.getConnection()!=null || mc.gui.screen() instanceof ConnectScreen)throw new IllegalStateException("Already connected or connecting");
                    if(remembered==null)throw new IllegalStateException("Connect manually once in this Minecraft session first");
                    SessionScheduler.INSTANCE.manualStop(com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source.REMOTE);loginAt=now;readyAt=lastJoin=0;joinAttempts=0;
                    ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(remembered.ip),remembered,false,null);yield "Reconnect requested";
                }
                case "chat" -> {
                    String text=command.get("text").getAsString();
                    if(mc.player==null || mc.getConnection()==null)throw new IllegalStateException("Player is offline");
                    if(!PlayerCommandPolicy.plainChat(text))throw new IllegalStateException("Use one plain chat message");
                    mc.getConnection().sendChat(text);yield "Chat sent";
                }
                default -> throw new IllegalStateException("Unsupported action");
            };
        }catch(RuntimeException failure){result="Not executed: "+failure.getMessage();}
        if(acks.size()>=32)acks.removeFirst();var ack=new JsonObject();ack.addProperty("id",id);ack.addProperty("result",result);acks.addLast(ack);
    }
}
