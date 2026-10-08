package com.goofy.goofyaddons.features;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSafetyTest {
    @Test void confirmationsMustMatchTheTransactionSide() {
        assertTrue(TradingSafety.confirmationTitle("Confirm Buy Order",false));
        assertTrue(TradingSafety.confirmationTitle("§aConfirm Sell Offer",true));
        assertFalse(TradingSafety.confirmationTitle("Confirm Buy Order",true));
        assertFalse(TradingSafety.confirmationTitle("Confirm Sell Offer",false));
        assertFalse(TradingSafety.confirmationTitle("Confirm Purchase",false));
        assertFalse(TradingSafety.confirmationTitle(null,false));
    }
    @Test void anvilInputDisappearanceIsNotProofOfACombinedBook() {
        assertFalse(TradingSafety.combinedBookArrived(0,0,true));
        assertFalse(TradingSafety.combinedBookArrived(-1,1,true));
        assertFalse(TradingSafety.combinedBookArrived(0,1,false));
        assertFalse(TradingSafety.combinedBookArrived(0,2,true));
        assertTrue(TradingSafety.combinedBookArrived(0,1,true));
        assertTrue(TradingSafety.combinedBookArrived(3,4,true));
    }
    @Test void anExistingBookOrderIsOnlyAdoptedWhenItsAmountCouldBeOurs() {
        // A route needing 16 units can never have ordered more than 16.
        assertTrue(TradingSafety.adoptableOrderTotal(16, 16));
        assertTrue(TradingSafety.adoptableOrderTotal(8, 16), "a partially claimed order is still ours");
        assertTrue(TradingSafety.adoptableOrderTotal(1, 1), "a one-unit route accepts exactly one");
        assertFalse(TradingSafety.adoptableOrderTotal(32, 16), "a larger same-name order is not ours");
        assertFalse(TradingSafety.adoptableOrderTotal(2, 1));
    }

    @Test void anUnreadableOrNonsensicalOrderAmountIsNeverAdopted() {
        assertFalse(TradingSafety.adoptableOrderTotal(null, 16), "unreadable must never be claimed");
        assertFalse(TradingSafety.adoptableOrderTotal(0, 16));
        assertFalse(TradingSafety.adoptableOrderTotal(-4, 16));
        assertFalse(TradingSafety.adoptableOrderTotal(16, 0), "an unknown requirement cannot authorise a claim");
        assertFalse(TradingSafety.adoptableOrderTotal(16, -1));
    }

    @Test void unreadableOrDifferentOrderQuantitiesCannotBeAdopted() {
        assertFalse(TradingSafety.orderQuantityMatches(16,null));
        assertFalse(TradingSafety.orderQuantityMatches(16,32));
        assertTrue(TradingSafety.orderQuantityMatches(16,16));
    }
    @Test void absenceAloneNeverReleasesSaleOwnership() {
        assertFalse(TradingSafety.saleComplete(false,false,true,0));
        assertFalse(TradingSafety.saleComplete(true,false,true,0));
        assertFalse(TradingSafety.saleComplete(true,true,false,0));
        assertFalse(TradingSafety.saleComplete(true,true,true,1));
        assertTrue(TradingSafety.saleComplete(true,true,true,0));
    }
    @Test void sourceAgeRatherThanFetchAgeDeterminesFreshness() {
        assertTrue(TradingSafety.fresh(100000, 160000));
        assertFalse(TradingSafety.fresh(100000, 160001));
        assertFalse(TradingSafety.fresh(0, 1));
        assertFalse(TradingSafety.fresh(170001, 160000));
    }
    @Test void missingTimestampCannotBeUsedToTrade() {
        assertThrows(IllegalStateException.class, () -> TradingSafety.sourceTime(new JsonObject(), 100000));
    }
    @Test void staleTimestampCannotBeUsedToTrade() {
        JsonObject root = new JsonObject(); root.addProperty("lastUpdated", 1);
        assertThrows(IllegalStateException.class, () -> TradingSafety.sourceTime(root, 100000));
    }
    @Test void unrelatedIncomeAndPartialClaimDoNotCompleteSale() {
        assertFalse(TradingSafety.claimReceipt("You earned 1,000 coins!", "Fuming Potato Book", 2));
        assertFalse(TradingSafety.claimReceipt("[Bazaar] Claimed 1,000 coins from selling 1x Fuming Potato Book!", "Fuming Potato Book", 2));
        assertFalse(TradingSafety.claimReceipt("[Bazaar] Claimed 1,000 coins from selling 2x Potato!", "Fuming Potato Book", 2));
    }
    @Test void exactItemAndQuantityAreRequiredForSaleClaim() {
        assertTrue(TradingSafety.claimReceipt("[Bazaar] Claimed 1,000.5 coins from selling 2x Fuming Potato Book!", "Fuming Potato Book", 2));
        assertFalse(TradingSafety.claimReceipt("[Bazaar] Claimed 1,000 coins from selling 2x Fuming Potato Book Extra!", "Fuming Potato Book", 2));
    }
    @Test void cancellationCannotBeInferredFromArbitraryChat() {
        assertFalse(TradingSafety.cancellationReceipt("Cancel your Potato order", "Potato"));
        assertFalse(TradingSafety.cancellationReceipt("[Bazaar] Cancelled buy order for Potato Extra!", "Potato"));
        assertTrue(TradingSafety.cancellationReceipt("[Bazaar] Cancelled buy order for 12x Potato!", "Potato"));
    }
    @Test void genericBazaarProductScreensAreNotOrdersScreens() {
        assertFalse(TradingSafety.ordersTitle("Bazaar"));
        assertFalse(TradingSafety.ordersTitle("Fuming Potato Book"));
        assertTrue(TradingSafety.ordersTitle("Your Bazaar Orders"));
    }
    @Test void formattedOrdersTitlesAreRecognizedWithoutAcceptingOtherBazaarMenus() {
        assertTrue(TradingSafety.ordersTitle("§aYour Bazaar Orders"));
        assertTrue(TradingSafety.ordersTitle("  §6Bazaar ➜ Orders  "));
        assertTrue(TradingSafety.ordersTitle("Bazaar → Orders"));
        assertFalse(TradingSafety.ordersTitle("Bazaar ➜ Enchanted Ink Sac"));
        assertFalse(TradingSafety.ordersTitle("Confirm Buy Order"));
        assertFalse(TradingSafety.ordersTitle(null));
    }
    @Test void coopOrdersMenuFromDiagnosticBundleIsRecognized() {
        assertTrue(TradingSafety.ordersTitle("Co-op Bazaar Orders"));
        assertTrue(TradingSafety.ordersTitle("§6Co-op Bazaar Orders"));
        assertFalse(TradingSafety.ordersTitle("Co-op Bazaar"));
        assertFalse(TradingSafety.ordersTitle("Co-op Bazaar Orders Confirmation"));
    }
    @Test void duplicateAndPagedOrdersCannotBeAdopted() {
        assertTrue(TradingSafety.ambiguousOrders(List.of("BUY Potato", "BUY Potato"), "Potato"));
        assertTrue(TradingSafety.ambiguousOrders(List.of("SELL Potato", "Next Page"), "Potato"));
        assertFalse(TradingSafety.ambiguousOrders(List.of("BUY Potato", "SELL Carrot"), "Potato"));
    }
    @Test void unstackableUnknownProductsFitReservedInventorySpace() {
        assertEquals(16, TradingSafety.conservativeCapacity(20, 4));
        assertEquals(0, TradingSafety.conservativeCapacity(2, 4));
    }
    @Test void holdingLimitsPauseAtAgeOrDrawdownBoundary() {
        assertFalse(TradingSafety.holdingLimit(1000, 60999, 60, 100, 86, 15));
        assertTrue(TradingSafety.holdingLimit(1000, 61000, 60, 100, -1, 15));
        assertTrue(TradingSafety.holdingLimit(1000, 2000, 60, 100, 85, 15));
        assertFalse(TradingSafety.holdingLimit(1000, 2000, 60, 100, Double.NaN, 15));
    }
    @Test void watchdogExcludesIdleOrderWaitsAndDetectsStalledTransaction() {
        TransactionWatchdog w = new TransactionWatchdog();
        assertFalse(w.stalled("orders", true, 1));
        assertFalse(w.stalled("orders", true, 1000000));
        assertFalse(w.stalled("sign", false, 1000000));
        assertFalse(w.stalled("sign", false, 1059999));
        assertTrue(w.stalled("sign", false, 1060000));
    }
    @Test void repeatedProgressCannotEvadeAbsoluteTransactionLimit() {
        TransactionWatchdog w = new TransactionWatchdog();
        for (long t=1;t<300001;t+=10000) assertFalse(w.stalled("step"+t,false,t));
        assertTrue(w.stalled("changed",false,300001));
        w.reset();
        assertFalse(w.stalled("new",false,400000));
    }
}
