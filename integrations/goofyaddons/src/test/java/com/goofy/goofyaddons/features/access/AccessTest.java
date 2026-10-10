package com.goofy.goofyaddons.features.access;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.features.generalflipper.BazaarAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessTest {
    @Test void essenceExclusionsRefreshWithDungeonLevelAndMutationControlsBlockAnalysisLocks() {
        var access=new BazaarAccess(dir.resolve("essence.json"));
        assertTrue(access.excluded(Map.of("catacombs",19)).contains("ESSENCE_DRAGON"));
        assertFalse(access.excluded(Map.of("catacombs",20)).contains("ESSENCE_DRAGON"));
        assertTrue(access.excluded(Map.of("catacombs",50)).containsAll(BazaarAccess.MUTATIONS));
        assertNotNull(BazaarAccess.unmet("You must first analyze this crop!"));
        assertNotNull(BazaarAccess.unmet("This mutation is not analyzed"));
        assertNull(BazaarAccess.unmet("Analyzed crops give Farming Fortune"));
        assertNotNull(ActionRequirements.blocked("Requires Catacombs Level XX",Map.of(),Map.of("catacombs",19),ActionRequirements.Action.BUY));
        assertNull(ActionRequirements.blocked("Requires Catacombs Level XX",Map.of(),Map.of("catacombs",20),ActionRequirements.Action.BUY));
        assertNotNull(ActionRequirements.blocked("Requires Master Mode The Catacombs Floor VII\nCompletion.",Map.of(),Map.of("catacombsfloor7completed",1),ActionRequirements.Action.CRAFT));
    }

    @TempDir Path dir;
    MenuSnapshot menu(int id,String title,boolean cursor,SlotView... controls) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var control:controls)slots.set(control.index(),control);
        return new MenuSnapshot(id,title,cursor,slots);
    }
    @Test void cookieDenialRequiresServerMessageWithoutAPlayerChatPrefix() {
        assertTrue(BazaarNpcAccess.cookieDenial("You must have the Cookie Buff active to use this command!"));
        assertTrue(BazaarNpcAccess.cookieDenial("You need a Booster Cookie to use this!"));
        assertFalse(BazaarNpcAccess.cookieDenial("[VIP] Someone: You need a Booster Cookie to use this!"));
        assertFalse(BazaarNpcAccess.cookieDenial("Your Cookie Buff expires in 2 days"));
    }
    @Test void decimalAndRomanLevelsAreParsedWithoutInventingUnknownLevels() {
        var facts=ActionRequirements.skills(menu(1,"Your Skills",true,
            SlotView.named(10,"§aEnchanting LX",List.of()),
            SlotView.named(11,"Mining",List.of("Progress to Level XXX: 2%"))));
        assertEquals(Map.of("enchanting",60,"mining",29),facts);
        assertEquals(-1,ActionRequirements.level("IIII"));
        assertTrue(ActionRequirements.skills(menu(1,"Bazaar",true,SlotView.named(10,"Enchanting LX",List.of()))).isEmpty());
    }
    @Test void conflictingOrInventorySkillsCannotProveALevel() {
        assertTrue(ActionRequirements.skills(menu(1,"Skills",true,
            SlotView.named(10,"Enchanting XX",List.of()),SlotView.named(11,"Enchanting XXI",List.of()))).isEmpty());
        var inventory=new SlotView(60,true,6,false,"Enchanting LX","Enchanting LX",List.of(),null,null,1,1);
        assertTrue(ActionRequirements.skills(menu(1,"Skills",true,inventory)).isEmpty());
    }
    @Test void actionRequirementUsesObservedLevelAndUnknownIsNotZero() {
        String control="Requires Enchanting Level XXX";
        assertNull(ActionRequirements.blocked(control,Map.of("enchanting",30),ActionRequirements.Action.COMBINE));
        assertTrue(ActionRequirements.blocked(control,Map.of("enchanting",29),ActionRequirements.Action.COMBINE).contains("observed level 29"));
        assertTrue(ActionRequirements.blocked(control,Map.of(),ActionRequirements.Action.COMBINE).contains("unobserved"));
        assertNotNull(ActionRequirements.blocked("You haven't unlocked this item!",Map.of("enchanting",60),ActionRequirements.Action.BUY));
    }
    @Test void actionControlsEnforceHotmAndSlayerRequirementsBeforeClicking() {
        String control="Requires Heart of the Mountain Tier VI\nRequires Wolf Slayer III";
        assertNull(ActionRequirements.blocked(control,Map.of(),Map.of("hotm",6,"wolfslayer",3),ActionRequirements.Action.FORGE));
        assertTrue(ActionRequirements.blocked(control,Map.of(),Map.of("hotm",5,"wolfslayer",3),ActionRequirements.Action.FORGE).contains("observed 5"));
        assertTrue(ActionRequirements.blocked(control,Map.of(),Map.of("hotm",6),ActionRequirements.Action.FORGE).contains("unobserved"));
    }
    @Test void learnedLocksPersistButClearWhenAccountLevelsUpAndMutationsStayExcluded() {
        var access=new BazaarAccess(dir.resolve("access.json"));
        access.deny("BOOK","BUY requires Enchanting 30; observed level 20");
        access.deny("UNKNOWN","BUY requires Mining 25; account skill level is unobserved");
        access.deny("LOCKED","You haven't unlocked this item!");
        access=new BazaarAccess(dir.resolve("access.json"));access.reevaluateSkills(Map.of("enchanting",29));
        assertTrue(access.excluded().contains("BOOK"));assertFalse(access.excluded().contains("UNKNOWN"));
        access.reevaluateSkills(Map.of("enchanting",30));
        assertFalse(access.excluded().contains("BOOK"));assertTrue(access.excluded().contains("LOCKED"));
        assertTrue(access.excluded().containsAll(BazaarAccess.MUTATIONS));
        assertFalse(new BazaarAccess(dir.resolve("access.json")).excluded().contains("BOOK"));
    }
    @Test void preflightMakesOneVisitThenClosesVerifiedSkillsMenu() {
        var check=new SkillPreflight();var actions=new RecordingActions();check.begin();
        check.tick(null,actions,1000);check.tick(null,actions,1200);
        check.tick(menu(1,"Skills",true,SlotView.named(10,"Enchanting XL",List.of())),actions,1500);
        check.tick(null,actions,2000);
        assertEquals(List.of("command:skills","close"),actions.performed());
        assertEquals(40,check.skills().get("enchanting"));assertFalse(check.pending());
    }
    @Test void preflightRetriesCommandsBoundedlyAndClosesOnlyItsOwnTimedOutMenu() {
        var check=new SkillPreflight();var actions=new RecordingActions();check.begin();
        for(long now=1000;now<=27000;now+=1000)check.tick(null,actions,now);
        assertEquals(3,actions.serverEffects().size());assertFalse(check.pending());assertTrue(check.skills().isEmpty());
        actions.clear();check.begin();check.tick(menu(1,"Skills",true),actions,30000);
        check.tick(menu(1,"Skills",true),actions,56000);assertTrue(actions.performed().contains("close"));
        actions.clear();check.begin();check.tick(menu(1,"Storage",true),actions,60000);
        check.tick(menu(1,"Storage",true),actions,86000);assertFalse(actions.performed().contains("close"));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void occupiedCursorAndCancellationCannotTriggerSkillsCommands() {
        var check=new SkillPreflight();var actions=new RecordingActions();check.begin();
        assertThrows(IllegalStateException.class,()->check.tick(menu(1,null,false),actions,1000));
        check.cancel();check.tick(null,actions,2000);assertTrue(actions.performed().isEmpty());
        check.clear();assertTrue(check.skills().isEmpty());
    }
    class Port implements BazaarNavigation.Port {
        MenuSnapshot view;boolean near=true,sign,walking,canWalk;
        List<String> effects=new ArrayList<>();
        public MenuSnapshot menu(){return view;}public boolean signOpen(){return sign;}
        public boolean nearNpc(){return near;}public boolean walking(){return walking;}
        public boolean walk(){effects.add("walk");walking=canWalk;return canWalk;}
        public void stopWalking(){walking=false;}public void interact(){effects.add("interact");}
        public void click(int slot){effects.add("click:"+slot);}
        public boolean writeSearch(String text){effects.add("sign:"+text);return true;}
    }
    @Test void npcSearchWaitsForInteractionSignAndNewContainerAcknowledgements() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("bz Wisdom",1000);
        nav.tick(port,1000,90);nav.tick(port,1200,90);assertEquals(List.of("interact"),port.effects);
        port.view=menu(2,"Bazaar",true,SlotView.named(45,"Search",List.of()));
        nav.tick(port,1500,90);nav.tick(port,2000,90);nav.tick(port,2100,90);
        assertEquals(List.of("interact","click:45"),port.effects);
        port.sign=true;nav.tick(port,2200,90);nav.tick(port,2300,90);
        assertEquals(List.of("interact","click:45","sign:Wisdom"),port.effects);assertTrue(nav.busy());
        port.sign=false;port.view=menu(3,"Bazaar search",true);nav.tick(port,2500,90);
        assertEquals(BazaarNavigation.State.DONE,nav.state());
    }
    @Test void npcOrdersRouteUsesOnlyOrdersControlAndAcceptsExistingOrdersPage() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("managebazaarorders",1000);
        port.view=menu(2,"Bazaar",true,SlotView.named(50,"Manage Orders",List.of()));
        nav.tick(port,1000,90);nav.tick(port,1500,90);assertEquals(List.of("click:50"),port.effects);
        port.view=menu(3,"Bazaar Orders",true);nav.tick(port,2000,90);
        assertEquals(BazaarNavigation.State.DONE,nav.state());
        nav.request("managebazaarorders",2500);nav.tick(port,2500,90);assertFalse(nav.busy());
    }
    @Test void missingPathfinderOrInterruptedWalkingFailsWithoutMenuClicks() {
        var nav=new BazaarNavigation();var port=new Port();port.near=false;nav.request("bz",1000);
        nav.tick(port,1000,90);assertEquals(BazaarNavigation.State.FAILED,nav.state());assertEquals(List.of("walk"),port.effects);
        port.canWalk=true;port.effects.clear();nav.request("bz",2000);nav.tick(port,2000,90);
        assertTrue(port.walking);nav.cancel(port);assertFalse(port.walking);assertFalse(nav.busy());
        nav.request("bz",3000);nav.tick(port,3000,90);port.walking=false;nav.tick(port,3500,90);
        assertEquals(BazaarNavigation.State.FAILED,nav.state());
    }
    @Test void reachingNpcStopsWalkingAndOpensIt() {
        var nav=new BazaarNavigation();var port=new Port();port.near=false;port.canWalk=true;
        nav.request("bz",1000);nav.tick(port,1000,90);port.near=true;nav.tick(port,1500,90);
        assertFalse(port.walking);assertEquals(List.of("walk","interact"),port.effects);
        port.view=menu(2,"Bazaar",true);nav.tick(port,2000,90);assertFalse(nav.busy());
    }
    @Test void repeatedRequestsDoNotExtendDeadlineOrMixSearches() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("bz Coal",1000);
        assertTrue(nav.request("bz Coal",14000));
        assertThrows(IllegalStateException.class,()->nav.request("bz Diamond",14000));
        nav.tick(port,17000,15);assertEquals(BazaarNavigation.State.FAILED,nav.state());assertTrue(port.effects.isEmpty());
        assertFalse(nav.request("anvil",18000));
        assertThrows(IllegalArgumentException.class,()->nav.request("bz hi\nthere",18000));
    }
    @Test void unrelatedMenuOccupiedCursorOrAmbiguousControlsNeverGetClicked() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("bz Coal",1000);
        port.view=menu(1,"Storage",true);nav.tick(port,1000,90);assertEquals(BazaarNavigation.State.FAILED,nav.state());
        nav.request("bz Coal",2000);port.view=menu(1,"Bazaar",false);nav.tick(port,2000,90);
        assertEquals(BazaarNavigation.State.FAILED,nav.state());
        nav.request("bz Coal",3000);port.view=menu(1,"Bazaar",true,
            SlotView.named(45,"Search",List.of()),SlotView.named(46,"Search",List.of()));
        nav.tick(port,3000,90);nav.tick(port,3500,90);assertTrue(port.effects.isEmpty());
    }
    @Test void ignoredSearchClickRetriesOnlyUnchangedReversibleControlAndStopsAfterThree() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("bz Coal",1000);
        port.view=menu(2,"Bazaar",true,SlotView.named(45,"Search",List.of()));
        nav.tick(port,1000,90);nav.tick(port,1500,90);
        for(long now=2000;now<=9000;now+=500)nav.tick(port,now,90);
        assertEquals(List.of("click:45","click:45","click:45"),port.effects);
        nav.tick(port,10000,90);assertEquals(BazaarNavigation.State.FAILED,nav.state());
        port.effects.clear();nav.request("bz Coal",11000);
        nav.tick(port,11000,90);nav.tick(port,11500,90);
        port.view=menu(2,"Bazaar",true,SlotView.named(45,"Confirm Order",List.of()));
        nav.tick(port,14000,90);assertEquals(List.of("click:45"),port.effects);
    }
    @Test void ignoredOrdersClickRetriesWithoutRepeatedTransactions() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("managebazaarorders",1000);
        port.view=menu(2,"Bazaar",true,SlotView.named(50,"Manage Orders",List.of()));
        nav.tick(port,1000,90);nav.tick(port,1500,90);nav.tick(port,3500,90);
        port.view=menu(3,"Your Bazaar Orders",true);nav.tick(port,4000,90);nav.tick(port,5000,90);
        assertEquals(List.of("click:50","click:50"),port.effects);assertFalse(nav.busy());
    }
    @Test void failedNpcInteractionIsBoundedAndNeverSendsRemoteCommands() {
        var nav=new BazaarNavigation();var port=new Port();nav.request("bz",1000);
        for(long now=1000;now<=9000;now+=1000)nav.tick(port,now,90);
        assertEquals(List.of("interact","interact","interact"),port.effects);
        assertEquals(BazaarNavigation.State.FAILED,nav.state());
    }
}
