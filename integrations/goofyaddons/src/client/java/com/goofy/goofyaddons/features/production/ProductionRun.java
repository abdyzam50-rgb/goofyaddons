package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.production.ProductionLoop.Outcome;
import com.goofy.goofyaddons.features.production.ProductionLoop.Stage;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;

/**
 * One production run bound to the executors that already prove each step: optional
 * instant buys for missing inputs, crafting or a Forge/Kat submission, the timed wait and
 * claim, and an optional BIN listing of the output.
 *
 * <p>Crafting and listing are delegated to the existing crafting and auction features, so
 * their menu handling and journals are reused unchanged. Instant buys and Forge/Kat steps
 * act only while this run owns the menu, and Forge/Kat steps only once the player has the
 * workstation open. The parent job in the production journal records each stage boundary;
 * a restart turns an interrupted buy, craft or submission into review, as for every other
 * production job.
 */
public final class ProductionRun implements ProductionLoop.Ports {
    /** A BIN price meaning "instant-sell the output on the Bazaar" instead of listing it. */
    public static final long SELL_ON_BAZAAR = -1;
    /** How far below the fresh quote an instant sale may fill, for the book moving before the click. */
    static final double SALE_FLOOR = 0.97;

    /** What the run needs from the game and the other features. */
    public interface Environment {
        ProductionJobs jobs();
        String account();
        MenuSnapshot menu();
        boolean signOpen();
        GameActions actions();
        double purse();
        /** Coins the capital manager allows a new purchase to spend. */
        double spendable();
        Map<String,Integer> skills();
        default Map<String,Integer> unlocks() { return Map.of(); }
        default boolean requirementsPending() { return false; }
        default String requirementsStatus() { return null; }
        /** Products owned by trader positions or other queued work. */
        Set<String> occupied();
        boolean buyingAllowed();
        default boolean openCompactor(SlotView device){return false;}
        default String procurementBlock(){return null;}
        /** Instant-buy cost of these units from fresh quotes, or null when unknown. */
        Double instantBuyCost(String id, int units);
        /** Instant-sell value of these units from fresh quotes, before tax, or null when unknown. */
        Double instantSellValue(String id, int units);
        default Double orderBuyCost(String id,int units){return null;}
        /** A diagnostics event about this run; tests ignore it. */
        default void event(String level,String type,Map<String,Object> data){}
        /** Whether a buy order for this input can be expected to fill; inputs that can't are bought instantly. */
        default boolean orderable(String id){return true;}
        default Double offerSellValue(String id,int units){return null;}
        default boolean queueOrder(String id,int units,double price,boolean buy,double limit){return false;}
        default boolean queueIngredientOrders(List<com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.IngredientOrder> basket){
            if(basket.size()!=1)return false;var order=basket.getFirst();return queueOrder(order.product(),order.units(),order.price(),true,order.limit());
        }
        default com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.Result tickOrder(boolean ownsMenu){return null;}
        default boolean acknowledgeOrder(){return false;}
        default boolean orderNeedsMenu(){return true;}
        String name(String id);
        /** Queues a craft through the crafting feature; returns its job id or null. */
        String queueCraft(String output, int batches);
        default String queueCraftRecipe(String output,int batches,String key){return queueCraft(output,batches);}
        boolean craftQueued();
        default String craftActivity(){return "Crafting";}
        /** Queues a published BIN listing through the auction feature; returns its job id or null. */
        String queueListing(String product, long price, double maximumFee);
        boolean listingQueued();
        /** The storage ledger, whenever it is readable; stored units count as held. */
        default ProductionStorage storage(){return null;}
        /** Whether new purchases may overflow into storage. */
        default boolean storageEnabled(){return false;}
        default List<ProductionStorage.Page> storagePages(){return List.of();}
        /** Products whose partial order claims wait for this run to store them. */
        default List<String> orderAwaitingStorage(){return List.of();}
        /** Products this run's orders let it move freely. */
        default Set<String> orderStorable(){return Set.of();}
        /** Products this run has order positions for. */
        default Set<String> orderTracked(){return Set.of();}
        default boolean orderStored(String product,int units){return false;}
        /** No order transaction is mid-menu, so storage may be opened. */
        default boolean orderIdle(){return true;}
        default void outputConfirmed(String id,String output,int units,Double cost){}
        default void saleConfirmed(String id,String output,int units,double proceeds){}
    }

    private final Environment env;
    private final RecipeCatalog catalog;
    private final String output;
    private final ProductionRecipe.Kind kind;
    private final int batches, forgeSlot;
    private final long binPrice;
    private final double maximumFee;
    private final String jobId;
    private ProductionLoop loop;
    private boolean ownsMenu;
    private BazaarInstantBuy buying;
    private BazaarInstantSell selling;
    private BazaarStrategy strategy=BazaarStrategy.INSTANT_INSTANT;
    private boolean orderActive,orderCostRecorded;
    private long orderRetryAfter;
    private boolean waitingForOrderRetry;
    private boolean waitingForProcurement;
    private boolean verifiedEmptyOrderHandoff;
    private ProductionRecipe recipe;
    private String craftJob, listingJob, workstationJob;
    private String ingredientCraftJob;
    private ForgeNavigation navigation;
    private WorkstationExecutor workstation;
    private double spent;
    private boolean basisUnknown;
    private Set<String> sellInputLocks;
    private ProductionStorage.Move storing;
    /** Full storage or inventory used to reopen every page each tick; wait between attempts instead. */
    static final long STORAGE_RETRY_MS=60_000;static final int STORAGE_ATTEMPTS=5;
    private long storageRetryAt;private int storageFailures;private String storageFailure;
    private Set<String> storingForOrder=Set.of();
    private int craftedBatches,roundBatches;
    private final CompactorClearance compactorClearance=new CompactorClearance();
    /** Which halves of a Bazaar trade test this run does; null for every production run. */
    public enum TradePart { BUY, SELL, BOTH; public boolean buys(){return this!=SELL;} public boolean sells(){return this!=BUY;} }
    private TradePart trade;
    private int tradeUnits,tradeHeldAtStart;

    private ProductionRun(Environment env, RecipeCatalog catalog, String output, ProductionRecipe.Kind kind, ProductionRecipe recipe,
                          int batches, int forgeSlot, long binPrice, double maximumFee, String jobId) {
        this.env = env; this.catalog = catalog; this.output = output; this.kind = kind; this.recipe = recipe;
        this.batches = batches; this.forgeSlot = forgeSlot; this.binPrice = binPrice; this.maximumFee = maximumFee; this.jobId = jobId;
        var menu=env.menu();
        basisUnknown=menu==null||lockedProducts().stream().anyMatch(id->menu.countInInventory(id)>0);
    }

