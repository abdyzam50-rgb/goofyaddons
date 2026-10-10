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
        int clicks;boolean discard,preview,quickCraft,ignoreSplitOnce,oneAtATime;String title="Craft Item";
        final List<String> inputs=new ArrayList<>();
        Server(ProductionRecipe r,int input){this.r=r;for(int i=0;i<90;i++)slots[i]=stack(i,"",0);slots[54]=stack(54,"INPUT",input);slots[55]=stack(55,"SECOND",1);}
        MenuSnapshot menu(){
            boolean ready=true;int[] grid={10,11,12,19,20,21,28,29,30};
            for(int i=0;i<9;i++){var need=r.grid().get(i);var slot=slots[grid[i]];
                ready &= need==null?slot.empty():!slot.empty() && need.id().equals(slot.customId()) && slot.count()>=need.count() && slot.count()%need.count()==0;}
            slots[23]=ready || preview?stack(23,r.outputId(),r.outputCount()):stack(23,"",0);
            if(quickCraft)slots[16]=stack(16,"OUTPUT",1);
            return new MenuSnapshot(77,title,cursor.empty(),Arrays.asList(slots.clone()),cursor);
        }
        public void click(int slot,boolean shift){clicks++;inputs.add((shift?"shift:":"left:")+slot);if(discard)return;
            if(shift && slot==23){
                assertEquals(r.outputId(),slots[23].customId());int[] grid={10,11,12,19,20,21,28,29,30};
                int crafts=64;
                for(int i=0;i<9;i++)if(r.grid().get(i)!=null)crafts=Math.min(crafts,slots[grid[i]].count()/r.grid().get(i).count());
                if(oneAtATime)crafts=1;
                for(int i=0;i<9;i++)if(r.grid().get(i)!=null)slots[grid[i]]=stack(grid[i],r.grid().get(i).id(),slots[grid[i]].count()-r.grid().get(i).count()*crafts);
                int output=r.outputCount()*crafts;
                for(int i=56;i<90&&output>0;i++)if(slots[i].empty()||r.outputId().equals(slots[i].customId())) {
                    int added=Math.min(output,64-slots[i].count());slots[i]=stack(i,r.outputId(),slots[i].count()+added);output-=added;
                }
                assertEquals(0,output,"Simulated inventory has no output capacity");return;
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
    @Test void loadsAnEntireRodStackAndVerifiesAll128PowderInOneCollection() {
        var r=RecipeCatalog.instance().forOutput("BLAZE_POWDER").getFirst();var server=new Server(r,0);
        server.slots[54]=stack(54,"BLAZE_ROD",64);server.slots[55]=stack(55,"",0);
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,64,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(64,executor.completedBatches());assertEquals(64,server.slots[56].count());assertEquals(64,server.slots[57].count());
        assertEquals(3,server.clicks);assertEquals(List.of("left:54","left:10","shift:23"),server.inputs);
    }
    @Test void partialServerCraftsReuseTheLoadedGridWithoutReplacingInputs() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.oneAtATime=true;
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(8,executor.completedBatches());assertEquals(8,server.slots[56].count());
        assertEquals(4,server.inputs.stream().filter(x->x.startsWith("left:")).count());assertEquals(8,server.inputs.stream().filter(x->x.startsWith("shift:")).count());
    }
    @Test void bulkCraftNeverCountsAnOutputWithoutItsExactConsumedIngredients() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.discard=true;
        var executor=new CraftingExecutor();assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1000));
        server.slots[56]=stack(56,"OUTPUT",8);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1100));
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),2700));assertEquals(1,server.clicks);
    }
    @Test void unknownUnstackableOutputLimitsTheLoadedBatchToFreeInventorySpace() {
        var r=recipe(1);var server=new Server(r,64);server.slots[55]=stack(55,"SECOND",64);
        for(int i=57;i<90;i++)server.slots[i]=stack(i,"OTHER",64);
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,64,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(1,executor.completedBatches());
    }
    @Test void nonPowerOfTwoAmountsAreLoadedOnceUsingHalvesAndExactRemainders() {
        var base=recipe(1);var r=new ProductionRecipe(base.key(),base.kind(),"BLAZE_POWDER",1,base.ingredients(),base.grid(),0,0,base.requirement(),null);
        var server=new Server(r,64);server.slots[55]=stack(55,"SECOND",47);var executor=new CraftingExecutor();
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,47,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(47,executor.completedBatches());assertEquals(17,server.slots[54].count());
        assertTrue(server.clicks<30,"47 exact inputs should use a half-stack plus remainder, not 47 placements");
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
    @Test void normalBlazePowderConsumesOneRodAndProducesTwoWithoutAccountRequirements() {
        var r=RecipeCatalog.instance().forOutput("BLAZE_POWDER").getFirst();
        assertEquals(Map.of("BLAZE_ROD",1),r.ingredients());assertEquals(2,r.outputCount());assertTrue(r.requirement().isBlank());
        var server=new Server(r,0);server.slots[54]=stack(54,"BLAZE_ROD",64);server.slots[55]=stack(55,"",0);
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=100)
            result=executor.tick(r,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertTrue(server.cursor.empty());
        assertEquals("BLAZE_POWDER",server.slots[56].customId());assertEquals(2,server.slots[56].count());
        assertEquals(63,server.slots[54].count());assertEquals("shift:23",server.inputs.getLast());
    }
    @Test void addedBasicIntermediatesExecuteTheirExactCatalogYieldsWithoutAccountRequirements() {
        for(String id:List.of("PAPER","SUGAR","BOOK","BOWL","CHEST","GOLD_NUGGET","REDSTONE_TORCH_ON","EYE_OF_ENDER","GLASS_BOTTLE",
                "WORKBENCH","BUCKET","WOOD_PICKAXE","WOOD_AXE","WOOD_HOE","WOOD_SPADE","WOOD_SWORD")) {
            var r=RecipeCatalog.instance().forOutput(id).getFirst();var server=new Server(r,0);server.slots[55]=stack(55,"",0);
            int slot=54;for(var e:new TreeMap<>(r.ingredients()).entrySet())server.slots[slot]=stack(slot++,e.getKey(),e.getValue());
            var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
            for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=100)
                result=executor.tick(r,server.menu(),server,Map.of(),Map.of(),now);
            assertEquals(CraftingExecutor.Result.CRAFTED,result,id+": "+executor.failure());assertTrue(server.cursor.empty());
            assertEquals(id,server.slots[56].customId());assertEquals(r.outputCount(),server.slots[56].count());
        }
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
    @Test void enchantedEyeSplitsBlazeIntoFourSixteensAcrossTheMixedIngredientGrid() {
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_EYE_OF_ENDER").stream().filter(x->x.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var server=new Server(r,64);server.slots[54]=stack(54,"BLAZE_POWDER",64);server.slots[55]=stack(55,"ENCHANTED_ENDER_PEARL",16);
        var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;long now=1000;
        for(;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("enderpearl",6),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(List.of("left:54","left:11","right:11","left:19","right:11","left:21","right:19","left:29","left:55","left:20","shift:23"),server.inputs);
        assertTrue(now-1000<=1600,"Acknowledged crafting added unnecessary delays: "+(now-1000));
        assertEquals("ENCHANTED_EYE_OF_ENDER",server.slots[56].customId());assertTrue(server.cursor.empty());
    }
    @Test void singleSixteenCellSplitsTwiceAndReturnsTheOtherFortyEight() {
        var r=recipe(16);var server=new Server(r,64);var executor=new CraftingExecutor();CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(48,server.slots[54].count());
        assertEquals(2,server.inputs.stream().filter("right:10"::equals).count());assertEquals(9,server.clicks);assertTrue(server.cursor.empty());
    }
    @Test void slowdownStopsNewClicksAndRetriesBeforeTheCooldownEnds() {
        var r=recipe(32);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor();
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);executor.slowdown(2900);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3100);assertEquals(1,server.clicks);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3900);assertEquals(2,server.clicks);
    }
    @Test void oversizedCellsNotPlacedForASplitCannotBeAdopted() {
        var r=recipe(16);var server=new Server(r,64);var executor=new CraftingExecutor();
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        server.slots[10]=stack(10,"INPUT",32);server.cursor=stack(-1,"INPUT",32);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1200));
        assertEquals(1,server.clicks);
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
