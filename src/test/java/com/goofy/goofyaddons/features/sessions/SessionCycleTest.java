package com.goofy.goofyaddons.features.sessions;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionCycleTest {
    private final RestScheduleSettings settings=new RestScheduleSettings();
    static final class Port implements SessionCycle.Port {
        boolean connected=true,connecting,ready,boundary=true,blocked;List<String> actions=new ArrayList<>();
        public boolean connected(){return connected;}public boolean connecting(){return connecting;}
        public boolean ready(){return ready;}public boolean transactionBoundary(){return boundary;}public boolean blocked(){return blocked;}
        public void stop(){actions.add("stop");}public void disconnect(){actions.add("disconnect");connected=false;}
        public void connect(){actions.add("connect");}public void command(String s){actions.add(s);}
        public void start(){actions.add("start");}public void block(String s){actions.add("block");blocked=true;}
    }
    @Test void finishExistingTransactionThenStopPersistAndDisconnectWithoutRunningDuringRest() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);p.boundary=false;
        assertFalse(c.tick(false,1000,settings,p));assertEquals(SessionCycle.State.FINISHING,c.state());assertTrue(p.actions.isEmpty());
        p.boundary=true;assertTrue(c.tick(false,2000,settings,p));assertEquals(List.of("stop","disconnect"),p.actions);
        assertTrue(c.tick(false,90000,settings,p));assertEquals(2,p.actions.size());
        assertTrue(c.tick(true,100000,settings,p));assertEquals("connect",p.actions.getLast());assertEquals(SessionCycle.State.CONNECTING,c.state());
    }
    @Test void reconnectWaitsForWorldAndReturnsToSkyblockBeforeRestartingTrading() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);c.tick(false,1000,settings,p);c.tick(true,40000,settings,p);
        p.connected=true;c.tick(true,41000,settings,p);assertEquals(SessionCycle.State.JOINING,c.state());
        c.tick(true,49000,settings,p);assertEquals("skyblock",p.actions.getLast());assertFalse(p.actions.contains("start"));
        p.ready=true;c.tick(true,50000,settings,p);assertEquals("is",p.actions.getLast());
        c.tick(true,58000,settings,p);assertFalse(p.actions.contains("start"));
        p.ready=false;c.tick(true,59000,settings,p);p.ready=true;c.tick(true,60000,settings,p);
        c.tick(true,63000,settings,p);assertEquals("start",p.actions.getLast());assertEquals(SessionCycle.State.ACTIVE,c.state());
        assertFalse(c.tick(true,64000,settings,p));
    }
    @Test void manualStopAndUnexpectedDisconnectNeverTriggerAutomaticReconnect() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);c.tick(false,1000,settings,p);c.cancel("Manual stop");
        assertFalse(c.tick(true,1000000,settings,p));assertFalse(p.actions.contains("connect"));
        c.arm(1000001);p.connected=false;c.tick(true,1000002,settings,p);assertEquals(SessionCycle.State.BLOCKED,c.state());
        c.tick(true,2000000,settings,p);assertFalse(p.actions.contains("connect"));
    }
    @Test void pendingTransactionAndSafetyBlockPreventBlindScheduledLogout() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);p.boundary=false;c.tick(false,1000,settings,p);
        c.tick(false,182000,settings,p);assertEquals(SessionCycle.State.BLOCKED,c.state());assertEquals(List.of("block"),p.actions);
        c=new SessionCycle();p=new Port();c.arm(1000);p.blocked=true;c.tick(false,1001,settings,p);
        assertFalse(p.actions.contains("disconnect"));assertFalse(c.armed());
    }
    @Test void failedReconnectsHaveCooldownAndHardAttemptLimitWithoutParallelConnections() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);c.tick(false,1000,settings,p);c.tick(true,40000,settings,p);
        c.tick(true,130000,settings,p);assertEquals(1,p.actions.stream().filter("connect"::equals).count());
        c.tick(true,190000,settings,p);c.tick(true,340000,settings,p);c.tick(true,430000,settings,p);
        assertEquals(3,p.actions.stream().filter("connect"::equals).count());assertEquals(SessionCycle.State.BLOCKED,c.state());
        c=new SessionCycle();p=new Port();c.arm(1000);c.tick(false,1000,settings,p);c.tick(true,40000,settings,p);
        p.connecting=true;c.tick(true,130000,settings,p);assertEquals(SessionCycle.State.BLOCKED,c.state());assertEquals(1,p.actions.stream().filter("connect"::equals).count());
    }
    @Test void doNotResumeOutsidePlayWindowAndManualConnectionDuringRestOverridesTheSchedule() {
        var c=new SessionCycle();var p=new Port();c.arm(1000);c.tick(false,1000,settings,p);c.tick(true,40000,settings,p);
        p.connected=true;c.tick(false,41000,settings,p);assertEquals(SessionCycle.State.RESTING,c.state());assertFalse(p.actions.contains("start"));
        p.connected=true;c.tick(false,42000,settings,p);assertEquals(SessionCycle.State.DISARMED,c.state());
    }
}
