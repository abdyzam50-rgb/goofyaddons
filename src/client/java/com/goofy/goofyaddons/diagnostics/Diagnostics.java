package com.goofy.goofyaddons.diagnostics;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;

public final class Diagnostics {
    private static DiagnosticLog LOG;
    private static final DiagnosticQueue WORKER=new DiagnosticQueue(512,64);
    private static final Map<String,String> LAST=new HashMap<>();
    private static volatile String error;
    private static long heartbeat;
    private static long lastSample;
    private Diagnostics() {}
    public static void event(String level,String type,Map<String,?> data) {
        if(LOG==null) return;
        try {
            var captured=LOG.capture(level,type,data);
            boolean critical=level.equals("ERROR") || level.equals("WARN") || type.startsWith("trade.")
                    || type.startsWith("order.") || type.startsWith("safety.") || type.startsWith("session.");
            boolean accepted=WORKER.submit(critical,()->{
                long dropped=WORKER.takePendingDropped();
                try {
                    LOG.append(captured);
                    if(dropped>0) LOG.append("WARN","logging.events_dropped",Map.of("count",dropped,"total",WORKER.totalDropped(),"critical",WORKER.criticalDropped()));
                    error=null;
                } catch(Exception failure) {
                    WORKER.restorePendingDropped(dropped);
                    if(error==null) LoggerFactory.getLogger(Diagnostics.class).error("Diagnostic log cannot be written",failure);
                    error=failure.getClass().getSimpleName();
                }
            });
            if(!accepted && critical) LoggerFactory.getLogger(Diagnostics.class).error("Critical diagnostic event rejected: {} (total critical drops {})",type,WORKER.criticalDropped());
        } catch(RuntimeException failure) {
            // Observation must never prevent a stop, pause, or transaction guard.
            LoggerFactory.getLogger(Diagnostics.class).error("Cannot capture diagnostic event {}",type,failure);
        }
    }

