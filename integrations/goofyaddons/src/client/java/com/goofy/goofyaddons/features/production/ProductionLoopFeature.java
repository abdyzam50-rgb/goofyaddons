package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import java.util.*;

/**
 * Runs bounded production loops (inputs, processing, claim, BIN listing) through the
 * same foreground menu scheduler as trading. The orchestration lives in {@link ProductionRun};
 * this class only connects it to the live game and the crafting and auction features.
 */
public final class ProductionLoopFeature implements Feature {
    private static final class Work {
        ProductionRun run;
        double budget;
        int inventorySlots;
        long nextAction;
        String lastReason;
        String lastObservation;
        long lastObserved,lastChanged;
        long lastTickAt=System.currentTimeMillis();
        final ProductionWaitEvidence waitEvidence=new ProductionWaitEvidence();
        String id(){return run.jobId();}
    }
    private final List<Work> work=new ArrayList<>();
    private Work foreground;
    private final CraftMenuLease<Work> menuLease=new CraftMenuLease<>();
    private boolean recoveringOrders;
    private final CraftFlipSelection selection=new CraftFlipSelection();
    private long nextSelection;
    private long nextSaleRestore;
    public boolean hasRetainedOrders(){return orders.retained();}
    public boolean hasRetainedOrders(String parent){return orders.retained(parent);}
    public boolean hasOrphanOrders(){return orders.orphaned(work.stream().map(Work::id).collect(java.util.stream.Collectors.toSet()));}
    private double availableFor(String owner,double purse) {
        if(!Double.isFinite(purse)||purse<0)return 0;
        var capital=CapitalManager.INSTANCE;
        var allocations=work.stream().map(w->new CraftPortfolioBudget.Allocation(w.id(),w.budget,w.run.spent(),orders.commitment(w.id()),orders.buyCommitment(w.id()))).toList();
        return CraftPortfolioBudget.available(capital.limit()-capital.committed(),purse-capital.reserve()-capital.pending(),allocations,owner);
    }
    double selectionBudget(double purse){return availableFor(null,purse)/Math.max(1,GoofyConfig.INSTANCE.craftFlips.maxActiveCrafts-work.size());}
    int reservedInventorySlots(MenuSnapshot menu) {
        return CraftPortfolioInventory.reserved(menu,work.stream().map(w->new CraftPortfolioInventory.Allocation(
            w.run.stage()==ProductionLoop.Stage.SELL?CraftFlipPlanner.inventorySlotsForProduct(w.run.output(),w.run.outputUnits()):w.inventorySlots,w.run.lockedProducts())).toList());
    }
    public void refreshSelection(){selection.refresh();}
    public Map<String,Object> craftPlan(){
        var view=new LinkedHashMap<String,Object>(selection.view());view.put("orders",orders.diagnosticState());view.put("queued",queued());view.put("activity",activity());
        view.put("selectionStatus",discoveryReason());view.put("eligibleRoutes",selection.calculated()?selection.routes().stream().filter(CraftFlipPlanner.Route::eligible).count():null);view.put("plannedBudget",work.stream().mapToDouble(w->w.budget).sum());view.put("spent",work.stream().mapToDouble(w->w.run.spent()).sum());
        view.put("maxActiveCrafts",GoofyConfig.INSTANCE.craftFlips.maxActiveCrafts);
        view.put("activeCrafts",work.stream().map(w->Map.of("job",w.id(),"output",w.run.output(),"stage",w.run.stage().name(),"reason",w.run.reason()==null?"":w.run.reason(),"budget",w.budget,"spent",w.run.spent(),"inventorySlots",w.inventorySlots)).toList());return view;
    }
    /** Complete current ownership plus durable blockers, including jobs absent from the live queue. */
    public Map<String,Object> diagnosticState() {
        var state=new LinkedHashMap<String,Object>();var world=new LiveWorld();
        state.put("schema",1);state.put("observedAt",world.now());state.put("running",running);state.put("paused",paused);
        state.put("recoveringOrders",recoveringOrders);state.put("foregroundJob",foreground==null?null:foreground.id());
        state.put("nextSelectionAt",nextSelection);state.put("selection",selection.diagnosticState());
        state.put("settings",GoofyConfig.INSTANCE.craftFlips);state.put("orders",orders.diagnosticState());
        state.put("lockedProducts",lockedProducts());
        state.put("craftDelegateQueued",FeatureManager.INSTANCE.crafting().queued());state.put("auctionDelegateQueued",FeatureManager.INSTANCE.auction().queued());
        state.put("activeRuns",work.stream().map(w->{var row=new LinkedHashMap<>(safeRunState(w.run));row.put("budget",w.budget);row.put("inventorySlots",w.inventorySlots);row.put("nextActionAt",w.nextAction);row.put("lastObservedAt",w.lastObserved);row.put("lastStateChangeAt",w.lastChanged);return row;}).toList());
        try {
            var jobs=FeatureManager.INSTANCE.crafting().journal().stream().filter(j->j.account().equals(world.username())).toList();
            state.put("journalCount",jobs.size());
            state.put("journal",jobs.stream().map(j->{var row=new LinkedHashMap<String,Object>();row.put("job",j.id());row.put("recipe",j.recipeKey());row.put("state",j.state().name());row.put("reason",j.reason());row.put("batches",j.batches());row.put("completedBatches",j.completedBatches());row.put("costBasis",j.costBasis());row.put("submittedAt",j.submittedAt());row.put("readyAt",j.readyAt());return row;}).toList());
            state.put("reviewJobs",jobs.stream().filter(j->j.state()==ProductionJobs.State.REVIEW).map(ProductionJobs.Job::id).toList());
            state.put("selectionBudget",selectionBudget(new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse()));
            state.put("reservedInventorySlots",reservedInventorySlots(world.menu()));
            state.put("emptyInventorySlots",world.menu()==null?null:world.menu().emptyInventorySlots());
        }catch(Exception unavailable){state.put("journalError",unavailable.getClass().getSimpleName()+": "+unavailable.getMessage());}
        return state;
    }
    private static Map<String,Object> safeRunState(ProductionRun run) {
        try{return run.diagnosticState();}
        catch(RuntimeException failed){return Map.of("job",run.jobId(),"observationError",failed.getClass().getSimpleName());}
    }
    private void checkWait(Work current,long now) {
        String signature=current.run.stage()+":"+current.run.reason()+":"+current.run.spent();
        if(current.waitEvidence.due(signature,now)) {
            var data=new LinkedHashMap<>(incident(current,"State unchanged for at least 120 seconds; this is evidence, not proof of a failed order"));
            data.put("lastTickAt",current.lastTickAt);data.put("craftDelegateQueued",FeatureManager.INSTANCE.crafting().queued());
            data.put("auctionDelegateQueued",FeatureManager.INSTANCE.auction().queued());
            Diagnostics.event("WARN","production.wait_observed",data);
        }
    }
    private void observeRun(Work current,ProductionLoop.Step result,boolean ownsMenu,long now) {
        String signature=current.run.stage()+":"+result+":"+current.run.reason()+":"+current.run.spent();
        boolean changed=!Objects.equals(signature,current.lastObservation);
        if(changed){current.lastObservation=signature;current.lastChanged=now;}
        if(!changed && now-current.lastObserved<30000)return;
        current.lastObserved=now;
        var data=new LinkedHashMap<>(safeRunState(current.run));data.put("result",result.name());
        data.put("ownsMenu",ownsMenu);data.put("foregroundJob",foreground==null?null:foreground.id());
        data.put("unchangedForMs",now-current.lastChanged);data.put("orders",orders.diagnosticState());
        Diagnostics.event("INFO","production.run_observed",data);
    }
    private Map<String,Object> incident(Work current,String reason) {
        var data=new LinkedHashMap<String,Object>();data.put("job",current.id());data.put("reason",reason);
        data.put("run",safeRunState(current.run));data.put("orders",orders.diagnosticState());
        data.put("menu",com.goofy.goofyaddons.diagnostics.DebugMenuCapture.describe(new LiveWorld().menu()));
        data.put("foregroundJob",foreground==null?null:foreground.id());return data;
    }

