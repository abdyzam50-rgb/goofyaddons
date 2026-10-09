package com.goofy.goofyaddons.features;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.failsafes.FailsafeManager;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper;
import com.goofy.goofyaddons.features.generalflipper.GeneralFlipper;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle;
import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Outcome;
import com.goofy.goofyaddons.features.lifecycle.TradingLifecycle.Source;
import java.util.List;

public class FeatureManager {
    public static final FeatureManager INSTANCE = new FeatureManager();
    private final BazaarFlipper books = com.goofy.goofyaddons.features.bookflipper.BookTraderFactory.create(() -> this);
    private final GeneralFlipper general = com.goofy.goofyaddons.features.generalflipper.GeneralTraderFactory.create(() -> this);
    private final com.goofy.goofyaddons.features.production.CraftingFeature crafting=new com.goofy.goofyaddons.features.production.CraftingFeature();
    private final com.goofy.goofyaddons.features.production.AuctionFeature auction=new com.goofy.goofyaddons.features.production.AuctionFeature();
    private final com.goofy.goofyaddons.features.production.ProductionLoopFeature production=new com.goofy.goofyaddons.features.production.ProductionLoopFeature();
    private final TradingEngines tradingEngines = new TradingEngines(books, general, crafting, auction, production);
    public com.goofy.goofyaddons.features.production.ProductionLoopFeature production(){return production;}
    public com.goofy.goofyaddons.features.production.AuctionFeature auction(){return auction;}
    public com.goofy.goofyaddons.features.production.CraftingFeature crafting(){return crafting;}
    public boolean prepareCrafting(){
        if(started() || GoofyConfig.loadError()!=null)return false;
        if(!prepareAccount(Source.MANUAL))return false;
        general.restoreBudget();return books.restoreBudget() && !general.hasStateError();
    }
    private final com.goofy.goofyaddons.features.marketanalysis.ShadowMarketAnalysis marketAnalysis = new com.goofy.goofyaddons.features.marketanalysis.ShadowMarketAnalysis();
    private final MenuScheduler scheduler = new MenuScheduler();
    private final com.goofy.goofyaddons.features.access.SkillPreflight skillPreflight = new com.goofy.goofyaddons.features.access.SkillPreflight();
    private final com.goofy.goofyaddons.features.access.AccountUnlocks accountUnlocks=new com.goofy.goofyaddons.features.access.AccountUnlocks();
    public java.util.Map<String,Integer> observedSkills(){return com.goofy.goofyaddons.features.access.AccountUnlocks.combinedSkills(accountUnlocks.skills(now()),skillPreflight.skills());}
    public java.util.Map<String,Integer> observedUnlocks(){return accountUnlocks.current(System.currentTimeMillis());}
    public boolean accountRequirementsPending(){return accountUnlocks.pending();}
    public void clearAccountRequirements(){skillPreflight.clear();accountUnlocks.clear();invalidateMarketReport();}
    public void navigationResumed(long elapsed){books.navigationResumed(elapsed);general.navigationResumed(elapsed);}
    private TradingMode mode = TradingMode.BOOKS;
    private TradingMode requested;
    private final TradingLifecycle lifecycle = new com.goofy.goofyaddons.features.lifecycle.TradingLifecycle();
    private Feature previousOwner;

    private String requirementAccount;
    private long profileTabAt;
    /** A production test runs the production, crafting and auction features alone, outside the trading lifecycle. */
    private boolean productionTest;
    private final MenuScheduler testScheduler = new MenuScheduler();
    private long testQuotesAt;
    private FeatureManager() {}
    private boolean started() { return lifecycle.started(); }
    private boolean paused() { return lifecycle.paused(); }
    private static long now() { return System.currentTimeMillis(); }

    public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report marketReport() {
        return marketAnalysis.latestReport();
    }
    public com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast executionForecast(String input,String output,int batch) {
        return marketAnalysis.executionForecast(input,output,batch);
    }
    public java.util.Set<String> retiredBookProducts(){var excluded=new java.util.HashSet<>(books.retirementExclusions());excluded.addAll(crafting.lockedProducts());excluded.addAll(auction.lockedProducts());excluded.addAll(production.lockedProducts());return java.util.Set.copyOf(excluded);}
    public void invalidateMarketReport() { marketAnalysis.stop(); }
    public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report automaticReport() {
        return marketAnalysis.automaticHeadReport();
    }
    /** Why automatic selection last chose its route, in one line; null before any decision. */
    public String lastRouteDecision() {
        var decision=marketAnalysis.lastDecision();return decision==null?null:decision.summary();
    }
    private List<Feature> engines() {
        return tradingEngines.enabled(mode, production.queued(), crafting.queued(), auction.queued());
    }

