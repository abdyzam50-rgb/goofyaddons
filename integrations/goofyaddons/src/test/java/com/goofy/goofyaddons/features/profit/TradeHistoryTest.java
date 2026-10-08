package com.goofy.goofyaddons.features.profit;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TradeHistoryTest {
    @TempDir Path dir;

    private static final long NOW = 1_700_000_000_000L;

    @Test void crashBeforeTheSnapshotSaveIsRecoveredExactlyOnce() throws Exception {
        var history = new TradeHistory(dir.resolve("transactions.jsonl"));
        var saved = new ProfitLedger();
        saved.acquire("t1", "books", "Wisdom I", "buy-1", 16, 1600.0);
        history.append(TradeHistory.Entry.acquire(NOW, "buy-1", "t1", "books", "Wisdom I", 16, 1600.0));
        saved.write(dir.resolve("profit.json"));
        // The sale reaches the history, then the client dies before the ledger is saved.
        history.append(TradeHistory.Entry.sell(NOW + 1, "sale-1", "t1", "books", "Wisdom I", 16, 2000.0));

        var reloaded = ProfitLedger.read(dir.resolve("profit.json"));
        var replay = history.replay(reloaded, new ExecutionLedger(), NOW);
        assertEquals(1, replay.ledgerEntries());
        assertEquals(400.0, reloaded.summary().profit(), 1e-9);

        var again = history.replay(reloaded, new ExecutionLedger(), NOW);
        assertEquals(0, again.ledgerEntries(), "a receipt is never counted twice");
        assertEquals(400.0, reloaded.summary().profit(), 1e-9);
        assertEquals(1, reloaded.summary().settlements());
    }

    @Test void ownershipSurvivesWhenOnlyTheHistoryWasWritten() throws Exception {
        var history = new TradeHistory(dir.resolve("transactions.jsonl"));
        history.append(TradeHistory.Entry.acquire(NOW, "claim-1", "g1", "general", "Enchanted Coal", 64, 640.0));
        var fresh = new ProfitLedger();
        history.replay(fresh, null, NOW);
        assertEquals(640.0, fresh.openCost("g1"), 1e-9);
        assertEquals(320.0, fresh.knownCost("g1", 32), 1e-9);
    }

    @Test void samplesMissingFromTheSavedFileAreRestored() throws Exception {
        var history = new TradeHistory(dir.resolve("transactions.jsonl"));
        var live = new ExecutionLedger();
        live.begin("t1", "books", "ENCHANTMENT_WISDOM_1", "ENCHANTMENT_WISDOM_5", 16, 1, NOW - 600_000);
        var sample = live.complete("t1", "sale-1", 16, 2000.0, 400.0, NOW, false);
        assertNotNull(sample);
        history.append(TradeHistory.Entry.sample(NOW, sample));

        var reloaded = new ExecutionLedger();
        assertEquals(1, history.replay(null, reloaded, NOW).samples());
        assertEquals(1, reloaded.samples().size());
        assertEquals(0, history.replay(null, reloaded, NOW).samples());
    }

    @Test void aLineCutShortByACrashIsSkippedButADamagedOneStopsTheReplay() throws Exception {
        Path file = dir.resolve("transactions.jsonl");
        var history = new TradeHistory(file);
        history.append(TradeHistory.Entry.acquire(NOW, "buy-1", "t1", "books", "Wisdom I", 16, 1600.0));
        Files.writeString(file, Files.readString(file) + "{\"v\":1,\"kind\":\"SELL\",\"ev");
        assertEquals(1, history.read().size());

        Files.writeString(file, "not json\n" + Files.readString(file));
        assertThrows(java.io.IOException.class, history::read);
    }

    @Test void rotatedEntriesAreStillReplayed() throws Exception {
        Path file = dir.resolve("transactions.jsonl");
        var history = new TradeHistory(file);
        history.append(TradeHistory.Entry.acquire(NOW, "buy-1", "t1", "books", "Wisdom I", 16, 1600.0));
        history.rotate();
        history.append(TradeHistory.Entry.sell(NOW + 1, "sale-1", "t1", "books", "Wisdom I", 16, 2000.0));
        var fresh = new ProfitLedger();
        assertEquals(2, history.replay(fresh, null, NOW).ledgerEntries());
        assertEquals(400.0, fresh.summary().profit(), 1e-9);
    }
}
