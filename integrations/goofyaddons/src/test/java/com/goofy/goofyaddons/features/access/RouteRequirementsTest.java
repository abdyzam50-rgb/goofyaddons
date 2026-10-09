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
    @Test void liveProfileIdentityUnlocksApiSkillsAndCollectionsWithoutSkillsGui() {
        var root=profile();var p=root.getAsJsonArray("profiles").get(0).getAsJsonObject();p.addProperty("name","Mango");
        p.addProperty("selected",false);p.getAsJsonObject("stats").getAsJsonObject("skills").addProperty("Fishing",35);
        var parsed=AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of(),1000000);
        assertEquals(35,parsed.skills().get("fishing"));assertEquals(33,parsed.skills().get("enchanting"));
        assertNull(RouteRequirements.craft("Diamond IV & Fishing XXXV & Zombie Slayer 3",parsed.skills(),parsed.unlocks()));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Apple",Map.of(),1000000));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of("fishing",34),1000000));
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of(),1300000));
        root.getAsJsonArray("profiles").add(p.deepCopy());
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of(),1000000));
    }
    @Test void unpublishedProgressionStaysUnknownAndLiveSkillsOverrideApi() {
        var root=profile();var p=root.getAsJsonArray("profiles").get(0).getAsJsonObject();p.addProperty("name","Mango");
        p.getAsJsonArray("unknown").add("enchanting");p.getAsJsonArray("unknown").add("collections");
        var parsed=AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of(),1000000);
        assertFalse(parsed.skills().containsKey("enchanting"));assertFalse(parsed.unlocks().containsKey("diamond"));
        assertEquals(Map.of("enchanting",34,"fishing",35),AccountUnlocks.combinedSkills(Map.of("enchanting",33,"fishing",35),Map.of("enchanting",34)));
        p.getAsJsonObject("stats").getAsJsonObject("skills").addProperty("Fishing",35.5);
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Mango",Map.of(),1000000));
    }
}
