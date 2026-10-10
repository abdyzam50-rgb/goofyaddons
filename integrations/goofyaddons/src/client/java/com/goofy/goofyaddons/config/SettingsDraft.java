package com.goofy.goofyaddons.config;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Validated settings edits shared by explicit saves and {@link SettingsAutosave}.
 * Named edits replay over the latest saved settings so concurrent keybind or reload
 * changes are preserved. Invalid typed values stay as field errors and never commit.
 */
public final class SettingsDraft {
    @FunctionalInterface public interface Committer { void commit(GoofyConfig candidate) throws Exception; }

    private static final Gson GSON = new Gson();

    private final Supplier<GoofyConfig> current;
    private final Committer committer;
    private final Map<String, Consumer<GoofyConfig>> edits = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Map<String, String> raw = new LinkedHashMap<>();
    private GoofyConfig base, preview;
    private List<String> changes = List.of(), restarts = List.of();
    private String problem;
    private long revision;
    public long revision() { return revision; }

    public SettingsDraft(Supplier<GoofyConfig> current, Committer committer) {
        this.current = current;
        this.committer = committer;
    }

    /** The draft over the live settings, committed through {@link GoofyConfig#commitSettings}. */
    public static SettingsDraft live() {
        return new SettingsDraft(() -> GoofyConfig.INSTANCE, GoofyConfig::commitSettings);
    }

    /** The settings as they would be after Apply. Read-only for callers. */
    public GoofyConfig view() {
        refresh();
        return preview;
    }

    public void edit(String label, Consumer<GoofyConfig> change) {
        revision++;
        errors.remove(label);
        raw.remove(label);
        edits.remove(label); // Re-inserted last so a later edit of the same control wins.
        edits.put(label, change);
        rebuild();
    }

    /** A decimal typed into a field; unparsable text becomes a field error. */
    public void decimal(String label, String text, BiConsumer<GoofyConfig, Double> set) {
        double value;
        try {
            value = Double.parseDouble(text.strip());
        } catch (NumberFormatException invalid) {
            reject(label, text, "Enter a number.");
            return;
        }
        if (!Double.isFinite(value)) { reject(label, text, "Enter a number."); return; }
        edit(label, cfg -> set.accept(cfg, value));
        keepRawIfRejected(label, text);
    }

    /** A whole number typed into a field, bounded to {@code min..max}. */
    public void whole(String label, String text, long min, long max, BiConsumer<GoofyConfig, Integer> set) {
        long value;
        try {
            value = Long.parseLong(text.strip());
        } catch (NumberFormatException invalid) {
            reject(label, text, "Enter a whole number.");
            return;
        }
        if (value < min || value > max) { reject(label, text, "Use a whole number from " + min + " to " + max + "."); return; }
        edit(label, cfg -> set.accept(cfg, (int) value));
        keepRawIfRejected(label, text);
    }

    public String error(String label) { return errors.get(label); }
    /** What the player typed into a field that is in error, so it is not replaced on screen. */
    public String raw(String label) { return raw.get(label); }

    public boolean dirty() { refresh(); return !changes.isEmpty() || !errors.isEmpty(); }
    public boolean canApply() { refresh(); return !changes.isEmpty() && errors.isEmpty() && problem == null; }
    /** Labels of controls whose recorded change differs from the saved settings. */
    public List<String> changes() { refresh(); return changes; }
    /** Restarts Apply would cause, in words. */
    public List<String> restarts() { refresh(); return restarts; }
    /** Why the whole candidate is invalid, or null. */
    public String problem() { refresh(); return problem; }
    public Map<String, String> errors() { return Map.copyOf(errors); }

    /** One line for the screen: pending changes, restarts and anything blocking Apply. */
    public String summary() {
        refresh();
        if (!errors.isEmpty()) return "Fix " + String.join(", ", errors.keySet()) + ": " + errors.values().iterator().next();
        if (problem != null) return "Not valid yet: " + problem;
        if (changes.isEmpty()) return "No unsaved changes.";
        String text = "Unsaved: " + String.join(", ", changes) + ".";
        if (!restarts.isEmpty()) text += " When saved, " + String.join("; ", restarts) + ".";
        return text;
    }

    /** Validates and commits the draft. The message says what happened, including restarts. */
    public String apply() {
        refresh();
        if (!errors.isEmpty()) return summary();
        if (changes.isEmpty()) return "No unsaved changes.";
        // Rebuild against the settings saved right now, so nothing committed meanwhile is lost.
        rebuild();
        if (problem != null) return "Not saved: " + problem;
        List<String> caused = restarts;
        try {
            committer.commit(preview);
        } catch (Exception failure) {
            return "Not saved: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
        edits.clear();
        rebuild();
        return caused.isEmpty() ? "Settings saved." : "Settings saved; " + String.join("; ", caused) + ".";
    }

    public void discard() {
        revision++;
        edits.clear();
        errors.clear();
        raw.clear();
        rebuild();
    }

    private void reject(String label, String text, String message) {
        revision++;
        errors.put(label, message);
        raw.put(label, text);
        edits.remove(label);
        rebuild();
    }

    private void keepRawIfRejected(String label, String text) {
        if (errors.containsKey(label)) raw.put(label, text);
    }

    private void refresh() {
        if (preview == null || base != current.get()) rebuild();
    }

    private void rebuild() {
        base = current.get();
        String saved = GSON.toJson(base);
        preview = copy(saved);
        var labels = new ArrayList<String>();
        for (var edit : List.copyOf(edits.entrySet())) {
            GoofyConfig alone = copy(saved);
            try {
                edit.getValue().accept(preview);
                edit.getValue().accept(alone);
            } catch (RuntimeException rejected) {
                // A control's own check (a port range, a whole slot count) is that field's error.
                edits.remove(edit.getKey());
                errors.put(edit.getKey(), rejected.getMessage() == null ? "Invalid value." : rejected.getMessage());
                rebuild(); // Start over without it, so no other change is applied twice.
                return;
            }
            if (!GSON.toJson(alone).equals(saved)) labels.add(edit.getKey());
        }
        changes = List.copyOf(labels);
        restarts = restartsBetween(base, preview);
        try {
            preview.validate();
            problem = null;
        } catch (RuntimeException invalid) {
            problem = invalid.getMessage() == null ? "Invalid settings." : invalid.getMessage();
        }
        if (changes.isEmpty() && errors.isEmpty()) problem = null;
    }

    static List<String> restartsBetween(GoofyConfig saved, GoofyConfig candidate) {
        var restarts = new ArrayList<String>();
        boolean wasOn = saved.marketAnalysis.autoStartCompanion, on = candidate.marketAnalysis.autoStartCompanion;
        int oldPort = port(saved), newPort = port(candidate);
        if (wasOn && !on) restarts.add("the bundled calculator stops");
        else if (!wasOn && on) restarts.add("the bundled calculator starts on port " + newPort);
        else if (on && oldPort != newPort) restarts.add("the bundled calculator restarts on port " + newPort);
        return List.copyOf(restarts);
    }

    private static int port(GoofyConfig config) {
        try {
            return java.net.URI.create(config.marketAnalysis.endpoint).getPort();
        } catch (RuntimeException invalid) {
            return -1;
        }
    }

    private static GoofyConfig copy(String json) {
        return GSON.fromJson(json, GoofyConfig.class);
    }
}
