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
        default String procurementBlock(){return null;}
        /** Instant-buy cost of these units from fresh quotes, or null when unknown. */
        Double instantBuyCost(String id, int units);
        /** Instant-sell value of these units from fresh quotes, before tax, or null when unknown. */
        Double instantSellValue(String id, int units);
        String name(String id);
        /** Queues a craft through the crafting feature; returns its job id or null. */
        String queueCraft(String output, int batches);
        default String queueCraftRecipe(String output,int batches,String key){return queueCraft(output,batches);}
        boolean craftQueued();
        /** Queues a published BIN listing through the auction feature; returns its job id or null. */
        String queueListing(String product, long price, double maximumFee);
        boolean listingQueued();
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
    private ProductionRecipe recipe;
    private String craftJob, listingJob, workstationJob;
    private String ingredientCraftJob;
    private ForgeNavigation navigation;
    private WorkstationExecutor workstation;
    private double spent;
    private boolean basisUnknown;

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
        if (!timed && (batches < 1 || batches > 16)) throw new IllegalArgumentException("Craft runs take 1–16 batches");
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
    public double spent(){return spent;}
    public int outputUnits(){return Math.multiplyExact(batches,recipe==null?1:recipe.outputCount());}

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

    public String jobId() { return jobId; }
    public Stage stage() { return loop.stage(); }
    public String reason() { return loop.reason(); }
    public boolean finished() { return loop.finished(); }
    public String output() { return output; }

    /** Products this run must keep away from traders. */
    public Set<String> lockedProducts() {
        var ids = new HashSet<String>();ids.add(output);
        if (recipe != null) ids.addAll(recipe.ingredients().keySet());
        else catalog.forOutput(output).stream().filter(r -> r.kind() == kind).forEach(r -> ids.addAll(r.ingredients().keySet()));
        ids.addAll(IngredientPreparation.dependencies(catalog,ids));
        return Set.copyOf(ids);
    }

    /** True while the current step has to click in a menu. */
    public boolean wantsMenu() {
        if (finished()) return false;
        return switch (loop.stage()) {
            case PROCURE -> ingredientCraftJob==null && (buying != null || env.buyingAllowed() && !preparation().purchases().isEmpty());
            case PROCESS -> kind != ProductionRecipe.Kind.CRAFT && workstationMenuOpen();
            case CLAIM -> workstationMenuOpen();
            case SELL -> binPrice == SELL_ON_BAZAAR;
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
        if(ingredientCraftJob!=null) {
            if(env.craftQueued())return Outcome.pending("Crafting intermediate ingredients");
            var result=childFinished(ingredientCraftJob,Set.of(ProductionJobs.State.OUTPUT_READY,ProductionJobs.State.DONE),"Ingredient craft");
            if(result!=Outcome.DONE)return result;
            ingredientCraftJob=null;
            return Outcome.pending("Intermediate craft verified; checking remaining inputs");
        }
        if (buying != null) {
            if (!ownsMenu) return Outcome.pending("Waiting to buy " + env.name(buying.productId()));
            var result = buying.tick(env.menu(), env.signOpen(), env.actions(), env.purse(), now);
            switch (result) {
                case BOUGHT -> { addCost(buying); buying = null; env.actions().closeMenu(); return Outcome.pending("Bought inputs; checking the rest"); }
                case UNCERTAIN -> { return Outcome.uncertain(buying.failure()); }
                case BLOCKED -> { String why = buying.failure(); buying = null; env.actions().closeMenu(); return Outcome.blocked(why); }
                default -> { return Outcome.PENDING; }
            }
        }
        String marketBlock=env.procurementBlock();if(marketBlock!=null)return Outcome.blocked(marketBlock);
        var requirement=craftRequirement();
        if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking crafting prerequisites"):Outcome.blocked(requirementReason(requirement));
        var compactorConflict=PersonalCompactors.conflict(env.menu(),lockedProducts(),catalog);
        if(compactorConflict!=null)return Outcome.blocked(compactorConflict);
        var preparation=preparation();var missing=preparation.purchases();
        for (String id : preparation.products()) if (env.occupied().contains(id)) return Outcome.blocked(id + " belongs to a trader position; production will not use it");
        if (missing.isEmpty()) {
            if(preparation.crafts().isEmpty())return Outcome.DONE;
            if(env.craftQueued())return Outcome.pending("Waiting for other crafting work");
            var craft=preparation.crafts().getFirst();
            // The existing executor journals at most 16 batches per child job. Replan from
            // actual inventory after completion, so large preparations need no guessed counts.
            try {env.jobs().put(parent().withState(ProductionJobs.State.PROCESSING,"Preparing "+craft.output()+" for "+output));}
            catch(Exception failure){return Outcome.blocked("Intermediate crafting intent could not be saved; no craft queued");}
            ingredientCraftJob=env.queueCraft(craft.output(),Math.min(16,craft.batches()));
            if(ingredientCraftJob==null)return Outcome.blocked("Intermediate craft could not be queued; check ingredients and cursor");
            return Outcome.pending("Crafting "+env.name(craft.output())+" from base materials");
        }
        if (!env.buyingAllowed()) {
            var text = new StringJoiner(", ");missing.forEach((id, n) -> text.add(n + " " + env.name(id)));
            return Outcome.blocked("Missing " + text + "; add them to your inventory or turn on automatic ingredient buying");
        }
        var first = missing.entrySet().iterator().next();
        Double cost = env.instantBuyCost(first.getKey(), first.getValue());
        if (cost == null) return Outcome.blocked("No fresh Bazaar quote covers " + first.getValue() + " " + env.name(first.getKey()));
        double limit = cost * 1.03; // Room for the book moving between the quote and the click, never more.
        if (limit > env.spendable()) return Outcome.blocked("Buying " + env.name(first.getKey()) + " would exceed spendable capital");
        buying = new BazaarInstantBuy(first.getKey(), env.name(first.getKey()), first.getValue(), limit,
                reason -> env.jobs().put(parent().withState(ProductionJobs.State.BUYING, reason)));
        return Outcome.pending("Buying " + first.getValue() + " " + env.name(first.getKey()));
    }

    @Override public Outcome process(long now) {
        if (kind == ProductionRecipe.Kind.CRAFT) {
            if (craftJob == null) {
                var requirement=craftRequirement();
                if(requirement!=null)return env.requirementsPending()?Outcome.pending("Checking crafting prerequisites"):Outcome.blocked(requirementReason(requirement));
                craftJob = recipe==null?env.queueCraft(output,batches):env.queueCraftRecipe(output,batches,recipe.key());
                if (craftJob == null) return Outcome.blocked("Craft could not be queued; inputs changed or another production step is queued");
                return Outcome.pending("Crafting");
            }
            if (env.craftQueued()) return Outcome.pending("Crafting");
            return childFinished(craftJob, Set.of(ProductionJobs.State.OUTPUT_READY, ProductionJobs.State.DONE), "Craft");
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
        var result = workstation.tick(menu, env.actions(), env.skills(), env.account(), env.purse(), env.spendable(), now);
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
        var result = workstation.tick(env.menu(), env.actions(), env.skills(), env.account(), env.purse(), env.spendable(), now);
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
            if (env.craftQueued() || env.listingQueued()) return Outcome.pending("Waiting for other production work");
            listingJob = env.queueListing(output, binPrice, maximumFee);
            if (listingJob == null) return Outcome.blocked("BIN listing could not be queued; keep exactly one stack of " + env.name(output));
            return Outcome.pending("Listing");
        }
        if (env.listingQueued()) return Outcome.pending("Listing");
        return childFinished(listingJob, Set.of(ProductionJobs.State.SELLING, ProductionJobs.State.DONE), "BIN listing");
    }

    private Outcome sellOnBazaar(long now) {
        if (!ownsMenu) return Outcome.pending("Waiting to sell " + env.name(output));
        if (selling == null) {
            int units = held().getOrDefault(output, 0);
            if (units == 0) return Outcome.uncertain("The crafted " + env.name(output) + " is not in the inventory");
            Double value = env.instantSellValue(output, units);
            if (value == null) return Outcome.blocked("No fresh Bazaar quote covers selling " + units + " " + env.name(output));
            selling = new BazaarInstantSell(output, env.name(output), units, value * SALE_FLOOR,value*1.03,
                    reason -> env.jobs().put(parent().withState(ProductionJobs.State.LISTING, reason)));
        }
        var result = selling.tick(env.menu(), env.actions(), env.purse(), now);
        return switch (result) {
            case SOLD -> { if(kind==ProductionRecipe.Kind.CRAFT)env.saleConfirmed(jobId,output,selling.amount(),selling.proceeds());env.actions().closeMenu();yield Outcome.DONE; }
            case UNCERTAIN -> Outcome.uncertain(selling.failure());
            case BLOCKED -> { String why = selling.failure(); selling = null; env.actions().closeMenu(); yield Outcome.blocked(why); }
            default -> Outcome.pending("Selling " + env.name(output));
        };
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
        if(stage==Stage.SELL&&kind==ProductionRecipe.Kind.CRAFT&&binPrice==SELL_ON_BAZAAR) {
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
        var options = recipe != null ? List.of(recipe)
                : catalog.forOutput(output).stream().filter(r -> r.kind() == kind).toList();
        Selection best = null;double bestCost = Double.POSITIVE_INFINITY;
        for (var option : options) {
            if(kind==ProductionRecipe.Kind.CRAFT && com.goofy.goofyaddons.features.access.RouteRequirements.craft(option.requirement(),env.skills(),env.unlocks())!=null)continue;
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
            for (var e : IngredientPreparation.plan(catalog,need,held,reserved).purchases().entrySet()) { Double c = env.instantBuyCost(e.getKey(), e.getValue()); cost += c == null ? 1e18 : c; }
            if (best == null || cost < bestCost) { best = new Selection(need,reserved); bestCost = cost; }
        }
        return best == null ? new Selection(Map.of(),Map.of()) : best;
    }

    private IngredientPreparation.Plan preparation() {
        var selected=selection();return IngredientPreparation.plan(catalog,selected.deficits(),held(),selected.reserved());
    }

    private String craftRequirement() {
        if(kind!=ProductionRecipe.Kind.CRAFT)return null;
        String reason="No verified crafting recipe for "+output;
        for(var option:recipe==null?catalog.forOutput(output):List.of(recipe))if(option.kind()==kind) {
            var blocked=com.goofy.goofyaddons.features.access.RouteRequirements.craft(option.requirement(),env.skills(),env.unlocks());
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
        return counts;
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

    private ProductionJobs.Job parent() {
        return env.jobs().find(jobId).orElseThrow(() -> new IllegalStateException("Production run job is missing"));
    }
}
