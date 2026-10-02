package com.goofy.goofyaddons.failsafes;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.utils.Clock;
import net.minecraft.client.Minecraft;

public class ScheduledReboot implements Failsafe {
    enum State {
        ISLAND,
        HUB,
        COMPLETED
    }

    private boolean enabled = false;
    private State state;
    private Clock clock = new Clock();


    public ScheduledReboot() {
        ChatHook.onMessage("Scheduled Reboot", this::handleMessage);
        ChatHook.onMessage("Game Update", this::handleMessage);
    }

    @Override
    public String name() {
        return "ScheduledReboot";
    }

    @Override
    public void onTick() {
        if (!enabled || Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return;

        switch (state) {
            case ISLAND -> {
                Diagnostics.command("Hub");
                state = State.HUB;
            }

            case HUB -> {
                clock.start(10000);
                if (clock.shouldFire()) {
                    Diagnostics.command("Is");
                    state = State.COMPLETED;
                }
            }

            case COMPLETED -> {
                clock.start(5000);
                if (clock.shouldFire()) {
                    FeatureManager.INSTANCE.resume();
                    enabled = false;
                }
            }

        }

    }

    @Override
    public void reset() {
        enabled = false;
        state = null;
        clock.stop();
    }

    private void handleMessage(String message) {
        if (!FeatureManager.INSTANCE.isMacroRunning() || enabled) return;
        clock.stop();
        FeatureManager.INSTANCE.pause();
        enabled = true;
        state = State.ISLAND;
    }
}
