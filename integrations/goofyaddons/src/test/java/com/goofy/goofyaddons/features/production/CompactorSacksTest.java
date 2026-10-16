package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompactorSacksTest {
    MenuSnapshot bazaar(String line){return new MenuSnapshot(4,"Bazaar ➜ Mining",true,List.of(SlotView.named(48,"Sell Sacks Now",
        List.of("Instantly sell everything in your","sacks on the Bazaar.","",line,"","You earn: 28,489 coins","Click to sell!"))));}
    @Test void capturedBazaarSackQuoteReadsExactGoldBalanceWithoutClickingTheSale() {
        var menu=bazaar(" 64x Enchanted Gold Ingot for 28,813 coins");
        assertEquals(64,CompactorSacks.bazaarBalance(menu,"ENCHANTED_GOLD","Enchanted Gold Ingot"));
        var reader=new CompactorSacks("ENCHANTED_GOLD","Enchanted Gold Ingot");var actions=new RecordingActions();
        reader.tick(new MenuSnapshot(0,null,true,List.of()),actions,true,1000);
        reader.tick(menu,actions,true,1500);
        assertEquals(ProductionLoop.Step.DONE,reader.tick(menu,actions,true,2000));assertEquals(64,reader.balance());
        assertEquals(List.of("command:bz"),actions.serverEffects());
    }
    @Test void hiddenOrRoundedQuantitiesAndOtherGoldProductsCannotProveABalance() {
        for(String line:List.of("64x Enchanted Gold Block for 28,813 coins","1.2kx Enchanted Gold Ingot for 28,813 coins","+ 10 other items"))
            assertNull(CompactorSacks.bazaarBalance(bazaar(line),"ENCHANTED_GOLD","Enchanted Gold Ingot"));
        assertEquals(0,CompactorSacks.bazaarBalance(bazaar("Your sacks are empty!"),"ENCHANTED_GOLD","Enchanted Gold Ingot"));
    }
    @Test void absentBazaarLineFallsBackToExactEnchantedSackAndDoesNotReplayItsOpenClick() {
        var reader=new CompactorSacks("ENCHANTED_GOLD","Enchanted Gold Ingot");var actions=new RecordingActions();
        var closed=new MenuSnapshot(0,null,true,List.of());
        var other=bazaar("64x Enchanted Gold Block for 28,813 coins");
        reader.tick(closed,actions,true,1000);reader.tick(other,actions,true,1500);reader.tick(other,actions,true,2000);
        reader.tick(closed,actions,true,2500);
        var root=new MenuSnapshot(5,"Sack of Sacks",true,List.of(SlotView.named(10,"Large Enchanted Mining Sack",List.of("Click to open!"))));
        reader.tick(root,actions,true,3000);reader.tick(root,actions,true,3500);reader.tick(root,actions,true,4000);
        var stored=new SlotView(12,false,12,false,"Enchanted Gold","Enchanted Gold",List.of("Stored: 12,000/20,160"),"ENCHANTED_GOLD",null,1,64);
        var sack=new MenuSnapshot(6,"Enchanted Mining Sack",true,List.of(stored));
        reader.tick(sack,actions,true,4500);assertEquals(ProductionLoop.Step.DONE,reader.tick(sack,actions,true,5000));
        assertEquals(12000,reader.balance());assertTrue(reader.found());
        assertEquals(List.of("command:bz","command:sacks","click:10"),actions.serverEffects());
    }

    @Test void exactSackTooltipParsesCommasAndRejectsRoundedCounts() {
        assertEquals(12345,CompactorSacks.stored(SlotView.named(1,"Enchanted Gold",List.of("Stored: 12,345/20,160"))));
        assertNull(CompactorSacks.stored(SlotView.named(1,"Enchanted Gold",List.of("Stored: 12.3k/20.1k"))));
    }
}
