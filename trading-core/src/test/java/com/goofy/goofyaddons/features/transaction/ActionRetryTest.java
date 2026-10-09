package com.goofy.goofyaddons.features.transaction;

import com.goofy.goofyaddons.menu.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionRetryTest {
    private final MenuSnapshot menu=new MenuSnapshot(1,"Anvil",true,List.of(SlotView.named(22,"Anvil",List.of())));
    @Test void repeatedSlowdownsExtendCooldownWithoutAuthorizingImmediateClicks() {
        var retry=new ActionRetry();var actions=new RecordingActions();
        retry.sent(22,false,1000);retry.slowdown(3900);
        retry.retry(menu,true,actions,4000);
        assertFalse(retry.retry(menu,true,actions,4800));
        retry.slowdown(5000);
        assertFalse(retry.retry(menu,true,actions,5100));
        assertFalse(retry.retry(menu,true,actions,6000));
        assertTrue(retry.retry(menu,true,actions,6500));
        assertEquals(List.of("click:22"),actions.serverEffects());
    }
    @Test void exhaustedRetriesNeverTurnIntoAnUnboundedClickLoop() {
        var retry=new ActionRetry();var actions=new RecordingActions();
        retry.sent(22,false,1000);
        for(long now=1500;now<25000;now+=500) retry.retry(menu,true,actions,now);
        assertEquals(3,actions.serverEffects().size());assertEquals(3,retry.retries());
    }
    @Test void partialAcknowledgementResetsTheUnchangedObservationWindow() {
        var retry=new ActionRetry();var actions=new RecordingActions();
        retry.sent(22,false,1000);retry.retry(menu,true,actions,4000);
        retry.retry(menu,false,actions,4500);
        assertFalse(retry.retry(menu,true,actions,5000));
        assertFalse(retry.retry(menu,true,actions,5500));
        assertTrue(retry.retry(menu,true,actions,5800));
        assertEquals(1,actions.serverEffects().size());
    }
    @Test void onlyServerStyleSlowdownNoticesAreRecognized() {
        assertTrue(ActionRetry.slowdownMessage("§cYou are clicking too fast! Slow down!"));
        assertTrue(ActionRetry.slowdownMessage("You're doing that too fast!"));
        assertTrue(ActionRetry.slowdownMessage("Please slow down!"));
        assertFalse(ActionRetry.slowdownMessage("[Player] please slow down"));
        assertFalse(ActionRetry.slowdownMessage("[Bazaar] Claimed items!"));
    }
}
