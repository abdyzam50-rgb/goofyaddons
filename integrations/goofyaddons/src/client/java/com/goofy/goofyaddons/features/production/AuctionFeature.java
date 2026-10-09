package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import java.util.*;

/** Opt-in BIN preparation owns the foreground menu until manual publication/review. */
public final class AuctionFeature implements Feature {
    private BinListingExecutor executor;
    private AuctionBrowserNavigation browser;
    private SlotView expected;
    private long requestedPrice;
    private boolean publish,guiPricing;
    private double maximumFee;
    private java.util.concurrent.CompletableFuture<AuctionPricing.Quote> priceCheck;
    private String jobId,product;
    private boolean running,paused;
    private long mismatchSince,missingSince;
    public String name(){return "Auction House";}
    public boolean queued(){return executor!=null || browser!=null;}
    /** The journal id of the most recently queued job, for callers that follow it. */
    public String jobId(){return jobId;}
    public String activity(){return queued()?"Preparing BIN listing for "+product:"No auction queued";}
    public Set<String> lockedProducts(){return product==null?Set.of():Set.of(product);}
    private ProductionJobs jobs()throws java.io.IOException{return FeatureManager.INSTANCE.crafting().productionJobs();}
    public boolean queue(String id,long price) {return queue(id,price,false,0);}
    public boolean queue(String id,long price,boolean publish,double maximumFee) {
        return queue(id,price,publish,maximumFee,false);
    }
    public boolean queueMarket(String id,long estimatedPrice,boolean publish,double maximumFee) {
        return queue(id,estimatedPrice,publish,maximumFee,true);
    }
    private boolean queue(String id,long price,boolean publish,double maximumFee,boolean guiPricing) {
        var actions=new LiveActions();var menu=new LiveWorld().menu();
        if(queued() || FeatureManager.INSTANCE.crafting().queued()) {actions.message("Finish the queued production operation first.");return false;}
        if(menu==null || !menu.cursorEmpty()){actions.message("Clear the cursor before preparing an auction.");return false;}
        if(CapitalManager.INSTANCE.occupied(id)){actions.message("That product belongs to a retained trader position.");return false;}
        var items=menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && !s.empty() && id.equals(ProductionMenus.productId(s))).toList();
        if(items.size()!=1){actions.message("Keep exactly one matching stack in ordinary inventory for auction preparation.");return false;}
        try {
            if(price<1 || price>1_000_000_000_000L || !Double.isFinite(maximumFee) || maximumFee<0 || maximumFee>1e12)throw new IllegalArgumentException("Invalid price/fee");
            jobId=UUID.randomUUID().toString();
            var item=items.getFirst();
            jobs().put(new ProductionJobs.Job(jobId,"auction:prepare:"+id,new LiveWorld().username(),ProductionJobs.State.OUTPUT_READY,1,-1,0,0,null,item.metadata().petType()==null?null:item.metadata().uuid(),null,
                "Existing inventory stack selected; cost basis unknown"));
            expected=item;requestedPrice=price;this.publish=publish;this.maximumFee=maximumFee;this.guiPricing=guiPricing;
            product=id;mismatchSince=missingSince=0;priceCheck=null;
            browser=new AuctionBrowserNavigation(RecipeCatalog.instance().name(id),s->id.equals(ProductionMenus.productId(s))
                && s.count()==item.count() && Objects.equals(s.enchantments(),item.enchantments())
                && Objects.equals(s.metadata().petType(),item.metadata().petType()) && Objects.equals(s.metadata().petTier(),item.metadata().petTier()));
            actions.message("Queued BIN "+(publish?"listing":"preparation")+" at "+price+" coins. Use the trading toggle."+(publish?" Fee limit: "+maximumFee:" Final publication remains manual."));return true;
        }catch(Exception failed){executor=null;browser=null;product=null;Diagnostics.failure("auction.queue_failed",failed);actions.message("Auction preparation could not be saved; no action performed.");return false;}
    }
    public void start(){running=true;paused=false;}
    public void resume(){paused=false;}
    public void pause(){paused=true;interrupt();}
    public void stop(){running=false;paused=false;interrupt();}
    public boolean isRunning(){return running && !paused;}
    public boolean needsMenu(){return isRunning() && queued();}
    public boolean canYield(){return !queued();}
    private void interrupt() {
        if(!queued())return;
        try {var job=jobs().find(jobId).orElseThrow();
            if(job.state()!=ProductionJobs.State.REVIEW)jobs().put(job.withState(job.state()==ProductionJobs.State.OUTPUT_READY?ProductionJobs.State.CANCELLED:ProductionJobs.State.REVIEW,
                "Auction preparation interrupted; inspect inventory and the sell slot before retrying"));
        }catch(Exception failure){Diagnostics.failure("auction.journal_failed",failure);}
        if(priceCheck!=null)priceCheck.cancel(true);priceCheck=null;browser=null;executor=null;product=null;
    }
    private long nextAction;
    public void onTick() {
        if(!needsMenu())return;
        // Same randomised pacing as the flippers: one step per action delay, never one per tick.
        long paced=System.currentTimeMillis();
        if(paced<nextAction)return;
        nextAction=paced+com.goofy.goofyaddons.utils.ActionDelay.next();
        var world=new LiveWorld();var live=world.menu();long now=world.now();
        try {
            var observed=live;
            if(live!=null && live.title()!=null && !world.signEditorOpen()) {
                observed=ServerMenuMirror.read();
                if(observed==null) {
                    if(missingSince==0)missingSince=now;
                    if(now-missingSince>=10000)FeatureManager.INSTANCE.safetyPause("Auction server snapshot did not arrive; inspect the current menu");
                    return;
                }
                missingSince=0;
                if(!sameOwnedState(observed,live)) {
                    if(mismatchSince==0)mismatchSince=now;
                    if(now-mismatchSince>=10000)FeatureManager.INSTANCE.safetyPause("Auction item movement was not confirmed by the server; inspect inventory and sell slot");
                    return;
                }
                mismatchSince=0;
            }
            double purse=new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse();
            if(browser!=null) {
                var state=browser.tick(observed,world.signEditorOpen(),new LiveActions(),now);
                if(state==AuctionBrowserNavigation.Result.BLOCKED){FeatureManager.INSTANCE.safetyPause(browser.failure());return;}
                if(state!=AuctionBrowserNavigation.Result.READY)return;
                if(priceCheck==null){priceCheck=AuctionPriceLookup.fetch(product);return;}
                if(!priceCheck.isDone())return;
                try {
                    var quote=priceCheck.join();AuctionPricing.validateObserved(quote,product,browser.price(),now);
                    long price=guiPricing?Math.max(1,(long)Math.floor(browser.price())-1):requestedPrice;
                    AuctionPricing.validateObserved(quote,product,price,now);
                    executor=new BinListingExecutor(expected,price,jobs(),jobId,publish,maximumFee);
                    new LiveActions().message("Observed matching lowest BIN "+(long)browser.price()+"; Coflnet validated. Listing price "+price+".");
                    browser=null;priceCheck=null;new LiveActions().closeMenu();return;
                }catch(RuntimeException failed){FeatureManager.INSTANCE.safetyPause("Auction GUI/API prices could not be validated; no item moved or listing submitted");return;}
            }
            var result=executor.tick(observed,world.signEditorOpen(),new LiveActions(),world.username(),purse,CapitalManager.INSTANCE.available(purse),now);
            if(result==BinListingExecutor.Result.BLOCKED) {
                if(observed!=null)try{AuctionCommands.capture(observed);}catch(java.io.IOException failure){Diagnostics.failure("auction.capture_failed",failure);}
                FeatureManager.INSTANCE.safetyPause(executor.failure());
            }
            else if(result==BinListingExecutor.Result.LISTED) {
                new LiveActions().message("BIN listing and creation fee verified. Listing retained for sale monitoring; proceeds/profit not assumed.");
                executor=null;product=null;new LiveActions().closeMenu();FeatureManager.INSTANCE.invalidateMarketReport();
            }
            else if(result==BinListingExecutor.Result.PREPARED) {
                new LiveActions().message("BIN item and price verified. Review duration/fees and publish manually; no listing fee or profit recorded.");
                FeatureManager.INSTANCE.safetyPause("BIN prepared for manual publication; retain the current menu and inspect the final confirmation");
            }
        }catch(Exception failure){Diagnostics.failure("auction.execution_failed",failure);FeatureManager.INSTANCE.safetyPause("Auction preparation failed; inspect the item before restarting");}
    }
    /** Listing item ownership includes the sell slot; GUI price/lore updates need not match local predictions. */
    static boolean sameOwnedState(MenuSnapshot a,MenuSnapshot b) {
        if(!CraftingExecutor.sameOwnedState(a,b))return false;
        var x=a.slot(13);var y=b.slot(13);
        if(x==null || y==null)return x==y;
        return x.empty()==y.empty() && x.count()==y.count() && Objects.equals(x.customId(),y.customId())
            && Objects.equals(x.metadata(),y.metadata()) && Objects.equals(x.enchantments(),y.enchantments());
    }
}
