package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelinePlannerTest {
    private final long now=1_000_000;
    private PipelineAccount account(double budget,int capacity,int books,int general,Set<String> occupied) {
        return new PipelineAccount(now,TradingMode.BOTH,budget,0,0,budget,capacity,books,general,occupied,true,null);
    }
    private MarketAnalysisProtocol.Recommendation route(String kind,String input,String output,double cost,double rank,int units,boolean configured) {
        return new MarketAnalysisProtocol.Recommendation(kind,input+":"+output,input,output,units,1,100,cost,1,rank,3600,"ESTIMATED",configured,"capital","current offer");
    }
    private MarketAnalysisProtocol.Report report(MarketAnalysisProtocol.Recommendation... rows) {
        return new MarketAnalysisProtocol.Report(now,now,now,true,"FRESH","a".repeat(40),rows.length,Map.of(),List.of(rows));
    }
    @Test void comparesBothEnginesAndSkipsUnaffordableLeaderWithoutSpendingItsBudget() {
        var p=PipelinePlanner.build(account(700,30,2,2,Set.of()),report(
                route("GENERAL","EXPENSIVE","EXPENSIVE",1000,300,1,true),
                route("BOOK","ENCHANTMENT_WISDOM_1","ENCHANTMENT_WISDOM_5",400,200,16,true),
                route("GENERAL","COAL","COAL",250,100,10,true)),now);
        assertEquals(List.of("BOOK","GENERAL"),p.next().stream().map(r->r.route().kind()).toList());
        assertEquals(650,p.plannedCapital());assertEquals(50,p.capitalLeft());assertEquals(4,p.inventoryLeft());
        assertEquals("Insufficient spendable capital",p.deferred().getFirst().reason());assertFalse(p.executionAuthority());
    }
    @Test void cannotPlanTwoLevelsOfTheSameBookOrAnAlreadyOccupiedProduct() {
        var p=PipelinePlanner.build(account(1000,30,3,3,Set.of("COAL")),report(
                route("BOOK","ENCHANTMENT_WISDOM_4","ENCHANTMENT_WISDOM_5",100,300,2,true),
                route("BOOK","ENCHANTMENT_WISDOM_3","ENCHANTMENT_WISDOM_5",100,200,4,true),
                route("GENERAL","COAL","COAL",100,100,1,true)),now);
        assertEquals(1,p.next().size());assertEquals(2,p.deferred().size());
        assertTrue(p.deferred().stream().allMatch(d->d.reason().contains("already")));
    }
    @Test void aggregateCapacityAndSeparateEngineLimitsPreventOverAllocation() {
        var p=PipelinePlanner.build(account(1000,3,1,1,Set.of()),report(
                route("GENERAL","A","A",100,400,2,true),route("GENERAL","B","B",100,300,1,true),
                route("BOOK","ENCHANTMENT_A_1","ENCHANTMENT_A_2",100,200,2,true)),now);
        assertEquals(1,p.next().size());assertEquals(List.of("Active position limit","Insufficient inventory capacity"),
                p.deferred().stream().map(PipelinePlanner.Deferred::reason).toList());
    }
    @Test void discoveryDoesNotPretendUnconfiguredRoutesAreReadyForExecution() {
        var p=PipelinePlanner.build(account(1000,30,2,2,Set.of()),report(route("GENERAL","A","A",100,100,1,false)),now);
        assertTrue(p.next().isEmpty());assertEquals(1000,p.capitalLeft());assertTrue(p.deferred().getFirst().reason().contains("Research only"));
    }
    @Test void staleForecastOrUnreadableAccountHasNoQueue() {
        var r=report(route("GENERAL","A","A",100,100,1,true));
        assertEquals("WAITING",PipelinePlanner.build(account(1000,30,2,2,Set.of()),r,now+61000).status());
        var unreadable=new PipelineAccount(now,TradingMode.BOTH,null,0,0,0,0,1,1,Set.of(),false,"Purse is unreadable");
        assertTrue(PipelinePlanner.build(unreadable,r,now).next().isEmpty());
    }
    private MenuSnapshot inventory(SlotView item) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<36;i++)slots.add(SlotView.empty(i,true,i));
        if(item!=null){if(item.containerSlot()<36)slots.set(item.index(),item);else slots.add(item);}
        return new MenuSnapshot(0,null,true,slots);
    }
    @Test void accountUsesRealSpendableCapitalAndLeavesItsLedgerUntouched() {
        var capital=new CapitalManager();capital.configure(1000,200);capital.restore("general","OPEN",300,true);
        var a=PipelineAccount.capture(now,TradingMode.BOTH,capital,900,inventory(null),2,3);
        assertEquals(400,a.available());assertEquals(300,a.pending());assertEquals(2,a.generalSlots());assertEquals(32,a.inventoryCapacity());
        assertEquals(300,capital.committed());assertEquals(300,capital.pending());
        var incomplete=new MenuSnapshot(0,null,true,List.of());
        assertFalse(PipelineAccount.capture(now,TradingMode.BOTH,capital,900,incomplete,2,3).ready());
    }
    @Test void armorEnchantmentsDoNotExcludeBooksButAnActualHeldBookDoes() {
        var capital=new CapitalManager();capital.configure(1000,0);
        var armor=new SlotView(36,true,36,false,"Armor","Armor",List.of(),"ARMOR",Map.of("ultimate_wisdom",5),1,1);
        assertFalse(PipelineAccount.capture(now,TradingMode.BOTH,capital,1000,inventory(armor),2,2).excludedProducts().contains("ENCHANTMENT_ULTIMATE_WISDOM"));
        var book=SlotView.enchantedBook(0,true,0,"ultimate_wisdom",1,List.of(),"Wisdom I");
        assertTrue(PipelineAccount.capture(now,TradingMode.BOTH,capital,1000,inventory(book),2,2).excludedProducts().contains("ENCHANTMENT_ULTIMATE_WISDOM"));
    }
    @Test void aNewReservationInvalidatesAnOldRecommendationOnTheNextPreview() {
        var r=report(route("GENERAL","A","A",100,100,1,true));
        assertEquals(1,PipelinePlanner.build(account(1000,30,2,2,Set.of()),r,now).next().size());
        assertTrue(PipelinePlanner.build(account(1000,30,2,2,Set.of("A")),r,now).next().isEmpty());
    }
}
