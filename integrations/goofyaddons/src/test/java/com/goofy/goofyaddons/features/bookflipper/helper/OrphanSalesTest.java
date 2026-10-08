package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Driven by the exact orders-menu lore captured when 26,240,759 coins were sitting unclaimed. */
class OrphanSalesTest {
    private static List<String> offer(String amount, String filledOf, String claim) {
        return List.of("Worth 4.8M coins", "", "Offer amount: " + amount,
                "Filled: " + filledOf + " 100%!", "", "Price per unit: 975,903.9 coins", "",
                "Single customer:", "<vendor redacted>", "", "<player redacted>", "",
                claim, "");
    }

    @Test void theCapturedOffersAreAllFound() {
        List<String> names = List.of("SELL Overload I", "SELL Duplex I", "SELL Soul Eater I",
                "SELL Swarm I", "SELL Wisdom I", "SELL Wisdom I", "SELL Wisdom II");
        List<List<String>> lores = List.of(
                offer("5x", "5/5", "You have 4,824,625 coins to claim!"),
                offer("8x", "8/8", "You have 6,332,822 coins to claim!"),
                offer("2x", "2/2", "You have 2,050,999 coins to claim!"),
                offer("16x", "16/16", "You have 11,918,937 coins to claim!"),
                offer("3x", "3/3", "You have 570,258 coins to claim!"),
                offer("1x", "1/1", "You have 190,086 coins to claim!"),
                offer("1x", "1/1", "You have 353,032 coins to claim!"));
        var found = OrphanSales.scan(names, lores);
        assertEquals(7, found.size());
        assertEquals(26_240_759L, OrphanSales.total(found), "the exact sum stranded in the field");
        assertEquals("Overload I", found.getFirst().item());
        assertEquals(5, found.getFirst().units());
    }

    @Test void aPartiallyFilledOfferIsNotCollectable() {
        assertNull(OrphanSales.unclaimed("SELL Overload I",
                offer("16x", "2/16", "You have 1,700,000 coins to claim!")),
                "claiming part of a live offer is a different action and must not be reported");
    }

    @Test void anOfferWithNoCoinsWaitingIsIgnored() {
        assertNull(OrphanSales.unclaimed("SELL Overload I",
                List.of("Offer amount: 5x", "Filled: 5/5 100%!")));
    }

    @Test void aBuyOrderIsNeverASale() {
        assertNull(OrphanSales.unclaimed("BUY Overload I",
                offer("5x", "5/5", "You have 4,824,625 coins to claim!")));
    }

    @Test void unreadableNumbersFailClosed() {
        assertNull(OrphanSales.unclaimed("SELL Overload I",
                offer("5x", "5/5", "You have lots of coins to claim!")));
        assertNull(OrphanSales.unclaimed("SELL Overload I",
                offer("5x", "five/five", "You have 4,824,625 coins to claim!")));
        assertNull(OrphanSales.unclaimed("SELL ", offer("5x", "5/5", "You have 1 coins to claim!")));
        assertNull(OrphanSales.unclaimed(null, List.of()));
        assertNull(OrphanSales.unclaimed("SELL Overload I", null));
    }

    @Test void anOfferWhoseAmountDisagreesWithItsFillIsNotTrusted() {
        // "Offer amount: 8x" against "Filled: 5/5" means the entry was misread; collecting on a
        // misread entry is how the engine would attribute the wrong sale to the wrong route.
        assertNull(OrphanSales.unclaimed("SELL Overload I",
                offer("8x", "5/5", "You have 4,824,625 coins to claim!")));
    }

    @Test void theSumIgnoresEntriesItCouldNotRead() {
        var found = OrphanSales.scan(
                List.of("SELL Overload I", "BUY Duplex I", "SELL Swarm I"),
                List.of(offer("5x", "5/5", "You have 100 coins to claim!"),
                        offer("8x", "8/8", "You have 999 coins to claim!"),
                        offer("2x", "1/2", "You have 50 coins to claim!")));
        assertEquals(1, found.size());
        assertEquals(100L, OrphanSales.total(found));
    }
}
