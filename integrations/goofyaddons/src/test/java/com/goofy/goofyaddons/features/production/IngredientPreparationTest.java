package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IngredientPreparationTest {
    private final RecipeCatalog catalog=RecipeCatalog.instance();
    @Test void fullyCoveredFinalPaperIsReservedInsteadOfConsumedToPrepareAMissingBook() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BOOK",1),Map.of("PAPER",3,"LEATHER",1),Map.of("BOOK",1,"PAPER",3));
        assertEquals(Map.of("SUGAR_CANE",3),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("PAPER",1),new IngredientPreparation.Craft("BOOK",1)),plan.crafts());
    }
    @Test void paperBeyondTheFinalRecipeReservationCanStillPrepareBooks() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BOOK",1),Map.of("PAPER",6,"LEATHER",1),Map.of("BOOK",1,"PAPER",3));
        assertTrue(plan.purchases().isEmpty());assertEquals(List.of(new IngredientPreparation.Craft("BOOK",1)),plan.crafts());
    }
    @Test void woodenMinionToolsAndEmptyBucketsArePreparedFromRawMaterials() {
        for(String id:List.of("WOOD_PICKAXE","WOOD_AXE","WOOD_HOE","WOOD_SPADE","WOOD_SWORD","WORKBENCH")) {
            var plan=IngredientPreparation.plan(catalog,Map.of(id,1),Map.of());
            assertEquals(Set.of("LOG"),plan.purchases().keySet(),id);assertEquals(id,plan.crafts().getLast().output());
            assertTrue(plan.products().contains("WOOD"));
        }
        assertEquals(Map.of("IRON_INGOT",3),IngredientPreparation.plan(catalog,Map.of("BUCKET",1),Map.of()).purchases());
    }
    @Test void booksAndSugarShareCaneWithoutBuyingPaperOrBooks() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BOOK",2,"SUGAR",1),Map.of());
        assertEquals(Map.of("SUGAR_CANE",7,"LEATHER",2),plan.purchases());
        assertEquals(List.of(new IngredientPreparation.Craft("PAPER",2),new IngredientPreparation.Craft("BOOK",2),
            new IngredientPreparation.Craft("SUGAR",1)),plan.crafts());
    }
    @Test void bowlsChestsAndRedstoneTorchesUseSharedLogSurplus() {
        var plan=IngredientPreparation.plan(catalog,Map.of("BOWL",4,"CHEST",1,"REDSTONE_TORCH_ON",1),Map.of());
        assertEquals(Map.of("LOG",4,"REDSTONE",1),plan.purchases());
        assertFalse(plan.purchases().containsKey("STICK"));assertFalse(plan.purchases().containsKey("WOOD"));
        assertTrue(plan.products().containsAll(Set.of("BOWL","CHEST","REDSTONE_TORCH_ON","WOOD","STICK","LOG")));
    }
    @Test void nuggetsAndNormalEyesUseRoundedBaseQuantities() {
        var plan=IngredientPreparation.plan(catalog,Map.of("GOLD_NUGGET",10,"EYE_OF_ENDER",3),Map.of());
        assertEquals(Map.of("GOLD_INGOT",2,"BLAZE_ROD",2,"ENDER_PEARL",3),plan.purchases());
        assertTrue(plan.crafts().contains(new IngredientPreparation.Craft("GOLD_NUGGET",2)));
        assertTrue(plan.crafts().indexOf(new IngredientPreparation.Craft("BLAZE_POWDER",2))<
            plan.crafts().indexOf(new IngredientPreparation.Craft("EYE_OF_ENDER",3)));
    }
    @Test void bottlesUseHeldGlassAndNeverInventASmeltingRecipe() {
        var held=IngredientPreparation.plan(catalog,Map.of("GLASS_BOTTLE",4),Map.of("GLASS",6));
        assertTrue(held.purchases().isEmpty());assertEquals(List.of(new IngredientPreparation.Craft("GLASS_BOTTLE",2)),held.crafts());
        assertEquals(Map.of("GLASS",6),IngredientPreparation.plan(catalog,Map.of("GLASS_BOTTLE",4),Map.of()).purchases());
    }
    @Test void reversibleMaterialsAndUnreviewedYieldsAreNotExpanded() {
        var plan=IngredientPreparation.plan(catalog,Map.of("COAL",9,"GOLD_INGOT",9,"TORCH",4,"ARROW",4),Map.of());
        assertEquals(Map.of("COAL",9,"GOLD_INGOT",9,"TORCH",4,"ARROW",4),plan.purchases());assertTrue(plan.crafts().isEmpty());
    }
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
