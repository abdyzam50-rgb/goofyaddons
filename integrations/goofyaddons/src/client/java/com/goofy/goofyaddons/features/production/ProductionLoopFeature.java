package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import java.util.*;

/**
 * Runs one queued production loop (inputs, processing, claim, BIN listing) through the
 * same foreground menu scheduler as trading. The orchestration lives in {@link ProductionRun};
 * this class only connects it to the live game and the crafting and auction features.
 */
public final class ProductionLoopFeature implements Feature {
    private ProductionRun run;
    private final CraftFlipSelection selection=new CraftFlipSelection();
    private double automaticBudget;
    private long nextSelection;
    public void refreshSelection(){selection.refresh();}
    public Map<String,Object> craftPlan(){
        var view=new LinkedHashMap<String,Object>(selection.view());view.put("queued",queued());view.put("activity",activity());
        view.put("selectionStatus",discoveryReason());view.put("eligibleRoutes",selection.routes().stream().filter(CraftFlipPlanner.Route::eligible).count());view.put("plannedBudget",automaticBudget);view.put("spent",run==null?0:run.spent());return view;
    }
    public void showCraftPlans(){
        selection.refresh();var actions=new LiveActions();
        var rows=selection.currentRoutes();
        if(rows.isEmpty())actions.message("No feasible craft routes: "+discoveryReason()+". Minimum net profit "+Math.round(GoofyConfig.INSTANCE.craftFlips.minimumProfit)+", maximum batches "+GoofyConfig.INSTANCE.craftFlips.maxBatches+". See debug export for excluded candidate counts.");
        rows.stream().limit(10).forEach(r->actions.message(RecipeCatalog.instance().name(r.recipe().outputId())+" · "+r.venue()+" · "+r.batches()+" batches · conservative net "+Math.round(r.profit())+" · "+(r.eligible()?"eligible":r.reason())));
    }
    private String discoveryReason() {
        if(FeatureManager.INSTANCE.accountRequirementsPending())return "Checking account prerequisites";
        var rows=selection.routes();
        if(rows.isEmpty())return selection.emptyReason();
        return rows.stream().filter(CraftFlipPlanner.Route::eligible).findAny().isPresent()?"Ready to select a craft":rows.getFirst().reason();
    }
    private void selectAutomatic() {
        if(!"CRAFT".equals(FeatureManager.INSTANCE.modeLabel())||System.currentTimeMillis()<nextSelection
                ||FeatureManager.INSTANCE.crafting().queued()||FeatureManager.INSTANCE.auction().queued())return;
        nextSelection=System.currentTimeMillis()+5000;
        var manager=FeatureManager.INSTANCE;
        if(manager.accountRequirementsPending())return;
        var candidate=selection.currentRoutes().stream().filter(CraftFlipPlanner.Route::eligible).findFirst().orElse(null);
        if(candidate==null)return;
        try {
            automaticBudget=candidate.capital();
            run=ProductionRun.startCraft(environment(true,false),RecipeCatalog.instance(),candidate.recipe(),candidate.batches(),candidate.binPrice(),candidate.fee());
            new LiveActions().message("Automatic craft: "+RecipeCatalog.instance().name(run.output())+" · "+candidate.batches()+" batches · budget "+Math.round(automaticBudget)+" · conservative net "+Math.round(candidate.profit()));
            Diagnostics.event("INFO","production.auto_selected",candidate.describe(RecipeCatalog.instance()));
        }catch(Exception failed){automaticBudget=0;Diagnostics.failure("production.auto_queue_failed",failed);FeatureManager.INSTANCE.safetyPause("Automatic craft could not be journaled; review production jobs");}
    }
    private boolean running, paused;
    private String lastReason;

    public String name() { return "Production"; }
    public boolean queued() { return run != null; }
    public String activity() {
        if (run == null) return "CRAFT".equals(FeatureManager.INSTANCE.modeLabel())?"Craft discovery · "+selection.routes().stream().filter(CraftFlipPlanner.Route::eligible).count()+" feasible routes · "+discoveryReason():"No production run queued";
        return "Production " + run.output() + " · " + run.stage().name().toLowerCase(Locale.ROOT) + (run.reason() == null ? "" : " · " + run.reason());
    }
    public Set<String> lockedProducts() { return run == null ? Set.of() : run.lockedProducts(); }

