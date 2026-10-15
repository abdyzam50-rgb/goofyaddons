package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.generalflipper.GeneralCalculator;
import com.goofy.goofyaddons.features.market.BazaarQuotes;
import com.goofy.goofyaddons.features.production.BazaarStrategy;
import com.google.gson.JsonObject;
import java.util.*;

/** Local estimates for configured routes only; no companion or automatic catalog expansion. */
public final class PresetMarketReport {
    private PresetMarketReport() {}
    public static MarketAnalysisProtocol.Report build(GoofyConfig cfg,JsonObject market,double available,int capacity,Map<String,Integer> skills,long now) {
        if(market==null || !market.has("lastUpdated") || !com.goofy.goofyaddons.features.TradingSafety.fresh(market.get("lastUpdated").getAsLong(),now))return null;
        var products=market.getAsJsonObject("products");var rows=new ArrayList<MarketAnalysisProtocol.Recommendation>();
        for(var c:GeneralCalculator.calculate(products,cfg.general,cfg.bazaarTaxPercentage,available,capacity))
            add(rows,"GENERAL",c.item().id(),c.item().id(),c.quantity(),c.quantity(),c.cost(),c.profit(),c.strategy(),products,0);
        for(var f:FlipCalculator.calculate(products,cfg.books,cfg.bazaarTaxPercentage,cfg.minNetProfit)) {
            var b=f.book();int units=b.getQtyAmount(b.level());
            if(f.totalCost()>available || units>capacity || com.goofy.goofyaddons.features.access.RouteRequirements.book(b.id(),skills)!=null)continue;
            var strategy=BazaarStrategy.of(f.instaBuy(),f.instaSell());
            var quote=strategy.sell(products.getAsJsonObject(b.getLevel(b.sellLevel())),1);
            if(quote!=null)add(rows,"BOOK",b.getLevel(b.level()),b.getLevel(b.sellLevel()),units,1,f.totalCost(),quote*(1-cfg.bazaarTaxPercentage/100)-f.totalCost(),strategy,products,units-1);
        }
        rows.sort(Comparator.comparingDouble(MarketAnalysisProtocol.Recommendation::coinsPerHour).reversed().thenComparingDouble(MarketAnalysisProtocol.Recommendation::capitalUsed));
        long at=market.get("lastUpdated").getAsLong();
        return new MarketAnalysisProtocol.Report(at,at,now,false,"MISSING","local-presets",rows.size(),Map.of(),List.copyOf(rows));
    }
    private static void add(List<MarketAnalysisProtocol.Recommendation> rows,String kind,String input,String output,int units,int batch,double capital,double profit,BazaarStrategy strategy,JsonObject products,int merges) {
        double seconds=10+merges*2.0+waitSeconds(products.getAsJsonObject(input),strategy.instantBuy,units,"sellMovingWeek")
            +waitSeconds(products.getAsJsonObject(output),strategy.instantSell,kind.equals("BOOK")?1:units,"buyMovingWeek");
        if(!Double.isFinite(seconds)||seconds<=0||profit<=0)return;
        rows.add(new MarketAnalysisProtocol.Recommendation(kind,input+":"+output+":"+strategy.name(),input,output,units,batch,profit,capital,
            (kind.equals("BOOK")?1:units)*3600/seconds,profit*3600/seconds,seconds,"ESTIMATED",true,"preset limits and market activity","live preset quotes",
            Map.of("buyMode",strategy.instantBuy?"instant":"order","sellMode",strategy.instantSell?"instant":"offer")));
    }
    private static double waitSeconds(JsonObject product,boolean instant,int units,String flow) {
        if(instant)return 0;
        if(product==null || !product.has("quick_status"))return Double.POSITIVE_INFINITY;
        double weekly=BazaarQuotes.weeklyVolume(product.getAsJsonObject("quick_status"),flow);
        return weekly>0?units*604800/(weekly*.05):Double.POSITIVE_INFINITY;
    }
}
