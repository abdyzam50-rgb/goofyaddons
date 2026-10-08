package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.TradingMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Optional Node-backed check: real mod HTTP client -> bundled upstream engine -> strict mod parser. */
@Tag("calculatorIntegration")
class CalculatorIntegrationTest {
    @Test void realCompanionAndModAgreeOnAnUnconfiguredOrdinaryRoute() throws Exception {
        int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Process process=new ProcessBuilder("node","tools/bazaar-calc/server.mjs",Integer.toString(port),"--no-collect","--no-discord").redirectErrorStream(true).start();
        try {
            var reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            String ready=CompletableFuture.supplyAsync(()->{try{return reader.readLine();}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
            assertNotNull(ready);assertTrue(ready.contains("Bazaar Calc companion"),ready);
            long now=System.currentTimeMillis();var market=MarketAnalysisProtocolTest.market(now);
            var product=market.getAsJsonObject("products").getAsJsonObject("ENCHANTED_COAL").deepCopy();
            var products=new com.google.gson.JsonObject();products.add("SYNTHETIC_ORDINARY",product);market.add("products",products);
            var c=MarketAnalysisProtocolTest.config();
            var packet=MarketAnalysisProtocol.request("integration-1",market,c,TradingMode.GENERAL,10000,32,2,3,Set.of());
            var response=new MarketAnalysisClient().request("http://127.0.0.1:"+port+"/v1/recommendations",packet).get(10,TimeUnit.SECONDS);
            var report=MarketAnalysisProtocol.parse(response,packet,System.currentTimeMillis());
            assertEquals(1,report.rows().size());assertEquals("SYNTHETIC_ORDINARY",report.rows().getFirst().inputId());
            assertFalse(report.rows().getFirst().configured());assertTrue(report.rows().getFirst().coinsPerHour()>0);
            assertEquals("51268005376496bf993e0c1934b8a7e44656b0bc",report.upstreamCommit());
            c.marketAnalysis.automaticSelection=true;c.general.items=java.util.List.of();c.books=java.util.List.of();
            // Use a catalog-known name absent from public history. Synthetic fixture quotes
            // test automatic eligibility without depending on current real-market medians.
            var automaticMarket=MarketAnalysisProtocolTest.market(System.currentTimeMillis());
            var automaticProducts=new com.google.gson.JsonObject();
            automaticProducts.add("AATROX_BATPHONE",product.deepCopy());automaticMarket.add("products",automaticProducts);
            var automaticPacket=MarketAnalysisProtocol.request("integration-auto",automaticMarket,
                    c,TradingMode.BOTH,10000,32,2,3,Set.of());
            var automaticResponse=new MarketAnalysisClient().request("http://127.0.0.1:"+port+"/v1/recommendations",automaticPacket).get(10,TimeUnit.SECONDS);
            var automaticReport=MarketAnalysisProtocol.parse(automaticResponse,automaticPacket,System.currentTimeMillis());
            assertFalse(automaticReport.rows().isEmpty());assertTrue(automaticReport.rows().stream().allMatch(MarketAnalysisProtocol.Recommendation::configured));
            var account=com.google.gson.JsonParser.parseString("""
                    {"protocol":"goofy-dashboard/1","sessionId":"java-integration","account":{"connected":true,"name":"LocalTest"},
                     "status":{"state":"STOPPED"},"inventory":[{"slot":0,"name":"Coal","count":2}],"books":{"tasks":[]},
                     "general":{"positions":[]},"analysis":{},"profit":{"profit":123}}
                    """).getAsJsonObject();account.addProperty("sentAt",System.currentTimeMillis());
            assertTrue(new MarketAnalysisClient().publishDashboard("http://127.0.0.1:"+port+"/v1/recommendations",account).get(5,TimeUnit.SECONDS).get("ok").getAsBoolean());
            try(var http=java.net.http.HttpClient.newHttpClient()) {
                var view=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v1/dashboard")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                var state=com.google.gson.JsonParser.parseString(view.body()).getAsJsonObject();
                assertTrue(state.get("fresh").getAsBoolean());assertEquals("LocalTest",state.getAsJsonObject("account").getAsJsonObject("account").get("name").getAsString());
                assertEquals(123,state.getAsJsonObject("account").getAsJsonObject("profit").get("profit").getAsInt());
            }
            // Real Java ledger -> local history -> similar-volume prior -> strict Java parser.
            long learnedAt=System.currentTimeMillis();var ledger=new com.goofy.goofyaddons.features.profit.ExecutionLedger();
            for(int i=0;i<10;i++) {
                String trade="peer-"+learnedAt+"-"+i;
                ledger.begin(trade,"general","COAL","COAL",16,16,learnedAt-120000,100.0,
                        new com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast(60,100000.0/7,100000.0/7));
                ledger.complete(trade,trade+":receipt",16,200.0,50.0,learnedAt,false);
            }
            account.addProperty("sentAt",learnedAt);account.add("executions",new com.google.gson.Gson().toJsonTree(ledger.samples()));
            assertTrue(new MarketAnalysisClient().publishDashboard("http://127.0.0.1:"+port+"/v1/recommendations",account).get(5,TimeUnit.SECONDS).get("ok").getAsBoolean());
            automaticMarket.addProperty("lastUpdated",learnedAt);
            var blocked=MarketAnalysisProtocol.request("integration-ranking",automaticMarket,c,TradingMode.BOTH,0,0,0,0,Set.of("AATROX_BATPHONE"));
            var catalog=MarketAnalysisProtocol.request("integration-ranking",automaticMarket,c,TradingMode.BOTH,10000,32,1,1,Set.of());
            blocked.add("rankingConstraints",catalog.get("constraints"));
            var combined=new MarketAnalysisClient().request("http://127.0.0.1:"+port+"/v1/recommendations",blocked).get(10,TimeUnit.SECONDS);
            assertTrue(MarketAnalysisProtocol.parse(combined,blocked,System.currentTimeMillis()).rows().isEmpty());
            var ranked=MarketAnalysisProtocol.parse(combined.getAsJsonObject("rankingReport"),catalog,System.currentTimeMillis()).rows().getFirst();
            assertEquals(0,ranked.executionEvidence().get("samples"));assertEquals(10,ranked.executionEvidence().get("sharedSamples"));
            assertTrue(((Number)ranked.executionEvidence().get("throughputFactor")).doubleValue()<1);
            assertTrue(((Number)ranked.executionEvidence().get("profitRealizationFactor")).doubleValue()<1);
        } finally {process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}
    }
}
