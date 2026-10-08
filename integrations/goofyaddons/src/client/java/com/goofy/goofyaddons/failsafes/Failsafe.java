package com.goofy.goofyaddons.failsafes;

public interface Failsafe {
    String name();

    void onTick();

    default void reset() {}
}
