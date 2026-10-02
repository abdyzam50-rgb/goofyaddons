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

    @Test void romanLevelsCoverEverySupportedLevel() {
        Book book = new Book("ENCHANTMENT_TEST", 1, 10, "Test", 0, 0);
        assertEquals("Test I", book.getRomanLevel(1));
        assertEquals("Test V", book.getRomanLevel(5));
        assertEquals("Test X", book.getRomanLevel(10));
    }
}
