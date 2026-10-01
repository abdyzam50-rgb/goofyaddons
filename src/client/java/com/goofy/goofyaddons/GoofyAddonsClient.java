package com.goofy.goofyaddons;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.FeatureManager;
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
        ChatHook.register();
        GoofyKeybinds.register();
        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                FeatureManager.INSTANCE.stop();
                return;
            }
            FailsafeManager.INSTANCE.onTick();
            FeatureManager.INSTANCE.onTick();

            // Reload only while stopped, and once per key press.
            boolean reloadDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_BACKSLASH);
            if (reloadDown && !reloadHeld && !FeatureManager.INSTANCE.isMacroRunning()) GoofyConfig.load();
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
