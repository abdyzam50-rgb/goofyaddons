package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.features.account.AccountStorage;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One production-owned order using the ordinary trader's placement, claim and receipt rules. */
public final class ProductionBazaarOrders {
    public record Result(boolean done,boolean failed,int units,double cost,Double proceeds,String reason){}
    private final GeneralFlipper engine;
    private Object scope;
    private void bind(){var current=AccountStorage.INSTANCE.pinned();if(!Objects.equals(scope,current)){engine.resetProductionAccount();scope=current;}}
    public ProductionBazaarOrders(){
        var settings=new GeneralSettings();settings.items=List.of();settings.maxReprices=0;settings.orderTimeoutSeconds=180;
        engine=new GeneralFlipper(new LiveWorld(),new LiveActions(),new JsonGeneralOrderRepository(()->AccountStorage.INSTANCE.path(AccountStorage.CRAFT_ORDERS)),new GeneralFlipper.Services(){
            public boolean productionOnly(){return true;}
            public CapitalManager capital(){return CapitalManager.INSTANCE;}
            public GeneralSettings settings(){return settings;}
            public double taxPercentage(){return com.goofy.goofyaddons.config.GoofyConfig.INSTANCE.bazaarTaxPercentage;}
            public boolean automaticSelection(){return false;}
            public long actionDelay(){return com.goofy.goofyaddons.utils.ActionDelay.next();}
            public double purse(){return new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse();}
            public JsonObject latestQuotes(){return BazaarApi.latestFresh();}
            public CompletableFuture<JsonObject> fetchQuotes(){return BazaarApi.fetch();}
            public void acquire(GeneralPosition p){}
            public void sell(GeneralPosition p,int units,Double proceeds){}
            public void safetyPause(String reason){}
            public void event(String level,String type,Map<String,?> details){com.goofy.goofyaddons.diagnostics.Diagnostics.event(level,"production."+type,details);}
            public void failure(String type,Throwable error){com.goofy.goofyaddons.diagnostics.Diagnostics.failure("production."+type,error);}
            public Map<String,Integer> skillLevels(){return FeatureManager.INSTANCE.observedSkills();}
        });
        com.goofy.goofyaddons.event.ChatHook.onMessage("[Bazaar]",engine::onNotice);
        com.goofy.goofyaddons.event.ChatHook.onMessage("",engine::onSlowdown);
    }
    public boolean queue(String id,String name,int units,double price,boolean buy,double limit){
        bind();engine.start();return engine.enqueueProduction(id,name,units,price,buy,limit);
    }
    public Result tick(boolean ownsMenu){
        engine.poll();if(ownsMenu && engine.needsMenu())engine.onTick();
        var p=engine.productionPosition();
        if(p==null)return new Result(false,true,0,0,null,"Production order journal is missing");
        boolean done=p.completed || p.productionBuy && p.stage==GeneralPosition.Stage.INVENTORY;
        return new Result(done,engine.productionFailed(),p.productionBuy && p.completed?0:p.quantity,p.productionBuy && !p.completed?p.cost():0,p.verifiedProceeds,engine.activity());
    }
    public boolean recoverBuy(){bind();return engine.recoverProductionBuy();}
    public com.goofy.goofyaddons.features.production.ProductionJobs.Job recoveryEvidence(String account) {
        var p=engine.productionPosition();
        if(p==null || !p.productionBuy || !p.purchasePriceKnown || !(p.completed || p.stage==GeneralPosition.Stage.INVENTORY))
            throw new IllegalStateException("Claim/cancellation has not been verified");
        return new com.goofy.goofyaddons.features.production.ProductionJobs.Job(p.tradeId,"recovered:bazaar:"+p.item.id(),account,
            com.goofy.goofyaddons.features.production.ProductionJobs.State.REVIEW,1,-1,0,0,p.completed?0:p.cost(),null,null,
            "Verified buy-order recovery: "+(p.completed?0:p.quantity)+" units in inventory at "+p.unitCost+" coins/unit; outstanding order removed. Inspect these inputs before acknowledging; no sale or profit assumed.");
    }
    public void poll(){bind();if(engine.hasRetainedPositions())engine.poll();}
    public boolean needsMenu(){return engine.needsMenu();}
    public Map<String,Object> diagnosticState(){return engine.diagnosticState();}
    public boolean acknowledge(){return engine.consumeProduction();}
    public boolean retained(){if(AccountStorage.INSTANCE.pinned()==null)return false;bind();engine.restoreBudget();return engine.hasRetainedPositions()||engine.hasStateError();}
    public void stop(){engine.stop();}
}
