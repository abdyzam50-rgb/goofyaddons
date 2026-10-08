package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol;
import com.goofy.goofyaddons.features.profit.ExecutionLedger;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.menu.LiveActions;
import com.goofy.goofyaddons.menu.LiveWorld;
import com.goofy.goofyaddons.utils.ActionDelay;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Live composition only. Constructing the executor itself does not subscribe or resolve paths. */
public final class BookTraderFactory {
    private BookTraderFactory() {}
    public static BazaarFlipper create(Supplier<FeatureManager> manager) {
        java.util.Objects.requireNonNull(manager);
        BookOrderRepository repository=new BookOrderRepository() {
            private BookJournal delegate;
            private java.nio.file.Path delegatePath;
            private BookJournal journal() {
                // Resolved under the profile trading pinned, so positions never cross profiles.
                var path=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE.path(com.goofy.goofyaddons.features.account.AccountStorage.BOOK_ORDERS);
                if(delegate==null || !path.equals(delegatePath)){delegate=new BookJournal(path);delegatePath=path;}
                return delegate;
            }
            public List<BookPosition> read() throws Exception {return journal().read();}
            public void backupVerified(List<BookPosition> expected) throws Exception {journal().backupVerified(expected);}
            public void reconcileVerified(List<BookPosition> expected,Set<String> present) throws Exception {journal().reconcileVerified(expected,present);}
            public void writeTracked(List<BookPosition> plans,Set<String> exposed) throws Exception {journal().writeTracked(plans,exposed);}
            public void write(List<BookPosition> positions) throws Exception {journal().write(positions);}
        };
        BookAccounting accounting=new BookAccounting() {
            public void acquire(String id,String engine,String item,String event,int units,Double cost) {ProfitTracker.INSTANCE.acquire(id,engine,item,event,units,cost);}
            public void sell(String id,String engine,String item,String event,int units,Double proceeds) {ProfitTracker.INSTANCE.sell(id,engine,item,event,units,proceeds);}
            public void recoverHoldings(String id,String engine,String item,int units) {ProfitTracker.INSTANCE.recoverHoldings(id,engine,item,units);}
            public void retire(String id) {ProfitTracker.INSTANCE.retire(id);}
            public void writeOff(String id,String engine,String item,String event,int units) {ProfitTracker.INSTANCE.writeOff(id,engine,item,event,units);}
            public Double knownCost(String id,int units) {return ProfitTracker.INSTANCE.knownCost(id,units);}
            public Double openCost(String id) {return ProfitTracker.INSTANCE.openCost(id);}
            public void beginExecution(String id,String engine,String input,String output,int units,int batch,long started,Double expectedProfit,ExecutionLedger.Forecast forecast) {
                ProfitTracker.INSTANCE.beginExecution(id,engine,input,output,units,batch,started,expectedProfit,forecast);
            }
        };
        var engine=new BazaarFlipper(new LiveWorld(),new LiveActions(),repository,new BookServices() {
            public BookSettings settings() {
                var c=GoofyConfig.INSTANCE;
                return new BookSettings(c.books,c.bazaarTaxPercentage,c.minNetProfit,c.maxTradingCapital,c.maxActiveBooks,
                        c.firstPage,c.secondPage,c.marketAnalysis.automaticSelection,c.liquidateStaleBooks,c.bookStaleSeconds,
                        c.bookOrderRecheckSeconds,c.maxBookHoldingSeconds,c.maxBookDrawdownPercentage,c.maxBookReprices,c.bookRepriceCooldownSeconds);
            }
            public CapitalManager capital() {return CapitalManager.INSTANCE;}
            public BookAccounting accounting() {return accounting;}
            public double purse() {return new ScoreboardUtils().getPurse();}
            public int actionDelay() {return ActionDelay.next();}
            public JsonObject latestQuotes() {return BazaarApi.latestFresh();}
            public CompletableFuture<JsonObject> fetchQuotes() {return BazaarApi.fetch();}
            public Map<String,Integer> observedSkills() {return manager.get().observedSkills();}
            public MarketAnalysisProtocol.Report automaticReport() {return manager.get().automaticReport();}
            public ExecutionLedger.Forecast executionForecast(String input,String output,int batch) {return manager.get().executionForecast(input,output,batch);}
            public void invalidateMarketReport() {manager.get().invalidateMarketReport();}
            public void safetyPause(String reason) {manager.get().safetyPause(reason);}
            public void event(String level,String type,Map<String,?> details) {Diagnostics.event(level,type,details);}
            public void failure(String type,Throwable failure) {Diagnostics.failure(type,failure);}
            public Map<String,Object> diagnosticContext() {return Diagnostics.detailedSnapshot();}
        });
        ChatHook.onMessage("Sold",engine::soldNotice);
        ChatHook.onMessage("filled",engine::handleFilledMessage);
        ChatHook.onMessage("Claimed",engine::handleClaimedMessage);
        ChatHook.onMessage("",engine::slowdownNotice);
        return engine;
    }
}
