package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftRankingRefreshTest {
    @Test void lateBazaarResponseBypassesTheExistingTwentySecondDeadline() {
        var gate=new CraftRankingRefresh();var settings=new Object();
        gate.attempted(1000,0,settings);
        assertTrue(gate.due(1300,1000,settings)); // First response arrives after the poll returned.
        gate.attempted(1300,1000,settings);
        assertFalse(gate.due(1500,1000,settings));
        assertTrue(gate.due(1600,1500,settings));
        assertTrue(gate.due(21300,1000,settings));
    }
    @Test void settingsChangeAndAccountResetInvalidateTheRankingDeadline() {
        var gate=new CraftRankingRefresh();var settings=new Object();
        gate.attempted(1000,1000,settings);
        assertFalse(gate.due(1100,1000,settings));
        assertTrue(gate.due(1100,1000,new Object()));
        gate.clear();assertTrue(gate.due(1100,1000,settings));
    }
}
