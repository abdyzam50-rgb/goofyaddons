package com.goofy.goofyaddons.features.generalflipper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BazaarAccessTest {
    @TempDir Path dir;
    @Test void wholeMutationCategoryIsExcludedAndOtherGardenProductsRemainEligible() {
        var access=new BazaarAccess(dir.resolve("access.json"));
        assertEquals(40,BazaarAccess.MUTATIONS.size());
        assertTrue(access.excluded().containsAll(java.util.Set.of("CHORUS_FRUIT","GODSEED","ZOMBUD","WITHERBLOOM")));
        assertFalse(access.excluded().contains("FINE_FLOUR"));
        assertFalse(access.excluded().contains("DESIGNER_COFFEE_BEANS"));
    }
    @Test void observedRequirementsPersistAcrossRestartAndCannotRemoveCategoryExclusion() throws Exception {
        Path file=dir.resolve("access.json");
        var access=new BazaarAccess(file);
        access.deny("LOCKED_PRODUCT","You have not unlocked this item!");
        assertTrue(new BazaarAccess(file).excluded().contains("LOCKED_PRODUCT"));
        Files.writeString(file,"{}");
        assertTrue(new BazaarAccess(file).excluded().contains("CHORUS_FRUIT"));
        assertFalse(new BazaarAccess(file).excluded().contains("LOCKED_PRODUCT"));
    }
    @Test void explicitDenialsAndMutationLabelsAreRecognizedWithoutBlockingDescriptiveLore() {
        assertNotNull(BazaarAccess.unmet("§cYou haven't unlocked this item!"));
        assertNotNull(BazaarAccess.unmet("Requires you to inspect this mutation!"));
        assertNotNull(BazaarAccess.unmet("§5§lEPIC MUTATION"));
        assertNotNull(BazaarAccess.unmet("You need Farming Level 25 to buy this item!"));
        assertNull(BazaarAccess.unmet("Requires Farming Level 25\n✓ Requirement met"));
        assertNull(BazaarAccess.unmet("You have unlocked this item!\nMutation chance +5%"));
    }
}
