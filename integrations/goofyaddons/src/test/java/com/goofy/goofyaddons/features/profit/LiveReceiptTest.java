package com.goofy.goofyaddons.features.profit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bazaar chat receipts captured verbatim from a field run.
 *
 * <p>The sale claim below is the whole reason no session ever recorded a completed sale. The
 * engine listed the book, the offer filled, it clicked to claim, Hypixel paid out 810,775
 * coins - and the receipt pattern, anchored at the item name, did not match the trailing
 * " at 820,000 each!". With no matching receipt the engine treated its own claim as an
 * unexplained disappearance and halted, with the sale missing from the profit ledger.
 */
class LiveReceiptTest {
    private static final String SALE =
            "[Bazaar] Claimed 810,775 coins from selling 1x Ultimate Wise V at 820,000 each!";

    @Test void theCapturedSaleClaimIsRecognised() {
        assertEquals(810775.0, TradeReceipts.saleProceeds(SALE, "Ultimate Wise V", 1),
                "this exact message halted a run that had already been paid");
    }

    @Test void aClaimWithoutTheTrailingPriceStillParses() {
        assertEquals(810775.0, TradeReceipts.saleProceeds(
                "[Bazaar] Claimed 810,775 coins from selling 1x Ultimate Wise V!", "Ultimate Wise V", 1));
    }

    @Test void theUnitCountIsStillChecked() {
        assertNull(TradeReceipts.saleProceeds(SALE, "Ultimate Wise V", 2),
                "a receipt for one unit must not satisfy a claim for two");
    }

    @Test void theItemIsStillChecked() {
        assertNull(TradeReceipts.saleProceeds(SALE, "Ultimate Wise IV", 1));
        assertNull(TradeReceipts.saleProceeds(SALE, "Overload V", 1));
    }

    @Test void aBuyClaimIsNotASaleReceipt() {
        assertNull(TradeReceipts.saleProceeds(
                "[Bazaar] Claimed 16x Overload I from buy order!", "Overload I", 16));
    }

    @Test void theRelaxedTailDoesNotSwallowExtraText() {
        assertNull(TradeReceipts.saleProceeds(
                SALE.replace("each!", "each, and also something else!"), "Ultimate Wise V", 1));
        assertNull(TradeReceipts.saleProceeds(
                "[Bazaar] Claimed 810,775 coins from selling 1x Ultimate Wise V at each!",
                "Ultimate Wise V", 1));
    }
}
