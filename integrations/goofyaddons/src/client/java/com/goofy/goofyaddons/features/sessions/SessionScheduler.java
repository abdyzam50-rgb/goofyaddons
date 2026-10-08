package com.goofy.goofyaddons.features.sessions;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.scores.DisplaySlot;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Client-thread adapter. No passwords or authentication tokens are stored. */
public final class SessionScheduler {
    public static final SessionScheduler INSTANCE=new SessionScheduler();
    private final SessionCycle cycle=new SessionCycle();
    private ServerData server;
    private long seed,lastPoll,nextPoll;
    private boolean seedLoaded;
    private String fingerprint="";
    private DailySessionPlan.Decision decision;
    private SessionScheduler() {}
    private RestScheduleSettings settings(){return GoofyConfig.INSTANCE.restSchedule;}
    public boolean finishing(){return cycle.state()==SessionCycle.State.FINISHING;}
    public boolean armed(){return cycle.armed();}
    public String status() {
        String change=!cycle.armed() || decision==null || decision.nextChange()==null?"":DateTimeFormatter.ofPattern("EEE HH:mm z").format(decision.nextChange().atZone(settings().zone()));
        return cycle.state()+(!change.isBlank()?" | next "+change:"")+(!cycle.reason().isBlank()?" | "+cycle.reason():"");
    }
    public void register() {
        com.goofy.goofyaddons.commands.GoofyCommands.register(dispatcher->dispatcher.register(ClientCommands.literal("schedule")
                .executes(context->{ChatUtils.clientMessage(status());return 1;})
                .then(ClientCommands.literal("on").executes(context->{manualStart();return 1;}))
                .then(ClientCommands.literal("off").executes(context->{manualStop();return 1;}))));
    }
    public void manualStart() { manualStart(Source.MANUAL); }

    /** A start asked for by a person, at the keyboard or through the remote control. */
    public void manualStart(Source source) {
        if(!settings().enabled){FeatureManager.INSTANCE.startConfigured(source);return;}
        if(cycle.armed())return;
        Minecraft mc=Minecraft.getInstance();var current=mc.getCurrentServer();
        if(current==null || mc.player==null || mc.level==null){ChatUtils.clientMessage("Scheduled sessions require a connected multiplayer server.");return;}
        try {
            settings().validate();loadSeed();
            server=new ServerData(current.name,current.ip,current.type());server.copyFrom(current);
            decision=DailySessionPlan.at(settings(),seed,Instant.now());
            fingerprint=settings().fingerprint();nextPoll=0;
            if(!decision.online() && !FeatureManager.INSTANCE.isMacroRunning()) {
                cycle.armForRest(System.currentTimeMillis(),port);
                if(!cycle.armed())return;
            } else {
                FeatureManager.INSTANCE.startConfigured(source);
                if(!FeatureManager.INSTANCE.isMacroRunning())return;
                cycle.arm(System.currentTimeMillis());
            }
            ChatUtils.clientMessage("Scheduled sessions armed: "+status());
            Diagnostics.event("INFO","sessions.armed",java.util.Map.of("zone",settings().zone().getId()));
        }catch(RuntimeException | java.io.IOException failure){
            FeatureManager.INSTANCE.safetyPause(Source.SCHEDULE,"Cannot arm scheduled sessions; check settings and schedule seed file");
            Diagnostics.failure("sessions.arm_failed",failure);
        }
    }
    public void manualStop() { manualStop(Source.MANUAL); }

