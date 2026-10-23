package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeneralCalculator {
    public record Candidate(GeneralItem item, int quantity, double bid, double ask, double profit, double score,com.goofy.goofyaddons.features.production.BazaarStrategy strategy) {
        public Candidate(GeneralItem item,int quantity,double bid,double ask,double profit,double score){this(item,quantity,bid,ask,profit,score,com.goofy.goofyaddons.features.production.BazaarStrategy.ORDER_OFFER);}
        public double cost() { return quantity * bid; }
    }

    private GeneralCalculator() {}

    /** Leave an equal share of spendable capital for every remaining General slot. */
    public static double entryBudget(double available,int remainingSlots) {
        return Double.isFinite(available) && available>0 && remainingSlots>0 ? available/remainingSlots : 0;
    }

    public static List<Candidate> calculate(JsonObject products, GeneralSettings settings,
                                            double taxPercentage, double available, int inventoryCapacity) {
        if (products == null || !Double.isFinite(available) || available <= 0 || inventoryCapacity <= 0
                || !Double.isFinite(taxPercentage) || taxPercentage < 0 || taxPercentage >= 100) return List.of();
        List<Candidate> result = new ArrayList<>();
        for (GeneralItem item : settings.items) {
            if(BazaarAccess.MUTATIONS.contains(item.id()))continue;
            try {
                for(var strategy:com.goofy.goofyaddons.features.production.BazaarStrategy.values()){
                    Candidate candidate = evaluate(products, item, settings, taxPercentage, available, inventoryCapacity,strategy);
                    if (candidate != null) result.add(candidate);
                }
            } catch (RuntimeException malformed) {
                // One malformed product must not hide every other allowlisted item.
            }
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return List.copyOf(result);
    }

    /** Fresh observed rates can rank existing, independently validated execution candidates. */
    public static List<Candidate> rankByForecast(List<Candidate> candidates,
            com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report report,long now) {
        if(report==null || !com.goofy.goofyaddons.features.TradingSafety.fresh(report.marketAt(),now))return candidates;
        var rates=new java.util.HashMap<String,Double>();
        for(var r:report.rows())if(r.kind().equals("GENERAL")&&r.configured()&&r.inputId().equals(r.outputId())
                &&Double.isFinite(r.coinsPerHour())&&r.coinsPerHour()>0)rates.put(r.inputId()+":"+r.batch(),r.coinsPerHour());
        return candidates.stream().sorted(Comparator.<Candidate>comparingDouble(c->rates.getOrDefault(c.item().id()+":"+c.quantity(),-1.0))
                .reversed().thenComparing(Comparator.comparingDouble(Candidate::score).reversed())).toList();
    }
    private static Candidate evaluate(JsonObject products, GeneralItem item, GeneralSettings settings,
                                      double taxPercentage, double available, int inventoryCapacity,com.goofy.goofyaddons.features.production.BazaarStrategy strategy) {
        JsonObject product = products.getAsJsonObject(item.id());
        if (product == null) return null;
        Double buyQuote=strategy.buy(product,1),sellQuote=strategy.sell(product,1);
        double bid=buyQuote==null?-1:buyQuote,ask=sellQuote==null?-1:sellQuote;
        JsonObject quick = product.getAsJsonObject("quick_status");
        if (quick == null || bid <= 0 || ask <= bid) return null;
        double flow = com.goofy.goofyaddons.features.market.BazaarQuotes.weeklyFlow(product);
        if (flow < settings.minWeeklyVolume || flow <= 0) return null;
        double net = ask * (1 - taxPercentage / 100) - bid;
        if (net <= 0 || net / bid * 100 < settings.minMarginPercentage) return null;
        // Avoid placing more than about one hour of historical market flow.
        int quantity = (int) Math.min(Math.min(settings.maxItemsPerOrder, inventoryCapacity),
                Math.min(Math.floor(Math.min(settings.maxCoinsPerItem, available) / bid), Math.floor(flow / 168)));
        if (quantity <= 0 || net * quantity < settings.minProfitPerBatch) return null;
        return new Candidate(item, quantity, bid, ask, net * quantity,
                net * quantity * Math.log10(flow + 1) / Math.sqrt(bid * quantity),strategy);
    }
}
