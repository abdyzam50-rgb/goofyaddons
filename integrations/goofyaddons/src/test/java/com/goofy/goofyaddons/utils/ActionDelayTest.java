package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.config.GoofyConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionDelayTest {
    @Test void tenToTwentyMillisecondRangeValidatesAndVaries(){
        var old=GoofyConfig.INSTANCE;
        try {
            var cfg=new GoofyConfig();cfg.minActionDelay=10;cfg.maxActionDelay=20;
            assertDoesNotThrow(cfg::validate);GoofyConfig.INSTANCE=cfg;
            for(int i=0;i<100;i++){int delay=ActionDelay.next();assertTrue(delay>=10&&delay<20);}
            cfg.minActionDelay=9;assertThrows(IllegalArgumentException.class,cfg::validate);
        }finally{GoofyConfig.INSTANCE=old;}
    }
    @Test void sharedVariationStaysWithinConfiguredBoundsAndHasStartupDefaults() {
        var old=GoofyConfig.INSTANCE;
        try {
            var cfg=new GoofyConfig();cfg.minActionDelay=75;cfg.maxActionDelay=125;GoofyConfig.INSTANCE=cfg;
            var observed=new java.util.HashSet<Integer>();
            for(int i=0;i<200;i++){int delay=ActionDelay.next();assertTrue(delay>=75&&delay<125);observed.add(delay);}
            assertTrue(observed.size()>1,"The configured range must produce variation");
            GoofyConfig.INSTANCE=null;int startup=ActionDelay.next();assertTrue(startup>=100&&startup<500);
        }finally{GoofyConfig.INSTANCE=old;}
    }
}
