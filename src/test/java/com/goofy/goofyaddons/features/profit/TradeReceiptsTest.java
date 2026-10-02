package com.goofy.goofyaddons.features.profit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeReceiptsTest {
    @Test void exactClaimProvidesTheCreditedCoins() {
        assertEquals(1234.5,TradeReceipts.saleProceeds("[Bazaar] Claimed 1,234.5 coins from selling 16x Potato!","Potato",16));
        assertNull(TradeReceipts.saleProceeds("[Bazaar] Claimed 1,234.5 coins from selling 16x Potato!","Potato",8));
    }
    @Test void unrelatedIncomeAndItemsNeverBecomeSaleProceeds() {
        assertNull(TradeReceipts.saleProceeds("You received 1000 coins!","Potato",16));
        assertNull(TradeReceipts.saleProceeds("[Bazaar] Claimed 1000 coins from selling 16x Potato Extra!","Potato",16));
        assertNull(TradeReceipts.saleProceeds("[Bazaar] Cancelled buy order for 16x Potato!","Potato",16));
    }
    @Test void bothKnownOrderPriceFormatsAreParsed() {
        assertEquals(1234.5,TradeReceipts.unitPrice("Price per unit: 1,234.5 coins"));
        assertEquals(100,TradeReceipts.unitPrice("Unit price: 100 coins"));
        assertNull(TradeReceipts.unitPrice("Total price: 1,000 coins"));
    }
    @Test void displayHandlesMissingValuesLossesAndClockHours() {
        assertEquals("--",ProfitDisplay.coins(null));assertEquals("-1.25m",ProfitDisplay.coins(-1250000.0));
        assertEquals("02:01:01",ProfitDisplay.duration(7261000));
        var right=ProfitDisplay.bounds(480,270,false);assertEquals(222,right.x());assertEquals(250,right.width());
        assertEquals(8,ProfitDisplay.bounds(480,270,true).x());
    }
}
