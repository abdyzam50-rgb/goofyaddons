package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import java.util.*;

/** Seed an enchanted filter, buy bounded raw lots, and prove their conversions before selling. */
final class CompactorProduction {
    @FunctionalInterface interface Journal {void record(String reason,double cost)throws Exception;}
    private enum Phase {BASELINE, SEED, CRAFT, OPEN, DISABLE, INSTALL, ENABLE, CLOSE, REFRESH, LEAVE_REFRESH, BUY, WITHDRAW_SACK, VERIFY_WITHDRAWAL, CLEAN, DONE}
    private final ProductionRun.Environment env;
    private final ProductionRecipe recipe;
    private final int batches,ratio,initialInput;
    private final String input;
    private final Journal journal;
    private final SlotView device;
    private final CompactorClearance cleanup=new CompactorClearance();
    private Phase phase=Phase.BASELINE;
    private enum SackPurpose {BASELINE, SETUP, PURCHASE, FINAL}
    private SackPurpose sackPurpose;
    private CompactorSacks sacks;
    private boolean sackReadClosed,sackBaselineKnown;
    private int sackBaseline,ownedSack,boughtUnits,withdrawUnits,withdrawBefore;
    private double withdrawalPurse;
    private BazaarInstantBuy purchase;
    private String seedJob;
    private long deadline,next,settled;
    private int configuredContainer=-1;
    private MenuSnapshot refreshStable;
    private boolean clicked;
    private double spent;
    private Map<String,Integer> installedInventory;

