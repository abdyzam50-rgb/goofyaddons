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

    @Test void ignoredProductAndCustomAmountClicksRecoverBeforeAnyPurchaseIntent() {
        var a=new RecordingActions();var b=buy(1100);var product=product(100);
        b.tick(product,false,a,5000,1000);b.tick(product,false,a,5000,2000);b.tick(product,false,a,5000,4000);
        assertEquals(List.of("click:10","click:10"),a.serverEffects());assertTrue(intents.isEmpty());
        var amount=menu(2,"How many do you want?",SlotView.named(16,"Custom Amount",List.of()));
        b.tick(amount,false,a,5000,4100);b.tick(amount,false,a,5000,5000);b.tick(amount,false,a,5000,7200);
        assertEquals(List.of("click:10","click:10","click:16","click:16"),a.serverEffects());assertTrue(intents.isEmpty());
        b.tick(amount,true,a,5000,7300);
        assertEquals(1,intents.size());assertEquals(1,a.serverEffects().stream().filter(x->x.startsWith("sign:")).count());
        assertEquals(BazaarInstantBuy.Result.BOUGHT,b.tick(menu(3,null,item(54,"ENCHANTED_COAL",10)),false,a,4000,7400));
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
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(menu(4,"Confirm Instant Buy"),false,actions,5000,300));
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,buy.tick(menu(4,"Confirm Instant Buy"),false,actions,5000,20_000));
        assertFalse(actions.serverEffects().contains("click:13"));
    }

    MenuSnapshot confirmation(int units,String price) {
        return menu(4,"Confirm Instant Buy",SlotView.named(13,"Custom Amount",
                List.of("Enchanted Coal","","Amount: "+units+"x","","Per unit: 100 coins","Price: "+price+" coins","","Click to buy now!")));
    }

    @Test void confirmsTheInstantBuyOnlyWhenItQuotesTheProductAmountAndAFittingTotal() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(product(100),false,actions,5000,0);
        buy.tick(menu(2,"Enchanted Coal \u279c Instant",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        buy.tick(null,true,actions,5000,200);
        assertEquals(1,intents.size());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(confirmation(10,"1,000"),false,actions,5000,300));
        assertTrue(actions.serverEffects().contains("click:13"));
        // The confirmation screen lingering after the click is not a second, unexpected one.
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(confirmation(10,"1,000"),false,actions,5000,400));
        var redrawn=confirmation(10,"1,000");redrawn=new MenuSnapshot(6,redrawn.title(),true,redrawn.slots());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(redrawn,false,actions,5000,450));
        assertEquals(BazaarInstantBuy.Result.BOUGHT,buy.tick(menu(5,null,item(54,"ENCHANTED_COAL",10)),false,actions,4000,500));
        assertEquals(1,actions.serverEffects().stream().filter(e->e.equals("click:13")).count());
    }

    @Test void aConfirmationAboveTheLimitOrForAnotherAmountIsNotClicked() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(product(100),false,actions,5000,0);
        buy.tick(menu(2,"Enchanted Coal \u279c Instant",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        buy.tick(null,true,actions,5000,200);
        assertEquals(BazaarInstantBuy.Result.BLOCKED,buy.tick(confirmation(10,"1,200"),false,actions,5000,300));
        assertTrue(buy.failure().contains("above"),buy.failure());
        var other=new RecordingActions();var wrong=buy(1100);
        wrong.tick(product(100),false,other,5000,0);
        wrong.tick(menu(2,"Enchanted Coal \u279c Instant",SlotView.named(16,"Custom Amount",List.of())),false,other,5000,100);
        wrong.tick(null,true,other,5000,200);
        assertEquals(BazaarInstantBuy.Result.WAITING,wrong.tick(confirmation(100,"10,000"),false,other,5000,300));
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,wrong.tick(confirmation(100,"10,000"),false,other,5000,20_000));
        assertFalse(actions.serverEffects().contains("click:13")||other.serverEffects().contains("click:13"));
    }

    @Test void readsQuotedUnitPrices() {
        assertEquals(1234.5,BazaarInstantBuy.unitPrice("Enchanted Coal\nPrice per unit: 1,234.5 coins"));
        assertNull(BazaarInstantBuy.unitPrice("Price per unit: free"));
    }

    @Test void aHeldCursorPausesClicksAndBlocksOnlyIfItStays() {
        var actions=new RecordingActions();var buy=buy(1100);
        var none=new MenuSnapshot(1,null,false,menu(1,null).slots());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(none,false,actions,5000,0));
        assertEquals(List.of("command:bz Enchanted Coal"),actions.serverEffects());
        var held=product(100);held=new MenuSnapshot(held.containerId(),held.title(),false,held.slots());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(held,false,actions,5000,100));
        assertEquals(1,actions.serverEffects().size());
        // The server resyncs the cursor and the buy carries on.
        buy.tick(product(100),false,actions,5000,200);
        assertTrue(actions.serverEffects().contains("click:10"));
        var amount=menu(2,"Enchanted Coal \u279c Instant",SlotView.named(16,"Custom Amount",List.of()));
        var heldAmount=new MenuSnapshot(2,amount.title(),false,amount.slots());
        assertEquals(BazaarInstantBuy.Result.WAITING,buy.tick(heldAmount,false,actions,5000,300));
        assertEquals(BazaarInstantBuy.Result.BLOCKED,buy.tick(heldAmount,false,actions,5000,30_000));
        assertTrue(buy.failure().contains("empty cursor"));
        assertFalse(actions.serverEffects().contains("click:16"));
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

    @Test void recognisesTheAmountMenuUnderACutTitle() {
        var actions=new RecordingActions();var buy=buy(1100);
        buy.tick(product(100),false,actions,5000,0);
        buy.tick(menu(2,"Enchanted Coal \u279c Instant",SlotView.named(10,"Buy only one!",List.of()),SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        assertTrue(actions.serverEffects().contains("click:16"));
        assertFalse(actions.serverEffects().contains("click:10")&&actions.serverEffects().indexOf("click:10")>0);
    }

    @Test void presetButtonsBuyOneOrAStackAtTheirQuotedTotal() {
        var actions=new RecordingActions();
        var one=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",1,600,intents::add);
        var amounts=menu(2,"Enchanted Coal \u279c Instant",
                SlotView.named(10,"Buy only one!",List.of("Enchanted Coal","","Amount: 1x","","Price: 485.6 coins","","Click to buy now!")),
                SlotView.named(12,"Buy a stack!",List.of("Enchanted Coal","","Amount: 64x","","Per unit: 466.9 coins","Price: 31,077 coins")),
                SlotView.named(16,"Custom Amount",List.of("Buy Order Quantity")));
        one.tick(product(485.6),false,actions,5000,0);
        one.tick(amounts,false,actions,5000,100);
        assertEquals(List.of("click:10","click:10"),actions.serverEffects());
        assertEquals(1,intents.size());
        assertEquals(BazaarInstantBuy.Result.BOUGHT,one.tick(menu(3,null,item(54,"ENCHANTED_COAL",1)),false,actions,4514.4,200));
        assertEquals(31_077.0,BazaarInstantBuy.quotedTotal("Enchanted Coal\n\nAmount: 64x\n\nPer unit: 466.9 coins\nPrice: 31,077 coins","Enchanted Coal",64));
        assertNull(BazaarInstantBuy.quotedTotal("Enchanted Coal Block\nAmount: 1x\nPrice: 485.6 coins","Enchanted Coal",1));
        assertNull(BazaarInstantBuy.quotedTotal("Enchanted Coal\nAmount: 64x\nPrice: 31,077 coins","Enchanted Coal",1));
        var stack=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",64,30_000,intents::add);
        var other=new RecordingActions();
        stack.tick(product(400),false,other,50_000,0);
        assertEquals(BazaarInstantBuy.Result.BLOCKED,stack.tick(amounts,false,other,50_000,100));
        assertTrue(stack.failure().contains("above"),stack.failure());
        assertEquals(List.of("click:10"),other.serverEffects());
    }
    @Test void unreadablePurseAfterPresetPurchaseWaitsAndNeverRepeatsTheBuy() {
        var actions=new RecordingActions();
        var one=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",1,600,intents::add);
        one.tick(product(485.6),false,actions,5000,1000);
        one.tick(menu(2,"Enchanted Coal → Instant Buy",SlotView.named(10,"Buy only one!",
            List.of("Enchanted Coal","Amount: 1x","Price: 485.6 coins")),
            SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,1100);
        var arrived=menu(2,"Enchanted Coal → Instant Buy",item(54,"ENCHANTED_COAL",1));
        int effects=actions.serverEffects().size();
        for(double unknown:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertEquals(BazaarInstantBuy.Result.WAITING,one.tick(arrived,false,actions,unknown,1200));
            assertNull(one.failure());assertEquals(0,one.spent());
        }
        assertEquals(BazaarInstantBuy.Result.BOUGHT,one.tick(arrived,false,actions,4514.4,1300));
        assertEquals(485.6,one.spent(),0.0001);assertEquals(effects,actions.serverEffects().size());
        assertEquals(1,intents.size());
    }
    @Test void unreadablePurseAfterConfirmationWaitsButRealExcessSpendingStillRequiresReview() {
        var actions=new RecordingActions();var purchase=buy(1100);
        purchase.tick(product(100),false,actions,5000,1000);
        purchase.tick(menu(2,"Amount",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,1100);
        purchase.tick(null,true,actions,5000,1200);
        purchase.tick(confirmation(10,"1,000"),false,actions,5000,1300);
        var arrived=menu(4,null,item(54,"ENCHANTED_COAL",10));
        int effects=actions.serverEffects().size();
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(arrived,false,actions,-1,1400));
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,purchase.tick(arrived,false,actions,3800,1500));
        assertTrue(purchase.failure().contains("more than expected"));
        assertEquals(effects,actions.serverEffects().size());
    }

    @Test void storageBackedPurchaseSubmitsWholeAmountAndCountsVerifiedStoredDeliveries() {
        var actions=new RecordingActions();
        var purchase=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",5000,500_000,intents::add,true);
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(product(100),false,actions,1_000_000,0));
        var amounts=menu(2,"Enchanted Coal \u279c Instant",
                SlotView.named(12,"Buy a stack!",List.of("Enchanted Coal","","Amount: 64x","","Price: 6,400 coins")),
                SlotView.named(14,"Fill my inventory!",List.of("Enchanted Coal","","Amount: 2,240x","","Per unit: 100 coins","Price: 224,000 coins")),
                SlotView.named(16,"Custom Amount",List.of()));
        purchase.tick(amounts,false,actions,1_000_000,100);
        assertEquals(List.of("click:10","click:16"),actions.serverEffects());
        purchase.tick(amounts,true,actions,1_000_000,200);
        assertEquals(5000,purchase.amount());assertEquals(5000,purchase.requested());
        assertEquals(BazaarInstantBuy.Result.BOUGHT,purchase.tick(menu(3,null,item(54,"ENCHANTED_COAL",40)),false,actions,500_000,300,4960));
        assertEquals(500_000,purchase.spent());
        assertEquals(1,actions.serverEffects().stream().filter("sign:5000"::equals).count());
    }
    @Test void storageBackedPurchaseKeepsItsWholeAmountPriceLimit() {
        var actions=new RecordingActions();
        var purchase=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",5000,500_000,intents::add,true);
        purchase.tick(product(100),false,actions,1_000_000,0);
        var amounts=menu(2,"Enchanted Coal \u279c Instant",
                SlotView.named(14,"Fill my inventory!",List.of("Enchanted Coal","","Amount: 2,240x","","Price: 300,000 coins")),
                SlotView.named(16,"Custom Amount",List.of()));
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(amounts,false,actions,1_000_000,100));
        purchase.tick(amounts,true,actions,1_000_000,200);
        assertEquals(BazaarInstantBuy.Result.BLOCKED,purchase.tick(confirmation(5000,"600,000"),false,actions,1_000_000,300));
        assertFalse(actions.serverEffects().contains("click:13"));assertEquals(500_000,purchase.maximumCost());
    }
    @Test void paidOverflowCanBeStoredAndPickedUpInSeveralRoundsWithoutBuyingAgain() {
        var actions=new RecordingActions();var purchase=new BazaarInstantBuy("SUPER_COMPACTOR_3000","Super Compactor 3000",10,1100,intents::add,true);
        var product=menu(1,"Super Compactor 3000",item(13,"SUPER_COMPACTOR_3000",1),SlotView.named(10,"Buy Instantly",List.of("Super Compactor 3000","Price per unit: 100 coins")));
        purchase.tick(product,false,actions,5000,1000);
        var amounts=menu(2,"Amount",SlotView.named(16,"Custom Amount",List.of()));purchase.tick(amounts,false,actions,5000,1100);purchase.tick(amounts,true,actions,5000,1200);
        var full=new ArrayList<SlotView>();for(int i=54;i<86;i++)full.add(item(i,"COBBLE",64));for(int i=86;i<90;i++)full.add(item(i,"SUPER_COMPACTOR_3000",1));
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(menu(3,null,full.toArray(SlotView[]::new)),false,actions,4000,3000));
        assertTrue(purchase.stashNeedsSpace());
        var room=full.subList(0,32).toArray(SlotView[]::new);purchase.roomPrepared(3500);
        purchase.tick(menu(3,null,room),false,actions,4000,5100,4);
        assertEquals(1,actions.serverEffects().stream().filter("command:pickupstash"::equals).count());
        purchase.tick(menu(3,null,full.toArray(SlotView[]::new)),false,actions,4000,5200,4);
        purchase.tick(menu(3,null,full.toArray(SlotView[]::new)),false,actions,4000,6800,4);assertTrue(purchase.stashNeedsSpace());
        purchase.roomPrepared(7000);purchase.tick(menu(3,null,room),false,actions,4000,8600,8);
        var finalItems=new ArrayList<>(full.subList(0,32));finalItems.add(item(86,"SUPER_COMPACTOR_3000",1));finalItems.add(item(87,"SUPER_COMPACTOR_3000",1));
        assertEquals(BazaarInstantBuy.Result.BOUGHT,purchase.tick(menu(3,null,finalItems.toArray(SlotView[]::new)),false,actions,4000,8700,8));
        assertEquals(1000,purchase.spent());assertEquals(2,actions.serverEffects().stream().filter("command:pickupstash"::equals).count());
        assertEquals(1,intents.size());assertEquals(1,actions.serverEffects().stream().filter("sign:10"::equals).count());
    }
    @Test void withoutFillModeALargeAmountStillNeedsTheSpace() {
        var actions=new RecordingActions();
        var purchase=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",5000,500_000,intents::add);
        assertEquals(BazaarInstantBuy.Result.BLOCKED,purchase.tick(product(100,item(54,"COBBLE",64)),false,actions,1_000_000,0));
        assertTrue(actions.serverEffects().isEmpty());
    }

    /** Ticks a ten-coal custom-amount buy up to the moment it is paid for. */
    BazaarInstantBuy paidForTen(RecordingActions actions) {
        var purchase=buy(1100);
        purchase.tick(product(100),false,actions,5000,0);
        purchase.tick(menu(2,"How many do you want?",SlotView.named(16,"Custom Amount",List.of())),false,actions,5000,100);
        purchase.tick(null,true,actions,5000,200);
        return purchase;
    }
    @Test void unitsThatOverflowedIntoTheStashArePickedUpOnceAndThenProven() {
        var actions=new RecordingActions();var purchase=paidForTen(actions);
        var short4=menu(3,null,item(54,"ENCHANTED_COAL",4));
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(short4,false,actions,4000,1000));
        assertFalse(actions.serverEffects().contains("command:pickupstash"),"The inventory gets a moment to update first");
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(short4,false,actions,4000,2000));
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(short4,false,actions,4000,2500));
        assertEquals(1,actions.serverEffects().stream().filter("command:pickupstash"::equals).count());
        assertEquals(BazaarInstantBuy.Result.BOUGHT,purchase.tick(menu(3,null,item(54,"ENCHANTED_COAL",10)),false,actions,4000,3000));
        assertEquals(1000,purchase.spent());
    }
    @Test void aStashThatDoesNotHoldTheMissingUnitsEndsInReview() {
        var actions=new RecordingActions();var purchase=paidForTen(actions);
        var short4=menu(3,null,item(54,"ENCHANTED_COAL",4));
        purchase.tick(short4,false,actions,4000,2000);
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(short4,false,actions,4000,5000));
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,purchase.tick(short4,false,actions,4000,20000));
        assertTrue(purchase.failure().contains("/pickupstash"),purchase.failure());
        assertEquals(1,actions.serverEffects().stream().filter("command:pickupstash"::equals).count());
        assertEquals(1,actions.serverEffects().stream().filter(e->e.startsWith("sign")).count());
    }
    @Test void anUnpaidPurchaseNeverPicksUpTheStash() {
        var actions=new RecordingActions();var purchase=paidForTen(actions);
        purchase.tick(menu(3,null),false,actions,5000,5000);
        assertEquals(BazaarInstantBuy.Result.UNCERTAIN,purchase.tick(menu(3,null),false,actions,5000,20000));
        assertFalse(actions.serverEffects().contains("command:pickupstash"));
    }
    @Test void occupiedSlotsStillAllowStashPickupIntoACompatiblePartialStack() {
        var actions=new RecordingActions();var purchase=paidForTen(actions);
        var full=new ArrayList<SlotView>(List.of(item(54,"ENCHANTED_COAL",4)));
        for(int i=55;i<90;i++)full.add(item(i,"COBBLE",64));
        assertEquals(BazaarInstantBuy.Result.WAITING,purchase.tick(menu(3,null,full.toArray(SlotView[]::new)),false,actions,4000,2000));
        assertTrue(actions.serverEffects().contains("command:pickupstash"));
    }
    @Test void aPresetPurchaseThatOverflowedPicksUpTheStashToo() {
        var actions=new RecordingActions();
        var stack=new BazaarInstantBuy("ENCHANTED_COAL","Enchanted Coal",64,30_000,intents::add);
        stack.tick(product(400),false,actions,50_000,0);
        stack.tick(menu(2,"Enchanted Coal \u279c Instant",
                SlotView.named(12,"Buy a stack!",List.of("Enchanted Coal","","Amount: 64x","","Price: 25,600 coins")),
                SlotView.named(16,"Custom Amount",List.of())),false,actions,50_000,100);
        var short20=menu(3,null,item(54,"ENCHANTED_COAL",20));
        assertEquals(BazaarInstantBuy.Result.WAITING,stack.tick(short20,false,actions,24_400,500));
        assertEquals(BazaarInstantBuy.Result.WAITING,stack.tick(short20,false,actions,24_400,2200));
        assertTrue(actions.serverEffects().contains("command:pickupstash"),actions.serverEffects().toString());
        assertEquals(BazaarInstantBuy.Result.BOUGHT,stack.tick(menu(3,null,item(54,"ENCHANTED_COAL",64)),false,actions,24_400,2500));
    }
}
