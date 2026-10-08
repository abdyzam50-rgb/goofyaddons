package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.config.GoofyConfig;

import java.util.SplittableRandom;

/** One randomised inter-action delay for every engine, so timing does not differ per engine. */
public final class ActionDelay {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private ActionDelay() {}

    /** Milliseconds to wait before the next menu action. */
    public static int next() {
        int min = GoofyConfig.INSTANCE.minActionDelay;
        int max = GoofyConfig.INSTANCE.maxActionDelay;
        // Config validation enforces min < max, but defaults must never make this throw.
        return max > min ? RANDOM.nextInt(min, max) : Math.max(1, min);
    }
}
