package com.goofy.goofyaddons.diagnostics;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DebugMenuCaptureTest {
    @Test void exportsSelectedItemOwnershipFactsWithoutPetOrRawMetadata() {
        var device=new SlotView(54,true,0,false,"Compactor","Personal Compactor 7000",List.of(),
            "PERSONAL_COMPACTOR_7000",null,1,1,new ItemMetadata("item-device-uuid","private-pet-type","private-pet-tier",100.0,"private-held-item","private-skin",2,"minecraft:dropper",
            new CompactorData(7000,true,Map.of(0,"ENCHANTED_GOLD")),"a".repeat(64)));
        var control=new SlotView(10,false,10,false,"Recipe","Configure recipe",List.of("Click to remove!"),null,null,1,64);
        var cursor=new SlotView(-1,false,-1,false,"Gold","Gold Ingot",List.of(),"GOLD_INGOT",null,32,64);
        var result=DebugMenuCapture.describe(new MenuSnapshot(4,"Personal Compactor",false,
            List.of(SlotView.empty(0,false,0),control,device),cursor,42));
        String json=new Gson().toJson(result);
        assertTrue(json.contains("item-device-uuid"));assertFalse(json.contains("private-pet"));
        assertFalse(json.contains("private-held-item"));assertFalse(json.contains("private-skin"));
        assertFalse(json.contains("petExperience"));assertFalse(json.contains("petCandyUsed"));
        assertFalse(json.contains("rawNbt"));assertFalse(json.contains("components"));
        assertTrue(json.contains("Click to remove!"));assertTrue(json.contains("ENCHANTED_GOLD"));
        assertEquals(2,((List<?>)result.get("slots")).size());
        assertEquals(32,((Map<?,?>)result.get("carried")).get("count"));
        var inventory=(List<?>)result.get("inventory");assertEquals(1,inventory.size());
        var observed=(Map<?,?>)inventory.getFirst();assertEquals(0,observed.get("inventorySlot"));
        assertEquals(1,observed.get("maxStackSize"));assertEquals("minecraft:dropper",observed.get("vanillaId"));
        assertEquals("item-device-uuid",observed.get("uuid"));assertEquals("a".repeat(64),observed.get("stackIdentity"));
        assertEquals(42L,result.get("serverObservation"));assertEquals(false,result.get("cursorEmpty"));
    }
    @Test void distinguishesSameProductWithDifferentComponentsInGridInventoryAndCursor() {
        var first=new ItemMetadata("smelter-one",null,null,null,null,null,null,"minecraft:furnace",null,"b".repeat(64));
        var second=new ItemMetadata("smelter-two",null,null,null,null,null,null,"minecraft:furnace",null,"c".repeat(64));
        var grid=new SlotView(11,false,11,false,"Auto Smelter","Auto Smelter",List.of(),"AUTO_SMELTER",null,1,64,first);
        var held=new SlotView(84,true,30,false,"Auto Smelter","Auto Smelter",List.of(),"AUTO_SMELTER",null,1,64,second);
        var cursor=new SlotView(-1,false,-1,false,"Auto Smelter","Auto Smelter",List.of(),"AUTO_SMELTER",null,1,64,second);
        var result=DebugMenuCapture.describe(new MenuSnapshot(72,"Craft Item",false,List.of(grid,held),cursor));
        var observedGrid=(Map<?,?>)((List<?>)result.get("slots")).getFirst();
        var observedHeld=(Map<?,?>)((List<?>)result.get("inventory")).getFirst();
        var observedCursor=(Map<?,?>)result.get("carried");
        assertEquals(observedGrid.get("productId"),observedCursor.get("productId"));
        assertEquals(observedGrid.get("maxStackSize"),observedCursor.get("maxStackSize"));
        assertNotEquals(observedGrid.get("stackIdentity"),observedCursor.get("stackIdentity"));
        assertEquals(observedHeld.get("stackIdentity"),observedCursor.get("stackIdentity"));
        assertEquals("smelter-one",observedGrid.get("uuid"));assertEquals("smelter-two",observedCursor.get("uuid"));
        assertEquals(30,observedHeld.get("inventorySlot"));
    }
    @Test void disconnectedClientProducesExplicitUnavailableCapture() {
        assertEquals(Map.of("available",false),DebugMenuCapture.describe(null));
    }
}
