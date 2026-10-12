package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.config.GoofyConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftConcurrencySettingsTest {
    @Test void olderSettingsDefaultToThreeJobsAndLimitsRemainBounded() {
        var old=new com.google.gson.Gson().fromJson("{\"maxBatches\":16}",GoofyConfig.CraftFlips.class);
        assertEquals(3,old.maxActiveCrafts);assertDoesNotThrow(old::validate);
        old.maxActiveCrafts=0;assertThrows(IllegalArgumentException.class,old::validate);
        old.maxActiveCrafts=5;assertThrows(IllegalArgumentException.class,old::validate);
        old.maxActiveCrafts=1;assertDoesNotThrow(old::validate);
        old.maxActiveCrafts=4;assertDoesNotThrow(old::validate);
    }
    @Test void batchAndStrategyDefaultsPreserveOlderExplicitLimits() {
        var defaults=new GoofyConfig.CraftFlips();assertEquals(64,defaults.maxBatches);assertEquals("AUTO",defaults.strategy);
        var old=new com.google.gson.Gson().fromJson("{\"maxBatches\":16}",GoofyConfig.CraftFlips.class);
        assertEquals(16,old.maxBatches);assertEquals("AUTO",old.strategy);assertDoesNotThrow(old::validate);
        old.maxBatches=64;old.strategy="INSTANT_OFFER";assertDoesNotThrow(old::validate);
        old.maxBatches=1024;assertDoesNotThrow(old::validate);
        old.maxBatches=1025;assertThrows(IllegalArgumentException.class,old::validate);
        old.maxBatches=64;old.strategy="UNKNOWN";assertThrows(IllegalArgumentException.class,old::validate);
    }
}
