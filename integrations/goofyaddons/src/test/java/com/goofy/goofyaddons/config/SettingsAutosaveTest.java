package com.goofy.goofyaddons.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsAutosaveTest {
    private GoofyConfig saved = new GoofyConfig();
    private long now;
    private int commits;
    private boolean editable = true, fail;
    private final SettingsDraft draft = new SettingsDraft(() -> saved, candidate -> {
        commits++;
        if (fail) throw new IllegalStateException("disk unavailable");
        saved = candidate;
    });
    private final SettingsAutosave autosave = new SettingsAutosave(draft, () -> editable, () -> now);
    private void slots(String text) { draft.whole("Book slots", text, 1, 10, (cfg, v) -> cfg.maxActiveBooks = v); }

    @Test void typingDebouncesAndCommitsOnlyTheLatestValue() {
        slots("3"); assertNull(autosave.tick());
        now=700; slots("4"); assertNull(autosave.tick());
        now=1449; assertNull(autosave.tick()); assertEquals(0, commits);
        now=1450; assertEquals("Settings saved.", autosave.tick());
        assertEquals(4, saved.maxActiveBooks);
        now=2000; assertNull(autosave.tick()); assertEquals(1, commits);
    }
    @Test void invalidTextNeverCommitsAndCorrectionSaves() {
        slots(""); autosave.tick(); now=800;
        assertTrue(autosave.tick().startsWith("Fix Book slots")); assertEquals(0, commits);
        slots("5"); assertEquals("Settings saved.", autosave.flush(false));
        assertEquals(5, saved.maxActiveBooks);
    }
    @Test void toggleAndCloseFlushWithoutWaitingAndKeepOtherChanges() {
        draft.edit("Rest", cfg -> cfg.restSchedule.enabled=true);
        assertEquals("Settings saved.", autosave.flush(false));
        slots("4");
        saved.toggleKey=300;
        assertEquals("Settings saved.", autosave.flush(false));
        assertEquals(300, saved.toggleKey); assertEquals(4, saved.maxActiveBooks);
    }
    @Test void discardCancelsAnUncommittedTextEdit() {
        slots("4"); autosave.tick(); draft.discard();
        assertNull(autosave.flush(false)); now=1000; assertNull(autosave.tick());
        assertEquals(0, commits); assertEquals(2, saved.maxActiveBooks);
    }
    @Test void tradingDefersUntilStoppedAndFailedWritesDoNotLoop() {
        slots("3"); editable=false;
        assertTrue(autosave.flush(false).contains("stop trading")); assertEquals(0, commits);
        editable=true; fail=true;
        assertEquals("Not saved: disk unavailable", autosave.flush(false));
        now=1000; assertNull(autosave.tick()); assertEquals(1, commits);
        fail=false; assertEquals("Settings saved.", autosave.flush(true)); assertEquals(2, commits);
    }
}