    public void showCraftPlans(){
        selection.refresh();var actions=new LiveActions();
        var rows=selection.currentRoutes();
        if(rows.isEmpty())actions.message("No feasible craft routes: "+discoveryReason()+". Minimum net profit "+Math.round(GoofyConfig.INSTANCE.craftFlips.minimumProfit)+", maximum batches "+GoofyConfig.INSTANCE.craftFlips.maxBatches+". See debug export for excluded candidate counts.");
        rows.stream().limit(10).forEach(r->actions.message(RecipeCatalog.instance().name(r.recipe().outputId())+" · "+r.venue()+" · "+r.strategy().label()+" · "+r.batches()+" batches ("+(r.recipe().outputCount()*r.batches())+" items) · estimated "+Math.round(r.score()*3600)+" coins/h · conservative net "+Math.round(r.profit())+" · "+(r.eligible()?"eligible":r.reason())));
    }
    private String discoveryReason() {
        if(FeatureManager.INSTANCE.accountRequirementsPending())return "Checking account prerequisites";
        var rows=selection.routes();
        if(rows.isEmpty())return selection.emptyReason();
        return rows.stream().filter(CraftFlipPlanner.Route::eligible).findAny().isPresent()?"Ready to select a craft":rows.getFirst().reason();
    }
    private void selectAutomatic() {
        if(!"CRAFT".equals(FeatureManager.INSTANCE.modeLabel())||System.currentTimeMillis()<nextSelection || recoveringOrders
                ||work.size()>=GoofyConfig.INSTANCE.craftFlips.maxActiveCrafts
                ||FeatureManager.INSTANCE.crafting().queued()||FeatureManager.INSTANCE.auction().queued())return;
        nextSelection=System.currentTimeMillis()+5000;
        var manager=FeatureManager.INSTANCE;
        if(manager.accountRequirementsPending() || hasOrphanOrders())return;
        var candidate=selection.currentRoutes().stream().filter(CraftFlipPlanner.Route::eligible).findFirst().orElse(null);
        if(candidate==null)return;
        try {
            var current=new Work();current.budget=candidate.capital();current.inventorySlots=candidate.inventorySlots();
            current.run=ProductionRun.startCraft(environment(current,true,false),RecipeCatalog.instance(),candidate.recipe(),candidate.batches(),candidate.binPrice(),candidate.fee(),candidate.strategy());
            work.add(current);
            new LiveActions().message("Automatic craft "+work.size()+"/"+GoofyConfig.INSTANCE.craftFlips.maxActiveCrafts+": "+RecipeCatalog.instance().name(current.run.output())+" · "+candidate.strategy().label()+" · "+candidate.batches()+" batches · budget "+Math.round(current.budget)+" · conservative net "+Math.round(candidate.profit()));
            Diagnostics.event("INFO","production.auto_selected",candidate.describe(RecipeCatalog.instance()));
        }catch(Exception failed){Diagnostics.failure("production.auto_queue_failed",failed);FeatureManager.INSTANCE.safetyPause("Automatic craft could not be journaled; review production jobs");}
    }
    private boolean running, paused;