    static boolean supported(ProductionRecipe r,RecipeCatalog catalog) {
        return r!=null && r.kind()==ProductionRecipe.Kind.CRAFT && r.outputId().startsWith("ENCHANTED_")
            && r.outputCount()==1 && r.ingredients().size()==1 && r.ingredients().values().iterator().next()==160
            && !catalog.unstackable(r.outputId()) && !catalog.unstackable(r.ingredients().keySet().iterator().next());
    }
    static SlotView available(MenuSnapshot menu) {
        return PersonalCompactors.detect(menu).stream().filter(s->s.inPlayerInventory()&&s.containerSlot()<9
            &&s.metadata().compactor()!=null&&s.metadata().compactor().active()!=null&&s.metadata().compactor().recipes().isEmpty()).findFirst().orElse(null);
    }
    CompactorProduction(ProductionRun.Environment env,ProductionRecipe recipe,int batches,SlotView device,Journal journal) {
        this.env=env;this.recipe=recipe;this.batches=batches;this.device=device;this.journal=journal;
        input=recipe.ingredients().keySet().iterator().next();ratio=recipe.ingredients().get(input);
        sacks=new CompactorSacks(recipe.outputId(),env.name(recipe.outputId()));sackPurpose=SackPurpose.BASELINE;
        initialInput=count(input); // Preserve pre-existing input basis; never purchase it twice.
    }
    static final String LOCK="GOOFY_PERSONAL_COMPACTOR_SESSION";
    boolean active(){return phase!=Phase.DONE;}
    Map<String,Integer> remainingInputs(){int missing=ratio*batches-equivalent();return missing>0?Map.of(input,missing):Map.of();}
    boolean wantsMenu(){return !env.craftQueued();}
    boolean needsRequirements(){return purchase==null&&(phase==Phase.BASELINE||phase==Phase.SEED||phase==Phase.BUY);}
    boolean submitted(){return purchase!=null&&purchase.submitted();}
    String status(){
        String action=sacks!=null?"checking sacks":phase==Phase.WITHDRAW_SACK||phase==Phase.VERIFY_WITHDRAWAL?"collecting owned sack outputs":phase.name().toLowerCase(Locale.ROOT);
        return "Personal Compactor · "+action+" · "+Math.min(batches,count(recipe.outputId())+ownedSack)+"/"+batches+" outputs";
    }
    Map<String,Object> diagnostics(){return Map.ofEntries(
        Map.entry("phase",phase.name()),Map.entry("output",recipe.outputId()),Map.entry("input",input),Map.entry("ratio",ratio),
        Map.entry("initialInput",initialInput),Map.entry("spent",spent),Map.entry("purchaseSubmitted",submitted()),
        Map.entry("sackBaseline",sackBaseline),Map.entry("sackBaselineVerified",sackBaselineKnown),Map.entry("ownedSackOutputs",ownedSack),
        Map.entry("sackReadPurpose",sacks==null?"none":sackPurpose.name()));}
    private int count(String id){return env.menu()==null?0:ProductionStorage.Move.inventoryCount(env.menu(),id);}
    private int equivalent(){return count(input)+ratio*(count(recipe.outputId())+ownedSack);}
    private int physicalEquivalent(){return count(input)+ratio*count(recipe.outputId());}
    private ProductionLoop.Outcome pending(){return ProductionLoop.Outcome.pending(status());}
    private ProductionLoop.Outcome uncertain(String reason){return ProductionLoop.Outcome.uncertain(reason+"; inspect compactor filter, inventory and purchases");}
    private void intent(String reason)throws Exception{journal.record(reason,spent);}
    private Map<String,Integer> inventory(){
        var result=new TreeMap<String,Integer>();for(var s:env.menu().slots())if(s.inPlayerInventory()&&s.containerSlot()<36&&!s.empty()) {
            String id=ProductionMenus.productId(s);if(id==null)id="name:"+s.hoverName();result.merge(id,s.count(),Integer::sum);
        }return result;
    }
    private boolean installationConserved(){
        var current=inventory();
        if(current.getOrDefault(input,0)+ratio*(current.getOrDefault(recipe.outputId(),0)+ownedSack)!=installedInventory.getOrDefault(input,0)+ratio*installedInventory.getOrDefault(recipe.outputId(),0))return false;
        var before=new TreeMap<>(installedInventory);before.remove(input);before.remove(recipe.outputId());
        current.remove(input);current.remove(recipe.outputId());return before.equals(current);
    }
    private SlotView currentDevice(){return PersonalCompactors.detect(env.menu()).stream().filter(s->s.containerSlot()==device.containerSlot()
        &&Objects.equals(s.metadata().uuid(),device.metadata().uuid())&&Objects.equals(s.customId(),device.customId())).findFirst().orElse(null);}
    ProductionLoop.Outcome tick(boolean owns,long now) {
        try{return advance(owns,now);}catch(Exception failed){return uncertain("Compactor operation or journal failed");}
    }
    private ProductionLoop.Outcome advance(boolean owns,long now)throws Exception {
        if(sacks!=null)return readSacks(owns,now);
        if(purchase!=null) {
            if(purchase.missingConvertedOutputs(env.menu(),env.purse(),now,ownedSack*ratio)>0) {
                if(!sackBaselineKnown)return uncertain("Compacted outputs are missing and no starting sack balance was verified");
                startSacks(SackPurpose.PURCHASE);return pending();
            }
            if(!owns)return pending();
            var result=purchase.tick(env.menu(),env.signOpen(),env.actions(),env.purse(),now,ownedSack*ratio);
            if(result==BazaarInstantBuy.Result.BLOCKED||result==BazaarInstantBuy.Result.UNCERTAIN)return uncertain(purchase.failure());
            if(result!=BazaarInstantBuy.Result.BOUGHT)return pending();
            boughtUnits+=purchase.amount();spent+=purchase.spent();journal.record("Compactor input lot paid and equivalent units verified",spent);purchase=null;
            deadline=now+8000;return pending();
        }
        // The child owns its grid and cursor until its durable completion receipt.
        if((phase==Phase.CRAFT||phase==Phase.SEED) && env.craftQueued())return pending();
        if(env.menu()==null || now<next)return pending();
        if(!env.menu().cursorEmpty()) {
            // Custom Hypixel controls briefly look like vanilla PICKUP clicks locally.
            // Wait for the same submitted control to be acknowledged; never click it again.
            if(clicked&&(phase==Phase.DISABLE||phase==Phase.INSTALL||phase==Phase.ENABLE))
                return now>deadline?uncertain("Compactor control cursor was not acknowledged; no click repeated"):pending();
            return uncertain("Compactor production cursor is occupied");
        }
        if(phase==Phase.SEED) {
            if(count(input)<ratio)return buy(owns,now,false);
            if(env.craftQueued())return pending();
            if(env.menu().title()!=null&&!CraftingExecutor.reusableMenu(env.menu())) {
                if(owns)env.actions().closeMenu();return pending();
            }
            intent("Crafting one verified enchanted seed for Personal Compactor");
            seedJob=env.queueCraftRecipe(recipe.outputId(),1,recipe.key());
            if(seedJob==null)return uncertain("Compactor seed craft could not be queued");
            phase=Phase.CRAFT;return pending();
        }
        if(phase==Phase.CRAFT) {
            if(env.craftQueued())return pending();
            var job=env.jobs().find(seedJob).orElse(null);
            if(job==null||job.state()!=ProductionJobs.State.OUTPUT_READY&&job.state()!=ProductionJobs.State.DONE)return uncertain("Compactor seed craft was not verified");
            if(count(recipe.outputId())!=1)return uncertain("Compactor seed output quantity changed");
            phase=Phase.OPEN;clicked=false;deadline=now+8000;return pending();
        }
        if(phase==Phase.OPEN) {
            if(!owns)return pending();
            if(now>deadline)return uncertain("Craft menu did not close before compactor configuration");
            if(env.menu().title()!=null) {
                if(!clicked){env.actions().closeMenu();clicked=true;next=now+Math.max(150,com.goofy.goofyaddons.utils.ActionDelay.next());}
                return pending();
            }
            var current=currentDevice();if(current==null)return uncertain("Selected Personal Compactor disappeared");
            installedInventory=inventory();intent("Opening Personal Compactor to install "+recipe.outputId());
            if(!env.openCompactor(current))return uncertain("Personal Compactor could not be opened from its hotbar slot");
            phase=Phase.DISABLE;deadline=now+8000;clicked=false;return pending();
        }
        if(phase==Phase.DISABLE||phase==Phase.INSTALL||phase==Phase.ENABLE) {
            if(!owns)return pending();
            if(phase==Phase.INSTALL&&!clicked&&!installationConserved())
                return now>deadline?uncertain("Inventory changed before compactor filter installation"):pending();
            var controls=CompactorClearance.controls(env.menu());
            if(controls==null)return now>deadline?uncertain("Personal Compactor controls did not load"):pending();
            if(controls.tier()!=device.metadata().compactor().tier())return uncertain("Wrong Personal Compactor menu opened");
            configuredContainer=env.menu().containerId();
            if(phase==Phase.DISABLE) {
                if(!controls.recipes().isEmpty())return uncertain("Unexpected filters before compactor setup");
                if(!controls.active()) {
                    if(now<settled)return pending();phase=Phase.INSTALL;clicked=false;deadline=now+8000;return pending();
                }
                if(clicked)return now>deadline?uncertain("Compactor disable was not acknowledged; no click repeated"):pending();
                var toggle=env.menu().slots().stream().filter(s->!s.inPlayerInventory()&&"Compactor Currently ON!".equals(com.goofy.goofyaddons.utils.Chat.strip(s.hoverName()))&&s.hasLoreLine("Click to toggle!")).findFirst().orElse(null);
                if(toggle==null)return uncertain("Compactor disable control is unreadable");
                intent("Disabling Personal Compactor before filter installation");env.actions().click(toggle.index(),false);clicked=true;settled=now+150;return pending();
            }
            if(phase==Phase.INSTALL) {
                if(controls.recipes().equals(Map.of(0,recipe.outputId()))) {
                    if(now<settled)return pending();phase=Phase.ENABLE;clicked=false;deadline=now+8000;return pending();
                }
                if(clicked)return now>deadline?uncertain("Compactor filter installation was not acknowledged; no click repeated"):pending();
                if(!controls.recipes().isEmpty())return uncertain("Unexpected compactor filters before installation");
                var seed=env.menu().slots().stream().filter(s->s.inPlayerInventory()&&recipe.outputId().equals(s.customId())).findFirst().orElse(null);
                if(seed==null)return uncertain("Enchanted seed disappeared before filter installation");
                intent("Installing Personal Compactor filter "+recipe.outputId());env.actions().click(seed.index(),false);
                clicked=true;settled=now+Math.max(150,com.goofy.goofyaddons.utils.ActionDelay.next());return pending();
            }
            if(!controls.recipes().equals(Map.of(0,recipe.outputId())))return uncertain("Personal Compactor filter changed while enabling");
            if(controls.active()) {
                if(now<settled)return pending();intent("Closing configured Personal Compactor");env.actions().closeMenu();phase=Phase.CLOSE;deadline=now+8000;settled=now+300;return pending();
            }
            if(clicked)return now>deadline?uncertain("Personal Compactor activation was not acknowledged"):pending();
            var toggle=env.menu().slots().stream().filter(s->!s.inPlayerInventory()&&"Compactor Currently OFF!".equals(com.goofy.goofyaddons.utils.Chat.strip(s.hoverName()))&&s.hasLoreLine("Click to toggle!")).findFirst().orElse(null);
            if(toggle==null)return uncertain("Personal Compactor activation control is unreadable");
            intent("Enabling configured Personal Compactor");env.actions().click(toggle.index(),false);clicked=true;settled=now+150;return pending();
        }
        if(phase==Phase.CLOSE) {
            if(env.menu().title()!=null)return now>deadline?uncertain("Personal Compactor menu did not close"):pending();
            if(!owns||now<settled)return pending();
            intent("Refreshing server inventory after compactor setup");env.actions().command("craft");
            phase=Phase.REFRESH;deadline=now+8000;refreshStable=null;return pending();
        }
        if(phase==Phase.REFRESH) {
            if(env.menu().title()==null)return now>deadline?uncertain("Fresh inventory menu did not open after compactor setup"):pending();
            if(!"Craft Item".equals(com.goofy.goofyaddons.utils.Chat.strip(env.menu().title()))||env.menu().containerId()==configuredContainer)
                return uncertain("Unexpected inventory menu during compactor reconciliation");
            if(refreshStable==null||!CraftingExecutor.sameOwnedState(refreshStable,env.menu())) {
                refreshStable=env.menu();settled=now+300;return pending();
            }
            if(now<settled)return pending();
            if(!installationConserved()) {
                int shortfall=(installedInventory.getOrDefault(input,0)+ratio*installedInventory.getOrDefault(recipe.outputId(),0))-equivalent();
                if(sackBaselineKnown&&shortfall>0&&shortfall%ratio==0){startSacks(SackPurpose.SETUP);return pending();}
                if(now<=deadline)return pending();
                env.event("ERROR","production.compactor_install_counts",Map.of("expected",installedInventory,"observed",inventory(),"output",recipe.outputId()));
                return uncertain("Compactor setup counts did not reconcile after closing; expected "+(installedInventory.getOrDefault(input,0)+ratio*installedInventory.getOrDefault(recipe.outputId(),0))+" raw-equivalent units, observed "+equivalent());
            }
            if(!owns)return pending();
            env.actions().closeMenu();phase=Phase.LEAVE_REFRESH;deadline=now+8000;return pending();
        }
        if(phase==Phase.LEAVE_REFRESH) {
            if(env.menu().title()!=null)return now>deadline?uncertain("Fresh inventory menu did not close before buying"):pending();
            phase=Phase.BUY;deadline=now+8000;return pending();
        }
        if(phase==Phase.BUY) {
            var current=currentDevice();var data=current==null?null:current.metadata().compactor();
            if(data==null||!Boolean.TRUE.equals(data.active())||!data.recipes().equals(Map.of(0,recipe.outputId())))
                return now>deadline?uncertain("Saved compactor configuration changed"):pending();
            for(var other:PersonalCompactors.detect(env.menu()))if(other.containerSlot()!=device.containerSlot()&&!(other.metadata().compactor()!=null&&other.metadata().compactor().recipes().isEmpty()))return uncertain("Another Personal Compactor gained filters");
            int remaining=ratio*batches-equivalent();
            if(remaining<0||count(recipe.outputId())+ownedSack>batches)return uncertain("Compactor produced more units than planned");
            if(remaining==0) {
                if(count(recipe.outputId())+ownedSack!=batches)return now>deadline?uncertain("Paid inputs did not finish converting"):pending();
                phase=ownedSack>0?Phase.WITHDRAW_SACK:Phase.CLEAN;return pending();
            }
            return buy(owns,now,true);
        }
        if(phase==Phase.WITHDRAW_SACK) {
            if(!owns)return pending();
            if(env.menu().title()!=null){env.actions().closeMenu();next=now+150;return pending();}
            if(ProductionInventorySpace.capacity(RecipeCatalog.instance(),env.menu(),recipe.outputId())<ownedSack)
                return ProductionLoop.Outcome.blocked("Free inventory space for this run's compacted sack outputs");
            if(!Double.isFinite(env.purse())||env.purse()<0)return pending();
            withdrawUnits=ownedSack;withdrawBefore=count(recipe.outputId());withdrawalPurse=env.purse();
            intent("Withdrawing only owned sack outputs: "+withdrawUnits+" "+recipe.outputId()+"; starting sack balance "+sackBaseline);
            env.actions().command("gfs "+recipe.outputId()+" "+withdrawUnits);phase=Phase.VERIFY_WITHDRAWAL;deadline=now+8000;return pending();
        }
        if(phase==Phase.VERIFY_WITHDRAWAL) {
            int gain=count(recipe.outputId())-withdrawBefore;
            if(!Double.isFinite(env.purse())||env.purse()<0)return now>deadline?uncertain("Sack withdrawal purse observation is unavailable"):pending();
            if(gain>withdrawUnits||Math.abs(env.purse()-withdrawalPurse)>.51)return uncertain("Sack withdrawal changed inventory or purse unexpectedly");
            if(gain!=withdrawUnits)return now>deadline?uncertain("Owned sack withdrawal was not acknowledged; command not repeated"):pending();
            startSacks(SackPurpose.FINAL);return pending();
        }
        if(phase==Phase.CLEAN) {
            var result=cleanup.tick(env.menu(),env.actions(),new CompactorClearance.Ports(){
                public boolean open(SlotView device){return env.openCompactor(device);}
                public void intent(String reason)throws Exception{CompactorProduction.this.intent(reason);}
            },owns,now);
            if(result.step()!=ProductionLoop.Step.DONE)return result;
            if(count(recipe.outputId())!=batches)return uncertain("Final compactor output count changed");phase=Phase.DONE;
        }
        return ProductionLoop.Outcome.DONE;
    }
    private void startSacks(SackPurpose purpose){
        sacks=new CompactorSacks(recipe.outputId(),env.name(recipe.outputId()));sackPurpose=purpose;sackReadClosed=false;
    }
    private ProductionLoop.Outcome readSacks(boolean owns,long now)throws Exception {
        var result=sacks.tick(env.menu(),env.actions(),owns,now);
        if(result==ProductionLoop.Step.UNCERTAIN)return uncertain(sacks.failure());
        if(result!=ProductionLoop.Step.DONE)return pending();
        int balance=sacks.balance();
        if(!sackReadClosed) {
            if(sackPurpose==SackPurpose.BASELINE) {
                sackBaseline=balance;sackBaselineKnown=sacks.found();ownedSack=0;
                intent("Starting sack balance verified: "+sackBaseline+" "+recipe.outputId()+"; item observed "+sackBaselineKnown);
            } else {
                if(!sacks.found()||!sackBaselineKnown||balance<sackBaseline)return uncertain("Compactor sack balance changed outside this run");
                int owned=balance-sackBaseline;
                int expected=sackPurpose==SackPurpose.SETUP?installedInventory.getOrDefault(input,0)+ratio*installedInventory.getOrDefault(recipe.outputId(),0)
                    :initialInput+boughtUnits+(sackPurpose==SackPurpose.PURCHASE?purchase.amount():0);
                if(physicalEquivalent()+owned*ratio!=expected)return uncertain("Sack increase does not reconcile with this run's paid inputs; nothing withdrawn");
                if(sackPurpose==SackPurpose.FINAL&&(owned!=0||count(recipe.outputId())!=batches))return uncertain("Sack withdrawal did not preserve the starting sack balance");
                ownedSack=owned;intent("Verified owned sack outputs: "+ownedSack+" "+recipe.outputId()+"; preserved starting balance "+sackBaseline);
            }
            if(!owns)return pending();env.actions().closeMenu();sackReadClosed=true;deadline=now+8000;return pending();
        }
        if(env.menu().title()!=null)return now>deadline?uncertain("Sack receipt menu did not close"):pending();
        sacks=null;
        switch(sackPurpose) {
            case BASELINE -> phase=Phase.SEED;
            case SETUP -> {if(!installationConserved())return uncertain("Sack and inventory setup counts disagree");phase=Phase.BUY;deadline=now+8000;}
            case PURCHASE -> {} // The existing instant-buy receipt now sees verified owned sack units.
            case FINAL -> phase=Phase.CLEAN;
        }
        return pending();
    }

