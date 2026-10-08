package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.profit.ExecutionLedger;

/** Explicit receipt/ownership effects; no singleton or filesystem lookup in the engine. */
public interface BookAccounting {
    void acquire(String id,String engine,String item,String event,int units,Double cost);
    void sell(String id,String engine,String item,String event,int units,Double proceeds);
    void recoverHoldings(String id,String engine,String item,int units);
    void retire(String id);
    void writeOff(String id,String engine,String item,String event,int units);
    Double knownCost(String id,int units);
    Double openCost(String id);
    void beginExecution(String id,String engine,String input,String output,int units,int batch,long started,
                        Double expectedProfit,ExecutionLedger.Forecast forecast);
}
