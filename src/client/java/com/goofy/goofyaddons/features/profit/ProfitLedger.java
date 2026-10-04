package com.goofy.goofyaddons.features.profit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Confirmed acquisitions and claims only. Reporting resets never remove open lots. */
public final class ProfitLedger {
    public static final class Lot {
        int units;
        Double cost;
    }
    public static final class Trade {
        String engine;
        String item;
        List<Lot> lots = new ArrayList<>();
    }
    public record Sale(String engine, String item, int units, Double proceeds, Double profit) {}
    public record Summary(double profit, double books, double general, int settlements, int incomplete, long activeMillis) {
        public Double perHour() { return activeMillis >= 60000 && settlements > 0 && incomplete == 0 ? profit * 3600000 / activeMillis : null; }
    }
    private int version = 1;
    private Map<String, Trade> trades = new LinkedHashMap<>();
    private Set<String> eventIds = new HashSet<>();
    private List<Sale> sales = new ArrayList<>();
    private int sessionFirstSale;
    private long activeMillis;

    public boolean acquire(String id, String engine, String item, String event, int units, Double cost) {
        validateIdentity(id, engine, item, event, units);
        validateMoney(cost);
        if (eventIds.contains(event)) return false;
        Trade trade = trades.computeIfAbsent(id, ignored -> { Trade t = new Trade(); t.engine=engine; t.item=item; return t; });
        if (!trade.engine.equals(engine) || !trade.item.equals(item)) throw new IllegalArgumentException("Trade identity changed");
        Lot lot = new Lot(); lot.units=units; lot.cost=cost; trade.lots.add(lot);
        eventIds.add(event);
        return true;
    }

    /** Add only observed units absent from the persisted lots; legacy cost is unknown. */
    public boolean recoverHoldings(String id,String engine,String item,int observedUnits) {
        if(observedUnits<0)throw new IllegalArgumentException("Invalid recovered quantity");
        Trade trade=trades.get(id);
        if(trade!=null && (!trade.engine.equals(engine) || !trade.item.equals(item)))throw new IllegalArgumentException("Trade identity changed");
        int recorded=trade==null?0:trade.lots.stream().mapToInt(lot->lot.units).sum();
        if(recorded>=observedUnits)return false;
        return acquire(id,engine,item,java.util.UUID.randomUUID().toString(),observedUnits-recorded,null);
    }

    public Double knownCost(String id,int units) {
        if(units<=0)return null;
        Trade trade=trades.get(id);if(trade==null)return null;
        int remaining=units;double cost=0;
        for(var lot:trade.lots) {
            int take=Math.min(remaining,lot.units);
            if(take>0 && lot.cost==null)return null;
            if(take>0)cost+=lot.cost*take/lot.units;
            remaining-=take;if(remaining==0)return cost;
        }
        return null;
    }

    public boolean sell(String id, String engine, String item, String event, int units, Double proceeds) {
        validateIdentity(id, engine, item, event, units);
        validateMoney(proceeds);
        if (eventIds.contains(event)) return false;
        Trade trade = trades.get(id);
        if (trade != null && (!trade.engine.equals(engine) || !trade.item.equals(item))) throw new IllegalArgumentException("Trade identity changed");
        int remaining = units;
        double cost = 0;
        boolean known = trade != null && proceeds != null;
        if (trade != null) {
            for (Lot lot : trade.lots) {
                if (remaining == 0) break;
                int take = Math.min(remaining, lot.units);
                if (lot.cost == null) known = false;
                else {
                    double portion = lot.cost * take / lot.units;
                    cost += portion;
                    lot.cost -= portion;
                }
                lot.units -= take;
                remaining -= take;
            }
            trade.lots.removeIf(lot -> lot.units == 0);
        }
        if (remaining != 0) known = false;
        sales.add(new Sale(engine,item,units,proceeds,known ? proceeds-cost : null));
        eventIds.add(event);
        return true;
    }

    public void activeTime(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Invalid timer delta");
        activeMillis = Math.addExact(activeMillis,millis);
    }
    public boolean writeOff(String id,String engine,String item,String event,int units) {
        return sell(id,engine,item,event,units,0.0);
    }
    public void resetSession() { sessionFirstSale=sales.size(); activeMillis=0; }
    public Summary summary() {
        double books=0, general=0;
        int incomplete=0;
        for (Sale sale : sales.subList(sessionFirstSale,sales.size())) {
            if (sale.profit==null) { incomplete++; continue; }
            if (sale.engine.equals("books")) books+=sale.profit; else general+=sale.profit;
        }
        return new Summary(books+general,books,general,sales.size()-sessionFirstSale,incomplete,activeMillis);
    }
    public List<Sale> history() { return List.copyOf(sales); }

    public static ProfitLedger read(Path path) throws Exception {
        if (!Files.exists(path)) return new ProfitLedger();
        String text=Files.readString(path);
        var root=com.google.gson.JsonParser.parseString(text);
        if (!root.isJsonObject() || !root.getAsJsonObject().keySet().containsAll(
                List.of("version","trades","eventIds","sales","sessionFirstSale","activeMillis"))) throw new IllegalStateException("Incomplete profit ledger");
        ProfitLedger ledger=new Gson().fromJson(text,ProfitLedger.class);
        if (ledger==null || ledger.version!=1 || ledger.trades==null || ledger.sales==null || ledger.eventIds==null
                || ledger.sessionFirstSale<0 || ledger.sessionFirstSale>ledger.sales.size() || ledger.activeMillis<0) throw new IllegalStateException("Invalid profit ledger");
        for (var entry : ledger.trades.entrySet()) {
            Trade trade=entry.getValue();
            if (trade==null || trade.lots==null) throw new IllegalStateException("Invalid trade lots");
            validateIdentity(entry.getKey(),trade.engine,trade.item,"validate",1);
            for (Lot lot : trade.lots) { if (lot==null || lot.units<=0) throw new IllegalStateException("Invalid lot"); validateMoney(lot.cost); }
        }
        for (Sale sale : ledger.sales) {
            if (sale==null) throw new IllegalStateException("Invalid sale");
            validateIdentity("validate",sale.engine,sale.item,"validate",sale.units);
            validateMoney(sale.proceeds);
            if (sale.profit!=null && !Double.isFinite(sale.profit)) throw new IllegalStateException("Invalid profit");
        }
        if (ledger.eventIds.stream().anyMatch(id->id==null || id.isBlank())) throw new IllegalStateException("Invalid event identity");
        return ledger;
    }
    public void write(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),"profit-", ".tmp");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(this));
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static void validateIdentity(String id,String engine,String item,String event,int units) {
        if (id==null || id.isBlank() || event==null || event.isBlank() || item==null || item.isBlank()
                || !("books".equals(engine) || "general".equals(engine)) || units<=0) throw new IllegalArgumentException("Invalid ledger event");
    }
    private static void validateMoney(Double value) {
        if (value!=null && (!Double.isFinite(value) || value<0)) throw new IllegalArgumentException("Invalid money amount");
    }
}
