package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import java.util.*;

/** Seed an enchanted filter, buy bounded raw lots, and prove their conversions before selling. */
final class CompactorProduction {
    @FunctionalInterface interface Journal {void record(String reason,double cost)throws Exception;}
    private enum Phase {SEED, CRAFT, OPEN, INSTALL, ENABLE, CLOSE, BUY, CLEAN, DONE}
    private final ProductionRun.Environment env;
    private final ProductionRecipe recipe;
    private final int batches,ratio,initialInput;
    private final String input;
    private final Journal journal;
    private final SlotView device;
    private final CompactorClearance cleanup=new CompactorClearance();
    private Phase phase=Phase.SEED;
    private BazaarInstantBuy purchase;
    private String seedJob;
    private long deadline,next,settled;
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
        initialInput=count(input); // Preserve pre-existing input basis; never purchase it twice.
    }
    static final String LOCK="GOOFY_PERSONAL_COMPACTOR_SESSION";
    boolean active(){return phase!=Phase.DONE;}
    Map<String,Integer> remainingInputs(){int missing=ratio*batches-equivalent();return missing>0?Map.of(input,missing):Map.of();}
    boolean wantsMenu(){return !env.craftQueued();}
    boolean needsRequirements(){return purchase==null&&(phase==Phase.SEED||phase==Phase.BUY);}
    boolean submitted(){return purchase!=null&&purchase.submitted();}
    String status(){return "Personal Compactor · "+phase.name().toLowerCase(Locale.ROOT)+" · "+count(recipe.outputId())+"/"+batches+" outputs";}
    Map<String,Object> diagnostics(){return Map.of("phase",phase.name(),"output",recipe.outputId(),"input",input,"ratio",ratio,"initialInput",initialInput,"spent",spent,"purchaseSubmitted",submitted());}
    private int count(String id){return env.menu()==null?0:ProductionStorage.Move.inventoryCount(env.menu(),id);}
    private int equivalent(){return count(input)+ratio*count(recipe.outputId());}
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
        if(current.getOrDefault(input,0)+ratio*current.getOrDefault(recipe.outputId(),0)!=installedInventory.getOrDefault(input,0)+ratio*installedInventory.getOrDefault(recipe.outputId(),0))return false;
        var before=new TreeMap<>(installedInventory);before.remove(input);before.remove(recipe.outputId());
        current.remove(input);current.remove(recipe.outputId());return before.equals(current);
    }
    private SlotView currentDevice(){return PersonalCompactors.detect(env.menu()).stream().filter(s->s.containerSlot()==device.containerSlot()
        &&Objects.equals(s.metadata().uuid(),device.metadata().uuid())&&Objects.equals(s.customId(),device.customId())).findFirst().orElse(null);}
    ProductionLoop.Outcome tick(boolean owns,long now) {
        try{return advance(owns,now);}catch(Exception failed){return uncertain("Compactor operation or journal failed");}
    }
    private ProductionLoop.Outcome advance(boolean owns,long now)throws Exception {
        if(purchase!=null) {
            if(!owns)return pending();
            var result=purchase.tick(env.menu(),env.signOpen(),env.actions(),env.purse(),now);
            if(result==BazaarInstantBuy.Result.BLOCKED||result==BazaarInstantBuy.Result.UNCERTAIN)return uncertain(purchase.failure());
            if(result!=BazaarInstantBuy.Result.BOUGHT)return pending();
            spent+=purchase.spent();journal.record("Compactor input lot paid and equivalent units verified",spent);purchase=null;
            deadline=now+8000;return pending();
        }
        // The child owns its grid and cursor until its durable completion receipt.
        if((phase==Phase.CRAFT||phase==Phase.SEED) && env.craftQueued())return pending();
        if(env.menu()==null || now<next)return pending();
        if(!env.menu().cursorEmpty())return uncertain("Compactor production cursor is occupied");
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
            phase=Phase.INSTALL;deadline=now+8000;clicked=false;return pending();
        }
        if(phase==Phase.INSTALL||phase==Phase.ENABLE) {
            if(!owns)return pending();
            if(!installationConserved())return uncertain("Inventory changed while installing the compactor filter");
            var controls=CompactorClearance.controls(env.menu());
            if(controls==null)return now>deadline?uncertain("Personal Compactor controls did not load"):pending();
            if(controls.tier()!=device.metadata().compactor().tier())return uncertain("Wrong Personal Compactor menu opened");
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
                if(now<settled)return pending();intent("Closing configured Personal Compactor");env.actions().closeMenu();phase=Phase.CLOSE;deadline=now+8000;return pending();
            }
            if(clicked)return now>deadline?uncertain("Personal Compactor activation was not acknowledged"):pending();
            var toggle=env.menu().slots().stream().filter(s->!s.inPlayerInventory()&&"Compactor Currently OFF!".equals(com.goofy.goofyaddons.utils.Chat.strip(s.hoverName()))&&s.hasLoreLine("Click to toggle!")).findFirst().orElse(null);
            if(toggle==null)return uncertain("Personal Compactor activation control is unreadable");
            intent("Enabling configured Personal Compactor");env.actions().click(toggle.index(),false);clicked=true;settled=now+150;return pending();
        }
        if(phase==Phase.CLOSE) {
            if(env.menu().title()!=null)return now>deadline?uncertain("Personal Compactor menu did not close"):pending();
            phase=Phase.BUY;deadline=now+8000;return pending();
        }
        if(phase==Phase.BUY) {
            var current=currentDevice();var data=current==null?null:current.metadata().compactor();
            if(data==null||!Boolean.TRUE.equals(data.active())||!data.recipes().equals(Map.of(0,recipe.outputId())))
                return now>deadline?uncertain("Saved compactor configuration changed"):pending();
            for(var other:PersonalCompactors.detect(env.menu()))if(other.containerSlot()!=device.containerSlot()&&!(other.metadata().compactor()!=null&&other.metadata().compactor().recipes().isEmpty()))return uncertain("Another Personal Compactor gained filters");
            int remaining=ratio*batches-equivalent();
            if(remaining<0||count(recipe.outputId())>batches)return uncertain("Compactor produced more units than planned");
            if(remaining==0) {
                if(count(recipe.outputId())!=batches)return now>deadline?uncertain("Paid inputs did not finish converting"):pending();
                phase=Phase.CLEAN;return pending();
            }
            return buy(owns,now,true);
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
    private ProductionLoop.Outcome buy(boolean owns,long now,boolean converted)throws Exception {
        if(!env.buyingAllowed())return ProductionLoop.Outcome.blocked("Enable ingredient buying for compactor production");
        var block=env.procurementBlock();if(block!=null)return ProductionLoop.Outcome.blocked(block);
        int remaining=ratio*batches-equivalent();if(remaining<=0)return uncertain("Compactor seed inputs are unavailable");
        Double quote=env.instantBuyCost(input,remaining);
        if(quote==null||!Double.isFinite(quote)||quote<=0)return ProductionLoop.Outcome.blocked("Waiting for fresh full compactor input quote");
        if(quote*1.03>env.spendable())return ProductionLoop.Outcome.blocked("Remaining compactor inputs exceed spendable capital");
        if(!owns)return pending();
        int outputSlots=(batches+63)/64;
        int heldOutputSlots=(count(recipe.outputId())+63)/64;
        int capacity=ProductionInventorySpace.capacity(RecipeCatalog.instance(),env.menu(),input)-64*Math.max(1,outputSlots-heldOutputSlots);
        if(capacity<Math.min(ratio,remaining))return ProductionLoop.Outcome.blocked("Keep enough inventory room for a compactor input lot and enchanted output");
        int lot=Math.min(remaining,Math.min(71680,capacity));
        Double lotQuote=env.instantBuyCost(input,lot);if(lotQuote==null)return ProductionLoop.Outcome.blocked("Waiting for compactor input lot quote");
        intent("Preparing inventory-sized compactor input lot: "+lot+" "+input);
        purchase=new BazaarInstantBuy(input,env.name(input),lot,lotQuote*1.03,reason->intent(reason),true);
        if(converted)purchase.withConversion(recipe.outputId(),ratio);
        return pending();
    }
}