    public void manualStop(Source source) {
        com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.cancelPending();
        cycle.cancel("Manual stop");FeatureManager.INSTANCE.stop(source);
        if(Minecraft.getInstance().gui.screen() instanceof ScheduledRestScreen)Minecraft.getInstance().gui.setScreen(new TitleScreen());
    }
    public boolean tick() {
        if(!cycle.armed())return false;
        if(!settings().enabled || !settings().fingerprint().equals(fingerprint)) {
            cycle.cancel("Schedule settings changed; use the trading toggle to rearm");
            if(Minecraft.getInstance().gui.screen() instanceof ScheduledRestScreen)Minecraft.getInstance().gui.setScreen(new TitleScreen());
            return false;
        }
        if(cycle.state()==SessionCycle.State.CONNECTING && Minecraft.getInstance().gui.screen() instanceof ScheduledRestScreen) {
            cycle.cancel("Connection cancelled; automatic retries disabled");return true;
        }
        long now=System.currentTimeMillis();
        if(now>=lastPoll && now<nextPoll)return cycle.state()!=SessionCycle.State.ACTIVE && cycle.state()!=SessionCycle.State.FINISHING;
        lastPoll=now;nextPoll=now+1000;
        try {
            decision=DailySessionPlan.at(settings(),seed,Instant.ofEpochMilli(now));
            SessionCycle.State before=cycle.state();boolean skip=cycle.tick(decision.online(),now,settings(),port);
            if(before!=cycle.state())Diagnostics.event("INFO","sessions.transition",java.util.Map.of("from",before.name(),"to",cycle.state().name(),"reason",cycle.reason()));
            return skip;
        }catch(RuntimeException failure) {
            cycle.cancel("Scheduled session failed; automatic reconnect cancelled");
            FeatureManager.INSTANCE.safetyPause(Source.SCHEDULE,cycle.reason());Diagnostics.failure("sessions.tick_failed",failure);return true;
        }
    }
    private void loadSeed() throws java.io.IOException {
        if(seedLoaded)return;
        Path file=FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-session-schedule.json");
        if(Files.exists(file)) {
            if(Files.size(file)>1024)throw new java.io.IOException("Schedule seed file too large; original preserved");
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(json.size()!=2 || !json.has("seed") || !json.has("version") || json.get("version").getAsInt()!=1
                    || !json.get("seed").isJsonPrimitive() || !json.getAsJsonPrimitive("seed").isNumber())
                throw new java.io.IOException("Schedule seed file unreadable; original preserved");
            seed=json.get("seed").getAsLong();
        } else {
            seed=new java.security.SecureRandom().nextLong();Files.createDirectories(file.getParent());
            Path temp=Files.createTempFile(file.getParent(),".schedule-seed-",".tmp");
            try {
                var json=new JsonObject();json.addProperty("version",1);json.addProperty("seed",seed);Files.writeString(temp,json.toString());
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file);}
            }finally{Files.deleteIfExists(temp);}
        }
        seedLoaded=true;
    }
    private final SessionCycle.Port port=new SessionCycle.Port() {
        private Minecraft mc(){return Minecraft.getInstance();}
        public boolean connected(){return mc().player!=null && mc().level!=null;}
        public boolean connecting(){return mc().gui.screen() instanceof ConnectScreen;}
        public boolean blocked(){return FeatureManager.INSTANCE.hasSafetyBlock();}
        public boolean transactionBoundary(){return (!FeatureManager.INSTANCE.isMacroRunning() || FeatureManager.INSTANCE.canRest())
                && !com.goofy.goofyaddons.features.access.BazaarNpcAccess.busy() && connected()
                && mc().player.containerMenu.getCarried().isEmpty() && !CapitalManager.INSTANCE.purchaseSettling();}
        public boolean ready(){
            if(!connected() || mc().getCurrentServer()==null || !server.ip.equals(mc().getCurrentServer().ip))return false;
            var sidebar=mc().level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
            return sidebar!=null && sidebar.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT).contains("SKYBLOCK")
                    && new ScoreboardUtils().getPurse()>=0;
        }
        public void stop(){FeatureManager.INSTANCE.stop(Source.SCHEDULE);}
        public void disconnect(){
            if(mc().player!=null && !mc().player.containerMenu.getCarried().isEmpty())
                throw new IllegalStateException("Cannot disconnect with an occupied cursor");
            if(mc().player!=null)mc().player.closeContainer();
            mc().disconnect(new ScheduledRestScreen(),false);
        }
        public void connect(){ConnectScreen.startConnecting(new ScheduledRestScreen(),mc(),ServerAddress.parseString(server.ip),server,false,null);}
        public void command(String command){Diagnostics.command(command);}
        public void start(){FeatureManager.INSTANCE.startConfigured(Source.SCHEDULE);}
        public void block(String reason){FeatureManager.INSTANCE.safetyPause(Source.SCHEDULE,reason);ChatUtils.clientMessage(reason);}
        public void scheduleCancelled(String reason){ChatUtils.clientMessage(reason+". Original stop: "+FeatureManager.INSTANCE.activity());}
    };
}
