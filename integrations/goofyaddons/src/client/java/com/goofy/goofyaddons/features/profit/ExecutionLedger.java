package com.goofy.goofyaddons.features.profit;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Observed whole-position durations, not exact server fill timestamps. */
public final class ExecutionLedger {
    public record Sample(String eventId,String engine,String inputId,String outputId,int inputUnits,int batch,
                         long completedAt,long observedMillis,Double proceeds,Double profit,boolean eligible,boolean censored,Double expectedProfit,Forecast forecast,String buyMode,String sellMode,String outcome) {
        public Sample(String eventId,String engine,String inputId,String outputId,int inputUnits,int batch,long completedAt,long observedMillis,
                      Double proceeds,Double profit,boolean eligible,boolean censored,Double expectedProfit,Forecast forecast) {
            this(eventId,engine,inputId,outputId,inputUnits,batch,completedAt,observedMillis,proceeds,profit,eligible,censored,expectedProfit,forecast,
                    "order","offer",eligible?"COMPLETED":censored?"RETIRED":"UNKNOWN");
        }
    }
    public record Forecast(double cycleSeconds,double inputPerDay,double outputPerDay) {}
    public record Active(String tradeId,String engine,String inputId,String outputId,int inputUnits,int batch,
                         long startedAt,long observedAt,long observedMillis,String buyMode,String sellMode) {}
    private record Open(String engine,String inputId,String outputId,int inputUnits,int batch,long startedAt,boolean interrupted,Double expectedProfit,Forecast forecast,String buyMode,String sellMode) {}
    private final Map<String,Open> open=new HashMap<>();
    private final List<Sample> samples=new ArrayList<>();
    private static final class Partial {
        int units;double proceeds,profit;boolean known=true;
        final Set<String> events=new HashSet<>();
    }
    private final Map<String,Partial> partials=new HashMap<>();
    public void begin(String trade,String engine,String input,String output,int units,int batch,long startedAt) {
        begin(trade,engine,input,output,units,batch,startedAt,null);
    }
    public void begin(String trade,String engine,String input,String output,int units,int batch,long startedAt,Double expectedProfit) {
        begin(trade,engine,input,output,units,batch,startedAt,expectedProfit,null);
    }
    public void begin(String trade,String engine,String input,String output,int units,int batch,long startedAt,Double expectedProfit,Forecast forecast) {
        if(forecast!=null && !valid(forecast))forecast=null;
        if(expectedProfit!=null && (!Double.isFinite(expectedProfit)||expectedProfit<=0))expectedProfit=null;
        if(trade==null || !(engine.equals("books")||engine.equals("general")) || !input.matches("[A-Z0-9_]+") || !output.matches("[A-Z0-9_]+") || units<1 || batch<1 || startedAt<=0)return;
        open.putIfAbsent(trade,new Open(engine,input,output,units,batch,startedAt,false,expectedProfit,forecast,"order","offer"));
    }
    public void strategy(String id,String buy,String sell) {
        if(!Set.of("order","instant").contains(buy)||!Set.of("offer","instant").contains(sell))throw new IllegalArgumentException("Invalid execution strategy");
        open.computeIfPresent(id,(key,o)->new Open(o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,o.interrupted,o.expectedProfit,o.forecast,buy,sell));
    }
    /** Failure evidence has no invented proceeds and never changes the retained position. */
    public Sample failure(String trade,long now) {
        Open o=open.get(trade);String event=trade+":pipeline-failure";
        if(o==null || samples.stream().anyMatch(s->s.eventId.equals(event)))return null;
        var sample=new Sample(event,o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,now,Math.max(0,now-o.startedAt),
                null,null,false,false,o.expectedProfit,o.forecast,o.buyMode,o.sellMode,"PIPELINE_FAILURE");
        samples.add(sample);while(samples.size()>2000)samples.removeFirst();interrupt(trade);return sample;
    }
    public void interrupt(String id) {open.computeIfPresent(id,(key,o)->new Open(o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,true,o.expectedProfit,o.forecast,o.buyMode,o.sellMode));}
    public void interrupt() { open.replaceAll((id,o)->new Open(o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,true,o.expectedProfit,o.forecast,o.buyMode,o.sellMode)); }
    /** Returns the completed cycle's sample, or null while the cycle is still partial or unmeasured. */
    public Sample complete(String trade,String event,int baseUnits,Double proceeds,Double profit,long now,boolean lost) {
        if(samples.stream().anyMatch(s->s.eventId.equals(event)))return null;
        Open o=open.get(trade);if(o==null)return null; // Resumed holdings have no measured start.
        Partial p=partials.computeIfAbsent(trade,ignored->new Partial());
        if(!p.events.add(event))return null;
        p.units+=baseUnits;p.known&=proceeds!=null&&profit!=null;
        if(proceeds!=null)p.proceeds+=proceeds;if(profit!=null)p.profit+=profit;
        if(!lost && p.units<o.inputUnits)return null; // Partial claims are not completed cycles.
        open.remove(trade);partials.remove(trade);
        long elapsed=now-o.startedAt;
        var sample=new Sample(event,o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,now,Math.max(0,elapsed),p.known?p.proceeds:null,p.known?p.profit:null,
                !lost&&!o.interrupted&&p.units==o.inputUnits&&p.known&&elapsed>0&&elapsed<=86400000,false,o.expectedProfit,o.forecast,o.buyMode,o.sellMode,lost?"LOSS":"COMPLETED");
        samples.add(sample);
        samples.removeIf(s->s.completedAt<now-7*86400000L);
        while(samples.size()>2000)samples.removeFirst();
        return sample;
    }

