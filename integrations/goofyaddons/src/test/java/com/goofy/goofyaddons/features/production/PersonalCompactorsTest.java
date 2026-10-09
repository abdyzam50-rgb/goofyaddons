package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalCompactorsTest {
    static SlotView device(int slot,boolean inventory,int tier,Boolean active,Map<Integer,String> recipes) {
        return new SlotView(slot,inventory,inventory?0:slot,false,"Personal Compactor","Personal Compactor",List.of(),
            "PERSONAL_COMPACTOR_"+tier,null,1,1,new ItemMetadata("device",null,null,null,null,null,null,"minecraft:dropper",new CompactorData(tier,active,recipes)));
    }
    @Test void detectionUsesOwnedSlotsAndDoesNotAdoptAuctionPreviewItems() {
        var menu=new MenuSnapshot(1,"Auctions Browser",true,List.of(device(10,false,7000,true,Map.of()),device(54,true,4000,false,Map.of())));
        assertEquals(1,PersonalCompactors.detect(menu).size());assertEquals(4000,PersonalCompactors.detect(menu).getFirst().metadata().compactor().tier());
        var bag=new MenuSnapshot(2,"Accessory Bag (1/3)",true,List.of(device(10,false,7000,true,Map.of())));
        assertEquals(7000,PersonalCompactors.status(bag).get("tier"));assertEquals(12,PersonalCompactors.status(bag).get("capacity"));
    }
    @Test void everyActiveDeviceIsCheckedEvenWhenAnotherHigherTierIsDisabled() {
        var menu=new MenuSnapshot(1,null,true,List.of(device(54,true,7000,false,Map.of()),device(55,true,4000,true,Map.of(0,"ENCHANTED_GOLD"))));
        assertNotNull(PersonalCompactors.conflict(menu,Set.of("GOLD_INGOT"),RecipeCatalog.instance()));
        assertNull(PersonalCompactors.conflict(menu,Set.of("DIAMOND"),RecipeCatalog.instance()));
    }
    @Test void unknownConfigurationDoesNotAuthorizeProductionAndUnrelatedKnownRecipesRemainUntouched() {
        var menu=new MenuSnapshot(1,null,true,List.of(device(54,true,7000,null,Map.of())));
        assertTrue(PersonalCompactors.conflict(menu,Set.of("GOLD_INGOT"),RecipeCatalog.instance()).contains("unreadable"));
        assertEquals(false,PersonalCompactors.status(menu).get("automaticBulkEnabled"));
        assertTrue(PersonalCompactors.detect(menu).getFirst().metadata().compactor().recipes().isEmpty());
    }
}
