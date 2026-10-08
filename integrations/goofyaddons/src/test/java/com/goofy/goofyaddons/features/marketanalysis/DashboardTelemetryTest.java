package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DashboardTelemetryTest {
    @Test void explicitOptInPublishesWhileStoppedAndThrottlesOutstandingWork() {
        var cfg=new GoofyConfig();long[] now={10000};var calls=new AtomicInteger();var pending=new CompletableFuture<JsonObject>();
        var publisher=new DashboardTelemetry(()->cfg,JsonObject::new,()->now[0],(endpoint,body)->{calls.incrementAndGet();return pending;});
        publisher.tick();assertEquals(0,calls.get());cfg.marketAnalysis.dashboardEnabled=true;
        publisher.tick();publisher.tick();assertEquals(1,calls.get());now[0]+=5000;publisher.tick();assertEquals(1,calls.get());
        pending.complete(new JsonObject());publisher.tick();assertEquals(2,calls.get());
    }
    @Test void disabledDashboardCancelsWorkAndFailuresCannotEscapeTradingTick() {
        var cfg=new GoofyConfig();cfg.marketAnalysis.dashboardEnabled=true;var pending=new CompletableFuture<JsonObject>();
        var publisher=new DashboardTelemetry(()->cfg,JsonObject::new,()->10000,(endpoint,body)->pending);
        publisher.tick();cfg.marketAnalysis.dashboardEnabled=false;publisher.tick();assertTrue(pending.isCancelled());
        cfg.marketAnalysis.dashboardEnabled=true;
        var broken=new DashboardTelemetry(()->cfg,()->{throw new IllegalStateException("snapshot unavailable");},()->10000,(endpoint,body)->pending);
        assertDoesNotThrow(broken::tick);
    }
}
