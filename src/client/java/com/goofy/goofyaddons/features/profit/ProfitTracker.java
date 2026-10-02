package com.goofy.goofyaddons.features.profit;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

/** Called on the client thread; disk errors affect reporting, never replay trades. */
public final class ProfitTracker {
    public static final ProfitTracker INSTANCE=new ProfitTracker();
    private final Path path=FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-profit.json");
    private ProfitLedger ledger=new ProfitLedger();
    private boolean loaded;
    private String error;
    private long lastTick;
    private long lastSave;
    private boolean previouslyActive;
    private ProfitTracker() {}
    private void load() {
        if (loaded) return;
        loaded=true;
        try { ledger=ProfitLedger.read(path); }
        catch (Exception bad) { error="Profit file unreadable; preserved"; LoggerFactory.getLogger(ProfitTracker.class).error(error,bad); }
    }
    public void tick(boolean active) {
        load();
        long now=System.nanoTime()/1000000;
        try { if (active && previouslyActive && lastTick>0) ledger.activeTime(Math.max(0,now-lastTick)); }
        catch (RuntimeException bad) { reportError(bad); }
        lastTick=now;
        if (previouslyActive && !active || active && now-lastSave>=10000) save();
        previouslyActive=active;
    }
    public void acquire(String id,String engine,String item,String event,int units,Double cost) {
        load(); if (error!=null) return;
        try { if (ledger.acquire(id,engine,item,event,units,cost)) save(); }
        catch (RuntimeException bad) { reportError(bad); }
    }
    public void sell(String id,String engine,String item,String event,int units,Double proceeds) {
        load(); if (error!=null) return;
        try { if (ledger.sell(id,engine,item,event,units,proceeds)) save(); }
        catch (RuntimeException bad) { reportError(bad); }
    }
    public ProfitLedger.Summary summary() { load(); return ledger.summary(); }
    public String error() { load(); return error; }
    public boolean resetSession() {
        load(); if (error!=null) return false;
        ledger.resetSession(); save(); return error==null;
    }
    private void save() {
        if (error!=null) return;
        try { ledger.write(path); lastSave=System.nanoTime()/1000000; }
        catch (Exception bad) { reportError(bad); }
    }
    private void reportError(Exception bad) {
        error="Profit tracking error; stats incomplete";
        LoggerFactory.getLogger(ProfitTracker.class).error(error,bad);
    }
}
