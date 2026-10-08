package com.goofy.goofyaddons.features.release;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every component version and data schema a running install is made of, in one record.
 *
 * <p>The mod, the bundled calculator, the calculator actually answering on the port, the
 * forecast contract and the saved-file schemas each have their own version. Protocol
 * compatibility alone does not show that a set belongs together, so the manifest records
 * what was expected and what is running, and lists each disagreement in words. Support
 * starts from this record instead of guessing which piece is out of date.
 */
public final class ReleaseManifest {
    /** The forecast contract this mod reads (calculator report fields). */
    public static final int FORECAST_CONTRACT = 2;
    /** Version text for a component whose running version could not be observed. */
    public static final String UNKNOWN = "unknown";

    private final Map<String, String> components;
    private final List<String> mismatches;

    private ReleaseManifest(Map<String, String> components, List<String> mismatches) {
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        this.mismatches = List.copyOf(mismatches);
    }

    public static Builder builder() { return new Builder(); }

    public Map<String, String> components() { return components; }
    public List<String> mismatches() { return mismatches; }
    public boolean consistent() { return mismatches.isEmpty(); }

    /** One line for chat or the settings screen. */
    public String summary() {
        String mod = components.getOrDefault("goofyaddons", UNKNOWN);
        String bundle = components.getOrDefault("calculator.bundle", UNKNOWN);
        return "GoofyAddons " + mod + " · calculator " + bundle
                + (mismatches.isEmpty() ? " · components agree" : " · " + mismatches.size() + " mismatch" + (mismatches.size() == 1 ? "" : "es") + ": " + mismatches.getFirst());
    }

    public Map<String, Object> diagnosticState() {
        var state = new LinkedHashMap<String, Object>();
        state.put("components", components);
        state.put("mismatches", mismatches);
        state.put("consistent", consistent());
        return state;
    }

    public static final class Builder {
        private final Map<String, String> components = new LinkedHashMap<>();
        private final List<String> mismatches = new ArrayList<>();

        /** Records a component version; null or blank is recorded as unknown. */
        public Builder component(String name, Object version) {
            components.put(Objects.requireNonNull(name), text(version));
            return this;
        }

        /**
         * Records the running version of a component next to the version this install expects.
         * An unknown running version is not a mismatch: there may be nothing running to ask.
         */
        public Builder expect(String name, Object expected, Object running) {
            String want = text(expected), have = text(running);
            components.put(name + ".expected", want);
            components.put(name + ".running", have);
            if (!have.equals(UNKNOWN) && !want.equals(UNKNOWN) && !want.equals(have))
                mismatches.add(name + ": expected " + want + ", running " + have);
            return this;
        }

        public ReleaseManifest build() { return new ReleaseManifest(components, mismatches); }

        private static String text(Object value) {
            if (value == null) return UNKNOWN;
            String text = value.toString().strip();
            return text.isEmpty() ? UNKNOWN : text.length() > 80 ? text.substring(0, 80) : text;
        }
    }
}
