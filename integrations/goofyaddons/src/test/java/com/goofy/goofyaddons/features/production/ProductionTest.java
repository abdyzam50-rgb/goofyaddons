package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTest {
    @TempDir Path dir;
    @Test void defaultCraftExecutorHonorsTheSharedDelayAfterServerAcknowledgement() {
        var old=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE;
        try {
            var cfg=new com.goofy.goofyaddons.config.GoofyConfig();cfg.minActionDelay=1000;cfg.maxActionDelay=1001;
            com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=cfg;
            var r=recipe(2);var server=new Server(r,2);var executor=new CraftingExecutor();
            executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);assertEquals(1,server.clicks);
            executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1001);
            executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1999);assertEquals(1,server.clicks);
            executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),2000);assertEquals(2,server.clicks);
        }finally{com.goofy.goofyaddons.config.GoofyConfig.INSTANCE=old;}
    }
    ProductionRecipe recipe(int amount) {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("INPUT",amount));grid.set(4,new ProductionRecipe.Ingredient("SECOND",1));
        return new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",amount,"SECOND",1),grid,0,0,"Collection II",null);
    }
    SlotView stack(int index,String id,int count){
        if(count==0)return SlotView.empty(index,index>=54,index>=54?index-54:index);
        if(id.startsWith("ENCHANTMENT_")) {
            int split=id.lastIndexOf('_');
            return new SlotView(index,index>=54,index>=54?index-54:index,false,"Enchanted Book","Enchanted Book",List.of(),
                "ENCHANTED_BOOK",Map.of(id.substring(12,split).toLowerCase(Locale.ROOT),Integer.parseInt(id.substring(split+1))),count,1);
        }
        return new SlotView(index,index>=54,index>=54?index-54:index,false,id,id,List.of(),id,null,count,64);
    }
    SlotView reindex(SlotView item,int index,int count) {
        if(count==0)return stack(index,"",0);
        return new SlotView(index,index>=54,index>=54?index-54:index,false,item.customName(),item.hoverName(),
            item.loreLines(),item.customId(),item.enchantments(),count,item.maxStackSize(),item.metadata());
    }
    SlotView taggedStack(int index,String id,int count,String uuid,String stackIdentity) {
        var item=stack(index,id,count);
        return new SlotView(index,item.inPlayerInventory(),item.containerSlot(),false,item.customName(),item.hoverName(),
            item.loreLines(),item.customId(),item.enchantments(),count,64,
            new ItemMetadata(uuid,null,null,null,null,null,null,"minecraft:furnace",null,stackIdentity));
    }
    class Server implements GameActions {
        final SlotView[] slots=new SlotView[90];SlotView cursor=SlotView.empty(-1,false,-1);ProductionRecipe r;
        int quickAvailable;boolean wrongQuickRecipe;Map<String,Integer> resultEnchantments;
        int clicks,swaps;boolean discard,preview,quickCraft,ignoreSplitOnce,oneAtATime;String title="Craft Item",forceSwapProduct,quickPreviewProduct="OUTPUT";
        final List<String> inputs=new ArrayList<>();
        final List<SlotView> consumedIngredients=new ArrayList<>();
        Server(ProductionRecipe r,int input){this.r=r;for(int i=0;i<90;i++)slots[i]=stack(i,"",0);slots[54]=stack(54,"INPUT",input);slots[55]=stack(55,"SECOND",1);}
        MenuSnapshot menu(){
            boolean ready=true;int[] grid={10,11,12,19,20,21,28,29,30};
            for(int i=0;i<9;i++){var need=r.grid().get(i);var slot=slots[grid[i]];
                ready &= need==null?slot.empty():!slot.empty() && need.id().equals(slot.bazaarProductId()) && slot.count()>=need.count() && slot.count()%need.count()==0;}
            slots[23]=ready || preview?stack(23,r.outputId(),r.outputCount()):stack(23,"",0);
            if(resultEnchantments!=null && !slots[23].empty()) {
                var output=slots[23];slots[23]=new SlotView(23,false,23,false,"Enchanted Book","Enchanted Book",List.of(),
                    "ENCHANTED_BOOK",resultEnchantments,output.count(),1);
            }
            if(quickCraft)slots[16]=stack(16,quickPreviewProduct,1);
            if(quickAvailable>0) {
                var lore=new ArrayList<String>(List.of("Craftable: "+quickAvailable+"x","Ingredients:"));
                r.ingredients().forEach((id,qty)->lore.add(qty+"x "+(wrongQuickRecipe?"Wrong ingredient":RecipeCatalog.instance().name(id))));
                lore.add("");lore.add("Click to craft!");
                var output=stack(16,r.outputId(),r.outputCount());
                slots[16]=new SlotView(16,false,16,false,output.customName(),output.hoverName(),lore,output.customId(),output.enchantments(),r.outputCount(),output.maxStackSize());
            } else if(!quickCraft)slots[16]=stack(16,"",0);
            return new MenuSnapshot(77,title,cursor.empty(),Arrays.asList(slots.clone()),cursor);
        }
        public void click(int slot,boolean shift){clicks++;inputs.add((shift?"shift:":"left:")+slot);if(discard)return;
            if(shift && slot==16 && quickAvailable>0) {
                int crafts=oneAtATime?1:quickAvailable;
                for(var need:r.ingredients().entrySet()) {
                    int remaining=need.getValue()*crafts;
                    for(int i=54;i<90&&remaining>0;i++)if(need.getKey().equals(slots[i].bazaarProductId())) {
                        int take=Math.min(remaining,slots[i].count());slots[i]=reindex(slots[i],i,slots[i].count()-take);remaining-=take;
                    }
                    assertEquals(0,remaining);
                }
                int output=r.outputCount()*crafts;
                for(int i=56;i<90&&output>0;i++)if(slots[i].empty()||r.outputId().equals(slots[i].bazaarProductId())) {
                    int added=Math.min(output,(r.outputId().startsWith("ENCHANTMENT_")?1:64)-slots[i].count());slots[i]=stack(i,r.outputId(),slots[i].count()+added);output-=added;
                }
                assertEquals(0,output);quickAvailable-=crafts;return;
            }
            if(shift && slot==23){
                assertEquals(r.outputId(),slots[23].bazaarProductId());int[] grid={10,11,12,19,20,21,28,29,30};
                int crafts=64;
                for(int i=0;i<9;i++)if(r.grid().get(i)!=null)crafts=Math.min(crafts,slots[grid[i]].count()/r.grid().get(i).count());
                if(oneAtATime)crafts=1;
                for(int i=0;i<9;i++)if(r.grid().get(i)!=null) {
                    consumedIngredients.add(reindex(slots[grid[i]],grid[i],r.grid().get(i).count()*crafts));
                    slots[grid[i]]=reindex(slots[grid[i]],grid[i],slots[grid[i]].count()-r.grid().get(i).count()*crafts);
                }
                int output=r.outputCount()*crafts;
                for(int i=56;i<90&&output>0;i++)if(slots[i].empty()||r.outputId().equals(slots[i].bazaarProductId())) {
                    int added=Math.min(output,(r.outputId().startsWith("ENCHANTMENT_")?1:64)-slots[i].count());slots[i]=stack(i,r.outputId(),slots[i].count()+added);output-=added;
                }
                assertEquals(0,output,"Simulated inventory has no output capacity");return;
            }
            if(cursor.empty()){var s=slots[slot];cursor=reindex(s,-1,s.count());slots[slot]=stack(slot,"",0);}
            else {var s=slots[slot];
                if(!s.empty() && (!s.canStackWith(cursor) || Objects.equals(forceSwapProduct,cursor.bazaarProductId()))) {
                    slots[slot]=reindex(cursor,slot,cursor.count());cursor=reindex(s,-1,s.count());swaps++;return;
                }
                int added=Math.min(cursor.count(),(s.empty()?cursor.maxStackSize():s.maxStackSize())-s.count());
                slots[slot]=reindex(s.empty()?cursor:s,slot,s.count()+added);cursor=reindex(cursor,-1,cursor.count()-added);}
        }
        public void rightClick(int slot){clicks++;inputs.add("right:"+slot);if(discard)return;
            if(ignoreSplitOnce && slot==10 && cursor.empty()){ignoreSplitOnce=false;return;}
            if(cursor.empty()){var s=slots[slot];int half=(s.count()+1)/2;cursor=reindex(s,-1,half);slots[slot]=reindex(s,slot,s.count()-half);}
            else {var s=slots[slot];
                if(!s.empty() && !s.canStackWith(cursor)){slots[slot]=reindex(cursor,slot,cursor.count());cursor=reindex(s,-1,s.count());swaps++;return;}
                if(s.count()>=(s.empty()?cursor.maxStackSize():s.maxStackSize()))return;
                slots[slot]=reindex(s.empty()?cursor:s,slot,s.count()+1);cursor=reindex(cursor,-1,cursor.count()-1);}
        }
        public void closeMenu(){}public void command(String text){}public void message(String text){}public boolean writeSign(String text){return false;}
    }
    private Server baitServer(int pairs) {
        var r=RecipeCatalog.instance().forOutput("HOTSPOT_BAIT").getFirst();var server=new Server(r,0);
        int slot=54;for(var need:r.ingredients().entrySet())server.slots[slot]=stack(slot++,need.getKey(),pairs);
        // Export: seven occupied inventory slots, 29 free, no output held.
        for(int i=56;i<61;i++)server.slots[i]=stack(i,"OTHER_"+i,1);
        return server;
    }
    @Test void loadingBaitQuickSlotWaitsWithoutClickingThenVerifiesAllSixteenBatches() {
        var server=baitServer(16);var r=server.r;var executor=new CraftingExecutor(()->50);
        assertEquals(29,server.menu().emptyInventorySlots());
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1000));
        assertTrue(executor.outputCapacityWaiting());assertEquals(0,server.clicks);assertFalse(executor.busy());
        // The dynamic controls arrive after the first Craft Item screen update.
        server.quickAvailable=16;
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1200));
        assertFalse(executor.outputCapacityWaiting());assertEquals(List.of("shift:16"),server.inputs);
        assertEquals(CraftingExecutor.Result.CRAFTED,executor.tick(r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1300),executor.failure());
        assertEquals(16,executor.completedBatches());assertEquals(512,server.menu().countInInventory("HOTSPOT_BAIT"));
    }
    @Test void readableBaitStackCapacityDoesNotAuthorizeAnExcessiveQuickCraft() {
        var server=baitServer(32);server.quickAvailable=32;var executor=new CraftingExecutor(()->50);
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(server.r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(16,executor.completedBatches());
        assertFalse(server.inputs.contains("shift:16"));assertTrue(server.inputs.contains("shift:23"));
        assertEquals(512,server.menu().countInInventory("HOTSPOT_BAIT"));
        for(var input:server.r.ingredients().keySet())assertEquals(16,server.menu().countInInventory(input));
    }
    @Test void unreadableQuickRecipeStillUsesExactOutputStackFactForSafeGridCraft() {
        var server=baitServer(16);server.quickAvailable=16;server.wrongQuickRecipe=true;var executor=new CraftingExecutor(()->50);
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(server.r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(16,executor.completedBatches());
        assertFalse(server.inputs.contains("shift:16"));assertTrue(server.inputs.contains("shift:23"));
    }
    @Test void missingExactOutputMetadataHasABoundedNoClickWaitDespiteWrongStackableIcon() {
        var server=baitServer(16);server.quickCraft=true;var executor=new CraftingExecutor(()->50);
        assertEquals("OUTPUT",server.menu().slot(16).customId(),"Lookalike stackable icon is another product");
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(server.r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1000));
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(server.r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),8999));
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(server.r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),9000));
        assertTrue(executor.failure().contains("stack size is still unreadable for HOTSPOT_BAIT"));assertEquals(0,server.clicks);
        assertEquals(16,server.menu().countInInventory("SCORCHED_CRAB_STICK"));assertTrue(server.cursor.empty());
        executor.reset();assertFalse(executor.outputCapacityWaiting());
    }
    @Test void observedUnstackableOutputCannotUseAnotherProductsSixtyFourStackLimit() {
        var server=baitServer(16);var slots=new ArrayList<>(server.menu().slots());
        slots.set(16,new SlotView(16,false,16,false,"Hotspot Bait","Hotspot Bait",List.of(),"HOTSPOT_BAIT",null,1,1));
        slots.set(25,stack(25,"UNRELATED",64));var menu=new MenuSnapshot(77,"Craft Item",true,slots);
        var executor=new CraftingExecutor(()->50);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(server.r,16,menu,server,Map.of(),Map.of("pufferfish",8),1000));
        assertTrue(executor.failure().contains("29 free slots, stack limit 1, 32 output per craft"));assertEquals(0,server.clicks);
    }
    @Test void capacityWaitCannotAdoptAnotherCraftContainer() {
        var server=baitServer(16);var executor=new CraftingExecutor(()->50);var menu=server.menu();
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(server.r,16,menu,server,Map.of(),Map.of("pufferfish",8),1000));
        var other=new MenuSnapshot(78,"Craft Item",true,menu.slots());
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(server.r,16,other,server,Map.of(),Map.of("pufferfish",8),1200));
        assertEquals(0,server.clicks);assertTrue(executor.failure().contains("owner/container changed"));
    }
    @Test void quickBiteGridCollectsTheRawBookVariantAndPreservesOtherLevels() {
        var r=RecipeCatalog.instance().forOutput("ENCHANTMENT_QUICK_BITE_1").getFirst();var server=new Server(r,0);
        server.slots[54]=stack(54,"ENCHANTED_PAPER",3);server.slots[55]=stack(55,"ALLIGATOR_SKIN",8);
        server.slots[60]=stack(60,"ENCHANTMENT_QUICK_BITE_2",1);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(1,executor.completedBatches());
        assertTrue(server.inputs.contains("shift:23"));assertEquals(1,server.menu().countInInventory(r.outputId()));
        assertEquals(1,server.menu().countInInventory("ENCHANTMENT_QUICK_BITE_2"));
        assertEquals("ENCHANTED_BOOK",server.slots[56].customId());assertTrue(CraftingExecutor.reusableMenu(server.menu()));
    }
    @Test void quickBiteQuickCraftVerifiesExactRawBookMetadata() {
        var r=RecipeCatalog.instance().forOutput("ENCHANTMENT_QUICK_BITE_1").getFirst();var server=new Server(r,0);
        server.slots[54]=stack(54,"ENCHANTED_PAPER",3);server.slots[55]=stack(55,"ALLIGATOR_SKIN",8);server.quickAvailable=1;
        var executor=new CraftingExecutor(()->50);executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),1000);
        assertEquals(List.of("shift:16"),server.inputs);
        assertEquals(CraftingExecutor.Result.CRAFTED,executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),1100),executor.failure());
        assertEquals(1,server.menu().countInInventory(r.outputId()));assertEquals(1,executor.completedBatches());
    }
    @Test void wrongLevelAndMultiEnchantCraftResultsNeverClickAndReportTheirIdentity() {
        for(var enchants:List.of(Map.of("quick_bite",2),Map.of("quick_bite",1,"sharpness",1))) {
            var r=RecipeCatalog.instance().forOutput("ENCHANTMENT_QUICK_BITE_1").getFirst();var server=new Server(r,0);
            server.slots[54]=stack(54,"ENCHANTED_PAPER",3);server.slots[55]=stack(55,"ALLIGATOR_SKIN",8);server.resultEnchantments=enchants;
            var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
            for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
                result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now);
            assertEquals(CraftingExecutor.Result.BLOCKED,result);assertFalse(server.inputs.contains("shift:23"));
            assertTrue(executor.failure().contains("expected 1x ENCHANTMENT_QUICK_BITE_1"));
            assertTrue(executor.failure().contains(enchants.size()==1?"observed 1x ENCHANTMENT_QUICK_BITE_2":"observed 1x ENCHANTED_BOOK"));
            assertEquals(8,server.slots[30].count());assertTrue(server.cursor.empty());
        }
    }
    @Test void delayedBookResultCanRecoverBeforeTheBoundedWait() {
        var r=RecipeCatalog.instance().forOutput("ENCHANTMENT_QUICK_BITE_1").getFirst();var server=new Server(r,0);
        server.slots[54]=stack(54,"ENCHANTED_PAPER",3);server.slots[55]=stack(55,"ALLIGATOR_SKIN",8);server.resultEnchantments=Map.of("quick_bite",2);
        var executor=new CraftingExecutor(()->50);
        for(long now=1000;now<=2000;now+=50)assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now));
        assertFalse(server.inputs.contains("shift:23"));server.resultEnchantments=null;
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),2050));
        assertEquals(CraftingExecutor.Result.CRAFTED,executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),2150));
    }
    @Test void bookIngredientUsesExactVariantInCursorGridAndConservation() {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("ENCHANTMENT_QUICK_BITE_1",1));
        var r=new ProductionRecipe("craft:BOOK_INPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,
            Map.of("ENCHANTMENT_QUICK_BITE_1",1),grid,0,0,"",null);var server=new Server(r,0);
        server.slots[54]=stack(54,"ENCHANTMENT_QUICK_BITE_1",1);server.slots[55]=stack(55,"ENCHANTMENT_QUICK_BITE_2",1);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(0,server.menu().countInInventory("ENCHANTMENT_QUICK_BITE_1"));
        assertEquals(1,server.menu().countInInventory("ENCHANTMENT_QUICK_BITE_2"));assertEquals(List.of("left:54","left:10","shift:23"),server.inputs);
        assertEquals("ENCHANTMENT_QUICK_BITE_2",ProductionMenus.productId(server.slots[55]));
    }
    @Test void catalogUsesBundledEnchantmentNamesForSearchAndReceipts() {
        var catalog=RecipeCatalog.instance();
        assertEquals("Quick Bite I",catalog.name("ENCHANTMENT_QUICK_BITE_1"));assertEquals("Quick Bite V",catalog.name("ENCHANTMENT_QUICK_BITE_5"));
        assertEquals("Turbo-Cane I",catalog.name("ENCHANTMENT_TURBO_CANE_1"));assertEquals("Ultimate Wise V",catalog.name("ENCHANTMENT_ULTIMATE_WISE_5"));
        assertEquals("ENCHANTMENT_QUICK_BITE_0",catalog.name("ENCHANTMENT_QUICK_BITE_0"));
        assertEquals("ENCHANTMENT_UNDOCUMENTED_1",catalog.name("ENCHANTMENT_UNDOCUMENTED_1"));
        assertEquals("Enchanted Paper",catalog.name("ENCHANTED_PAPER"));
        assertEquals("Enchantment Table",catalog.name("ENCHANTMENT_TABLE"));
    }
    @Test void specificReviewChecksItsResolvedOwnerBeforeWritingAndPreservesOtherReviews()throws Exception {
        var path=dir.resolve("scoped-review.json");var jobs=new ProductionJobs(path);
        var bait=new ProductionJobs.Job("43bbdc42-bait","run:craft:HOTSPOT_BAIT","account",ProductionJobs.State.REVIEW,16,-1,0,0,4352247.2,null,null,"Production paused");
        var sale=new ProductionJobs.Job("800c2279-sale","run:craft:FLAWLESS_SAPPHIRE_GEM","account",ProductionJobs.State.REVIEW,2,-1,0,0,3519388.3,null,null,"Production stopped");
        jobs.put(bait);jobs.put(sale);
        var checked=new ArrayList<String>();
        jobs.acknowledgeReview("43bbdc42","account",id->{checked.add(id);return id.equals(sale.id());});
        assertEquals(List.of(bait.id()),checked);assertEquals(ProductionJobs.State.CANCELLED,jobs.find(bait.id()).orElseThrow().state());
        String before=Files.readString(path);
        assertThrows(IllegalArgumentException.class,()->jobs.acknowledgeReview("800c2279","account",id->true));
        assertEquals(before,Files.readString(path));assertEquals(sale,jobs.find(sale.id()).orElseThrow());
        assertEquals(4352247.2,jobs.find(bait.id()).orElseThrow().costBasis());
    }
    @Test void quickCraftsFifteenHotspotBaitBatchesWithOneShiftClickAndExactReceipt() {
        var r=RecipeCatalog.instance().forOutput("HOTSPOT_BAIT").stream().filter(v->v.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var server=new Server(r,0);int slot=54;
        for(var need:r.ingredients().entrySet())server.slots[slot]=stack(slot++,need.getKey(),need.getValue()*15);
        server.quickAvailable=15;var executor=new CraftingExecutor(()->50);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1000));
        assertEquals(List.of("shift:16"),server.inputs);
        assertEquals(CraftingExecutor.Result.CRAFTED,executor.tick(r,16,server.menu(),server,Map.of(),Map.of("pufferfish",8),1100),executor.failure());
        assertEquals(15,executor.completedBatches());assertTrue(CraftingExecutor.reusableMenu(server.menu()));
        assertEquals(480,Arrays.stream(server.slots).filter(v->v.inPlayerInventory()&&r.outputId().equals(v.customId())).mapToInt(SlotView::count).sum());
    }
    @Test void quickCraftCannotConsumeMoreThanTheRequestedOwnerBatch() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.quickAvailable=8;
        var executor=new CraftingExecutor(()->50);
        executor.tick(r,4,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        assertEquals(List.of("left:54"),server.inputs);
    }
    @Test void quickCraftWithDifferentIngredientsFallsBackToTheExactGridRecipe() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.quickAvailable=8;server.wrongQuickRecipe=true;
        new CraftingExecutor(()->50).tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        assertEquals(List.of("left:54"),server.inputs);
    }
    @Test void partialQuickCraftReportsOnlyTheVerifiedBatchAndKeepsTheMenuReusable() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.quickAvailable=8;server.oneAtATime=true;
        var executor=new CraftingExecutor(()->50);executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        assertEquals(CraftingExecutor.Result.CRAFTED,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1100));
        assertEquals(1,executor.completedBatches());assertEquals(14,server.slots[54].count());assertEquals(1,server.clicks);
    }
    @Test void quickCraftWaitsForLaggedReceiptAndNeverAcceptsOutputWithoutIngredientConsumption() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.quickAvailable=8;server.discard=true;
        var executor=new CraftingExecutor(()->50);executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1900));
        assertEquals(1,server.clicks);server.slots[56]=stack(56,"OUTPUT",8);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),2000));
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),3600));
        assertEquals(1,server.clicks);
    }
    @Test void loadsAnEntireRodStackAndVerifiesAll128PowderInOneCollection() {
        var r=RecipeCatalog.instance().forOutput("BLAZE_POWDER").getFirst();var server=new Server(r,0);
        server.slots[54]=stack(54,"BLAZE_ROD",64);server.slots[55]=stack(55,"",0);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,64,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(64,executor.completedBatches());assertEquals(64,server.slots[56].count());assertEquals(64,server.slots[57].count());
        assertEquals(3,server.clicks);assertEquals(List.of("left:54","left:10","shift:23"),server.inputs);
    }
    @Test void partialServerCraftsReuseTheLoadedGridWithoutReplacingInputs() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.oneAtATime=true;
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(8,executor.completedBatches());assertEquals(8,server.slots[56].count());
        assertEquals(4,server.inputs.stream().filter(x->x.startsWith("left:")).count());assertEquals(8,server.inputs.stream().filter(x->x.startsWith("shift:")).count());
    }
    @Test void bulkCraftNeverCountsAnOutputWithoutItsExactConsumedIngredients() {
        var r=recipe(2);var server=new Server(r,16);server.slots[55]=stack(55,"SECOND",8);server.discard=true;
        var executor=new CraftingExecutor(()->50);assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1000));
        server.slots[56]=stack(56,"OUTPUT",8);
        assertEquals(CraftingExecutor.Result.WAITING,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),1100));
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,8,server.menu(),server,Map.of(),Map.of("collection",2),2700));assertEquals(1,server.clicks);
    }
    @Test void unknownUnstackableOutputLimitsTheLoadedBatchToFreeInventorySpace() {
        var r=recipe(1);var server=new Server(r,64);server.slots[55]=stack(55,"SECOND",64);
        for(int i=57;i<90;i++)server.slots[i]=stack(i,"OTHER",64);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,64,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(1,executor.completedBatches());
    }
    @Test void nonPowerOfTwoAmountsAreLoadedOnceUsingHalvesAndExactRemainders() {
        var base=recipe(1);var r=new ProductionRecipe(base.key(),base.kind(),"BLAZE_POWDER",1,base.ingredients(),base.grid(),0,0,base.requirement(),null);
        var server=new Server(r,64);server.slots[55]=stack(55,"SECOND",47);var executor=new CraftingExecutor(()->50);
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,47,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(47,executor.completedBatches());assertEquals(17,server.slots[54].count());
        assertTrue(server.clicks<30,"47 exact inputs should use a half-stack plus remainder, not 47 placements");
    }
    @Test void exactInventoryStackIsPreferredOverSplittingALargerCompatibleStack(){
        var r=recipe(20);var server=new Server(r,64);server.slots[56]=stack(56,"INPUT",20);
        var executor=new CraftingExecutor(()->50);var result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals("left:56",server.inputs.getFirst());assertEquals(64,server.slots[54].count());
    }
    @Test void arbitraryAndOddStackSplitsCraftExactAmountsAndPreserveEveryRemainder(){
        for(int stack:new int[]{17,31,47,63,64})for(int wanted=1;wanted<=stack;wanted++) {
            var base=recipe(1);var r=new ProductionRecipe(base.key(),base.kind(),"BLAZE_POWDER",1,base.ingredients(),base.grid(),0,0,base.requirement(),null);
            var server=new Server(r,stack);server.slots[55]=stack(55,"SECOND",wanted);
            var executor=new CraftingExecutor(()->50);var result=CraftingExecutor.Result.WAITING;
            for(long now=1000;now<30000&&result==CraftingExecutor.Result.WAITING;now+=50)
                result=executor.tick(r,wanted,server.menu(),server,Map.of(),Map.of("collection",2),now);
            assertEquals(CraftingExecutor.Result.CRAFTED,result,"stack="+stack+", need="+wanted+": "+executor.failure());
            assertEquals(wanted,executor.completedBatches());assertTrue(server.cursor.empty());
            assertEquals(stack-wanted,Arrays.stream(server.slots).filter(v->"INPUT".equals(v.bazaarProductId())).mapToInt(SlotView::count).sum());
            if(stack==64&&wanted==20)assertTrue(server.clicks<=15,"Twenty ingredients should use repeated halves plus four placements: "+server.inputs);
        }
    }
    @Test void consecutivePowderAndEyeCraftsUseTheSameContainerWithoutReopening() {
        var powder=RecipeCatalog.instance().forOutput("BLAZE_POWDER").getFirst();var eye=RecipeCatalog.instance().forOutput("ENCHANTED_EYE_OF_ENDER").getFirst();
        var server=new Server(powder,0);server.slots[54]=stack(54,"BLAZE_ROD",32);server.slots[55]=stack(55,"ENCHANTED_ENDER_PEARL",16);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        long now=1000;
        for(;now<20000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(powder,32,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertTrue(CraftingExecutor.reusableMenu(server.menu()));
        int container=server.menu().containerId();server.r=eye;result=CraftingExecutor.Result.WAITING;
        for(;now<40000&&result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(eye,1,server.menu(),server,Map.of(),Map.of("enderpearl",6),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(container,server.menu().containerId());assertTrue(CraftingExecutor.reusableMenu(server.menu()));
        assertEquals(1,Arrays.stream(server.slots).filter(v->!v.empty()&&eye.outputId().equals(v.customId())).mapToInt(SlotView::count).sum());
    }
    @Test void hypixelsQuickCraftSuggestionIsNotMistakenForTheGridResult() {
        var r=recipe(32);var server=new Server(r,64);server.quickCraft=true;var executor=new CraftingExecutor(()->50);
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
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
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
            var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
            for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=100)
                result=executor.tick(r,server.menu(),server,Map.of(),Map.of(),now);
            assertEquals(CraftingExecutor.Result.CRAFTED,result,id+": "+executor.failure());assertTrue(server.cursor.empty());
            assertEquals(id,server.slots[56].customId());assertEquals(r.outputCount(),server.slots[56].count());
        }
    }
    @Test void fullCraftWaitsForEachObservationAndConservesSurplusIngredients() {
        var r=recipe(5);var server=new Server(r,64);var executor=new CraftingExecutor(()->50);
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(59,server.slots[54].count());assertEquals("OUTPUT",server.slots[56].customId());assertTrue(server.cursor.empty());
    }
    @Test void missingCollectionBlocksBeforeTouchingIngredients() {
        for(var unlocks:List.of(Map.<String,Integer>of(),Map.of("collection",1))) {
            var r=recipe(5);var server=new Server(r,64);var executor=new CraftingExecutor(()->50);
            assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),unlocks,1000));
            assertEquals(0,server.clicks);assertEquals(64,server.slots[54].count());assertTrue(server.cursor.empty());
        }
    }
    @Test void fullStackIsPickedUpThenSplitInsideGridAndOutputIsShiftClicked() {
        var r=recipe(32);var server=new Server(r,64);var executor=new CraftingExecutor(()->50);
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
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(3,server.inputs.stream().filter(s->s.startsWith("right:")).count());
        assertTrue(server.inputs.stream().noneMatch(s->s.equals("right:54") || s.equals("right:57") || s.equals("right:58")));
        assertEquals(32,server.slots[58].count());assertTrue(server.cursor.empty());assertEquals("OUTPUT",server.slots[56].customId());
    }
    @Test void ignoredGridSplitRetriesOnlyWhileTheSameFullStackRemains() {
        var r=recipe(32);var server=new Server(r,64);server.ignoreSplitOnce=true;var executor=new CraftingExecutor(()->50);
        CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<20000 && result==CraftingExecutor.Result.WAITING;now+=150)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(32,server.slots[54].count());
        assertEquals(2,server.inputs.stream().filter("right:10"::equals).count());assertTrue(server.cursor.empty());
    }
    @Test void enchantedEyeSplitsBlazeIntoFourSixteensAcrossTheMixedIngredientGrid() {
        var r=RecipeCatalog.instance().forOutput("ENCHANTED_EYE_OF_ENDER").stream().filter(x->x.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElseThrow();
        var server=new Server(r,64);server.slots[54]=stack(54,"BLAZE_POWDER",64);server.slots[55]=stack(55,"ENCHANTED_ENDER_PEARL",16);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;long now=1000;
        for(;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("enderpearl",6),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
        assertEquals(List.of("left:54","left:11","right:11","left:19","right:11","left:21","right:19","left:29","left:55","left:20","shift:23"),server.inputs);
        assertTrue(now-1000<=1600,"Acknowledged crafting added unnecessary delays: "+(now-1000));
        assertEquals("ENCHANTED_EYE_OF_ENDER",server.slots[56].customId());assertTrue(server.cursor.empty());
    }
    @Test void uniqueUuidMinionUpgradesCraftFourDwarvenCompactorsInExactSingleBatchRounds()throws Exception {
        assertDwarvenCompactorRounds(true);
    }
    @Test void distinctComponentsWithoutUuidAlsoPreventMinionUpgradeStackMerging()throws Exception {
        assertDwarvenCompactorRounds(false);
    }
    private void assertDwarvenCompactorRounds(boolean withUuid)throws Exception {
        var r=RecipeCatalog.instance().forOutput("DWARVEN_COMPACTOR").getFirst();var server=new Server(r,0);
        server.slots[55]=stack(55,"",0);
        for(int i=54;i<58;i++)server.slots[i]=stack(i,"ENCHANTED_MITHRIL",i==57?32:64);
        for(int i=0;i<4;i++) {
            server.slots[70+i]=taggedStack(70+i,"AUTO_SMELTER",1,withUuid?"smelter-"+i:null,withUuid?null:"smelter-components-"+i);
            server.slots[74+i]=taggedStack(74+i,"SUPER_COMPACTOR_3000",1,withUuid?"compactor-"+i:null,withUuid?null:"compactor-components-"+i);
        }
        assertEquals(64,server.slots[70].maxStackSize());
        assertFalse(server.slots[70].canStackWith(server.slots[71]));
        if(!withUuid){assertNull(server.slots[70].metadata().uuid());assertNotNull(server.slots[70].metadata().stackIdentity());}
        server.quickCraft=true;server.quickPreviewProduct="MITHRIL_COAT";
        var executor=new CraftingExecutor(()->50);long now=1000;int remaining=4;
        var journalPath=dir.resolve(withUuid?"uuid-rounds.json":"component-rounds.json");var jobs=new ProductionJobs(journalPath);
        double cost=1_110_533.0;
        var job=new ProductionJobs.Job("dwarven-owned",r.key(),"account",ProductionJobs.State.PROCESSING,4,-1,0,0,cost,null,null,null);
        jobs.put(job);
        while(remaining>0) {
            CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
            for(;now<60000 && result==CraftingExecutor.Result.WAITING;now+=50)
                result=executor.tick(r,remaining,server.menu(),server,Map.of(),Map.of(),now);
            assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());
            assertEquals(1,executor.completedBatches(),"Distinct minion upgrades must stay in individual craft rounds");
            remaining-=executor.completedBatches();job=job.completedBatch();jobs.put(job);
            assertEquals(cost,jobs.find(job.id()).orElseThrow().costBasis());
            assertTrue(CraftingExecutor.reusableMenu(server.menu()));
        }
        assertEquals(4,server.menu().countInInventory(r.outputId()));
        for(var ingredient:r.ingredients().keySet())assertEquals(0,server.menu().countInInventory(ingredient),ingredient);
        assertEquals(4,server.inputs.stream().filter("shift:23"::equals).count());
        assertTrue(server.inputs.stream().noneMatch(s->s.endsWith(":16")),"Mithril Coat preview must never be clicked");
        assertEquals(0,server.swaps);assertTrue(server.cursor.empty());
        for(String ingredient:List.of("AUTO_SMELTER","SUPER_COMPACTOR_3000")) {
            var consumed=server.consumedIngredients.stream().filter(s->ingredient.equals(s.bazaarProductId())).toList();
            assertEquals(4,consumed.size());assertTrue(consumed.stream().allMatch(s->s.count()==1));
            assertEquals(4,consumed.stream().map(SlotView::metadata).distinct().count(),"Pickup and grid placement lost distinct item ownership");
        }
        var restored=new ProductionJobs(journalPath).find(job.id()).orElseThrow();
        assertEquals(4,restored.completedBatches());assertEquals(cost,restored.costBasis());assertEquals(ProductionJobs.State.OUTPUT_READY,restored.state());
    }
    @Test void compatibleFragmentedIngredientStacksStillCraftTheRequestedWholeBatch() {
        var base=recipe(1);var r=new ProductionRecipe(base.key(),base.kind(),"BLAZE_POWDER",1,base.ingredients(),base.grid(),0,0,base.requirement(),null);
        var server=new Server(r,2);server.slots[57]=stack(57,"INPUT",2);server.slots[55]=stack(55,"SECOND",4);
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,4,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(4,executor.completedBatches());
        assertEquals(4,server.menu().countInInventory("BLAZE_POWDER"));assertEquals(1,server.inputs.stream().filter("shift:23"::equals).count());assertEquals(0,server.swaps);
    }
    @Test void unequalCellQuantitiesRespectTheFeasibleComponentGroupAllocation() {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("INPUT",3));
        grid.set(1,new ProductionRecipe.Ingredient("INPUT",4));
        grid.set(2,new ProductionRecipe.Ingredient("INPUT",5));
        var r=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",12),grid,0,0,"",null);
        var server=new Server(r,0);server.slots[55]=stack(55,"",0);
        server.slots[54]=taggedStack(54,"INPUT",5,null,"group-five");
        server.slots[57]=taggedStack(57,"INPUT",7,null,"group-seven");
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(1,executor.completedBatches());
        assertEquals(1,server.menu().countInInventory("OUTPUT"));assertEquals(0,server.menu().countInInventory("INPUT"));
        assertEquals(12,server.consumedIngredients.stream().mapToInt(SlotView::count).sum());
        assertEquals(1,server.inputs.stream().filter("shift:23"::equals).count());assertEquals(0,server.swaps);
        assertTrue(CraftingExecutor.reusableMenu(server.menu()));
    }
    @Test void insufficientComponentGroupSurplusReturnsBeforeTheNextCellUsesAnotherGroup() {
        var grid=new ArrayList<ProductionRecipe.Ingredient>(Collections.nCopies(9,null));
        grid.set(0,new ProductionRecipe.Ingredient("INPUT",2));grid.set(1,new ProductionRecipe.Ingredient("INPUT",2));
        var r=new ProductionRecipe("craft:OUTPUT:0",ProductionRecipe.Kind.CRAFT,"OUTPUT",1,Map.of("INPUT",4),grid,0,0,"",null);
        var server=new Server(r,0);server.slots[55]=stack(55,"",0);
        server.slots[54]=taggedStack(54,"INPUT",3,null,"first-group");
        server.slots[57]=taggedStack(57,"INPUT",3,null,"second-group");
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of(),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(1,server.menu().countInInventory("OUTPUT"));
        assertEquals(4,server.consumedIngredients.stream().mapToInt(SlotView::count).sum());
        assertEquals(2,server.menu().countInInventory("INPUT"));assertEquals(1,server.slots[54].count());assertEquals(1,server.slots[57].count());
        assertEquals("first-group",server.slots[54].metadata().stackIdentity());assertEquals("second-group",server.slots[57].metadata().stackIdentity());
        assertEquals(0,server.swaps);assertTrue(CraftingExecutor.reusableMenu(server.menu()));
    }
    @Test void metadataChangesCannotKeepAnUnacknowledgedGridMergeAliveForThreeMinutes() {
        var r=recipe(2);
        var server=new Server(r,1) {
            int observations;
            @Override MenuSnapshot menu(){
                // The unavailable server component signature makes these INPUT stacks look
                // compatible; unrelated metadata churn must not count as crafting progress.
                slots[58]=taggedStack(58,"OTHER",1,"observation-"+(++observations),null);
                return super.menu();
            }
        };
        server.slots[57]=stack(57,"INPUT",1);server.forceSwapProduct="INPUT";
        var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;long now=1000;
        for(;now<=9000 && result==CraftingExecutor.Result.WAITING;now+=50)
            result=executor.tick(r,1,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.BLOCKED,result,executor.failure());
        assertTrue(now<=9050,"Grid merge did not stop within eight seconds");
        assertTrue(server.swaps<=3,"Unchanged logical grid/cursor was clicked repeatedly: "+server.swaps);
        assertEquals(1,server.slots[10].count());assertEquals(1,server.cursor.count());assertEquals("INPUT",server.cursor.bazaarProductId());
        assertTrue(server.inputs.stream().noneMatch(s->s.startsWith("shift:")));assertEquals(0,server.menu().countInInventory("OUTPUT"));
    }
    @Test void singleSixteenCellSplitsTwiceAndReturnsTheOtherFortyEight() {
        var r=recipe(16);var server=new Server(r,64);var executor=new CraftingExecutor(()->50);CraftingExecutor.Result result=CraftingExecutor.Result.WAITING;
        for(long now=1000;now<15000 && result==CraftingExecutor.Result.WAITING;now+=50)result=executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(CraftingExecutor.Result.CRAFTED,result,executor.failure());assertEquals(48,server.slots[54].count());
        assertEquals(2,server.inputs.stream().filter("right:10"::equals).count());assertEquals(9,server.clicks);assertTrue(server.cursor.empty());
    }
    @Test void slowdownStopsNewClicksAndRetriesBeforeTheCooldownEnds() {
        var r=recipe(32);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor(()->50);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);executor.slowdown(2900);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3100);assertEquals(1,server.clicks);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3900);assertEquals(2,server.clicks);
    }
    @Test void oversizedCellsNotPlacedForASplitCannotBeAdopted() {
        var r=recipe(16);var server=new Server(r,64);var executor=new CraftingExecutor(()->50);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        server.slots[10]=stack(10,"INPUT",32);server.cursor=stack(-1,"INPUT",32);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1200));
        assertEquals(1,server.clicks);
    }
    @Test void ignoredPickupRetriesBoundedlyWithoutRepeatedFastClicks() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor(()->50);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);
        for(long now=1100;now<3000;now+=100)executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),now);
        assertEquals(1,server.clicks);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),3000);executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),5000);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),7000));assertEquals(3,server.clicks);
    }
    @Test void previewChangesCannotAcknowledgeAnUnchangedCursorAndInventory() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor(()->50);
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000);server.preview=true;
        executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1400);executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1600);assertEquals(1,server.clicks);
    }
    @Test void foreignGridCursorAndMissingIngredientsPreventAllClicks() {
        var r=recipe(5);var server=new Server(r,4);var executor=new CraftingExecutor(()->50);
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
        server=new Server(r,64);server.slots[10]=stack(10,"FOREIGN",1);executor.reset();
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
        server=new Server(r,64);server.cursor=stack(-1,"FOREIGN",1);executor.reset();
        assertEquals(CraftingExecutor.Result.BLOCKED,executor.tick(r,server.menu(),server,Map.of(),Map.of("collection",2),1000));assertEquals(0,server.clicks);
    }
    @Test void briefPacketMismatchWaitsButPersistentMissingItemsBlocks() {
        var r=recipe(5);var server=new Server(r,64);server.discard=true;var executor=new CraftingExecutor(()->50);
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
    @Test void oldFinishedJobsArePrunedButUnfinishedOnesAreKept()throws Exception {
        var path=dir.resolve("jobs.json");var jobs=new ProductionJobs(path);
        jobs.put(new ProductionJobs.Job("review","craft:OUTPUT:0","account",ProductionJobs.State.REVIEW,1,-1,0,0,null,null,null,"check me"));
        for(int i=0;i<ProductionJobs.KEEP_FINISHED+40;i++)jobs.put(new ProductionJobs.Job("done-"+i,"craft:OUTPUT:0","account",i%2==0?ProductionJobs.State.DONE:ProductionJobs.State.CANCELLED,1,-1,0,0,null,null,null,null));
        jobs.put(new ProductionJobs.Job("new","craft:OUTPUT:0","account",ProductionJobs.State.PLANNED,1,-1,0,0,null,null,null,null));
        var reloaded=new ProductionJobs(path);
        assertTrue(reloaded.find("review").isPresent());assertTrue(reloaded.find("new").isPresent());
        assertTrue(reloaded.find("done-0").isEmpty());assertTrue(reloaded.find("done-"+(ProductionJobs.KEEP_FINISHED+39)).isPresent());
        assertTrue(reloaded.all().stream().filter(j->j.state()==ProductionJobs.State.DONE||j.state()==ProductionJobs.State.CANCELLED).count()<=ProductionJobs.KEEP_FINISHED);
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
