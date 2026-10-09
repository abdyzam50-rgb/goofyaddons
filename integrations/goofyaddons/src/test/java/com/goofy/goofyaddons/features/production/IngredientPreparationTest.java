package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IngredientPreparationTest {
    private final RecipeCatalog catalog=RecipeCatalog.instance();
    @Test void sixtyFourPowderUsesThirtyTwoRodsAndNeverBuysPowder() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BLAZE_POWDER",64),Map.of());
        assertEquals(Map.of("BLAZE_ROD",32),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("BLAZE_POWDER",32)),plan.crafts());
    }
    @Test void sticksUseLogsThenPlanksThenSticksWithYieldRounding() {
        var plan=IngredientPreparation.plan(catalog,Map.of("STICK",5),Map.of());
        assertEquals(Map.of("LOG",1),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("WOOD",1),new IngredientPreparation.Craft("STICK",2)),plan.crafts());
        assertTrue(IngredientPreparation.dependencies(catalog,Set.of("STICK")).containsAll(Set.of("STICK","WOOD","LOG")));
    }
    @Test void heldFinalIngredientsAreNotSubtractedTwiceAndExistingBaseInputsAreUsed() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BLAZE_POWDER",54),Map.of("BLAZE_POWDER",10,"BLAZE_ROD",20));
        assertEquals(Map.of("BLAZE_ROD",7),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("BLAZE_POWDER",27)),plan.crafts());
        assertTrue(IngredientPreparation.plan(catalog,Map.of("STICK",4),Map.of("WOOD",2)).purchases().isEmpty());
    }
    @Test void sharedInputsAreReservedForTheFinalRecipeAndSurplusIsReused() {
        var plan=IngredientPreparation.plan(catalog,Map.of("STICK",4,"WOOD",2),Map.of("WOOD",4));
        assertEquals(Map.of("LOG",1),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("WOOD",1),new IngredientPreparation.Craft("STICK",1)),plan.crafts());
    }
    @Test void otherMissingItemsKeepDirectProcurementAndNoWorkIsPlannedForCoveredInputs() {
        assertEquals(Map.of("ENCHANTED_ENDER_PEARL",16),IngredientPreparation.plan(catalog,Map.of("ENCHANTED_ENDER_PEARL",16),Map.of()).purchases());
        var covered=IngredientPreparation.plan(catalog,Map.of(),Map.of("BLAZE_POWDER",64));
        assertTrue(covered.purchases().isEmpty());assertTrue(covered.crafts().isEmpty());
    }
}
