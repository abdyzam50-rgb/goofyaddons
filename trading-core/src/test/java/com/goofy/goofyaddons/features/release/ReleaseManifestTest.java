package com.goofy.goofyaddons.features.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseManifestTest {
    @Test
    void agreeingComponentsAreConsistent() {
        var manifest = ReleaseManifest.builder()
                .component("goofyaddons", "0.2.16-BETA").component("calculator.bundle", "abc")
                .expect("calculator.bundle", "abc", "abc").expect("calculator.forecastContract", 2, 2)
                .build();
        assertTrue(manifest.consistent());
        assertEquals("abc", manifest.components().get("calculator.bundle.running"));
        assertTrue(manifest.summary().contains("components agree"));
    }

    @Test
    void eachDisagreementIsListedInWords() {
        var manifest = ReleaseManifest.builder()
                .component("goofyaddons", "0.2.16-BETA").component("calculator.bundle", "abc")
                .expect("calculator.bundle", "abc", "old").expect("calculator.upstreamCommit", "1111", "2222")
                .build();
        assertEquals(2, manifest.mismatches().size());
        assertEquals("calculator.bundle: expected abc, running old", manifest.mismatches().getFirst());
        assertTrue(manifest.summary().contains("2 mismatches"));
        assertFalse((Boolean) manifest.diagnosticState().get("consistent"));
    }

    @Test
    void nothingRunningIsUnknownRatherThanAMismatch() {
        var manifest = ReleaseManifest.builder().expect("calculator.bundle", "abc", null).component("config.schema", " ").build();
        assertTrue(manifest.consistent());
        assertEquals(ReleaseManifest.UNKNOWN, manifest.components().get("calculator.bundle.running"));
        assertEquals(ReleaseManifest.UNKNOWN, manifest.components().get("config.schema"));
    }
}
