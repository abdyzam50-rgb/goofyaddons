package com.goofy.goofyaddons.menu;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.goofy.goofyaddons.menu.NavigationRetry.Result.*;

class NavigationRetryTest {
    final NavigationRetry flow=new NavigationRetry();
    final RecordingActions actions=new RecordingActions();
    MenuSnapshot menu(int id,String title,String button,boolean cursor) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<72;i++)slots.add(SlotView.empty(i,i>=36,i>=36?i-36:i));
        slots.set(16,SlotView.named(16,button,List.of("exact control")));
        return new MenuSnapshot(id,title,cursor,slots);
    }
    final MenuSnapshot source=menu(1,"How many?","Custom Amount",true);
    @Test void ignoredNavigationRetriesOnlyAfterQuietStableObservation() {
        flow.sent(source,16,0);
        assertEquals(WAITING,flow.observe(source,false,actions,2200));
        assertEquals(WAITING,flow.observe(source,false,actions,2900));
        assertEquals(RETRIED,flow.observe(source,false,actions,3000));
        assertEquals(List.of("click:16"),actions.serverEffects());
        assertEquals(READY,flow.observe(menu(2,"Next screen","Different",true),false,actions,3100));
        assertFalse(flow.pending());assertEquals(1,actions.serverEffects().size());
    }
    @Test void signAppearanceAcknowledgesTheCustomAmountClick() {
        flow.sent(source,16,0);assertEquals(READY,flow.observe(source,true,actions,200));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void changedControlCursorOrClosedScreenCannotAuthorizeReplay() {
        flow.sent(source,16,0);
        for(long now:new long[]{4000,6000,8000}) {
            assertEquals(WAITING,flow.observe(menu(1,source.title(),"Confirm Order",true),false,actions,now));
            assertEquals(WAITING,flow.observe(menu(1,source.title(),"Custom Amount",false),false,actions,now));
            assertEquals(WAITING,flow.observe(menu(1,null,"Custom Amount",true),false,actions,now));
        }
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void repeatedFailureStopsAfterThreeRetriesWithinBoundedTime() {
        flow.sent(source,16,0);
        for(long now=0;now<15000;now+=250)flow.observe(source,false,actions,now);
        assertEquals(3,actions.serverEffects().size());
        assertEquals(EXHAUSTED,flow.observe(source,false,actions,15000));
        assertEquals(3,actions.serverEffects().size());
    }
    @Test void slowdownClearsQuietWindowAndResetDiscardsPreviousTransaction() {
        flow.sent(source,16,0);flow.observe(source,false,actions,2250);flow.slowdown(2900);
        assertEquals(WAITING,flow.observe(source,false,actions,3000));
        assertEquals(WAITING,flow.observe(source,false,actions,4000));
        assertEquals(RETRIED,flow.observe(source,false,actions,4500));
        flow.reset();assertEquals(READY,flow.observe(source,false,actions,18000));
        assertEquals(1,actions.serverEffects().size());
    }
}
