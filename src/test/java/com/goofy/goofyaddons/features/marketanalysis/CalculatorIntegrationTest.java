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
        Process process=new ProcessBuilder("node","tools/bazaar-calc/server.mjs",Integer.toString(port),"--no-collect").redirectErrorStream(true).start();
        try {
            var reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            String ready=CompletableFuture.supplyAsync(()->{try{return reader.readLine();}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
            assertNotNull(ready);assertTrue(ready.contains("Read-only Bazaar Calc companion"),ready);
            long now=System.currentTimeMillis();var market=MarketAnalysisProtocolTest.market(now);
            var product=market.getAsJsonObject("products").getAsJsonObject("ENCHANTED_COAL").deepCopy();
            var products=new com.google.gson.JsonObject();products.add("SYNTHETIC_ORDINARY",product);market.add("products",products);
            var c=MarketAnalysisProtocolTest.config();
            var packet=MarketAnalysisProtocol.request("integration-1",market,c,TradingMode.GENERAL,10000,32,2,3,Set.of());
            var response=new MarketAnalysisClient().request("http://127.0.0.1:"+port+"/v1/recommendations",packet).get(10,TimeUnit.SECONDS);
            var report=MarketAnalysisProtocol.parse(response,packet,System.currentTimeMillis());
            assertEquals(1,report.rows().size());assertEquals("SYNTHETIC_ORDINARY",report.rows().getFirst().inputId());
            assertFalse(report.rows().getFirst().configured());assertTrue(report.rows().getFirst().coinsPerHour()>0);
            assertEquals("6dd0ae9565fd555dec9dbe5eca3a2f48ca3218cc",report.upstreamCommit());
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
        } finally {process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}
    }
}
