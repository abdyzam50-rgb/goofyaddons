package com.goofy.goofyaddons.features.bookflipper.helper;

/** Profit targets govern entry; already-held books may exit at a verified current price. */
public final class BookPricePolicy {
    private BookPricePolicy() {}
    public record Check(boolean allowed,String reason,Double estimatedCost,Double estimatedNet) {}
    public static Check check(boolean sale,boolean quotesFresh,double price,double reservedUnitCost,
                              int inputUnits,double expectedExit,double tax,double minimumProfit) {
        if (!quotesFresh) return reject("quotes-stale");
        if (!Double.isFinite(price) || price<=0) return reject("price-unreadable");
        if (!Double.isFinite(reservedUnitCost) || reservedUnitCost<0 || inputUnits<=0
                || !Double.isFinite(tax) || tax<0 || tax>=100
                || !Double.isFinite(minimumProfit) || minimumProfit<0) return reject("invalid-cost-or-policy");
        double cost=Math.max(sale?0:price,reservedUnitCost)*inputUnits;
        double exit=sale?price:expectedExit;
        if (!Double.isFinite(exit) || exit<=0) return reject("exit-quote-unreadable");
        double net=exit*(1-tax/100)-cost;
        if (!Double.isFinite(cost) || !Double.isFinite(net)) return reject("invalid-cost-or-policy");
        if (sale) return new Check(true,"held-book-exit",cost,net);
        boolean allowed=net>0 && net>=minimumProfit;
        return new Check(allowed,allowed?"profitable-entry":"entry-below-profit-target",cost,net);
    }
    private static Check reject(String reason) { return new Check(false,reason,null,null); }
}
