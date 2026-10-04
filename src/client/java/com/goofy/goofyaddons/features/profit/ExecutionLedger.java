package com.goofy.goofyaddons.features.profit;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Observed whole-position durations, not exact server fill timestamps. */
public final class ExecutionLedger {
    public record Sample(String eventId,String engine,String inputId,String outputId,int inputUnits,int batch,
                         long completedAt,long observedMillis,Double proceeds,Double profit,boolean eligible,boolean censored,Double expectedProfit) {}
    public record Active(String tradeId,String engine,String inputId,String outputId,int inputUnits,int batch,
                         long startedAt,long observedAt,long observedMillis) {}
    private record Open(String engine,String inputId,String outputId,int inputUnits,int batch,long startedAt,boolean interrupted,Double expectedProfit) {}
    private final Map<String,Open> open=new HashMap<>();
    private final List<Sample> samples=new ArrayList<>();
    public void begin(String trade,String engine,String input,String output,int units,int batch,long startedAt) {
        begin(trade,engine,input,output,units,batch,startedAt,null);
    }
    public void begin(String trade,String engine,String input,String output,int units,int batch,long startedAt,Double expectedProfit) {
        if(expectedProfit!=null && (!Double.isFinite(expectedProfit)||expectedProfit<=0))expectedProfit=null;
        if(trade==null || !(engine.equals("books")||engine.equals("general")) || !input.matches("[A-Z0-9_]+") || !output.matches("[A-Z0-9_]+") || units<1 || batch<1 || startedAt<=0)return;
        open.putIfAbsent(trade,new Open(engine,input,output,units,batch,startedAt,false,expectedProfit));
    }
    public void interrupt(String id) {open.computeIfPresent(id,(key,o)->new Open(o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,true,o.expectedProfit));}
    public void interrupt() { open.replaceAll((id,o)->new Open(o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,true,o.expectedProfit)); }
    public void complete(String trade,String event,int baseUnits,Double proceeds,Double profit,long now,boolean lost) {
        if(samples.stream().anyMatch(s->s.eventId.equals(event)))return;
        Open o=open.remove(trade);if(o==null)return; // Resumed holdings have no measured start.
        long elapsed=now-o.startedAt;
        samples.add(new Sample(event,o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,now,Math.max(0,elapsed),proceeds,profit,
                !lost&&!o.interrupted&&baseUnits==o.inputUnits&&proceeds!=null&&profit!=null&&elapsed>0&&elapsed<=86400000,false,o.expectedProfit));
        samples.removeIf(s->s.completedAt<now-7*86400000L);
        while(samples.size()>2000)samples.removeFirst();
    }
    /** A deliberately retired route gives a lower bound on normal-cycle duration, not a successful cycle. */
    public boolean retire(String trade,long now) {
        Open o=open.remove(trade);if(o==null || o.interrupted)return false;
        long elapsed=now-o.startedAt;
        if(elapsed<180_000 || elapsed>86400000)return false;
        samples.add(new Sample(trade+":retired-timing",o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,
                now,elapsed,null,null,false,true,o.expectedProfit));
        samples.removeIf(s->s.completedAt<now-7*86400000L);
        while(samples.size()>2000)samples.removeFirst();
        return true;
    }
    public List<Active> active(long now) {
        return open.entrySet().stream().filter(e->!e.getValue().interrupted && now>=e.getValue().startedAt
                && now-e.getValue().startedAt<=86400000).map(e->{var o=e.getValue();
            return new Active(e.getKey(),o.engine,o.inputId,o.outputId,o.inputUnits,o.batch,o.startedAt,now,now-o.startedAt);
        }).sorted(Comparator.comparing(Active::tradeId)).toList();
    }
    public List<Sample> samples() {return List.copyOf(samples);}
    public static ExecutionLedger read(Path path) throws Exception {
        var ledger=new ExecutionLedger();if(!Files.exists(path))return ledger;
        if(Files.size(path)>2*1024*1024)throw new IllegalStateException("Execution history too large");
        Sample[] rows=new Gson().fromJson(Files.readString(path),Sample[].class);
        if(rows==null||rows.length>2000)throw new IllegalStateException("Invalid execution history");
        for(var s:rows) {
            if(s==null||s.eventId==null||s.eventId.isBlank()||s.inputId==null||!s.inputId.matches("[A-Z0-9_]+")||s.outputId==null||!s.outputId.matches("[A-Z0-9_]+")
                ||!("books".equals(s.engine)||"general".equals(s.engine))||s.inputUnits<1||s.batch<1||s.completedAt<=0||s.observedMillis<0
                ||s.proceeds!=null&&(!Double.isFinite(s.proceeds)||s.proceeds<0)||s.censored&&(s.eligible||s.proceeds!=null||s.profit!=null||s.observedMillis<180000||s.observedMillis>86400000)
                ||s.profit!=null&&!Double.isFinite(s.profit)||s.expectedProfit!=null&&(!Double.isFinite(s.expectedProfit)||s.expectedProfit<=0))throw new IllegalStateException("Invalid execution sample");
            ledger.samples.add(s);
        }
        return ledger;
    }
    public void write(Path path) throws Exception {
        Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"execution-",".tmp");
        try {Files.writeString(temp,new Gson().toJson(samples));Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
}
