package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirmation tooltips captured verbatim from real Hypixel screens by a field run, pinned here
 * so a parser change cannot silently re-break one. A screen that failed in the field is the most
 * valuable thing a diagnostics bundle can carry; this is where those go.
 */
class LiveConfirmationTest {
    /**
     * Captured 2026-10-02, 1.3.7-BETA, where it was rejected: 749,014.1 x 16 is 11,984,225.6
     * against a displayed 11,984,226, so an exact comparison fails and the rounding tolerance
     * added afterwards is what accepts it.
     */
    private static final String OVERLOAD_BUY = String.join("\n",
            "Bazaar",
            "",
            "Price per unit: 749,014.1 coins",
            "",
            "Order: 16x Overload I",
            "Total price: 11,984,226 coins",
            "",
            "Orders are shared by co-op!",
            "",
            "Click to submit order!");

    @Test void theCapturedOverloadBuyConfirmationIsAccepted() {
        assertTrue(ConfirmationCheck.matches("Confirm Buy Order", false, "Buy Order",
                OVERLOAD_BUY, "Overload I", 16, 749014.1),
                "this exact tooltip was rejected in the field; the displayed total is rounded");
    }

    @Test void theSameTooltipIsRejectedForADifferentItem() {
        assertFalse(ConfirmationCheck.matches("Confirm Buy Order", false, "Buy Order",
                OVERLOAD_BUY, "Duplex I", 16, 749014.1));
    }

    @Test void theSameTooltipIsRejectedForADifferentQuantity() {
        assertFalse(ConfirmationCheck.matches("Confirm Buy Order", false, "Buy Order",
                OVERLOAD_BUY, "Overload I", 15, 749014.1));
    }

    @Test void theToleranceDoesNotStretchToAWrongPrice() {
        assertFalse(ConfirmationCheck.matches("Confirm Buy Order", false, "Buy Order",
                OVERLOAD_BUY, "Overload I", 16, 759014.1));
    }
}
