package com.goofy.goofyaddons.features.profit;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

/** Called on the client thread; disk errors affect reporting, never replay trades. */
public final class ProfitTracker {
    public static final ProfitTracker INSTANCE=new ProfitTracker();
    private final Path path=FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-profit.json");
    private ProfitLedger ledger=new ProfitLedger();
    private ExecutionLedger execution=new ExecutionLedger();
    private final Path executionPath=path.resolveSibling("goofyaddons-execution.json");
    private String executionError;
    private boolean loaded;
    private String error;
    private long lastTick;
    private long lastSave;
    private boolean previouslyActive;
    private ProfitTracker() {}
    private void load() {
        if (loaded) return;
        loaded=true;
        try { execution=ExecutionLedger.read(executionPath); } catch(Exception bad) { executionError="Execution history unreadable; preserved"; Diagnostics.failure("execution.load_failed",bad); }
        try { ledger=ProfitLedger.read(path); }
        catch (Exception bad) { Diagnostics.failure("profit.load_failed",bad); error="Profit file unreadable; preserved"; LoggerFactory.getLogger(ProfitTracker.class).error(error,bad); }
    }
    public void tick(boolean active) {
        load();
        long now=System.nanoTime()/1000000;
        if(!active || lastTick>0&&now-lastTick>5000)execution.interrupt();
        try { if (active && previouslyActive && lastTick>0) ledger.activeTime(Math.max(0,now-lastTick)); }
        catch (RuntimeException bad) { reportError(bad); }
        lastTick=now;
        if (previouslyActive && !active || active && now-lastSave>=10000) save();
        previouslyActive=active;
    }
    public void acquire(String id,String engine,String item,String event,int units,Double cost) {
        load(); if (error!=null) return;
        try { if (ledger.acquire(id,engine,item,event,units,cost)) { Diagnostics.event("INFO","trade.acquired",java.util.Map.of("trade",id,"engine",engine,"item",item,"units",units,"cost",cost==null?"unknown":cost)); save(); } }
        catch (RuntimeException bad) { reportError(bad); }
    }
    public void recoverHoldings(String id,String engine,String item,int observedUnits) {
        load();if(error!=null)return;
        try {if(ledger.recoverHoldings(id,engine,item,observedUnits))save();}
        catch(RuntimeException bad){reportError(bad);}
    }
    public void retire(String id){load();if(executionError!=null)return;
        try {if(execution.retire(id,System.currentTimeMillis()))execution.write(executionPath);}
        catch(Exception bad){executionError="Execution history save failed; reporting only";Diagnostics.failure("execution.save_failed",bad);}
    }
    public void sell(String id,String engine,String item,String event,int units,Double proceeds) {
        load(); if (error!=null) return;
        Double cost=ledger.knownCost(id,units);
        try { if (ledger.sell(id,engine,item,event,units,proceeds)) {
            completeExecution(id,event,units,proceeds,cost==null||proceeds==null?null:proceeds-cost,false); Diagnostics.event("INFO","trade.sold",java.util.Map.of("trade",id,"engine",engine,"item",item,"units",units,"proceeds",proceeds==null?"unknown":proceeds,"cost",cost==null?"unknown":cost,"profit",cost==null||proceeds==null?"unknown":proceeds-cost)); save(); } }
        catch (RuntimeException bad) { reportError(bad); }
    }
    /** Zero recovery value consumes the old cost basis permanently, even if an item appears later. */
    public void writeOff(String id,String engine,String item,String event,int units) {
        load(); if (error!=null) return;
        try { if (ledger.writeOff(id,engine,item,event,units)) {
            completeExecution(id,event,units,0.0,null,true);
            Diagnostics.event("WARN","trade.written_off",java.util.Map.of("trade",id,"engine",engine,"item",item,"units",units));
            save();
        } }
        catch (RuntimeException bad) { reportError(bad); }
    }
    public void beginExecution(String id,String engine,String input,String output,int units,int batch,long startedAt) {
        load();if(executionError!=null)return;
        execution.begin(id,engine,input,output,units,batch,startedAt);
    }
    public void beginExecution(String id,String engine,String input,String output,int units,int batch,long startedAt,Double expectedProfit) {
        beginExecution(id,engine,input,output,units,batch,startedAt,expectedProfit,null);
    }
    public void beginExecution(String id,String engine,String input,String output,int units,int batch,long startedAt,Double expectedProfit,ExecutionLedger.Forecast forecast) {
        load();if(executionError!=null)return;
        execution.begin(id,engine,input,output,units,batch,startedAt,expectedProfit,forecast);
    }
    public java.util.List<ExecutionLedger.Sample> executionSamples() {load();return execution.samples();}
    public java.util.List<ExecutionLedger.Active> activeExecutions() {load();return execution.active(System.currentTimeMillis());}
    public String executionError() {load();return executionError;}
    private void completeExecution(String id,String event,int units,Double proceeds,Double profit,boolean lost) {
        if(executionError!=null)return;
        try {execution.complete(id,event,units,proceeds,profit,System.currentTimeMillis(),lost);execution.write(executionPath);}
        catch(Exception bad){executionError="Execution history save failed; reporting only";Diagnostics.failure("execution.save_failed",bad);}
    }
    public ProfitLedger.Summary summary() { load(); return ledger.summary(); }
    public Double knownCost(String id,int units) {load();return error==null?ledger.knownCost(id,units):null;}
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
        Diagnostics.failure("profit.persistence_failed",bad);
        error="Profit tracking error; stats incomplete";
        LoggerFactory.getLogger(ProfitTracker.class).error(error,bad);
    }
}
