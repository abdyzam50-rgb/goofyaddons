package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoadingPipelineTest {
    @Test void saleWaitsForLoadingTotalInPlaceThenSubmitsAndVerifiesOnlyOnce(){
        var helper=new BazaarInstantBuyTest();var actions=new RecordingActions();var intents=new ArrayList<String>();
        var sale=new BazaarInstantSell("ENCHANTED_COAL","Enchanted Coal",1,900,1100,intents::add);
        var loading=helper.menu(1,"Enchanted Coal",helper.item(13,"ENCHANTED_COAL",1),
                SlotView.named(11,"Sell Instantly",List.of("Amount: 1x","Total: Loading...")),helper.item(54,"ENCHANTED_COAL",1));
        for(long at:new long[]{1000,1010,1020,2000,5000})assertEquals(BazaarInstantSell.Result.WAITING,sale.tick(loading,actions,1000,at));
        assertTrue(actions.serverEffects().isEmpty());assertTrue(intents.isEmpty());assertNull(sale.failure());
        var ready=helper.menu(1,"Enchanted Coal",helper.item(13,"ENCHANTED_COAL",1),
                SlotView.named(11,"Sell Instantly",List.of("Total: 950 coins")),helper.item(54,"ENCHANTED_COAL",1));
        sale.tick(ready,actions,1000,5010);sale.tick(ready,actions,1000,5020);
        assertEquals(List.of("click:11"),actions.serverEffects());assertEquals(1,intents.size());
        assertEquals(BazaarInstantSell.Result.SOLD,sale.tick(helper.menu(1,null),actions,1950,5100));
        assertEquals(950,sale.proceeds());
    }
    @Test void buyLoadingPriceWaitsWithoutOpeningAmountOrRecordingIntent(){
        var h=new BazaarInstantBuyTest();var actions=new RecordingActions();var buy=h.buy(1100);
        var loading=h.menu(1,"Enchanted Coal",h.item(13,"ENCHANTED_COAL",1),SlotView.named(10,"Buy Instantly",List.of("Enchanted Coal","Price per unit: Loading...")));
        buy.tick(loading,false,actions,5000,1000);buy.tick(loading,false,actions,5000,5000);
        assertTrue(actions.serverEffects().isEmpty());assertTrue(h.intents.isEmpty());
        buy.tick(h.product(100),false,actions,5000,5010);assertEquals(List.of("click:10"),actions.serverEffects());
    }
    @Test void persistentSaleLoadingTimesOutWithoutSelling(){
        var h=new BazaarInstantBuyTest();var actions=new RecordingActions();
        var sale=new BazaarInstantSell("ENCHANTED_COAL","Enchanted Coal",1,900,r->fail("No sale intent permitted"));
        var loading=h.menu(1,"Enchanted Coal",h.item(13,"ENCHANTED_COAL",1),SlotView.named(11,"Sell Instantly",List.of("Total: Loading...")),h.item(54,"ENCHANTED_COAL",1));
        sale.tick(loading,actions,1000,1000);
        assertEquals(BazaarInstantSell.Result.BLOCKED,sale.tick(loading,actions,1000,46001));
        assertTrue(actions.serverEffects().isEmpty());
    }
    @Test void storageScanWaitsForLoadingAndStableContentsBeforeSavingCapacity()throws Exception{
        var world=new ProductionStorageTest.World().page("ec 1",4);var ledger=new ProductionStorage(null);
        var pages=ProductionStorage.pages("ec 1");var scan=ProductionStorage.Move.scan(ledger,pages);
        scan.tick(world.menu(),world,1000);
        var ready=world.menu();var slots=new ArrayList<>(ready.slots());slots.set(0,SlotView.named(0,"Loading...",List.of()));
        var loading=new MenuSnapshot(ready.containerId(),ready.title(),true,slots);
        scan.tick(loading,world,1100);scan.tick(loading,world,2000);assertEquals(-1,ledger.emptySlots(pages));
        scan.tick(ready,world,2010);scan.tick(ready,world,2409);assertEquals(-1,ledger.emptySlots(pages));
        scan.tick(ready,world,2410);assertEquals(4,ledger.emptySlots(pages));assertEquals(0,world.clicks);
    }
}
