package com.goofy.goofyaddons.utils;

public class Clock {
    private final java.util.function.LongSupplier now;
    public Clock() {this(System::currentTimeMillis);}
    public Clock(java.util.function.LongSupplier now) {this.now=java.util.Objects.requireNonNull(now);}
    private long duration;
    private long startMs;
    private boolean running = false;

    public void start(long ms) {
        if (running) return;
        this.duration = ms;
        this.startMs = now.getAsLong();
        this.running = true;
    }

    public boolean shouldFire() {
        if (!running) return false;
        if (now.getAsLong() - startMs >= duration) {
            stop();
            return true;
        }
        return false;
    }

    public void reset() {
        startMs = now.getAsLong();
    }

    public void stop() {
        running = false;
    }
}
