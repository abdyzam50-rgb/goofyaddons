package com.goofy.goofyaddons.features;

import java.util.List;

/** One UI owner; rotate only at a boundary with no transaction in progress. */
public class MenuScheduler {
    private Feature owner;
    private int next;

    public Feature select(List<Feature> engines) {
        if (owner != null && engines.contains(owner) && owner.isRunning() && !owner.canYield()) return owner;
        owner = null;
        for (int offset = 0; offset < engines.size(); offset++) {
            int index = (next + offset) % engines.size();
            Feature candidate = engines.get(index);
            if (!candidate.isRunning() || !candidate.needsMenu()) continue;
            owner = candidate;
            next = (index + 1) % engines.size();
            return owner;
        }
        return null;
    }

    public boolean canSwitch() {
        return owner == null || owner.canYield() || !owner.isRunning();
    }

    public void reset() {
        owner = null;
        next = 0;
    }
}
