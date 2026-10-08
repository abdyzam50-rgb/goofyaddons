package com.goofy.goofyaddons.features;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class MenuRecheckTest {
    @Test void absentSubmittedOrderTriggersOnlyBoundedObservationRetries() {
        var retry=new MenuRecheck();var actions=new ArrayList<String>();
        actions.add("submit Ectoplasm once");
        for(long now:new long[]{0,1499,1500,3999,4000,6500,9000}) {
            var d=retry.missing("verify-sell:Ectoplasm",now);
            if(d==MenuRecheck.Decision.REOPEN) {actions.add("close stale orders");actions.add("open fresh orders");}
        }
        assertEquals(1,actions.stream().filter(x->x.startsWith("submit")).count());
        assertEquals(3,actions.stream().filter(x->x.equals("open fresh orders")).count());
        assertEquals(MenuRecheck.Decision.EXHAUSTED,retry.missing("verify-sell:Ectoplasm",9001));
    }
    @Test void differentOperationAndExplicitResetStartANewBudget() {
        var retry=new MenuRecheck();
        assertEquals(MenuRecheck.Decision.WAIT,retry.missing("placement",0));
        assertEquals(MenuRecheck.Decision.REOPEN,retry.missing("placement",1500));
        assertEquals(MenuRecheck.Decision.WAIT,retry.missing("cancel",1501));
        assertEquals(0,retry.attempts());
        assertEquals(MenuRecheck.Decision.REOPEN,retry.missing("cancel",3001));
        retry.reset();assertEquals(0,retry.attempts());
        assertEquals(MenuRecheck.Decision.WAIT,retry.missing("cancel",9000));
    }
    @Test void refreshedSnapshotCanVerifyOrderWithoutResubmittingOrExhausting() {
        var retry=new MenuRecheck();var actions=new ArrayList<String>();
        actions.add("submit sell 30 Ectoplasm");
        assertEquals(MenuRecheck.Decision.WAIT,retry.missing("Ectoplasm-placement",0));
        assertEquals(MenuRecheck.Decision.REOPEN,retry.missing("Ectoplasm-placement",1500));
        actions.add("close old orders");actions.add("open new orders");
        var quantity=com.goofy.goofyaddons.features.generalflipper.OrderLore.total("Offer amount: 30x\nPrice per unit: 192,341.8 coins\nClick to view options!");
        assertTrue(TradingSafety.orderQuantityMatches(30,quantity));
        actions.add("verify sell 30 Ectoplasm");
        assertEquals(1,retry.attempts());
        assertEquals(java.util.List.of("submit sell 30 Ectoplasm","close old orders","open new orders","verify sell 30 Ectoplasm"),actions);
    }
    @Test void bookOrderCannotBeVerifiedFromAbsenceOrAMismatchedReplacement() {
        assertFalse(TradingSafety.orderMatchesIntent(16,685509.8,null,null));
        assertFalse(TradingSafety.orderMatchesIntent(16,685509.8,15,685509.8));
        assertFalse(TradingSafety.orderMatchesIntent(16,685509.8,16,700000.0));
        assertTrue(TradingSafety.orderMatchesIntent(16,685509.8,16,685509.8));
    }
    @Test void invalidMoneyCannotAcknowledgeABookSubmission() {
        assertFalse(TradingSafety.orderMatchesIntent(16,685509.8,16,Double.NaN));
        assertFalse(TradingSafety.orderMatchesIntent(16,685509.8,16,Double.POSITIVE_INFINITY));
        assertFalse(TradingSafety.orderMatchesIntent(16,Double.NaN,16,685509.8));
        assertFalse(TradingSafety.orderMatchesIntent(16,0,16,0.0));
    }
}
