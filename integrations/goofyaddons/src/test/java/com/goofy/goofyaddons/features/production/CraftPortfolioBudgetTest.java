package com.goofy.goofyaddons.features.production;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftPortfolioBudgetTest {
    @Test void reservesSubCoinAndMoondewQuoteDriftWithoutIncreasingEstimatedSpending() {
        double estimate=2949235.874;
        double reserved=CraftPortfolioBudget.allocated(estimate,300000,10000,10000000,8000000);
        assertEquals(estimate*1.03,reserved,0.000001);
        assertFalse(ProductionPlanner.exceedsFunds(2949236.698,reserved));
        double moondew=CraftPortfolioBudget.allocated(4523174.96,500000,10000,10000000,8000000);
        assertFalse(ProductionPlanner.exceedsFunds(4523189.792,moondew));
        assertTrue(ProductionPlanner.exceedsFunds(estimate+100000,reserved),"The drift reservation remains bounded");
        // Siblings reserve the ceiling, not merely the old estimate; it is not an acquisition.
        var allocation=new CraftPortfolioBudget.Allocation("first",reserved,0,0,0);
        assertEquals(8000000-reserved,CraftPortfolioBudget.available(8000000,8000000,List.of(allocation),null),0.000001);
        assertEquals(8000000,CraftPortfolioBudget.available(8000000,8000000,List.of(allocation),"first"));
    }
    @Test void driftRoomCannotCrossProfitFloorConfiguredCapOrLiveAvailableShare() {
        assertEquals(1005,CraftPortfolioBudget.allocated(1000,15,10,0,5000));
        assertEquals(1000,CraftPortfolioBudget.allocated(1000,10,10,0,5000));
        assertEquals(1007,CraftPortfolioBudget.allocated(1000,100,10,1007,5000));
        assertEquals(1003,CraftPortfolioBudget.allocated(1000,100,10,0,1003));
        assertEquals(0,CraftPortfolioBudget.allocated(1000,9,10,0,5000));
        assertEquals(0,CraftPortfolioBudget.allocated(1000,100,10,999,5000));
        assertEquals(0,CraftPortfolioBudget.allocated(1000,100,10,0,999));
        assertEquals(0,CraftPortfolioBudget.allocated(Double.NaN,100,10,0,5000));
        assertEquals(0,CraftPortfolioBudget.allocated(1000,100,10,0,Double.POSITIVE_INFINITY));
    }
    @Test void unchangedNearShareQuotesStillFundFourJobsWithoutBorrowingSiblingRoom() {
        var jobs=new java.util.ArrayList<CraftPortfolioBudget.Allocation>();
        for(int i=0;i<4;i++) {
            double share=CraftPortfolioBudget.available(1000,1000,jobs,null)/(4-i);
            double reserved=CraftPortfolioBudget.allocated(249,100,10,0,share);
            assertEquals(250,reserved);
            jobs.add(new CraftPortfolioBudget.Allocation("job-"+i,reserved,0,0,0));
        }
        assertEquals(0,CraftPortfolioBudget.available(1000,1000,jobs,null));
        assertEquals(250,CraftPortfolioBudget.available(1000,1000,jobs,"job-0"));
        assertTrue(ProductionPlanner.exceedsFunds(250.01,CraftPortfolioBudget.available(1000,1000,jobs,"job-0")));
    }
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
