package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.menu.RecordingActions;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Probes how far the seam currently reaches. The engine still reads the live game for
 * observation, so this does not drive a transaction yet; what it establishes is whether
 * an engine can be constructed at all with injected effects, which decides how the
 * remaining migration has to be shaped.
 */
class EngineSeamProbeTest {
    @Test void anEngineCanBeBuiltWithInjectedEffectsAndDoesNothingOnItsOwn() {
        RecordingActions actions = new RecordingActions();
        GeneralFlipper engine = new GeneralFlipper(actions);
        assertFalse(engine.isRunning(), "a fresh engine is not running");
        assertEquals(List.of(), actions.performed(), "construction must never touch the world");
    }
}
