package com.goofy.goofyaddons.features.marketanalysis;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AllModeSelectionTest {
    private MarketAnalysisProtocol.Report route(String kind,double rate,double capital) {
        return new MarketAnalysisProtocol.Report(1,1,1,false,"FRESH","a".repeat(40),1,Map.of(),List.of(
            new MarketAnalysisProtocol.Recommendation(kind,"route","INPUT","OUTPUT",1,1,100,capital,10,rate,60,"MEDIUM",true,"volume","live")));
    }
    @Test void craftCompetesWithBothBookAndGeneralOnNetHourlyReturn() {
        for(String kind:List.of("BOOK","GENERAL")) {
            assertTrue(AllModeSelection.craftWins(20000,5000,route(kind,10000,1000)));
            assertFalse(AllModeSelection.craftWins(5000,1000,route(kind,10000,5000)));
        }
    }
    @Test void tiedReturnsPreferLessCommittedCapitalWithoutAlternatingEqualRoutes() {
        assertTrue(AllModeSelection.craftWins(10000,500,route("BOOK",10000,1000)));
        assertFalse(AllModeSelection.craftWins(10000,1000,route("BOOK",10000,1000)));
        assertFalse(AllModeSelection.craftWins(10000,2000,route("GENERAL",10000,1000)));
    }
    @Test void ineligibleCraftsCannotSuppressOtherEngines() {
        assertFalse(AllModeSelection.craftWins(0,1000,route("BOOK",10000,1000)));
        assertFalse(AllModeSelection.craftWins(Double.NaN,1000,null));
        assertTrue(AllModeSelection.craftWins(10000,1000,null));
    }
}
