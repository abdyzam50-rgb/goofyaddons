package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BazaarInstantBuyTest {
    SlotView item(int i,String id,int n){return new SlotView(i,i>=54,i>=54?i-54:i,false,id,id,List.of(),id,null,n,64);}
    MenuSnapshot menu(int id,String title,SlotView... actual) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:actual)slots.set(s.index(),s);return new MenuSnapshot(id,title,true,slots);
    }
    MenuSnapshot product(double unit,SlotView... inventory) {
        var all=new ArrayList<SlotView>(List.of(item(13,"ENCHANTED_COAL",1),
                SlotView.named(10,"Buy Instantly",List.of("Enchanted Coal","Price per unit: "+unit+" coins"))));
        all.addAll(List.of(inventory));
        return menu(1,"Enchanted Coal",all.toArray(SlotView[]::new));
    }
    final List<String> intents=new ArrayList<>();
    BazaarInstantBuy buy(double limit){return new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",10,limit,intents::add);}

    @Test void buysExactlyOnceAfterRecordingIntentAndProvesItByInventoryAndPurse() {
        var actions=new RecordingActions();var buy=buy(1100);
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(null,false,actions,5000,0));
        assertEquals(List.of("command:bz Enchanted Coal"),actions.serverEffects());
        buy.tick(product(100),false,actions,5000,100);
        assertTrue(actions.serverEffects().contains("click:10"));
        buy.tick(menu(2,"How many do you want?",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,200);
        assertTrue(actions.serverEffects().contains("click:16"));
        assertTrue(intents.isEmpty());
        buy.tick(null,true,actions,5000,300);
        assertEquals(1,intents.size());
        assertTrue(actions.serverEffects().contains("sign:10"));
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(menu(3,null),false,actions,5000,400));
        assertEquals(BazaarInstantBuy.Result.BOUGHT,buy.tick(menu(3,null,item(54,"ENCHANTED_COAL",10)),false,actions,4000,500));
        int effects=actions.serverEffects().size();
        assertEquals(BazaarInstantBuy.Result.BOUGHT,buy.tick(menu(3,null,item(54,"ENCHANTED_COAL",10)),false,actions,4000,600));
        assertEquals(effects,actions.serverEffects().size());
    }

    @Test void priceAboveTheLimitStopsBeforeAnyClick() {
        var actions=new RecordingActions();var buy=buy(900);
        assertEquals(BazaarInstantBuy.Result.BLOCKED,buy.tick(product(100),false,actions,5000,0));
        assertTrue(actions.serverEffects().isEmpty());
        assertTrue(buy.failure().contains("above"));
    }

    @Test void unprovenPurchaseEndsInReviewAndIsNotRepeated() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(product(100),false,actions,5000,0);
        buy.tick(menu(2,"How many do you want?",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        buy.tick(null,true,actions,5000,200);
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(menu(3,null),false,actions,5000,5000));
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,buy.tick(menu(3,null),false,actions,5000,20000));
        assertEquals(1,actions.serverEffects().stream().filter(e->e.startsWith("sign")).count());
    }

    @Test void confirmationScreenIsNotGuessedAt() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(product(100),false,actions,5000,0);
        buy.tick(menu(2,"How many do you want?",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        buy.tick(null,true,actions,5000,200);
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,buy.tick(menu(4,"Confirm Instant Buy"),false,actions,5000,300));
    }

    @Test void readsQuotedUnitPrices() {
        assertEquals(1234.5,BazaarInstantBuy.unitPrice("Enchanted Coal\nPrice per unit: 1,234.5 coins"));
        assertNull(BazaarInstantBuy.unitPrice("Price per unit: free"));
    }

    @Test void aStaleCursorWithNoMenuOnScreenStillOpensTheBazaarButBlocksClicks() {
        var actions=new RecordingActions();var buy=buy(1100);
        var none=new MenuSnapshot(1,null,false,menu(1,null).slots());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(none,false,actions,5000,0));
        assertEquals(List.of("command:bz Enchanted Coal"),actions.serverEffects());
        var held=product(100);held=new MenuSnapshot(held.containerId(),held.title(),false,held.slots());
        assertEquals(BazaarInstantBuy.Result.BLOCKED,buy.tick(held,false,actions,5000,100));
        assertEquals(1,actions.serverEffects().size());
    }

    MenuSnapshot results(int id,String... names) {
        var found=new ArrayList<SlotView>();
        for(int i=0;i<names.length;i++)found.add(SlotView.named(11+i,names[i],List.of()));
        return menu(id,"Bazaar \u279c \"Enchanted Coal\"",found.toArray(SlotView[]::new));
    }

    @Test void clicksTheExactSearchResultInsteadOfResendingTheCommand() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(null,false,actions,5000,0);
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(results(5,"Enchanted Coal Block","Enchanted Coal"),false,actions,5000,100));
        assertEquals(List.of("command:bz Enchanted Coal","click:12"),actions.serverEffects());
        // The same results page is not clicked twice, and the command is not resent while it loads.
        buy.tick(results(5,"Enchanted Coal Block","Enchanted Coal"),false,actions,5000,200);
        assertEquals(2,actions.serverEffects().size());
        buy.tick(product(100),false,actions,5000,300);
        assertTrue(actions.serverEffects().contains("click:10"));
    }

    @Test void aSearchWithoutTheExactNameBlocksAfterRetries() {
        var actions=new RecordingActions();var buy=buy(1100);
        BazaarInstantBuy.Result result=null;
        for(long t=0;t<40_000;t+=1000){result=buy.tick(results(5,"Enchanted Coal Block"),false,actions,5000,t);if(result!=BazaarInstantBuy.Result.WAITING)break;}
        assertEquals(BazaarInstantBuy.Result.BLOCKED,result);
        assertTrue(buy.failure().contains("exact name"),buy.failure());
        assertTrue(actions.serverEffects().stream().noneMatch(e->e.startsWith("click")));
    }
}
