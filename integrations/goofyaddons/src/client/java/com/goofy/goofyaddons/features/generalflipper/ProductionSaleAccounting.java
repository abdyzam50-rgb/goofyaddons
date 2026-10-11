package com.goofy.goofyaddons.features.generalflipper;

/** Receipt totals survive partial cancellation/relisting; a repeated receipt event is idempotent. */
final class ProductionSaleAccounting {
    static boolean initialListing(GeneralPosition position) {
        return !position.submitted && position.reprices==0 && position.productionSoldUnits==0;
    }
    static void record(GeneralPosition position,int units,Double proceeds) {
        if(position.saleEvent==null || units<1 || units>position.quantity)
            throw new IllegalArgumentException("Invalid production sale receipt");
        if(position.saleEvent.equals(position.productionSaleEvent))return;
        if(proceeds!=null && (!Double.isFinite(proceeds)||proceeds<0))throw new IllegalArgumentException("Invalid sale proceeds");
        position.productionSoldUnits=Math.addExact(position.productionSoldUnits,units);
        if(position.productionSoldUnits>4096)throw new IllegalArgumentException("Sale units exceed original batch bounds");
        position.productionProceedsUnknown|=proceeds==null;
        position.verifiedProceeds=position.productionProceedsUnknown?null:(position.verifiedProceeds==null?0:position.verifiedProceeds)+proceeds;
        position.productionSaleEvent=position.saleEvent;
    }
    static int totalUnits(GeneralPosition position) {
        return position.completed && position.productionSoldUnits>0?position.productionSoldUnits:position.quantity+position.productionSoldUnits;
    }
    static double minimumPrice(double cost,double minimumProfit,int remainingUnits,Double proceeds,boolean unknown,double tax,double floor) {
        if(unknown || !Double.isFinite(cost)||cost<0 || !Double.isFinite(minimumProfit)||minimumProfit<0
            || remainingUnits<1 || !Double.isFinite(tax)||tax<0||tax>=100||!Double.isFinite(floor)||floor<0
            || proceeds!=null && (!Double.isFinite(proceeds)||proceeds<0))return Double.POSITIVE_INFINITY;
        double netRequired=Math.max(0,cost+minimumProfit-(proceeds==null?0:proceeds));
        return Math.max(floor,netRequired/(remainingUnits*(1-tax/100)));
    }
}
