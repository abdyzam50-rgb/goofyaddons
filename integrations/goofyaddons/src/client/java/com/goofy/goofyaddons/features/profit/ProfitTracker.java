package com.goofy.goofyaddons.features.profit;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

/** Called on the client thread; disk errors affect reporting, never replay trades. */
public final class ProfitTracker {
    public static final ProfitTracker INSTANCE=new ProfitTracker();
    private Path path;
    private ProfitLedger ledger=new ProfitLedger();
    private ExecutionLedger execution=new ExecutionLedger();
    private Path executionPath;
    private TradeHistory history;
    private String executionError;
    private boolean loaded;
    private String error;
    private long lastTick;
    private long lastSave;
    private boolean previouslyActive;
    private ProfitTracker() {}
    /**
     * Loads the profile trading uses, or the current profile while none is pinned. Reports
     * follow the profile; with none identified they are empty and nothing is written.
     */
    private void load() {
        Path current=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.displayPath(com.goofy.goofyaddons.features.account.AccountStorage.PROFIT);
        if (loaded && java.util.Objects.equals(current,path)) return;
        if (loaded && path!=null) save(); // Keep the previous profile's session timer.
        loaded=true;path=current;error=null;executionError=null;
        ledger=new ProfitLedger();execution=new ExecutionLedger();
        lastTick=0;previouslyActive=false;
        if (path==null) {executionPath=null;history=null;return;}
        executionPath=path.resolveSibling(com.goofy.goofyaddons.features.account.AccountStorage.EXECUTION);
        history=new TradeHistory(path.resolveSibling(com.goofy.goofyaddons.features.account.AccountStorage.TRANSACTIONS));
        try { execution=ExecutionLedger.read(executionPath); } catch(Exception bad) { executionError="Execution history unreadable; preserved"; Diagnostics.failure("execution.load_failed",bad); }
        try { ledger=ProfitLedger.read(path); }
        catch (Exception bad) { Diagnostics.failure("profit.load_failed",bad); error="Profit file unreadable; preserved"; LoggerFactory.getLogger(ProfitTracker.class).error(error,bad); }
        try {
            var replay=history.replay(error==null?ledger:null,executionError==null?execution:null,System.currentTimeMillis());
            if(replay.ledgerChanged()||replay.samplesChanged())Diagnostics.event("WARN","history.replayed",java.util.Map.of(
                    "ledgerEntries",replay.ledgerEntries(),"samples",replay.samples()));
            if(replay.ledgerChanged())save();
            if(replay.samplesChanged())writeExecution();
        } catch(Exception bad) {
            // The snapshots stay usable; only the crash-recovery guarantee is lost until fixed.
            Diagnostics.failure("history.load_failed",bad);
            error="Transaction history unreadable; preserved";
            LoggerFactory.getLogger(ProfitTracker.class).error(error,bad);
        }
    }

    /** Durable first: the change reaches the history before any snapshot is saved. */
    private boolean record(TradeHistory.Entry entry) {
        if (history==null) return false; // No profile: nothing may be counted.
        try { history.append(entry); return true; }
        catch (Exception bad) { reportError(bad); return false; }
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
        try { if (ledger.acquire(id,engine,item,event,units,cost)) { if(!record(TradeHistory.Entry.acquire(System.currentTimeMillis(),event,id,engine,item,units,cost)))return; Diagnostics.event("INFO","trade.acquired",java.util.Map.of("trade",id,"engine",engine,"item",item,"units",units,"cost",cost==null?"unknown":cost)); save(); } }
        catch (RuntimeException bad) { reportError(bad); }
    }
    public void recoverHoldings(String id,String engine,String item,int observedUnits) {
        load();if(error!=null)return;
        try {
            int missing=ledger.missingUnits(id,engine,item,observedUnits);
            String event=java.util.UUID.randomUUID().toString();
            if(missing>0 && ledger.acquire(id,engine,item,event,missing,null)
                    && record(TradeHistory.Entry.acquire(System.currentTimeMillis(),event,id,engine,item,missing,null)))save();
        }
        catch(RuntimeException bad){reportError(bad);}
    }
    public void retire(String id){load();if(executionError!=null || path==null)return;
        try {if(execution.retire(id,System.currentTimeMillis())) {recordSample(execution.latest());execution.write(executionPath);}}
        catch(Exception bad){executionError="Execution history save failed; reporting only";Diagnostics.failure("execution.save_failed",bad);}
    }
    public void sell(String id,String engine,String item,String event,int units,Double proceeds) {
        load(); if (error!=null) return;
        Double cost=ledger.knownCost(id,units);
        try { if (ledger.sell(id,engine,item,event,units,proceeds)) {
            if(!record(TradeHistory.Entry.sell(System.currentTimeMillis(),event,id,engine,item,units,proceeds)))return;
            completeExecution(id,event,units,proceeds,cost==null||proceeds==null?null:proceeds-cost,false); Diagnostics.event("INFO","trade.sold",java.util.Map.of("trade",id,"engine",engine,"item",item,"units",units,"proceeds",proceeds==null?"unknown":proceeds,"cost",cost==null?"unknown":cost,"profit",cost==null||proceeds==null?"unknown":proceeds-cost)); save(); } }
        catch (RuntimeException bad) { reportError(bad); }
    }
    /** Zero recovery value consumes the old cost basis permanently, even if an item appears later. */
    public void writeOff(String id,String engine,String item,String event,int units) {
        load(); if (error!=null) return;
        try { if (ledger.writeOff(id,engine,item,event,units)) {
            if(!record(TradeHistory.Entry.writeOff(System.currentTimeMillis(),event,id,engine,item,units)))return;
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
        if(executionError!=null || executionPath==null)return;
        try {var sample=execution.complete(id,event,units,proceeds,profit,System.currentTimeMillis(),lost);recordSample(sample);execution.write(executionPath);}
        catch(Exception bad){executionError="Execution history save failed; reporting only";Diagnostics.failure("execution.save_failed",bad);}
    }
    private void recordSample(ExecutionLedger.Sample sample) throws Exception {
        if(sample!=null && history!=null)history.append(TradeHistory.Entry.sample(System.currentTimeMillis(),sample));
    }
    private void writeExecution() {
        if(executionError!=null || executionPath==null)return;
        try {execution.write(executionPath);}
        catch(Exception bad){executionError="Execution history save failed; reporting only";Diagnostics.failure("execution.save_failed",bad);}
    }
    public ProfitLedger.Summary summary() { load(); return ledger.summary(); }
    public Double knownCost(String id,int units) {load();return error==null?ledger.knownCost(id,units):null;}
    public Double openCost(String id) {load();return error==null?ledger.openCost(id):null;}
    public String error() { load(); return error; }
    public boolean resetSession() {
        load(); if (error!=null) return false;
        ledger.resetSession(); save(); return error==null;
    }
    private void save() {
        if (error!=null || path==null) return;
        try { ledger.write(path); lastSave=System.nanoTime()/1000000; }
        catch (Exception bad) { reportError(bad); return; }
        // Set the history aside only when both snapshots hold everything in it.
        if (executionError==null) {
            try { if (history.large()) { execution.write(executionPath); history.rotate(); } }
            catch (Exception bad) { Diagnostics.failure("history.rotate_failed",bad); }
        }
    }
    private void reportError(Exception bad) {
        Diagnostics.failure("profit.persistence_failed",bad);
        error="Profit tracking error; stats incomplete";
        LoggerFactory.getLogger(ProfitTracker.class).error(error,bad);
    }
}
