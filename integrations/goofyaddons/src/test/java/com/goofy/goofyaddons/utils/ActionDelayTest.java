package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.config.GoofyConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionDelayTest {
    @Test void categoryRangesAreIndependentAndInheritNavigationUntilConfigured(){
        var old=GoofyConfig.INSTANCE;
        try {
            var c=new GoofyConfig();c.minActionDelay=100;c.maxActionDelay=101;GoofyConfig.INSTANCE=c;
            assertEquals(100,ActionDelay.craft());assertEquals(100,ActionDelay.storage());
            c.minCraftDelay=10;c.maxCraftDelay=11;c.minStorageDelay=20;c.maxStorageDelay=21;c.validate();
            assertEquals(100,ActionDelay.next());assertEquals(10,ActionDelay.craft());assertEquals(20,ActionDelay.storage());
            c.maxStorageDelay=0;assertThrows(IllegalArgumentException.class,c::validate);
        }finally{GoofyConfig.INSTANCE=old;}
    }
    @Test void oneToTwoMillisecondRangeValidates(){
        var old=GoofyConfig.INSTANCE;
        try {
            var cfg=new GoofyConfig();cfg.minActionDelay=1;cfg.maxActionDelay=2;
            assertDoesNotThrow(cfg::validate);GoofyConfig.INSTANCE=cfg;
            for(int i=0;i<100;i++){int delay=ActionDelay.next();assertTrue(delay>=1&&delay<2);}
            cfg.minActionDelay=0;assertThrows(IllegalArgumentException.class,cfg::validate);
        }finally{GoofyConfig.INSTANCE=old;}
    }
    @Test void freshInstallUsesSixtyToSeventyMilliseconds(){
        var cfg=new GoofyConfig();assertEquals(60,cfg.minActionDelay);assertEquals(70,cfg.maxActionDelay);assertDoesNotThrow(cfg::validate);
    }
    @Test void sharedVariationStaysWithinConfiguredBoundsAndHasStartupDefaults() {
        var old=GoofyConfig.INSTANCE;
        try {
            var cfg=new GoofyConfig();cfg.minActionDelay=75;cfg.maxActionDelay=125;GoofyConfig.INSTANCE=cfg;
            var observed=new java.util.HashSet<Integer>();
            for(int i=0;i<200;i++){int delay=ActionDelay.next();assertTrue(delay>=75&&delay<125);observed.add(delay);}
            assertTrue(observed.size()>1,"The configured range must produce variation");
            GoofyConfig.INSTANCE=null;int startup=ActionDelay.next();assertTrue(startup>=60&&startup<70);
        }finally{GoofyConfig.INSTANCE=old;}
    }
}