    public void onTick() {
        var requirementWorld=new com.goofy.goofyaddons.menu.LiveWorld();
        if(!requirementWorld.inWorld()){if(requirementAccount!=null){clearAccountRequirements();requirementAccount=null;}return;}
        com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.player(requirementWorld.playerId(),requirementWorld.username());
        var accounts=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE;
        if(accounts.current()==null && requirementWorld.now()-profileTabAt>=1000){profileTabAt=requirementWorld.now();accounts.tabList(requirementWorld.tabList());}
        if(!java.util.Objects.equals(requirementAccount,requirementWorld.username())){clearAccountRequirements();requirementAccount=requirementWorld.username();if(started())skillPreflight.begin();}
        if(skillPreflight.observe(requirementWorld.menu())){accountUnlocks.clear();invalidateMarketReport();}
        var scope=accounts.current();
        accountUnlocks.poll(requirementWorld.username(),scope==null?null:scope.profile(),GoofyConfig.INSTANCE.marketAnalysis.endpoint,skillPreflight.skills(),requirementWorld.now());
        if(skillPreflight.pending() && accountUnlocks.skills(requirementWorld.now()).containsKey("enchanting")) {
            skillPreflight.cancel();
            com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().reevaluateSkills(observedSkills());invalidateMarketReport();
        }
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital,GoofyConfig.INSTANCE.purseReserve);
        marketAnalysis.poll(started()?mode:GoofyConfig.INSTANCE.tradingMode);
        if (productionTest && !started()) { tickProductionTest(); return; }
        if (!started() || paused()) return;
        if(com.goofy.goofyaddons.features.access.BazaarNpcAccess.tick())return;
        if(skillPreflight.pending()) {
            if(accountUnlocks.pending())return; // Fast profile request first; Skills remains the fallback.
            try {skillPreflight.tick(new com.goofy.goofyaddons.menu.LiveWorld().menu(),new com.goofy.goofyaddons.menu.LiveActions(),System.currentTimeMillis());
                if(!skillPreflight.pending()){com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().reevaluateSkills(observedSkills());invalidateMarketReport();}}
            catch(RuntimeException failure){safetyPause("Account prerequisite check failed; close the current menu and review inventory");}
            return;
        }
        if(com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.finishing() || com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.finishing()) {
            // Observe acknowledgements, but never select another menu transaction during wind-down.
            for(Feature engine:engines()){engine.poll();if(paused() || !started())return;}
            if(books.recoveryPending() && !books.canYield())books.onTick();
            else if(previousOwner!=null && !previousOwner.canYield())previousOwner.onTick();
            return;
        }
        try {
        if(books.recoveryPending()) {
            books.onTick();
            if(!paused() && !books.recoveryPending())applyMode(GoofyConfig.INSTANCE.tradingMode);
            return;
        }
        if (requested != null && scheduler.canSwitch()) applyMode(requested);
        for (Feature engine : engines()) { engine.poll(); if(paused() || !started()) return; }
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

    public void startConfigured() { startConfigured(Source.MANUAL); }

    /** A start from any source; a schedule or transfer never clears a safety block. */
    public void startConfigured(Source source) {
        if (productionTest) { stopProductionTest("Production test stopped by the trading toggle"); return; }
        Diagnostics.event("INFO","trading.start_requested",Diagnostics.snapshot());
        if (lifecycle.start(source, now()) == Outcome.DENIED) {
            Diagnostics.event("WARN","trading.start_denied",java.util.Map.of("source",source.name(),"reason",lifecycle.reason()));
            return;
        }
        if (GoofyConfig.loadError() != null) {
            lifecycle.refuse(source, "Config file rejected", now());
            ChatUtils.clientMessage("Cannot start: " + GoofyConfig.loadError());
            return;
        }
        CapitalManager.INSTANCE.configure(GoofyConfig.INSTANCE.maxTradingCapital, GoofyConfig.INSTANCE.purseReserve);
        if (!prepareAccount(source)) return;
        // Load both engines' exposure before any gate so capital stays reserved for both.
        boolean booksReady = books.restoreBudget();
        general.restoreBudget(); // Count persisted ordinary-item positions even in Books mode.
        if (!booksReady) { lifecycle.refuse(source, "Book recovery required", now()); return; }
        if (general.hasStateError()) {
            lifecycle.refuse(source, "General order state unreadable", now());
            ChatUtils.clientMessage("Cannot start: general-order state is unreadable. File preserved; check logs.");
            return;
        }
        if(started() && paused() && books.recoveryPending()) {lifecycle.running();books.restartRecovery();return;}
        if (started() && paused()) { resume(source); return; }
        if (started()) return;
        skillPreflight.clear();invalidateMarketReport(); // Reuse fresh, profile-scoped API evidence across starts.
        if(GoofyConfig.INSTANCE.access.checkSkills || mode!=TradingMode.GENERAL || crafting.queued() || production.queued())skillPreflight.begin();
        else com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().reevaluateSkills(java.util.Map.of());
        crafting.start();
        auction.start();
        production.start();
        lifecycle.running();
        if(books.recoveryPending()) {mode=GoofyConfig.INSTANCE.tradingMode;books.start();return;}
        applyMode(GoofyConfig.INSTANCE.tradingMode);
    }

    /** Pins the current SkyBlock profile's files before any journal is read. */
    private boolean prepareAccount(Source source) {
        var storage=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE;
        String reason=storage.prepare();
        String adopted=storage.takeEvent();
        if(adopted!=null){Diagnostics.event("INFO","account.legacy_adopted",java.util.Map.of("detail",adopted));ChatUtils.clientMessage(adopted+". The originals in config/ are unchanged.");}
        if(reason==null)return true;
        lifecycle.refuse(source, reason, now());
        ChatUtils.clientMessage("Cannot start: " + reason);
        return false;
    }

    public void cycleMode() {
        TradingMode next = (requested != null ? requested : GoofyConfig.INSTANCE.tradingMode).next();
        GoofyConfig.INSTANCE.tradingMode = next;
        GoofyConfig.save();
        if (!started()) { mode = next; ChatUtils.clientMessage("Trading mode: " + next); return; }
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

    public void stop() { stop(Source.MANUAL); }

    public void stop(Source source) {
        if (productionTest) stopProductionTest("Production test stopped");
        com.goofy.goofyaddons.features.access.BazaarNpcAccess.cancel();
        if (lifecycle.stop(source, now()) != Outcome.GRANTED){production.stop();crafting.stop();auction.stop();return;}
        skillPreflight.cancel();
        marketAnalysis.stop();
        books.stop();
        general.stop();
        production.stop();
        crafting.stop();
        auction.stop();
        requested = null;
        scheduler.reset();
        previousOwner = null;
        FailsafeManager.INSTANCE.reset();
    }

    /**
     * Runs the queued production work now, without the trading toggle: no trader starts, and
     * the rest schedule and server transfers do not gate it. Every journal, proof and review
     * rule still applies, a safety pause still ends it, and the toggle or stop ends it too.
     */
    public boolean startProductionTest() {
        if (started()) { ChatUtils.clientMessage("Stop trading first; the queued production runs with trading anyway."); return false; }
        if (!production.queued()) return false;
        productionTest = true;
        testScheduler.reset();
        production.start(); crafting.start(); auction.start();
        Diagnostics.event("INFO", "production.test_started", java.util.Map.of("activity", production.activity()));
        return true;
    }

    private void tickProductionTest() {
        try {
            long now = now();
            // Traders normally keep Bazaar quotes fresh; with them off, the test keeps its own.
            if (com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh() == null && now - testQuotesAt >= 5000) {
                testQuotesAt = now; com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch();
            }
            var parts = List.<Feature>of(production, crafting, auction);
            for (Feature part : parts) { part.poll(); if (!productionTest) return; }
            Feature owner = testScheduler.select(parts);
            if (owner != null) owner.onTick();
            if (productionTest && !production.queued() && !crafting.queued() && !auction.queued()) {
                productionTest = false;
                production.stop(); crafting.stop(); auction.stop();
                testScheduler.reset();
                ChatUtils.clientMessage("Production test finished.");
            }
        } catch (RuntimeException failure) {
            Diagnostics.failure("production.test_failed", failure);
            safetyPause("Production test failed; inspect inventory and menus before retrying");
        }
    }

    private void stopProductionTest(String why) {
        productionTest = false;
        production.stop(); crafting.stop(); auction.stop();
        testScheduler.reset();
        ChatUtils.clientMessage(why + ".");
    }

    public void pause() { pause(Source.MANUAL); }

    public void pause(Source source) {
        com.goofy.goofyaddons.features.access.BazaarNpcAccess.cancel();
        if (lifecycle.pause(source, now()) != Outcome.GRANTED) return;
        marketAnalysis.stop();
        books.pause();
        general.pause();
        production.pause();
        crafting.pause();
        auction.pause();
        scheduler.reset();
        previousOwner = null;
    }

    public void safetyPause(String reason) { safetyPause(Source.ENGINE, reason); }

    /** Latches a pause; the first reason stays the visible one until a person clears it. */
    public void safetyPause(Source source, String reason) {
        productionTest = false;
        SafetyActions.latch(() -> {
            lifecycle.block(source, reason, now());
            scheduler.reset();
            previousOwner = null;
        }, failure -> org.slf4j.LoggerFactory.getLogger(FeatureManager.class)
                .error("Safety cleanup failed; trading remains paused", failure),
                com.goofy.goofyaddons.features.access.BazaarNpcAccess::cancel, marketAnalysis::stop, books::pause, general::pause, production::pause, crafting::pause, auction::pause,
                () -> Diagnostics.event("ERROR","safety.pause",java.util.Map.of("reason",reason,"context",Diagnostics.detailedSnapshot())),
                () -> ChatUtils.clientMessage("Trading paused: " + reason + " Check tracked orders before restarting."));
    }

    public void restartAfterTransfer() {
        if(lifecycle.restartAfterTransfer(now())!=Outcome.GRANTED)return;
        // Discard menu/navigation state, retain persisted positions and verify them afresh.
        stop(Source.TRANSFER);startConfigured(Source.TRANSFER);
    }
    public void resumeAfterTravel() {
        resume(Source.REBOOT); // An automatic travel recovery must not clear a safety block.
    }
    public void resume() { resume(Source.MANUAL); }

    public void resume(Source source) {
        if (lifecycle.resume(source, now()) != Outcome.GRANTED) return;
        for (Feature engine : engines()) engine.resume();
        scheduler.reset();
    }

    public boolean isMacroRunning() {
        return started() && (books.recoveryPending() || engines().stream().anyMatch(Feature::isRunning));
    }
    public boolean hasSafetyBlock(){return lifecycle.blocked();}
    public boolean canRest(){return !com.goofy.goofyaddons.features.access.BazaarNpcAccess.busy() && started() && !paused() && !CapitalManager.INSTANCE.purchaseSettling() && scheduler.canSwitch() && engines().stream().allMatch(Feature::canYield);}
    public boolean canReloadConfig() { return !started(); }
    public boolean isTradingActive() { return started() && !skillPreflight.pending() && !paused() && !books.recoveryPending(); }
    public String status() {
        if (!started()) return lifecycle.blocked() ? "BLOCKED" : "STOPPED";
        return paused() ? "PAUSED" : skillPreflight.pending()?"RECOVERING":books.recoveryPending()?"RECOVERING":"RUNNING";
    }
    public String modeLabel() { return (started() ? mode : GoofyConfig.INSTANCE.tradingMode).name(); }
    public java.util.Map<String,Object> diagnosticState() {
        return java.util.Map.of("capabilities",com.goofy.goofyaddons.features.capability.Capabilities.diagnosticState(),"lifecycle",lifecycle.diagnosticState(),"marketAnalysis",marketAnalysis.diagnosticState(),"books",books.diagnosticState(),"general",general.diagnosticState(),"owner",previousOwner==null?"none":previousOwner.name(),"requestedMode",requested==null?"none":requested.name());
    }
    public String taskItem() {
        if (!started() || paused()) return general.hasRetainedPositions()?general.retainedItem():books.hasRetainedTasks()?"Retained book tasks: review required":"No pending orders";
        if(production.queued() && (previousOwner==production || previousOwner==crafting || previousOwner==auction || previousOwner==null))return production.activity();
        if(previousOwner==crafting)return crafting.activity();
        if(previousOwner==auction)return auction.activity();
        if (previousOwner == books) return books.taskItem();
        if (previousOwner == general) return general.taskItem();
        return "Monitoring both configured engines";
    }
    public String activity() {
        if (lifecycle.blocked()) return lifecycle.reason();
        if (paused()) return "Paused for travel or review";
        if (!started()) return "Use the trading toggle to start";
        if(skillPreflight.pending())return "Checking account skill requirements";
        if(books.recoveryPending())return books.activity();
        if(production.queued() && (previousOwner==production || previousOwner==crafting || previousOwner==auction || previousOwner==null))return production.activity();
        if(previousOwner==crafting)return crafting.activity();
        if(previousOwner==auction)return auction.activity();
        if (previousOwner == books) return books.activity();
        if (previousOwner == general) return general.activity();
        return "Waiting for orders or eligible flips";
    }
}
