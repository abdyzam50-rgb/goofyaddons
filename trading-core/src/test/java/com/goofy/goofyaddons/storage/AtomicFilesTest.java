package com.goofy.goofyaddons.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
    @TempDir Path dir;

    @Test void replaceLeavesOnlyTheNewContentAndNoTemporaryFiles() throws Exception {
        Path file = dir.resolve("nested/orders.json");
        AtomicFiles.replace(file, "[1]", "orders-");
        AtomicFiles.replace(file, "[1,2]", "orders-");
        assertEquals("[1,2]", Files.readString(file));
        try (var listing = Files.list(file.getParent())) { assertEquals(1, listing.count()); }
    }

    @Test void appendAddsWholeLinesAndRefusesEmbeddedNewlines() throws Exception {
        Path file = dir.resolve("history.jsonl");
        AtomicFiles.appendLine(file, "{\"a\":1}");
        AtomicFiles.appendLine(file, "{\"a\":2}");
        assertEquals("{\"a\":1}\n{\"a\":2}\n", Files.readString(file));
        assertThrows(IllegalArgumentException.class, () -> AtomicFiles.appendLine(file, "{\n}"));
    }
}
