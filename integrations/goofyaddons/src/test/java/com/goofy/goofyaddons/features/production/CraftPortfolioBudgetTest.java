package com.goofy.goofyaddons.features.production;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftPortfolioBudgetTest {
    @Test void identicalCoinBudgetsDoNotFailBecauseOfFloatingPointRounding() {
        assertFalse(ProductionPlanner.exceedsFunds(4482805.552,4482805.551999999));
        assertFalse(ProductionPlanner.exceedsFunds(4482805.552,4482805.552));
        assertTrue(ProductionPlanner.exceedsFunds(4482805.562,4482805.552));
        for(double invalid:List.of(Double.NaN,Double.POSITIVE_INFINITY,-1.0)) {
            assertTrue(ProductionPlanner.exceedsFunds(invalid,100));
            assertTrue(ProductionPlanner.exceedsFunds(100,invalid));
        }
    }
    @Test void verifiedPartialFillCanExpandToFreshBasketWithinConfiguredCap() {
        double spent=28222.8,remaining=910188.752;
        assertEquals(spent+remaining,CraftPortfolioBudget.expanded(881745.92,spent,remaining,1500000,10000,5000000,2000000),0.001);
        assertEquals(spent+remaining,CraftPortfolioBudget.expanded(881745.92,spent,remaining,1500000,10000,0,2000000),0.001);
    }
    @Test void expansionCannotSpendOtherJobsReservationsOrBreakProfitAndCapitalLimits() {
        double available=CraftPortfolioBudget.available(1000,1000,List.of(
            new CraftPortfolioBudget.Allocation("own",400,100,0,0),
            new CraftPortfolioBudget.Allocation("other",500,0,0,0)),"own");
        assertEquals(400,available);
        assertEquals(400,CraftPortfolioBudget.expanded(400,100,450,1000,10,1000,available));
        assertEquals(400,CraftPortfolioBudget.expanded(400,100,450,1000,10,500,1000));
        assertEquals(400,CraftPortfolioBudget.expanded(400,100,450,559,10,1000,1000));
        assertEquals(400,CraftPortfolioBudget.expanded(400,0,450,1000,10,1000,1000));
        assertEquals(400,CraftPortfolioBudget.expanded(400,100,Double.NaN,1000,10,1000,1000));
    }
    @Test void wholeRecipeReservationLeavesFundsForRemainingInputsAcrossJobs() {
        var first=new CraftPortfolioBudget.Allocation("first",200,0,100,100);
        // First order already owns 100; reserve only the other 100, not another full 200.
        assertEquals(800,CraftPortfolioBudget.available(900,900,List.of(first),null));
        assertEquals(900,CraftPortfolioBudget.available(900,900,List.of(first),"first"));
        var second=new CraftPortfolioBudget.Allocation("second",300,0,0,0);
        assertEquals(500,CraftPortfolioBudget.available(900,900,List.of(first,second),null));
        assertEquals(600,CraftPortfolioBudget.available(900,900,List.of(first,second),"first"));
    }
    @Test void claimedInputsRemainCommittedButAreNotChargedToTheLiquidPurseTwice() {
        var claimed=new CraftPortfolioBudget.Allocation("first",200,100,0,0);
        assertEquals(800,CraftPortfolioBudget.available(1000,900,List.of(claimed),null));
        assertEquals(900,CraftPortfolioBudget.available(1000,900,List.of(claimed),"first"));
        assertEquals(0,CraftPortfolioBudget.available(1000,100,List.of(claimed),null));
        assertEquals(100,CraftPortfolioBudget.available(1000,100,List.of(claimed),"first"));
    }
    @Test void sellOfferOwnershipIsNotCountedAgainAsBuyEscrow() {
        var offer=new CraftPortfolioBudget.Allocation("first",200,180,250,0);
        assertEquals(750,CraftPortfolioBudget.available(750,820,List.of(offer),null));
        assertEquals(0,CraftPortfolioBudget.available(-1,820,List.of(offer),null));
        assertEquals(0,CraftPortfolioBudget.available(Double.NaN,820,List.of(offer),null));
    }
}
