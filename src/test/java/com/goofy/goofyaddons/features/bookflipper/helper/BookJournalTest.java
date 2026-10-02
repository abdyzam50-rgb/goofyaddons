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
    @Test void outstandingOwnershipSurvivesProcessRestart() throws Exception {
        Path path=dir.resolve("books.json");
        BookJournal journal=new BookJournal(path);
        assertTrue(journal.read().isEmpty());
        journal.write(List.of(new BookJournal.Position(book,1000000)));
        assertEquals(List.of(new BookJournal.Position(book,1000000)),new BookJournal(path).read());
    }
    @Test void corruptedOwnershipIsRejectedWithoutOverwritingEvidence() throws Exception {
        Path path=dir.resolve("books.json"); Files.writeString(path,"broken");
        assertThrows(Exception.class,()->new BookJournal(path).read());
        assertEquals("broken",Files.readString(path));
    }
    @Test void duplicateAndInvalidCostsBlockRecovery() throws Exception {
        Path path=dir.resolve("books.json"); BookJournal journal=new BookJournal(path);
        journal.write(List.of(new BookJournal.Position(book,1),new BookJournal.Position(book,2)));
        assertThrows(Exception.class, journal::read);
        journal.write(List.of(new BookJournal.Position(book,-1)));
        assertThrows(Exception.class, journal::read);
    }
    @Test void completedOwnershipCanBeClearedWithoutLeavingTemporaryFiles() throws Exception {
        Path path=dir.resolve("books.json"); BookJournal journal=new BookJournal(path);
        journal.write(List.of(new BookJournal.Position(book,1))); journal.write(List.of());
        assertTrue(new BookJournal(path).read().isEmpty());
        try(var files=Files.list(dir)){assertEquals(1,files.count());}
    }
    @Test void stoppingBeforeSubmissionLeavesNoRestartBarrier() throws Exception {
        Path path = dir.resolve("books.json");
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookJournal.Position(book, 1000000)), java.util.Set.of());
        assertTrue(new BookJournal(path).read().isEmpty());
    }
    @Test void uncertainSubmissionSurvivesRestartWithoutRetainingOtherPlans() throws Exception {
        Path path = dir.resolve("books.json");
        Book other = new Book("ENCHANTMENT_OVERLOAD", 1, 5, "Overload", 0, 0);
        BookJournal journal = new BookJournal(path);
        journal.writeTracked(List.of(new BookJournal.Position(book, 1000000),
                new BookJournal.Position(other, 2000000)), java.util.Set.of(book.id()));
        assertEquals(List.of(new BookJournal.Position(book, 1000000)), new BookJournal(path).read());
        // A later snapshot of unused plans must not repopulate a completed barrier.
        journal.writeTracked(List.of(new BookJournal.Position(other, 2000000)), java.util.Set.of());
        assertTrue(new BookJournal(path).read().isEmpty());
    }
}
