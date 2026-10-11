package com.goofy.goofyaddons.features.market;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BazaarQuotesTest {
    static JsonObject product() {
        return JsonParser.parseString("{\"buy_summary\":[{\"pricePerUnit\":10,\"amount\":2},{\"pricePerUnit\":12,\"amount\":5}],"
            +"\"sell_summary\":[{\"pricePerUnit\":9,\"amount\":3},{\"pricePerUnit\":8,\"amount\":10}],"
            +"\"quick_status\":{\"buyMovingWeek\":1680,\"sellMovingWeek\":3360}}").getAsJsonObject();
    }
    @Test void namedSidesReadTheRightBook() {
        var p=product();
        assertEquals(10,BazaarQuotes.bestAsk(p),0);assertEquals(9,BazaarQuotes.bestBid(p),0);
        assertEquals(-1,BazaarQuotes.topPrice(new JsonObject(),BazaarQuotes.ASKS),0);
    }
    @Test void instantQuotesWalkDepthAndChargeTheBuyFee() {
        var p=product();
        assertEquals((2*10+12)*1.04,BazaarQuotes.instantBuyCost(p,3),1e-9);
        assertEquals(3*9+8,BazaarQuotes.instantSellValue(p,4),1e-9);
        assertNull(BazaarQuotes.instantBuyCost(p,8));assertNull(BazaarQuotes.instantSellValue(p,0));
        p.getAsJsonArray("buy_summary").get(1).getAsJsonObject().addProperty("pricePerUnit",-1);
        assertNull(BazaarQuotes.instantBuyCost(p,3));
    }
    @Test void volumesDefaultToZeroAndSnapshotsMatchDirectQuotes() {
        var p=product();
        assertEquals(1680,BazaarQuotes.weeklyFlow(p),0);assertEquals(10,BazaarQuotes.hourlyActivity(p),0);
        assertEquals(0,BazaarQuotes.weeklyFlow(new JsonObject()),0);assertEquals(0,BazaarQuotes.hourlyActivity(null),0);
        var products=new JsonObject();products.add("X",p);var snapshot=new BazaarQuotes.Snapshot(products);
        assertEquals(BazaarQuotes.instantBuyCost(p,5),snapshot.instantBuyCost("X",5));
        assertEquals(BazaarQuotes.instantSellValue(p,7),snapshot.instantSellValue("X",7));
        assertNull(snapshot.instantBuyCost("MISSING",1));assertEquals(10,snapshot.hourlyActivity("X"),0);
    }
}
