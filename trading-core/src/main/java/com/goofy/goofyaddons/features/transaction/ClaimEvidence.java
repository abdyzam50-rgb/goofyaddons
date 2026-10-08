package com.goofy.goofyaddons.features.transaction;

/**
 * Whether a claim reached the inventory, judged only by the count before it and the units
 * it should deliver.
 *
 * <p>A claim is never acknowledged by chat. Both engines compare the inventory against the
 * count taken when the claim was clicked; this keeps that arithmetic in one place. What to
 * do with each outcome stays with the engine: books require the exact increase, while the
 * general engine accepts at least the expected increase because the same item can arrive
 * from elsewhere.
 */
public final class ClaimEvidence {
    private ClaimEvidence() {}

    public enum Outcome { PENDING, EXACT, EXCESS }

    public static Outcome observe(int before, int expected, int count) {
        int target = before + expected;
        if (count < target) return Outcome.PENDING;
        return count == target ? Outcome.EXACT : Outcome.EXCESS;
    }

    /** Some units arrived and at least the expected number did. */
    public static boolean arrived(int before, int expected, int count) {
        return count > before && count >= before + expected;
    }

    /** Units were expected and fewer than that have arrived so far. */
    public static boolean outstanding(int before, int expected, int count) {
        return expected > 0 && count < before + expected;
    }
}