    /** Plans and journals a new run. Throws with a player-readable reason when it cannot start. */
    public static ProductionRun start(Environment env, RecipeCatalog catalog, String output, ProductionRecipe.Kind kind,
                                      int batches, int forgeSlot, long binPrice, double maximumFee) throws Exception {
        if (!ProductionRecipe.validId(output)) throw new IllegalArgumentException("Unknown product " + output);
        var options = catalog.forOutput(output).stream().filter(r -> r.kind() == kind).toList();
        if (options.isEmpty()) throw new IllegalArgumentException("No supported " + kind.name().toLowerCase(Locale.ROOT) + " recipe for " + output);
        boolean timed = kind != ProductionRecipe.Kind.CRAFT;
        if (timed && batches != 1) throw new IllegalArgumentException("Forge and Kat runs process one batch");
        if (!timed && (batches < 1 || batches > 64)) throw new IllegalArgumentException("Craft runs take 1–64 batches");
        if (kind == ProductionRecipe.Kind.FORGE && (forgeSlot < 0 || forgeSlot > 6)) throw new IllegalArgumentException("Forge slot must be 1–7");
        if (binPrice < SELL_ON_BAZAAR || binPrice > 1_000_000_000_000L || !Double.isFinite(maximumFee) || maximumFee < 0) throw new IllegalArgumentException("Invalid BIN price or fee");
        var occupied = env.occupied();
        if (occupied.contains(output)) throw new IllegalArgumentException(output + " belongs to a trader position or queued work");
        ProductionRecipe chosen = timed ? options.getFirst() : null;
        String id = UUID.randomUUID().toString();
        // KAT runs never buy: the pet is the player's own, and its materials are bought by hand.
        boolean procure = kind != ProductionRecipe.Kind.KAT;
        var run = new ProductionRun(env, catalog, output, kind, chosen, batches, forgeSlot, binPrice, maximumFee, id);
        if (binPrice == SELL_ON_BAZAAR && run.held().getOrDefault(output, 0) > 0)
            throw new IllegalArgumentException("move the " + env.name(output) + " you already hold out of your inventory first; an instant sale sells every unit held");
        var plan = new ProductionLoop.Plan(procure, timed, binPrice != 0);
        env.jobs().put(new ProductionJobs.Job(id, "run:" + kind.name().toLowerCase(Locale.ROOT) + ":" + output, env.account(),
                procure ? ProductionJobs.State.PLANNED : ProductionJobs.State.PROCESSING, batches, timed ? Math.max(forgeSlot, -1) : -1,
                0, 0, 0.0, null, null, "Production run planned"));
        run.loop = new ProductionLoop(plan, run);
        return run;
    }

    static ProductionRun startCraft(Environment env,RecipeCatalog catalog,ProductionRecipe recipe,int batches,long price,double fee)throws Exception {
        if(recipe.kind()!=ProductionRecipe.Kind.CRAFT||!catalog.byKey(recipe.key()).filter(recipe::equals).isPresent())throw new IllegalArgumentException("Unverified craft recipe");
        var run=start(env,catalog,recipe.outputId(),recipe.kind(),batches,-1,price,fee);run.recipe=recipe;return run;
    }
    static ProductionRun startCraft(Environment env,RecipeCatalog catalog,ProductionRecipe recipe,int batches,long price,double fee,BazaarStrategy strategy)throws Exception {
        var run=startCraft(env,catalog,recipe,batches,price,fee);run.strategy=strategy;return run;
    }
    /**
     * A Bazaar trade test: buy and/or sell units of one product with the chosen strategy, and no
     * crafting. It uses the same instant-buy, buy-order, instant-sell and sell-offer steps as a
     * production run, so each can be tried in game on its own. Selling sells every unit held.
     */
    static ProductionRun startTrade(Environment env,RecipeCatalog catalog,String product,int units,TradePart part,BazaarStrategy strategy)throws Exception {
        if(!ProductionRecipe.validId(product))throw new IllegalArgumentException("Unknown product "+product);
        if(part==null||strategy==null)throw new IllegalArgumentException("Choose what to test and how");
        if(env.occupied().contains(product))throw new IllegalArgumentException(product+" belongs to a trader position or queued work");
        var menu=env.menu();
        if(menu==null)throw new IllegalArgumentException("open your inventory view first; the inventory could not be read");
        String id=UUID.randomUUID().toString();
        var run=new ProductionRun(env,catalog,product,ProductionRecipe.Kind.CRAFT,null,1,-1,SELL_ON_BAZAAR,0,id);
        run.trade=part;run.strategy=strategy;
        int held=run.held().getOrDefault(product,0);
        if(part==TradePart.BOTH && held>0)
            throw new IllegalArgumentException("move the "+env.name(product)+" you already hold out of your inventory first; the sale sells every unit held");
        if(part==TradePart.SELL) {
            if(held==0)throw new IllegalArgumentException("put the "+env.name(product)+" you want to sell in your inventory first");
            units=held;
        }else {
            if(units<1||units>4096)throw new IllegalArgumentException("buy between 1 and 4096 units");
            int slots=CraftFlipPlanner.inventorySlotsForProduct(catalog,product,units);
            if(slots>menu.emptyInventorySlots())
                throw new IllegalArgumentException(units+" "+env.name(product)+" need "+slots+" free inventory slots and "+menu.emptyInventorySlots()+" are free");
        }
        run.tradeUnits=units;run.tradeHeldAtStart=part==TradePart.SELL?0:held;run.basisUnknown=part==TradePart.SELL;
        env.jobs().put(new ProductionJobs.Job(id,"run:trade:"+product,env.account(),part.buys()?ProductionJobs.State.PLANNED:ProductionJobs.State.PROCESSING,
                units,-1,0,0,0.0,null,null,"Bazaar trade test planned: "+part.name().toLowerCase(Locale.ROOT)+" "+units+" "+product+" · "+strategy.label()));
        run.loop=new ProductionLoop(new ProductionLoop.Plan(part.buys(),false,part.sells()),run);
        return run;
    }
    public boolean isTradeTest(){return trade!=null;}
    public BazaarStrategy strategy(){return strategy;}
    public Double expectedBazaarSale(){return strategy.instantSell?env.instantSellValue(output,outputUnits()):env.offerSellValue(output,outputUnits());}
    private Double purchaseCost(String id,int units){return strategy.instantBuy?env.instantBuyCost(id,units):env.orderBuyCost(id,units);}
    public double spent(){return spent;}
    public int outputUnits(){return trade!=null?tradeUnits:Math.multiplyExact(batches,recipe==null?1:recipe.outputCount());}

    /** A claim-only run for a Forge or Kat job already waiting in the journal. */
    public static ProductionRun claim(Environment env, RecipeCatalog catalog, ProductionJobs.Job waiting, ItemMetadata pet,
                                      long binPrice, double maximumFee) throws Exception {
        var recipe = catalog.byKey(waiting.recipeKey()).orElseThrow(() -> new IllegalArgumentException("Unknown recipe " + waiting.recipeKey()));
        if (recipe.kind() == ProductionRecipe.Kind.CRAFT || waiting.state() != ProductionJobs.State.WAITING)
            throw new IllegalArgumentException("Only a waiting Forge or Kat job can be claimed");
        var run = new ProductionRun(env, catalog, recipe.outputId(), recipe.kind(), recipe, 1, waiting.workstationSlot(), binPrice, maximumFee,
                "claim-" + waiting.id());
        run.workstationJob = waiting.id();
        run.workstation = new WorkstationExecutor(recipe, waiting.id(), env.jobs(), pet);
        env.jobs().put(new ProductionJobs.Job(run.jobId, "run:claim:" + recipe.outputId(), env.account(), ProductionJobs.State.WAITING, 1,
                waiting.workstationSlot(), waiting.submittedAt(), waiting.readyAt(), waiting.costBasis(), waiting.petUuid(), null, "Claim run for " + waiting.id()));
        run.loop = new ProductionLoop(new ProductionLoop.Plan(false, true, binPrice > 0), Stage.AWAIT, run);
        return run;
    }

    /** Reselect untouched plans or a fully verified zero-input order handoff; never uncertain intent. */
    public boolean cancelUnstarted(String reason)throws Exception {
        if(stage()!=Stage.PROCURE||spent!=0||buying!=null||orderActive||ingredientCraftJob!=null||craftJob!=null)return false;
        var job=env.jobs().find(jobId).orElse(null);
        if(job==null)return false;
        boolean emptyVerified=verifiedEmptyOrderHandoff && job.state()==ProductionJobs.State.BUYING
            && !compactorClearance.inProgress() && env.menu()!=null && env.menu().cursorEmpty()
            && held().entrySet().stream().noneMatch(e->e.getValue()>0 && lockedProducts().contains(e.getKey()));
        if(job.state()!=ProductionJobs.State.PLANNED && !emptyVerified)return false;
        env.jobs().put(job.withState(ProductionJobs.State.CANCELLED,reason));return true;
    }

