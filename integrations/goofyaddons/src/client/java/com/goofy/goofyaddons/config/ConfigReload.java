package com.goofy.goofyaddons.config;

import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.utils.ChatUtils;
import net.fabricmc.fabric.api.client.command.v2.*;

/** One visible reload result for both the normal Minecraft keybind and client command. */
public final class ConfigReload {
    private ConfigReload() {}
    public static void register() {
        com.goofy.goofyaddons.commands.GoofyCommands.register(dispatcher->dispatcher.register(
                ClientCommands.literal("reload").executes(context->{reload();return 1;})));
    }
    public static void reload() {
        if(!FeatureManager.INSTANCE.canReloadConfig()) {
            ChatUtils.clientMessage("Config not reloaded: stop trading with the toggle key first (including paused/recovering modes).");return;
        }
        GoofyConfig.load();
        if(GoofyConfig.lastLoadProblem()!=null) {
            ChatUtils.clientMessage(GoofyConfig.lastLoadProblem()+" File: "+GoofyConfig.location()+". Last working settings kept when available.");return;
        }
        var cfg=GoofyConfig.INSTANCE;
        CapitalManager.INSTANCE.configure(cfg.maxTradingCapital,cfg.purseReserve);
        ChatUtils.clientMessage("Config reloaded: "+GoofyConfig.location());
        ChatUtils.clientMessage("Dashboard "+(cfg.marketAnalysis.dashboardEnabled?"ON":"OFF")+" | market analysis "+(cfg.marketAnalysis.enabled?"ON":"OFF")
                +" | mode "+cfg.tradingMode+" | capital "+(long)cfg.maxTradingCapital+" | reserve "+(long)cfg.purseReserve);
    }
}
