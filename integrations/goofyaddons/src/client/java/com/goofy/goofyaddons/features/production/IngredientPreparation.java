package com.goofy.goofyaddons.features.production;

import java.util.*;

/** Expands basic vanilla intermediates into base purchases and ordered, verified crafts. */
final class IngredientPreparation {
    private static final Set<String> INTERMEDIATES=Set.of("BLAZE_POWDER","STICK","WOOD");
    record Craft(String output,int batches) {}
    record Plan(Map<String,Integer> purchases,List<Craft> crafts,Set<String> products) {}
    private final RecipeCatalog catalog;
    private final Map<String,Integer> available,purchases=new TreeMap<>();
    private final List<Craft> crafts=new ArrayList<>();
    private final Set<String> products=new HashSet<>();
    private IngredientPreparation(RecipeCatalog catalog,Map<String,Integer> held) {this.catalog=catalog;available=new HashMap<>(held);}
    static Plan plan(RecipeCatalog catalog,Map<String,Integer> deficits,Map<String,Integer> held) {
        var planner=new IngredientPreparation(catalog,held);
        // Held final inputs were already subtracted when calculating deficits: reserve them.
        deficits.keySet().forEach(id->planner.available.put(id,0));
        for(var e:new TreeMap<>(deficits).entrySet())planner.require(e.getKey(),e.getValue(),new HashSet<>());
        return new Plan(Collections.unmodifiableMap(planner.purchases),List.copyOf(planner.crafts),Set.copyOf(planner.products));
    }
    static Set<String> dependencies(RecipeCatalog catalog,Collection<String> inputs) {
        var result=new HashSet<>(inputs);var queue=new ArrayDeque<>(inputs);
        while(!queue.isEmpty()) {
            var recipe=intermediate(catalog,queue.remove());
            if(recipe!=null)for(String id:recipe.ingredients().keySet())if(result.add(id))queue.add(id);
        }
        return Set.copyOf(result);
    }
    private static ProductionRecipe intermediate(RecipeCatalog catalog,String id) {
        if(!INTERMEDIATES.contains(id))return null;
        return catalog.forOutput(id).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT && r.requirement().isBlank()).findFirst()
            .orElseThrow(()->new IllegalStateException("Missing basic intermediate recipe: "+id));
    }
    private void require(String id,int units,Set<String> path) {
        products.add(id);int taken=Math.min(units,available.getOrDefault(id,0));available.merge(id,-taken,Integer::sum);units-=taken;
        if(units==0)return;
        var recipe=intermediate(catalog,id);
        if(recipe==null){purchases.merge(id,units,Math::addExact);return;}
        if(!path.add(id))throw new IllegalStateException("Cyclic intermediate recipe: "+id);
        int batches=(int)(((long)units+recipe.outputCount()-1)/recipe.outputCount());
        for(var e:new TreeMap<>(recipe.ingredients()).entrySet())require(e.getKey(),Math.multiplyExact(e.getValue(),batches),path);
        path.remove(id);crafts.add(new Craft(id,batches));available.merge(id,Math.subtractExact(Math.multiplyExact(batches,recipe.outputCount()),units),Math::addExact);
    }
}