    public String name() { return "Production"; }
    public boolean queued() { return !work.isEmpty() || recoveringOrders; }
    public String discoveryStatus() {
        return "Craft discovery · "+(selection.calculated()?selection.routes().stream().filter(CraftFlipPlanner.Route::eligible).count()+" feasible routes · ":"")+discoveryReason();
    }
    /** Lightweight presentation; no journal reads or diagnostic capture while rendering. */
    public boolean reviewRequired(){return selection.reviewId()!=null;}
    public String reviewSummary(){return reviewRequired()?selection.reviewId().substring(0,8)+" · "+Objects.toString(selection.reviewReason(),"Unverified production work"):"No blocking review";}
    public int activeCrafts(){return work.size();}
    public String displayActivity() {
        if(reviewRequired() && work.isEmpty())return "Review "+selection.reviewId().substring(0,8)+"\n"+Objects.toString(selection.reviewReason(),"Inspect retained work before restarting");
        if(recoveringOrders)return "Recovering craft orders\n"+orders.diagnosticState().get("step");
        if(work.isEmpty())return "Finding a craft\n"+discoveryReason();
        var current=foreground!=null && work.contains(foreground)?foreground:work.getFirst();
        return RecipeCatalog.instance().name(current.run.output())+"\n"+current.run.stage().name().toLowerCase(Locale.ROOT)+": "+Objects.toString(current.run.reason(),"Working");
    }
    public String activity() {
        if(recoveringOrders)return "Recovering craft positions · "+orders.diagnosticState().get("step");
        if(work.isEmpty())return "CRAFT".equals(FeatureManager.INSTANCE.modeLabel())?discoveryStatus():"No production run queued";
        var current=foreground!=null && work.contains(foreground)?foreground:work.getFirst();
        return "Craft queue "+work.size()+"/"+GoofyConfig.INSTANCE.craftFlips.maxActiveCrafts+" · "+current.run.output()+" · "+current.run.stage().name().toLowerCase(Locale.ROOT)+(current.run.reason()==null?"":" · "+current.run.reason());
    }
    public Set<String> lockedProducts() {
        var products=new HashSet<String>();for(var current:work)products.addAll(current.run.lockedProducts());return Set.copyOf(products);
    }

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
        if (queued() || hasOrphanOrders() || FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued()) {
            actions.message("Finish the queued production work first."); return false;
        }
        try {
            var current=new Work();
            current.run = ProductionRun.start(environment(current,buyInputs,marketPricing), RecipeCatalog.instance(), output, kind, batches, forgeSlot, binPrice, maximumFee);
            work.add(current);
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
            Diagnostics.failure("production.run_queue_failed", failure);
            actions.message("Production journal could not be saved; no action performed."); return false;
        }
    }

    /** Claims a Forge or Kat job that is waiting in the journal, optionally listing the result. */
    public boolean claim(String jobPrefix, long binPrice, double maximumFee) {
        var actions = new LiveActions();
        if (queued() || hasOrphanOrders() || FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued()) {
            actions.message("Finish the queued production work first."); return false;
        }
        try {
            var current=new Work();
            var env = environment(current,false,false);
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
            current.run = ProductionRun.claim(env, RecipeCatalog.instance(), job, pet, binPrice, maximumFee);
            work.add(current);
            actions.message("Queued claim of " + RecipeCatalog.instance().name(current.run.output()) + ". Use the trading toggle to run.");
            return true;
        } catch (IllegalArgumentException invalid) {
            actions.message("Cannot claim: " + invalid.getMessage()); return false;
        } catch (Exception failure) {
            Diagnostics.failure("production.claim_queue_failed", failure);
            actions.message("Production journal could not be saved; no action performed."); return false;
        }
    }

    private final com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders orders=new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders();

    public void start() { running = true; paused = false; }
    public void resume() { paused = false; }
    public void pause() { paused = true; interrupt("Production paused"); }
    public void stop() { running = false; paused = false; interrupt("Production stopped"); }
    public boolean isRunning() { return running && !paused; }
    private Work menuWork() {
        foreground=menuLease.select(work,w->w.run.wantsMenu(),FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued());
        return foreground;
    }
    public boolean needsMenu() { return isRunning() && (recoveringOrders?orders.needsMenu():menuWork()!=null); }
    public boolean canYield() { return !needsMenu(); }

    /** Recover ingredient buys and verified unsubmitted outputs; never select another craft. */
    public boolean recoverOrders() {
        if(queued() || FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued())return false;
        if(!orders.recoverBuy()){new LiveActions().message("No recoverable craft positions, or the order journal is unreadable. Evidence preserved.");return false;}
        recoveringOrders=true;
        new LiveActions().message("Recovering retained craft positions: claim/cancel ingredient buys and verify unsubmitted outputs against inventory and live orders. Evidence stays in manual review. No new purchases or sales.");
        return true;
    }
    private void recoverOrderStep(boolean ownsMenu) {
        try {
            var result=orders.tick(ownsMenu);
            if(result.failed()) {
                recoveringOrders=false;orders.stop();
                FeatureManager.INSTANCE.safetyPause("Craft order recovery needs review: "+result.reason());return;
            }
            if(!result.done())return;
            var evidence=orders.recoveryEvidence(new LiveWorld().username());
            // Preserve known input cost before releasing the child's ownership, also after a crash.
            FeatureManager.INSTANCE.crafting().productionJobs().put(evidence);
            if(!orders.acknowledge())throw new java.io.IOException("Could not save the recovered order handoff");
            Diagnostics.event("INFO","production.order_recovered",Map.of("job",evidence.id(),"reason",evidence.reason()));
            if(orders.retained())return;
            recoveringOrders=false;orders.stop();
            FeatureManager.INSTANCE.safetyPause("Craft positions recovered. Inspect inventory and recovered evidence, then use production acknowledge all before restarting crafts.");
        }catch(Exception failed) {
            recoveringOrders=false;orders.stop();Diagnostics.failure("production.order_recovery_failed",failed);
            FeatureManager.INSTANCE.safetyPause("Craft order recovery could not be saved; evidence retained");
        }
    }

    /** Waiting jobs advance independently; one foreground lease keeps menu transactions intact. */
    @Override public void poll() {
        if(!isRunning())return;
        restoreSavedSales();
        orders.poll();
        if(FeatureManager.INSTANCE.crafting().queued() || FeatureManager.INSTANCE.auction().queued())orders.yieldMenu();
        if(recoveringOrders){recoverOrderStep(false);return;}
        boolean manual=work.stream().anyMatch(w->w.budget<=0);
        for(var current:List.copyOf(work)) {
            checkWait(current,System.currentTimeMillis());
            if(!current.run.wantsMenu())step(current,false);
            if(!isRunning())return;
        }
        if(!manual)selectAutomatic();
    }
    private void restoreSavedSales(){
        long now=System.currentTimeMillis();
        if(!work.isEmpty() || recoveringOrders || now<nextSaleRestore || !"CRAFT".equals(FeatureManager.INSTANCE.modeLabel()))return;
        nextSaleRestore=now+5000;
        var sales=orders.savedSales();if(sales.isEmpty() || sales.size()>4)return;
        try {
            var jobs=FeatureManager.INSTANCE.crafting().productionJobs();var restored=new ArrayList<Work>();
            for(var sale:sales){
                var parent=jobs.find(sale.parent()).orElseThrow(()->new IllegalArgumentException("Saved sale parent is missing"));
                var current=new Work();current.run=ProductionRun.resumeSale(environment(current,false,false),RecipeCatalog.instance(),parent,sale.product(),sale.units(),true);
                current.budget=Math.max(1,current.run.spent());restored.add(current);
            }
            for(var current:restored)jobs.put(jobs.find(current.id()).orElseThrow().withState(ProductionJobs.State.LISTING,"Resuming retained submitted Bazaar sell offer; live ownership checks required"));
            work.addAll(restored);orders.resumeSavedSales();FeatureManager.INSTANCE.invalidateMarketReport();
            Diagnostics.event("INFO","production.sales_restored",Map.of("parents",restored.stream().map(Work::id).toList()));
            new LiveActions().message("Resumed monitoring "+restored.size()+" saved craft sell offers; no purchases or crafts repeated.");
        }catch(Exception failure){Diagnostics.failure("production.sale_restore_failed",failure);}
    }
    @Override public void onTick() {
        if(!needsMenu())return;
        if(recoveringOrders){recoverOrderStep(true);return;}
        var current=menuWork();if(current!=null)step(current,true);
    }
    private void step(Work current,boolean ownsMenu) {
        long now=System.currentTimeMillis();var run=current.run;
        if(now<current.nextAction)return;
        current.lastTickAt=now;
        current.nextAction=now+(run.hasReusableCraftMenu() && (run.stage()==ProductionLoop.Stage.PROCURE || run.stage()==ProductionLoop.Stage.PROCESS)?50:com.goofy.goofyaddons.utils.ActionDelay.next());
        try {
            expandProcurementBudget(current);
            var result=run.tick(ownsMenu,now);
            observeRun(current,result,ownsMenu,now);
            switch(result) {
                case DONE -> {
                    if(ownsMenu && run.hasReusableCraftMenu())new LiveActions().closeMenu();
                    new LiveActions().message("Production of "+RecipeCatalog.instance().name(run.output())+" finished.");
                    Diagnostics.event("INFO","production.run_finished",Map.of("job",run.jobId(),"output",run.output()));
                    work.remove(current);if(foreground==current)foreground=null;FeatureManager.INSTANCE.invalidateMarketReport();
                }
                case UNCERTAIN -> {
                    String reason=run.reason()==null?"Production step could not be verified":run.reason();
                    Diagnostics.event("WARN","production.run_review",incident(current,reason));
                    work.remove(current);if(foreground==current)foreground=null;
                    FeatureManager.INSTANCE.safetyPause("Production needs review: "+reason);
                }
                case BLOCKED,PENDING -> {
                    if(result==ProductionLoop.Step.BLOCKED && current.budget>0 && run.cancelUnstarted(run.reason())) {
                        Diagnostics.event("INFO","production.auto_reselect",Map.of("job",run.jobId(),"reason",run.reason()));
                        work.remove(current);if(foreground==current)foreground=null;nextSelection=now+5000;FeatureManager.INSTANCE.invalidateMarketReport();return;
                    }
                    if(result==ProductionLoop.Step.BLOCKED && run.reason()!=null && !run.reason().equals(current.lastReason))new LiveActions().message("Production waiting: "+run.reason());
                    if(result==ProductionLoop.Step.BLOCKED)current.lastReason=run.reason();
                }
            }
        }catch(Exception failure) {
            Diagnostics.failure("production.run_failed",failure);
            // Keep every unfinished run available to the interruption journal below.
            FeatureManager.INSTANCE.safetyPause("Production run failed; inspect inventory and menus before restarting");
        }
    }
    private void expandProcurementBudget(Work current) {
        var run=current.run;
        if(current.budget<=0 || !run.canExpandProcurementBudget())return;
        Double remaining=run.remainingPurchaseCost(),sale=run.expectedBazaarSale();
        if(remaining==null || sale==null)return;
        var cfg=GoofyConfig.INSTANCE;
        double expanded=CraftPortfolioBudget.expanded(current.budget,run.spent(),remaining,
            sale*ProductionRun.SALE_FLOOR*(1-cfg.bazaarTaxPercentage/100),cfg.craftFlips.minimumProfit,
            cfg.craftFlips.maximumCapital,availableFor(current.id(),new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse()));
        if(expanded<=current.budget)return;
        double previous=current.budget;current.budget=expanded;
        Diagnostics.event("INFO","production.budget_expanded",Map.of("job",current.id(),"previous",previous,
            "budget",expanded,"spent",run.spent(),"remaining",remaining,"maximumCapital",cfg.craftFlips.maximumCapital));
    }
    /** Stops preserve every job and every submitted child order; never clear portfolio ownership. */
    private void interrupt(String why) {
        try {
            var jobs=work.isEmpty()?null:FeatureManager.INSTANCE.crafting().productionJobs();
            for(var current:work) {
                var run=current.run;var job=jobs.find(run.jobId()).orElse(null);
                if(job!=null && job.state()!=ProductionJobs.State.WAITING && job.state()!=ProductionJobs.State.DONE) {
                    Diagnostics.event("WARN","production.run_interrupted",incident(current,why+"; prior journal state "+job.state()));
                    if(run.cancelUnstarted(why+"; verified no acquired inputs or pending order"))continue;
                    boolean untouched=job.state()==ProductionJobs.State.PLANNED && run.stage()==ProductionLoop.Stage.PROCURE;
                    jobs.put(job.withState(untouched?ProductionJobs.State.CANCELLED:ProductionJobs.State.REVIEW,why+"; inspect inventory, workstation and listings before requeueing"));
                }
            }
        }catch(Exception failure){Diagnostics.failure("production.journal_failed",failure);}
        orders.stop();work.clear();foreground=null;menuLease.clear();recoveringOrders=false;
    }

    private ProductionRun.Environment environment(Work current,boolean buyInputs,boolean marketPricing) throws java.io.IOException {
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
                double purse=purse(),available=availableFor(current.run==null?null:current.id(),purse);
                return current.budget>0?Math.max(0,Math.min(available,current.budget-(current.run==null?0:current.run.spent()))):available;
            }
            public Map<String,Integer> skills() { return FeatureManager.INSTANCE.observedSkills(); }
            public Map<String,Integer> unlocks() { return FeatureManager.INSTANCE.observedUnlocks(); }
            public boolean requirementsPending() { return FeatureManager.INSTANCE.accountRequirementsPending(); }
            public String requirementsStatus() { return FeatureManager.INSTANCE.accountRequirementsStatus(); }
            public Set<String> occupied() { var occupied=new HashSet<>(CapitalManager.INSTANCE.occupiedProducts());
                for(var other:work)if(other!=current)occupied.addAll(other.run.lockedProducts());return occupied; }
            public String procurementBlock() {
                if(current.budget<=0||current.run==null)return null;
                Double value=current.run.expectedBazaarSale(),remaining=current.run.remainingPurchaseCost();
                if(value==null||remaining==null)return "Fresh input/output depth is unavailable; automatic purchases are waiting";
                double currentCost=current.run.spent()+remaining;
                if(!current.run.hasVerifiedInputBasis())currentCost=Math.max(currentCost,current.budget); // Held materials are not free.
                if(GoofyConfig.INSTANCE.craftFlips.maximumCapital>0 && ProductionPlanner.exceedsFunds(currentCost,GoofyConfig.INSTANCE.craftFlips.maximumCapital))
                    return "Remaining ingredients exceed the configured craft capital limit; waiting before further purchases";
                if(ProductionPlanner.exceedsFunds(currentCost,current.budget))return "Remaining ingredients exceed the selected craft budget; waiting before further purchases";
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
            public Double orderBuyCost(String id,int units){return strategyQuote(id,units,true);}
            public Double offerSellValue(String id,int units){return strategyQuote(id,units,false);}
            private Double strategyQuote(String id,int units,boolean buy){
                var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
                if(market==null)return null;var product=market.getAsJsonObject("products").getAsJsonObject(id);
                return buy?BazaarStrategy.ORDER_OFFER.buy(product,units):BazaarStrategy.ORDER_OFFER.sell(product,units);
            }
            public boolean queueOrder(String id,int units,double price,boolean buy,double limit){return orders.queue(current.id(),id,name(id),units,price,buy,limit);}
            public boolean queueIngredientOrders(List<com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.IngredientOrder> basket){return orders.queueBasket(current.id(),basket);}
            public com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result tickOrder(boolean ownsMenu){return orders.tick(current.id(),ownsMenu);}
            public boolean acknowledgeOrder(){return orders.acknowledge(current.id());}
            public boolean orderNeedsMenu(){return orders.needsMenu(current.id());}
            public String name(String id) { return RecipeCatalog.instance().name(id); }
            public String queueCraft(String output, int batches) {
                if(foreground!=null && foreground!=current && foreground.run.wantsMenu())return null;
                var crafting = FeatureManager.INSTANCE.crafting();
                return (current.run!=null && current.run.isCraftRun()?crafting.queueForProduction(output,batches,null):crafting.queue(output,batches))?crafting.jobId():null;
            }
            public String queueCraftRecipe(String output,int batches,String key) {
                if(foreground!=null && foreground!=current && foreground.run.wantsMenu())return null;
                var crafting=FeatureManager.INSTANCE.crafting();return (current.run!=null && current.run.isCraftRun()?crafting.queueForProduction(output,batches,key):crafting.queue(output,batches,key))?crafting.jobId():null;
            }
            public boolean craftQueued() { return FeatureManager.INSTANCE.crafting().queued(); }
            public String queueListing(String product, long price, double maximumFee) {
                if(foreground!=null && foreground!=current && foreground.run.wantsMenu())return null;
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
