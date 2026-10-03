package com.goofy.goofyaddons.features.bookflipper.helper;

/**
 * What to do when COMBINE has chosen a pair to merge and the inputs are not in the inventory.
 *
 * <p>The engine used to return and try again, with no bound: one field run span this 110 times
 * in 36 seconds before the stall watchdog stopped it. The cause was a buy order that had filled
 * but was never claimed, so the books were real and sitting in the Bazaar while the model had
 * them in hand.
 *
 * <p>Separated out so the policy can be tested without a menu. Three outcomes only.
 */
public final class CombineRecovery {
    private CombineRecovery() {}

    public enum Action {
        /** Within the settle window; the menu may just not have caught up yet. */
        WAIT,
        /** Re-read the live orders menu and claim what is actually there. */
        RECONCILE,
        /** Already reconciled once for this task; a second divergence is not a timing artefact. */
        HALT
    }

    /**
     * @param consecutiveTicks how many ticks in a row the inputs have been absent, counting this one
     * @param settleTicks      how many absences to tolerate before acting
     * @param alreadyReconciled whether this task has been sent back to the Bazaar once already
     */
    public static Action decide(int consecutiveTicks, int settleTicks, boolean alreadyReconciled) {
        if (consecutiveTicks < settleTicks) return Action.WAIT;
        return alreadyReconciled ? Action.HALT : Action.RECONCILE;
    }
}
