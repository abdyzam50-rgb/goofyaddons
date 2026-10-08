package com.goofy.goofyaddons.features;

/** Counts time while the engine owns a transaction; idle order waits are excluded. */
public final class TransactionWatchdog {
    private String previous;
    private long started;
    private long changed;
    public boolean stalled(String progress, boolean idle, long now) {
        if (idle) { reset(); return false; }
        if (previous == null) { started = now; changed = now; }
        else if (!previous.equals(progress)) changed = now;
        previous = progress;
        return now - changed >= 60000 || now - started >= 300000;
    }
    public void reset() { previous = null; started = changed = 0; }
}
