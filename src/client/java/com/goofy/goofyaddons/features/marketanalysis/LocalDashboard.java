package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.UUID;

/** Explicit local account projection. Excludes credentials, chat, server addresses and menu lore. */
public final class LocalDashboard {
    private static final Gson GSON=new Gson();
    private static final String SESSION=UUID.randomUUID().toString();
    private LocalDashboard() {}
    public static void register() {
        var publisher=new DashboardTelemetry(()->GoofyConfig.INSTANCE,LocalDashboard::snapshot,System::currentTimeMillis,new MarketAnalysisClient()::publishDashboard);
        ClientTickEvents.END_CLIENT_TICK.register(client->publisher.tick());
    }
    private static JsonObject snapshot() {
        var mc=Minecraft.getInstance();var manager=FeatureManager.INSTANCE;var cfg=GoofyConfig.INSTANCE;
        var body=new JsonObject();body.addProperty("protocol","goofy-dashboard/1");body.addProperty("sessionId",SESSION);body.addProperty("sentAt",System.currentTimeMillis());
        var account=new JsonObject();boolean connected=mc.player!=null && mc.level!=null;
        account.addProperty("connected",connected);account.addProperty("name",connected?mc.player.getName().getString():"");body.add("account",account);
        var status=new JsonObject();status.addProperty("state",manager.status());status.addProperty("mode",manager.modeLabel());
        status.addProperty("action",manager.activity());status.addProperty("item",manager.taskItem());
        double purse=connected?new ScoreboardUtils().getPurse():-1;
        status.add("purse",purse>=0 && Double.isFinite(purse)?new JsonPrimitive(purse):JsonNull.INSTANCE);
        status.addProperty("taxPercentage",cfg.bazaarTaxPercentage);
        status.addProperty("pending",CapitalManager.INSTANCE.pending());status.addProperty("available",CapitalManager.INSTANCE.available(purse));
        status.addProperty("committed",CapitalManager.INSTANCE.committed());status.addProperty("capitalLimit",cfg.maxTradingCapital);status.addProperty("reserve",cfg.purseReserve);
        body.add("status",status);
        var inventory=new JsonArray();
        if(connected) for(int i=0;i<mc.player.getInventory().getContainerSize();i++) {
            var stack=mc.player.getInventory().getItem(i);if(stack.isEmpty())continue;
            var row=new JsonObject();row.addProperty("slot",i);row.addProperty("name",stack.getHoverName().getString());row.addProperty("count",stack.getCount());
            var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(custom!=null)row.addProperty("id",custom.copyTag().getStringOr("id",""));
            inventory.add(row);
        }
        body.add("inventory",inventory);
        var engines=manager.diagnosticState();
        body.add("books",GSON.toJsonTree(engines.get("books")));body.add("general",GSON.toJsonTree(engines.get("general")));
        body.add("analysis",GSON.toJsonTree(engines.get("marketAnalysis")));body.add("profit",GSON.toJsonTree(ProfitTracker.INSTANCE.summary()));
        body.addProperty("profitError",ProfitTracker.INSTANCE.error());
        body.add("executions",GSON.toJsonTree(ProfitTracker.INSTANCE.executionSamples()));
        body.addProperty("executionError",ProfitTracker.INSTANCE.executionError());
        return body;
    }
}