    public boolean queue(String output, ProductionRecipe.Kind kind, int batches, int forgeSlot, long binPrice, double maximumFee) {
        return queue(output, kind, batches, forgeSlot, binPrice, maximumFee, false);
    }

    /**
     * Queues a run. {@code buyInputs} lets this one run instant-buy missing inputs even while the
     * global setting is off; the player asked for it by name, and spendable capital still caps it.
     */
    public boolean queue(String output, ProductionRecipe.Kind kind, int batches, int forgeSlot, long binPrice, double maximumFee, boolean buyInputs) {
        return queue(output,kind,batches,forgeSlot,binPrice,maximumFee,buyInputs,false);
    }
    public boolean queue(String output, ProductionRecipe.Kind kind, int batches, int forgeSlot, long binPrice, double maximumFee, boolean buyInputs,boolean marketPricing) {
        var actions = new LiveActions();
        if (run != null || FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued()) {
            actions.message("Finish the queued production work first."); return false;
        }
        try {
            automaticBudget=0;
            run = ProductionRun.start(environment(buyInputs,marketPricing), RecipeCatalog.instance(), output, kind, batches, forgeSlot, binPrice, maximumFee);
            lastReason = null;
            FeatureManager.INSTANCE.invalidateMarketReport();
            boolean buys = buyInputs || GoofyConfig.INSTANCE.productionBuysIngredients;
            actions.message("Queued production of " + RecipeCatalog.instance().name(output) + "."
                    + (buyInputs ? " Running it now as a test; the trading toggle or stop ends it." : " Use the trading toggle to run.")
                    + (buys ? " Missing inputs will be bought instantly within spendable capital." : " Inputs must already be in your inventory.")
                    + (binPrice > 0 ? marketPricing?" The BIN sale price is checked with Coflnet before opening Create Auction.":" The result is then listed as a BIN at " + binPrice + " coins."
                    : binPrice == ProductionRun.SELL_ON_BAZAAR ? " The result is then sold instantly on the Bazaar." : ""));
            return true;
        } catch (IllegalArgumentException invalid) {
            actions.message("Cannot queue production: " + invalid.getMessage()); return false;
        } catch (Exception failure) {
            run = null; Diagnostics.failure("production.run_queue_failed", failure);
            actions.message("Production journal could not be saved; no action performed."); return false;
        }
    }

    /** Claims a Forge or Kat job that is waiting in the journal, optionally listing the result. */
    public boolean claim(String jobPrefix, long binPrice, double maximumFee) {
        var actions = new LiveActions();
        if (run != null || FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued()) {
            actions.message("Finish the queued production work first."); return false;
        }
        try {
            automaticBudget=0;
            var env = environment(false);
            var matches = env.jobs().all().stream().filter(j -> j.id().startsWith(jobPrefix) && j.state() == ProductionJobs.State.WAITING
                    && j.account().equals(env.account()) && !j.recipeKey().startsWith("run:")).toList();
            if (matches.size() != 1) { actions.message("Name exactly one waiting Forge or Kat job; see production jobs."); return false; }
            var job = matches.getFirst();
            ItemMetadata pet = null;
            if (job.petUuid() != null) {
                var menu = env.menu();var placed = menu == null ? null : menu.slot(13);
                if (placed == null || placed.empty() || !job.petUuid().equals(placed.metadata().uuid())) {
                    actions.message("Open Kat's Pet Sitter showing that pet before claiming."); return false;
                }
                pet = placed.metadata();
            }
            run = ProductionRun.claim(env, RecipeCatalog.instance(), job, pet, binPrice, maximumFee);
            lastReason = null;
            actions.message("Queued claim of " + RecipeCatalog.instance().name(run.output()) + ". Use the trading toggle to run.");
            return true;
        } catch (IllegalArgumentException invalid) {
            actions.message("Cannot claim: " + invalid.getMessage()); return false;
        } catch (Exception failure) {
            run = null; Diagnostics.failure("production.claim_queue_failed", failure);
            actions.message("Production journal could not be saved; no action performed."); return false;
        }
    }

    private long nextAction;

