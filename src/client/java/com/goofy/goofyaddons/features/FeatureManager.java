package com.goofy.goofyaddons.features;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;
import com.goofy.goofyaddons.features.generalflipper.GeneralFlipper;
import com.goofy.goofyaddons.utils.ChatUtils;
import java.util.List;

public class FeatureManager {
    public static final FeatureManager INSTANCE = new FeatureManager();
    private final BazaarFlipper books = new BazaarFlipper();
    private final GeneralFlipper general = new GeneralFlipper();
    private final MenuScheduler scheduler = new MenuScheduler();
    private TradingMode mode = TradingMode.BOOKS;
    private TradingMode requested;
    private boolean started;
    private boolean paused;
    private Feature previousOwner;
    private String statusReason = "";

    private FeatureManager() {}

    private List<Feature> engines() {
        return switch (mode) {
            case BOOKS -> List.of(books);
            case GENERAL -> List.of(general);
            case BOTH -> List.of(books, general);
        };
    }

    public void onTick() {
        if (!started || paused) return;
        try {
        if (requested != null && scheduler.canSwitch()) applyMode(requested);
        for (Feature engine : engines()) engine.poll();
        Feature owner = scheduler.select(engines());
        if (owner != previousOwner && previousOwner != null) previousOwner.yieldMenu();
        previousOwner = owner;
        if (owner != null) owner.onTick();
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(FeatureManager.class).error("Trading tick failed; pausing", failure);
            Diagnostics.failure("trading.tick_failed",failure);
            safetyPause("Unexpected trading error; ownership records retained.");
        }
    }

    public void startConfigured() {
        Diagnostics.event("INFO","trading.start_requested",Diagnostics.snapshot());
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        if (!books.restoreBudget()) { statusReason = "Book recovery required"; return; }
        general.restoreBudget(); // Count persisted ordinary-item positions even in Books mode.
        if (general.hasStateError()) {
            statusReason = "General order state unreadable";
            ChatUtils.clientMessage("Cannot start: general-order state is unreadable. File preserved; check logs.");
            return;
        }
        if (started && paused) { resume(); return; }
        if (started) return;
        started = true;
        paused = false;
        statusReason = "";
        applyMode(GoofyConfig.INSTANCE.tradingMode);
    }

    public void start(String name) {
        GoofyConfig.INSTANCE.tradingMode = name.equals("GeneralFlipper") ? TradingMode.GENERAL : TradingMode.BOOKS;
        startConfigured();
    }

    public void cycleMode() {
        TradingMode next = (requested != null ? requested : GoofyConfig.INSTANCE.tradingMode).next();
        GoofyConfig.INSTANCE.tradingMode = next;
        GoofyConfig.save();
        if (!started) { mode = next; ChatUtils.clientMessage("Trading mode: " + next); return; }
        requested = next;
        ChatUtils.clientMessage("Switching to " + next + " after the current menu transaction.");
    }

    private void applyMode(TradingMode next) {
        if (previousOwner != null) previousOwner.yieldMenu();
        List<Feature> old = engines();
        mode = next;
        requested = null;
        List<Feature> enabled = engines();
        for (Feature engine : old) if (!enabled.contains(engine)) {
            if (engine == books) books.pauseForMode();
            else engine.pause();
        }
        for (Feature engine : enabled) engine.start();
        scheduler.reset();
        previousOwner = null;
        ChatUtils.clientMessage("Trading mode: " + mode);
    }

    public void stop() {
        if (!started) return;
        books.stop();
        general.stop();
        started = false;
        paused = false;
        requested = null;
        scheduler.reset();
        previousOwner = null;
        FailsafeManager.INSTANCE.reset();
        statusReason = "";
    }

    public void pause() {
        if (!started || paused) return;
        paused = true;
        books.pause();
        general.pause();
        scheduler.reset();
        previousOwner = null;
    }

    public void safetyPause(String reason) {
        Diagnostics.event("ERROR","safety.pause",java.util.Map.of("reason",reason,"context",Diagnostics.detailedSnapshot()));
        statusReason = reason;
        ChatUtils.clientMessage("Trading paused: " + reason + " Check tracked orders before restarting.");
        pause();
    }

    public void resume() {
        if (!started || !paused) return;
        paused = false;
        statusReason = "";
        for (Feature engine : engines()) engine.resume();
        scheduler.reset();
    }

    public boolean isMacroRunning() {
        return started && engines().stream().anyMatch(Feature::isRunning);
    }
    public boolean isTradingActive() { return started && !paused; }
    public String status() {
        if (!started) return statusReason.isBlank() ? "STOPPED" : "BLOCKED";
        return paused ? "PAUSED" : "RUNNING";
    }
    public String modeLabel() { return (started ? mode : GoofyConfig.INSTANCE.tradingMode).name(); }
    public java.util.Map<String,Object> diagnosticState() {
        return java.util.Map.of("books",books.diagnosticState(),"general",general.diagnosticState(),"owner",previousOwner==null?"none":previousOwner.name(),"requestedMode",requested==null?"none":requested.name());
    }
    public String taskItem() {
        if (!started || paused) return general.hasRetainedPositions()?general.retainedItem():books.hasRetainedTasks()?"Retained book tasks: review required":"No pending orders";
        if (previousOwner == books) return books.taskItem();
        if (previousOwner == general) return general.taskItem();
        return "Monitoring both configured engines";
    }
    public String activity() {
        if (!statusReason.isBlank()) return statusReason;
        if (paused) return "Paused for travel or review";
        if (!started) return "Press J to start";
        if (previousOwner == books) return books.activity();
        if (previousOwner == general) return general.activity();
        return "Waiting for orders or eligible flips";
    }
}
