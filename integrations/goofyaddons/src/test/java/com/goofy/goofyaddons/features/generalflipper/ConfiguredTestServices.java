package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/** Compatibility configuration for the existing log-derived scenarios; no live effects. */
class ConfiguredTestServices implements GeneralFlipper.Services {
    private final CapitalManager capital = new CapitalManager();
    ConfiguredTestServices() {capital.configure(GoofyConfig.INSTANCE.maxTradingCapital,GoofyConfig.INSTANCE.purseReserve);}
    public CapitalManager capital() {return capital;}
    public GeneralSettings settings() {return GoofyConfig.INSTANCE.general;}
    public double taxPercentage() {return GoofyConfig.INSTANCE.bazaarTaxPercentage;}
    public boolean automaticSelection() {return GoofyConfig.INSTANCE.marketAnalysis.automaticSelection;}
    public long actionDelay() {return 51;}
    public double purse() {return -1;}
    public JsonObject latestQuotes() {return null;}
    public CompletableFuture<JsonObject> fetchQuotes() {return new CompletableFuture<>();}
    public void acquire(GeneralPosition position) {}
    public void sell(GeneralPosition position,int units,Double proceeds) {}
    public void safetyPause(String reason) {}
}
