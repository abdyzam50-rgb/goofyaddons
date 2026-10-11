package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Craft books retain their exact enchantment and level through Bazaar ownership and navigation. */
class ProductionBookOrderTest {
    private static final String ID="ENCHANTMENT_QUICK_BITE_1",NAME="Quick Bite I";
    @TempDir Path dir;

    private static SlotView book(int index,int level,int count,boolean multiple) {
        return new SlotView(index,index>=54,index>=54?index-54:index,false,"Enchanted Book",NAME,List.of(),"ENCHANTED_BOOK",
            multiple?Map.of("quick_bite",level,"luck",1):Map.of("quick_bite",level),count,64);
    }
    private static MenuSnapshot menu(String title,SlotView... actual) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var slot:actual)slots.set(slot.index(),slot);return new MenuSnapshot(42,title,true,slots);
    }
    private static ConfiguredTestServices services() {
        GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=0;
        return new ConfiguredTestServices(){public boolean productionOnly(){return true;}public double purse(){return 100000;}};
    }
    private static GeneralPosition output() {
        var p=new GeneralPosition();p.item=new GeneralItem(ID,NAME);p.quantity=2;p.unitCost=100;p.sellPrice=110;
        p.minimumSellPrice=100;p.stage=GeneralPosition.Stage.INVENTORY;p.productionParent="parent-book-craft";p.tradeId="crafted-book";
        return p;
    }

    @Test void scopedCraftBookRoundTripsWithoutAllowingItInOrdinaryGeneralFlips() throws Exception {
        var path=dir.resolve("book.json");var repository=new JsonGeneralOrderRepository(()->path);var p=output();
        assertEquals(List.of(p),GeneralPosition.validated(List.of(p)));repository.save(List.of(p));
        var restored=repository.load().getFirst();assertEquals(ID,restored.item.id());assertEquals(NAME,restored.item.name());
        p.productionParent=null;assertThrows(IllegalArgumentException.class,()->GeneralPosition.validated(List.of(p)));
        var ordinary=new GeneralSettings();ordinary.items=List.of(new GeneralItem(ID,NAME));
        assertThrows(IllegalArgumentException.class,ordinary::validate);
    }

    @Test void malformedBookProductsCannotBeSavedOrQueued() throws Exception {
        for(String invalid:List.of("ENCHANTMENT_QUICK_BITE","ENCHANTMENT_QUICK_BITE_0","ENCHANTMENT_QUICK_BITE_01",
            "ENCHANTMENT__1","ENCHANTMENT_QUICK_BITE_101","ENCHANTMENT_QUICK_BITE_999999999999")) {
            var p=output();p.item=new GeneralItem(invalid,NAME);
            assertThrows(IllegalArgumentException.class,()->GeneralPosition.validated(List.of(p)),invalid);
            var engine=new GeneralFlipper(new FakeWorld().showing(menu(null)),new RecordingActions(),
                new JsonGeneralOrderRepository(()->dir.resolve(invalid+".json")),services());engine.start();
            assertFalse(engine.enqueueProductionBasket("parent-book-craft",List.of(new ProductionBazaarOrders.IngredientOrder(invalid,NAME,2,100,103))),invalid);
            assertFalse(engine.enqueueProduction("parent-book-craft",invalid,NAME,2,100,true,103),invalid);
        }
    }

    @Test void fundedIngredientBasketAcceptsOneCanonicalBookAndRetainsItsProductionOwnership() throws Exception {
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("basket.json"));var actions=new RecordingActions();
        var engine=new GeneralFlipper(new FakeWorld().showing(menu(null)),actions,repository,services());engine.start();
        assertTrue(engine.enqueueProductionBasket("parent-book-craft",List.of(
            new ProductionBazaarOrders.IngredientOrder(ID,NAME,2,100,103),
            new ProductionBazaarOrders.IngredientOrder("PAPER","Paper",3,10,10.3))));
        var saved=repository.load();assertEquals(2,saved.size());
        assertTrue(saved.stream().allMatch(p->p.productionBuy&&p.productionBasket&&"parent-book-craft".equals(p.productionParent)));
        assertEquals(2,saved.stream().filter(p->ID.equals(p.item.id())).findFirst().orElseThrow().quantity);
        assertTrue(actions.serverEffects().isEmpty());
    }

    @Test void sellHandoffCountsOnlyExactSingleEnchantmentBooks() throws Exception {
        var world=new FakeWorld().showing(menu(null,book(54,1,2,false),book(55,2,4,false),book(56,1,6,true)));
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("output.json"));var engine=new GeneralFlipper(world,new RecordingActions(),repository,services());engine.start();
        assertFalse(engine.enqueueProduction("parent-book-craft",ID,NAME,3,110,false,100),"Other levels and multi-enchant books must not satisfy output ownership");
        assertFalse(engine.enqueueProduction(ID,NAME,2,110,false,100),"A book may not enter the unscoped general order path");
        assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,2,110,false,100));
        assertEquals(ID,repository.load().getFirst().item.id());assertEquals(2,repository.load().getFirst().quantity);
    }

    @Test void replacementBookIngredientBaselineIgnoresOtherLevels() throws Exception {
        var world=new FakeWorld().showing(menu(null,book(54,1,2,false),book(55,2,9,false),book(56,1,4,true)));
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("replacement.json"));var engine=new GeneralFlipper(world,new RecordingActions(),repository,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,3,100,true,103));
        assertEquals(2,repository.load().getFirst().productionInventoryBefore);
    }

    @Test void ingredientClaimNeedsNewBooksAtExactLevelAboveTheSavedBaseline() throws Exception {
        var world=new FakeWorld().showing(menu(null,book(54,1,2,false),book(55,2,9,false)));
        var repository=new JsonGeneralOrderRepository(()->dir.resolve("claim.json"));var engine=new GeneralFlipper(world,new RecordingActions(),repository,services());engine.start();
        assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,3,100,true,103));var ctx=context(engine);
        assertFalse(new GeneralBuy().filledBeforeVisible(ctx),"Existing books do not verify a new purchase");
        world.showing(menu(null,book(54,1,4,false),book(55,2,10,false),book(56,1,8,true)));engine.poll();
        assertFalse(new GeneralBuy().filledBeforeVisible(ctx),"Wrong levels and additional enchants do not complete the claim");
        world.showing(menu(null,book(54,1,5,false),book(55,2,10,false)));engine.poll();
        assertTrue(new GeneralBuy().filledBeforeVisible(ctx));
        var result=ProductionBazaarOrders.basketResult(engine.productionPositions(),false,"Verified book claim");
        assertTrue(result.done());assertEquals(3,result.units());assertEquals(300,result.cost());
        assertTrue(engine.consumeProduction("parent-book-craft"));assertTrue(repository.load().isEmpty());
    }

    @Test void bookSearchSelectsExactMetadataInsteadOfIdenticalDisplayNames() throws Exception {
        var world=new FakeWorld().showing(menu(null,book(54,1,2,false)));var actions=new RecordingActions();
        var engine=new GeneralFlipper(world,actions,new JsonGeneralOrderRepository(()->dir.resolve("search.json")),services());engine.start();
        assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,2,110,false,100));var ctx=context(engine);
        ctx.trade().selling=true;ctx.transition(GeneralFlipper.Step.PRODUCT);
        world.showing(menu("Bazaar search",book(11,2,1,false),book(12,1,1,false),book(13,1,1,true),book(54,1,2,false)));engine.poll();
        new GeneralOrderEntry().product(ctx);assertEquals(List.of("click:12"),actions.serverEffects());
    }

    @Test void wrongOrMultipleEnchantmentsOnAConfirmationBookNeverSubmit() throws Exception {
        for(boolean multiple:List.of(false,true)) {
            var world=new FakeWorld().showing(menu(null,book(54,1,2,false)));var actions=new RecordingActions();
            var engine=new GeneralFlipper(world,actions,new JsonGeneralOrderRepository(()->dir.resolve("confirmation-"+multiple+".json")),services());engine.start();
            assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,2,110,false,100));var ctx=context(engine);
            ctx.trade().selling=true;ctx.transition(GeneralFlipper.Step.CONFIRM);
            var wrong=book(13,multiple?1:2,2,multiple);
            var button=new SlotView(wrong.index(),false,wrong.containerSlot(),false,wrong.customName(),wrong.hoverName(),
                List.of("Item: "+NAME,"Amount: 2","Unit price: 110 coins"),wrong.customId(),wrong.enchantments(),wrong.count(),wrong.maxStackSize());
            world.showing(menu("Confirm Sell Offer",button,book(54,1,2,false)));engine.poll();
            new GeneralOrderEntry().confirm(ctx,new GeneralSell());assertFalse(engine.productionPosition().submitted);
            assertTrue(actions.serverEffects().stream().noneMatch(action->action.startsWith("click:")));
        }
    }

    @Test void productControlRequiresExactBookMetadataDespiteMatchingGenericNameOrTitle() throws Exception {
        var inventory=book(54,1,2,false);var world=new FakeWorld().showing(menu(null,inventory));var actions=new RecordingActions();
        var engine=new GeneralFlipper(world,actions,new JsonGeneralOrderRepository(()->dir.resolve("navigation.json")),services());engine.start();
        assertTrue(engine.enqueueProduction("parent-book-craft",ID,NAME,2,110,false,100));
        var ctx=context(engine);
        ctx.trade().selling=true;ctx.transition(GeneralFlipper.Step.PRODUCT);var entry=new GeneralOrderEntry();
        var control=SlotView.named(15,"Create Sell Offer",List.of(NAME));
        for(var wrong:List.of(book(13,2,1,false),book(13,1,1,true),SlotView.named(13,NAME,List.of()))) {
            world.showing(menu(NAME,wrong,control,inventory));engine.poll();entry.product(ctx);world.advance(1000);engine.poll();entry.product(ctx);
            assertTrue(actions.serverEffects().isEmpty(),"Matching names alone must not identify an enchantment book");
        }
        world.showing(menu(NAME,book(13,1,1,false),control,inventory));engine.poll();entry.product(ctx);
        world.advance(750);engine.poll();entry.product(ctx);assertEquals(List.of("click:15"),actions.serverEffects());
    }

    private static GeneralContext context(GeneralFlipper engine)throws Exception {
        var active=GeneralFlipper.class.getDeclaredField("active");active.setAccessible(true);active.set(engine,engine.productionPosition());
        var field=GeneralFlipper.class.getDeclaredField("session");field.setAccessible(true);return (GeneralContext)field.get(engine);
    }
}
