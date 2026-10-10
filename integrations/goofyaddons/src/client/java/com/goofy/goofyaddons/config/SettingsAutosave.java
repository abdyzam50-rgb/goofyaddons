package com.goofy.goofyaddons.config;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Debounces validated edits; failed writes are retried only after another edit or explicit retry. */
public final class SettingsAutosave {
    private final SettingsDraft draft;
    private final BooleanSupplier editable;
    private final LongSupplier clock;
    private long observed = -1, attempted = -1, changedAt;
    public SettingsAutosave(SettingsDraft draft, BooleanSupplier editable, LongSupplier clock) {
        this.draft = draft; this.editable = editable; this.clock = clock;
    }
    public String tick() {
        observe();
        return clock.getAsLong() - changedAt >= 750 ? flush(false) : null;
    }
    public String flush(boolean retry) {
        observe();
        if (!draft.dirty()) return null;
        if (!draft.canApply()) return draft.summary();
        if (!editable.getAsBoolean()) return "Not saved yet: stop trading to save these changes automatically.";
        if (!retry && attempted == observed) return null;
        attempted = observed;
        return draft.apply();
    }
    private void observe() {
        if (observed != draft.revision()) {
            observed = draft.revision(); changedAt = clock.getAsLong();
        }
    }
}
