package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCatalogCoverageTest {
    @Test void allSourceGridsAreImportedOrHaveAnExplicitExclusion()throws Exception {
        var catalog=RecipeCatalog.instance();
        try(var stream=RecipeCatalog.class.getResourceAsStream("/goofyaddons/production-recipes.json")) {
            var root=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var coverage=root.getAsJsonObject("coverage");
            int imported=coverage.get("importedCraftRows").getAsInt(),unsupported=coverage.get("unsupportedCraftRows").getAsInt();
            assertEquals(coverage.get("sourceCraftRows").getAsInt(),imported+unsupported);
            assertEquals(imported+coverage.get("retainedCraftRows").getAsInt(),catalog.recipes().stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).count());
            assertTrue(imported>2500);
            assertEquals(unsupported,root.getAsJsonArray("unsupportedCrafts").size());
            assertEquals(unsupported,catalog.unsupportedCrafts().size());
            for(var value:root.getAsJsonArray("unsupportedCrafts")) {
                var row=value.getAsJsonObject();
                assertFalse(row.get("reason").getAsString().isBlank());
                assertTrue(row.has("grid"));assertTrue(catalog.byKey(row.get("key").getAsString()).isEmpty());
            }
        }
    }
    @Test void newVariantsBooksAndCollectionGatesAreAvailableWithoutLosingSavedKeys() {
        var catalog=RecipeCatalog.instance();
        assertFalse(catalog.forOutput("WOOD:1").isEmpty());
        assertTrue(catalog.recipes().stream().anyMatch(r->r.kind()==ProductionRecipe.Kind.CRAFT&&r.outputId().startsWith("ENCHANTMENT_")));
        assertFalse(catalog.forOutput("SMALL_BAIT_SACK").isEmpty());
        var hotspot=catalog.forOutput("HOTSPOT_RING").getFirst();
        assertTrue(hotspot.requirement().contains("Clay Ball VII"));
        assertNotNull(com.goofy.goofyaddons.features.access.RouteRequirements.craft(hotspot.requirement(),Map.of(),Map.of()));
        assertNull(com.goofy.goofyaddons.features.access.RouteRequirements.craft(hotspot.requirement(),Map.of(),Map.of("clayball",7)));
        assertTrue(catalog.byKey("crafting:ASPECT_OF_THE_LEECH_2:1").isPresent());
        assertTrue(catalog.byKey("crafting:ENCHANTED_EYE_OF_ENDER:0").isPresent());
    }
}
