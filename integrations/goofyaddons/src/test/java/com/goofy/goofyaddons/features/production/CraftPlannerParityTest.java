package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The website's craft planner (tools/bazaar-calc/website-src/craft-plan.mjs) ports CraftFlipPlanner.
 * Both test suites read fixtures/craft-parity.json, so the two cannot drift apart silently.
 */
class CraftPlannerParityTest {
    static JsonObject fixture() {
        try(var in=CraftPlannerParityTest.class.getResourceAsStream("/fixtures/craft-parity.json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    /** A player inventory with exactly `free` empty slots; the rest hold an unrelated item. */
    static MenuSnapshot inventory(int free) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++)slots.add(i>=54&&i-54>=free?new SlotView(i,true,i-54,false,null,"Stone",List.of(),"FILLER",Map.of(),1,64):SlotView.empty(i,i>=54,i>=54?i-54:i));
        return new MenuSnapshot(1,null,true,slots);
    }
    @Test void modAndWebsiteRankTheSameRoutes() {
        var root=fixture();var catalog=RecipeCatalog.parse(root.getAsJsonObject("catalog"));long now=root.get("now").getAsLong();
        for(var value:root.getAsJsonArray("cases")) {
            var c=value.getAsJsonObject();var s=c.getAsJsonObject("settings");String name=c.get("name").getAsString();
            var menu=inventory(s.get("freeSlots").getAsInt());assertEquals(s.get("freeSlots").getAsInt(),menu.emptyInventorySlots(),name);
            var excludedByOutput=new LinkedHashMap<String,Map<String,Integer>>();
            var routes=CraftFlipPlanner.rank(catalog,root.getAsJsonObject("market"),root.getAsJsonObject("ah"),menu,Map.of(),Map.of(),Set.of(),
                s.get("budget").getAsDouble(),s.get("minimumProfit").getAsDouble(),s.get("bazaarTax").getAsDouble(),s.get("maxBatches").getAsInt(),
                s.get("venue").getAsString(),now,new LinkedHashMap<>(),0,s.get("strategy").getAsString(),excludedByOutput);
            var expected=c.getAsJsonObject("expected");var rows=expected.getAsJsonArray("routes");
            assertEquals(rows.size(),routes.size(),name+": route count");
            for(int i=0;i<rows.size();i++) {
                var want=rows.get(i).getAsJsonObject();var got=routes.get(i);String label=name+" #"+i+" "+want.get("recipeKey").getAsString();
                assertEquals(want.get("recipeKey").getAsString(),got.recipe().key(),label);
                assertEquals(want.get("batches").getAsInt(),got.batches(),label+" batches");
                assertEquals(want.get("venue").getAsString(),got.venue(),label+" venue");
                assertEquals(want.get("strategy").getAsString(),got.strategy().name(),label+" strategy");
                assertEquals(want.get("eligible").getAsBoolean(),got.eligible(),label+" eligible");
                assertEquals(want.get("reason").isJsonNull()?null:want.get("reason").getAsString(),got.reason(),label+" reason");
                assertEquals(want.get("inventorySlots").getAsInt(),got.inventorySlots(),label+" slots");
                assertEquals(want.get("binPrice").isJsonNull()?ProductionRun.SELL_ON_BAZAAR:want.get("binPrice").getAsLong(),got.binPrice(),label+" BIN");
                for(var field:List.of("capital","profit","score","fee")) {
                    double w=want.get(field).getAsDouble(),g=switch(field){case "capital"->got.capital();case "profit"->got.profit();case "score"->got.score();default->got.fee();};
                    assertEquals(w,g,Math.abs(w)*1e-9+1e-9,label+" "+field);
                }
            }
            var wantExcluded=new LinkedHashMap<String,Map<String,Integer>>();
            for(var e:expected.getAsJsonObject("excludedByOutput").entrySet()){var m=new LinkedHashMap<String,Integer>();e.getValue().getAsJsonObject().entrySet().forEach(r->m.put(r.getKey(),r.getValue().getAsInt()));wantExcluded.put(e.getKey(),m);}
            assertEquals(wantExcluded,excludedByOutput,name+": exclusions");
        }
    }
}
