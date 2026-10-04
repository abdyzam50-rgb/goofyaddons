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
    private final com.goofy.goofyaddons.features.marketanalysis.ShadowMarketAnalysis marketAnalysis = new com.goofy.goofyaddons.features.marketanalysis.ShadowMarketAnalysis();
    private final MenuScheduler scheduler = new MenuScheduler();
    private TradingMode mode = TradingMode.BOOKS;
    private TradingMode requested;
    private boolean started;
    private boolean paused;
    private Feature previousOwner;
    private String statusReason = "";

    private FeatureManager() {}

    public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report marketReport() {
        return marketAnalysis.latestReport();
    }
    public com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast executionForecast(String input,String output,int batch) {
        return marketAnalysis.executionForecast(input,output,batch);
    }
    public java.util.Set<String> retiredBookProducts(){return books.retirementExclusions();}
    public void invalidateMarketReport() { marketAnalysis.stop(); }
    public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report automaticReport() {
        return marketAnalysis.automaticHeadReport();
    }
    private List<Feature> engines() {
        return switch (mode) {
            case BOOKS -> List.of(books);
            case GENERAL -> List.of(general);
            case BOTH -> List.of(books, general);
        };
    }

    public void onTick() {
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital,GoofyConfig.INSTANCE.purseReserve);
        marketAnalysis.poll(started?mode:GoofyConfig.INSTANCE.tradingMode);
        if (!started || paused) return;
        try {
        if(books.recoveryPending()) {
            books.onTick();
            if(!paused && !books.recoveryPending())applyMode(GoofyConfig.INSTANCE.tradingMode);
            return;
        }
        if (requested != null && scheduler.canSwitch()) applyMode(requested);
        for (Feature engine : engines()) { engine.poll(); if(paused || !started) return; }
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
        if (GoofyConfig.loadError() != null) {
            statusReason = "Config file rejected";
            ChatUtils.clientMessage("Cannot start: " + GoofyConfig.loadError());
            return;
        }
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        // Load both engines' exposure before any gate so capital stays reserved for both.
        boolean booksReady = books.restoreBudget();
        general.restoreBudget(); // Count persisted ordinary-item positions even in Books mode.
        if (!booksReady) { statusReason = "Book recovery required"; return; }
        if (general.hasStateError()) {
            statusReason = "General order state unreadable";
            ChatUtils.clientMessage("Cannot start: general-order state is unreadable. File preserved; check logs.");
            return;
        }
        if(started && paused && books.recoveryPending()) {paused=false;statusReason="";books.restartRecovery();return;}
        if (started && paused) { resume(); return; }
        if (started) return;
        started = true;
        paused = false;
        statusReason = "";
        if(books.recoveryPending()) {mode=GoofyConfig.INSTANCE.tradingMode;books.start();return;}
        applyMode(GoofyConfig.INSTANCE.tradingMode);
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
        marketAnalysis.stop();
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
        marketAnalysis.stop();
        books.pause();
        general.pause();
        scheduler.reset();
        previousOwner = null;
    }

    public void safetyPause(String reason) {
        SafetyActions.latch(() -> {
            statusReason = reason;
            paused = true;
            scheduler.reset();
            previousOwner = null;
        }, failure -> org.slf4j.LoggerFactory.getLogger(FeatureManager.class)
                .error("Safety cleanup failed; trading remains paused", failure),
                marketAnalysis::stop, books::pause, general::pause,
                () -> Diagnostics.event("ERROR","safety.pause",java.util.Map.of("reason",reason,"context",Diagnostics.detailedSnapshot())),
                () -> ChatUtils.clientMessage("Trading paused: " + reason + " Check tracked orders before restarting."));
    }

    public void resumeAfterTravel() {
        if(!statusReason.isBlank()) return; // An automatic travel recovery must not clear a safety block.
        resume();
    }
    public void resume() {
        if (!started || !paused) return;
        paused = false;
        statusReason = "";
        for (Feature engine : engines()) engine.resume();
        scheduler.reset();
    }

    public boolean isMacroRunning() {
        return started && (books.recoveryPending() || engines().stream().anyMatch(Feature::isRunning));
    }
    public boolean canReloadConfig() { return !started; }
    public boolean isTradingActive() { return started && !paused && !books.recoveryPending(); }
    public String status() {
        if (!started) return statusReason.isBlank() ? "STOPPED" : "BLOCKED";
        return paused ? "PAUSED" : books.recoveryPending()?"RECOVERING":"RUNNING";
    }
    public String modeLabel() { return (started ? mode : GoofyConfig.INSTANCE.tradingMode).name(); }
    public java.util.Map<String,Object> diagnosticState() {
        return java.util.Map.of("marketAnalysis",marketAnalysis.diagnosticState(),"books",books.diagnosticState(),"general",general.diagnosticState(),"owner",previousOwner==null?"none":previousOwner.name(),"requestedMode",requested==null?"none":requested.name());
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
        if(books.recoveryPending())return books.activity();
        if (previousOwner == books) return books.activity();
        if (previousOwner == general) return general.activity();
        return "Waiting for orders or eligible flips";
    }
}