    private ProductionLoop.Outcome buy(boolean owns,long now,boolean converted)throws Exception {
        if(!env.buyingAllowed())return ProductionLoop.Outcome.blocked("Enable ingredient buying for compactor production");
        var block=env.procurementBlock();if(block!=null)return ProductionLoop.Outcome.blocked(block);
        int remaining=ratio*batches-equivalent();if(remaining<=0)return uncertain("Compactor seed inputs are unavailable");
        Double quote=env.instantBuyCost(input,remaining);
        if(quote==null||!Double.isFinite(quote)||quote<=0)return ProductionLoop.Outcome.blocked("Waiting for fresh full compactor input quote");
        if(quote*1.03>env.spendable())return ProductionLoop.Outcome.blocked("Remaining compactor inputs exceed spendable capital");
        if(!owns)return pending();
        // A full main inventory yields fewer than 64 outputs at 160:1. Reserve
        // one additional output stack per lot; accumulated inventory reduces later lots.
        int capacity=ProductionInventorySpace.capacity(RecipeCatalog.instance(),env.menu(),input)-64;
        if(capacity<Math.min(ratio,remaining))return ProductionLoop.Outcome.blocked("Keep enough inventory room for a compactor input lot and enchanted output");
        int lot=Math.min(remaining,Math.min(71680,capacity));
        Double lotQuote=env.instantBuyCost(input,lot);if(lotQuote==null)return ProductionLoop.Outcome.blocked("Waiting for compactor input lot quote");
        intent("Preparing inventory-sized compactor input lot: "+lot+" "+input);
        purchase=new BazaarInstantBuy(input,env.name(input),lot,lotQuote*1.03,reason->intent(reason),true);
        if(converted)purchase.withConversion(recipe.outputId(),ratio);
        return pending();
    }
}
