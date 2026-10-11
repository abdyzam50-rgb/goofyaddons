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
    public record SavedSale(String parent,String product,int units) {}
    public record IngredientOrder(String product,String name,int units,double price,double limit) {}
    public boolean queueBasket(String parent,List<IngredientOrder> basket){
        bind();engine.start();return engine.enqueueProductionBasket(parent,basket);
    }
    /** Only an intact submitted sell journal may be reattached; other recovery remains manual. */
    public List<SavedSale> savedSales(){
        if(AccountStorage.INSTANCE.pinned()==null)return List.of();bind();
        return resumableSales(engine.productionPositions());
    }
    static List<SavedSale> resumableSales(List<GeneralPosition> positions){
        if(positions.isEmpty() || positions.stream().anyMatch(p->p.productionBuy || p.productionParent==null
            || !p.submitted || p.cancelRequested || p.stage!=GeneralPosition.Stage.SELL_ORDER
            || (p.completed ? p.verifiedProceeds==null || p.productionProceedsUnknown : p.settlementPending)))return List.of();
        return positions.stream().map(p->new SavedSale(p.productionParent,p.item.id(),ProductionSaleAccounting.totalUnits(p))).toList();
    }
    public void resumeSavedSales(){bind();engine.start();}
    public record Result(boolean done,boolean failed,int units,double cost,Double proceeds,String reason,boolean rejected){
        public Result(boolean done,boolean failed,int units,double cost,Double proceeds,String reason){this(done,failed,units,cost,proceeds,reason,false);}
    }
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
            public double productionMinimumSellPrice(GeneralPosition position){
                try {
                    var parent=FeatureManager.INSTANCE.crafting().productionJobs().find(position.productionParent).orElseThrow();
                    if(!parent.account().equals(new LiveWorld().username()))return Double.POSITIVE_INFINITY;
                    Double knownCost=com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE.knownCost(parent.id(),ProductionSaleAccounting.totalUnits(position));
                    if(knownCost==null)return ProductionSaleAccounting.initialListing(position)?position.minimumSellPrice:Double.POSITIVE_INFINITY;
                    return ProductionSaleAccounting.minimumPrice(Math.max(parent.costBasis()==null?0:parent.costBasis(),knownCost),com.goofy.goofyaddons.config.GoofyConfig.INSTANCE.craftFlips.minimumProfit,
                        position.quantity,position.verifiedProceeds,position.productionProceedsUnknown,
                        taxPercentage(),position.minimumSellPrice);
                }catch(Exception unavailable){return Double.POSITIVE_INFINITY;}
            }
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
    public boolean queue(String parent,String id,String name,int units,double price,boolean buy,double limit){
        bind();engine.start();return engine.enqueueProduction(parent,id,name,units,price,buy,limit);
    }
    public Result tick(String parent,boolean ownsMenu){
        engine.poll();
        if(ownsMenu && needsMenu(parent))engine.onTick();
        return basketResult(engine.productionPositions().stream().filter(p->Objects.equals(parent,p.productionParent)).toList(),engine.productionFailed(parent),engine.activity());
    }
    static Result basketResult(List<GeneralPosition> positions,boolean failed,String activity){
        if(positions.isEmpty())return new Result(false,true,0,0,null,"Production order basket is missing");
        boolean done=positions.stream().allMatch(p->p.completed || p.productionBuy && p.stage==GeneralPosition.Stage.INVENTORY);
        int units=positions.stream().mapToInt(p->p.productionBuy?(p.completed?0:p.quantity):ProductionSaleAccounting.totalUnits(p)).sum();
        double cost=positions.stream().filter(p->p.productionBuy&&!p.completed).mapToDouble(GeneralPosition::cost).sum();
        String rejected=positions.stream().map(p->p.rejectedReason).filter(Objects::nonNull).findFirst().orElse(null);
        if(positions.size()>1){
            long placed=positions.stream().filter(p->p.submitted||p.stage==GeneralPosition.Stage.INVENTORY).count();
            long ready=positions.stream().filter(p->p.completed||p.stage==GeneralPosition.Stage.INVENTORY).count();
            activity="Ingredient basket: "+placed+"/"+positions.size()+" placed, "+ready+"/"+positions.size()+" resolved · "+activity;
        }
        return new Result(done,failed,units,cost,positions.size()==1?positions.getFirst().verifiedProceeds:null,
            rejected==null?activity:rejected,rejected!=null);
    }
    public boolean needsMenu(String parent){return engine.needsMenu() && java.util.Objects.equals(parent,engine.productionMenuParent());}
    public boolean acknowledge(String parent){return engine.consumeProduction(parent);}
    public double commitment(String parent){return engine.productionCommitment(parent);}
    public double buyCommitment(String parent){return engine.productionBuyCommitment(parent);}
    public boolean orphaned(Set<String> activeParents){if(AccountStorage.INSTANCE.pinned()==null)return false;bind();return engine.orphanProductionOrders(activeParents);}
    public Result tick(boolean ownsMenu){
        engine.poll();if(ownsMenu && engine.needsMenu())engine.onTick();
        var p=engine.productionPosition();
        if(p==null)return new Result(false,true,0,0,null,"Production order journal is missing");
        boolean done=p.completed || p.productionBuy && p.stage==GeneralPosition.Stage.INVENTORY;
        return new Result(done,engine.productionFailed(),p.productionBuy?(p.completed?0:p.quantity):ProductionSaleAccounting.totalUnits(p),p.productionBuy && !p.completed?p.cost():0,p.verifiedProceeds,p.rejectedReason==null?engine.activity():p.rejectedReason,p.rejectedReason!=null);
    }
    public boolean recoverBuy(){bind();return engine.recoverProductionBuy();}
    public com.goofy.goofyaddons.features.production.ProductionJobs.Job recoveryEvidence(String account) {
        var p=engine.productionPosition();
        if(p!=null && !p.productionBuy && p.completed && !p.submitted && p.stage==GeneralPosition.Stage.INVENTORY)
            return new com.goofy.goofyaddons.features.production.ProductionJobs.Job(p.tradeId,"recovered:output:"+p.item.id(),account,
                com.goofy.goofyaddons.features.production.ProductionJobs.State.REVIEW,1,-1,0,0,null,null,null,
                "Verified unsold output recovery: "+p.quantity+" "+p.item.id()+" units remain in inventory; no order exists and no sale was submitted. Parent "+p.productionParent+" retains its original cost basis. No sale, new acquisition or profit assumed.");
        if(p==null || !p.productionBuy || !p.purchasePriceKnown || !(p.completed || p.stage==GeneralPosition.Stage.INVENTORY))
            throw new IllegalStateException("Claim/cancellation has not been verified");
        return new com.goofy.goofyaddons.features.production.ProductionJobs.Job(p.tradeId,"recovered:bazaar:"+p.item.id(),account,
            com.goofy.goofyaddons.features.production.ProductionJobs.State.REVIEW,1,-1,0,0,p.completed?0:p.cost(),null,null,
            "Verified buy-order recovery: "+(p.completed?0:p.quantity)+" units in inventory at "+p.unitCost+" coins/unit; outstanding order removed. Inspect these inputs before acknowledging; no sale or profit assumed.");
    }
    public void poll(){bind();if(engine.hasRetainedPositions())engine.poll();}
    public void yieldMenu(){engine.yieldProductionMenu();}
    public boolean needsMenu(){return engine.needsMenu();}
    public Map<String,Object> diagnosticState(){return engine.diagnosticState();}
    public boolean acknowledge(){return engine.consumeProduction();}
    public boolean retained(String parent){
        if(AccountStorage.INSTANCE.pinned()==null)return true;
        bind();engine.restoreBudget();
        return engine.hasStateError() || reviewBlockedByOrders(engine.productionPositions(),parent);
    }
    static boolean reviewBlockedByOrders(List<GeneralPosition> positions,String parent){
        return parent==null || positions.stream().anyMatch(p->p.productionParent==null || parent.equals(p.productionParent));
    }
    public boolean retained(){if(AccountStorage.INSTANCE.pinned()==null)return false;bind();engine.restoreBudget();return engine.hasRetainedPositions()||engine.hasStateError();}
    public void stop(){engine.stop();}
}
