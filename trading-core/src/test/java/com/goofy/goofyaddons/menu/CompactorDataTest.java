package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompactorDataTest {
    @Test void actualTierCapacitiesAndActivationAreDetectedWithoutNames() {
        for(int[] pair:new int[][]{{4000,1},{5000,3},{6000,7},{7000,12}}) {
            var data=CompactorData.parse("PERSONAL_COMPACTOR_"+pair[0],1,Map.of(pair[1]-1,"ENCHANTED_GOLD"));
            assertEquals(pair[0],data.tier());assertEquals(pair[1],CompactorData.capacity(data.tier()));assertTrue(data.active());
            assertEquals("ENCHANTED_GOLD",data.recipes().get(pair[1]-1));
        }
        assertNull(CompactorData.parse("SUPER_COMPACTOR_3000",1,Map.of()));
        assertNull(CompactorData.parse("PERSONAL_DELETOR_7000",1,Map.of()));
    }
    @Test void missingActivationStaysUnknownAndSnapshotsAreImmutable() {
        var input=new HashMap<Integer,String>();input.put(0,"ENCHANTED_GOLD");input.put(1,"");
        var data=CompactorData.parse("PERSONAL_COMPACTOR_7000",null,input);input.clear();
        assertNull(data.active());assertEquals(Map.of(0,"ENCHANTED_GOLD"),data.recipes());
        assertThrows(UnsupportedOperationException.class,()->data.recipes().clear());
        assertNull(CompactorData.parse("PERSONAL_COMPACTOR_7000",2,Map.of()).active());
        assertFalse(CompactorData.parse("PERSONAL_COMPACTOR_7000",0,Map.of()).active());
    }
    @Test void malformedSlotsAndRecipeIdsCannotBecomeConfigurationEvidence() {
        assertThrows(IllegalArgumentException.class,()->CompactorData.parse("PERSONAL_COMPACTOR_4000",1,Map.of(1,"ENCHANTED_GOLD")));
        assertThrows(IllegalArgumentException.class,()->CompactorData.parse("PERSONAL_COMPACTOR_7000",1,Map.of(0,"broken recipe")));
    }
}
