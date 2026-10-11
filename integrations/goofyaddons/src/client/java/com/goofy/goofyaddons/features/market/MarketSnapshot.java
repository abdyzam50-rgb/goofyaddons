package com.goofy.goofyaddons.features.market;

import com.google.gson.*;
import java.util.*;

/**
 * A compact copy of the order books behind a decision: the top levels on each side, weekly
 * movement and whether the bid is fillable. Exports and selection events carry this so a bad
 * pick can be explained from the prices it actually saw.
 */
public final class MarketSnapshot {
    private MarketSnapshot() {}
    public static final int LEVELS=3;

    /** Products from a Hypixel market response, or empty when the response or a product is missing. */
    public static Map<String,Object> of(JsonObject market,Collection<String> productIds,long now) {
        var result=new LinkedHashMap<String,Object>();
        if(market==null)return result;
        if(market.has("lastUpdated")){long at=market.get("lastUpdated").getAsLong();result.put("marketAt",at);result.put("marketAgeMs",now-at);}
        var products=market.has("products")&&market.get("products").isJsonObject()?market.getAsJsonObject("products"):null;
        var rows=new TreeMap<String,Object>();
        if(products!=null)for(String id:new TreeSet<>(productIds)) {
            var element=products.get(id);
            if(element==null||!element.isJsonObject()){rows.put(id,Map.of("missing",true));continue;}
            rows.put(id,product(element.getAsJsonObject()));
        }
        result.put("products",rows);
        return result;
    }
    static Map<String,Object> product(JsonObject product) {
        var row=new LinkedHashMap<String,Object>();
        try {
            double ask=BazaarQuotes.bestAsk(product),bid=BazaarQuotes.bestBid(product);
            row.put("bestAsk",ask);row.put("bestBid",bid);
            if(ask>0&&bid>0)row.put("spreadPercent",Math.round((ask-bid)/ask*10000)/100.0);
            row.put("orderableBid",BazaarQuotes.orderableBid(product));
        }catch(RuntimeException malformed){row.put("malformed",malformed.getClass().getSimpleName());}
        row.put("asks",levels(product,BazaarQuotes.ASKS));row.put("bids",levels(product,BazaarQuotes.BIDS));
        if(product.has("quick_status")&&product.get("quick_status").isJsonObject()) {
            var quick=product.getAsJsonObject("quick_status");
            for(String key:List.of("buyMovingWeek","sellMovingWeek"))if(quick.has(key))row.put(key,quick.get(key).getAsDouble());
        }
        return row;
    }
    private static List<Map<String,Object>> levels(JsonObject product,String side) {
        var out=new ArrayList<Map<String,Object>>();
        if(!product.has(side)||!product.get(side).isJsonArray())return out;
        for(var level:product.getAsJsonArray(side)) {
            if(out.size()==LEVELS)break;
            try{var o=level.getAsJsonObject();out.add(Map.of("price",o.get("pricePerUnit").getAsDouble(),"amount",o.get("amount").getAsLong(),"orders",o.has("orders")?o.get("orders").getAsInt():-1));}
            catch(RuntimeException malformed){out.add(Map.of("malformed",true));}
        }
        return out;
    }
}