    public void start() { running = true; paused = false; }
    public void resume() { paused = false; }
    public void pause() { paused = true; interrupt("Production paused"); }
    public void stop() { running = false; paused = false; interrupt("Production stopped"); }
    public boolean isRunning() { return running && !paused; }
    public boolean needsMenu() { return isRunning() && run != null && run.wantsMenu(); }
    public boolean canYield() { return run == null || !run.wantsMenu(); }

    /** Steps that need no menu (delegated crafts and listings, timers) advance here. */
    @Override public void poll() {
        if(!isRunning())return;
        if(run==null)selectAutomatic();
        if(run==null||run.wantsMenu())return;
        step(false);
    }

    @Override public void onTick() {
        if (!needsMenu()) return;
        step(true);
    }

    private void step(boolean ownsMenu) {
        long now = System.currentTimeMillis();
        // Reusing a clean craft menu advances metadata-only handoffs on the next tick.
        // Transactions outside the craft chain keep their configured action pacing.
        if (now < nextAction) return;
        nextAction = now + (run.hasReusableCraftMenu() && (run.stage()==ProductionLoop.Stage.PROCURE || run.stage()==ProductionLoop.Stage.PROCESS)
                ?50:com.goofy.goofyaddons.utils.ActionDelay.next());
        try {
            var result = run.tick(ownsMenu, now);
            switch (result) {
                case DONE -> {
                    if(run.hasReusableCraftMenu())new LiveActions().closeMenu();
                    new LiveActions().message("Production of " + RecipeCatalog.instance().name(run.output()) + " finished.");
                    Diagnostics.event("INFO", "production.run_finished", Map.of("job", run.jobId(), "output", run.output()));
                    run=null;automaticBudget=0;FeatureManager.INSTANCE.invalidateMarketReport();
                }
                case UNCERTAIN -> {
                    String reason = run.reason() == null ? "Production step could not be verified" : run.reason();
                    Diagnostics.event("WARN", "production.run_review", Map.of("job", run.jobId(), "reason", reason));
                    run = null;
                    FeatureManager.INSTANCE.safetyPause("Production needs review: " + reason);
                }
                case BLOCKED, PENDING -> {
                    if(result==ProductionLoop.Step.BLOCKED&&automaticBudget>0&&run.cancelUnstarted(run.reason())) {
                        Diagnostics.event("INFO","production.auto_reselect",Map.of("job",run.jobId(),"reason",run.reason()));
                        run=null;automaticBudget=0;nextSelection=System.currentTimeMillis()+5000;lastReason=null;
                        FeatureManager.INSTANCE.invalidateMarketReport();return;
                    }
                    if (result == ProductionLoop.Step.BLOCKED && run.reason() != null && !run.reason().equals(lastReason))
                        new LiveActions().message("Production waiting: " + run.reason());
                    lastReason = result == ProductionLoop.Step.BLOCKED ? run.reason() : lastReason;
                }
            }
        } catch (Exception failure) {
            Diagnostics.failure("production.run_failed", failure);
            run = null;
            FeatureManager.INSTANCE.safetyPause("Production run failed; inspect inventory and menus before restarting");
        }
    }

    /** A stop never guesses: unstarted runs are cancelled, waiting timers are kept, anything else goes to review. */
    private void interrupt(String why) {
        if(run==null){automaticBudget=0;return;}
        try {
            var jobs = FeatureManager.INSTANCE.crafting().productionJobs();
            var job = jobs.find(run.jobId()).orElse(null);
            if (job != null && job.state() != ProductionJobs.State.WAITING && job.state() != ProductionJobs.State.DONE) {
                boolean untouched = job.state() == ProductionJobs.State.PLANNED && run.stage() == ProductionLoop.Stage.PROCURE;
                jobs.put(job.withState(untouched ? ProductionJobs.State.CANCELLED : ProductionJobs.State.REVIEW,
                        why + "; inspect inventory, workstation and listings before requeueing"));
            }
        } catch (Exception failure) { Diagnostics.failure("production.journal_failed", failure); }
        run=null;automaticBudget=0;
    }

