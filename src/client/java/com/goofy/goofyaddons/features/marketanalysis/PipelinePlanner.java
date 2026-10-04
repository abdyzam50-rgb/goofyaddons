package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.*;
import java.util.*;

/** Deterministic advisory allocation; never mutates reservations, configuration or execution tasks. */
public final class PipelinePlanner {
    private PipelinePlanner() {}
    public record Proposal(int priority,MarketAnalysisProtocol.Recommendation route) {}
    public record Deferred(String routeKey,String reason) {}
    public record Plan(String status,boolean executionAuthority,PipelineAccount account,List<Proposal> next,
                       List<Deferred> deferred,double plannedCapital,double capitalLeft,int inventoryLeft,
                       String reason,long generatedAt,long expiresAt) {}
    public static Plan build(PipelineAccount account,MarketAnalysisProtocol.Report report,long now) {
        boolean noSlots=account!=null && (account.mode()==TradingMode.BOOKS?account.bookSlots()<=0:
                account.mode()==TradingMode.GENERAL?account.generalSlots()<=0:account.bookSlots()<=0&&account.generalSlots()<=0);
        String blocked=account==null?"Waiting for account observations":!account.ready()?account.reason():noSlots?"Active-position limits reached; waiting for a position to finish":
                account.available()<=0?"No spendable capital for a new position":account.inventoryCapacity()<=0?"No inventory headroom for a new position":
                report==null?"Waiting for a fresh calculator forecast":!TradingSafety.fresh(report.marketAt(),now)?"Calculator forecast expired":null;
        if(blocked!=null)return new Plan("WAITING",false,account,List.of(),List.of(),0,account==null?0:account.available(),
                account==null?0:account.inventoryCapacity(),blocked,now,now);
        double remaining=account.available();int capacity=account.inventoryCapacity(),books=account.bookSlots(),general=account.generalSlots();
        var occupied=new HashSet<>(account.excludedProducts());var next=new ArrayList<Proposal>();var deferred=new ArrayList<Deferred>();
        var ranked=report.rows().stream().sorted(Comparator.comparingDouble(MarketAnalysisProtocol.Recommendation::coinsPerHour)
                .reversed().thenComparingDouble(MarketAnalysisProtocol.Recommendation::capitalUsed)
                .thenComparing(MarketAnalysisProtocol.Recommendation::routeKey)).toList();
        for(var route:ranked) {
            boolean book=route.kind().equals("BOOK");
            String product=book?route.inputId().substring(0,route.inputId().lastIndexOf('_')):route.inputId();
            String reason=!route.configured()?"Research only: route is outside configured lists":
                    book && account.mode()==TradingMode.GENERAL || !book && account.mode()==TradingMode.BOOKS?"Engine disabled by trading mode":
                    occupied.contains(product) || occupied.contains(route.inputId()) || occupied.contains(route.outputId())?"Product already held, reserved or planned":
                    (book?books:general)<=0?"Active position limit":route.capitalUsed()>remaining?"Insufficient spendable capital":
                    route.inputUnits()>capacity?"Insufficient inventory capacity":null;
            if(reason!=null){deferred.add(new Deferred(route.routeKey(),reason));continue;}
            next.add(new Proposal(next.size()+1,route));remaining-=route.capitalUsed();capacity-=route.inputUnits();
            occupied.add(product);occupied.add(route.inputId());occupied.add(route.outputId());if(book)books--;else general--;
        }
        return new Plan("READY",false,account,List.copyOf(next),List.copyOf(deferred),account.available()-remaining,remaining,capacity,
                next.isEmpty()?report.rows().isEmpty()?"No qualifying routes in the calculator report":"All reported routes were deferred":
                remaining>0?"Remaining coins do not fit another reported eligible batch":"Spendable budget allocated in this preview",
                now,report.marketAt()+60_000);
    }
}
