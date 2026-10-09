package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftFlipPlannerTest {
    static final long NOW=1700000000000L;
    static ProductionRecipe recipe(String output,String input,int units,String requirement) {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient(input,units));
        return new ProductionRecipe(output,ProductionRecipe.Kind.CRAFT,output,1,Map.of(input,units),grid,0,0,requirement,null);
    }
    static JsonObject market(int outputDepth) {
        return JsonParser.parseString("{\"lastUpdated\":"+NOW+",\"products\":{\"INPUT\":{\"buy_summary\":[{\"pricePerUnit\":5,\"amount\":10000}]},\"OUTPUT\":{\"sell_summary\":[{\"pricePerUnit\":100,\"amount\":"+outputDepth+"}],\"quick_status\":{\"buyMovingWeek\":10000,\"sellMovingWeek\":10000}}}}").getAsJsonObject();
    }
    static List<CraftFlipPlanner.Route> rank(JsonObject market,JsonObject ah,RecipeCatalog catalog,MenuSnapshot menu,double budget,Map<String,Integer> unlocks) {
        return CraftFlipPlanner.rank(catalog,market,ah,menu,Map.of(),unlocks,Set.of(),budget,1,1.25,16,"BOTH",NOW);
    }
    @Test void wholeBatchFitsDepthAndBudgetAndIncludesBuyFeeAndSaleMovementAllowance() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",4,"")),Map.of());
        var rows=rank(market(2),null,catalog,ProductionRunTest.menu(null),30,Map.of());
        assertEquals(1,rows.size());assertTrue(rows.getFirst().eligible());assertEquals(1,rows.getFirst().batches());
        assertEquals(20*1.04*1.03,rows.getFirst().capital(),0.0001);
        assertEquals(100*0.97*0.9875-20*1.04*1.03,rows.getFirst().profit(),0.0001);
        assertEquals(2,rank(market(2),null,catalog,ProductionRunTest.menu(null),1000,Map.of()).getFirst().batches());
    }
    @Test void unobservedRequirementsAndInsufficientInventoryCannotAuthorizeCrafts() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",4,"Gold Ingot IV")),Map.of());
        var row=rank(market(1),null,catalog,ProductionRunTest.menu(null),1000,Map.of()).getFirst();
        assertFalse(row.eligible());assertTrue(row.reason().contains("unobserved"));
        assertTrue(rank(market(1),null,catalog,ProductionRunTest.menu(null),1000,Map.of("goldingot",4)).getFirst().eligible());
        var full=new MenuSnapshot(1,null,true,List.of());
        assertFalse(rank(market(1),null,catalog,full,1000,Map.of("goldingot",4)).getFirst().eligible());
    }
    @Test void intermediateCraftsAreCostedFromBaseMaterialsAndStaleMarketsAreRejected() {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));grid.set(0,new ProductionRecipe.Ingredient("BLAZE_ROD",1));
        var powder=new ProductionRecipe("powder",ProductionRecipe.Kind.CRAFT,"BLAZE_POWDER",2,Map.of("BLAZE_ROD",1),grid,0,0,"",null);
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","BLAZE_POWDER",16,""),powder),Map.of());
        var market=market(1);market.getAsJsonObject("products").add("BLAZE_ROD",market.getAsJsonObject("products").remove("INPUT"));
        assertEquals(8*5*1.04*1.03,rank(market,null,catalog,ProductionRunTest.menu(null),1000,Map.of()).getFirst().capital(),0.0001);
        market.addProperty("lastUpdated",NOW-60001);assertTrue(rank(market,null,catalog,ProductionRunTest.menu(null),1000,Map.of()).isEmpty());
    }
    @Test void ahDiscoveryRequiresFreshQuoteAndDoesNotPretendPublicationIsACompletedSale() {
        var catalog=new RecipeCatalog(List.of(recipe("AH_OUTPUT","INPUT",4,"")),Map.of());
        var quote=JsonParser.parseString("{\"protocol\":\"goofy-ah-price/1\",\"item\":\"AH_OUTPUT\",\"lowest\":20000,\"secondLowest\":21000,\"source\":\"coflnet\",\"fetchedAt\":"+NOW+"}");
        var row=new JsonObject();row.addProperty("item","AH_OUTPUT");row.addProperty("volume",100);row.add("quote",quote);
        var ah=new JsonObject();var rows=new JsonArray();rows.add(row);ah.add("rows",rows);
        var result=rank(market(1),ah,catalog,ProductionRunTest.menu(null),100000,Map.of()).getFirst();
        assertEquals("AH",result.venue());assertFalse(result.eligible());assertTrue(result.reason().contains("reconciliation"));
        quote.getAsJsonObject().addProperty("fetchedAt",NOW-60001);
        assertTrue(rank(market(1),ah,catalog,ProductionRunTest.menu(null),100000,Map.of()).isEmpty());
    }
}
