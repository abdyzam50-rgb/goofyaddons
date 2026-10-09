package com.goofy.goofyaddons.diagnostics;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DebugMenuCaptureTest {
    @Test void exportsEmptyControlsDescriptionsCursorAndCompactorSettingsWithoutItemUuid() {
        var device=new SlotView(54,true,0,false,"Compactor","Personal Compactor 7000",List.of(),
            "PERSONAL_COMPACTOR_7000",null,1,1,new ItemMetadata("private-device-uuid",null,null,null,null,null,null,null,
            new CompactorData(7000,true,Map.of(0,"ENCHANTED_GOLD"))));
        var control=new SlotView(10,false,10,false,"Recipe","Configure recipe",List.of("Click to remove!"),null,null,1,64);
        var cursor=new SlotView(-1,false,-1,false,"Gold","Gold Ingot",List.of(),"GOLD_INGOT",null,32,64);
        var result=DebugMenuCapture.describe(new MenuSnapshot(4,"Personal Compactor",false,
            List.of(SlotView.empty(0,false,0),control,device),cursor,42));
        String json=new Gson().toJson(result);
        assertFalse(json.contains("private-device-uuid"));assertFalse(json.contains("uuid"));
        assertTrue(json.contains("Click to remove!"));assertTrue(json.contains("ENCHANTED_GOLD"));
        assertEquals(2,((List<?>)result.get("slots")).size());
        assertEquals(32,((Map<?,?>)result.get("carried")).get("count"));
        assertEquals(42L,result.get("serverObservation"));assertEquals(false,result.get("cursorEmpty"));
    }
    @Test void disconnectedClientProducesExplicitUnavailableCapture() {
        assertEquals(Map.of("available",false),DebugMenuCapture.describe(null));
    }
}
