package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.TradingMode;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MarketAnalysisProtocolTest {
    static final long NOW = 1791046800000L;
    static GoofyConfig config() {
        var c = new GoofyConfig(); c.general.minProfitPerBatch=0;c.general.minWeeklyVolume=0;c.marketAnalysis.enabled=true;return c;
    }
    static JsonObject market(long now) {
        var product = JsonParser.parseString("""
                {"sell_summary":[{"pricePerUnit":100,"amount":1000,"orders":10}],
                 "buy_summary":[{"pricePerUnit":130,"amount":1000,"orders":10}],
                 "quick_status":{"buyMovingWeek":100000,"sellMovingWeek":100000,"buyVolume":1000,"sellVolume":1000,"buyOrders":10,"sellOrders":10}}
                """).getAsJsonObject();
        var products=new JsonObject();products.add("ENCHANTED_COAL",product);
        products.add("ENCHANTMENT_OVERLOAD_4",product.deepCopy());products.add("ENCHANTMENT_OVERLOAD_5",product.deepCopy());
        var root=new JsonObject();root.addProperty("success",true);root.addProperty("lastUpdated",now);root.add("products",products);return root;
    }
    static JsonObject request(GoofyConfig c,long now) {
        return MarketAnalysisProtocol.request("test-1",market(now),c,TradingMode.BOTH,10000,32,2,3,Set.of());
    }
    static JsonObject response(long now) {
        var root=JsonParser.parseString("""
                {"protocol":"goofy-bazaar-shadow/1","requestId":"test-1","marketAt":0,"dataAt":0,"generatedAt":0,
                "historyUsed":true,"historyStatus":"FRESH","upstreamCommit":"6dd0ae9565fd555dec9dbe5eca3a2f48ca3218cc","total":1,
                "rows":[{"kind":"GENERAL","routeKey":"ENCHANTED_COAL","inputId":"ENCHANTED_COAL","outputId":"ENCHANTED_COAL",
                "batch":4,"inputsPerOutput":1,"inputUnits":4,"costPerOutput":100.1,"buyPrice":100.1,"sellPrice":129.9,
                "profitPerOutput":28.17625,"profitPerBatch":112.705,"capitalUsed":500,
                "outputsPerHour":6,"coinsPerHour":169.0575,"cycleSeconds":2400,
                "confidence":"ESTIMATED","buyBasis":"estimated","sellBasis":"estimated","configured":true,
                "limitedBy":"demand","priceBasis":"current offer"}]}
                """).getAsJsonObject();
        root.addProperty("marketAt",now);root.addProperty("dataAt",now);root.addProperty("generatedAt",now);return root;
    }
    @Test void acceptsConsistentRecommendationsWithoutChangingTheConfiguration() {
        var c=config();var books=java.util.List.copyOf(c.books);var items=java.util.List.copyOf(c.general.items);
        var report=MarketAnalysisProtocol.parse(response(NOW),request(c,NOW),NOW);
        assertEquals(1,report.rows().size());assertEquals("ESTIMATED",report.rows().getFirst().confidence());
        assertEquals(books,c.books);assertEquals(items,c.general.items);assertTrue(report.rows().getFirst().configured());
    }
    @Test void requestContainsOnlyMarketAndConstraintsNoPlayerIdentityOrJournal() {
        var r=request(config(),NOW);
        assertEquals(Set.of("protocol","requestId","market","constraints"),r.keySet());
        assertEquals(10000,r.getAsJsonObject("constraints").get("availableCapital").getAsDouble());
        assertFalse(r.toString().contains("username"));assertFalse(r.toString().contains("tradeId"));
    }
    @Test void rejectsMismatchedRequestOrMarketAndExpiredQuotes() {
        var wrong=response(NOW);wrong.addProperty("requestId","wrong");assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(wrong,request(config(),NOW),NOW));
        var mismatch=response(NOW);mismatch.addProperty("marketAt",NOW-1);
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(mismatch,request(config(),NOW),NOW));
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(response(NOW),request(config(),NOW),NOW+61000));
    }
    @Test void rejectsInventedProfitAndCapitalOrFractionalOrderSizes() {
        for(String key:new String[]{"profitPerOutput","capitalUsed","inputUnits"}) {
            var r=response(NOW);r.getAsJsonArray("rows").get(0).getAsJsonObject().addProperty(key,key.equals("inputUnits")?4.5:99999);
            assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(r,request(config(),NOW),NOW),key);
        }
    }
    @Test void rejectsStaleHistoryAdvertisedAsMeasuredAndCanAcceptExplicitEstimatedFallback() {
        var r=response(NOW);r.addProperty("dataAt",NOW-49*3600000L);
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(r,request(config(),NOW),NOW));
        r.addProperty("historyUsed",false);r.addProperty("historyStatus","STALE");
        assertEquals("ESTIMATED",MarketAnalysisProtocol.parse(r,request(config(),NOW),NOW).rows().getFirst().confidence());
        var row=r.getAsJsonArray("rows").get(0).getAsJsonObject();row.addProperty("confidence","MEASURED");row.addProperty("buyBasis","measured");row.addProperty("sellBasis","measured");
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(r,request(config(),NOW),NOW));
    }
    @Test void rejectsUnsupportedModesAndOccupiedProducts() {
        var booksOnly=request(config(),NOW);booksOnly.getAsJsonObject("constraints").addProperty("mode","BOOKS");
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(response(NOW),booksOnly,NOW));
        var occupied=request(config(),NOW);occupied.getAsJsonObject("constraints").getAsJsonArray("excludedProducts").add("ENCHANTED_COAL");
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(response(NOW),occupied,NOW));
    }
    @Test void rejectsNewProfitThresholdsAndInsufficientCapacity() {
        var c=config();c.general.minProfitPerBatch=1000;
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(response(NOW),request(c,NOW),NOW));
        var q=request(config(),NOW);q.getAsJsonObject("constraints").addProperty("inventoryCapacity",3);
        assertThrows(IllegalArgumentException.class,()->MarketAnalysisProtocol.parse(response(NOW),q,NOW));
    }
    @Test void onlyLocalCompanionEndpointsAreSupported() {
        new MarketAnalysisSettings().validate();
        for(String url:new String[]{"https://example.com/v1/recommendations","http://127.0.0.1:8789/other","http://user@127.0.0.1:8789/v1/recommendations"}) {
            var c=new MarketAnalysisSettings();c.endpoint=url;assertThrows(IllegalArgumentException.class,c::validate);
        }
    }
}
