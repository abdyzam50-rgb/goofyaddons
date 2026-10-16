package com.goofy.goofyaddons.features.marketanalysis;

/** Compare feasible entries on net coins/hour; conserve capital when forecasts tie. */
public final class AllModeSelection {
    private AllModeSelection() {}
    public static boolean craftWins(double coinsPerHour,double capital,MarketAnalysisProtocol.Report other) {
        if(!Double.isFinite(coinsPerHour) || coinsPerHour<=0 || !Double.isFinite(capital) || capital<=0)return false;
        if(other==null || other.rows().isEmpty())return true;
        var head=other.rows().getFirst();
        return coinsPerHour>head.coinsPerHour() || coinsPerHour==head.coinsPerHour() && capital<head.capitalUsed();
    }
}