    public String jobId() { return jobId; }
    public Stage stage() { return loop.stage(); }
    public String reason() { return loop.reason(); }
    public boolean finished() { return loop.finished(); }
    public String output() { return output; }
    public boolean isCraftRun(){return kind==ProductionRecipe.Kind.CRAFT;}
    public boolean hasVerifiedInputBasis(){return !basisUnknown;}
    /** Reattach a stopped sale to its original parent ID; never replay acquisition or crafting. */
    static ProductionRun resumeSale(Environment env,RecipeCatalog catalog,ProductionJobs.Job job,String product,int units) {
        return resumeSale(env,catalog,job,product,units,false);
    }
    /** The caller may prove an intact submitted sell child before generic interrupted-parent recovery. */
    static ProductionRun resumeSale(Environment env,RecipeCatalog catalog,ProductionJobs.Job job,String product,int units,boolean verifiedSaleChild) {
        boolean stopped=job.state()==ProductionJobs.State.REVIEW && job.reason()!=null
            && (job.reason().startsWith("Production stopped;")||job.reason().startsWith("Production paused;"));
        boolean interrupted=verifiedSaleChild && job.state()==ProductionJobs.State.REVIEW
            && "Interrupted operation; verify live inventory, workstation and listings".equals(job.reason());
        boolean tradeTest=job.recipeKey().equals("run:trade:"+product);
        if(!job.account().equals(env.account()) || !(tradeTest || job.recipeKey().equals("run:craft:"+product))
            || !(stopped || interrupted || job.state()==ProductionJobs.State.LISTING || job.state()==ProductionJobs.State.SELLING))
            throw new IllegalArgumentException("Saved sale parent requires manual review");
        if(tradeTest) {
            if(job.batches()!=units)throw new IllegalArgumentException("Saved sale quantity differs");
            var run=new ProductionRun(env,catalog,product,ProductionRecipe.Kind.CRAFT,null,1,-1,SELL_ON_BAZAAR,0,job.id());
            run.trade=TradePart.SELL;run.tradeUnits=units;
            run.spent=job.costBasis()==null?0:job.costBasis();run.basisUnknown=true;
            run.strategy=BazaarStrategy.ORDER_OFFER;run.orderActive=true;
            run.loop=new ProductionLoop(new ProductionLoop.Plan(false,false,true),Stage.SELL,run);
            return run;
        }
        var recipe=catalog.forOutput(product).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT
            && (long)r.outputCount()*job.batches()==units).findFirst().orElseThrow(()->new IllegalArgumentException("Saved sale quantity/recipe differs"));
        var run=new ProductionRun(env,catalog,product,ProductionRecipe.Kind.CRAFT,recipe,job.batches(),-1,SELL_ON_BAZAAR,0,job.id());
        run.spent=job.costBasis()==null?0:job.costBasis();run.basisUnknown=job.costBasis()==null;
        run.strategy=BazaarStrategy.ORDER_OFFER;run.orderActive=true;
        run.captureSellInputLocks();
        run.loop=new ProductionLoop(new ProductionLoop.Plan(false,false,true),Stage.SELL,run);
        return run;
    }
    /** Allocation changes must not overlap a submitted or delegated transaction. */
    public boolean canExpandProcurementBudget(){
        return stage()==Stage.PROCURE && spent>0 && hasVerifiedInputBasis() && !orderActive && buying==null
            && ingredientCraftJob==null && craftJob==null && !compactorClearance.inProgress() && !waitingForOrderRetry;
    }

    /** Read-only evidence for exports: never tick a child, acknowledge an order or save a job. */
    public Map<String,Object> diagnosticState() {
        var state=new LinkedHashMap<String,Object>();
        state.put("job",jobId);state.put("output",output);state.put("recipe",recipe==null?null:recipe.key());
        state.put("stage",stage().name());state.put("reason",reason());state.put("batches",batches);
        state.put("strategy",strategy.name());state.put("spent",spent);state.put("inputBasisVerified",hasVerifiedInputBasis());
        state.put("verifiedEmptyOrderHandoff",verifiedEmptyOrderHandoff);
        state.put("orderActive",orderActive);state.put("orderCostRecorded",orderCostRecorded);
        state.put("waitingForOrderRetry",waitingForOrderRetry);state.put("retryAt",orderRetryAfter);
        state.put("waitingForProcurement",waitingForProcurement);state.put("compactorInProgress",compactorClearance.inProgress());
        state.put("ingredientCraftJob",ingredientCraftJob);state.put("craftJob",craftJob);
        state.put("listingJob",listingJob);state.put("workstationJob",workstationJob);
        state.put("instantBuyItem",buying==null?null:buying.productId());state.put("instantBuySubmitted",buying!=null&&buying.submitted());
        state.put("held",held());state.put("lockedProducts",lockedProducts());
        var storage=env.storage();state.put("stored",storage==null?Map.of():storage.stored(jobId));
        state.put("storing",storing==null?null:storing.describe());state.put("craftedBatches",craftedBatches);state.put("roundBatches",roundBatches);
        state.put("storageFailures",storageFailures);state.put("storageRetryAt",storageRetryAt);
        if(trade!=null){state.put("tradeTest",trade.name());state.put("tradeUnits",tradeUnits);state.put("tradeHeldAtStart",tradeHeldAtStart);}
        try {
            state.put("wantsMenu",wantsMenu());state.put("spendable",env.spendable());
            state.put("procurementBlock",env.procurementBlock());
            state.put("missingPurchases",preparation().purchases());
            state.put("remainingPurchaseCost",remainingPurchaseCost());state.put("expectedBazaarSale",expectedBazaarSale());
        }catch(RuntimeException unavailable){state.put("observationError",unavailable.getClass().getSimpleName()+": "+unavailable.getMessage());}
        return state;
    }

    /** Products this run must keep away from traders. */
    public Set<String> lockedProducts() {
        if(loop!=null && loop.stage()==Stage.SELL && sellInputLocks!=null) {
            var ids=new HashSet<>(sellInputLocks);ids.add(output);return Set.copyOf(ids);
        }
        return requiredProducts();
    }

    private Set<String> requiredProducts() {
        if(trade!=null)return Set.of(output);
        var ids = new HashSet<String>();ids.add(output);
        if (recipe != null) ids.addAll(recipe.ingredients().keySet());
        else catalog.forOutput(output).stream().filter(r -> r.kind() == kind).forEach(r -> ids.addAll(r.ingredients().keySet()));
        ids.addAll(IngredientPreparation.dependencies(catalog,ids));
        return Set.copyOf(ids);
    }

    /** Freeze ownership at the verified craft boundary, before another run buys shared inputs. */
    private void captureSellInputLocks() {
        if(sellInputLocks!=null)return;
        var menu=env.menu();var remaining=new HashSet<>(requiredProducts());remaining.remove(output);
        if(menu!=null)remaining.removeIf(id->menu.countInInventory(id)==0);
        sellInputLocks=Set.copyOf(remaining);
    }

    public boolean hasReusableCraftMenu(){return kind==ProductionRecipe.Kind.CRAFT && CraftingExecutor.reusableMenu(env.menu());}

    /** True while this step owns a menu, including a verified craft-to-craft handoff. */
    public boolean wantsMenu() {
        if (finished() || waitingForOrderRetry || waitingForProcurement) return false;
        if(storing!=null)return true;
        if(orderActive)return env.orderNeedsMenu() || env.storage()!=null && !env.orderAwaitingStorage().isEmpty();
        return switch (loop.stage()) {
            case PROCURE -> !env.craftQueued() && (compactorClearance.needsMenu(env.menu()) || hasReusableCraftMenu() || ingredientCraftJob==null && (buying != null || env.buyingAllowed() && !preparation().purchases().isEmpty()));
            case PROCESS -> trade!=null ? false : kind==ProductionRecipe.Kind.CRAFT ? !env.craftQueued() && (hasReusableCraftMenu() || craftJob==null && craftsInRounds()) : workstationMenuOpen();
            case CLAIM -> workstationMenuOpen();
            case SELL -> binPrice == SELL_ON_BAZAAR || hasReusableCraftMenu() && !env.listingQueued();
            default -> false;
        };
    }

    /** Advances the run. {@code menuOwner} says whether this run may click this tick. */
    public ProductionLoop.Step tick(boolean menuOwner, long now) {
        ownsMenu = menuOwner;
        try { return loop.tick(now); } finally { ownsMenu = false; }
    }

    // ---- Stages ------------------------------------------------------------------------

    @Override public Outcome procure(long now) {
        if(storing!=null)return storageStep(now);
        if(orderActive) {
            var awaiting=env.storage()==null?List.<String>of():env.orderAwaitingStorage();
            if(!awaiting.isEmpty()) {
                // A claim filled the inventory: store it, then the order claims the rest.
                if(!ownsMenu || !env.orderIdle())return Outcome.pending("Claimed what fits; storing it before claiming the rest");
                var products=new HashSet<>(movableInputs());products.addAll(awaiting);
                var wait=storageWait(now);if(wait!=null)return wait;
                storingForOrder=Set.copyOf(awaiting);
                storing=ProductionStorage.Move.deposit(jobId,env.storage(),env.storagePages(),products);
                return storageStep(now);
            }
            var result=env.tickOrder(ownsMenu);
            if(result==null || result.failed())return Outcome.uncertain(result==null?"Input order unavailable":result.reason());
            if(!result.done())return Outcome.pending("Ingredient order · "+result.reason());
            try {
                if(!orderCostRecorded){spent+=result.cost();env.jobs().put(parent().withCost(spent,"Ingredient order claim verified"));orderCostRecorded=true;}
                if(!env.acknowledgeOrder())return Outcome.uncertain("Claimed ingredient handoff could not be persisted");
                orderActive=false;orderCostRecorded=false;
                verifiedEmptyOrderHandoff=result.units()==0 && result.cost()==0;
                if(result.rejected()){
                    waitingForOrderRetry=true;orderRetryAfter=now+30000;
                    // A safe pre-submission rejection must reach the scheduler's empty-run
                    // reselection guard. PENDING bypasses that guard and pins the old budget.
                    // Runs with acquired inputs still fail cancelUnstarted and retain the delay.
                    return Outcome.blocked("Ingredient quote changed; recalculating an empty plan or refreshing remaining inputs: "+result.reason());
                }
                return Outcome.pending(result.units()>0
                    ? "Claimed ingredients; checking remaining inputs" : "Ingredient order cancelled; checking replacement budget and profit");
            }catch(Exception failure){return Outcome.uncertain("Ingredient order cost could not be persisted");}
        }
        if(now<orderRetryAfter)return Outcome.pending("Waiting for fresh ingredient prices; retrying in "+Math.max(1,(orderRetryAfter-now+999)/1000)+" seconds");
        waitingForOrderRetry=false;
        if(ingredientCraftJob!=null) {
            if(env.craftQueued())return Outcome.pending(env.craftActivity());
            var result=childFinished(ingredientCraftJob,Set.of(ProductionJobs.State.OUTPUT_READY,ProductionJobs.State.DONE),"Ingredient craft");
            if(result!=Outcome.DONE)return result;
            ingredientCraftJob=null;
            return Outcome.pending("Intermediate craft verified; checking remaining inputs");
        }
        if (buying != null) {
            if (!ownsMenu) return Outcome.pending("Waiting to buy " + env.name(buying.productId()));
            if(!buying.submitted()) {
                String changed=env.procurementBlock();
                var basket=ProductionPlanner.purchaseQuote(preparation().purchases(),this::purchaseCost);
                if(changed==null&&!basket.complete())changed="No fresh Bazaar quote covers all remaining inputs: "+env.name(basket.blockedProduct());
                if(changed==null&&ProductionPlanner.exceedsFunds(basket.total(),env.spendable()))changed="All remaining ingredients would exceed spendable capital; nothing bought";
                if(changed!=null){
                    // Closing an amount editor may submit its text. Keep it open and wait instead.
                    if(!env.signOpen()){buying=null;env.actions().closeMenu();}
                    return Outcome.blocked(changed);
                }
            }
            var result = buying.tick(env.menu(), env.signOpen(), env.actions(), env.purse(), now);
            switch (result) {
                case BOUGHT -> {
                    var evidence=new LinkedHashMap<String,Object>();evidence.put("job",jobId);evidence.put("product",buying.productId());
                    evidence.put("requested",buying.requested());evidence.put("bought",buying.amount());evidence.put("spent",buying.spent());
                    evidence.put("limit",buying.maximumCost());evidence.put("usedStash",buying.usedStash());evidence.put("purseAfter",env.purse());
                    env.event("INFO","production.instant_buy_verified",evidence);
                    addCost(buying); buying = null; env.actions().closeMenu(); return Outcome.pending("Bought inputs; checking the rest"); }
                case UNCERTAIN -> { return Outcome.uncertain(buying.failure()); }
                case BLOCKED -> { String why = buying.failure(); buying = null; env.actions().closeMenu(); return Outcome.blocked(why); }
                default -> { return Outcome.PENDING; }
            }
        }
        // Once a compactor action starts, finish its evidence checks before applying market gates.
        waitingForProcurement=false;
        String marketBlock=compactorClearance.inProgress()?null:env.procurementBlock();
        if(marketBlock!=null){waitingForProcurement=true;return Outcome.blocked(marketBlock);}
        var requirement=productionRequirement();
        if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking production prerequisites"):Outcome.blocked(requirementReason(requirement));
        var cleared=compactorClearance.tick(env.menu(),env.actions(),new CompactorClearance.Ports(){
            public boolean open(SlotView device){return env.openCompactor(device);}
            public void intent(String reason)throws Exception{env.jobs().put(parent().withState(ProductionJobs.State.PROCESSING,reason));}
        },ownsMenu,now);
        if(cleared.step()!=ProductionLoop.Step.DONE)return cleared;
        marketBlock=env.procurementBlock();
        if(marketBlock!=null){waitingForProcurement=true;return Outcome.blocked(marketBlock);}
        var compactorConflict=PersonalCompactors.conflict(env.menu(),lockedProducts(),catalog);
        if(compactorConflict!=null)return Outcome.blocked(compactorConflict);
        var preparation=preparation();var missing=preparation.purchases();
        for (String id : preparation.products()) if (env.occupied().contains(id)) return Outcome.blocked(id + " belongs to a trader position; production will not use it");
        if (missing.isEmpty()) {
            if(preparation.crafts().isEmpty())return Outcome.DONE;
            if(env.craftQueued())return Outcome.pending("Waiting for other crafting work");
            var craft=preparation.crafts().getFirst();
            // The existing executor journals at most 64 batches per child job. Replan from
            // actual inventory after completion, so large preparations need no guessed counts.
            try {env.jobs().put(parent().withState(ProductionJobs.State.PROCESSING,"Preparing "+craft.output()+" for "+output));}
            catch(Exception failure){return Outcome.blocked("Intermediate crafting intent could not be saved; no craft queued");}
            ingredientCraftJob=env.queueCraft(craft.output(),Math.min(64,craft.batches()));
            if(ingredientCraftJob==null)return Outcome.blocked("Intermediate craft could not be queued; check ingredients and cursor");
            return Outcome.pending("Crafting "+env.name(craft.output())+" from base materials");
        }
        if (!env.buyingAllowed()) {
            var text = new StringJoiner(", ");missing.forEach((id, n) -> text.add(n + " " + env.name(id)));
            return Outcome.blocked("Missing " + text + "; add them to your inventory or turn on automatic ingredient buying");
        }
        var basket=ProductionPlanner.purchaseQuote(missing,this::purchaseCost);
        if(!basket.complete())return Outcome.blocked("No fresh Bazaar quote covers all remaining inputs: "+env.name(basket.blockedProduct()));
        if(ProductionPlanner.exceedsFunds(basket.total(),env.spendable()))return Outcome.blocked("All remaining ingredients would exceed spendable capital; nothing bought");
        var first = missing.entrySet().iterator().next();
        // Instant-buy inputs whose bids would never fill before ordering the rest.
        // A trade test places the order the player asked for, even one unlikely to fill.
        var unorderable=strategy.instantBuy||trade!=null?null:missing.entrySet().stream().filter(e->!env.orderable(e.getKey())).findFirst().orElse(null);
        if(unorderable!=null)first=unorderable;
        double limit=basket.limits().get(first.getKey());
        if(!strategy.instantBuy && unorderable==null) {
            var orders=new ArrayList<com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.IngredientOrder>();
            for(var purchase:missing.entrySet()){
                double planned=basket.limits().get(purchase.getKey())/purchase.getValue()/1.03;
                // The order GUI bids 0.1 above the top order, which is more than 3% below about 3.3 coins.
                double ceiling=Math.max(planned*1.03,planned+0.1+1e-6);
                orders.add(new com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.IngredientOrder(purchase.getKey(),env.name(purchase.getKey()),Math.min(4096,purchase.getValue()),planned,ceiling));
            }
            try{env.jobs().put(parent().withState(ProductionJobs.State.BUYING,"Ingredient buy-order basket intent: "+orders.size()+" products"));}
            catch(Exception failure){return Outcome.blocked("Ingredient order intent could not be saved");}
            if(!env.queueIngredientOrders(List.copyOf(orders)))return Outcome.uncertain("Ingredient order basket could not be queued; check retained craft orders");
            verifiedEmptyOrderHandoff=false;orderActive=true;return Outcome.pending("Placing ingredient buy orders for "+orders.size()+" products");
        }
        boolean overflow=trade==null && env.storageEnabled() && env.storage()!=null;
        if(overflow && env.menu()!=null && env.menu().emptyInventorySlots()<2) {
            // The inventory is full of bought inputs: store them before buying the next lot.
            var products=movableInputs();
            if(products.stream().anyMatch(id->ProductionStorage.Move.inventoryCount(env.menu(),id)>0)) {
                if(!ownsMenu)return Outcome.pending("Waiting to store bought inputs");
                var wait=storageWait(now);if(wait!=null)return wait;
                storing=ProductionStorage.Move.deposit(jobId,env.storage(),env.storagePages(),products);
                return storageStep(now);
            }
        }
        buying = new BazaarInstantBuy(first.getKey(), env.name(first.getKey()), first.getValue(), limit,
                reason -> env.jobs().put(parent().withState(ProductionJobs.State.BUYING, reason)),overflow);
        return Outcome.pending("Buying " + first.getValue() + " " + env.name(first.getKey())+(overflow?" (what fits, then storing)":""));
    }

    @Override public Outcome process(long now) {
        if(trade!=null)return tradeBought();
        if (kind == ProductionRecipe.Kind.CRAFT) {
            if(storing!=null)return storageStep(now);
            if(craftsInRounds())return craftRound(now);
            if (craftJob == null) {
                var requirement=productionRequirement();
                if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking production prerequisites"):Outcome.blocked(requirementReason(requirement));
                craftJob = recipe==null?env.queueCraft(output,batches):env.queueCraftRecipe(output,batches,recipe.key());
                if (craftJob == null) return Outcome.blocked("Craft could not be queued; inputs changed or another production step is queued");
                return Outcome.pending("Crafting");
            }
            if (env.craftQueued()) return Outcome.pending(env.craftActivity());
            return childFinished(craftJob, Set.of(ProductionJobs.State.OUTPUT_READY, ProductionJobs.State.DONE), "Craft");
        }
        if(workstation==null || workstationState()==ProductionJobs.State.PLANNED) {
            String requirement=productionRequirement();
            if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking production prerequisites"):Outcome.blocked(requirementReason(requirement));
        }
        if (!workstationMenuOpen()) return Outcome.pending(kind == ProductionRecipe.Kind.FORGE ? "Open The Forge to submit" : "Open Kat's Pet Sitter with the pet placed");
        if (!ownsMenu) return Outcome.pending("Waiting for the menu");
        var menu = env.menu();
        if (workstation == null) {
            if (kind == ProductionRecipe.Kind.FORGE) {
                if (navigation == null) navigation = new ForgeNavigation(recipe, env.name(output), forgeSlot);
                navigation.tick(menu, env.actions(), now);
                if (navigation.state() == ForgeNavigation.State.FAILED) { String why = navigation.failure(); navigation = null; return Outcome.blocked(why); }
                if (navigation.state() != ForgeNavigation.State.READY) return Outcome.pending("Choosing the Forge recipe");
                workstation = workstation(null, -1);
            } else {
                var placed = menu.slot(13);
                if (placed == null || placed.empty() || !recipe.inputPet().equals(placed.metadata().petVariant()))
                    return Outcome.blocked("Place the " + env.name(recipe.inputPet()) + " pet in Kat's menu");
                workstation = workstation(placed.metadata(), -1);
            }
            if (workstation == null) return Outcome.blocked("Production journal could not record the workstation job");
        }
        var result = workstation.tick(menu, env.actions(), env.skills(), env.unlocks(), env.account(), env.purse(), env.spendable(), now);
        return switch (result) {
            case SUBMITTED -> { copyTiming(); yield Outcome.DONE; }
            case WAITING -> Outcome.pending("Submitting");
            case BLOCKED -> workstationState() == ProductionJobs.State.REVIEW || workstationState() == ProductionJobs.State.SUBMITTING
                    ? Outcome.uncertain(workstation.failure()) : Outcome.blocked(workstation.failure());
            case CLAIMED -> Outcome.uncertain("Workstation reported a claim during submission");
        };
    }

    @Override public Outcome await(long now) {
        var job = env.jobs().find(workstationJob).orElse(null);
        if (job == null) return Outcome.uncertain("Workstation job is missing from the journal");
        if (job.readyAt() > now) return Outcome.pending("Ready in " + Math.max(1, (job.readyAt() - now + 59_999) / 60_000) + " min");
        return Outcome.DONE;
    }

    @Override public Outcome claim(long now) {
        if (!workstationMenuOpen()) return Outcome.pending(kind == ProductionRecipe.Kind.FORGE ? "Open The Forge to claim" : "Open Kat's Pet Sitter to claim");
        if (!ownsMenu) return Outcome.pending("Waiting for the menu");
        if (workstation == null) return Outcome.uncertain("Claim needs the workstation job; use the claim command");
        var result = workstation.tick(env.menu(), env.actions(), env.skills(), env.unlocks(), env.account(), env.purse(), env.spendable(), now);
        return switch (result) {
            case CLAIMED -> Outcome.DONE;
            case WAITING, SUBMITTED -> Outcome.pending("Claiming");
            case BLOCKED -> workstationState() == ProductionJobs.State.REVIEW || workstationState() == ProductionJobs.State.CLAIMING
                    ? Outcome.uncertain(workstation.failure()) : Outcome.blocked(workstation.failure());
        };
    }

    @Override public Outcome sell(long now) {
        if (binPrice == SELL_ON_BAZAAR) return sellOnBazaar(now);
        if (listingJob == null) {
            if(hasReusableCraftMenu()) {
                if(!ownsMenu)return Outcome.pending("Waiting to leave crafting for Auction House");
                env.actions().closeMenu();return Outcome.pending("Crafting complete; opening Auction House next");
            }
            if (env.craftQueued() || env.listingQueued()) return Outcome.pending("Waiting for other production work");
            listingJob = env.queueListing(output, binPrice, maximumFee);
            if (listingJob == null) return Outcome.blocked("BIN listing could not be queued; keep exactly one stack of " + env.name(output));
            return Outcome.pending("Listing");
        }
        if (env.listingQueued()) return Outcome.pending("Listing");
        return childFinished(listingJob, Set.of(ProductionJobs.State.SELLING, ProductionJobs.State.DONE), "BIN listing");
    }

    private Outcome sellOnBazaar(long now) {
        if(!strategy.instantSell) {
            if(!orderActive) {
                int units=held().getOrDefault(output,0);Double value=env.offerSellValue(output,units);
                if(units!=outputUnits())return Outcome.uncertain((trade!=null?"Held "+env.name(output)+" changed ("+units+" held, "+outputUnits()+" expected)":"Craft output quantity differs")+" before sell-offer handoff");
                if(value==null)return Outcome.blocked("Waiting for a fresh sell-offer quote");
                try{env.jobs().put(parent().withState(ProductionJobs.State.LISTING,trade!=null?"Trade test sell-offer intent":"Craft sell-offer intent"));}
                catch(Exception failure){return Outcome.blocked("Sell-offer intent could not be saved");}
                if(!env.queueOrder(output,units,value/units,false,value/units*SALE_FLOOR))return Outcome.uncertain("Craft sell offer could not be queued; retained orders require review");
                orderActive=true;return Outcome.pending("Placing crafted output sell offer");
            }
            var result=env.tickOrder(ownsMenu);
            if(result==null||result.failed())return Outcome.uncertain(result==null?"Sell offer unavailable":result.reason());
            if(!result.done())return Outcome.pending(result.reason()==null||result.reason().isBlank()?"Waiting for crafted output sell offer and coin claim":result.reason());
            if(result.proceeds()==null)return Outcome.uncertain("Sell-offer proceeds are unconfirmed");
            if(trade!=null)tradeSold("offer",result.units(),result.proceeds());
            else env.saleConfirmed(jobId,output,result.units(),result.proceeds());
            if(!env.acknowledgeOrder())return Outcome.uncertain("Sale handoff could not be saved");
            orderActive=false;return Outcome.DONE;
        }
        if (!ownsMenu) return Outcome.pending("Waiting to sell " + env.name(output));
        if (selling == null) {
            int units = held().getOrDefault(output, 0);
            if (units == 0) return Outcome.uncertain("The crafted " + env.name(output) + " is not in the inventory");
            if (trade != null && units != outputUnits()) return Outcome.uncertain("Held " + env.name(output) + " changed (" + units + " held, " + outputUnits() + " expected) before the instant sale");
            Double value = env.instantSellValue(output, units);
            if (value == null) return Outcome.blocked("No fresh Bazaar quote covers selling " + units + " " + env.name(output));
            selling = new BazaarInstantSell(output, env.name(output), units, value * SALE_FLOOR,value*1.03,
                    reason -> env.jobs().put(parent().withState(ProductionJobs.State.LISTING, reason)));
        }
        var result = selling.tick(env.menu(), env.actions(), env.purse(), now);
        return switch (result) {
            case SOLD -> {
                env.event("INFO","production.instant_sale_verified",Map.of("job",jobId,"product",output,"units",selling.amount(),"proceeds",selling.proceeds(),"spent",spent));
                if(trade!=null)tradeSold("instant",selling.amount(),selling.proceeds());
                else if(kind==ProductionRecipe.Kind.CRAFT)env.saleConfirmed(jobId,output,selling.amount(),selling.proceeds());
                env.actions().closeMenu();yield Outcome.DONE; }
            case UNCERTAIN -> Outcome.uncertain(selling.failure());
            case BLOCKED -> { String why = selling.failure(); selling = null; env.actions().closeMenu(); yield Outcome.blocked(why); }
            default -> Outcome.pending("Selling " + env.name(output));
        };
    }

    @Override public void failed(String what,Stage stage,Exception failure) {
        var out=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(out));
        env.event("ERROR","production.stage_exception",Map.of("job",jobId,"output",output,"what",what,"stage",stage.name(),"exception",out.toString()));
    }
    @Override public void persist(Stage stage, String reason) throws Exception {
        var state = switch (stage) {
            case PROCURE -> ProductionJobs.State.PLANNED;
            case PROCESS -> ProductionJobs.State.PROCESSING;
            case AWAIT -> ProductionJobs.State.WAITING;
            case CLAIM -> ProductionJobs.State.CLAIMING;
            case SELL -> ProductionJobs.State.OUTPUT_READY;
            case DONE -> ProductionJobs.State.DONE;
            case REVIEW -> ProductionJobs.State.REVIEW;
        };
        var job = parent();
        env.jobs().put(new ProductionJobs.Job(job.id(), job.recipeKey(), job.account(), state, job.batches(), job.workstationSlot(),
                job.submittedAt(), job.readyAt(), spent > 0 ? spent : job.costBasis(), job.petUuid(), job.auctionUuid(),
                reason != null ? reason : stage == Stage.DONE ? "Production run finished; proceeds are recorded only when the sale is seen" : null,
                job.completedBatches()));
        if(stage==Stage.SELL)captureSellInputLocks();
        if(stage==Stage.SELL&&trade==null&&kind==ProductionRecipe.Kind.CRAFT&&binPrice==SELL_ON_BAZAAR) {
            var menu=env.menu();int units=menu==null?0:menu.countInInventory(output);
            if(units>0)env.outputConfirmed(jobId,output,units,basisUnknown||spent<=0?null:spent);
        }
    }

    // ---- Helpers -----------------------------------------------------------------------

    /** Inputs still missing for the cheapest craftable recipe, or nothing when one is already covered. */
    Map<String,Integer> missing() {return selection().deficits();}
    private record Selection(Map<String,Integer> deficits,Map<String,Integer> reserved) {}
    private Selection selection() {
        var held = held();
        if(trade!=null) {
            if(!trade.buys())return new Selection(Map.of(),Map.of());
            long short_=(long)tradeHeldAtStart+tradeUnits-held.getOrDefault(output,0);
            return new Selection(short_>0?Map.of(output,(int)short_):Map.of(),Map.of());
        }
        var options = recipe != null ? List.of(recipe)
                : catalog.forOutput(output).stream().filter(r -> r.kind() == kind).toList();
        Selection best = null;double bestCost = Double.POSITIVE_INFINITY;
        for (var option : options) {
            if(com.goofy.goofyaddons.features.access.RouteRequirements.craft(option.requirement(),env.skills(),env.unlocks())!=null)continue;
            var need = new LinkedHashMap<String,Integer>();
            var reserved=new HashMap<String,Integer>();
            for (var e : new TreeMap<>(option.ingredients()).entrySet()) {
                if (e.getKey().equals(option.inputPet())) continue;
                reserved.put(e.getKey(),Math.multiplyExact(e.getValue(),batches));
                long short_ = (long) e.getValue() * batches - held.getOrDefault(e.getKey(), 0);
                if (short_ > 0) need.put(e.getKey(), (int) Math.min(Integer.MAX_VALUE, short_));
            }
            if (need.isEmpty()) return new Selection(Map.of(),reserved);
            double cost = 0;
            for (var e : IngredientPreparation.plan(catalog,need,held,reserved).purchases().entrySet()) { Double c = purchaseCost(e.getKey(), e.getValue()); cost += c == null ? 1e18 : c; }
            if (best == null || cost < bestCost) { best = new Selection(need,reserved); bestCost = cost; }
        }
        return best == null ? new Selection(Map.of(),Map.of()) : best;
    }

    /** Fresh, fee-inclusive ceiling for every still-missing purchase, including base preparations. */
    public Double remainingPurchaseCost() {
        var basket=ProductionPlanner.purchaseQuote(preparation().purchases(),this::purchaseCost);
        return basket.complete()?basket.total():null;
    }

    private IngredientPreparation.Plan preparation() {
        var selected=selection();
        // A trade test buys the product itself; never craft it from intermediates.
        if(trade!=null)return new IngredientPreparation.Plan(selected.deficits(),List.of(),Set.of(output));
        return IngredientPreparation.plan(catalog,selected.deficits(),held(),selected.reserved());
    }

    private String productionRequirement() {
        String outputRequirement=com.goofy.goofyaddons.features.access.RouteRequirements.product(output,env.unlocks());
        if(outputRequirement!=null || trade!=null)return outputRequirement;
        if(kind==ProductionRecipe.Kind.FORGE) {
            Integer hotm=env.unlocks().get("hotm");
            String access=com.goofy.goofyaddons.features.access.RouteRequirements.threshold("Heart of the Mountain",2,hotm);
            if(access!=null)return access;
            if(forgeSlot>=Math.min(7,hotm))return "Forge slot "+(forgeSlot+1)+" is unavailable at Heart of the Mountain "+hotm;
        }
        String reason="No verified production recipe for "+output;
        for(var option:recipe==null?catalog.forOutput(output):List.of(recipe))if(option.kind()==kind) {
            var blocked=com.goofy.goofyaddons.features.access.RouteRequirements.craft(option.requirement(),env.skills(),env.unlocks());
            if(blocked==null)blocked=com.goofy.goofyaddons.features.access.RouteRequirements.products(option.ingredients().keySet(),env.unlocks());
            if(blocked==null)return null;
            reason=blocked;
        }
        return reason;
    }
    private String requirementReason(String reason) {
        String status=env.requirementsStatus();
        return reason.contains("unobserved") && status!=null?reason+". "+status:reason;
    }

    private Map<String,Integer> held() {
        var counts = new HashMap<String,Integer>();var menu = env.menu();
        if (menu == null) return counts;
        for (var slot : menu.slots()) if (slot.inPlayerInventory() && slot.containerSlot() < 36 && !slot.empty()) {
            String id = ProductionMenus.productId(slot);
            if (id != null) counts.merge(id, slot.count(), Integer::sum);
        }
        // Units this run parked in storage are still its inputs.
        var storage=env.storage();
        if(storage!=null)storage.stored(jobId).forEach((id,units)->counts.merge(id,units,Integer::sum));
        return counts;
    }

    // ---- Storage -----------------------------------------------------------------------

    /** Inputs no order position tracks, or whose orders are settled: this run may move them. */
    private Set<String> movableInputs() {
        var ids=new HashSet<String>();var tracked=env.orderTracked();
        for(String id:requiredProducts())if(!id.equals(output) && !tracked.contains(id))ids.add(id);
        ids.addAll(env.orderStorable());ids.remove(output);
        return ids;
    }

    private Outcome storageWait(long now){
        return now<storageRetryAt?Outcome.blocked(storageFailure+"; waiting a minute before trying storage again"):null;
    }
    private Outcome storageFailed(String reason,long now){
        storageFailures++;storageFailure=reason;storageRetryAt=now+STORAGE_RETRY_MS;
        if(storageFailures>=STORAGE_ATTEMPTS)return Outcome.uncertain(reason+" ("+storageFailures+" attempts); check storage and inventory");
        return Outcome.blocked(reason);
    }
    private Outcome storageStep(long now) {
        if(!ownsMenu)return Outcome.pending("Waiting to use storage");
        var move=storing;
        var result=move.tick(env.menu(),env.actions(),now);
        if(result==ProductionStorage.Move.Result.WAITING)return Outcome.pending(move.describe());
        storing=null;
        var forOrder=storingForOrder;storingForOrder=Set.of();
        if(result==ProductionStorage.Move.Result.BLOCKED)return Outcome.uncertain(move.failure());
        for(String product:forOrder) {
            int units=move.moved(product);
            // Recording zero lets the order look at the inventory again instead of waiting forever.
            if(!env.orderStored(product,units))return Outcome.uncertain("Stored "+units+" "+env.name(product)+" but its order could not record it; check storage and orders");
            if(units==0 && result==ProductionStorage.Move.Result.DONE)
                return storageFailed("No inventory room to claim "+env.name(product)+"; free some inventory slots",now);
        }
        if(result==ProductionStorage.Move.Result.NO_SPACE)
            return storageFailed(move.kind()==ProductionStorage.Move.Kind.WITHDRAW?"Not enough free inventory to take inputs back from storage"
                : "Storage is full; free space in your ender chest pages or backpacks to keep buying",now);
        if(move.moved()>0){storageFailures=0;storageRetryAt=0;storageFailure=null;}
        return Outcome.pending(move.kind()==ProductionStorage.Move.Kind.DEPOSIT?"Stored "+move.moved()+" items; continuing":"Took "+move.moved()+" items from storage; crafting next");
    }

    /** Inputs parked in storage are crafted in rounds that fit the inventory. */
    private boolean craftsInRounds() {
        if(craftedBatches>0 || roundBatches>0)return true;
        var storage=env.storage();
        return storage!=null && !storage.stored(jobId).isEmpty();
    }

    private record Round(int batches,Map<String,Integer> withdraw) {}

    private Outcome craftRound(long now) {
        if(craftJob!=null) {
            if(env.craftQueued())return Outcome.pending(env.craftActivity());
            var finished=childFinished(craftJob,Set.of(ProductionJobs.State.OUTPUT_READY,ProductionJobs.State.DONE),"Craft");
            if(finished!=Outcome.DONE)return finished;
            craftedBatches+=roundBatches;roundBatches=0;craftJob=null;
            if(craftedBatches<batches)return Outcome.pending("Crafted "+craftedBatches+"/"+batches+" batches; taking the next inputs from storage");
        }
        if(craftedBatches>=batches)return finishRounds();
        var requirement=productionRequirement();
        if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking production prerequisites"):Outcome.blocked(requirementReason(requirement));
        var menu=env.menu();
        if(menu==null)return Outcome.pending("Waiting for the inventory");
        var chosen=recipe!=null?recipe:catalog.forOutput(output).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).findFirst().orElse(null);
        if(chosen==null)return Outcome.uncertain("No craft recipe for the stored inputs");
        int remaining=batches-craftedBatches;
        var stored=env.storage()==null?Map.<String,Integer>of():env.storage().stored(jobId);
        for(var need:chosen.ingredients().entrySet())
            if((long)ProductionStorage.Move.inventoryCount(menu,need.getKey())+stored.getOrDefault(need.getKey(),0)<(long)need.getValue()*remaining)
                return Outcome.uncertain("Inputs for the remaining "+remaining+" batches are missing from the inventory and storage");
        var round=roundPlan(menu,chosen,remaining,stored);
        if(round==null)return Outcome.blocked("Free some inventory space: one craft batch, its inputs and its output do not fit");
        if(!round.withdraw().isEmpty()) {
            if(!ownsMenu)return Outcome.pending("Waiting to take inputs from storage");
            var wait=storageWait(now);if(wait!=null)return wait;
            storing=ProductionStorage.Move.withdraw(jobId,env.storage(),env.storagePages(),round.withdraw());
            return storageStep(now);
        }
        // A storage page closing is asynchronous; crafting refuses to start over another menu.
        if(menu.title()!=null && !hasReusableCraftMenu()) {
            if(ownsMenu && menu.cursorEmpty())env.actions().closeMenu();
            return Outcome.pending("Closing storage before crafting");
        }
        craftJob=env.queueCraftRecipe(output,round.batches(),chosen.key());
        if(craftJob==null)return Outcome.blocked("Craft could not be queued; inputs changed or another production step is queued");
        roundBatches=round.batches();
        return Outcome.pending("Crafting "+round.batches()+" of "+remaining+" remaining batches");
    }

    /** The most batches whose missing inputs, output and a crafting slot fit the free inventory. */
    private Round roundPlan(MenuSnapshot menu,ProductionRecipe chosen,int remaining,Map<String,Integer> stored) {
        int free=menu.emptyInventorySlots();
        for(int k=Math.min(64,remaining);k>=1;k--) {
            var withdraw=new TreeMap<String,Integer>();int slots=0;boolean covered=true;
            for(var need:chosen.ingredients().entrySet()) {
                int missing=Math.multiplyExact(need.getValue(),k)-ProductionStorage.Move.inventoryCount(menu,need.getKey());
                if(missing<=0)continue;
                if(stored.getOrDefault(need.getKey(),0)<missing){covered=false;break;}
                withdraw.put(need.getKey(),missing);
                // Whole stacks come back, so allow one extra slot for a stackable input.
                slots+=CraftFlipPlanner.inventorySlotsForProduct(catalog,need.getKey(),missing)+(catalog.unstackable(need.getKey())?0:1);
            }
            if(!covered)continue;
            // Earlier rounds' output already holds slots; new units may top those stacks up.
            int made=ProductionStorage.Move.inventoryCount(menu,chosen.outputId());
            int outputs=CraftFlipPlanner.inventorySlotsForProduct(catalog,chosen.outputId(),made+Math.multiplyExact(chosen.outputCount(),k))
                -CraftFlipPlanner.inventorySlotsForProduct(catalog,chosen.outputId(),made);
            if(slots+outputs+1<=free)return new Round(k,withdraw);
        }
        return null;
    }

    private Outcome finishRounds() {
        var storage=env.storage();
        if(storage!=null && !storage.stored(jobId).isEmpty()) {
            try {
                var left=storage.release(jobId);
                env.actions().message("Craft finished; left in storage: "+String.join(", ",left));
            }catch(Exception failed){return Outcome.uncertain("Storage ledger could not be saved after crafting");}
        }
        return Outcome.DONE;
    }

    private boolean workstationMenuOpen() {
        var menu = env.menu();
        if (menu == null || menu.title() == null) return false;
        String title = Chat.strip(menu.title());
        if (kind == ProductionRecipe.Kind.KAT) return title.equals("Pet Sitter");
        return title.equals("The Forge") || title.equals("Select Process") || title.equals("Forge Item") || title.equals("Confirm Process")
                || title.startsWith("Refine") || title.startsWith("Item Casting");
    }

    private WorkstationExecutor workstation(ItemMetadata pet, int unused) {
        try {
            workstationJob = UUID.randomUUID().toString();
            env.jobs().put(new ProductionJobs.Job(workstationJob, recipe.key(), env.account(), ProductionJobs.State.PLANNED, 1,
                    kind == ProductionRecipe.Kind.FORGE ? forgeSlot : -1, 0, 0, spent, pet == null ? null : pet.uuid(), null, "Workstation step of run " + jobId));
            return new WorkstationExecutor(recipe, workstationJob, env.jobs(), pet);
        } catch (Exception failure) { return null; }
    }

    private ProductionJobs.State workstationState() {
        return workstationJob == null ? null : env.jobs().find(workstationJob).map(ProductionJobs.Job::state).orElse(null);
    }

    private void copyTiming() {
        try {
            var child = env.jobs().find(workstationJob).orElseThrow();var job = parent();
            env.jobs().put(new ProductionJobs.Job(job.id(), job.recipeKey(), job.account(), job.state(), job.batches(), child.workstationSlot(),
                    child.submittedAt(), child.readyAt(), child.costBasis(), child.petUuid(), job.auctionUuid(), "Submitted; waiting for the workstation", job.completedBatches()));
        } catch (Exception ignored) { /* The child job keeps the authoritative timing. */ }
    }

    private Outcome childFinished(String child, Set<ProductionJobs.State> success, String what) {
        var job = env.jobs().find(child).orElse(null);
        if (job == null) return Outcome.uncertain(what + " job is missing from the journal");
        if (success.contains(job.state())) return Outcome.DONE;
        return Outcome.uncertain(what + " ended as " + job.state().name().toLowerCase(Locale.ROOT) + (job.reason() == null ? "" : ": " + job.reason()));
    }

    private void addCost(BazaarInstantBuy bought) { spent += bought.spent(); }

    /** The bought half of a trade test is verified: report it before any sale starts. */
    private Outcome tradeBought() {
        if(trade.buys()) {
            int bought=held().getOrDefault(output,0)-tradeHeldAtStart;
            var evidence=new LinkedHashMap<String,Object>();evidence.put("job",jobId);evidence.put("product",output);evidence.put("part",trade.name());
            evidence.put("strategy",strategy.name());evidence.put("requested",tradeUnits);evidence.put("bought",bought);evidence.put("spent",spent);
            env.event("INFO","production.trade_test_bought",evidence);
            env.actions().message(String.format(Locale.ROOT,"Trade test: bought %d %s for %,.1f coins (%s)%s.",bought,env.name(output),spent,
                    strategy.instantBuy?"instant buy":"buy order",trade.sells()?"; selling next":""));
        }
        return Outcome.DONE;
    }
    /** Test sales are not craft profit: report them instead of recording them in the ledger. */
    private void tradeSold(String how,int units,double proceeds) {
        var evidence=new LinkedHashMap<String,Object>();evidence.put("job",jobId);evidence.put("product",output);evidence.put("part",trade.name());
        evidence.put("strategy",strategy.name());evidence.put("units",units);evidence.put("proceeds",proceeds);evidence.put("spent",spent);
        env.event("INFO","production.trade_test_sold",evidence);
        env.actions().message(String.format(Locale.ROOT,"Trade test: sold %d %s for %,.1f coins (%s)%s.",units,env.name(output),proceeds,
                how.equals("offer")?"sell offer":"instant sell",trade==TradePart.BOTH&&!basisUnknown?String.format(Locale.ROOT,"; bought for %,.1f, result %,.1f",spent,proceeds-spent):""));
    }

    private ProductionJobs.Job parent() {
        return env.jobs().find(jobId).orElseThrow(() -> new IllegalStateException("Production run job is missing"));
    }
}