    public static void failure(String type,Throwable failure) {
        if(LOG==null) return;
        java.io.StringWriter out=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(out));
        var data=new LinkedHashMap<String,Object>();data.put("exception",out.toString());
        try { if(Minecraft.getInstance().isSameThread()) data.put("context",snapshot(true)); } catch(RuntimeException ignored) { data.put("context","unavailable"); }
        event("ERROR",type,data);
    }
    public static Map<String,Object> snapshot() { return snapshot(false); }
    public static Map<String,Object> detailedSnapshot() {
        try { return snapshot(true); }
        catch (RuntimeException failure) {
            return Map.of("contextUnavailable",failure.getClass().getSimpleName());
        }
    }
    private static Map<String,Object> snapshot(boolean detailed) {
        var data=new LinkedHashMap<String,Object>();
        Minecraft mc=Minecraft.getInstance();var manager=FeatureManager.INSTANCE;
        data.put("status",manager.status());data.put("mode",manager.modeLabel());data.put("action",manager.activity());data.put("item",manager.taskItem());
        data.put("connected",mc.player!=null && mc.level!=null);
        data.put("screen",mc.screen==null?"none":mc.screen.getClass().getSimpleName());
        if(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            data.put("menuTitle",mc.screen.getTitle().getString());
        }
        data.put("purse",mc.player==null?-1:new ScoreboardUtils().getPurse());data.put("committed",CapitalManager.INSTANCE.committed());
        data.put("purseStatus",mc.player==null?"player-absent":ScoreboardUtils.purseStatus());
        data.put("positions",CapitalManager.INSTANCE.positionCount());data.put("freshQuotes",BazaarApi.latestFresh()!=null);
        if(mc.player!=null) {
            data.put("container",mc.player.containerMenu.containerId);
            data.put("slots",mc.player.containerMenu.slots.size());
            if(detailed) {
            var items=new ArrayList<Map<String,Object>>();
            for(int i=0;i<mc.player.getInventory().getContainerSize();i++) {
                var stack=mc.player.getInventory().getItem(i);
                if(!stack.isEmpty()) {
                    var item=new LinkedHashMap<String,Object>();
                    item.put("slot",i);item.put("type",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());item.put("count",stack.getCount());
                    var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if(custom!=null) {
                        var tag=custom.copyTag();item.put("hypixelId",tag.getStringOr("id",""));
                        var enchants=tag.getCompound("enchantments").orElse(null);
                        if(enchants!=null) {
                            var levels=new TreeMap<String,Integer>();
                            for(String key:enchants.keySet()) levels.put(key,enchants.getIntOr(key,-1));
                            item.put("enchantments",levels);
                        }
                    }
                    items.add(item);
                }
            }
            data.put("inventory",items);
            var menuItems=new ArrayList<Map<String,Object>>();
            int end=mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>?Math.max(0,mc.player.containerMenu.slots.size()-36):0;
            for(int i=0;i<end;i++) {
                var stack=mc.player.containerMenu.slots.get(i).getItem();
                if(!stack.isEmpty()) {
                    var item=new LinkedHashMap<String,Object>();
                    item.put("slot",i);item.put("name",stack.getHoverName().getString());
                    item.put("count",stack.getCount());item.put("lore",menuLore(stack));
                    var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if(custom!=null) {
                        var tag=custom.copyTag();item.put("hypixelId",tag.getStringOr("id",""));
                        var enchants=tag.getCompound("enchantments").orElse(null);
                        if(enchants!=null) {
                            var levels=new TreeMap<String,Integer>();
                            for(String key:enchants.keySet()) levels.put(key,enchants.getIntOr(key,-1));
                            item.put("enchantments",levels);
                        }
                    }
                    menuItems.add(item);
                }
            }
            data.put("menuItems",menuItems);
            }
        }
        if(GoofyConfig.INSTANCE!=null) {
            data.put("capitalLimit",GoofyConfig.INSTANCE.maxTradingCapital);data.put("reserve",GoofyConfig.INSTANCE.purseReserve);
            data.put("minDelay",GoofyConfig.INSTANCE.minActionDelay);data.put("maxDelay",GoofyConfig.INSTANCE.maxActionDelay);
        }
        if(detailed) data.put("engines",manager.diagnosticState());
        data.put("droppedEvents",WORKER.totalDropped());data.put("criticalDroppedEvents",WORKER.criticalDropped());data.put("logError",error==null?"none":error);
        return data;
    }
    private static java.util.List<String> menuLore(net.minecraft.world.item.ItemStack stack) {
        var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
        if(lore==null) return java.util.List.of();
        return lore.lines().stream().map(line->line.getString().replaceAll("§.", "").replaceAll("(?i)^(?:By|Created by|Order by|Seller|Buyer|Owner|Placed by|Co-op member):.*$","<player redacted>").replaceAll("^-\\s*[\\d,]+x\\s+.*$","<vendor redacted>")).toList();
    }
    public static void tick() {
        long sample=System.currentTimeMillis();
        if(sample-lastSample<250) return;
        lastSample=sample;
        try {
        var context=snapshot();
        for(String key:List.of("status","mode","action","item","screen","menuTitle","container","freshQuotes","connected")) {
            String value=String.valueOf(context.get(key));
            if(!Objects.equals(LAST.put(key,value),value)) event("INFO","context.changed",Map.of("field",key,"value",value,"context",context));
        }
        long now=System.currentTimeMillis();
        if(now-heartbeat>=30000) {heartbeat=now;event("INFO","heartbeat",context);}
        } catch(RuntimeException failure) { failure("diagnostics.snapshot_failed",failure); }
    }
    public static void command(String command) {
        // Only record recognised game commands, never arbitrary configured text.
        String safe=command.matches("(?i)(bz .*|managebazaarorders|anvil|ec( 2)?|hub|is)")?command:"<custom command>";
        event("INFO","command.sent",Map.of("command",safe));
        Minecraft.getInstance().player.connection.sendCommand(command);
    }
    public static void register() {
        LOG=new DiagnosticLog(FabricLoader.getInstance().getGameDir().resolve("logs/goofyaddons"),2*1024*1024,5);
        var versions=new TreeMap<String,String>();
        for(String id:List.of("goofyaddons","minecraft","fabricloader","fabric-api")) FabricLoader.getInstance().getModContainer(id).ifPresent(mod->versions.put(id,mod.getMetadata().getVersion().getFriendlyString()));
        event("INFO","session.started",Map.of("versions",versions,"java",System.getProperty("java.version"),"os",System.getProperty("os.name")));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(ClientCommands.literal("goofydebug")
            .executes(context->{context.getSource().sendFeedback(Component.literal("Diagnostics: logs/goofyaddons | dropped: "+WORKER.totalDropped()+" | error: "+(error==null?"none":error)+" | /goofydebug export"));return 1;})
            .then(ClientCommands.literal("export").executes(context->{
                var state=snapshot(true);var source=context.getSource();var mc=Minecraft.getInstance();
                var profit=com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE;
                state.put("versions",Map.copyOf(versions));
                state.put("profit",profit.summary());state.put("profitError",profit.error());
                state.put("executions",profit.executionSamples());state.put("activeExecutions",profit.activeExecutions());
                state.put("executionError",profit.executionError());
                state.put("funded",CapitalManager.INSTANCE.funded());
                state.put("fundingUnknown",CapitalManager.INSTANCE.fundingUnknown());
                source.sendFeedback(Component.literal("Creating diagnostic bundle..."));
                boolean accepted=WORKER.submit(false,()->{
                    try { var file=LOG.export(state);mc.execute(()->source.sendFeedback(Component.literal("Saved diagnostics: "+file))); }
                    catch(Exception failed) {mc.execute(()->source.sendFeedback(Component.literal("Diagnostic export failed: "+failed.getClass().getSimpleName())));}
                });
                if(!accepted) {source.sendFeedback(Component.literal("Diagnostic queue full; retry export shortly."));return 0;}
                return 1;
            }))));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{try {event("INFO","session.stopping",snapshot());} catch(RuntimeException failed) {failure("session.snapshot_failed",failed);} finally {WORKER.shutdown();}try {WORKER.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
    }
}
