package com.goofy.goofyaddons.features.production;

import java.util.*;

/** Immutable recipe facts; a recipe is not proof that this account can execute it. */
public record ProductionRecipe(String key,Kind kind,String outputId,int outputCount,
        Map<String,Integer> ingredients,List<Ingredient> grid,long durationSeconds,double coins,
        String requirement,String inputPet) {
    public enum Kind {CRAFT,FORGE,KAT}
    public record Ingredient(String id,int count) {}
    public ProductionRecipe {
        if(key==null || kind==null || !validId(outputId) || outputCount<1 || outputCount>64
                || ingredients==null || ingredients.isEmpty() || ingredients.size()>20
                || ingredients.entrySet().stream().anyMatch(e->!validId(e.getKey()) || e.getValue()==null || e.getValue()<1 || e.getValue()>1000000)
                || ingredients.containsKey(outputId) || durationSeconds<0 || durationSeconds>31536000
                || !Double.isFinite(coins) || coins<0 || coins>1e13)throw new IllegalArgumentException("Invalid production recipe");
        ingredients=Map.copyOf(ingredients);
        grid=Collections.unmodifiableList(new ArrayList<>(grid==null?List.of():grid));
        if(kind==Kind.CRAFT) {
            if(grid.size()!=9 || outputId.contains(";") || coins!=0 || durationSeconds!=0)throw new IllegalArgumentException("Unsupported craft recipe");
            var totals=new HashMap<String,Integer>();
            for(var cell:grid)if(cell!=null){
                if(!validId(cell.id()) || cell.id().contains(";") || cell.count()<1 || cell.count()>64)throw new IllegalArgumentException("Invalid crafting cell");
                totals.merge(cell.id(),cell.count(),Math::addExact);
            }
            if(!totals.equals(ingredients))throw new IllegalArgumentException("Crafting grid disagrees with ingredients");
        }
        if(kind==Kind.KAT && (!validId(inputPet) || !inputPet.contains(";") || ingredients.getOrDefault(inputPet,0)!=1))
            throw new IllegalArgumentException("Kat requires one exact pet variant");
    }
    public static boolean validId(String value){return value!=null && value.matches("[A-Z0-9_]+(?:;[0-6])?");}
}
