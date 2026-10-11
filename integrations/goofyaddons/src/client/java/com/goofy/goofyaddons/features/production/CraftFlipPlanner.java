package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.access.RouteRequirements;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.google.gson.*;
import java.util.*;

/** Recompute whole craft batches from verified grids, depth, fees, capacity and account evidence. */
final class CraftFlipPlanner {
    record Route(ProductionRecipe recipe,int batches,String venue,double capital,double profit,long binPrice,
            double fee,double score,String reason,BazaarStrategy strategy,int inventorySlots) {
        boolean eligible(){return reason==null;}
        Map<String,Object> describe(RecipeCatalog catalog) {
            var row=new LinkedHashMap<String,Object>();
            row.put("recipeKey",recipe.key());row.put("output",recipe.outputId());row.put("name",catalog.name(recipe.outputId()));
            row.put("batches",batches);row.put("outputUnits",recipe.outputCount()*batches);row.put("venue",venue);
            row.put("inventorySlots",inventorySlots);row.put("strategy",strategy.name());row.put("buySell",strategy.label());row.put("capital",capital);row.put("profit",profit);row.put("rankingScore",score);row.put("eligible",eligible());
            row.put("estimatedCoinsPerHour",score*3600);row.put("rankingBasis","conservative cycle and observed activity");
            row.put("reason",reason);row.put("requirement",recipe.requirement());row.put("ingredients",recipe.ingredients());
            return row;
        }
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now) {
        return rank(catalog,market,ah,menu,skills,unlocks,blocked,budget,minimumProfit,bazaarTax,maxBatches,venue,now,new LinkedHashMap<>());
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now,Map<String,Integer> excluded) {
        return rank(catalog,market,ah,menu,skills,unlocks,blocked,budget,minimumProfit,bazaarTax,maxBatches,venue,now,excluded,0);
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now,Map<String,Integer> excluded,int reservedSlots) {
        return rank(catalog,market,ah,menu,skills,unlocks,blocked,budget,minimumProfit,bazaarTax,maxBatches,venue,now,excluded,reservedSlots,"AUTO");
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now,Map<String,Integer> excluded,int reservedSlots,String strategySetting) {
        return rank(catalog,market,ah,menu,skills,unlocks,blocked,budget,minimumProfit,bazaarTax,maxBatches,venue,now,excluded,reservedSlots,strategySetting,new LinkedHashMap<>());
    }
    static List<Route> rank(RecipeCatalog catalog,JsonObject market,JsonObject ah,MenuSnapshot menu,
            Map<String,Integer> skills,Map<String,Integer> unlocks,Set<String> blocked,double budget,
            double minimumProfit,double bazaarTax,int maxBatches,String venue,long now,Map<String,Integer> excluded,int reservedSlots,String strategySetting,
            Map<String,Map<String,Integer>> excludedByOutput) {
        excludedByOutput.clear();excluded.clear();
        if(menu==null){exclude(excluded,"Player inventory unavailable");return List.of();}
        if(!menu.cursorEmpty()){exclude(excluded,"Clear the item held on the cursor");return List.of();}
        if(!Double.isFinite(budget)||budget<=0){exclude(excluded,"No observed spendable budget");return List.of();}
        if(market==null||!market.has("lastUpdated")||!TradingSafety.fresh(market.get("lastUpdated").getAsLong(),now)
                ||!Double.isFinite(minimumProfit)||minimumProfit<0
                ||!Double.isFinite(bazaarTax)||bazaarTax<0||bazaarTax>=100||maxBatches<1||maxBatches>64||!Set.of("BOTH","BAZAAR","AH").contains(venue)
                ||!Set.of("AUTO","ORDER_OFFER","INSTANT_OFFER","ORDER_INSTANT","INSTANT_INSTANT").contains(strategySetting)){exclude(excluded,"Fresh Bazaar prices or valid craft settings unavailable");return List.of();}
        var products=market.getAsJsonObject("products");if(products==null){exclude(excluded,"Bazaar products unavailable");return List.of();}
        var auctions=new HashMap<String,JsonObject>();
        if(ah!=null&&ah.has("rows"))for(var value:ah.getAsJsonArray("rows"))try {
            var row=value.getAsJsonObject();auctions.put(row.get("item").getAsString(),row);
        }catch(RuntimeException ignored){}
        var best=new HashMap<String,Route>();
        var books=new com.goofy.goofyaddons.features.market.BazaarQuotes.Snapshot(products);
        String compactorConflict=CompactorClearance.unreadable(menu);
        int freeSlots=Math.max(0,menu.emptyInventorySlots()-reservedSlots);
        for(var recipe:catalog.recipes()) {
            if(recipe.kind()!=ProductionRecipe.Kind.CRAFT)continue;
            boolean bz=books.has(recipe.outputId());String sale=bz?"BAZAAR":"AH";
            if(!venue.equals("BOTH")&&!venue.equals(sale))continue;
            if(!bz&&!auctions.containsKey(recipe.outputId())){exclude(excluded,excludedByOutput,recipe.outputId(),"AH quote unavailable");continue;}
            String requirement=RouteRequirements.craft(recipe.requirement(),skills,unlocks);
            if(requirement==null)requirement=RouteRequirements.product(recipe.outputId(),unlocks);
            if(requirement==null)requirement=RouteRequirements.products(IngredientPreparation.dependencies(catalog,recipe.ingredients().keySet()),unlocks);
            int limit=bz?Math.min(64,maxBatches):1;
            boolean outputHeld=menu.countInInventory(recipe.outputId())>0;
            var preparations=new HashMap<Integer,IngredientPreparation.Plan>();
            for(var strategy:bz?BazaarStrategy.values():new BazaarStrategy[]{BazaarStrategy.INSTANT_INSTANT}) for(int batches=1;batches<=limit;batches++)try {
                if(bz && !strategySetting.equals("AUTO") && !strategy.name().equals(strategySetting))continue;
                // Preparation is independent of buy/sell strategy; expand each batch only once.
                var preparation=preparations.computeIfAbsent(batches,count->{
                    var quantities=new TreeMap<String,Integer>();
                    recipe.ingredients().forEach((id,n)->quantities.put(id,Math.multiplyExact(n,count)));
                    return IngredientPreparation.plan(catalog,quantities,Map.of());
                });
                String reason=requirement;
                if(outputHeld)reason="Output already held; move it before automatic production";
                if(blocked.contains(recipe.outputId())||preparation.products().stream().anyMatch(blocked::contains))reason="Product belongs to another position or unresolved production job";
                if(compactorConflict!=null)reason=compactorConflict;
                int outputSlots=inventorySlotsForProduct(catalog,recipe.outputId(),Math.multiplyExact(recipe.outputCount(),batches));
                int slots=outputSlots;double cost=0,orderWait=0;boolean priced=true;
                for(var entry:preparation.purchases().entrySet()) {
                    var product=books.element(entry.getKey());
                    Double amount=product!=null&&product.isJsonObject()?(strategy.instantBuy?books.instantBuyCost(entry.getKey(),entry.getValue()):strategy.buy(product.getAsJsonObject(),entry.getValue())):null;
                    if(amount==null){priced=false;break;}
                    cost+=amount;slots+=inventorySlotsForProduct(catalog,entry.getKey(),entry.getValue());
                    if(!strategy.instantBuy) {
                        double rate=books.hourlyActivity(entry.getKey())*0.05;
                        if(rate<=0){reason="Input order activity unavailable";}
                        // The funded ingredient basket is submitted before any fill wait;
                        // its orders fill concurrently, so the slowest input sets that wait.
                        else orderWait=Math.max(orderWait,entry.getValue()/rate*3600);
                    }
                }
                if(!priced||cost<=0){exclude(excluded,excludedByOutput,recipe.outputId(),"Missing input prices or insufficient buy depth");continue;}
                for(var craft:preparation.crafts()) {
                    var intermediate=catalog.forOutput(craft.output()).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
                    slots=Math.max(slots,outputSlots+inventorySlotsForProduct(catalog,intermediate.outputId(),Math.multiplyExact(intermediate.outputCount(),craft.batches()))+
                        intermediate.ingredients().entrySet().stream().mapToInt(e->inventorySlotsForProduct(catalog,e.getKey(),Math.multiplyExact(e.getValue(),craft.batches()))).sum());
                }
                if(slots>freeSlots)reason="Insufficient empty inventory slots for inputs, intermediate crafts and output";
                double fee=0,net,liquidity;long price=ProductionRun.SELL_ON_BAZAAR;
                if(bz) {
                    var product=books.object(recipe.outputId());
                    Double gross=strategy.instantSell?books.instantSellValue(recipe.outputId(),recipe.outputCount()*batches):strategy.sell(product,recipe.outputCount()*batches);
                    if(gross==null){exclude(excluded,excludedByOutput,recipe.outputId(),"Insufficient Bazaar sell depth");continue;}
                    net=gross*ProductionRun.SALE_FLOOR*(1-bazaarTax/100);
                    double week=com.goofy.goofyaddons.features.market.BazaarQuotes.weeklyFlow(product);
                    if(!Double.isFinite(week)||week<=0){exclude(excluded,excludedByOutput,recipe.outputId(),"Daily volume unavailable");continue;}
                    if(recipe.outputCount()*batches>week/7*0.05)reason="Batch exceeds 5% of observed daily market volume";
                    liquidity=week/168;
                    if(!strategy.instantSell)orderWait+=recipe.outputCount()*batches/(liquidity*0.05)*3600;
                } else {
                    // Existing BIN executor lists one exact item.
                    if(recipe.outputCount()!=1){exclude(excluded,excludedByOutput,recipe.outputId(),"AH output stacks are not listed automatically");continue;}
                    var row=auctions.get(recipe.outputId());var quote=row.get("quote");
                    var fetched=quote!=null&&quote.isJsonObject()?quote.getAsJsonObject().get("fetchedAt"):null;
                    if(fetched==null||!fetched.isJsonPrimitive()||now-fetched.getAsLong()>60000||fetched.getAsLong()>now+5000){exclude(excluded,excludedByOutput,recipe.outputId(),"AH quote stale");continue;}
                    AuctionPricing.Quote parsed;
                    try{parsed=AuctionPricing.parse(quote.getAsJsonObject(),recipe.outputId(),now);}
                    catch(RuntimeException invalid){exclude(excluded,excludedByOutput,recipe.outputId(),"AH quote invalid");continue;}
                    if(parsed.secondLowest()!=null&&parsed.lowest()*2<parsed.secondLowest()){exclude(excluded,excludedByOutput,recipe.outputId(),"AH lowest BIN looks like an outlier");continue;}
                    price=AuctionPricing.listingPrice(parsed);
                    fee=ProductionPlanner.listingFeeLimit(price);
                    // Price can vary within the GUI validation band; include conservative claim tax too.
                    net=price*0.90*0.965;
                    if(reason==null)reason="AH sale/expiry/claim reconciliation is not implemented; use production test for one verified listing";
                    // Catalog outputs without reported sales arrive with a null volume.
                    var reported=row.get("volume");
                    double volume=reported==null||!reported.isJsonPrimitive()?Double.NaN:reported.getAsDouble();
                    if(!Double.isFinite(volume)||volume<=2){exclude(excluded,excludedByOutput,recipe.outputId(),"AH demand unavailable or too low");continue;}
                    liquidity=Math.min(1,Math.log1p(volume)/Math.log(101));
                }
                double capital=cost*1.03+fee,profit=net-capital;
                if(!Double.isFinite(profit)||profit<minimumProfit||profit<=0){exclude(excluded,excludedByOutput,recipe.outputId(),"Below minimum net profit after fees and price allowance");continue;}
                if(capital>budget)reason="Whole batch exceeds spendable budget";
                double seconds=60+preparation.purchases().size()*20+preparation.crafts().size()*15+batches*3+orderWait;
                if(bz)seconds=Math.max(seconds,recipe.outputCount()*batches/liquidity*3600);
                double score=profit/seconds*(bz?1:liquidity);
                var route=new Route(recipe,batches,sale,capital,profit,price,fee,score,reason,strategy,slots);
                var old=best.get(recipe.outputId());
                if(old==null||route.eligible()&&!old.eligible()||route.eligible()==old.eligible()&&route.score()>old.score())best.put(recipe.outputId(),route);
            }catch(RuntimeException malformed){exclude(excluded,excludedByOutput,recipe.outputId(),"Malformed quote or unsupported recipe: "+malformed.getClass().getSimpleName());}
        }
        return best.values().stream().sorted(Comparator.comparingDouble(Route::score).reversed().thenComparing(r->r.recipe().key())).toList();
    }
    private static final java.util.regex.Pattern BOOK=java.util.regex.Pattern.compile("ENCHANTMENT_[A-Z][A-Z0-9_]*_(?:[1-9][0-9]?|100)");
    private static final Map<String,Boolean> BOOK_IDS=new java.util.concurrent.ConcurrentHashMap<>();
    /** Books and catalogued unstackable items take one slot per unit; everything else stacks to 64. */
    static int inventorySlotsForProduct(RecipeCatalog catalog,String id,int units) {
        if(units<0)throw new IllegalArgumentException("Negative inventory quantity");
        if(catalog.unstackable(id))return units;
        return id!=null && BOOK_IDS.computeIfAbsent(id,key->BOOK.matcher(key).matches())?units:Math.toIntExact((units+63L)/64);
    }
    private static void exclude(Map<String,Integer> excluded,Map<String,Map<String,Integer>> byOutput,String output,String reason) {
        exclude(excluded,reason);byOutput.computeIfAbsent(output,id->new LinkedHashMap<>()).merge(reason,1,Integer::sum);
    }
    private static void exclude(Map<String,Integer> excluded,String reason) { excluded.merge(reason,1,Integer::sum); }
}
