package com.goofy.goofyaddons.features.access;

/** Adapter contract for the user's forthcoming SkyBlock pathfinder. Client-thread calls only. */
public interface HubPathfinder {
    /** Own movement toward the named BAZAAR landmark in the current Hub. */
    boolean goTo(String landmark);
    boolean active();
    /** Must release every owned movement input immediately. */
    void stop();
}
