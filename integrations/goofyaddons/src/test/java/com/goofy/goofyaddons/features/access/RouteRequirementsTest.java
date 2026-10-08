package com.goofy.goofyaddons.features.access;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RouteRequirementsTest {
    @Test void bookMinimumIsInclusiveAndUnknownIsNotZero() {
        assertNotNull(RouteRequirements.book("ENCHANTMENT_OVERLOAD",Map.of("enchanting",32)));
        assertNull(RouteRequirements.book("ENCHANTMENT_OVERLOAD",Map.of("enchanting",33)));
        assertNull(RouteRequirements.book("ENCHANTMENT_OVERLOAD",Map.of("enchanting",34)));
        assertNotNull(RouteRequirements.book("ENCHANTMENT_OVERLOAD",Map.of()));
        assertNotNull(RouteRequirements.book("ENCHANTMENT_UNKNOWN",Map.of("enchanting",60)));
        assertNull(RouteRequirements.book("ENCHANTMENT_TURBO_SUNFLOWER",Map.of("enchanting",0)));
    }
    @Test void combinedCraftUnlocksMustAllBeKnownAndAtOrAboveTheMinimum() {
        String rule="Requires: Diamond IV & Fishing XXXV & Zombie Slayer 3";
        assertNull(RouteRequirements.craft(rule,Map.of("fishing",35),Map.of("diamond",4,"zombieslayer",3)));
        assertNotNull(RouteRequirements.craft(rule,Map.of("fishing",34),Map.of("diamond",4,"zombieslayer",3)));
        assertNotNull(RouteRequirements.craft(rule,Map.of("fishing",35),Map.of("diamond",3,"zombieslayer",3)));
        assertNotNull(RouteRequirements.craft(rule,Map.of("fishing",35),Map.of("diamond",4)));
        assertNotNull(RouteRequirements.craft("Requires: Special Quest",Map.of(),Map.of()));
    }
    private com.google.gson.JsonObject profile() {
        return JsonParser.parseString("""
            {"protocol":"goofy-profile/1","username":"Tester","fetchedAt":1000000,"profiles":[
             {"selected":true,"unknown":["Heart of the Mountain"],"stats":{
               "skills":{"Enchanting":33},"collections":{"Diamond":4},"slayers":{"Zombie":3},"reputation":{},"hotmTier":0}}]}
            """).getAsJsonObject();
    }
    @Test void profileLookupRequiresFreshMatchingAccountAndLiveEnchanting() {
        var root=profile();var skills=Map.of("enchanting",33);
        var unlocks=AccountUnlocks.parse(root,"Tester",skills,1000000);
        assertEquals(4,unlocks.get("diamond"));assertEquals(3,unlocks.get("zombieslayer"));assertFalse(unlocks.containsKey("hotm"));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parse(root,"Other",skills,1000000));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parse(root,"Tester",Map.of(),1000000));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parse(root,"Tester",Map.of("enchanting",32),1000000));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parse(root,"Tester",skills,1300000));
        root.getAsJsonArray("profiles").add(root.getAsJsonArray("profiles").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parse(root,"Tester",skills,1000000));
    }
}
