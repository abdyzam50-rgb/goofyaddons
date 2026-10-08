package com.goofy.goofyaddons.features;

import java.util.function.Consumer;

/** Safety state is latched before fallible cleanup or observation. */
public final class SafetyActions {
    private SafetyActions() {}
    public static void latch(Runnable latch, Consumer<RuntimeException> failure, Runnable... cleanup) {
        latch.run();
        for (Runnable action : cleanup) {
            try { action.run(); }
            catch (RuntimeException error) {
                try { failure.accept(error); } catch (RuntimeException ignored) { /* Keep the safety latch. */ }
            }
        }
    }
    /** Injectable tick boundary used by the client and deterministic action replays. */
    public static boolean tradingTick(boolean stopRequested, Runnable stop, Runnable failsafes, Runnable trading) {
        if (stopFirst(stopRequested, stop)) return true;
        failsafes.run();
        trading.run();
        return false;
    }
    /** Returns true when the stop consumed this tick; later actions must be skipped. */
    public static boolean stopFirst(boolean requested, Runnable stop) {
        if (!requested) return false;
        stop.run();
        return true;
    }
}
