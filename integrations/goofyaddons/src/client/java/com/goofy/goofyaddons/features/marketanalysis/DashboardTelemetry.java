package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Publishes observed account state to localhost even while trading is stopped. No game actions. */
public final class DashboardTelemetry {
    @FunctionalInterface public interface Transport { CompletableFuture<JsonObject> send(String endpoint, JsonObject body); }
    private final Supplier<GoofyConfig> config;
    private final Supplier<JsonObject> snapshot;
    private final LongSupplier clock;
    private final Transport transport;
    private CompletableFuture<JsonObject> pending;
    private long next;
    public DashboardTelemetry(Supplier<GoofyConfig> config, Supplier<JsonObject> snapshot, LongSupplier clock, Transport transport) {
        this.config=config;this.snapshot=snapshot;this.clock=clock;this.transport=transport;
    }
    public void tick() {
        try {
            var cfg=config.get();
            if(cfg==null || !cfg.marketAnalysis.dashboardEnabled) { if(pending!=null)pending.cancel(true);pending=null;next=0;return; }
            if(pending!=null && !pending.isDone() || clock.getAsLong()<next) return;
            next=clock.getAsLong()+2000;
            pending=transport.send(cfg.marketAnalysis.endpoint,snapshot.get());
        } catch(RuntimeException ignored) { next=clock.getAsLong()+10000; /* Dashboard outages never pause trading. */ }
    }
}
