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
        com.goofy.goofyaddons.features.companion.BundledCalculator.register();
        com.goofy.goofyaddons.features.CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        ChatHook.register();
        com.goofy.goofyaddons.features.sessions.TransferRecovery.INSTANCE.register();
        com.goofy.goofyaddons.features.access.BazaarNpcAccess.register();
        com.goofy.goofyaddons.features.production.ProductionCommands.register();
        com.goofy.goofyaddons.features.production.AuctionCommands.register();
        GoofyKeybinds.register();
        // Vanilla does not dispatch ordinary mapping clicks through container/rest screens.
        // The same toggle must still stop automation immediately while a menu owns input.
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,width,height)->
            net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((current,event)->{
                if(GoofyKeybinds.toggleKey.matches(event) && (!FeatureManager.INSTANCE.canReloadConfig()
                        || com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.armed()
                        || com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.active())) {
                    while(GoofyKeybinds.toggleKey.consumeClick()) {}
                    com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStop();
                    return false;
                }
                return true;
            }));
        com.goofy.goofyaddons.config.ConfigReload.register();
        com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.register();
        ProfitHud.register();
        com.goofy.goofyaddons.features.marketanalysis.LocalDashboard.register();
        com.goofy.goofyaddons.features.companion.ContributorTelemetry.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
            // Coalesce queued clicks: a slow tick must never stop and immediately restart.
            boolean toggleRequested = false;
            while (GoofyKeybinds.toggleKey.consumeClick()) toggleRequested = true;
            boolean stopRequested = toggleRequested && (!FeatureManager.INSTANCE.canReloadConfig()
                    || com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.armed()
                    || com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.active());
            if(stopRequested)com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStop();
            com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.tick();
            if(!stopRequested && com.goofy.goofyaddons.features.sessions.TransferRecovery.INSTANCE.tick()) {
                while(GoofyKeybinds.modeKey.consumeClick()) {}
                ProfitTracker.INSTANCE.tick(false);Diagnostics.tick();return;
            }
            if(!stopRequested && com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.tick()) {
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
                while (GoofyKeybinds.modeKey.consumeClick()) {}
                ProfitTracker.INSTANCE.tick(false);
                Diagnostics.tick();
                return;
            }
            ProfitTracker.INSTANCE.tick(FeatureManager.INSTANCE.isTradingActive());

            if(reloadRequested)com.goofy.goofyaddons.config.ConfigReload.reload();

            if (toggleRequested && client.gui.screen()==null) {
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
            com.goofy.goofyaddons.features.sessions.TransferRecovery.INSTANCE.worldChanged();
        });
    }
}
