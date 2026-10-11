package com.goofy.goofyaddons.diagnostics;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
class DiagnosticLogTest {
    @TempDir Path directory;
    @Test void eventsAreValidJsonCorrelatedAndRedacted() throws Exception {
        var log=new DiagnosticLog(directory,100000,2);
        log.append("ERROR","failure",Map.of("authorization","secret","message","Bearer abc\npassword=xyz","nested",Map.of("apiKey","secret")));
        log.append("INFO","next",Map.of("value",12));
        var lines=Files.readAllLines(directory.resolve("events.jsonl"));
        var first=JsonParser.parseString(lines.get(0)).getAsJsonObject();
        var next=JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assertEquals(first.get("session"),next.get("session"));
        assertEquals(2,next.get("sequence").getAsInt());
        assertFalse(lines.get(0).contains("secret"));assertFalse(first.getAsJsonObject("data").get("message").getAsString().contains("xyz"));assertFalse(first.getAsJsonObject("data").get("message").getAsString().contains("abc"));
    }
    @Test void rotationIsBoundedAndExportIncludesSnapshotAndEvents() throws Exception {
        var log=new DiagnosticLog(directory,200,2);
        for(int i=0;i<10;i++) log.append("INFO","event",Map.of("index",i));
        assertTrue(Files.exists(directory.resolve("events.2.jsonl")));
        assertFalse(Files.exists(directory.resolve("events.3.jsonl")));
        try(var zip=new ZipFile(log.export(Map.of("password","sensitive","status","PAUSED")).toFile())) {
            assertNotNull(zip.getEntry("events.jsonl"));
            String snapshot=new String(zip.getInputStream(zip.getEntry("snapshot.json")).readAllBytes());
            assertFalse(snapshot.contains("sensitive"));assertTrue(snapshot.contains("PAUSED"));
            assertDoesNotThrow(()->JsonParser.parseString(snapshot));
        }
    }
    @Test void exportIncludesAccountJournalsRedacted() throws Exception {
        var log=new DiagnosticLog(directory,1000,1);
        var orders=directory.resolve("craft-orders.json");
        Files.writeString(orders,"[{\"item\":{\"id\":\"PRECURSOR_APPARATUS\"},\"quantity\":6,\"note\":\"api_key=abc123\"}]");
        var broken=directory.resolve("jobs.json");Files.writeString(broken,"{not json");
        try(var zip=new ZipFile(log.export(Map.of(),Map.of("goofyaddons-craft-orders.json",orders,
                "goofyaddons-production-jobs.json",broken,"goofyaddons-book-orders.json",directory.resolve("missing.json"))).toFile())) {
            String craft=new String(zip.getInputStream(zip.getEntry("account/goofyaddons-craft-orders.json")).readAllBytes());
            assertTrue(craft.contains("PRECURSOR_APPARATUS"));assertFalse(craft.contains("abc123"));
            String jobs=new String(zip.getInputStream(zip.getEntry("account/goofyaddons-production-jobs.json")).readAllBytes());
            assertTrue(jobs.contains("unreadable"));
            assertNull(zip.getEntry("account/goofyaddons-book-orders.json"));
        }
    }
    @Test void rawTooltipEvidenceRedactsPlayerAndVendorLines() {
        String text=DiagnosticLog.redact("Order amount: 32x\nBy: [MVP+] privateplayer\n- 2x [VIP+] vendorname 3m ago");
        assertFalse(text.contains("privateplayer"));assertFalse(text.contains("vendorname"));
        assertTrue(text.contains("Order amount: 32x"));
    }
    @Test void exportsAreRetainedAtMostFive() throws Exception {
        var log=new DiagnosticLog(directory,1000,1);
        for(int i=0;i<8;i++) log.export(Map.of("index",i));
        try(var files=Files.list(directory.resolve("bundles"))) {assertTrue(files.count()<=5);}
    }
    @Test void queuedCaptureKeepsOriginalTimeAndAnImmutablePayload() throws Exception {
        var log=new DiagnosticLog(directory,100000,2);
        var nested=new java.util.HashMap<String,Object>();nested.put("count",3);
        var captured=log.capture("ERROR","safety.pause",Map.of("nested",nested));
        nested.put("count",999);
        log.append(captured);
        var event=JsonParser.parseString(Files.readString(directory.resolve("events.jsonl"))).getAsJsonObject();
        assertEquals(captured.time(),event.get("time").getAsString());
        assertEquals(captured.monotonicNanos(),event.get("monotonicNanos").getAsLong());
        assertEquals(3,event.getAsJsonObject("data").getAsJsonObject("nested").get("count").getAsInt());
        assertTrue(event.has("writeTime"));
    }
    @Test void exportIncludesCurrentMenuAndRedactsItsDescriptions() throws Exception {
        var log=new DiagnosticLog(directory,100000,2);
        var menu=Map.of("title","Personal Compactor","lore",List.of("apiKey=private-value"));
        try(var zip=new ZipFile(log.export(Map.of("currentMenu",menu)).toFile())) {
            var entry=zip.getEntry("current-menu.json");assertNotNull(entry);
            String text=new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("Personal Compactor"));assertFalse(text.contains("private-value"));
            assertNotNull(zip.getEntry("snapshot.json"));
        }
    }

    @Test void incidentsSurviveEventRotationAndRestartWithBoundedRedactedEvidence()throws Exception {
        var log=new DiagnosticLog(directory,200,1);
        log.append("WARN","production.run_review",Map.of("job","blocked-job","reason","Unverified claim","apiKey","private-secret"));
        for(int i=0;i<20;i++)log.append("INFO","heartbeat",Map.of("index",i));
        var restarted=new DiagnosticLog(directory,200,1);
        try(var zip=new ZipFile(restarted.export(Map.of("productionDebug",Map.of("reviewJobs",List.of("blocked-job")))).toFile())) {
            assertNotNull(zip.getEntry("production-debug.json"));
            var entries=zip.stream().filter(e->e.getName().startsWith("incidents/")).toList();assertEquals(1,entries.size());
            String text=new String(zip.getInputStream(entries.getFirst()).readAllBytes());
            assertTrue(text.contains("blocked-job"));assertFalse(text.contains("private-secret"));
        }
        for(int i=0;i<20;i++)restarted.append("WARN","production.run_interrupted",Map.of("job","job-"+i));
        try(var files=Files.list(directory.resolve("incidents"))){assertEquals(16,files.count());}
    }
    @Test void oversizedIncidentReportsTruncationInsteadOfGrowingUnbounded()throws Exception {
        var log=new DiagnosticLog(directory,3000000,1);
        log.append("ERROR","failure",Map.of("message","x".repeat(2100000),"authorization","private-secret"));
        try(var files=Files.list(directory.resolve("incidents"))) {
            var file=files.findFirst().orElseThrow();assertTrue(Files.size(file)<40000);
            var incident=JsonParser.parseString(Files.readString(file)).getAsJsonObject();assertTrue(incident.get("evidenceTruncated").getAsBoolean());
            assertFalse(Files.readString(file).contains("private-secret"));
        }
    }

}
