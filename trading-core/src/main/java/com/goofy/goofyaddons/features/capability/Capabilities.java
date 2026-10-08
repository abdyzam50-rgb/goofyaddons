package com.goofy.goofyaddons.features.capability;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that says what each feature actually does today.
 *
 * <p>The presence of a helper class is not evidence that a feature runs end to end, so the
 * settings screen, diagnostics and the dashboard read these labels instead of inferring them.
 * A feature is promoted only when its whole loop (procurement, processing, claim and sale,
 * with restart recovery) is wired and tested.
 */
public final class Capabilities {
    /** How far a feature goes without the player. */
    public enum Level {
        /** Forecasts, plans or parsers only; never acts in the game. */
        RESEARCH,
        /** Runs steps the player queued or confirmed; does not choose or finish work on its own. */
        QUEUED,
        /** Chooses, executes, recovers and settles its work on its own. */
        AUTOMATIC
    }

    public enum Feature {
        BOOK_FLIPS("Book flips", "Buying, combining, selling, retirement and saved-position recovery"),
        GENERAL_FLIPS("Item flips", "Buying, selling, repricing, retirement and saved-position recovery"),
        AUTOMATIC_SELECTION("Automatic routes", "Chooses supported book and item routes; rechecks mode, budget, capacity, requirements and fresh prices"),
        PIPELINE("Pipeline preview", "Advisory allocation; selects only its next route and never reserves the rest"),
        CRAFTING("Crafting", "Runs a queued run end to end: inputs (bought only if you allow it), craft, optional BIN listing; you choose each run"),
        AUCTION_HOUSE("Auction House", "Queued BIN listings, including a production run's output, and exact BIN purchases; recommended AH routes are not all executable"),
        FORGE("Forge", "Submits, waits for and claims a queued run once you open The Forge; then lists it if asked"),
        KAT("Kat", "Upgrades and claims a queued pet once you open Kat with the pet placed; materials are your own"),
        PRODUCTION_PLANNING("Production recommendations", "Planning only; candidates are marked non-executable"),
        ADAPTIVE_ESTIMATES("Adaptive estimates", "Personal and shared execution history calibrate forecasts; no hourly return is guaranteed");

        public final String label, boundary;
        Feature(String label, String boundary) { this.label = label; this.boundary = boundary; }
    }

    public record Entry(Feature feature, Level level, String boundary) {}

    private static final Map<Feature, Entry> ENTRIES = new EnumMap<>(Feature.class);
    static {
        set(Feature.BOOK_FLIPS, Level.AUTOMATIC);
        set(Feature.GENERAL_FLIPS, Level.AUTOMATIC);
        set(Feature.AUTOMATIC_SELECTION, Level.AUTOMATIC);
        set(Feature.PIPELINE, Level.RESEARCH);
        set(Feature.CRAFTING, Level.QUEUED);
        set(Feature.AUCTION_HOUSE, Level.QUEUED);
        set(Feature.FORGE, Level.QUEUED);
        set(Feature.KAT, Level.QUEUED);
        set(Feature.PRODUCTION_PLANNING, Level.RESEARCH);
        set(Feature.ADAPTIVE_ESTIMATES, Level.RESEARCH);
    }

    private Capabilities() {}

    private static void set(Feature feature, Level level) { ENTRIES.put(feature, new Entry(feature, level, feature.boundary)); }

    public static Entry get(Feature feature) { return ENTRIES.get(feature); }
    public static Level level(Feature feature) { return get(feature).level(); }
    public static List<Entry> all() { return List.copyOf(ENTRIES.values()); }

    /** A forecast row is executable only if its engine is automatic and the route is wanted. */
    public static Level route(String kind, boolean configured) {
        Feature engine = "BOOK".equals(kind) ? Feature.BOOK_FLIPS : "GENERAL".equals(kind) ? Feature.GENERAL_FLIPS : null;
        if (engine == null || !configured) return Level.RESEARCH;
        return level(engine);
    }

    /** Plain map for diagnostics exports. */
    public static Map<String, Object> diagnosticState() {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : all()) result.put(entry.feature().name(), Map.of("level", entry.level().name(), "boundary", entry.boundary()));
        return result;
    }
}
