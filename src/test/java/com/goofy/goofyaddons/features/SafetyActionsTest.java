package com.goofy.goofyaddons.features;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SafetyActionsTest {
    @Test void observationAndCleanupFailuresCannotUndoLatchOrSkipOtherCleanup() {
        List<String> actions=new ArrayList<>();
        boolean[] paused={false};
        SafetyActions.latch(()->paused[0]=true, error->{throw new RuntimeException("logger also fails");},
                ()->{assertTrue(paused[0]); throw new RuntimeException("book cleanup");},
                ()->actions.add("general paused"),
                ()->{throw new RuntimeException("snapshot");},
                ()->actions.add("user informed"));
        assertTrue(paused[0]);
        assertEquals(List.of("general paused","user informed"),actions);
    }
    @Test void queuedStopPreventsEligibleConfirmationInSameTick() {
        List<String> actions=new ArrayList<>();
        if(!SafetyActions.stopFirst(true,()->actions.add("stop"))) actions.add("confirm");
        assertEquals(List.of("stop"),actions);
        if(!SafetyActions.stopFirst(false,()->actions.add("stop"))) actions.add("confirm");
        assertEquals(List.of("stop","confirm"),actions);
    }
    @Test void tickReplayRunsStopBeforeFailsafeAndTradingActions() {
        List<String> actions=new ArrayList<>();
        assertTrue(SafetyActions.tradingTick(true,()->actions.add("stop"),
                ()->actions.add("warp"),()->actions.add("confirm order")));
        assertEquals(List.of("stop"),actions);
        assertFalse(SafetyActions.tradingTick(false,()->actions.add("stop"),
                ()->actions.add("failsafe observation"),()->actions.add("trading observation")));
        assertEquals(List.of("stop","failsafe observation","trading observation"),actions);
    }
}
