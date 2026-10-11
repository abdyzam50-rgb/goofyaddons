package com.goofy.goofyaddons.features.market;

import com.google.gson.*;
import java.util.*;

/**
 * The one place that reads Hypixel Bazaar product JSON. Book, general, craft and monitoring
 * pipelines all price through here, so a fee, rounding or validation rule changes once.
 *
 * <p>Hypixel names the sides from the order owner's view: {@code buy_summary} lists sell
 * offers (what an instant buy pays, lowest first) and {@code sell_summary} lists buy orders
 * (what an instant sell receives, highest first). Prefer {@link #bestAsk} and {@link #bestBid}
 * over passing a side name.
 */
public final class BazaarQuotes {
    public static final String ASKS="buy_summary",BIDS="sell_summary";
    /**
     * Hypixel charges more than the order book for an instant buy: every "Instant Buy" quote seen
     * in game totals 4% above its per-unit price times the amount (485.6 for one at 466.9).
     */
    public static final double INSTANT_BUY_FEE=1.04;
    private BazaarQuotes() {}

    /** First listed price on a side, or -1 when missing, non-finite or not positive. */
    public static double topPrice(JsonObject product,String side) {
        JsonArray summary=product.getAsJsonArray(side);
        if(summary==null||summary.isEmpty())return -1;
        double value=summary.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
        return Double.isFinite(value)&&value>0?value:-1;
    }
    /** Lowest sell offer: what one unit costs to instant buy, before the fee. */
    public static double bestAsk(JsonObject product){return topPrice(product,ASKS);}
    /** Highest buy order: what one unit receives when instantly sold. */
    public static double bestBid(JsonObject product){return topPrice(product,BIDS);}

    /** Coins an instant buy of these units costs: the order book walked, plus Hypixel's fee. */
    public static Double instantBuyCost(JsonObject product,int units) {
        Double book=depth(product,ASKS,units,false);
        return book==null?null:book*INSTANT_BUY_FEE;
    }
    /** Coins from instantly selling these units into the buy orders, best bid first, before tax. */
    public static Double instantSellValue(JsonObject product,int units){return depth(product,BIDS,units,true);}

    /** Walks a side in price order; any malformed level reached voids the quote. */
    static Double depth(JsonObject product,String side,int units,boolean descending) {
        if(product==null||units<1||!product.has(side))return null;
        try{return walk(ladder(product,side,descending),units);}catch(RuntimeException invalid){return null;}
    }
    /** A weekly volume field, or 0 when missing or invalid. */
    public static double weeklyVolume(JsonObject quick,String key) {
        if(quick==null||!quick.has(key))return 0;
        double value=quick.get(key).getAsDouble();
        return Double.isFinite(value)?Math.max(0,value):0;
    }
    /** Units that actually traded in both directions over the last week, or 0 when unknown. */
    public static double weeklyFlow(JsonObject product) {
        var quick=product==null?null:product.getAsJsonObject("quick_status");
        return Math.min(weeklyVolume(quick,"sellMovingWeek"),weeklyVolume(quick,"buyMovingWeek"));
    }
    /** Average hourly flow over the last week, or 0 when the product reports none. */
    public static double hourlyActivity(JsonObject product) {
        var quick=product==null?null:product.getAsJsonObject("quick_status");
        if(quick==null||!quick.has("buyMovingWeek")||!quick.has("sellMovingWeek"))return 0;
        double week=Math.min(quick.get("buyMovingWeek").getAsDouble(),quick.get("sellMovingWeek").getAsDouble());
        return Double.isFinite(week)&&week>0?week/168:0;
    }

    private record Level(double price,double amount) {}
    private static List<Level> ladder(JsonObject product,String side,boolean descending) {
        var levels=new ArrayList<Level>();
        for(var value:product.getAsJsonArray(side)){var level=value.getAsJsonObject();levels.add(new Level(level.get("pricePerUnit").getAsDouble(),level.get("amount").getAsDouble()));}
        Comparator<Level> order=Comparator.comparingDouble(Level::price);
        levels.sort(descending?order.reversed():order);
        return List.copyOf(levels);
    }
    private static Double walk(List<Level> ladder,int units) {
        double total=0;int remaining=units;
        for(var level:ladder){double price=level.price(),amount=level.amount();
            if(!Double.isFinite(price)||price<=0||!Double.isFinite(amount)||amount<0)return null;
            int take=(int)Math.min(remaining,Math.floor(amount));total+=take*price;remaining-=take;if(remaining==0)return Double.isFinite(total)?total:null;
        }
        return null;
    }

    /**
     * One market snapshot read for many quotes. Rankings walk the same books for every strategy
     * and batch size; this parses and sorts each side once and answers from memory.
     */
    public static final class Snapshot {
        private final Map<String,JsonElement> products=new HashMap<>();
        private final Map<String,Optional<List<Level>>> asks=new HashMap<>(),bids=new HashMap<>();
        private final Map<String,Double> activity=new HashMap<>();
        public Snapshot(JsonObject products){for(var entry:products.entrySet())this.products.put(entry.getKey(),entry.getValue());}
        public boolean has(String id){return products.containsKey(id);}
        public JsonElement element(String id){return products.get(id);}
        public JsonObject object(String id){return (JsonObject)products.get(id);}
        public Double instantBuyCost(String id,int units) {
            var product=object(id);if(product==null||units<1)return null;
            var book=cached(asks,id,ASKS,false);if(book.isEmpty())return null;
            Double cost=walk(book.get(),units);return cost==null?null:cost*INSTANT_BUY_FEE;
        }
        public Double instantSellValue(String id,int units) {
            var product=object(id);if(product==null||units<1)return null;
            var book=cached(bids,id,BIDS,true);return book.isEmpty()?null:walk(book.get(),units);
        }
        public double hourlyActivity(String id){return activity.computeIfAbsent(id,key->BazaarQuotes.hourlyActivity(object(key)));}
        private Optional<List<Level>> cached(Map<String,Optional<List<Level>>> cache,String id,String side,boolean descending) {
            return cache.computeIfAbsent(id,key->{
                var product=object(key);
                if(product==null||!product.has(side))return Optional.empty();
                try{return Optional.of(ladder(product,side,descending));}catch(RuntimeException invalid){return Optional.empty();}
            });
        }
    }
}
