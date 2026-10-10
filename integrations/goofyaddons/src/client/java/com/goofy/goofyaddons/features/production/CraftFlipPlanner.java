package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.access.RouteRequirements;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.google.gson.*;
import java.util.*;

/** Recompute whole craft batches from verified grids, depth, fees, capacity and account evidence. */
final class CraftFlipPlanner {
    record Route(ProductionRecipe recipe,int batches,String venue,double capital,double profit,long binPrice,
            double fee,double score,String reason) {
        boolean eligible(){return reason==null;}
        Map<String,Object> describe(RecipeCatalog catalog) {
            var row=new LinkedHashMap<String,Object>();
            row.put("recipeKey",recipe.key());row.put("output",recipe.outputId());row.put("name",catalog.name(recipe.outputId()));
            row.put("batches",batches);row.put("outputUnits",recipe.outputCount()*batches);row.put("venue",venue);
            row.put("capital",capital);row.put("profit",profit);row.put("rankingScore",score);row.put("eligible",eligible());
            row.put("reason",reason);row.put("requirement",recipe.requirement());row.put("ingredients",recipe.ingredients());
            return row;
        }
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now) {
        if(market==null||!market.has("lastUpdated")||!TradingSafety.fresh(market.get("lastUpdated").getAsLong(),now)
                ||menu==null||!menu.cursorEmpty()||!Double.isFinite(budget)||budget<=0||!Double.isFinite(minimumProfit)||minimumProfit<0
                ||!Double.isFinite(bazaarTax)||bazaarTax<0||bazaarTax>=100||maxBatches<1||maxBatches>16||!Set.of("BOTH","BAZAAR","AH").contains(venue))return List.of();
        var products=market.getAsJsonObject("products");if(products==null)return List.of();
        var auctions=new HashMap<String,JsonObject>();
        if(ah!=null&&ah.has("rows"))for(var value:ah.getAsJsonArray("rows"))try {
            var row=value.getAsJsonObject();auctions.put(row.get("item").getAsString(),row);
        }catch(RuntimeException ignored){}
        var best=new HashMap<String,Route>();
        for(var recipe:catalog.recipes()) {
            if(recipe.kind()!=ProductionRecipe.Kind.CRAFT)continue;
            boolean bz=products.has(recipe.outputId());String sale=bz?"BAZAAR":"AH";
            if(!venue.equals("BOTH")&&!venue.equals(sale))continue;
            if(!bz&&!auctions.containsKey(recipe.outputId()))continue;
            String requirement=RouteRequirements.craft(recipe.requirement(),skills,unlocks);
            int limit=bz?Math.min(16,maxBatches):1;
            for(int batches=1;batches<=limit;batches++)try {
                var quantities=new TreeMap<String,Integer>();
                final int count=batches;recipe.ingredients().forEach((id,n)->quantities.put(id,Math.multiplyExact(n,count)));
                var preparation=IngredientPreparation.plan(catalog,quantities,Map.of());
                String reason=requirement;
                if(menu.countInInventory(recipe.outputId())>0)reason="Output already held; move it before automatic production";
                if(blocked.contains(recipe.outputId())||preparation.products().stream().anyMatch(blocked::contains))reason="Product belongs to another position or unresolved production job";
                String conflict=CompactorClearance.unreadable(menu);if(conflict!=null)reason=conflict;
                int slots=1;double cost=0;boolean priced=true;
                for(var entry:preparation.purchases().entrySet()) {
                    var product=products.get(entry.getKey());
                    Double amount=product!=null&&product.isJsonObject()?ProductionPlanner.instantBuyCost(product.getAsJsonObject(),entry.getValue()):null;
                    if(amount==null){priced=false;break;}
                    cost+=amount;slots+=(entry.getValue()+63)/64;
                }
                if(!priced||cost<=0)continue;
                for(var craft:preparation.crafts()) {
                    var intermediate=catalog.forOutput(craft.output()).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
                    slots=Math.max(slots,1+(intermediate.outputCount()*craft.batches()+63)/64+
                        intermediate.ingredients().entrySet().stream().mapToInt(e->(e.getValue()*craft.batches()+63)/64).sum());
                }
                if(slots>menu.emptyInventorySlots())reason="Insufficient empty inventory slots for inputs, intermediate crafts and output";
                double fee=0,net,liquidity;long price=ProductionRun.SELL_ON_BAZAAR;
                if(bz) {
                    var product=products.getAsJsonObject(recipe.outputId());
                    Double gross=ProductionPlanner.instantSellValue(product,recipe.outputCount()*batches);
                    if(gross==null)continue;
                    net=gross*ProductionRun.SALE_FLOOR*(1-bazaarTax/100);
                    var quick=product.getAsJsonObject("quick_status");
                    double week=quick==null?0:Math.min(quick.get("buyMovingWeek").getAsDouble(),quick.get("sellMovingWeek").getAsDouble());
                    if(!Double.isFinite(week)||week<=0)continue;
                    if(recipe.outputCount()*batches>week/7*0.05)reason="Batch exceeds 5% of observed daily market volume";
                    liquidity=Math.min(1,week/168/Math.max(1,recipe.outputCount()*batches));
                } else {
                    if(recipe.outputCount()!=1)continue; // Existing BIN executor lists one exact item.
                    var row=auctions.get(recipe.outputId());var quote=row.getAsJsonObject("quote");
                    if(quote==null||now-quote.get("fetchedAt").getAsLong()>60000||quote.get("fetchedAt").getAsLong()>now+5000)continue;
                    price=AuctionPricing.listingPrice(AuctionPricing.parse(quote,recipe.outputId(),now));
                    fee=ProductionPlanner.listingFeeLimit(price);
                    // Price can vary within the GUI validation band; include conservative claim tax too.
                    net=price*0.90*0.965;
                    if(reason==null)reason="AH sale/expiry/claim reconciliation is not implemented; use production test for one verified listing";
                    double volume=row.get("volume").getAsDouble();if(!Double.isFinite(volume)||volume<=2)continue;
                    liquidity=Math.min(1,Math.log1p(volume)/Math.log(101));
                }
                double capital=cost*1.03+fee,profit=net-capital;
                if(!Double.isFinite(profit)||profit<minimumProfit||profit<=0)continue;
                if(capital>budget)reason="Whole batch exceeds spendable budget";
                double seconds=60+preparation.purchases().size()*20+preparation.crafts().size()*15+batches*3;
                double score=profit/seconds*liquidity;
                var route=new Route(recipe,batches,sale,capital,profit,price,fee,score,reason);
                var old=best.get(recipe.outputId());
                if(old==null||route.eligible()&&!old.eligible()||route.eligible()==old.eligible()&&route.score()>old.score())best.put(recipe.outputId(),route);
            }catch(RuntimeException malformed){/* Invalid quotes/unsupported grids never authorize a route. */}
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Route::score).reversed().thenComparing(r->r.recipe().key())).toList();
    }
}
