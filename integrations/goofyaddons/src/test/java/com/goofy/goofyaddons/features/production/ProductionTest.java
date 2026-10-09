package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTest {
    @TempDir Path dir;
    ProductionRecipe recipe(int amount) {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("INPUT",amount));grid.set(4,new ProductionRecipe.Ingredient("SECOND",1));
        return new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",amount,"SECOND",1),grid,0,0,"Collection II",null);
    }
    SlotView stack(int index,String id,int count){return count==0?SlotView.empty(index,index>=54,index>=54?index-54:index):
            new SlotView(index,index>=54,index>=54?index-54:index,false,id,id,List.of(),id,null,count,64);}
    class Server implements GameActions {
        final SlotView[] slots=new SlotView[90];SlotView cursor=SlotView.empty(-1,false,-1);final ProductionRecipe r;
        int clicks;boolean discard,preview,quickCraft,ignoreSplitOnce;String title="Craft Item";
        final List<String> inputs=new ArrayList<>();
        Server(ProductionRecipe r,int input){this.r=r;for(int i=0;i<90;i++)slots[i]=stack(i,"",0);slots[54]=stack(54,"INPUT",input);slots[55]=stack(55,"SECOND",1);}
        MenuSnapshot menu(){
            boolean ready=true;int[] grid={10,11,12,19,20,21,28,29,30};
            for(int i=0;i<9;i++){var need=r.grid().get(i);var slot=slots[grid[i]];
                ready &= need==null?slot.empty():!slot.empty() && need.id().equals(slot.customId()) && need.count()==slot.count();}
            slots[23]=ready || preview?stack(23,"OUTPUT",1):stack(23,"",0);
            if(quickCraft)slots[16]=stack(16,"OUTPUT",1);
            return new MenuSnapshot(77,title,cursor.empty(),Arrays.asList(slots.clone()),cursor);
        }
        public void click(int slot,boolean shift){clicks++;inputs.add((shift?"shift:":"left:")+slot);if(discard)return;
            if(shift && slot==23){
                assertEquals("OUTPUT",slots[23].customId());int[] grid={10,11,12,19,20,21,28,29,30};
                for(int i:grid)slots[i]=stack(i,"",0);slots[56]=stack(56,"OUTPUT",1);return;
            }
            if(cursor.empty()){var s=slots[slot];cursor=stack(-1,s.customId(),s.count());slots[slot]=stack(slot,"",0);}
            else {var s=slots[slot];assertTrue(s.empty() || s.customId().equals(cursor.customId()));
                slots[slot]=stack(slot,cursor.customId(),s.count()+cursor.count());cursor=stack(-1,"",0);}
        }
        public void rightClick(int slot){clicks++;inputs.add("right:"+slot);if(discard)return;
            if(ignoreSplitOnce && slot==10 && cursor.empty()){ignoreSplitOnce=false;return;}
            if(cursor.empty()){var s=slots[slot];int half=(s.count()+1)/2;cursor=stack(-1,s.customId(),half);slots[slot]=stack(slot,s.customId(),s.count()-half);}
            else {var s=slots[slot];assertTrue(s.empty() || s.customId().equals(cursor.customId()));slots[slot]=stack(slot,cursor.customId(),s.count()+1);cursor=stack(-1,cursor.customId(),cursor.count()-1);}
        }
        public void closeMenu(){}public void command(String text){}public void message(String text){}public boolean writeSign(String text){return false;}
    }
    @Test void hypixelsQuickCraftSuggestionIsNotMistakenForTheGridResult() {
        var r=recipe(32);var server=new Server(r,64);server.quickCraft=true;var executor=new CraftingExecutor();
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals("OUTPUT",server.slots[56].customId());
    }
    @Test void craftingSelectionCannotConsumeRetainedPositionsAndReservesWholeBatchRequirements() {
        var r=recipe(5);var catalog=new RecipeCatalog(List.of(r),Map.of());
        assertTrue(CraftingFeature.selectRecipe(catalog,"OUTPUT",2,Map.of("INPUT",10,"SECOND",2),Set.of()).isPresent());
        assertTrue(CraftingFeature.selectRecipe(catalog,"OUTPUT",2,Map.of("INPUT",9,"SECOND",2),Set.of()).isEmpty());
        assertTrue(CraftingFeature.selectRecipe(catalog,"OUTPUT",1,Map.of("INPUT",5,"SECOND",1),Set.of("INPUT")).isEmpty());
        assertTrue(CraftingFeature.selectRecipe(catalog,"OUTPUT",1,Map.of("INPUT",5,"SECOND",1),Set.of("OUTPUT")).isEmpty());
    }
    @Test void verifiedPartialCraftCountsSurviveRestartWithoutInventingProfit()throws Exception {
        var jobs=new ProductionJobs(dir.resolve("counts.json"));
        var job=new ProductionJobs.Job("job",recipe(5).key(),"account",ProductionJobs.State.PROCESSING,2,-1,0,0,null,null,null,null);
        jobs.put(job.completedBatch());var restored=new ProductionJobs(dir.resolve("counts.json")).find("job").orElseThrow();
        assertEquals(1,restored.completedBatches());assertEquals(ProductionJobs.State.PROCESSING,restored.state());assertNull(restored.costBasis());
        jobs.put(restored.completedBatch());assertEquals(ProductionJobs.State.OUTPUT_READY,jobs.find("job").orElseThrow().state());
    }
    @Test void generalCatalogLoadsWithValidIngredientTotalsAndVariants() {
        var catalog=RecipeCatalog.instance();
        assertTrue(catalog.recipes().stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).count()>2000);
        assertFalse(catalog.forOutput("REFINED_MITHRIL").isEmpty());
        assertTrue(catalog.forOutput("ASPECT_OF_THE_VOID").stream().allMatch(r->r.requirement().contains("Enderman Slayer 6")));
        assertTrue(catalog.forOutput("BLUE_WHALE;4").stream().anyMatch(r->r.inputPet().equals("BLUE_WHALE;3")));
    }
    @Test void fullCraftWaitsForEachObservationAndConservesSurplusIngredients() {
        var r=recipe(5);var server=new Server(r,64);var executor=new CraftingExecutor();
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(59,server.slots[54].count());assertEquals("OUTPUT",server.slots[56].customId());assertTrue(server.cursor.empty());
    }
    @Test void missingCollectionBlocksBeforeTouchingIngredients() {
        for(var unlocks:List.of(Map.<String,Integer>of(),Map.of("collection",1))) {
            var r=recipe(5);var server=new Server(r,64);var executor=new CraftingExecutor();
            assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),unlocks,1000));
            assertEquals(0,server.clicks);assertEquals(64,server.slots[54].count());assertTrue(server.cursor.empty());
        }
    }
    @Test void fullStackIsPickedUpThenSplitInsideGridAndOutputIsShiftClicked() {
        var r=recipe(32);var server=new Server(r,64);var executor=new CraftingExecutor();
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertTrue(server.clicks<=7);assertEquals(32,server.slots[54].count());
        assertEquals(List.of("left:54","left:10","right:10","left:54"),server.inputs.subList(0,4));
        assertEquals("shift:23",server.inputs.getLast());assertFalse(server.inputs.contains("right:54"));
    }
    @Test void gridHalvesAreReusedForFiveThirtyTwoItemCellsWithExactRemainder() {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        for(int i=0;i<5;i++)grid.set(i,new ProductionRecipe.Ingredient("INPUT",32));
        var r=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",160),grid,0,0,"Collection II",null);
        var server=new Server(r,64);server.slots[57]=stack(57,"INPUT",64);server.slots[58]=stack(58,"INPUT",64);
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(3,server.inputs.stream().filter(s->s.startsWith("right:")).count());
        assertTrue(server.inputs.stream().noneMatch(s->s.equals("right:54") || s.equals("right:57") || s.equals("right:58")));
        assertEquals(32,server.slots[58].count());assertTrue(server.cursor.empty());assertEquals("OUTPUT",server.slots[56].customId());
    }
    @Test void ignoredGridSplitRetriesOnlyWhileTheSameFullStackRemains() {
        var r=recipe(32);var server=new Server(r,64);server.ignoreSplitOnce=true;var executor=new CraftingExecutor();
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(32,server.slots[54].count());
        assertEquals(2,server.inputs.stream().filter("right:10"::equals).count());assertTrue(server.cursor.empty());
    }
    @Test void ignoredPickupRetriesBoundedlyWithoutRepeatedFastClicks() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor();
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        for(long now=1100;now<3000;now+=100)executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(1,server.clicks);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3000);executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),5000);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),7000));assertEquals(3,server.clicks);
    }
    @Test void previewChangesCannotAcknowledgeAnUnchangedCursorAndInventory() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor();
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);server.preview=true;
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1400);executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1600);assertEquals(1,server.clicks);
    }
    @Test void foreignGridCursorAndMissingIngredientsPreventAllClicks() {
        var r=recipe(5);var server=new Server(r,4);var executor=new CraftingExecutor();
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
        server=new Server(r,64);server.slots[10]=stack(10,"FOREIGN",1);executor.reset();
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
        server=new Server(r,64);server.cursor=stack(-1,"FOREIGN",1);executor.reset();
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
    }
    @Test void briefPacketMismatchWaitsButPersistentMissingItemsBlocks() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor();
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);server.slots[54]=stack(54,"INPUT",63);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1200));
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),2800));assertEquals(1,server.clicks);
    }
    @Test void journalPreservesTimersAndAccountAndMarksInterruptedActionsForReview()throws Exception {
        var path=dir.resolve("jobs.json");var jobs=new ProductionJobs(path);
        var job=new ProductionJobs.Job("job","forge:REFINED_MITHRIL:0","account-a",ProductionJobs.State.WAITING,1,2,1000,9000,123.0,null,null,null);
        jobs.put(job);jobs.put(new ProductionJobs.Job("pending","craft:OUTPUT:0","account-a",ProductionJobs.State.PROCESSING,1,-1,0,0,null,null,null,null));
        jobs=new ProductionJobs(path);jobs.recoverUncertain("account-b");assertEquals(ProductionJobs.State.PROCESSING,jobs.find("pending").orElseThrow().state());
        jobs.recoverUncertain("account-a");assertEquals(ProductionJobs.State.REVIEW,jobs.find("pending").orElseThrow().state());
        assertEquals(job,jobs.find("job").orElseThrow());assertEquals(job,new ProductionJobs(path).find("job").orElseThrow());
    }
    @Test void malformedJournalIsPreservedAndFailedWritesDoNotAdvanceMemory()throws Exception {
        var path=dir.resolve("jobs.json");Files.writeString(path,"broken");assertThrows(java.io.IOException.class,()->new ProductionJobs(path));assertEquals("broken",Files.readString(path));
        var directory=dir.resolve("target");Files.createDirectory(directory);var jobs=new ProductionJobs(dir.resolve("new.json"));
        Files.createDirectory(dir.resolve("new.json"));
        var job=new ProductionJobs.Job("job","recipe","account",ProductionJobs.State.PLANNED,1,-1,0,0,null,null,null,null);
        assertThrows(java.io.IOException.class,()->jobs.put(job));assertTrue(jobs.all().isEmpty());
    }
    @Test void petIdentityRequiresUuidRarityXpHeldItemAndSkin() {
        var pet=new ItemMetadata("uuid","BLUE_WHALE","EPIC",1200.0,null,null,0,null);
        assertEquals("BLUE_WHALE;3",pet.petVariant());assertTrue(pet.samePet(pet));
        assertFalse(pet.samePet(new ItemMetadata("other","BLUE_WHALE","EPIC",1200.0,null,null,0,null)));
        assertFalse(pet.samePet(new ItemMetadata("uuid","BLUE_WHALE","LEGENDARY",1200.0,null,null,0,null)));
        assertFalse(pet.samePet(new ItemMetadata("uuid","BLUE_WHALE","EPIC",1200.0,"PET_ITEM_EXP_SHARE",null,0,null)));
        assertFalse(ItemMetadata.EMPTY.samePet(ItemMetadata.EMPTY));
    }
}
