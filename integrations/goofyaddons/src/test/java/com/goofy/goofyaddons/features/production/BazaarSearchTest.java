package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BazaarSearchTest {
    SlotView result(int index,String id,String name) {
        return new SlotView(index,false,index,false,name,name,List.of(),id,null,1,64);
    }
    MenuSnapshot menu(SlotView... items) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:items)slots.set(s.index(),s);
        return new MenuSnapshot(3,"Bazaar ➜ \"Gold Ingot\"",true,slots);
    }
    @Test void productIdDisambiguatesEnchantedAndNormalResultsInEitherOrder() {
        for(boolean reversed:List.of(false,true)) {
            int gold=reversed?11:12,enchanted=reversed?12:11;
            var page=menu(result(gold,"GOLD_INGOT","Gold Ingot"),result(enchanted,"ENCHANTED_GOLD","Gold Ingot"));
            assertEquals(gold,BazaarSearch.resultSlot(page,"GOLD_INGOT","Gold Ingot"));
            assertEquals(enchanted,BazaarSearch.resultSlot(page,"ENCHANTED_GOLD","Enchanted Gold Ingot"));
            var actions=new RecordingActions();var search=new BazaarSearch("GOLD_INGOT","Gold Ingot");
            search.step(page,false,actions,1000);search.step(page,false,actions,1100);
            assertEquals(List.of("click:"+gold),actions.serverEffects());
        }
    }
    @Test void readableWrongIdCannotBeAdoptedByAnExactName() {
        assertEquals(-1,BazaarSearch.resultSlot(menu(result(11,"ENCHANTED_GOLD","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
    }
    @Test void uniqueExactNameRemainsFallbackWithoutProductId() {
        assertEquals(12,BazaarSearch.resultSlot(menu(result(11,null,"Enchanted Gold Ingot"),result(12,null,"§6Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(-1,BazaarSearch.resultSlot(menu(result(11,null,"Gold Ingot"),result(12,null,"Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
    }
    @Test void confirmedIdWinsOverNamelessDuplicatesAndPlayerInventoryIsExcluded() {
        assertEquals(13,BazaarSearch.resultSlot(menu(result(11,null,"Gold Ingot"),result(12,null,"Gold Ingot"),result(13,"GOLD_INGOT","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        var held=new SlotView(54,true,0,false,"Gold Ingot","",List.of(),"GOLD_INGOT",null,64,64);
        assertEquals(-1,BazaarSearch.resultSlot(menu(held,result(11,"ENCHANTED_GOLD","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(-1,BazaarSearch.resultSlot(menu(result(11,"GOLD_INGOT","Gold Ingot"),result(12,"GOLD_INGOT","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
    }
    @Test void wrongProductPageCannotTriggerInstantBuyDespiteMatchingLabels() {
        var page=menu(result(13,"ENCHANTED_GOLD","Gold Ingot"),SlotView.named(10,"Buy Instantly",List.of("Gold Ingot","Price per unit: 10 coins")));
        var actions=new RecordingActions();var intents=new ArrayList<String>();
        var buy=new BazaarInstantBuy("GOLD_INGOT","Gold Ingot",64,1000,intents::add);
        buy.tick(page,false,actions,5000,1000);
        assertTrue(actions.serverEffects().stream().noneMatch(s->s.startsWith("click:")));assertTrue(intents.isEmpty());
    }
}
