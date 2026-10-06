package com.goofy.goofyaddons;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.profit.ProfitHud;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.keybinds.GoofyKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;


public class GoofyAddonsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Diagnostics.register();
        GoofyConfig.load();
        com.goofy.goofyaddons.features.CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        ChatHook.register();
        com.goofy.goofyaddons.features.access.BazaarNpcAccess.register();
        com.goofy.goofyaddons.features.production.ProductionCommands.register();
        com.goofy.goofyaddons.features.production.AuctionCommands.register();
        GoofyKeybinds.register();
        com.goofy.goofyaddons.config.ConfigReload.register();
        com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.register();
        ProfitHud.register();
        com.goofy.goofyaddons.features.marketanalysis.LocalDashboard.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
            boolean stopRequested = false;
            while (GoofyKeybinds.stopKey.consumeClick()) stopRequested = true;
            if(stopRequested)com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStop();
            if(!stopRequested && com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.tick()) {
                while(GoofyKeybinds.startKey.consumeClick()) {}
                while(GoofyKeybinds.modeKey.consumeClick()) {}
                ProfitTracker.INSTANCE.tick(false);Diagnostics.tick();return;
            }
            if (client.player == null || client.level == null) {
                FeatureManager.INSTANCE.stop();
                ProfitTracker.INSTANCE.tick(false);
                Diagnostics.tick();
                return;
            }
            boolean reloadRequested=false;
            while(GoofyKeybinds.reloadKey.consumeClick())reloadRequested=true;
            if (com.goofy.goofyaddons.features.SafetyActions.tradingTick(stopRequested, FeatureManager.INSTANCE::stop,
                    FailsafeManager.INSTANCE::onTick, FeatureManager.INSTANCE::onTick)) {
                if(reloadRequested)com.goofy.goofyaddons.config.ConfigReload.reload();
                // Discard queued starts/mode changes so stop wins the entire tick.
                while (GoofyKeybinds.startKey.consumeClick()) {}
                while (GoofyKeybinds.modeKey.consumeClick()) {}
                ProfitTracker.INSTANCE.tick(false);
                Diagnostics.tick();
                return;
            }
            ProfitTracker.INSTANCE.tick(FeatureManager.INSTANCE.isTradingActive());

            if(reloadRequested)com.goofy.goofyaddons.config.ConfigReload.reload();

            while (GoofyKeybinds.startKey.consumeClick()) {
                com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStart();
            }
            while (GoofyKeybinds.modeKey.consumeClick()) {
                FeatureManager.INSTANCE.cycleMode();
            }
            Diagnostics.tick();
            } catch (RuntimeException failure) {
                Diagnostics.failure("client.tick_failed",failure);
                FeatureManager.INSTANCE.safetyPause("Client tick failed; check diagnostic logs.");
            }
        });

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            com.goofy.goofyaddons.menu.ServerMenuMirror.clear();
            if (FeatureManager.INSTANCE.isMacroRunning()) FeatureManager.INSTANCE.pause();
        });
    }
}
