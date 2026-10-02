package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GeneralCalculatorTest {
    private GeneralSettings settings() {
        GeneralSettings settings = new GeneralSettings();
        settings.items = List.of(new GeneralItem("ENCHANTED_SUGAR", "Enchanted Sugar"));
        settings.minProfitPerBatch = 0;
        settings.minMarginPercentage = 0;
        settings.minWeeklyVolume = 0;
        return settings;
    }
    private JsonObject products(double bid, double ask, double volume) {
        return JsonParser.parseString("{\"ENCHANTED_SUGAR\":{\"sell_summary\":[{\"pricePerUnit\":" + bid
                + "}],\"buy_summary\":[{\"pricePerUnit\":" + ask
                + "}],\"quick_status\":{\"buyMovingWeek\":" + volume + ",\"sellMovingWeek\":" + volume + "}}}")
                .getAsJsonObject();
    }

    @Test void OrdinaryFlipsRequireProfitAfterTax() {
        assertEquals(1, GeneralCalculator.calculate(products(100, 101, 100000), settings(), 0, 10000, 100).size());
        assertTrue(GeneralCalculator.calculate(products(100, 101, 100000), settings(), 1.25, 10000, 100).isEmpty());
    }

    @Test void VolumeAndMarginsAreRequiredOnBothSides() {
        GeneralSettings settings = settings();
        settings.minWeeklyVolume = 10000;
        assertTrue(GeneralCalculator.calculate(products(100, 120, 9999), settings, 1.25, 10000, 100).isEmpty());
        settings.minMarginPercentage = 25;
        assertTrue(GeneralCalculator.calculate(products(100, 120, 100000), settings, 1.25, 10000, 100).isEmpty());
    }

    @Test void OrderQuantityRespectsCashInventoryAndHistoricalFlow() {
        GeneralSettings settings = settings();
        settings.maxCoinsPerItem = 5000;
        assertEquals(30, GeneralCalculator.calculate(products(100, 120, 100000), settings, 1.25, 3000, 100).getFirst().quantity());
        assertEquals(10, GeneralCalculator.calculate(products(100, 120, 100000), settings, 1.25, 3000, 10).getFirst().quantity());
        assertEquals(5, GeneralCalculator.calculate(products(100, 120, 840), settings, 1.25, 3000, 100).getFirst().quantity());
    }

    @Test void MissingOrdersAndInvalidQuotesDoNotCreateCandidates() {
        assertTrue(GeneralCalculator.calculate(new JsonObject(), settings(), 1.25, 10000, 100).isEmpty());
        assertTrue(GeneralCalculator.calculate(products(0, 120, 100000), settings(), 1.25, 10000, 100).isEmpty());
        assertTrue(GeneralCalculator.calculate(products(100, 120, 100000), settings(), Double.NaN, 10000, 100).isEmpty());
    }

    @Test void GeneralAllowlistCannotIncludeBooksOrDuplicateItems() {
        GeneralSettings settings = settings();
        settings.items = List.of(new GeneralItem("ENCHANTMENT_ULTIMATE_WISE_1", "Ultimate Wise I"));
        assertThrows(IllegalArgumentException.class, settings::validate);
        settings.items = List.of(new GeneralItem("ENCHANTED_SUGAR", "Sugar"), new GeneralItem("ENCHANTED_SUGAR", "Sugar"));
        assertThrows(IllegalArgumentException.class, settings::validate);
    }

    @Test void OneMalformedProductDoesNotHideOtherItems() {
        GeneralSettings settings = settings();
        settings.items = List.of(new GeneralItem("ENCHANTED_COAL", "Enchanted Coal"),
                new GeneralItem("ENCHANTED_SUGAR", "Enchanted Sugar"));
        JsonObject products = products(100, 120, 100000);
        products.add("ENCHANTED_COAL", JsonParser.parseString("{\"sell_summary\":[{\"pricePerUnit\":[]}]}"));
        List<GeneralCalculator.Candidate> result = GeneralCalculator.calculate(products, settings, 1.25, 10000, 100);
        assertEquals(1, result.size());
        assertEquals("ENCHANTED_SUGAR", result.getFirst().item().id());
    }
}
