package com.goofy.goofyaddons.features;

public interface Feature {
    String name();

    void stop();

    void start();

    void pause();

    void resume();

    void onTick();

    boolean isRunning();

    default void poll() {}

    default void navigationResumed(long elapsed) {}

    default boolean needsMenu() { return isRunning(); }

    default boolean canYield() { return true; }

    default void yieldMenu() {}
}
