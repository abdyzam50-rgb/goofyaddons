package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookTest {
    @Test void combiningLevelsAreEstablishedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Book("ENCHANTMENT_TEST", 0, 5, "Test", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Book("ENCHANTMENT_TEST", -1, 5, "Test", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Book("ENCHANTMENT_TEST", 5, 5, "Test", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Book("ENCHANTMENT_TEST", 6, 5, "Test", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Book("ENCHANTMENT_TEST", 1, 11, "Test", 0, 0));
    }

    @Test void quantityCannotThrowForABookThatExists() {
        // Every tracked level of a constructed route must be priceable and bookkeepable.
        for (int sellLevel = 2; sellLevel <= 10; sellLevel++) {
            for (int level = 1; level < sellLevel; level++) {
                Book book = new Book("ENCHANTMENT_TEST", level, sellLevel, "Test", 0, 0);
                assertEquals(1 << (sellLevel - level), book.getQtyAmount(book.level()));
                assertEquals(1, book.getQtyAmount(book.sellLevel()));
            }
        }
    }

    @Test void aBookIsWorthItsDoublingInBaseUnits() {
        Book route = new Book("ENCHANTMENT_TEST", 1, 5, "Test", 0, 0);
        assertEquals(1, route.baseUnits(1));
        assertEquals(2, route.baseUnits(2));
        assertEquals(8, route.baseUnits(4));
        assertEquals(16, route.baseUnits(5));
        // The valuation must agree with the quantity the route orders.
        assertEquals(route.getQtyAmount(route.level()), route.baseUnits(route.sellLevel()));
    }

    @Test void aLevelOutsideTheRouteIsWorthNothingRatherThanThrowing() {
        // Valuing a stale entry must under-count, never break a capital adjustment.
        Book route = new Book("ENCHANTMENT_TEST", 2, 5, "Test", 0, 0);
        assertEquals(0, route.baseUnits(1), "below the route's own level");
        assertEquals(0, route.baseUnits(6), "above the sell level");
        assertEquals(0, route.baseUnits(0));
        assertEquals(0, route.baseUnits(-3));
        assertEquals(1, route.baseUnits(2));
    }

    @Test void romanLevelsCoverEverySupportedLevel() {
        Book book = new Book("ENCHANTMENT_TEST", 1, 10, "Test", 0, 0);
        assertEquals("Test I", book.getRomanLevel(1));
        assertEquals("Test V", book.getRomanLevel(5));
        assertEquals("Test X", book.getRomanLevel(10));
    }
}
