package com.goofy.goofyaddons.features.sessions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.goofy.goofyaddons.features.sessions.TransferGate.Result.*;
class TransferGateTest {
    @Test void stableReadableNewWorldIsRequiredBeforeRecovery() {
        var gate=new TransferGate();gate.begin(0);
        assertEquals(WAITING,gate.observe(true,false,false,1000));
        assertEquals(WAITING,gate.observe(true,false,true,3000));
        assertEquals(WAITING,gate.observe(true,false,false,7000));
        assertEquals(WAITING,gate.observe(true,false,true,8000));
        assertEquals(RESUME,gate.observe(true,false,true,13000));assertFalse(gate.pending());
    }
    @Test void manualStopsSafetyBlocksAndTimeoutsNeverAuthorizeResume() {
        var gate=new TransferGate();gate.begin(0);assertEquals(CANCELLED,gate.observe(false,false,true,10000));
        gate.begin(20000);assertEquals(CANCELLED,gate.observe(true,true,true,30000));
        gate.begin(40000);assertEquals(TIMED_OUT,gate.observe(true,false,true,160001));
    }
}
