package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.JsonObject;
import com.goofy.goofyaddons.features.TradingSafety;

/** Atomically publish snapshots by server timestamp, never response arrival order. */
public final class BazaarQuoteCache {
    private JsonObject latest;
    private long sourceTime;
    public synchronized JsonObject publish(JsonObject root,long now) {
        long timestamp=TradingSafety.sourceTime(root,now);
        if(!root.has("products") || !root.get("products").isJsonObject()) throw new IllegalArgumentException("Missing products");
        if(latest==null || timestamp>sourceTime) {
            latest=root.deepCopy();sourceTime=timestamp;
        }
        return latest;
    }
    /** Readers treat the published JSON as immutable. */
    public synchronized JsonObject fresh(long now) {
        return latest!=null && TradingSafety.fresh(sourceTime,now) ? latest : null;
    }
}
