package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import java.util.*;

public final class RecipeCatalog {
    private static final class Holder {static final RecipeCatalog INSTANCE=load();}
    private final List<ProductionRecipe> recipes;
    private final Map<String,String> names;
    public record UnsupportedCraft(String key,String outputId,String reason) {}
    private final List<UnsupportedCraft> unsupported;
    /** Items that take one inventory slot per unit (accessories, minion upgrades, gear). */
    private final Set<String> unstackable;
    // Planners look recipes up per candidate batch; a linear catalog scan there costs whole seconds per ranking.
    private final Map<String,List<ProductionRecipe>> byOutput;
    private final Map<String,ProductionRecipe> byKey;
    public RecipeCatalog(List<ProductionRecipe> recipes,Map<String,String> names){this(recipes,names,List.of(),Set.of());}
    private RecipeCatalog(List<ProductionRecipe> recipes,Map<String,String> names,List<UnsupportedCraft> unsupported,Set<String> unstackable){this.recipes=List.copyOf(recipes);this.names=Map.copyOf(names);this.unsupported=List.copyOf(unsupported);this.unstackable=Set.copyOf(unstackable);
        var outputs=new HashMap<String,List<ProductionRecipe>>();var keys=new HashMap<String,ProductionRecipe>();
        for(var recipe:this.recipes){outputs.computeIfAbsent(recipe.outputId(),id->new ArrayList<>()).add(recipe);keys.putIfAbsent(recipe.key(),recipe);}
        outputs.replaceAll((id,list)->List.copyOf(list));byOutput=Map.copyOf(outputs);byKey=Map.copyOf(keys);}
    public List<UnsupportedCraft> unsupportedCrafts(){return unsupported;}
    public boolean unstackable(String id){return id!=null && unstackable.contains(id);}
    public static RecipeCatalog instance(){return Holder.INSTANCE;}
    private static volatile String loadedRevision;
    /** Which recipe data this client ranks with, so website and mod drift shows up in an export. */
    public Map<String,Object> diagnosticState(){return Map.of("recipes",recipes.size(),"unstackable",unstackable.size(),"unsupported",unsupported.size(),"sourceRevision",Objects.toString(loadedRevision,"unknown"));}
    public List<ProductionRecipe> recipes(){return recipes;}
    public List<ProductionRecipe> forOutput(String id){return id==null?List.of():byOutput.getOrDefault(id,List.of());}
    public Optional<ProductionRecipe> byKey(String key){return key==null?Optional.empty():Optional.ofNullable(byKey.get(key));}
    public String name(String id){
        if(id!=null && id.startsWith("ENCHANTMENT_")) {
            int split=id.lastIndexOf('_');
            try {
                int level=Integer.parseInt(id.substring(split+1));
                String base=com.goofy.goofyaddons.features.access.RouteRequirements.bookName(id.substring(0,split));
                if(base!=null && level>=1 && level<=100 && Integer.toString(level).equals(id.substring(split+1))) {
                    String roman=switch(level){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";case 5->"V";
                        case 6->"VI";case 7->"VII";case 8->"VIII";case 9->"IX";case 10->"X";default->Integer.toString(level);};
                    return base+" "+roman;
                }
            }catch(NumberFormatException invalid) { return names.getOrDefault(id,id); }
            return id;
        }
        return names.getOrDefault(id,id);
    }
    private static RecipeCatalog load() {
        try(var in=RecipeCatalog.class.getResourceAsStream("/goofyaddons/production-recipes.json")) {
            if(in==null)throw new IllegalStateException("Production catalog missing");
            var root=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.has("sourceRevision"))loadedRevision=root.get("sourceRevision").getAsString();
            return parse(root);
        } catch(java.io.IOException failed){throw new IllegalStateException("Cannot load production catalog",failed);}
    }
    /** The production-recipes.json schema, also used by the website and the shared planner fixtures. */
    static RecipeCatalog parse(JsonObject root) {
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
        var unsupported=new ArrayList<UnsupportedCraft>();
        if(root.has("unsupportedCrafts"))for(var value:root.getAsJsonArray("unsupportedCrafts")) {
            var row=value.getAsJsonObject();
            unsupported.add(new UnsupportedCraft(row.get("key").getAsString(),row.get("outputId").getAsString(),row.get("reason").getAsString()));
        }
        var single=new HashSet<String>();
        if(root.has("unstackable"))for(var value:root.getAsJsonArray("unstackable"))single.add(value.getAsString());
        return new RecipeCatalog(recipes,names,unsupported,single);
    }
}
