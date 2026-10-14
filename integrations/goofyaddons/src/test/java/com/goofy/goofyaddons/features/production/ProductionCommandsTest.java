package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.TradingMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionCommandsTest {
    @Test void auctionTestsNormalizeTheItemAndRejectUnknownOrMultiOutputRecipes() {
        assertEquals("ASPECT_OF_THE_END",ProductionCommands.testItem("aspect_of_the_end",true));
        assertEquals("ASPECT_OF_THE_END",ProductionCommands.testItem(" Aspect of the End ",true));
        assertThrows(IllegalArgumentException.class,()->ProductionCommands.testItem("UNKNOWN_CRAFT_12345",true));
        assertThrows(IllegalArgumentException.class,()->ProductionCommands.testItem("BLAZE_POWDER",true));
        assertEquals("BLAZE_POWDER",ProductionCommands.testItem("blaze_powder",false));
    }
    @Test void aspectOfTheEndRecipeHasTheExactGridAndCollectionGate() {
        var recipe=RecipeCatalog.instance().forOutput("ASPECT_OF_THE_END").getFirst();
        assertEquals(1,recipe.outputCount());assertEquals(32,recipe.ingredients().get("ENCHANTED_EYE_OF_ENDER"));
        assertEquals(1,recipe.ingredients().get("ENCHANTED_DIAMOND"));assertEquals(16,recipe.grid().get(1).count());assertEquals(16,recipe.grid().get(4).count());
        assertNotNull(com.goofy.goofyaddons.features.access.RouteRequirements.craft(recipe.requirement(),java.util.Map.of(),java.util.Map.of("enderpearl",7)));
        assertNull(com.goofy.goofyaddons.features.access.RouteRequirements.craft(recipe.requirement(),java.util.Map.of(),java.util.Map.of("enderpearl",8)));
    }
    @Test void craftModeKeepsItsSavedIdentifierAndHasADiscoverableLabel() {
        assertEquals("CRAFT",TradingMode.CRAFT.name());assertEquals("Craft flips",TradingMode.CRAFT.label());
        assertEquals(TradingMode.CRAFT,TradingMode.BOTH.next());assertEquals(TradingMode.ALL,TradingMode.CRAFT.next());
        assertEquals(TradingMode.BOOKS,TradingMode.ALL.next());
    }
    @Test void buyingAndSellingEachTakeTheirOwnMode() {
        assertEquals(BazaarStrategy.ORDER_INSTANT,ProductionCommands.strategy("order instant"));
        assertEquals(BazaarStrategy.INSTANT_OFFER,ProductionCommands.strategy("insta offer"));
        assertEquals(BazaarStrategy.ORDER_OFFER,ProductionCommands.strategy("order"));
        assertEquals(BazaarStrategy.INSTANT_INSTANT,ProductionCommands.strategy("INSTANT_INSTANT"));
        assertTrue(ProductionCommands.buyMode("instant"));assertFalse(ProductionCommands.buyMode("order"));
        assertTrue(ProductionCommands.sellMode("insta"));assertFalse(ProductionCommands.sellMode("offer"));
        assertThrows(IllegalArgumentException.class,()->ProductionCommands.buyMode("offer"),"a sell offer is not a way to buy");
        assertThrows(IllegalArgumentException.class,()->ProductionCommands.sellMode("order"));
        assertThrows(IllegalArgumentException.class,()->ProductionCommands.strategy("order sideways"));
    }
}