    private ProductionRun.Environment environment(boolean buyInputs) throws java.io.IOException {
        return environment(buyInputs,false);
    }
    private ProductionRun.Environment environment(boolean buyInputs,boolean marketPricing) throws java.io.IOException {
        var jobs = FeatureManager.INSTANCE.crafting().productionJobs();
        return new ProductionRun.Environment() {
            public ProductionJobs jobs() { return jobs; }
            public String account() { return new LiveWorld().username(); }
            public MenuSnapshot menu() { return new LiveWorld().menu(); }
            public boolean signOpen() { return new LiveWorld().signEditorOpen(); }
            public GameActions actions() { return new LiveActions(); }
            public boolean openCompactor(SlotView device){return LiveCompactorOpening.open(device);}
            public double purse() { return new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse(); }
            public double spendable() {
                double purse=purse(),available=Double.isFinite(purse)&&purse>=0?CapitalManager.INSTANCE.available(purse):0;
                return automaticBudget>0?Math.max(0,Math.min(available,automaticBudget-(run==null?0:run.spent()))):available;
            }
            public Map<String,Integer> skills() { return FeatureManager.INSTANCE.observedSkills(); }
            public Map<String,Integer> unlocks() { return FeatureManager.INSTANCE.observedUnlocks(); }
            public boolean requirementsPending() { return FeatureManager.INSTANCE.accountRequirementsPending(); }
            public String requirementsStatus() { return FeatureManager.INSTANCE.accountRequirementsStatus(); }
            public Set<String> occupied() { return CapitalManager.INSTANCE.occupiedProducts(); }
            public String procurementBlock() {
                if(automaticBudget<=0||run==null)return null;
                Double value=instantSellValue(run.output(),run.outputUnits()),remaining=run.remainingPurchaseCost();
                if(value==null||remaining==null)return "Fresh input/output depth is unavailable; automatic purchases are waiting";
                double currentCost=run.spent()+remaining;
                if(!run.hasVerifiedInputBasis())currentCost=Math.max(currentCost,automaticBudget); // Held materials are not free.
                if(currentCost>automaticBudget)return "Remaining ingredients exceed the selected craft budget; waiting before further purchases";
                return value*ProductionRun.SALE_FLOOR*(1-GoofyConfig.INSTANCE.bazaarTaxPercentage/100)-currentCost<GoofyConfig.INSTANCE.craftFlips.minimumProfit?
                    "Market moved below the craft profit target; waiting before further purchases":null;
            }
            public boolean buyingAllowed() { return buyInputs || GoofyConfig.INSTANCE.productionBuysIngredients; }
            public Double instantBuyCost(String id, int units) {
                var market = com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
                if (market == null || !market.has("products")) return null;
                var product = market.getAsJsonObject("products").get(id);
                return product != null && product.isJsonObject() ? ProductionPlanner.instantBuyCost(product.getAsJsonObject(), units) : null;
            }
            public Double instantSellValue(String id, int units) {
                var market = com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
                if (market == null || !market.has("products")) return null;
                var product = market.getAsJsonObject("products").get(id);
                return product != null && product.isJsonObject() ? ProductionPlanner.instantSellValue(product.getAsJsonObject(), units) : null;
            }
            public String name(String id) { return RecipeCatalog.instance().name(id); }
            public String queueCraft(String output, int batches) {
                var crafting = FeatureManager.INSTANCE.crafting();
                return (run!=null && run.isCraftRun()?crafting.queueForProduction(output,batches,null):crafting.queue(output,batches))?crafting.jobId():null;
            }
            public String queueCraftRecipe(String output,int batches,String key) {
                var crafting=FeatureManager.INSTANCE.crafting();return (run!=null && run.isCraftRun()?crafting.queueForProduction(output,batches,key):crafting.queue(output,batches,key))?crafting.jobId():null;
            }
            public boolean craftQueued() { return FeatureManager.INSTANCE.crafting().queued(); }
            public String queueListing(String product, long price, double maximumFee) {
                var auction = FeatureManager.INSTANCE.auction();
                return (marketPricing?auction.queueMarket(product,price,true,maximumFee):auction.queue(product,price,true,maximumFee)) ? auction.jobId() : null;
            }
            public boolean listingQueued() { return FeatureManager.INSTANCE.auction().queued(); }
            public void outputConfirmed(String id,String output,int units,Double cost) {
                com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE.acquire(id,"craft",output,"craft-output:"+id,units,cost);
            }
            public void saleConfirmed(String id,String output,int units,double proceeds) {
                com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE.sell(id,"craft",output,"craft-sale:"+id,units,proceeds);
            }
        };
    }
}
