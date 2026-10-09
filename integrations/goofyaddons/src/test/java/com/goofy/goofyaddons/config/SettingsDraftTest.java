package com.goofy.goofyaddons.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsDraftTest {
    private GoofyConfig saved = new GoofyConfig();
    private final List<GoofyConfig> commits = new ArrayList<>();
    private final SettingsDraft draft = new SettingsDraft(() -> saved, candidate -> {
        candidate.validate();
        commits.add(candidate);
        saved = candidate;
    });

    @Test
    void typingNeverCommitsUntilApply() {
        draft.whole("Book slots", "3", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertTrue(commits.isEmpty());
        assertEquals(2, saved.maxActiveBooks);
        assertEquals(3, draft.view().maxActiveBooks);
        assertEquals(List.of("Book slots"), draft.changes());
        assertTrue(draft.canApply());
        assertEquals("Settings saved.", draft.apply());
        assertEquals(1, commits.size());
        assertEquals(3, saved.maxActiveBooks);
        assertFalse(draft.dirty());
    }

    @Test
    void unparsableTextIsAFieldErrorThatBlocksApplyAndKeepsTheText() {
        draft.whole("Book slots", "3x", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertEquals("Enter a whole number.", draft.error("Book slots"));
        assertEquals("3x", draft.raw("Book slots"));
        assertTrue(draft.dirty());
        assertFalse(draft.canApply());
        assertTrue(draft.apply().startsWith("Fix Book slots"));
        assertTrue(commits.isEmpty());
        draft.whole("Book slots", "11", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertEquals("Use a whole number from 1 to 10.", draft.error("Book slots"));
        draft.whole("Book slots", "4", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertNull(draft.error("Book slots"));
        assertTrue(draft.canApply());
    }

    @Test
    void aControlsOwnCheckBecomesThatFieldsError() {
        draft.edit("Purse reserve", cfg -> cfg.purseReserve = 1);
        draft.decimal("Capital limit", "5", (cfg, v) -> { throw new IllegalArgumentException("Too small"); });
        assertEquals("Too small", draft.error("Capital limit"));
        assertEquals("5", draft.raw("Capital limit"));
        assertEquals(1, draft.view().purseReserve);
        assertEquals(List.of("Purse reserve"), draft.changes());
    }

    @Test
    void wholeCandidateValidationIsShownBeforeApply() {
        draft.edit("Minimum delay", cfg -> cfg.minActionDelay = 5000);
        assertNotNull(draft.problem());
        assertFalse(draft.canApply());
        assertTrue(draft.apply().startsWith("Not saved"));
        assertTrue(commits.isEmpty());
    }

    @Test
    void revertingAValueLeavesNothingToSave() {
        draft.whole("Book slots", "5", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        draft.whole("Book slots", "2", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertFalse(draft.dirty());
        assertEquals("No unsaved changes.", draft.apply());
        assertTrue(commits.isEmpty());
    }

    @Test
    void changesCommittedElsewhereAreKeptWhenTheDraftApplies() {
        draft.whole("Book slots", "4", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        GoofyConfig keybind = new GoofyConfig();
        keybind.toggleKey = 300;
        saved = keybind; // A keybind change committed while the draft was open.
        assertEquals(300, draft.view().toggleKey);
        draft.apply();
        assertEquals(300, saved.toggleKey);
        assertEquals(4, saved.maxActiveBooks);
    }

    @Test
    void portAndAutoStartChangesAnnounceTheirRestart() {
        saved.marketAnalysis.autoStartCompanion = true;
        draft.whole("Calculator port", "8790", 1024, 65535,
                (cfg, v) -> cfg.marketAnalysis.endpoint = "http://127.0.0.1:" + v + "/v1/recommendations");
        assertEquals(List.of("the bundled calculator restarts on port 8790"), draft.restarts());
        assertTrue(draft.summary().contains("On Apply, the bundled calculator restarts on port 8790."));
        assertEquals("Settings saved; the bundled calculator restarts on port 8790.", draft.apply());

        draft.edit("Auto-start", cfg -> cfg.marketAnalysis.autoStartCompanion = false);
        assertEquals(List.of("the bundled calculator stops"), draft.restarts());
        draft.discard();
        assertFalse(draft.dirty());
        assertTrue(draft.restarts().isEmpty());
    }

    @Test
    void failedCommitKeepsTheDraft() {
        var refusing = new SettingsDraft(() -> saved, candidate -> { throw new IllegalStateException("Stop trading before editing settings"); });
        refusing.whole("Book slots", "3", 1, 10, (cfg, v) -> cfg.maxActiveBooks = v);
        assertEquals("Not saved: Stop trading before editing settings", refusing.apply());
        assertTrue(refusing.canApply());
        assertEquals(3, refusing.view().maxActiveBooks);
    }
}
