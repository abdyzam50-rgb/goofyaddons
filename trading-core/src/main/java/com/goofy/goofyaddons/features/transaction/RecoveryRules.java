package com.goofy.goofyaddons.features.transaction;

/**
 * The timeouts and retry limits both trading engines recover by.
 *
 * <p>Each value used to be a literal in each engine. They happened to agree, but nothing kept
 * them agreeing, so a fix to one engine's patience could silently leave the other behind.
 * Changing one here changes it for book and general trading together.
 */
public final class RecoveryRules {
    private RecoveryRules() {}

    /** An amount or price input that has not advanced in this long is reopened from the product. */
    public static final long INPUT_RESTART_MS = 8_000;
    /** How many times an unadvanced input may be reopened before the engine stops retrying it. */
    public static final int MAX_INPUT_RESTARTS = 2;
    /** Time for a submitted order to reach the server before the orders menu is read for it. */
    public static final long ORDER_SETTLE_MS = 2_000;
    /** How long a sale claim waits for its coin receipt before relying on the order's removal. */
    public static final long RECEIPT_GRACE_MS = 10_000;
    /** A selected price older than this no longer authorizes its confirmation click. */
    public static final long SELECTION_EXPIRY_MS = 30_000;
    /** A single transaction step that makes no progress for this long is abandoned for recovery. */
    public static final long STEP_TIMEOUT_MS = 30_000;
    /** Ticks a claim may stay unacknowledged before the engine halts instead of guessing. */
    public static final int CLAIM_ACKNOWLEDGEMENT_TICKS = 1_200;
}
