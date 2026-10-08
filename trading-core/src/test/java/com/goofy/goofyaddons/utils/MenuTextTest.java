package com.goofy.goofyaddons.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuTextTest {
    @Test void theContainerRegionExcludesThePlayerInventory() {
        assertEquals(18, MenuText.containerEnd(54));
        assertEquals(0, MenuText.containerEnd(36));
        assertEquals(17, MenuText.containerEnd(53));
    }

    @Test void aMenuSmallerThanTheInventoryHasNoContainerRegion() {
        // Never a negative bound, whatever shape the menu turns out to be.
        assertEquals(0, MenuText.containerEnd(0));
        assertEquals(0, MenuText.containerEnd(9));
    }

    @Test void titlesMatchThroughFormattingCodes() {
        assertTrue(MenuText.titleContains("§6§lConfirm Buy Order", "Confirm"));
        // A code splitting the label used to defeat the general engine's plain contains().
        assertTrue(MenuText.titleContains("Confirm §aBuy Order", "Confirm Buy"));
        assertFalse(MenuText.titleContains("Bazaar", "Confirm"));
    }

    @Test void missingTitlesOrLabelsNeverMatch() {
        assertFalse(MenuText.titleContains(null, "Confirm"));
        assertFalse(MenuText.titleContains("Confirm Buy Order", null));
    }
}
