package com.goofy.goofyaddons.features.companion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Starts the exact resource ZIP shipped inside the mod, not the development source tree. */
@Tag("calculatorIntegration")
class ManagedCompanionTest {
    @TempDir Path folder;
    byte[] bundle() throws Exception {try(var resource=getClass().getResourceAsStream("/goofyaddons/calculator.zip")){assertNotNull(resource);return resource.readAllBytes();}}
    int port() throws Exception {try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    ManagedCompanion manager(Path data) throws Exception {
        return new ManagedCompanion(bundle(),data,folder.resolve("config"),(cache,status)->"node",Map.of(),List.of("--no-collect","--no-community","--no-discord"));
    }
    @Test void coldCalculatorStartupIsNotKilledByTheOldSixSecondWindow() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var input=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bundle()));var output=new java.util.zip.ZipOutputStream(bytes)) {
            java.util.zip.ZipEntry entry;
            while((entry=input.getNextEntry())!=null) {
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if(entry.getName().equals("server.mjs"))output.write("await new Promise(resolve=>setTimeout(resolve,8000));\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                input.transferTo(output);output.closeEntry();
            }
        }
        try(var service=new ManagedCompanion(bytes.toByteArray(),folder.resolve("slow-data"),folder.resolve("config"),(cache,status)->"node",Map.of(),List.of("--no-collect","--no-community","--no-discord"))) {
            service.configure(true,port());service.reconcile();
            assertEquals(ManagedCompanion.State.READY,service.state(),service.status());
            assertEquals(0,service.diagnosticState().get("failures"));
        }
    }
    @Test void shippedBundleStartsRestartsAndStopsWithLocalPairingAndHistoryPreserved() throws Exception {
        Path data=folder.resolve("data");Files.createDirectories(data);Files.writeString(data.resolve("collection-status.json"),"user history");
        int port=port();
        try(var service=manager(data)) {
            service.configure(true,port);service.reconcile();assertTrue(service.status().startsWith("Running"),service.status());
            assertEquals("user history",Files.readString(data.resolve("collection-status.json")));
            String key=Files.readString(folder.resolve("config/goofyaddons-discord.key"));assertTrue(key.trim().matches("[a-f0-9]{64}"));
            String settings=Files.readString(data.resolve("discord-settings.json"));assertTrue(settings.contains(key.trim()));
            service.retry();service.reconcile();assertTrue(service.status().startsWith("Running"),service.status());
            assertEquals(settings,Files.readString(data.resolve("discord-settings.json")));assertEquals(key,Files.readString(folder.resolve("config/goofyaddons-discord.key")));
            service.configure(false,port);service.reconcile();assertEquals("Auto-start off",service.status());
        }
        try(var socket=new ServerSocket(port)){assertEquals(port,socket.getLocalPort());} // No orphan listener.
    }
    @Test void reuseDoesNotInstallOrTerminateAnExistingCompanion() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/health",exchange->{byte[] body="{\"protocol\":\"goofy-bazaar-shadow/1\"}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {
            try(var service=new ManagedCompanion(bundle(),folder.resolve("data"),folder.resolve("config"),(cache,status)->{throw new AssertionError("Must reuse");},Map.of())) {
                service.configure(true,server.getAddress().getPort());service.reconcile();assertEquals("Using existing calculator",service.status());
                service.configure(false,server.getAddress().getPort());service.reconcile();
            }
            assertEquals(200,java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/health")).build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {server.stop(0);}
    }
    @Test void anotherInstanceCannotRunASecondCollectorAgainstTheSameHistory() throws Exception {
        try(var first=manager(folder.resolve("data"));var second=manager(folder.resolve("data"))) {
            first.configure(true,port());first.reconcile();assertTrue(first.status().startsWith("Running"),first.status());
            second.configure(true,port());second.reconcile();assertEquals("Calculator owned by another Minecraft instance",second.status());
        }
    }
    @Test void disabledStartupDoesNotDownloadOrExtractAnything() throws Exception {
        try(var service=new ManagedCompanion(bundle(),folder.resolve("data"),folder.resolve("config"),(cache,status)->{throw new AssertionError("Disabled");},Map.of())) {
            service.configure(false,port());service.reconcile();assertEquals("Auto-start off",service.status());assertTrue(service.sharingStatus().contains("Auto-start off"));assertFalse(Files.exists(folder.resolve("data")));
        }
    }
    @Test void unrelatedListenerReportsPortConflictAndChangingPortStartsCalculatorWithoutStoppingListener() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/health",exchange->{exchange.sendResponseHeaders(404,-1);exchange.close();});server.start();
        Path data=folder.resolve("conflict-data");
        try(var service=manager(data)) {
            service.configure(true,server.getAddress().getPort());service.reconcile();
            assertTrue(service.status().contains("occupied by another service"),service.status());
            assertTrue(service.sharingStatus().contains("occupied by another service"),"Sync status must explain the startup failure, not wait silently");
            assertFalse(Files.exists(data),"Port conflict must not launch or unpack another calculator");
            service.configure(true,port());service.reconcile();
            assertTrue(service.status().startsWith("Running"),service.status());
            assertEquals(404,java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/health")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {server.stop(0);}
    }
    @Test void modTradeFeedReachesTheShippedCalculatorWithoutPublishingAccountOrKey() throws Exception {
        Path data=folder.resolve("data");
        String key="private-test-only-"+"b".repeat(40);
        ContributorSettings.save(data,"https://collector.test","owner/repo",key,true,false);
        int port=port();
        try(var service=manager(data)) {
            service.configure(true,port);service.reconcile();assertTrue(service.status().startsWith("Running"),service.status());
            var sample=com.google.gson.JsonParser.parseString("{\"eventId\":\"local-only-receipt\",\"engine\":\"general\",\"inputId\":\"COAL\",\"outputId\":\"COAL\",\"inputUnits\":16,\"batch\":16,\"observedMillis\":120000,\"eligible\":true,\"proceeds\":200,\"profit\":50}").getAsJsonObject();
            long now=System.currentTimeMillis();sample.addProperty("completedAt",now-1000);
            var rows=new com.google.gson.JsonArray();rows.add(sample);
            var packet=ContributorTelemetry.packet(now,rows);
            assertFalse(packet.toString().contains(key));assertFalse(packet.has("account"));assertFalse(packet.has("inventory"));
            var reply=new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisClient().publishExecutions("http://127.0.0.1:"+port+"/v1/recommendations",packet).get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(reply.get("ok").getAsBoolean());assertEquals(1,reply.getAsJsonObject("execution").get("samples").getAsInt());
            String history=Files.readString(data.resolve("execution-history.json"));assertFalse(history.contains(key));assertTrue(history.contains("local-only-receipt"));
        }
    }

}
