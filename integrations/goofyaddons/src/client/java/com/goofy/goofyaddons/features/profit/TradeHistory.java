package com.goofy.goofyaddons.features.profit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.goofy.goofyaddons.storage.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The durable record every ownership, cost, profit and learning change goes through first.
 *
 * <p>The profit ledger and execution history are snapshots saved separately, so a crash
 * between the in-memory change and a snapshot save used to lose the change, and a crash
 * between the two snapshots left them disagreeing. Each change is now appended here and
 * flushed before its snapshot is saved. On the next load, entries a snapshot lacks are
 * applied again; the ledger's event ids and the samples' event ids make that idempotent, so
 * a receipt is never counted twice. Replaying this file only rebuilds records. It never
 * reaches the game and can never repeat a purchase.
 */
public final class TradeHistory {
    public static final int VERSION = 1;
    /** Above this size the file is rotated once both snapshots hold everything in it. */
    static final long ROTATE_BYTES = 8L * 1024 * 1024;

    public enum Kind { ACQUIRE, SELL, WRITE_OFF, SAMPLE }

    /** One line of the history. Money is coins; trade is the operation id shared across records. */
    public record Entry(int v, Kind kind, long at, String event, String trade, String engine, String item,
                        int units, Double money, ExecutionLedger.Sample sample) {
        public static Entry acquire(long at, String event, String trade, String engine, String item, int units, Double cost) {
            return new Entry(VERSION, Kind.ACQUIRE, at, event, trade, engine, item, units, cost, null);
        }
        public static Entry sell(long at, String event, String trade, String engine, String item, int units, Double proceeds) {
            return new Entry(VERSION, Kind.SELL, at, event, trade, engine, item, units, proceeds, null);
        }
        public static Entry writeOff(long at, String event, String trade, String engine, String item, int units) {
            return new Entry(VERSION, Kind.WRITE_OFF, at, event, trade, engine, item, units, 0.0, null);
        }
        public static Entry sample(long at, ExecutionLedger.Sample sample) {
            return new Entry(VERSION, Kind.SAMPLE, at, sample.eventId(), null, sample.engine(), sample.outputId(), sample.inputUnits(), null, sample);
        }
    }

    /** What applying the history to loaded snapshots changed. */
    public record Replay(int ledgerEntries, int samples) {
        public boolean ledgerChanged() { return ledgerEntries > 0; }
        public boolean samplesChanged() { return samples > 0; }
    }

    private static final Gson GSON = new Gson();
    private final Path path;

    public TradeHistory(Path path) { this.path = path; }

    public Path path() { return path; }

    public void append(Entry entry) throws IOException {
        AtomicFiles.appendLine(path, GSON.toJson(entry));
    }

    /**
     * Every entry in order. A final line cut short by a crash mid-append is skipped; any
     * other unreadable line rejects the file so nothing is rebuilt from a damaged record.
     */
    public List<Entry> read() throws IOException {
        var entries = new ArrayList<Entry>();
        for (Path file : List.of(rotated(), path)) {
            if (!Files.exists(file)) continue;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty()) continue;
                boolean last = i == lines.length - 1; // no trailing newline: the append never finished
                try {
                    Entry entry = GSON.fromJson(line, Entry.class);
                    if (entry == null || entry.v() != VERSION || entry.kind() == null || entry.event() == null || entry.event().isBlank())
                        throw new JsonParseException("Invalid history entry");
                    entries.add(entry);
                } catch (JsonParseException | IllegalStateException bad) {
                    if (last && file.equals(path)) break;
                    throw new IOException("Transaction history line " + (i + 1) + " of " + file.getFileName() + " is unreadable", bad);
                }
            }
        }
        return entries;
    }

    /** Applies entries the snapshots lack. Ledger entries are skipped when the ledger is unusable. */
    public Replay replay(ProfitLedger ledger, ExecutionLedger execution, long now) throws IOException {
        int ledgerEntries = 0, samples = 0;
        for (Entry entry : read()) {
            switch (entry.kind()) {
                case ACQUIRE -> { if (ledger != null && ledger.acquire(entry.trade(), entry.engine(), entry.item(), entry.event(), entry.units(), entry.money())) ledgerEntries++; }
                case SELL -> { if (ledger != null && ledger.sell(entry.trade(), entry.engine(), entry.item(), entry.event(), entry.units(), entry.money())) ledgerEntries++; }
                case WRITE_OFF -> { if (ledger != null && ledger.writeOff(entry.trade(), entry.engine(), entry.item(), entry.event(), entry.units())) ledgerEntries++; }
                case SAMPLE -> { if (execution != null && execution.restore(entry.sample(), now)) samples++; }
            }
        }
        return new Replay(ledgerEntries, samples);
    }

    /**
     * Starts a fresh file. Call only right after both snapshots were saved,
     * so every entry being set aside is already reflected in them.
     */
    public void rotate() throws IOException {
        if (Files.exists(path)) Files.move(path, rotated(), StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean large() throws IOException { return Files.exists(path) && Files.size(path) >= ROTATE_BYTES; }

    private Path rotated() { return path.resolveSibling(path.getFileName() + ".1"); }
}
