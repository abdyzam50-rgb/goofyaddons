package com.goofy.goofyaddons.features.access;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RouteRequirementsTest {
    @Test void essenceAndMutationRequirementsAreIndependentOfQuotesAndBudget() {
        assertNotNull(RouteRequirements.product("ESSENCE_WITHER",Map.of()));
        assertNotNull(RouteRequirements.product("ESSENCE_WITHER",Map.of("catacombs",19)));
        assertNull(RouteRequirements.product("ESSENCE_WITHER",Map.of("catacombs",20)));
        assertNotNull(RouteRequirements.products(java.util.List.of("CHORUS_FRUIT","BLAZE_POWDER"),Map.of("catacombs",50)));
        assertNull(RouteRequirements.product("CHORUS_FRUIT",Map.of("mutationchorusfruit",1)));
        assertNull(RouteRequirements.product("BLAZE_POWDER",Map.of()));
        assertNotNull(RouteRequirements.craft("Crop Analyzer Milestone V",Map.of(),Map.of()));
        assertNull(RouteRequirements.craft("Crop Analyzer Milestone V",Map.of(),Map.of("cropanalyzermilestone",5)));
    }
    @Test void dungeonLevelAndNormalMasterFloorClearsAreDifferentFacts() {
        assertNull(RouteRequirements.craft("Cata XX",Map.of(),Map.of("catacombs",20)));
        assertNotNull(RouteRequirements.craft("The Catacombs Floor VII Completion",Map.of(),Map.of("catacombs",50)));
        assertNull(RouteRequirements.craft("The Catacombs Floor VII Completion",Map.of(),Map.of("catacombsfloor7completed",1)));
        assertNotNull(RouteRequirements.craft("Master Mode The Catacombs Floor VII Completion",Map.of(),Map.of("catacombsfloor7completed",1)));
        assertNull(RouteRequirements.craft("Master Mode The Catacombs Floor VII Completion",Map.of(),Map.of("mastercatacombsfloor7completed",1)));
    }
    @Test void profileImportsDungeonFactsWithBoundsAndUnknownHandling() {
        var root=profile();var p=root.getAsJsonArray("profiles").get(0).getAsJsonObject();p.addProperty("name","Apple");
        var stats=p.getAsJsonObject("stats");stats.addProperty("catacombsLevel",20);
        stats.add("dungeonCompletions",JsonParser.parseString("{\"Catacombs Floor 7\":1,\"Master Catacombs Floor 7\":0}"));
        var parsed=AccountUnlocks.parseProfile(root,"Tester","Apple",Map.of(),1000000);
        assertEquals(20,parsed.unlocks().get("catacombs"));assertEquals(1,parsed.unlocks().get("catacombsfloor7completed"));
        p.getAsJsonArray("unknown").add("Catacombs");p.getAsJsonArray("unknown").add("Master Catacombs floor completions");
        parsed=AccountUnlocks.parseProfile(root,"Tester","Apple",Map.of(),1000000);
        assertFalse(parsed.unlocks().containsKey("catacombs"));assertFalse(parsed.unlocks().containsKey("mastercatacombsfloor7completed"));
        p.getAsJsonArray("unknown").remove(p.getAsJsonArray("unknown").size()-2);stats.addProperty("catacombsLevel",51);
        assertThrows(IllegalArgumentException.class,()->AccountUnlocks.parseProfile(root,"Tester","Apple",Map.of(),1000000));
    }

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
    @Test void rawSlayerCodesAndHotmAliasesUseObservedLevels() {
        var levels=Map.of("hotm",6,"wolfslayer",3,"endermanslayer",6,"barbarianreputation",1000);
        assertNull(RouteRequirements.craft("Requires: HotM Tier VI & WOLF_3 & EMAN_6 & BARBARIAN:1000",Map.of(),levels));
        assertNull(RouteRequirements.craft("Heart of the Mountain Level 6",Map.of(),levels));
        assertNull(RouteRequirements.craft("HotM 6",Map.of(),Map.of("heartofthemountain",6)));
        assertNotNull(RouteRequirements.craft("WOLF_4",Map.of(),levels));
        assertTrue(RouteRequirements.craft("BLAZE_3",Map.of(),levels).contains("unobserved"));
        assertNotNull(RouteRequirements.craft("UNKNOWN_3",Map.of(),levels));
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
