package com.goofy.goofyaddons.features.production;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftPortfolioBudgetTest {
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
