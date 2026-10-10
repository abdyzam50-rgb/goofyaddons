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
    @Test void quantityCanBeReadIndependentlyOfFillFormatting() {
        assertEquals(32,OrderLore.total("Order amount: 32x\nFilled: 0%"));
        assertEquals(32,OrderLore.total("§7Order amount: §a32x\n§7Filled: §a0§7/§a32"));
        assertEquals(2048,OrderLore.total("Amount: 2,048 items"));
        assertNull(OrderLore.total("Order amount: 32x\nFilled: 40/32"));
        assertNull(OrderLore.total("Order amount: 64x\nFilled: 0/32"));
        assertNull(OrderLore.total("Order amount: 0x"));
        assertNull(OrderLore.total("Filled: 50%"));
        assertNull(OrderLore.total(null));
    }
    @Test void partialClaimMustArriveBeforeReopeningTheRemainingOrder() {
        String remaining="Order amount: 32x\nFilled: 4/32 (12.5%)\nPrice per unit: 133,350.4 coins\nClick to view options!";
        assertTrue(OrderLore.canOpenOptionsAfterClaim(remaining,1,4,3));
        assertFalse(OrderLore.canOpenOptionsAfterClaim(remaining,1,3,3));
        assertFalse(OrderLore.canOpenOptionsAfterClaim("Loading...",1,4,3));
        assertFalse(OrderLore.canOpenOptionsAfterClaim(remaining+"\nYou have 1 item to claim!",1,4,3));
    }
    @Test void remainingPartialFillsAreNotHiddenByEarlierClaims() {
        assertEquals(3,OrderLore.claimable("Filled: 4/32",1));
        assertEquals(0,OrderLore.claimable("Filled: 4/32",4));
        assertEquals(2,OrderLore.claimable("Filled: 6/32\nYou have 2 items to claim!",4));
    }
    @Test void coopCreatorMustMatchEvenWhenRankAndColorsArePresent() {
        assertTrue(OrderLore.ownOrder("§7By: §b[MVP+] §acuredmc","curedmc"));
        assertTrue(OrderLore.ownOrder("By: curedmc","CUREDMC"));
        assertFalse(OrderLore.ownOrder("By: curedmc_extra","curedmc"));
        assertFalse(OrderLore.ownOrder("By: coopmate","curedmc"));
        assertFalse(OrderLore.ownOrder("Loading...","curedmc"));
    }
    @Test void partialSellClaimMustBeConfirmedBeforeReopeningCancellation() {
        String options="Order amount: 32x\nFilled: 4/32\nClick to view options!";
        assertFalse(OrderLore.canOpenSellOptionsAfterClaim(options,4,false));
        assertTrue(OrderLore.canOpenSellOptionsAfterClaim(options,4,true));
        assertTrue(OrderLore.canOpenSellOptionsAfterClaim(options,0,false));
        assertFalse(OrderLore.canOpenSellOptionsAfterClaim("Loading...",4,true));
    }
    @Test void observedSellOfferWithoutFilledLineIsVerifiedAsUnfilled() {
        String observed="Worth 7.6M coins\n\nOffer amount: 32x\n\nPrice per unit: 239,998.6 coins\n\nBy: [MVP+] curedmc\n\nClick to view options!";
        assertEquals(32,OrderLore.total(observed));
        assertEquals(new OrderLore.Fill(0,32),OrderLore.fill(observed));
        assertTrue(OrderLore.canOpenSellOptionsAfterClaim(observed,0,false));
        assertEquals(new OrderLore.Fill(12,32),OrderLore.fill(observed+"\nFilled: 12/32 (37.5%)"));
        assertEquals(new OrderLore.Fill(32,32),OrderLore.fill(observed+"\nFilled: 32/32 (100%)"));
    }
    @Test void missingMalformedOrConflictingFillEvidenceIsNotAssumedZero() {
        assertNull(OrderLore.fill("Offer amount: 32x"));
        assertNull(OrderLore.fill("Offer amount: 32x\nFilled: Loading...\nClick to view options!"));
        assertNull(OrderLore.fill("Offer amount: 32x\nYou have coins to claim!\nClick to view options!"));
        assertNull(OrderLore.total("Offer amount: 32x\nFilled: 0/64"));
        assertNull(OrderLore.total("Offer amount: 32x\nOrder amount: 64x"));
    }
    @Test void invalidOrMissingFillCountsAreRejected() {
        assertNull(OrderLore.fill("Filled: 300/256"));
        assertNull(OrderLore.fill("Filled: 0/0"));
        assertNull(OrderLore.fill("Filled: 9999999999999999/256"));
        assertNull(OrderLore.fill("Loading..."));
    }
    @Test void abbreviatedDenominatorUsesExactOrderAmountWithoutRoundingOwnership() {
        String observed="Order amount: 1,280x\nFilled: 7/1.3k (0.5%)\nYou have 7 items to claim!";
        assertEquals(1280,OrderLore.total(observed));
        assertEquals(new OrderLore.Fill(7,1280),OrderLore.fill(observed));
        assertEquals(7,OrderLore.claimable(observed,0));
        assertEquals(4096,OrderLore.total("Offer amount: 4,096x\nFilled: 512/4.1k (12.5%)"));
        assertNull(OrderLore.total("Filled: 7/1.3k"));
        assertNull(OrderLore.total("Order amount: 1,280x\nFilled: 7/1.4k"));
        assertNull(OrderLore.total("Order amount: 1,280x\nFilled: 1,281/1.3k"));
        assertNull(OrderLore.total("Order amount: 1,280x\nFilled: 7/1.3kabcd"));
    }
    @Test void abbreviatedFilledCountsNeverInventClaimQuantityOrFullSaleProof() {
        String observed="Order amount: 1,280x\nFilled: 1.2k/1.3k (93.7%)";
        assertEquals(1280,OrderLore.total(observed));assertNull(OrderLore.fill(observed));
        assertEquals(0,OrderLore.claimable(observed,0));
        assertEquals(1201,OrderLore.claimable(observed+"\nYou have 1,201 items to claim!",0));
        assertNull(OrderLore.fill("Order amount: 1,280x\nFilled: 1.3k/1.3k (100%)"));
        assertNull(OrderLore.total("Order amount: 1,280x\nFilled: 1.4k/1.3k"));
    }

}
