package com.goofy.goofyaddons.features.generalflipper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderLoreTest {
    @Test void partialFillsAndThousandsAreParsedWithoutPercentDigits() {
        assertEquals(new OrderLore.Fill(1024, 2048), OrderLore.fill("Filled: 1,024/2,048 50%"));
        assertEquals(4, OrderLore.claimable("Filled: 4/256 1.6%\nYou have 4 items to claim!", false));
    }
    @Test void previouslyClaimedItemsAreNotCountedAgain() {
        assertEquals(0, OrderLore.claimable("Filled: 4/256 1.6%", true));
        assertEquals(2, OrderLore.claimable("Filled: 6/256\nYou have 2 items to claim!", true));
    }
    @Test void invalidOrMissingFillCountsAreRejected() {
        assertNull(OrderLore.fill("Filled: 300/256"));
        assertNull(OrderLore.fill("Filled: 0/0"));
        assertNull(OrderLore.fill("Filled: 9999999999999999/256"));
        assertNull(OrderLore.fill("Loading..."));
    }
}
