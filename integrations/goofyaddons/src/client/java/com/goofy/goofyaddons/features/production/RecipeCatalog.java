package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import java.util.*;

public final class RecipeCatalog {
    private static final class Holder {static final RecipeCatalog INSTANCE=load();}
    private final List<ProductionRecipe> recipes;
    private final Map<String,String> names;
    public RecipeCatalog(List<ProductionRecipe> recipes,Map<String,String> names){this.recipes=List.copyOf(recipes);this.names=Map.copyOf(names);}
    public static RecipeCatalog instance(){return Holder.INSTANCE;}
    public List<ProductionRecipe> recipes(){return recipes;}
    public List<ProductionRecipe> forOutput(String id){return recipes.stream().filter(r->r.outputId().equals(id)).toList();}
    public Optional<ProductionRecipe> byKey(String key){return recipes.stream().filter(r->r.key().equals(key)).findFirst();}
    public String name(String id){return names.getOrDefault(id,id);}
    private static RecipeCatalog load() {
        try(var in=RecipeCatalog.class.getResourceAsStream("/goofyaddons/production-recipes.json")) {
            if(in==null)throw new IllegalStateException("Production catalog missing");
            var root=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported production catalog");
            var names=new HashMap<String,String>();root.getAsJsonObject("names").entrySet().forEach(e->names.put(e.getKey(),e.getValue().getAsString()));
            var recipes=new ArrayList<ProductionRecipe>();var keys=new HashSet<String>();
            for(var value:root.getAsJsonArray("recipes")) {
                var r=value.getAsJsonObject();var ingredients=new HashMap<String,Integer>();
                r.getAsJsonObject("ingredients").entrySet().forEach(e->ingredients.put(e.getKey(),e.getValue().getAsInt()));
                var grid=new ArrayList<ProductionRecipe.Ingredient>();
                for(var cell:r.getAsJsonArray("grid"))grid.add(cell.isJsonNull()?null:new ProductionRecipe.Ingredient(cell.getAsJsonObject().get("id").getAsString(),cell.getAsJsonObject().get("count").getAsInt()));
                String key=r.get("key").getAsString();if(!keys.add(key))throw new IllegalArgumentException("Duplicate recipe");
                recipes.add(new ProductionRecipe(key,ProductionRecipe.Kind.valueOf(r.get("kind").getAsString()),r.get("outputId").getAsString(),r.get("outputCount").getAsInt(),ingredients,grid,r.get("durationSeconds").getAsLong(),r.get("coins").getAsDouble(),r.get("requirement").getAsString(),r.get("inputPet").isJsonNull()?null:r.get("inputPet").getAsString()));
            }
            return new RecipeCatalog(recipes,names);
        } catch(java.io.IOException failed){throw new IllegalStateException("Cannot load production catalog",failed);}
    }
}
