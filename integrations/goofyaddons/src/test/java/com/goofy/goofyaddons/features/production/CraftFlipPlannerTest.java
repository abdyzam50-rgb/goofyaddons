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
        return JsonParser.parseString("{\"lastUpdated\":"+NOW+",\"products\":{\"INPUT\":{\"quick_status\":{\"buyMovingWeek\":10000,\"sellMovingWeek\":10000},\"buy_summary\":[{\"pricePerUnit\":5,\"amount\":10000}]},\"OUTPUT\":{\"sell_summary\":[{\"pricePerUnit\":100,\"amount\":"+outputDepth+"}],\"quick_status\":{\"buyMovingWeek\":10000,\"sellMovingWeek\":10000}}}}").getAsJsonObject();
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
    @Test void bazaarTradingUnlocksApplyToCraftOutputsAndInputsDuringSelection() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","ESSENCE_WITHER",4,"")),Map.of());
        var market=market(1);market.getAsJsonObject("products").add("ESSENCE_WITHER",market.getAsJsonObject("products").remove("INPUT"));
        var blocked=rank(market,null,catalog,ProductionRunTest.menu(null),1000,Map.of()).getFirst();
        assertFalse(blocked.eligible());assertTrue(blocked.reason().contains("Catacombs"));
        assertTrue(rank(market,null,catalog,ProductionRunTest.menu(null),1000,Map.of("catacombs",20)).getFirst().eligible());
    }

    @Test void emptyRankingExplainsProfitThresholdAndUnobservedBudget() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",4,"")),Map.of());
        var counts=new LinkedHashMap<String,Integer>();
        assertTrue(CraftFlipPlanner.rank(catalog,market(100),null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),100000,
            10000,1.25,16,"BAZAAR",NOW,counts).isEmpty());
        assertEquals(16,counts.get("Below minimum net profit after fees and price allowance"));
        assertTrue(CraftFlipPlanner.rank(catalog,market(100),null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),0,
            10000,1.25,16,"BAZAAR",NOW,counts).isEmpty());
        assertEquals(Map.of("No observed spendable budget",1),counts);
    }

    @Test void diagnosticExclusionsIdentifyTheOutputAndDoNotChangeRankings() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",4,"")),Map.of());
        var counts=new LinkedHashMap<String,Integer>();var byOutput=new LinkedHashMap<String,Map<String,Integer>>();
        var detailed=CraftFlipPlanner.rank(catalog,market(100),null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),100000,
            10000,1.25,16,"BAZAAR",NOW,counts,0,"AUTO",byOutput);
        assertTrue(detailed.isEmpty());assertEquals(16,byOutput.get("OUTPUT").get("Below minimum net profit after fees and price allowance"));
        var normal=CraftFlipPlanner.rank(catalog,market(100),null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),100000,
            1,1.25,16,"BAZAAR",NOW,counts,0,"AUTO");
        detailed=CraftFlipPlanner.rank(catalog,market(100),null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),100000,
            1,1.25,16,"BAZAAR",NOW,counts,0,"AUTO",byOutput);
        assertEquals(normal,detailed);
    }

    @Test void orderIngredientsAndOutputOffersMakePreviouslyUnprofitableCraftExecutable(){
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",4,"")),Map.of());
        var market=market(100);var products=market.getAsJsonObject("products");
        products.getAsJsonObject("INPUT").getAsJsonArray("buy_summary").get(0).getAsJsonObject().addProperty("pricePerUnit",90);
        products.getAsJsonObject("INPUT").add("sell_summary",JsonParser.parseString("[{\"pricePerUnit\":1,\"amount\":10000}]"));
        products.getAsJsonObject("OUTPUT").add("buy_summary",JsonParser.parseString("[{\"pricePerUnit\":200,\"amount\":10000}]"));
        products.getAsJsonObject("OUTPUT").getAsJsonArray("sell_summary").get(0).getAsJsonObject().addProperty("pricePerUnit",3);
        var rows=rank(market,null,catalog,ProductionRunTest.menu(null),1000,Map.of());
        assertEquals(1,rows.size());var route=rows.getFirst();assertTrue(route.eligible());
        assertEquals(BazaarStrategy.ORDER_OFFER,route.strategy());assertTrue(route.profit()>0);
        assertEquals("order → offer",route.describe(catalog).get("buySell"));
    }

    @Test void anotherCraftsReservedWorkspaceCanReduceBatchSizeOrExcludeTheRoute() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",2,"")),Map.of());
        var empty=ProductionRunTest.menu(null);
        var clear=CraftFlipPlanner.rank(catalog,market(10000),null,empty,Map.of(),Map.of(),Set.of(),1000,1,1.25,16,"BOTH",NOW,new LinkedHashMap<>(),0);
        assertTrue(clear.stream().anyMatch(CraftFlipPlanner.Route::eligible));
        assertTrue(clear.getFirst().inventorySlots()>=2);
        var reserved=CraftFlipPlanner.rank(catalog,market(10000),null,empty,Map.of(),Map.of(),Set.of(),1000,1,1.25,16,"BOTH",NOW,new LinkedHashMap<>(),35);
        assertTrue(reserved.stream().noneMatch(CraftFlipPlanner.Route::eligible));
        assertTrue(reserved.getFirst().reason().contains("inventory"));
        var conflicting=CraftFlipPlanner.rank(catalog,market(10000),null,empty,Map.of(),Map.of(),Set.of("INPUT"),1000,1,1.25,16,"BOTH",NOW,new LinkedHashMap<>(),0);
        assertTrue(conflicting.stream().noneMatch(CraftFlipPlanner.Route::eligible));
    }

    @Test void largerHotspotBaitRunsCountAllOutputStacksAndRespectStrategySelection() {
        var recipe=RecipeCatalog.instance().forOutput("HOTSPOT_BAIT").stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        assertEquals(32,recipe.outputCount());
        var catalog=new RecipeCatalog(List.of(recipe),Map.of());
        var market=market(10000);var products=market.getAsJsonObject("products");
        var input=products.getAsJsonObject("INPUT").deepCopy();
        input.add("sell_summary",JsonParser.parseString("[{\"pricePerUnit\":1,\"amount\":10000}]"));
        input.getAsJsonObject("quick_status").addProperty("buyMovingWeek",10000000);
        input.getAsJsonObject("quick_status").addProperty("sellMovingWeek",10000000);
        for(String id:recipe.ingredients().keySet())products.add(id,input.deepCopy());
        var output=products.getAsJsonObject("OUTPUT").deepCopy();
        output.add("buy_summary",JsonParser.parseString("[{\"pricePerUnit\":100,\"amount\":10000}]"));
        output.getAsJsonObject("quick_status").addProperty("buyMovingWeek",10000000);
        output.getAsJsonObject("quick_status").addProperty("sellMovingWeek",10000000);
        products.add("HOTSPOT_BAIT",output);
        var rows=CraftFlipPlanner.rank(catalog,market,null,ProductionRunTest.menu(null),Map.of(),Map.of("pufferfish",8),Set.of(),1000000,
            100000,1.25,64,"BAZAAR",NOW,new LinkedHashMap<>(),0,"INSTANT_OFFER");
        assertEquals(1,rows.size());var route=rows.getFirst();assertTrue(route.eligible(),route.reason());
        assertEquals(64,route.batches());assertEquals(BazaarStrategy.INSTANT_OFFER,route.strategy());
        assertEquals(34,route.inventorySlots(),"2 input stacks and 32 output stacks");
        assertEquals(route.score()*3600,route.describe(catalog).get("estimatedCoinsPerHour"));
        var shortRun=CraftFlipPlanner.rank(catalog,market,null,ProductionRunTest.menu(null),Map.of(),Map.of("pufferfish",8),Set.of(),1000000,
            100000,1.25,16,"BAZAAR",NOW,new LinkedHashMap<>(),0,"INSTANT_OFFER");
        assertTrue(shortRun.isEmpty(),"Explicit old batch limit remains respected");
        var reserved=CraftFlipPlanner.rank(catalog,market,null,ProductionRunTest.menu(null),Map.of(),Map.of("pufferfish",8),Set.of(),1000000,
            100000,1.25,64,"BAZAAR",NOW,new LinkedHashMap<>(),4,"INSTANT_OFFER").getFirst();
        assertTrue(reserved.eligible());assertEquals(60,reserved.batches());assertEquals(32,reserved.inventorySlots());
    }

    @Test void orderRankingRespondsToObservedActivityRatherThanAFixedWait() {
        var catalog=new RecipeCatalog(List.of(recipe("OUTPUT","INPUT",2,"")),Map.of());
        var slow=market(10000);var products=slow.getAsJsonObject("products");
        products.getAsJsonObject("OUTPUT").add("buy_summary",JsonParser.parseString("[{\"pricePerUnit\":100,\"amount\":10000}]"));
        var fast=slow.deepCopy();fast.getAsJsonObject("products").getAsJsonObject("OUTPUT").getAsJsonObject("quick_status").addProperty("buyMovingWeek",1000000);
        fast.getAsJsonObject("products").getAsJsonObject("OUTPUT").getAsJsonObject("quick_status").addProperty("sellMovingWeek",1000000);
        var a=CraftFlipPlanner.rank(catalog,slow,null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),1000000,1,1.25,16,"BAZAAR",NOW,new LinkedHashMap<>(),0,"INSTANT_OFFER").getFirst();
        var b=CraftFlipPlanner.rank(catalog,fast,null,ProductionRunTest.menu(null),Map.of(),Map.of(),Set.of(),1000000,1,1.25,16,"BAZAAR",NOW,new LinkedHashMap<>(),0,"INSTANT_OFFER").getFirst();
        assertEquals(a.profit(),b.profit());assertTrue(b.score()>a.score()*2);
    }

}
