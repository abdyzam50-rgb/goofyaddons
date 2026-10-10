package com.goofy.goofyaddons.features.production;

import java.util.HashMap;
import java.util.Map;

/** Wait for server-populated controls without clicking placeholders or extending a deadline each tick. */
final class MenuLoadGrace {
    private final Map<String,Long> waiting=new HashMap<>();
    boolean expired(String evidence,long now) {
        return now-waiting.computeIfAbsent(evidence,ignored->now)>=15_000;
    }
    void clear(String evidence){waiting.remove(evidence);}
    void reset(){waiting.clear();}
}
