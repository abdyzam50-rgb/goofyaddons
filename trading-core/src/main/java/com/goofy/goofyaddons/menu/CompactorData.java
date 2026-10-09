package com.goofy.goofyaddons.menu;

import java.util.*;

/** Selected Personal Compactor fields, not raw item NBT. Unknown activation stays unknown. */
public record CompactorData(int tier,Boolean active,Map<Integer,String> recipes) {
    public CompactorData {
        int capacity=capacity(tier);
        if(capacity==0 || recipes==null || recipes.entrySet().stream().anyMatch(e->e.getKey()<0 || e.getKey()>=capacity
            || e.getValue()==null || !e.getValue().matches("[A-Z0-9_]+")))throw new IllegalArgumentException("Invalid compactor data");
        recipes=Map.copyOf(recipes);
    }
    public static int capacity(int tier){return switch(tier){case 4000->1;case 5000->3;case 6000->7;case 7000->12;default->0;};}
    public static int tier(String id) {
        return switch(id==null?"":id){case "PERSONAL_COMPACTOR_4000"->4000;case "PERSONAL_COMPACTOR_5000"->5000;
            case "PERSONAL_COMPACTOR_6000"->6000;case "PERSONAL_COMPACTOR_7000"->7000;default->0;};
    }
    public static CompactorData parse(String id,Integer enabled,Map<Integer,String> values) {
        int tier=tier(id);if(tier==0)return null;
        var recipes=new HashMap<Integer,String>();
        for(var e:values.entrySet())if(e.getValue()!=null && !e.getValue().isBlank())recipes.put(e.getKey(),e.getValue());
        return new CompactorData(tier,enabled==null || enabled<0 || enabled>1?null:enabled==1,recipes);
    }
}
