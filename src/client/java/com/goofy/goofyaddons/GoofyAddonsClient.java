package com.goofy.goofyaddons;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.profit.ProfitHud;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.keybinds.GoofyKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;


public class GoofyAddonsClient implements ClientModInitializer {
    private boolean reloadHeld;

    @Override
    public void onInitializeClient() {
        Diagnostics.register();
        GoofyConfig.load();
        com.goofy.goofyaddons.features.CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        ChatHook.register();
        GoofyKeybinds.register();
        ProfitHud.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
            if (client.player == null || client.level == null) {
                FeatureManager.INSTANCE.stop();
                ProfitTracker.INSTANCE.tick(false);
                Diagnostics.tick();
                return;
            }
            boolean stopRequested = false;
            while (GoofyKeybinds.stopKey.consumeClick()) stopRequested = true;
            if (com.goofy.goofyaddons.features.SafetyActions.tradingTick(stopRequested, FeatureManager.INSTANCE::stop,
                    FailsafeManager.INSTANCE::onTick, FeatureManager.INSTANCE::onTick)) {
                // Discard queued starts/mode changes so stop wins the entire tick.
                while (GoofyKeybinds.startKey.consumeClick()) {}
                while (GoofyKeybinds.modeKey.consumeClick()) {}
                ProfitTracker.INSTANCE.tick(false);
                Diagnostics.tick();
                return;
            }
            ProfitTracker.INSTANCE.tick(FeatureManager.INSTANCE.isTradingActive());

            // Reload only while stopped, and once per key press.
            boolean reloadDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_BACKSLASH);
            if (reloadDown && !reloadHeld && !FeatureManager.INSTANCE.isMacroRunning()) {
                GoofyConfig.load();
                com.goofy.goofyaddons.features.CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
            }
            reloadHeld = reloadDown;

            while (GoofyKeybinds.startKey.consumeClick()) {
                FeatureManager.INSTANCE.startConfigured();
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
            if (FeatureManager.INSTANCE.isMacroRunning()) FeatureManager.INSTANCE.pause();
        });
    }
}
