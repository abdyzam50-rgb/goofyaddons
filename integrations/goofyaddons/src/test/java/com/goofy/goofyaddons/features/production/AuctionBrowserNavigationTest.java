package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionBrowserNavigationTest {
    MenuSnapshot menu(int id,String title,SlotView... actual){
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:actual)slots.set(s.index(),s);return new MenuSnapshot(id,title,true,slots);
    }
    SlotView control(int slot,String name,String selected){return SlotView.named(slot,name,List.of("▶ "+selected));}
    SlotView item(int slot,String product,String lore){return new SlotView(slot,false,slot,false,"Target","Target",List.of(lore),product,null,1,64);}
    AuctionBrowserNavigation browser(){return new AuctionBrowserNavigation("Target",s->"TARGET".equals(s.customId()));}
    @Test void capturedCoopRootOpensTheBrowserWithoutTouchingCreateOrBids()throws Exception {
        var captured=CompactorClearanceTest.captured("auction-coop-root.json");var b=browser();var actions=new RecordingActions();
        assertEquals(AuctionBrowserNavigation.Result.WAITING,b.tick(captured,false,actions,1000));
        assertEquals(List.of("click:11"),actions.performed());
    }
    @Test void binAndSortAreVerifiedBeforeSearchAndLowestMatchingResultIsSelected() {
        var b=browser();var a=new RecordingActions();
        b.tick(menu(1,null),false,a,1000);
        b.tick(menu(2,"Auction House",control(11,"Auctions Browser","")),false,a,1100);
        var filter=control(50,"BIN Filter","Show All");var sort=control(49,"Sort","Highest Price");var search=SlotView.named(48,"Search",List.of());
        b.tick(menu(3,"Auctions Browser",filter,sort,search),false,a,1200);
        filter=control(50,"BIN Filter","BIN Only");b.tick(menu(3,"Auctions Browser",filter,sort,search),false,a,1300);
        sort=control(49,"Sort","Lowest Price");b.tick(menu(3,"Auctions Browser",filter,sort,search),false,a,1400);
        b.tick(menu(3,"Auctions Browser",filter,sort,search),true,a,1500);
        var results=menu(4,"Auctions Browser",filter,sort,search,item(10,"TARGET","Buy it now: 2,000 coins"),
            item(11,"WRONG_TARGET","Buy it now: 1 coins"),item(12,"TARGET","Buy it now: 1,000 coins"),item(13,"TARGET","Starting bid: 2 coins"));
        b.tick(results,false,a,1600);assertEquals(AuctionBrowserNavigation.Result.READY,b.tick(results,false,a,2200));
        assertEquals(12,b.selected().index());assertEquals(1000,b.price());
        assertEquals(List.of("command:ah","click:11","click:50","click:49","click:48","sign:Target"),a.serverEffects());
    }
    @Test void unverifiedControlsDoNotSelectOrBuyAnything() {
        var a=new RecordingActions();var b=browser();
        assertEquals(AuctionBrowserNavigation.Result.BLOCKED,b.tick(menu(1,"Auctions Browser",item(10,"TARGET","Buy it now: 1 coins")),false,a,1000));
        assertTrue(a.serverEffects().isEmpty());assertNull(b.selected());
    }
    @Test void inactiveBinOptionInLoreIsNotMistakenForSelectedBinMode() {
        var filter=SlotView.named(50,"BIN Filter",List.of("▶ Show All","BIN Only","Auctions Only"));
        assertFalse(AuctionBrowserNavigation.selected(filter,"BIN Only"));
        assertNull(AuctionBrowserNavigation.binPrice(item(10,"TARGET","Starting bid: 1,000 coins")));
    }
    @Test void expectedSearchSignCanBeWrittenWithTransientCarriedControlButResultCursorIsStillChecked()throws Exception {
        var b=browser();var a=new RecordingActions();
        var ready=CompactorClearanceTest.captured("auction-browser-controls.json");
        b.tick(ready,false,a,1000);
        var sign=new MenuSnapshot(1,"Edit Sign Message",false,ready.slots());
        assertEquals(AuctionBrowserNavigation.Result.WAITING,b.tick(sign,true,a,1100));
        assertEquals(AuctionBrowserNavigation.Result.WAITING,b.tick(sign,true,a,1200));
        assertEquals(List.of("click:48","sign:Target"),a.serverEffects());
        assertEquals(AuctionBrowserNavigation.Result.BLOCKED,b.tick(new MenuSnapshot(1,"Auctions Browser",false,ready.slots()),false,a,1300));
        assertNull(b.selected());
    }
    @Test void coflnetIsAPriceGuardRatherThanASourceOfAuctionIds() {
        var q=new AuctionPricing.Quote("TARGET",1000,1100L,1000);
        assertDoesNotThrow(()->AuctionPricing.validateObserved(q,"TARGET",999,1100));
        for(double price:new double[]{500,1200,Double.NaN})assertThrows(IllegalArgumentException.class,()->AuctionPricing.validateObserved(q,"TARGET",price,1100));
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.validateObserved(q,"WRONG",1000,1100));
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.validateObserved(q,"TARGET",1000,301001));
        assertThrows(IllegalArgumentException.class,()->AuctionPricing.validateObserved(null,"TARGET",1000,1100));
    }
}
