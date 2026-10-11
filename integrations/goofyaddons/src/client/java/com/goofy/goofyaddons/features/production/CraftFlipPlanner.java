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
        var books=new Books(products);
        String compactorConflict=CompactorClearance.unreadable(menu);
        int freeSlots=Math.max(0,menu.emptyInventorySlots()-reservedSlots);
        var activity=new HashMap<String,Double>();
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
                int outputSlots=inventorySlotsForProduct(recipe.outputId(),Math.multiplyExact(recipe.outputCount(),batches));
                int slots=outputSlots;double cost=0,orderWait=0;boolean priced=true;
                for(var entry:preparation.purchases().entrySet()) {
                    var product=books.element(entry.getKey());
                    Double amount=product!=null&&product.isJsonObject()?books.buy(strategy,entry.getKey(),entry.getValue()):null;
                    if(amount==null){priced=false;break;}
                    cost+=amount;slots+=inventorySlotsForProduct(entry.getKey(),entry.getValue());
                    if(!strategy.instantBuy) {
                        double rate=activity.computeIfAbsent(entry.getKey(),id->hourlyActivity(product.getAsJsonObject()))*0.05;
                        if(rate<=0){reason="Input order activity unavailable";}
                        // The funded ingredient basket is submitted before any fill wait;
                        // its orders fill concurrently, so the slowest input sets that wait.
                        else orderWait=Math.max(orderWait,entry.getValue()/rate*3600);
                    }
                }
                if(!priced||cost<=0){exclude(excluded,excludedByOutput,recipe.outputId(),"Missing input prices or insufficient buy depth");continue;}
                for(var craft:preparation.crafts()) {
                    var intermediate=catalog.forOutput(craft.output()).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
                    slots=Math.max(slots,outputSlots+inventorySlotsForProduct(intermediate.outputId(),Math.multiplyExact(intermediate.outputCount(),craft.batches()))+
                        intermediate.ingredients().entrySet().stream().mapToInt(e->inventorySlotsForProduct(e.getKey(),Math.multiplyExact(e.getValue(),craft.batches()))).sum());
                }
                if(slots>freeSlots)reason="Insufficient empty inventory slots for inputs, intermediate crafts and output";
                double fee=0,net,liquidity;long price=ProductionRun.SELL_ON_BAZAAR;
                if(bz) {
                    var product=books.object(recipe.outputId());
                    Double gross=books.sell(strategy,recipe.outputId(),recipe.outputCount()*batches);
                    if(gross==null){exclude(excluded,excludedByOutput,recipe.outputId(),"Insufficient Bazaar sell depth");continue;}
                    net=gross*ProductionRun.SALE_FLOOR*(1-bazaarTax/100);
                    var quick=product.getAsJsonObject("quick_status");
                    double week=quick==null?0:Math.min(quick.get("buyMovingWeek").getAsDouble(),quick.get("sellMovingWeek").getAsDouble());
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
    /** Books can be unstackable; reserve every unit until an actual stack proves otherwise. */
    static int inventorySlotsForProduct(String id,int units) {
        if(units<0)throw new IllegalArgumentException("Negative inventory quantity");
        return id!=null && BOOK_IDS.computeIfAbsent(id,key->BOOK.matcher(key).matches())?units:Math.toIntExact((units+63L)/64);
    }
    /**
     * Order books parsed and sorted once per ranking. Ranking walks the same books for every
     * strategy and batch size; re-reading Gson numbers and re-sorting each time cost most of a
     * second on the client thread. Arithmetic and validation match ProductionPlanner exactly.
     */
    private static final class Books {
        private record Level(double price,double amount) {}
        private final Map<String,JsonElement> products=new HashMap<>();
        private final Map<String,Optional<List<Level>>> asks=new HashMap<>(),bids=new HashMap<>();
        Books(JsonObject products){for(var entry:products.entrySet())this.products.put(entry.getKey(),entry.getValue());}
        boolean has(String id){return products.containsKey(id);}
        JsonElement element(String id){return products.get(id);}
        JsonObject object(String id){return (JsonObject)products.get(id);}
        Double buy(BazaarStrategy strategy,String id,int units) {
            var product=object(id);
            if(product==null||units<1)return null;
            if(!strategy.instantBuy)return strategy.buy(product,units);
            Double book=walk(ladder(asks,id,"buy_summary",false),units);
            return book==null?null:book*ProductionPlanner.INSTANT_BUY_FEE;
        }
        Double sell(BazaarStrategy strategy,String id,int units) {
            var product=object(id);
            if(product==null||units<1)return null;
            if(!strategy.instantSell)return strategy.sell(product,units);
            return walk(ladder(bids,id,"sell_summary",true),units);
        }
        private Optional<List<Level>> ladder(Map<String,Optional<List<Level>>> cache,String id,String side,boolean descending) {
            return cache.computeIfAbsent(id,key->{
                var product=object(key);
                if(product==null||!product.has(side))return Optional.empty();
                try {
                    var levels=new ArrayList<Level>();
                    for(var value:product.getAsJsonArray(side)){var level=value.getAsJsonObject();levels.add(new Level(level.get("pricePerUnit").getAsDouble(),level.get("amount").getAsDouble()));}
                    Comparator<Level> order=Comparator.comparingDouble(Level::price);
                    levels.sort(descending?order.reversed():order);
                    return Optional.of(List.copyOf(levels));
                }catch(RuntimeException invalid){return Optional.empty();}
            });
        }
        private static Double walk(Optional<List<Level>> ladder,int units) {
            if(ladder.isEmpty())return null;
            double total=0;int remaining=units;
            for(var level:ladder.get()){double price=level.price(),amount=level.amount();
                if(!Double.isFinite(price) || price<=0 || !Double.isFinite(amount) || amount<0)return null;
                int take=(int)Math.min(remaining,Math.floor(amount));total+=take*price;remaining-=take;if(remaining==0)return Double.isFinite(total)?total:null;
            }
            return null;
        }
    }
    private static double hourlyActivity(JsonObject product) {
        var quick=product.getAsJsonObject("quick_status");
        if(quick==null || !quick.has("buyMovingWeek") || !quick.has("sellMovingWeek"))return 0;
        double week=Math.min(quick.get("buyMovingWeek").getAsDouble(),quick.get("sellMovingWeek").getAsDouble());
        return Double.isFinite(week)&&week>0?week/168:0;
    }
    private static void exclude(Map<String,Integer> excluded,Map<String,Map<String,Integer>> byOutput,String output,String reason) {
        exclude(excluded,reason);byOutput.computeIfAbsent(output,id->new LinkedHashMap<>()).merge(reason,1,Integer::sum);
    }
    private static void exclude(Map<String,Integer> excluded,String reason) { excluded.merge(reason,1,Integer::sum); }
}
