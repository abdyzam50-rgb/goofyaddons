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

}
