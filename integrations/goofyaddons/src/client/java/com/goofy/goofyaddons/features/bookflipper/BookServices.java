package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol;
import com.goofy.goofyaddons.features.profit.ExecutionLedger;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Required live effects plus optional reporting. Implementations belong to the composition layer. */
public interface BookServices {
    BookSettings settings();
    CapitalManager capital();
    BookAccounting accounting();
    double purse();
    int actionDelay();
    JsonObject latestQuotes();
    CompletableFuture<JsonObject> fetchQuotes();
    Map<String,Integer> observedSkills();
    MarketAnalysisProtocol.Report automaticReport();
    ExecutionLedger.Forecast executionForecast(String input,String output,int batch);
    void invalidateMarketReport();
    void safetyPause(String reason);
    default void event(String level,String type,Map<String,?> details) {}
    default void failure(String type,Throwable failure) {}
    default Map<String,Object> diagnosticContext() {return Map.of();}
}
