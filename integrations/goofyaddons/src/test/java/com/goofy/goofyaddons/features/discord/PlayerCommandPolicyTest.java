package com.goofy.goofyaddons.features.discord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerCommandPolicyTest {
    @Test void queuedRequestsCannotUndoANewerLocalStopOrStaffPause() {
        assertTrue(PlayerCommandPolicy.superseded("start",1000,2000,0));
        assertTrue(PlayerCommandPolicy.superseded("chat",1000,0,2000));
        assertTrue(PlayerCommandPolicy.superseded("login",1000,0,2000));
        assertFalse(PlayerCommandPolicy.superseded("stop",1000,0,2000));
        assertFalse(PlayerCommandPolicy.superseded("chat",3000,2000,2500));
    }
    @Test void logoutNeverClosesAnOccupiedCursorAndTimesOutEvenWhenTradingIsStopped() {
        assertEquals(PlayerCommandPolicy.Logout.WAITING,PlayerCommandPolicy.logout(1000,0,true,false));
        assertEquals(PlayerCommandPolicy.Logout.WAITING,PlayerCommandPolicy.logout(1000,0,false,true));
        assertEquals(PlayerCommandPolicy.Logout.DISCONNECT,PlayerCommandPolicy.logout(1000,0,true,true));
        assertEquals(PlayerCommandPolicy.Logout.TIMED_OUT,PlayerCommandPolicy.logout(60001,0,true,false));
    }
    @Test void remoteChatIsPlainTextAndCannotExecuteGameOrDotCommands() {
        for(String text:new String[]{"/is"," .a* stop","hello\n/is","", "a".repeat(257),"hello\u0000"})assertFalse(PlayerCommandPolicy.plainChat(text));
        assertTrue(PlayerCommandPolicy.plainChat("Yes, I'm here."));
    }
}
