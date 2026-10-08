package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import com.goofy.goofyaddons.features.TradingSafety;
import java.util.*;

/** Instant Bazaar acquisition uses depth, not a top-price multiplied by an arbitrary batch. */
public final class ProductionPlanner {
    public record Candidate(ProductionRecipe recipe,double inputCost,double expectedNetProceeds,double expectedProfit,
            boolean executable,String reason) {}
    private ProductionPlanner() {}
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
                Double price=depth(product(products,e.getKey()),"buy_summary",e.getValue());
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
    static Double depth(JsonObject product,String side,int units) {
        if(product==null || units<1 || !product.has(side))return null;
        try {
            var levels=new ArrayList<JsonObject>();for(var value:product.getAsJsonArray(side))levels.add(value.getAsJsonObject());
            levels.sort(Comparator.comparingDouble(x->x.get("pricePerUnit").getAsDouble()));
            double cost=0;int remaining=units;
            for(var level:levels){double price=level.get("pricePerUnit").getAsDouble(),amount=level.get("amount").getAsDouble();
                if(!Double.isFinite(price) || price<=0 || !Double.isFinite(amount) || amount<0)return null;
                int take=(int)Math.min(remaining,Math.floor(amount));cost+=take*price;remaining-=take;if(remaining==0)return Double.isFinite(cost)?cost:null;
            }
        }catch(RuntimeException invalid){return null;}
        return null;
    }
    private static Double topOffer(JsonObject product) {
        try {var levels=product.getAsJsonArray("buy_summary");double price=Double.POSITIVE_INFINITY;
            for(var value:levels){double p=value.getAsJsonObject().get("pricePerUnit").getAsDouble();if(!Double.isFinite(p) || p<=0)return null;price=Math.min(price,p);}
            return Double.isFinite(price) && price>0.1?price:null;
        }catch(RuntimeException invalid){return null;}
    }
}
