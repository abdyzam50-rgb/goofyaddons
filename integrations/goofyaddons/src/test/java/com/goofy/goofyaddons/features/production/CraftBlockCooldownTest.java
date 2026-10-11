package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftBlockCooldownTest {
    @Test void aBlockedCraftWaitsLongerEachTimeAndAFinishedRunClearsIt(){
        var cooldown=new CraftBlockCooldown();
        var first=cooldown.blocked("SMELTER","Compactor not in hotbar",0);
        assertEquals(1,first.blocks());assertTrue(cooldown.coolingDown("SMELTER",59_999));assertFalse(cooldown.coolingDown("SMELTER",60_000));
        assertFalse(cooldown.coolingDown("OTHER",0));
        assertEquals(120_000,cooldown.blocked("SMELTER","again",60_000).until()-60_000);
        for(int i=0;i<20;i++)cooldown.blocked("SMELTER","again",0);
        assertEquals(CraftBlockCooldown.MAX_MS,cooldown.blocked("SMELTER","again",0).until());
        cooldown.finished("SMELTER");assertFalse(cooldown.coolingDown("SMELTER",0));
        assertEquals(1,cooldown.blocked("SMELTER","fresh",0).blocks());
    }
}
