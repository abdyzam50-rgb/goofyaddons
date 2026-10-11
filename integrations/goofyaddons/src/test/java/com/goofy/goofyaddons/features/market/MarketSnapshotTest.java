package com.goofy.goofyaddons.features.market;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketSnapshotTest {
    @Test void keepsTheTopLevelsSpreadAndMovementOfOnlyTheNamedProducts(){
        var market=JsonParser.parseString("{\"lastUpdated\":1000,\"products\":{"
            +"\"SMELTER\":{\"buy_summary\":[{\"pricePerUnit\":48000,\"amount\":3,\"orders\":1},{\"pricePerUnit\":49000,\"amount\":2,\"orders\":1},{\"pricePerUnit\":50000,\"amount\":1,\"orders\":1},{\"pricePerUnit\":51000,\"amount\":1,\"orders\":1}],"
            +"\"sell_summary\":[{\"pricePerUnit\":1,\"amount\":1,\"orders\":1}],\"quick_status\":{\"buyMovingWeek\":500,\"sellMovingWeek\":20}},"
            +"\"OTHER\":{\"buy_summary\":[],\"sell_summary\":[]}}}").getAsJsonObject();
        var snapshot=MarketSnapshot.of(market,List.of("SMELTER","GONE"),1500);
        assertEquals(500L,snapshot.get("marketAgeMs"));
        @SuppressWarnings("unchecked") var products=(Map<String,Map<String,Object>>)snapshot.get("products");
        assertEquals(Set.of("SMELTER","GONE"),products.keySet());
        var smelter=products.get("SMELTER");
        assertEquals(3,((List<?>)smelter.get("asks")).size());
        assertEquals(false,smelter.get("orderableBid"));
        assertEquals(20.0,smelter.get("sellMovingWeek"));
        assertEquals(Map.of("missing",true),products.get("GONE"));
        assertTrue(MarketSnapshot.of(null,List.of("SMELTER"),0).isEmpty());
    }
}
