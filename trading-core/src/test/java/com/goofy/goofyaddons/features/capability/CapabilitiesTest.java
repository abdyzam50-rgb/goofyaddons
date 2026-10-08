package com.goofy.goofyaddons.features.capability;

import org.junit.jupiter.api.Test;

import static com.goofy.goofyaddons.features.capability.Capabilities.Feature;
import static com.goofy.goofyaddons.features.capability.Capabilities.Level;
import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {
    @Test
    void everyFeatureHasOneLabelAndABoundary() {
        assertEquals(Feature.values().length, Capabilities.all().size());
        for (var entry : Capabilities.all()) assertFalse(entry.boundary().isBlank(), entry.feature().name());
    }

    @Test
    void advisoryAndUnwiredFeaturesAreNotAdvertisedAsAutomatic() {
        assertEquals(Level.RESEARCH, Capabilities.level(Feature.PIPELINE));
        assertEquals(Level.RESEARCH, Capabilities.level(Feature.PRODUCTION_PLANNING));
        assertEquals(Level.QUEUED, Capabilities.level(Feature.CRAFTING));
        assertEquals(Level.AUTOMATIC, Capabilities.level(Feature.BOOK_FLIPS));
    }

    @Test
    void onlyWantedRoutesOfAnAutomaticEngineAreExecutable() {
        assertEquals(Level.AUTOMATIC, Capabilities.route("BOOK", true));
        assertEquals(Level.AUTOMATIC, Capabilities.route("GENERAL", true));
        assertEquals(Level.RESEARCH, Capabilities.route("GENERAL", false));
        assertEquals(Level.RESEARCH, Capabilities.route("FORGE", true));
    }

    @Test
    void diagnosticsListEveryFeature() {
        var state = Capabilities.diagnosticState();
        assertEquals(Feature.values().length, state.size());
        assertTrue(state.get("FORGE").toString().contains("RESEARCH"));
    }
}
