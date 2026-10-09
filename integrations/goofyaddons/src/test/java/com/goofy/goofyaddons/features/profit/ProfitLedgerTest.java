package com.goofy.goofyaddons.features.profit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProfitLedgerTest {
    @Test void fundedHoldingsUseRemainingAcquisitionsAndNeverInventRecoveredCost() {
        var l=new ProfitLedger();assertEquals(0.0,l.openCost("t"));
        l.acquire("t","books","Wisdom","buy",10,1000.0);
        l.sell("t","books","Wisdom","sale",4,480.0);
        assertEquals(600.0,l.openCost("t"));assertEquals(80,l.summary().profit());
        l.resetSession();assertEquals(600.0,l.openCost("t"));
        l.recoverHoldings("t","books","Wisdom",7);assertNull(l.openCost("t"));
    }
    @TempDir Path dir;
    @Test void profitUsesConfirmedClaimMinusAcquiredCostWithoutDoubleTax() {
        ProfitLedger l=new ProfitLedger();
        l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","sale",10,1200.0);
        assertEquals(200,l.summary().profit());
        assertEquals(200,l.summary().general());
        assertEquals(0,l.summary().books());
    }
    @Test void partialSalesConsumeOnlyTheirShareOfCost() {
        ProfitLedger l=new ProfitLedger();
        l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","part",4,480.0);
        assertEquals(80,l.summary().profit());
        l.sell("t","general","Potato","rest",6,720.0);
        assertEquals(200,l.summary().profit());
    }
    @Test void bookProfitIncludesAllSixteenInputsAtTheirConfirmedPrices() {
        ProfitLedger l=new ProfitLedger();
        l.acquire("t","books","Wisdom","buy1",8,800.0);
        l.acquire("t","books","Wisdom","buy2",8,1200.0);
        l.sell("t","books","Wisdom","sale",16,2500.0);
        assertEquals(500,l.summary().books());
    }
    @Test void lossesRemainNegativeRatherThanBeingDiscarded() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","general","Potato","buy",1,100.0);
        l.sell("t","general","Potato","sale",1,80.0);
        assertEquals(-20,l.summary().profit());
    }
    @Test void writtenOffCostRemainsLostWhenANewFreeCopyAppears() {
        var ledger=new ProfitLedger();ledger.acquire("t","books","Overload","buy",1,100.0);
        assertTrue(ledger.writeOff("t","books","Overload","loss",1));
        assertFalse(ledger.writeOff("t","books","Overload","loss",1));
        assertEquals(-100,ledger.summary().profit());
        ledger.acquire("t","books","Overload","found",1,0.0);
        assertEquals(-100,ledger.summary().profit(),"finding a copy never reverses the loss");
        ledger.sell("t","books","Overload","sale-found",1,30.0);
        assertEquals(-70,ledger.summary().profit());assertEquals(2,ledger.history().size());
    }
    @Test void partialWriteOffConsumesOnlyTheLostCopiesCost() {
        var ledger=new ProfitLedger();ledger.acquire("t","books","Overload","buy",4,400.0);
        ledger.writeOff("t","books","Overload","loss",1);
        ledger.sell("t","books","Overload","sale",3,360.0);
        assertEquals(-40,ledger.summary().profit());
    }
    @Test void refundsAndOpenInventoryDoNotCountAsProfit() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","general","Potato","buy",10,1000.0);
        assertEquals(0,l.summary().profit());assertEquals(0,l.summary().settlements());
    }
    @Test void duplicateClaimsAndAcquisitionsAreIdempotent() {
        ProfitLedger l=new ProfitLedger();
        assertTrue(l.acquire("t","general","Potato","buy",10,1000.0));
        assertFalse(l.acquire("t","general","Potato","buy",10,1000.0));
        assertTrue(l.sell("t","general","Potato","sale",10,1200.0));
        assertFalse(l.sell("t","general","Potato","sale",10,1200.0));
        assertEquals(200,l.summary().profit());assertEquals(1,l.summary().settlements());
    }
    @Test void unknownPurchaseCostsAreExcludedAndFlagged() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","books","Wisdom","buy",16,null);
        l.sell("t","books","Wisdom","sale",16,1200.0);
        assertEquals(0,l.summary().profit());assertEquals(1,l.summary().incomplete());
        assertNull(l.history().getFirst().profit());
    }
    @Test void preExistingBooksAndMissingClaimAmountsDoNotInventProfits() {
        ProfitLedger l=new ProfitLedger();
        l.sell("old","books","Wisdom","old-sale",16,1200.0);
        l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","missing-coins",4,null);
        assertEquals(2,l.summary().incomplete());assertEquals(0,l.summary().profit());
    }
    @Test void mixedKnownAndUnknownInputsMakeTheWholeBookSaleIncomplete() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","books","Wisdom","known",8,800.0);
        l.acquire("t","books","Wisdom","unknown",8,null);
        l.sell("t","books","Wisdom","sale",16,2500.0);
        assertEquals(1,l.summary().incomplete());assertEquals(0,l.summary().profit());
    }
    @Test void resetKeepsCostOfOpenPositionsAndDoesNotDeleteHistory() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","part",4,480.0);l.activeTime(60000);l.resetSession();
        assertEquals(0,l.summary().profit());assertEquals(0,l.summary().activeMillis());
        l.sell("t","general","Potato","rest",6,720.0);
        assertEquals(120,l.summary().profit());assertEquals(2,l.history().size());
    }
    @Test void rateRequiresAFullActiveMinuteAndKnownSettlement() {
        ProfitLedger l=new ProfitLedger();l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","sale",10,1200.0);l.activeTime(59999);
        assertNull(l.summary().perHour());l.activeTime(1);assertEquals(12000,l.summary().perHour());
        ProfitLedger unknown=new ProfitLedger();unknown.sell("old","books","Wisdom","sale",16,1200.0);
        unknown.activeTime(60000);assertNull(unknown.summary().perHour());
        l.sell("unknown","books","Wisdom","unknown-sale",16,1200.0);
        assertNull(l.summary().perHour());
    }
    @Test void persistenceKeepsOpenCostSessionAndDuplicateProtection() throws Exception {
        ProfitLedger l=new ProfitLedger();l.acquire("t","general","Potato","buy",10,1000.0);
        l.sell("t","general","Potato","part",4,480.0);l.activeTime(60000);
        Path p=dir.resolve("profit.json");l.write(p);ProfitLedger restored=ProfitLedger.read(p);
        assertFalse(restored.sell("t","general","Potato","part",4,480.0));
        restored.sell("t","general","Potato","rest",6,720.0);
        assertEquals(200,restored.summary().profit());assertEquals(60000,restored.summary().activeMillis());
    }
    @Test void invalidMoneyAndChangedIdentityCannotPoisonTotals() {
        ProfitLedger l=new ProfitLedger();
        assertThrows(IllegalArgumentException.class,()->l.acquire("t","general","Potato","bad",1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->l.sell("t","general","Potato","bad",1,-1.0));
        l.acquire("t","general","Potato","buy",1,100.0);
        assertThrows(IllegalArgumentException.class,()->l.sell("t","books","Wisdom","wrong",1,100.0));
        assertEquals(0,l.summary().settlements());
    }
    @Test void corruptLedgerIsPreservedAndRejected() throws Exception {
        Path p=dir.resolve("profit.json");Files.writeString(p,"broken");
        assertThrows(Exception.class,()->ProfitLedger.read(p));assertEquals("broken",Files.readString(p));
        Files.writeString(p,"{}");assertThrows(Exception.class,()->ProfitLedger.read(p));
        assertEquals("{}",Files.readString(p));
    }
    @Test void resumedTradePreservesKnownCostAndDoesNotAcquireTheSameBooksTwice() throws Exception {
        var ledger=new ProfitLedger();ledger.acquire("saved","books","Wisdom","buy",16,1600.0);
        var path=dir.resolve("profit.json");ledger.write(path);ledger=ProfitLedger.read(path);
        assertFalse(ledger.recoverHoldings("saved","books","Wisdom",16));
        assertEquals(1600.0,ledger.knownCost("saved",16));
        ledger.sell("saved","books","Wisdom","saved:sale",16,2000.0);
        assertEquals(400,ledger.summary().profit());assertEquals(0,ledger.summary().incomplete());
    }
    @Test void legacyRecoveryRecordsUnknownBasisRatherThanUsingTheReservationAsCost() {
        var ledger=new ProfitLedger();assertTrue(ledger.recoverHoldings("legacy","books","Wisdom",16));
        assertFalse(ledger.recoverHoldings("legacy","books","Wisdom",16));
        assertNull(ledger.knownCost("legacy",16));
        ledger.sell("legacy","books","Wisdom","sale",16,2000.0);
        assertEquals(0,ledger.summary().profit());assertEquals(1,ledger.summary().incomplete());
    }
    @Test void onlyMissingRecoveredUnitsAreAddedAndLaterClaimsKeepTheirOwnBasis() {
        var ledger=new ProfitLedger();ledger.acquire("partial","books","Wisdom","old",4,400.0);
        assertTrue(ledger.recoverHoldings("partial","books","Wisdom",8));
        ledger.acquire("partial","books","Wisdom","next",8,800.0);
        ledger.sell("partial","books","Wisdom","sale",16,2000.0);
        assertEquals(1,ledger.summary().incomplete());
        assertThrows(IllegalArgumentException.class,()->ledger.recoverHoldings("partial","general","Coal",1));
    }
    @Test void craftReceiptsUseTheirOwnProfitBucketAndOldInputsRemainUnknown()throws Exception {
        var ledger=new ProfitLedger();ledger.acquire("craft-run","craft","OUTPUT","craft-input",1,100.0);
        ledger.sell("craft-run","craft","OUTPUT","craft-sale",1,160.0);
        assertEquals(60,ledger.summary().profit());assertEquals(60,ledger.summary().craft());assertEquals(0,ledger.summary().general());
        ledger.acquire("old-inputs","craft","OUTPUT","old-inputs",1,null);
        ledger.sell("old-inputs","craft","OUTPUT","old-sale",1,200.0);
        assertEquals(1,ledger.summary().incomplete());assertEquals(60,ledger.summary().profit());
    }

}
