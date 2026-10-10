package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactorClearanceTest {
    static MenuSnapshot captured(String name)throws Exception {
        try(var stream=Objects.requireNonNull(CompactorClearanceTest.class.getResourceAsStream("/fixtures/"+name));
                var reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();var slots=new ArrayList<SlotView>();
            for(var value:root.getAsJsonArray("slots")) {
                var s=value.getAsJsonObject();var lore=new ArrayList<String>();s.getAsJsonArray("lore").forEach(l->lore.add(l.getAsString()));
                slots.add(new SlotView(s.get("slot").getAsInt(),false,s.get("slot").getAsInt(),false,
                    s.get("name").getAsString(),s.get("name").getAsString(),lore,s.has("productId")?s.get("productId").getAsString():null,null,1,1));
            }
            return new MenuSnapshot(1,root.get("title").getAsString(),true,slots);
        }
    }
    static MenuSnapshot menu(String title,boolean active,Map<Integer,String> recipes,Map<Integer,String> saved) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<72;i++)slots.add(SlotView.empty(i,i>=36,i>=63?i-63:i-27));
        for(int i=0;i<12;i++) {
            int index=i<7?10+i:20+i-7;String id=recipes.get(i);
            slots.set(index,new SlotView(index,false,index,false,"Auto-Craft Slot #"+(i+1),"Auto-Craft Slot #"+(i+1),
                id==null?List.of("Click on an item in your inventory to","add it to this Personal Compactor","filter."):
                List.of("Item: "+id,"Click to remove item!"),id,null,1,1));
        }
        slots.set(31,SlotView.named(31,"Compactor Currently "+(active?"ON":"OFF")+"!",List.of("Click to toggle!")));
        slots.set(63,PersonalCompactorsTest.device(63,true,7000,active,saved));
        return new MenuSnapshot(1,title,true,slots);
    }
    static final class Ports implements CompactorClearance.Ports {
        final List<String> intents=new ArrayList<>();int opens;boolean writable=true;
        public boolean open(SlotView device){opens++;return true;}
        public void intent(String reason)throws Exception{if(!writable)throw new java.io.IOException();intents.add(reason);}
    }
    @Test void capturesVerifyAllTwelveControlsAndTheTwoEmptyFilters()throws Exception {
        var full=CompactorClearance.controls(captured("compactor-7000-full.json"));
        var empty=CompactorClearance.controls(captured("compactor-7000-empty.json"));
        assertNotNull(full);assertNotNull(empty);assertEquals(12,full.recipes().size());assertEquals(10,empty.recipes().size());
        assertEquals(23,empty.slots().get(10));assertEquals(24,empty.slots().get(11));assertTrue(empty.active());
    }
    @Test void removesEveryFilterIncludingUnrelatedOnesAndVerifiesSavedConfiguration() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();
        var original=new TreeMap<Integer,String>();for(int i=0;i<12;i++)original.put(i,"ITEM_"+i);
        var current=new TreeMap<>(original);
        assertTrue(clearance.needsMenu(menu("Personal Compactor 7000",true,current,original)));
        for(int i=0;i<12;i++) {
            assertEquals(ProductionLoop.Step.PENDING,clearance.tick(menu("Personal Compactor 7000",true,current,original),actions,ports,true,i*600).step());
            assertEquals(i+1,ports.intents.size());current.remove(i);
        }
        clearance.tick(menu("Personal Compactor 7000",true,current,original),actions,ports,true,7200);
        assertEquals("close",actions.performed().getLast());assertEquals(12,actions.serverEffects().size());
        assertEquals(ProductionLoop.Step.PENDING,clearance.tick(menu(null,true,current,original),actions,ports,true,7800).step(),"An empty GUI is not enough; item data must confirm it");
        assertEquals(ProductionLoop.Step.DONE,clearance.tick(menu(null,true,current,current),actions,ports,true,7900).step());
        assertFalse(clearance.needsMenu(menu(null,true,current,current)));assertEquals(0,ports.opens);
        assertFalse(actions.performed().contains("click:31"),"The activation toggle is preserved");
    }
    @Test void disabledCompactorsAreAlsoClearedAndNoUnacknowledgedClickIsRepeated() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();var recipes=Map.of(4,"UNRELATED");
        clearance.tick(menu("Personal Compactor 7000",false,recipes,recipes),actions,ports,true,0);
        clearance.tick(menu("Personal Compactor 7000",false,recipes,recipes),actions,ports,true,1000);
        assertEquals(List.of("click:14"),actions.performed());
        assertEquals(ProductionLoop.Step.UNCERTAIN,clearance.tick(menu("Personal Compactor 7000",false,recipes,recipes),actions,ports,true,9000).step());
        assertEquals(List.of("click:14"),actions.performed());
    }
    @Test void noClickWithoutOwnershipOrDurableIntent() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();var menu=menu("Personal Compactor 7000",true,Map.of(0,"INPUT"),Map.of(0,"INPUT"));
        clearance.tick(menu,actions,ports,false,0);assertTrue(actions.performed().isEmpty());
        ports.writable=false;assertEquals(ProductionLoop.Step.BLOCKED,clearance.tick(menu,actions,ports,true,100).step());
        assertTrue(actions.performed().isEmpty());
    }
    @Test void missingControlOrChangedUnrelatedFilterDoesNotAuthorizeAnotherClick() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();var recipes=Map.of(0,"FIRST",1,"SECOND");
        clearance.tick(menu("Personal Compactor 7000",true,recipes,recipes),actions,ports,true,0);
        assertEquals(ProductionLoop.Step.UNCERTAIN,clearance.tick(menu("Personal Compactor 7000",true,Map.of(1,"CHANGED"),recipes),actions,ports,true,9000).step());
        assertEquals(List.of("click:10"),actions.performed());
        var complete=menu("Personal Compactor 7000",true,recipes,recipes);var slots=new ArrayList<>(complete.slots());slots.set(24,SlotView.empty(24,false,24));
        assertNull(CompactorClearance.controls(new MenuSnapshot(1,complete.title(),true,slots)));
    }
    @Test void waitsForTheOpenedMenuAndBlocksInventoryChanges() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();var recipes=Map.of(0,"FIRST");
        clearance.tick(menu(null,true,recipes,recipes),actions,ports,true,0);assertEquals(1,ports.opens);
        clearance.tick(menu(null,true,recipes,recipes),actions,ports,true,1000);assertEquals(1,ports.opens);assertTrue(actions.performed().isEmpty());
        var open=menu("Personal Compactor 7000",true,recipes,recipes);var slots=new ArrayList<>(open.slots());
        slots.set(36,new SlotView(36,true,9,false,"Input","Input",List.of(),"INPUT",null,64,64));
        assertEquals(ProductionLoop.Step.UNCERTAIN,clearance.tick(new MenuSnapshot(1,open.title(),true,slots),actions,ports,true,2000).step());
        assertTrue(actions.performed().isEmpty());
    }
    @Test void emptyDeviceSkipsNavigationAndOccupiedCursorBlocksRemoval() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();
        assertEquals(ProductionLoop.Step.DONE,clearance.tick(menu(null,true,Map.of(),Map.of()),actions,ports,false,0).step());
        var full=menu("Personal Compactor 7000",true,Map.of(0,"INPUT"),Map.of(0,"INPUT"));
        assertEquals(ProductionLoop.Step.BLOCKED,clearance.tick(new MenuSnapshot(1,full.title(),false,full.slots()),actions,ports,true,1).step());
        assertTrue(actions.performed().isEmpty());
    }
    @Test void clearsASecondDeviceBeforeReturningDone() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();
        var first=Map.of(0,"FIRST");var second=Map.of(1,"SECOND");
        java.util.function.Function<MenuSnapshot,MenuSnapshot> withSecond=m->{
            var slots=new ArrayList<>(m.slots());slots.set(64,new SlotView(64,true,1,false,"Compactor","Compactor",List.of(),"PERSONAL_COMPACTOR_7000",null,1,1,
                new ItemMetadata("second",null,null,null,null,null,null,"minecraft:dropper",new CompactorData(7000,true,second))));
            return new MenuSnapshot(m.containerId(),m.title(),true,slots);
        };
        clearance.tick(withSecond.apply(menu("Personal Compactor 7000",true,first,first)),actions,ports,true,0);
        clearance.tick(withSecond.apply(menu("Personal Compactor 7000",true,Map.of(),first)),actions,ports,true,600);
        var normal=withSecond.apply(menu(null,true,Map.of(),Map.of()));
        assertEquals(ProductionLoop.Step.PENDING,clearance.tick(normal,actions,ports,true,1200).step());assertEquals(1,ports.opens);
        var next=withSecond.apply(menu("Personal Compactor 7000",true,second,Map.of()));
        clearance.tick(next,actions,ports,true,1800);assertEquals(List.of("click:10","close","click:11"),actions.performed());
    }
    @Test void alreadyEmptyConfigurationMenuIsClosedBeforeCrafting() {
        var clearance=new CompactorClearance();var actions=new RecordingActions();var ports=new Ports();
        var open=menu("Personal Compactor 7000",true,Map.of(),Map.of());assertTrue(clearance.needsMenu(open));
        clearance.tick(open,actions,ports,false,0);assertTrue(actions.performed().isEmpty());
        clearance.tick(open,actions,ports,true,1);assertEquals(List.of("close"),actions.performed());
        assertEquals(ProductionLoop.Step.DONE,clearance.tick(menu(null,true,Map.of(),Map.of()),actions,ports,false,2).step());
    }
}
