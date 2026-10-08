package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookJournalTest {
    @TempDir Path dir;
    private final Book book = new Book("ENCHANTMENT_ULTIMATE_WISDOM", 1, 5, "Wisdom", 0, 0);
    @Test void legacyJsonRetainsItsFieldContractAfterThePositionModelExtraction() throws Exception {
        var path=dir.resolve("legacy.json");
        var legacy="""
            [{"book":{"id":"ENCHANTMENT_ULTIMATE_WISDOM","level":1,"sellLevel":5,"name":"Wisdom",
              "instaSellPercentage":0,"instaBuyPercentage":0},"cost":1600,"tradeId":"legacy-trade",
              "retiring":false,"progressAt":0,"orphanCleanup":false}]
            """;
        Files.writeString(path,legacy);var journal=new BookJournal(path);
        var positions=journal.read();assertEquals("legacy-trade",positions.getFirst().tradeId());
        journal.write(positions);
        assertEquals(com.google.gson.JsonParser.parseString(legacy),com.google.gson.JsonParser.parseString(Files.readString(path)));
    }
    @Test void outstandingOwnershipSurvivesProcessRestart() throws Exception {
        Path path=dir.resolve("books.json");
        BookJournal journal=new BookJournal(path);
        assertTrue(journal.read().isEmpty());
        journal.write(List.of(new BookPosition(book,1000000)));
        assertEquals(List.of(new BookPosition(book,1000000)),new BookJournal(path).read());
    }
    @Test void corruptedOwnershipIsRejectedWithoutOverwritingEvidence() throws Exception {
        Path path=dir.resolve("books.json"); Files.writeString(path,"broken");
        assertThrows(Exception.class,()->new BookJournal(path).read());
        assertEquals("broken",Files.readString(path));
    }
    @Test void duplicateAndInvalidCostsBlockRecovery() throws Exception {
        Path path=dir.resolve("books.json"); BookJournal journal=new BookJournal(path);
        journal.write(List.of(new BookPosition(book,1),new BookPosition(book,2)));
        assertThrows(Exception.class, journal::read);
        journal.write(List.of(new BookPosition(book,-1)));
        assertThrows(Exception.class, journal::read);
    }
    @Test void completedOwnershipCanBeClearedWithoutLeavingTemporaryFiles() throws Exception {
        Path path=dir.resolve("books.json"); BookJournal journal=new BookJournal(path);
        journal.write(List.of(new BookPosition(book,1))); journal.write(List.of());
        assertTrue(new BookJournal(path).read().isEmpty());
        try(var files=Files.list(dir)){assertEquals(1,files.count());}
    }
    @Test void stoppingBeforeSubmissionLeavesNoRestartBarrier() throws Exception {
        Path path = dir.resolve("books.json");
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of());
        assertTrue(new BookJournal(path).read().isEmpty());
    }
    @Test void anUnchangedJournalIsNotRewritten() throws Exception {
        Path path = dir.resolve("books.json");
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of(book.id()));
        long firstWrite = Files.getLastModifiedTime(path).toMillis();
        Files.writeString(path, "SENTINEL");
        // Identical input must not touch the file again.
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of(book.id()));
        assertEquals("SENTINEL", Files.readString(path), "redundant write should have been skipped");
        assertTrue(firstWrite > 0);
    }

    @Test void aChangedJournalIsAlwaysWritten() throws Exception {
        Path path = dir.resolve("books.json");
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of(book.id()));
        // A different cost, a different exposure set, and an emptied journal must each land.
        journal.writeTracked(List.of(new BookPosition(book, 2000000)), java.util.Set.of(book.id()));
        assertEquals(List.of(new BookPosition(book, 2000000)), new BookJournal(path).read());
        journal.writeTracked(List.of(new BookPosition(book, 2000000)), java.util.Set.of());
        assertTrue(new BookJournal(path).read().isEmpty());
    }

    @Test void aDirectWriteIsNeverMaskedByTheTrackedCache() throws Exception {
        Path path = dir.resolve("books.json");
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of(book.id()));
        journal.write(List.of());
        assertTrue(new BookJournal(path).read().isEmpty());
        // The same tracked input as before must be written again, not skipped.
        journal.writeTracked(List.of(new BookPosition(book, 1000000)), java.util.Set.of(book.id()));
        assertEquals(List.of(new BookPosition(book, 1000000)), new BookJournal(path).read());
    }

    @Test void uncertainSubmissionSurvivesRestartWithoutRetainingOtherPlans() throws Exception {
        Path path = dir.resolve("books.json");
        Book other = new Book("ENCHANTMENT_OVERLOAD", 1, 5, "Overload", 0, 0);
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookPosition(book, 1000000),
                new BookPosition(other, 2000000)), java.util.Set.of(book.id()));
        assertEquals(List.of(new BookPosition(book, 1000000)), new BookJournal(path).read());
        // A later snapshot of unused plans must not repopulate a completed barrier.
        journal.writeTracked(List.of(new BookPosition(other, 2000000)), java.util.Set.of());
        assertTrue(new BookJournal(path).read().isEmpty());
    }
    @Test void verifiedStaleEntriesAreBackedUpAndOnlyObservedOwnershipIsRetained() throws Exception {
        var other=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
        var path=dir.resolve("books.json");var journal=new BookJournal(path);
        var saved=List.of(new BookPosition(book,1000),new BookPosition(other,2000));
        journal.write(saved);journal.reconcileVerified(saved,java.util.Set.of(other.id()));
        assertEquals(List.of(saved.get(1)),journal.read());
        try(var files=Files.list(dir)) {assertEquals(2,files.count());}
        journal.reconcileVerified(journal.read(),java.util.Set.of());assertTrue(journal.read().isEmpty());
    }
    @Test void aJournalChangedDuringLiveVerificationIsNeverCleared() throws Exception {
        var journal=new BookJournal(dir.resolve("books.json"));var before=List.of(new BookPosition(book,1000));
        journal.write(before);journal.write(List.of(new BookPosition(book,2000)));
        assertThrows(IllegalStateException.class,()->journal.reconcileVerified(before,java.util.Set.of()));
        assertEquals(2000,journal.read().getFirst().cost());
    }

    @Test void resumedTradeIdentitySurvivesAnotherProcessRestartAndOriginalIsBackedUp() throws Exception {
        var path=dir.resolve("books.json");var journal=new BookJournal(path);
        var legacy=List.of(new BookPosition(book,1600));journal.write(legacy);journal.backupVerified(legacy);
        var resumed=List.of(new BookPosition(book,1600,"saved-trade"));journal.write(resumed);
        assertEquals(resumed,new BookJournal(path).read());
        try(var files=Files.list(dir)){var backup=files.filter(p->p.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertEquals(legacy,new BookJournal(backup).read());}
    }
    @Test void originalLegacyJsonWithoutTradeIdentityStillLoads() throws Exception {
        var path=dir.resolve("books.json");Files.writeString(path,"[{\"book\":{\"id\":\"ENCHANTMENT_ULTIMATE_WISDOM\",\"level\":1,\"sellLevel\":5,\"name\":\"Wisdom\"},\"cost\":1600}]");
        assertNull(new BookJournal(path).read().getFirst().tradeId());
    }
    @Test void retirementIntentAndProgressSurviveRestartWithLegacyDefaults() throws Exception {
        var path=dir.resolve("retirement.json");var journal=new BookJournal(path);
        var p=new BookPosition(book,1000000,"old-trade",true,1000,true);
        journal.write(List.of(p));assertEquals(List.of(p),new BookJournal(path).read());
        Files.writeString(path,"[{\"book\":{\"id\":\"ENCHANTMENT_ULTIMATE_WISDOM\",\"level\":1,\"sellLevel\":5,\"name\":\"Wisdom\"},\"cost\":1000000,\"tradeId\":\"old-trade\"}]");
        var old=new BookJournal(path).read().getFirst();assertFalse(old.retiring());assertFalse(old.orphanCleanup());assertEquals(0,old.progressAt());
    }
    @Test void repriceAndQuoteRefreshDoNotCountAsBookProgress() {
        Task task=new Task(book,false,false);task.progress(1000);
        task.recordPlacement(2000);task.recordPlacement(3000);task.markOrderObserved(4000);
        assertFalse(task.stale(900999,900000));assertTrue(task.stale(901000,900000));
        task.progress(901000);assertFalse(task.stale(901001,900000));
        task.retire();assertTrue(task.retiring());
    }
}
