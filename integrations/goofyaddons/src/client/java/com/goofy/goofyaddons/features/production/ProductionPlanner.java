package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import com.goofy.goofyaddons.features.TradingSafety;
import java.util.*;

/** Production purchase budgets and BIN fee ceilings; Bazaar quotes come from BazaarQuotes. */
public final class ProductionPlanner {
    public record Candidate(ProductionRecipe recipe,double inputCost,double expectedNetProceeds,double expectedProfit,
            boolean executable,String reason) {}
    private ProductionPlanner() {}
    /** Coin arithmetic may differ by a double rounding bit; reject invalid values and real overruns. */
    static boolean exceedsFunds(double required,double available) {
        return !Double.isFinite(required)||required<0||!Double.isFinite(available)||available<0
            || required>available && required-available>0.000001;
    }

    /** Complete remaining purchase basket; no partial spending when later inputs cannot be funded. */
    public record PurchaseQuote(Map<String,Double> limits,double total,String blockedProduct) {
        public boolean complete(){return blockedProduct==null;}
    }
    public static PurchaseQuote purchaseQuote(Map<String,Integer> purchases,java.util.function.BiFunction<String,Integer,Double> quote) {
        var limits=new TreeMap<String,Double>();double total=0;
        for(var entry:new TreeMap<>(purchases).entrySet()) {
            Double cost=quote.apply(entry.getKey(),entry.getValue());
            if(entry.getValue()<=0||cost==null||!Double.isFinite(cost)||cost<=0||!Double.isFinite(cost*1.03+total))
                return new PurchaseQuote(Map.of(),0,entry.getKey());
            double limit=cost*1.03;limits.put(entry.getKey(),limit);total+=limit;
        }
        return new PurchaseQuote(Map.copyOf(limits),total,null);
    }

    /**
     * A ceiling for the BIN listing fee when the player gave none: the Auction House rate for the
     * price (1% below 10M, 2% below 100M, 2.5% above) plus the largest duration fee. A quote above
     * it sends the listing to review; nothing is published.
     */
    public static long listingFeeLimit(long price) {
        if(price<1)throw new IllegalArgumentException("BIN price must be positive");
        double rate=price<10_000_000L?0.01:price<100_000_000L?0.02:0.025;
        return (long)Math.ceil(price*rate)+1_200;
    }
    public static List<Candidate> bazaar(JsonObject market,List<ProductionRecipe> recipes,long now,double capital,double tax,
            double minimumProfit,int inventorySlots,Set<String> blocked,Set<String> verifiedUnlocks) {
        if(recipes==null || blocked==null || verifiedUnlocks==null || !Double.isFinite(minimumProfit) || minimumProfit<0
                || market==null || !market.has("lastUpdated") || !TradingSafety.fresh(market.get("lastUpdated").getAsLong(),now)
                || !Double.isFinite(capital) || capital<0 || !Double.isFinite(tax) || tax<0 || tax>=100 || inventorySlots<0)return List.of();
        var products=market.getAsJsonObject("products");if(products==null)return List.of();
        var result=new ArrayList<Candidate>();
        for(var recipe:recipes) {
            if(recipe.kind()==ProductionRecipe.Kind.KAT || blocked.contains(recipe.outputId()) || recipe.ingredients().keySet().stream().anyMatch(blocked::contains))continue;
            double cost=recipe.coins();int slots=0;boolean complete=true;
            for(var e:recipe.ingredients().entrySet()) {
                Double price=com.goofy.goofyaddons.features.market.BazaarQuotes.instantBuyCost(product(products,e.getKey()),e.getValue());
                if(price==null){complete=false;break;}cost+=price;slots+=(e.getValue()+63)/64;
            }
            if(!complete || cost<=0 || cost>capital || slots+1>inventorySlots)continue;
            var output=product(products,recipe.outputId());
            if(output==null)continue;
            Double offer=topOffer(output);if(offer==null)continue;
            double net=(offer-0.1)*recipe.outputCount()*(1-tax/100),profit=net-cost;
            if(!Double.isFinite(profit) || profit<minimumProfit || profit<=0)continue;
            boolean unlocked=verifiedUnlocks.contains(recipe.key());
            result.add(new Candidate(recipe,cost,net,profit,false,!unlocked?"Account recipe/workstation unlock not yet verified":"Production procurement and sale executor not yet enabled"));
        }
        result.sort(Comparator.comparingDouble(Candidate::expectedProfit).reversed().thenComparing(c->c.recipe().key()));return List.copyOf(result);
    }
    private static JsonObject product(JsonObject products,String id){var value=products.get(id);return value!=null && value.isJsonObject()?value.getAsJsonObject():null;}
    private static Double topOffer(JsonObject product) {
        try {var levels=product.getAsJsonArray("buy_summary");double price=Double.POSITIVE_INFINITY;
            for(var value:levels){double p=value.getAsJsonObject().get("pricePerUnit").getAsDouble();if(!Double.isFinite(p) || p<=0)return null;price=Math.min(price,p);}
            return Double.isFinite(price) && price>0.1?price:null;
        }catch(RuntimeException invalid){return null;}
    }
}
