package com.goofy.goofyaddons.features.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CompanionLogTailTest {
    @TempDir Path directory;

    @Test
    void tailIsBoundedToTheLastTwelveLines() throws Exception {
        Path log = directory.resolve("companion-error.log");
        var text = new StringBuilder();
        for (int i = 0; i < 40; i++) text.append("line ").append(i).append('\n');
        Files.writeString(log, text);
        var tail = ManagedCompanion.errorLogTail(log);
        assertEquals(12, tail.size());
        assertEquals("line 39", tail.getLast());
        assertEquals("line 28", tail.getFirst());
    }

    @Test
    void largeLogsAreReadFromTheEndOnly() throws Exception {
        Path log = directory.resolve("companion-error.log");
        Files.writeString(log, "x".repeat(20_000) + "\nError: listen EADDRINUSE 127.0.0.1:8789\n");
        var tail = ManagedCompanion.errorLogTail(log);
        assertEquals("Error: listen EADDRINUSE 127.0.0.1:8789", tail.getLast());
        assertTrue(tail.stream().allMatch(line -> line.length() <= 301));
    }

    @Test
    void missingLogIsEmpty() {
        assertTrue(ManagedCompanion.errorLogTail(directory.resolve("absent.log")).isEmpty());
    }

    @Test
    void keysAndTokensAreRedacted() {
        assertEquals("token: [redacted] failed", ManagedCompanion.redact("token: abc.def failed"));
        assertEquals("{\"key\":\"[redacted]\"}", ManagedCompanion.redact("{\"key\":\"s3cret\"}"));
        assertEquals("Authorization=[redacted]", ManagedCompanion.redact("Authorization=Bot123"));
        assertEquals("bad [redacted]", ManagedCompanion.redact("bad " + "A".repeat(40)));
        assertEquals("Error: listen EADDRINUSE 127.0.0.1:8789", ManagedCompanion.redact("Error: listen EADDRINUSE 127.0.0.1:8789"));
    }
}
