package com.goofy.goofyaddons.features.bookflipper.helper;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BazaarQuoteCacheTest {
    private JsonObject root(long time,String marker) {
        JsonObject r=new JsonObject();r.addProperty("lastUpdated",time);
        JsonObject p=new JsonObject();p.addProperty("marker",marker);r.add("products",p);return r;
    }
    @Test void slowerOldResponseAndEqualTimestampCannotReplaceNewerSnapshot() {
        var cache=new BazaarQuoteCache();cache.publish(root(100000,"new"),110000);
        assertEquals("new",cache.publish(root(90000,"old"),110000).getAsJsonObject("products").get("marker").getAsString());
        assertEquals("new",cache.publish(root(100000,"equal"),110000).getAsJsonObject("products").get("marker").getAsString());
        assertEquals("newer",cache.publish(root(110000,"newer"),110000).getAsJsonObject("products").get("marker").getAsString());
    }
    @Test void publisherMutationCannotChangeSnapshotAndExpiryUsesSourceTime() {
        var cache=new BazaarQuoteCache();var r=root(100000,"original");cache.publish(r,110000);
        r.getAsJsonObject("products").addProperty("marker","mutated");
        assertEquals("original",cache.fresh(160000).getAsJsonObject("products").get("marker").getAsString());
        assertNull(cache.fresh(160001));
        assertThrows(IllegalStateException.class,()->cache.publish(root(1,"stale"),160001));
        assertNull(cache.fresh(160001));
    }
}
