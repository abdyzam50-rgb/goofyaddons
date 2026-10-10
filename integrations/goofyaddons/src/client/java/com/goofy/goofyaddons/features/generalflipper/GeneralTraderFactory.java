package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.menu.LiveActions;
import com.goofy.goofyaddons.menu.LiveWorld;
import com.goofy.goofyaddons.utils.ActionDelay;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import com.google.gson.JsonObject;

/** Client composition boundary. The engine itself never locates these live services. */
public final class GeneralTraderFactory {
    private GeneralTraderFactory() {}

    public static GeneralFlipper create(Supplier<FeatureManager> manager) {
        java.util.Objects.requireNonNull(manager);
        var repository = new JsonGeneralOrderRepository(() -> com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.path(com.goofy.goofyaddons.features.account.AccountStorage.GENERAL_ORDERS));
        var engine = new GeneralFlipper(new LiveWorld(), new LiveActions(), repository, new GeneralFlipper.Services() {
            @Override public CapitalManager capital() {return CapitalManager.INSTANCE;}
            @Override public GeneralSettings settings() {return GoofyConfig.INSTANCE.general;}
            @Override public double taxPercentage() {return GoofyConfig.INSTANCE.bazaarTaxPercentage;}
            @Override public boolean automaticSelection() {return GoofyConfig.INSTANCE.marketAnalysis.automaticSelection;}
            @Override public long actionDelay() {return ActionDelay.next();}
            @Override public double purse() {return new ScoreboardUtils().getPurse();}
            @Override public JsonObject latestQuotes() {return BazaarApi.latestFresh();}
            @Override public CompletableFuture<JsonObject> fetchQuotes() {return BazaarApi.fetch();}
            @Override public Set<String> excludedProducts() {return BazaarAccess.instance().excluded(manager.get().observedUnlocks());}
            @Override public Map<String,Integer> skillLevels() {return manager.get().observedSkills();}
            @Override public void excludeProduct(String id,String reason) {
                BazaarAccess.instance().deny(id,reason);manager.get().invalidateMarketReport();
            }
            @Override public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report recommendations() {
                return automaticSelection()?manager.get().automaticReport():manager.get().marketReport();
            }
            @Override public void placed(GeneralPosition position) {
                ProfitTracker.INSTANCE.beginExecution(position.tradeId,GeneralFlipper.OWNER,position.item.id(),position.item.id(),position.quantity,position.quantity,position.placedAt,
                        position.sellPrice*position.quantity*(1-taxPercentage()/100)-position.cost(),position.forecast);
            }
            @Override public com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast forecast(String item,int batch) {
                return manager.get().executionForecast(item,item,batch);
            }
            @Override public void finished(GeneralPosition position) {ProfitTracker.INSTANCE.retire(position.tradeId);}
            @Override public void acquire(GeneralPosition position) {
                ProfitTracker.INSTANCE.acquire(position.tradeId,GeneralFlipper.OWNER,position.item.name(),position.tradeId+":buy",
                        position.quantity,position.purchasePriceKnown?position.cost():null);
            }
            @Override public void sell(GeneralPosition position,int units,Double proceeds) {
                ProfitTracker.INSTANCE.sell(position.tradeId,GeneralFlipper.OWNER,position.item.name(),position.saleEvent,units,proceeds);
            }
            @Override public void safetyPause(String reason) {manager.get().safetyPause(reason);}
            @Override public void event(String level,String type,Map<String,?> details) {Diagnostics.event(level,type,details);}
            @Override public void failure(String type,Throwable failure) {Diagnostics.failure(type,failure);}
            @Override public Map<String,Object> diagnosticContext() {return Diagnostics.detailedSnapshot();}
        });
        ChatHook.onMessage("[Bazaar]", engine::onNotice);
        ChatHook.onMessage("", engine::onSlowdown);
        return engine;
    }
}
