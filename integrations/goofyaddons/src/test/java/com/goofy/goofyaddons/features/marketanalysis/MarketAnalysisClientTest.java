package com.goofy.goofyaddons.features.marketanalysis;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MarketAnalysisClientTest {
    @Test void postsOnlyTheReadOnlyProtocolToTheLocalCompanion() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var packet=new JsonObject();packet.addProperty("protocol",MarketAnalysisProtocol.VERSION);
        server.createContext("/v1/recommendations",exchange->{
            assertEquals("POST",exchange.getRequestMethod());assertEquals("shadow-v1",exchange.getRequestHeaders().getFirst("X-Goofy-Analysis"));
            assertEquals(packet.toString(),new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {var reply=new MarketAnalysisClient().request("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/recommendations",packet).get(5,TimeUnit.SECONDS);assertTrue(reply.get("ok").getAsBoolean());}
        finally {server.stop(0);}
    }
    @Test void oversizedAndFailedResponsesAreNotAccepted() throws Exception {
        for(int status:new int[]{200,503}) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/recommendations",exchange->{
                byte[] body=(status==200?"x".repeat(300000):"offline").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status,body.length);try {exchange.getResponseBody().write(body);} finally {exchange.close();}
            });server.start();
            try {var request=new MarketAnalysisClient().request("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/recommendations",new JsonObject());
                assertThrows(java.util.concurrent.ExecutionException.class,()->request.get(5,TimeUnit.SECONDS));}
            finally {server.stop(0);}
        }
    }
    @Test void localRequestsStayDirectWhenTheJvmHasAProxyAndTheServerRejectsProtocolUpgrades() throws Exception {
        var proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var local=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var proxyCalls=new java.util.concurrent.atomic.AtomicInteger();
        proxy.createContext("/",exchange->{proxyCalls.incrementAndGet();exchange.close();});
        local.createContext("/v1/recommendations",exchange->{
            if(exchange.getRequestHeaders().containsKey("Upgrade")){exchange.close();return;}
            byte[] body="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        var original=java.net.ProxySelector.getDefault();proxy.start();local.start();
        try {
            java.net.ProxySelector.setDefault(java.net.ProxySelector.of(new InetSocketAddress("127.0.0.1",proxy.getAddress().getPort())));
            var reply=new MarketAnalysisClient().request("http://127.0.0.1:"+local.getAddress().getPort()+"/v1/recommendations",new JsonObject()).get(5,TimeUnit.SECONDS);
            assertTrue(reply.get("ok").getAsBoolean());assertEquals(0,proxyCalls.get());
        } finally {java.net.ProxySelector.setDefault(original);local.stop(0);proxy.stop(0);}
    }
}
