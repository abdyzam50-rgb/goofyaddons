package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class QuoteWaitRecoveryTest {
    @Test void stalledBookSelectionRequestCanExpireAndRetry(){
        long[] now={1000};var requests=new ArrayList<CompletableFuture<JsonObject>>();
        var calculator=new FlipCalculator(()->{var f=new CompletableFuture<JsonObject>();requests.add(f);return f;},Runnable::run,
                ()->new FlipCalculator.Inputs(List.of(),1.25,0),()->now[0]);
        calculator.Refresh();assertTrue(calculator.isRunning());now[0]=16000;
        assertFalse(calculator.isRunning());assertTrue(requests.getFirst().isCancelled());assertTrue(calculator.lastProblem().contains("timed out"));
        calculator.Refresh();assertEquals(2,requests.size());assertTrue(calculator.isRunning());
        var quote=new JsonObject();quote.addProperty("lastUpdated",now[0]);quote.add("products",new JsonObject());
        requests.getLast().complete(quote);assertFalse(calculator.isRunning());assertNull(calculator.lastProblem());
    }
    @Test void stalledOutbidMonitoringRequestDoesNotDisableLaterChecks(){
        long[] now={1000};var requests=new ArrayList<CompletableFuture<JsonObject>>();
        var monitor=new BazaarMonitor(()->{var f=new CompletableFuture<JsonObject>();requests.add(f);return f;},Runnable::run,()->now[0]);
        monitor.add(new Book("ENCHANTMENT_ULTIMATE_WISE",1,5,"Ultimate Wise",0,0),100,false);monitor.start();
        now[0]=21000;monitor.onTick();assertEquals(1,requests.size());
        now[0]=36000;monitor.onTick();assertTrue(requests.getFirst().isCancelled());
        now[0]=41000;monitor.onTick();assertEquals(2,requests.size());assertFalse(requests.getLast().isCancelled());
    }
}
