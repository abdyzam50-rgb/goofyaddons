package com.goofy.goofyaddons.features;

/**
 * A freshly opened container must be observed for a while before its contents are
 * treated as the server's answer. Both engines had their own copy of this debounce;
 * books kept it inline in onTick, general inside ordersReady().
 */
public final class MenuSettle {
    private static final long SETTLE_MS = 750;
    private int container = -1;
    private long since;

    /** False until the same container has been open for the settle window. */
    public boolean settled(int currentContainer, long now) {
        if (container != currentContainer) {
            container = currentContainer;
            since = now;
            return false;
        }
        return now - since >= SETTLE_MS;
    }

    /** The container currently being observed, or -1 when none is. */
    public int container() { return container; }

    public void reset() {
        container = -1;
        since = 0;
    }
}
