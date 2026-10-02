package com.goofy.goofyaddons;

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
        GoofyConfig.load();
        com.goofy.goofyaddons.features.CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        ChatHook.register();
        GoofyKeybinds.register();
        ProfitHud.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                FeatureManager.INSTANCE.stop();
                ProfitTracker.INSTANCE.tick(false);
                return;
            }
            FailsafeManager.INSTANCE.onTick();
            FeatureManager.INSTANCE.onTick();
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
            while (GoofyKeybinds.stopKey.consumeClick()) {
                FeatureManager.INSTANCE.stop();
            }
        });

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, world) -> {
            if (FeatureManager.INSTANCE.isMacroRunning()) FeatureManager.INSTANCE.pause();
        });
    }
}
