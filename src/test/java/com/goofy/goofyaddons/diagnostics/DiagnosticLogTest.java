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
        assertFalse(lines.get(0).contains("secret"));assertFalse(lines.get(0).contains("xyz"));assertFalse(lines.get(0).contains("abc"));
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
    @Test void exportsAreRetainedAtMostFive() throws Exception {
        var log=new DiagnosticLog(directory,1000,1);
        for(int i=0;i<8;i++) log.export(Map.of("index",i));
        try(var files=Files.list(directory.resolve("bundles"))) {assertTrue(files.count()<=5);}
    }
}
