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
        assertEquals("+1.25m",ProfitDisplay.signedCoins(1250000.0));
        assertEquals("-1.25m",ProfitDisplay.signedCoins(-1250000.0),"a loss already carries its sign");
        assertEquals("0",ProfitDisplay.signedCoins(0.0),"zero is not signed");
        assertEquals("--",ProfitDisplay.signedCoins(null));
        var right=ProfitDisplay.bounds(480,270,false);assertEquals(222,right.x());assertEquals(250,right.width());
        assertEquals(8,ProfitDisplay.bounds(480,270,true).x());
    }

    @Test void thePanelStaysInsideTheScreenAtEverySize() {
        for (int height : new int[]{90,120,180,270,360,720,1440}) {
            for (boolean left : new boolean[]{true,false}) {
                var panel=ProfitDisplay.bounds(480,height,left);
                assertTrue(panel.y()+panel.height()<=height,"panel must not run off a "+height+"px screen");
                assertTrue(panel.x()>=8);
                assertTrue(panel.x()+panel.width()<=480);
            }
        }
    }

    @Test void sectionsAreDroppedRatherThanClipped() {
        // A31: the panel used to draw a fixed set of rows and let the scissor cut off
        // whatever did not fit, so rows vanished with nothing to say they had.
        for (int height=0;height<=400;height++) {
            var layout=ProfitDisplay.layout(height);
            int used=ProfitDisplay.HEADER_HEIGHT
                    +(layout.hero() ? ProfitDisplay.HERO_HEIGHT : 0)
                    +(layout.task() ? ProfitDisplay.TASK_HEIGHT : 0)
                    +(layout.rate() ? ProfitDisplay.RATE_HEIGHT : 0)
                    +(layout.detailRows()>0
                        ? ProfitDisplay.DETAIL_HEADER_HEIGHT+layout.detailRows()*ProfitDisplay.DETAIL_ROW_HEIGHT : 0)
                    +(layout.footer() ? ProfitDisplay.FOOTER_HEIGHT : 0);
            assertTrue(used<=Math.max(ProfitDisplay.HEADER_HEIGHT,height),
                    "height "+height+" laid out "+used+"px of content");
            assertTrue(layout.detailRows()<=ProfitDisplay.MAX_DETAIL_ROWS);
            assertTrue(layout.detailRows()>=0);
        }
    }

    @Test void moreRoomOnlyEverAddsContent() {
        int previous=-1;
        for (int height=0;height<=400;height++) {
            var layout=ProfitDisplay.layout(height);
            int score=(layout.footer()?1:0)+(layout.hero()?1:0)+(layout.task()?1:0)
                    +(layout.rate()?1:0)+layout.detailRows();
            assertTrue(score>=previous,"growing the panel must not remove content at "+height);
            previous=score;
        }
    }

    @Test void warningsSurviveWhenStatRowsCannotFit() {
        // The footer carries the ledger error and staleness notices, so it is reserved first.
        var tight=ProfitDisplay.layout(ProfitDisplay.HEADER_HEIGHT+ProfitDisplay.FOOTER_HEIGHT);
        assertTrue(tight.footer(),"a warning outranks every stat row");
        assertEquals(0,tight.detailRows());
        assertFalse(tight.hero());
    }

    @Test void aPanelWithNoRoomUnderItsHeaderIsNotDrawn() {
        assertFalse(ProfitDisplay.layout(0).anything());
        assertFalse(ProfitDisplay.layout(ProfitDisplay.HEADER_HEIGHT).anything());
        assertFalse(ProfitDisplay.worthDrawing(250,ProfitDisplay.HEADER_HEIGHT));
        assertFalse(ProfitDisplay.worthDrawing(120,400),"too narrow for a label and a value");
        assertTrue(ProfitDisplay.worthDrawing(250,302));
    }

    @Test void theCardIsExactlyAsTallAsWhatItDraws() {
        for (int height=0;height<=400;height++) {
            var panel=ProfitDisplay.bounds(480,height,false);
            assertEquals(ProfitDisplay.contentHeight(ProfitDisplay.layout(panel.height())),panel.height(),
                    "no dead space or overflow at screen height "+height);
        }
    }

    @Test void aFullHeightPanelShowsEverything() {
        var full=ProfitDisplay.layout(302);
        assertTrue(full.hero());
        assertTrue(full.task());
        assertTrue(full.rate());
        assertTrue(full.footer());
        assertEquals(ProfitDisplay.MAX_DETAIL_ROWS,full.detailRows(),"the tallest panel shows every row");
    }
}
