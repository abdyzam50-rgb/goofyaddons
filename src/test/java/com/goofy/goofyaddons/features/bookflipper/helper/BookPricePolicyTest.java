package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.profit.TradeReceipts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookPricePolicyTest {
    @Test void capturedDuplexSaleIsAllowedEvenBelowConfiguredEntryTarget() {
        double price=TradeReceipts.unitPrice("Sell Offer Setup\nSelling: 1x\nUnit price: 16,265,381.3 coins\nTotal: 16,082,396 coins");
        var check=BookPricePolicy.check(true,true,price,11185289.6/16,16,price,1.25,6_000_000);
        assertTrue(check.allowed());assertEquals("held-book-exit",check.reason());
        assertEquals(11185289.6,check.estimatedCost(),0.001);
        assertTrue(check.estimatedNet()>0 && check.estimatedNet()<6_000_000);
    }
    @Test void sameTargetStillBlocksNewInputsAndBuyConfirmation() {
        var check=BookPricePolicy.check(false,true,11185289.6/16,11185289.6/16,
                16,16265381.3,1.25,6_000_000);
        assertFalse(check.allowed());assertEquals("entry-below-profit-target",check.reason());
    }
    @Test void alreadyHeldBooksMayExitAtALossWithoutAuthorizingUnprofitableEntry() {
        var sale=BookPricePolicy.check(true,true,90,10,16,90,1.25,50);
        assertTrue(sale.allowed());assertTrue(sale.estimatedNet()<0);
        assertFalse(BookPricePolicy.check(false,true,10,10,16,90,1.25,0).allowed());
    }
    @Test void profitableEntryStillPassesAndBoundaryIncludesTaxAndFullInputQuantity() {
        var check=BookPricePolicy.check(false,true,10,9,16,200,1.25,37.5);
        assertTrue(check.allowed());assertEquals(37.5,check.estimatedNet());
        assertFalse(BookPricePolicy.check(false,true,10,9,16,200,1.25,37.6).allowed());
        assertFalse(BookPricePolicy.check(false,true,10,9,16,160,0,0).allowed());
    }
    @Test void unreadablePricesAndStaleQuotesNeverAuthorizeAnExit() {
        for(double price:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertFalse(BookPricePolicy.check(true,true,price,10,16,200,1.25,0).allowed());
        assertFalse(BookPricePolicy.check(true,false,200,10,16,200,1.25,0).allowed());
    }
    @Test void invalidCostsOrConfigurationCannotBypassValidationForSales() {
        assertFalse(BookPricePolicy.check(true,true,200,Double.NaN,16,200,1.25,0).allowed());
        assertFalse(BookPricePolicy.check(true,true,200,10,0,200,1.25,0).allowed());
        assertFalse(BookPricePolicy.check(true,true,200,10,16,200,100,0).allowed());
    }
}