    /**
     * Adds a sample recorded in the transaction history but missing from the saved file,
     * after a crash between the two writes. False when it is already present or invalid.
     */
    public boolean restore(Sample sample,long now) {
        if(sample==null || samples.stream().anyMatch(s->s.eventId.equals(sample.eventId)))return false;
        if(!validSample(sample) || sample.completedAt<now-7*86400000L)return false;
        samples.add(sample);
        samples.sort(Comparator.comparingLong(Sample::completedAt));
        while(samples.size()>2000)samples.removeFirst();
        return samples.contains(sample);
    }

    /** The most recently added sample, or null. */
    public Sample latest() { return samples.isEmpty()?null:samples.getLast(); }
    /** A deliberately retired route gives a lower bound on normal-cycle duration, not a successful cycle. */
    public boolean retire(String trade,long now) {
        partials.remove(trade);
        Open o=open.remove(trade);if(o==null || o.interrupted)return false;
        long elapsed=now-o.startedAt;
        if(elapsed<1000 || elapsed>86400000)return false;
        samples.add(new Sample(trade+":retired-timing",o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,
                now,elapsed,null,null,false,true,o.expectedProfit,o.forecast,o.buyMode,o.sellMode,"RETIRED"));
        samples.removeIf(s->s.completedAt<now-7*86400000L);
        while(samples.size()>2000)samples.removeFirst();
        return true;
    }
    public List<Active> active(long now) {
        return open.entrySet().stream().filter(e->!e.getValue().interrupted && now>=e.getValue().startedAt
                && now-e.getValue().startedAt<=86400000).map(e->{var o=e.getValue();
            return new Active(e.getKey(),o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,now,now-o.startedAt,o.buyMode,o.sellMode);
        }).sorted(Comparator.comparing(Active::tradeId)).toList();
    }
    public List<Sample> samples() {return List.copyOf(samples);}
    public int clearCompletedThrough(long cutoff) {
        int before=samples.size();samples.removeIf(s->s.completedAt()<=cutoff);return before-samples.size();
    }
    public static ExecutionLedger read(Path path) throws Exception {
        var ledger=new ExecutionLedger();if(!Files.exists(path))return ledger;
        if(Files.size(path)>2*1024*1024)throw new IllegalStateException("Execution history too large");
        Sample[] rows=new Gson().fromJson(Files.readString(path),Sample[].class);
        if(rows==null||rows.length>2000)throw new IllegalStateException("Invalid execution history");
        for(var s:rows) {
            if(!validSample(s))throw new IllegalStateException("Invalid execution sample");
            ledger.samples.add(s);
        }
        return ledger;
    }
    private static boolean validSample(Sample s) {
            if(s==null||s.eventId==null||s.eventId.isBlank()||s.inputId==null||!s.inputId.matches("[A-Z0-9_]+")||s.outputId==null||!s.outputId.matches("[A-Z0-9_]+")
                ||s.buyMode!=null&&!Set.of("order","instant").contains(s.buyMode)||s.sellMode!=null&&!Set.of("offer","instant").contains(s.sellMode)
                ||s.outcome!=null&&!Set.of("COMPLETED","RETIRED","LOSS","UNKNOWN","PIPELINE_FAILURE").contains(s.outcome)
                ||!("books".equals(s.engine)||"general".equals(s.engine))||s.inputUnits<1||s.batch<1||s.completedAt<=0||s.observedMillis<0
                ||s.proceeds!=null&&(!Double.isFinite(s.proceeds)||s.proceeds<0)||s.censored&&(s.eligible||s.proceeds!=null||s.profit!=null||s.observedMillis<1000||s.observedMillis>86400000)
                ||s.profit!=null&&!Double.isFinite(s.profit)||s.expectedProfit!=null&&(!Double.isFinite(s.expectedProfit)||s.expectedProfit<=0))return false;
            return s.forecast==null || valid(s.forecast);
    }
    private static boolean valid(Forecast f) {
        return Double.isFinite(f.cycleSeconds)&&f.cycleSeconds>0&&f.cycleSeconds<=30*86400
                &&Double.isFinite(f.inputPerDay)&&f.inputPerDay>0&&f.inputPerDay<=1e15
                &&Double.isFinite(f.outputPerDay)&&f.outputPerDay>0&&f.outputPerDay<=1e15;
    }
    public void write(Path path) throws Exception {
        com.goofy.goofyaddons.storage.AtomicFiles.replace(path,new Gson().toJson(samples),"execution-");
    }
}
