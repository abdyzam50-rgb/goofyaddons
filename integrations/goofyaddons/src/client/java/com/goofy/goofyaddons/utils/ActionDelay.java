package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.config.GoofyConfig;

import java.util.SplittableRandom;

/** One randomised inter-action delay for every engine, so timing does not differ per engine. */
public final class ActionDelay {
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private ActionDelay() {}

    /** Milliseconds to wait before the next menu action. */
    public static int next() {
        var config=GoofyConfig.INSTANCE;
        int min = config==null?60:config.minActionDelay;
        int max = config==null?70:config.maxActionDelay;
        // Config validation enforces min < max, but defaults must never make this throw.
        return sample(min,max);
    }
    private static int sample(int min,int max){return max>min?RANDOM.nextInt(min,max):Math.max(1,min);}
    public static int craft(){var c=GoofyConfig.INSTANCE;return c==null||c.minCraftDelay==0?next():sample(c.minCraftDelay,c.maxCraftDelay);}
    public static int storage(){var c=GoofyConfig.INSTANCE;return c==null||c.minStorageDelay==0?next():sample(c.minStorageDelay,c.maxStorageDelay);}
}
