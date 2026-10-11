package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.generalflipper.GeneralPosition.Stage;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Ordinary-item buy orders and sell offers. All state and clicks run on the client thread. */
public class GeneralFlipper implements Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralFlipper.class);
    static final String OWNER = "general";
    enum Step { OPEN_ORDERS, ORDERS, CANCEL_DETAIL, VERIFY_CANCEL, OPEN_PRODUCT,
        PRODUCT, QUANTITY, SIGN, PRICE, CONFIRM, VERIFY_ORDER, VERIFY_SALE, PARTIAL_CLAIM }


    /** External services stay replaceable so tests can drive real execution without Minecraft or HTTP. */
    interface Services {
        CapitalManager capital();
        GeneralSettings settings();
        double taxPercentage();
        boolean automaticSelection();
        long actionDelay();
        double purse();
        JsonObject latestQuotes();
        CompletableFuture<JsonObject> fetchQuotes();
        void acquire(GeneralPosition position);
        void sell(GeneralPosition position, int units, Double proceeds);
        void safetyPause(String reason);
        default boolean productionOnly(){return false;}
        /** Production buys may claim in parts, storing between claims. */
        default boolean productionStorage(){return false;}
        /** Null until this live account/profile equals the pinned trading scope. */
        default String accountScope(){return null;}
        default double productionMinimumSellPrice(GeneralPosition position){return position.minimumSellPrice;}
        default void placed(GeneralPosition position) {}
        default com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast forecast(String item,int batch) {return null;}
        default void finished(GeneralPosition position) {}
        default java.util.Set<String> excludedProducts() { return java.util.Set.of(); }
        default void excludeProduct(String id,String reason) {}
        default java.util.Map<String,Integer> skillLevels(){return java.util.Map.of();}
        default com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.Report recommendations() {return null;}
        default void event(String level,String type,java.util.Map<String,?> details) {}
        default void failure(String type,Throwable failure) {}
        default java.util.Map<String,Object> diagnosticContext() {return java.util.Map.of();}
    }

    private final Services services;
    private String productionWait;
    private final com.goofy.goofyaddons.menu.NavigationRetry navigationRetry = new com.goofy.goofyaddons.menu.NavigationRetry();
    private int inputRestarts;
    private final GeneralClaim claim = new GeneralClaim();
    private final GeneralOrderEntry entry = new GeneralOrderEntry();
    private final GeneralBuy buySide = new GeneralBuy();
    private final GeneralSell sellSide = new GeneralSell();
    private final GeneralCancel cancellation = new GeneralCancel();
    private final GeneralOrderReview review = new GeneralOrderReview();
    private final Session session = new Session();
    private final com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation purseObservation =
            new com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation();
    private final com.goofy.goofyaddons.menu.GameWorld world;
    private final GeneralOrderRepository repository;
    private final com.goofy.goofyaddons.menu.GameActions actions;
    /** The menu as observed once this tick; every decision below reads this, not the game. */
    private com.goofy.goofyaddons.menu.MenuSnapshot view = new com.goofy.goofyaddons.menu.MenuSnapshot(0, null, true, List.of());
    private final CapitalManager capital;
    private final List<GeneralPosition> positions = new ArrayList<>();
    private final java.util.Map<String, Long> cooldownUntil = new java.util.HashMap<>();
    private boolean loaded;
    private boolean running;
    private boolean paused;
    private boolean blocked;
    private boolean productionRecovery;
    /** Transient ownership scope: restored sales never advance orphaned ingredient buys. */
    private java.util.Set<GeneralPosition> productionWorkScope;
    private CompletableFuture<JsonObject> request;
    private int generation;
    private JsonObject products;
    private final com.goofy.goofyaddons.features.MenuRecheck menuRecheck=new com.goofy.goofyaddons.features.MenuRecheck();
    private final com.goofy.goofyaddons.menu.BedrockMenuRecovery bedrockRecovery=new com.goofy.goofyaddons.menu.BedrockMenuRecovery();
    private boolean reopeningOrders;
    private String recheckReason;
    private long quotesAt;
    private long nextPoll;
    // Recomputed once per tick by refreshSnapshot(); read by the scheduler and the HUD,
    // neither of which may recompute the market or advance quote state themselves.
    private List<GeneralCalculator.Candidate> snapshotCandidates = List.of();
    private double snapshotPurse = -1;
    private boolean snapshotFresh;
    private GeneralPosition active;
    private GeneralPosition instantPosition;
    private com.goofy.goofyaddons.features.production.BazaarInstantBuy instantBuying;
    private com.goofy.goofyaddons.features.production.BazaarInstantSell instantSelling;
    private Step step;
    /** Evidence for the position being worked on; replaced whenever work is selected. */
    private GeneralTrade trade = new GeneralTrade();
    private final com.goofy.goofyaddons.features.MenuSettle ordersSettle=new com.goofy.goofyaddons.features.MenuSettle();
    private String lastBlockedItem;
    private String lastFailure;
    private String lastFailureParent;
    @Override public void navigationResumed(long elapsed){stepSince+=elapsed;nextAction+=elapsed;navigationRetry.reset();}
    private long stepSince;
    private long nextAction;
    private long lastCommand;
    private ExternalReconciliation externalReconciliation;

    /** Client wiring supplies all effects; construction performs no I/O or event registration. */
    GeneralFlipper(com.goofy.goofyaddons.menu.GameWorld world, com.goofy.goofyaddons.menu.GameActions actions,
                   GeneralOrderRepository repository, Services services) {
        this.services = java.util.Objects.requireNonNull(services);
        this.capital = java.util.Objects.requireNonNull(services.capital());
        this.world = java.util.Objects.requireNonNull(world);
        this.actions = java.util.Objects.requireNonNull(actions);
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    /** One observation per tick. Decisions inside a tick then share a single view. */
    private void observe() {
        var observed = world.menu();
        view = observed == null ? new com.goofy.goofyaddons.menu.MenuSnapshot(0, null, true, List.of()) : observed;
    }

    @Override public String name() { return "GeneralFlipper"; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean canYield() { return active == null; }
    @Override public void yieldMenu() {
        if (active == null) actions.closeMenu();
    }

    public boolean hasStateError() { return blocked; }
    public java.util.Map<String,Object> diagnosticState() {
        var state=new java.util.LinkedHashMap<String,Object>();
        state.put("navigationPending",navigationRetry.pending());
        state.put("navigation",navigationRetry.diagnosticState(view));
        state.put("activeTrade",active==null?"none":active.tradeId);state.put("activeItem",active==null?"none":active.item.id());
        state.put("step",step==null?"none":step.name());state.put("stepAgeMs",stepSince==0?0:world.now()-stepSince);
        state.put("paused",paused);state.put("blocked",blocked);state.put("claimPending",trade.claimPending);state.put("receipt",trade.receipt);
        state.put("claimUnits",trade.claimUnits);state.put("inventoryBefore",trade.inventoryBefore);state.put("expectedClaim",trade.expectedClaim);
        state.put("ordersContainer",ordersSettle.container());state.put("selling",trade.selling);
        state.put("cancelNeedsClaim",trade.cancelNeedsClaim);state.put("cancelSoldUnits",trade.cancelSoldUnits);
        state.put("claimedProceeds",trade.claimedProceeds);state.put("hasClaimedProceeds",trade.claimedProceeds!=null);
        state.put("reopenedCancelOptions",trade.reopenedCancelOptions);
        if(active!=null) {
            int inventory=itemCount(active.item.id());
            state.put("targetInventoryTotal",inventory);state.put("targetAcquiredInventory",active.acquiredInventory(inventory));
            state.put("buyOrderSlot",findOrder(false));state.put("sellOrderSlot",findOrder(true));
        }
        state.put("cancellation",cancellation.diagnosticState());
        var retained=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(GeneralPosition position:positions) {
            var details=new java.util.LinkedHashMap<String,Object>();
            details.put("trade",position.tradeId);details.put("item",position.item.id());details.put("units",position.quantity);
            details.put("stage",position.stage);details.put("cost",position.cost());details.put("sellPrice",position.sellPrice);
            details.put("purchasePriceKnown",position.purchasePriceKnown);
            details.put("settlementPending",position.settlementPending);
            details.put("parent",position.productionParent==null?"legacy":position.productionParent);
            details.put("productionInventoryBefore",position.productionInventoryBefore);
            details.put("productionStoredUnits",position.productionStoredUnits);details.put("productionAwaitingStorage",position.productionAwaitingStorage);
            details.put("productionBasket",position.productionBasket);
            details.put("unitCost",position.unitCost);details.put("maximumBuyPrice",position.maximumBuyPrice);details.put("minimumSellPrice",position.minimumSellPrice);
            details.put("placedAt",position.placedAt);details.put("checkedAt",position.checkedAt);details.put("completed",position.completed);details.put("rejectedReason",position.rejectedReason);details.put("verifiedProceeds",position.verifiedProceeds);
            details.put("productionSoldUnits",position.productionSoldUnits);details.put("productionSaleEvent",position.productionSaleEvent);details.put("productionProceedsUnknown",position.productionProceedsUnknown);
            details.put("submitted",position.submitted);details.put("cancelRequested",position.cancelRequested);details.put("reprices",position.reprices);
            details.put("confirmedCancelRefund",position.confirmedCancelRefund);details.put("hasConfirmedCancelRefund",position.confirmedCancelRefund!=null);
            retained.add(details);
        }
        state.put("quotesAt",quotesAt);state.put("quotesFresh",freshQuotes());state.put("nextPollAt",nextPoll);state.put("nextActionAt",nextAction);
        state.put("productionWait",productionWait);
        state.put("productionRecovery",productionRecovery);
        state.put("productionWorkParents",productionWorkScope==null?"all":productionWorkScope.stream()
            .filter(positions::contains).map(p->p.productionParent==null?"legacy:"+p.tradeId:p.productionParent).distinct().sorted().toList());
        state.put("lastFailure",lastFailure);state.put("lastFailureParent",lastFailureParent);
        state.put("positions",retained);return state;
    }
    /** Preserve the decision's tick snapshot before a recheck or failure closes its menu. */
    private java.util.Map<String,Object> orderDiagnosticContext() {
        var context=new java.util.LinkedHashMap<>(services.diagnosticContext());
        context.put("observedAt",world.now());context.put("orderState",diagnosticState());
        context.put("orderMenu",com.goofy.goofyaddons.diagnostics.DebugMenuCapture.describe(view));
        return context;
    }
    public String retainedItem() {
        return lastBlockedItem!=null?lastBlockedItem:positions.isEmpty() ? "No retained general orders" : "Retained: "+positions.getFirst().quantity+"x "+positions.getFirst().item.name();
    }
    public boolean hasRetainedPositions() { return !positions.isEmpty(); }
    public String taskItem() {
        return active == null ? "No item selected" : active.item.name();
    }
    /** Pure reader: the HUD calls this every frame, so it must not recompute or mutate. */
    public String activity() {
        if(lastFailure!=null && (paused||blocked))return lastFailure;
        if (active != null) return (services.productionOnly()?"Craft orders: ":"General: ") + step.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (!snapshotFresh) return "Waiting for price data";
        if (!positions.isEmpty()) return services.productionOnly()?(productionWait==null?"Waiting for craft orders":productionWait):"Waiting for general orders";
        if (capital.available(snapshotPurse) <= 0) return "Capital limit / purse reserve reached";
        return snapshotCandidates.isEmpty() ? "No flips meet the configured filters" : "Selecting eligible flips";
    }

    private void restoreFunding(GeneralPosition position) {
        capital.restore(OWNER,position.item.id(),position.cost(),position.stage==Stage.PLANNED);
        if(!position.purchasePriceKnown)capital.funding(OWNER,position.item.id(),null);
    }

    public void restoreBudget() {
        if (!loaded) load();
        for (GeneralPosition position : positions) restoreFunding(position);
    }

    @Override public void start() {
        cancelExternalReconciliation();
        productionRecovery=false;productionWorkScope=null;
        bedrockRecovery.reset();
        restoreBudget();
        if (blocked) { actions.message("General flipper is blocked; resolve the logged order-state error first."); return; }
        running = true;
        paused = false;
        lastBlockedItem=null;lastFailure=null;lastFailureParent=null;
        nextPoll = 0;
        for (GeneralPosition position : positions) {
            position.checkedAt = 0;
            restoreFunding(position);
        }
    }

    @Override public void stop() {
        cancelExternalReconciliation();
        productionRecovery=false;productionWorkScope=null;
        running = false;
        paused = false;
        invalidateRequest();
        // Submitted orders and acquired inventory remain owned across stops.
        positions.removeIf(position -> {
            if (position.stage == Stage.PLANNED && !position.submitted) {
                capital.release(OWNER, position.item.id());
                return true;
            }
            position.checkedAt = 0;
            return false;
        });
        active = null;instantPosition=null;instantBuying=null;instantSelling=null;navigationRetry.reset();inputRestarts=0;
        clearSnapshot();
        save();
    }

    @Override public void pause() {
        paused = true;
        invalidateRequest();
        active = null;instantPosition=null;instantBuying=null;instantSelling=null;navigationRetry.reset();inputRestarts=0;
        clearSnapshot();
        save();
    }

    /** A snapshot only describes a running engine; never let a stale one be read back. */
    private void clearSnapshot() {
        snapshotCandidates = List.of();
        snapshotFresh = false;
        snapshotPurse = -1;
    }
    @Override public void resume() { if (running) start(); }

    private void invalidateRequest() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
    }

    @Override public void poll() {
        if (!running || paused || blocked) return;
        observe();
        refreshSnapshot();
        if (request != null || world.now() < nextPoll) return;
        nextPoll = world.now() + settings().refreshSeconds * 1000L;
        int run = generation;
        try {
            request = services.fetchQuotes();
            request.whenComplete((root, error) -> world.onClientThread(() -> {
                if (generation != run || !running || paused) return;
                try {
                    if (error != null) throw new IllegalStateException("Bazaar request failed", error);
                    long updated = TradingSafety.sourceTime(root, world.now());
                    if(updated<quotesAt) return;
                    products = root.getAsJsonObject("products");
                    quotesAt = updated;
                } catch (Exception failure) { LOGGER.warn("General quotes unavailable; retrying", failure); }
                finally { request = null; }
            }));
        } catch (Exception failure) {
            request = null;
            LOGGER.warn("General quotes unavailable; retrying", failure);
        }
    }

    @Override public boolean needsMenu() {
        if (!running || paused || blocked) return false;
        if (active != null) return true;
        long now = world.now();
        if (workPositions().stream().anyMatch(position -> workDue(position, now))) return true;
        return snapshotFresh && positions.size() < settings().maxActiveItems && !capital.purchaseSettling()
                && !snapshotCandidates.isEmpty();
    }

    @Override public void onTick() {
        if (!running || paused || blocked || !world.inWorld()) return;
        observe();
        if (active == null) selectWork();
        if (active == null || paused || blocked) return;
        long now = world.now();
        if(step==Step.OPEN_PRODUCT || step==Step.PRODUCT || step==Step.QUANTITY
                || step==Step.SIGN || step==Step.PRICE || step==Step.CONFIRM) {
            var recovery=bedrockRecovery.observe(active.tradeId+":"+trade.selling,view,now);
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.EXHAUSTED) {
                fail("Bazaar action icons failed to load after three menu reopens; position retained.");return;
            }
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.REOPEN) {
                navigationRetry.reset();actions.closeMenu();transition(Step.OPEN_PRODUCT);
                services.event("WARN","general.bedrock_menu_reopen",java.util.Map.of("trade",active.tradeId==null?"legacy":active.tradeId,"item",active.item.id()));return;
            }
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.WAITING)return;
        }
        var navigation = navigationRetry.observe(view,world.signEditorOpen(),actions,now);
        if(navigation==com.goofy.goofyaddons.menu.NavigationRetry.Result.EXHAUSTED) {
            services.event("ERROR","general.navigation_exhausted",java.util.Map.of("step",step.name(),"navigation",navigationRetry.diagnosticState(view)));
            fail("Menu navigation was not acknowledged ("+navigationRetry.diagnosticState(view).get("retries")+" retries); position retained.");return;
        }
        if(navigation==com.goofy.goofyaddons.menu.NavigationRetry.Result.RETRIED)
            services.event("WARN","general.navigation_retry",java.util.Map.of("trade",active.tradeId,"step",step.name()));
        if(navigation!=com.goofy.goofyaddons.menu.NavigationRetry.Result.READY)return;
        boolean inputStuck = step==Step.SIGN && !world.signEditorOpen() && !priceMenu()
                || step==Step.PRICE && !priceMenu()
                || step==Step.CONFIRM && !menu("Confirm");
        if(inputStuck && now-stepSince>=com.goofy.goofyaddons.features.transaction.RecoveryRules.INPUT_RESTART_MS && inputRestarts<com.goofy.goofyaddons.features.transaction.RecoveryRules.MAX_INPUT_RESTARTS) {
            inputRestarts++;actions.closeMenu();transition(Step.OPEN_PRODUCT);
            services.event("WARN","general.input_navigation_restart",java.util.Map.of("trade",active.tradeId,"attempt",inputRestarts));
            return;
        }
        if (now - stepSince > com.goofy.goofyaddons.features.transaction.RecoveryRules.STEP_TIMEOUT_MS) { fail("Menu/transaction timed out; retained the tracked position for recovery."); return; }
        if (now < nextAction || claim.coolingDown(now)) return;
        nextAction = now + services.actionDelay();
        try {
            if(reopeningOrders) {
                command("managebazaarorders");
                if(!ordersReady()) {
                    if(view.title()!=null) recheckOrders(recheckReason);
                    return;
                }
                reopeningOrders=false;
            }
            if((step==Step.ORDERS || step==Step.VERIFY_ORDER || step==Step.VERIFY_CANCEL || step==Step.VERIFY_SALE)
                    && view.title()!=null && !TradingSafety.ordersTitle(view.title())) {
                recheckOrders("unexpected-verification-menu");return;
            }
            if((step==Step.ORDERS || step==Step.VERIFY_ORDER || step==Step.VERIFY_CANCEL || step==Step.VERIFY_SALE)
                    && TradingSafety.ordersTitle(view.title()) && !ordersReady()) {
                recheckOrders("orders-not-loaded");return;
            }
            if((trade.selling?active.instantSell:active.instantBuy) && (step==Step.OPEN_PRODUCT || step==Step.PRODUCT)){tickInstant(now);return;}
            switch (step) {
                case OPEN_ORDERS -> {
                    // A sibling craft can leave its reusable grid open when it yields.
                    // Close that settled menu before requesting orders; command() waits for no GUI.
                    if(view.title()!=null && !TradingSafety.ordersTitle(view.title())) {
                        if(view.cursorEmpty())recheckOrders("unexpected-orders-menu");
                        return;
                    }
                    command("managebazaarorders");
                    if (TradingSafety.ordersTitle(view.title())) transition(Step.ORDERS);
                }
                case ORDERS -> {if(!cancellation.resumeVerification(session))review.inspect(session);}
                case CANCEL_DETAIL -> cancellation.detail(session);
                case VERIFY_CANCEL -> cancellation.verify(session);
                case PARTIAL_CLAIM -> cancellation.partial(session);
                case OPEN_PRODUCT -> entry.openProduct(session);
                case PRODUCT -> entry.product(session);
                case QUANTITY -> entry.quantity(session, world.signEditorOpen());
                case SIGN -> entry.sign(session, world.signEditorOpen());
                case PRICE -> entry.price(session, side());
                case CONFIRM -> entry.confirm(session, side());
                case VERIFY_ORDER -> entry.verifyPlacement(session, side());
                case VERIFY_SALE -> sellSide.settle(session);
            }
        } catch (Exception failure) {
            services.failure("general.transaction_failed",failure);
            LOGGER.error("General transaction failed", failure);
            fail("General transaction failed; position retained. Check logs before restarting.");
        }
    }

    private boolean workDue(GeneralPosition position, long now) {
        if(position.completed || services.productionOnly() && position.productionBuy && (position.stage==Stage.INVENTORY || position.productionAwaitingStorage))return false;
        long interval = position.stage == Stage.INVENTORY ? 2000 : settings().refreshSeconds * 1000L;
        return position.stage == Stage.RECONCILE || now - position.checkedAt >= interval;
    }

    private void selectWork() {
        long now = world.now();
        for (GeneralPosition position : workPositions()) {
            if(position.settlementPending && !position.completed) {
                active=position;
                fail("A settlement was interrupted; reconcile the retained order and profit records before restarting.");
                return;
            }
            if (workDue(position, now)) {
                active = position;
                trade = new GeneralTrade();
                trade.selling = position.stage == Stage.INVENTORY || position.stage == Stage.SELL_ORDER;
                cancellation.begin(position);
                claim.reset();
                transition(Step.OPEN_ORDERS);
                return;
            }
        }
        if (services.productionOnly() || !snapshotFresh || positions.size() >= settings().maxActiveItems || capital.purchaseSettling()) return;
        // Reserving capital stays on a live purse read; only ranking uses the snapshot.
        double purse = services.purse();
        for (GeneralCalculator.Candidate candidate : snapshotCandidates) {
            if(services.automaticSelection()) {
                var latest=services.recommendations();
                if(latest==null || !TradingSafety.fresh(latest.marketAt(),world.now()) || latest.rows().isEmpty()
                        || !latest.rows().getFirst().kind().equals("GENERAL")
                        || !latest.rows().getFirst().inputId().equals(candidate.item().id()))continue;
            }
            if (itemCount(candidate.item().id()) > 0 || capital.occupied(candidate.item().id())
                    || world.now() < cooldownUntil.getOrDefault(candidate.item().id(), 0L)) continue;
            if (!capital.reserve(OWNER, candidate.item().id(), candidate.cost(), purse)) continue;
            GeneralPosition position = new GeneralPosition();
            position.item = candidate.item();position.instantBuy=candidate.strategy().instantBuy;position.instantSell=candidate.strategy().instantSell;
            position.tradeId = java.util.UUID.randomUUID().toString();
            position.quantity = candidate.quantity();
            position.forecast = services.forecast(candidate.item().id(),candidate.quantity());
            position.unitCost = candidate.bid();
            position.sellPrice = candidate.ask();
            position.stage = Stage.PLANNED;
            positions.add(position);
            active = position;
            trade = new GeneralTrade();
            cancellation.begin(position);
            claim.reset();
            if (save()) transition(Step.OPEN_ORDERS);
            return;
        }
    }
    private java.util.List<GeneralPosition> workPositions(){
        return services.productionOnly()?positions.stream().filter(p->productionWorkScope==null || productionWorkScope.contains(p))
            .sorted(java.util.Comparator.comparingInt(p->p.stage==Stage.PLANNED?0:1)).toList():positions;
    }

    private void tickInstant(long now)throws Exception {
        if(instantPosition!=active){instantPosition=active;instantBuying=null;instantSelling=null;}
        var strategy=com.goofy.goofyaddons.features.production.BazaarStrategy.of(active.instantBuy,active.instantSell);
        var product=products==null?null:products.getAsJsonObject(active.item.id());
        if(trade.selling){
            if(instantSelling==null){
                if(!freshQuotes())return;Double value=strategy.sell(product,active.quantity);
                if(value==null || !saleAllowed(value/active.quantity)){fail("Instant exit depth or drawdown limit is unavailable; position retained.");return;}
                instantSelling=new com.goofy.goofyaddons.features.production.BazaarInstantSell(active.item.id(),active.item.name(),active.quantity,value*.97,value*1.03,reason->{
                    active.saleEvent=java.util.UUID.randomUUID().toString();active.settlementPending=true;if(!save())throw new IllegalStateException("Sale intent failed");
                });
            }
            var result=instantSelling.tick(view,actions,services.purse(),now);
            switch(result){
                case SOLD -> {if(recordSale(active.quantity,instantSelling.proceeds()))completePosition();}
                case BLOCKED,UNCERTAIN -> fail(instantSelling.failure());
                default -> {}
            }
        }else{
            if(instantBuying==null){
                if(!freshQuotes())return;
                if(active.quantity>capacityFor(active.item.id())){fail("Not enough inventory capacity for instant purchase; no purchase submitted.");return;}
                Double cost=strategy.buy(product,active.quantity),exit=strategy.sell(product,active.quantity);
                if(cost==null||exit==null||cost>settings().maxCoinsPerItem||exit*.97*(1-services.taxPercentage()/100)-cost*1.03<settings().minProfitPerBatch || cost!=null&&exit!=null&&(exit*.97*(1-services.taxPercentage()/100)-cost*1.03)/cost*100<settings().minMarginPercentage){fail("Instant entry no longer meets profit/depth limits; no purchase submitted.");return;}
                double limit=Math.min(cost*1.03,settings().maxCoinsPerItem);if(!capital.resize(OWNER,active.item.id(),limit,services.purse())){fail("Instant entry exceeds spendable capital.");return;}
                instantBuying=new com.goofy.goofyaddons.features.production.BazaarInstantBuy(active.item.id(),active.item.name(),active.quantity,limit,reason->{
                    active.stage=Stage.RECONCILE;active.submitted=true;active.purchasePriceKnown=false;if(!save())throw new IllegalStateException("Buy intent failed");
                });
            }
            var result=instantBuying.tick(view,world.signEditorOpen(),actions,services.purse(),now);
            switch(result){
                case BOUGHT -> {active.unitCost=instantBuying.spent()/active.quantity;active.purchasePriceKnown=true;active.stage=Stage.INVENTORY;capital.purchased(OWNER,active.item.id());capital.funding(OWNER,active.item.id(),active.cost());if(save()&&recordAcquisition()){services.placed(active);finishWork();}}
                case BLOCKED,UNCERTAIN -> fail(instantBuying.failure());
                default -> {}
            }
        }
    }

    private boolean purchasePurseReady(double purse) {
        var result = purseObservation.observe(purse, world.now());
        if (result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.TIMED_OUT)
            fail("Purse stayed unreadable before purchase; no order submitted and position retained.");
        return result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.READY;
    }

    private boolean saleAllowed(double price) {
        // Profit and margin are entry filters. Existing stock exits at a readable
        // market price within the drawdown guard. Age cannot block an exit.
        if(services.productionOnly()) {
            boolean allowed=Double.isFinite(price) && price>0 && price>=services.productionMinimumSellPrice(active);
            productionWait=allowed?null:"Sell offer retained: current price is below the craft profit floor";
            return allowed;
        }
        return Double.isFinite(price) && price > 0 && !TradingSafety.holdingLimit(
                0, world.now(), settings().maxHoldingSeconds,
                active.unitCost, price * (1 - services.taxPercentage() / 100),
                settings().maxDrawdownPercentage);
    }

    void onSlowdown(String message) {
        if (!running || paused || active == null
                || !com.goofy.goofyaddons.features.transaction.ActionRetry.slowdownMessage(message)) return;
        claim.slowdown(world.now());navigationRetry.slowdown(world.now());
        services.event("WARN", "general.slowdown", java.util.Map.of("trade", active.tradeId, "step", step.name()));
    }

    private boolean shouldReprice(boolean sell) {
        if (!freshQuotes() || (!services.productionOnly() && active.reprices >= settings().maxReprices)
                || world.now() - active.placedAt < settings().repriceCooldownSeconds * 1000L) return false;
        // Ingredient replacements return to the parent planner after verified cancellation.
        // It checks the whole basket again; standalone item-flip repricing limits do not apply.
        if (services.productionOnly() && ((!sell && !active.productionBuy) || active.cancelRequested)) return false;
        JsonObject product = products.getAsJsonObject(active.item.id());
        if (product == null) return false;
        double price = com.goofy.goofyaddons.features.market.BazaarQuotes.topPrice(product, sell ? "buy_summary" : "sell_summary");
        return sell ? price > 0 && price < active.sellPrice - 0.01 : price > active.unitCost + 0.01;
    }

    private List<GeneralCalculator.Candidate> candidates() {
        if (services.productionOnly() || !world.inWorld() || products == null) return List.of();
        if(services.automaticSelection())
            return com.goofy.goofyaddons.features.marketanalysis.AutomaticSelection.general(services.recommendations(),world.now(),
                    products,settings(),services.taxPercentage(),capital.available(snapshotPurse),
                    TradingSafety.conservativeCapacity(view.emptyInventorySlots(),4),services.excludedProducts());
        return GeneralCalculator.rankByForecast(GeneralCalculator.calculate(products, settings(), services.taxPercentage(),
                capital.available(snapshotPurse), TradingSafety.conservativeCapacity(view.emptyInventorySlots(), 4))
                .stream().filter(c->!services.excludedProducts().contains(c.item().id())).toList(),services.recommendations(),world.now());
    }

    private void skipUnavailable(String reason) {
        services.event("INFO","general.requirement_skipped",java.util.Map.of("item",active.item.id(),"reason",reason));
        actions.message("Skipping "+active.item.name()+": "+reason);
        completePosition();
        snapshotCandidates=List.of();
    }

    private double currentAsk() {
        JsonObject product = products == null ? null : products.getAsJsonObject(active.item.id());
        return product == null ? -1 : com.goofy.goofyaddons.features.market.BazaarQuotes.topPrice(product, active!=null && active.instantSell?"sell_summary":"buy_summary");
    }
    /** Pure: whether the quotes this engine already adopted are usable right now. */
    private boolean freshQuotes() {
        return products != null && TradingSafety.fresh(quotesAt, world.now());
    }

    /** Adopting a newer snapshot mutates engine state, so it belongs on the tick path only. */
    private void adoptLatestQuotes() {
        JsonObject latest=services.latestQuotes();
        if(latest==null) return;
        long updated=latest.get("lastUpdated").getAsLong();
        if(updated>quotesAt) {products=latest.getAsJsonObject("products");quotesAt=updated;}
    }

    /**
     * One market evaluation and one purse read per tick. Everything that used to
     * recompute these per query - needsMenu() on every scheduler pass and activity()
     * on every rendered frame - now reads the result.
     */
    private void refreshSnapshot() {
        adoptLatestQuotes();
        snapshotPurse = services.purse();
        snapshotFresh = freshQuotes();
        snapshotCandidates = snapshotFresh ? candidates() : List.of();
    }
    private GeneralSettings settings() { return services.settings(); }
    private boolean priceMenu() { return menu(trade.selling ? "At what price" : "How much do you want to pay"); }
    // Now strips formatting codes, matching BazaarFlipper. A code inside the label used
    // to make a known menu unrecognisable, which surfaced as a step timeout.
    private boolean menu(String title) {
        return com.goofy.goofyaddons.utils.MenuText.titleContains(view.title(), title);
    }
    private boolean loadedSlot(int slot) { return view.loaded(slot); }
    private void click(int slot) {
        if (!loadedSlot(slot)) return;
        if(step==Step.PRODUCT || step==Step.QUANTITY || step==Step.PRICE)
            navigationRetry.sent(view,slot,world.now());
        actions.click(slot,false);
    }
    private void transition(Step next) {
        services.event("INFO","general.transition",java.util.Map.of("from",step==null?"none":step.name(),"to",next.name(),"item",taskItem()));
        entry.stepChanged();menuRecheck.reset();reopeningOrders=false;
        purseObservation.reset();
        step = next; stepSince = world.now(); lastCommand = 0;
        ordersSettle.reset();
    }
    private void command(String text) {
        long now = world.now();
        if (view.title() == null && now - lastCommand > 1500) {
            actions.command(text);
            lastCommand = now;
        }
    }

    private boolean recheckOrders(String reason) {
        recheckReason=reason;
        var decision=menuRecheck.missing(step+":"+active.tradeId,world.now());
        if(decision==com.goofy.goofyaddons.features.MenuRecheck.Decision.REOPEN) {
            services.event("WARN","order.observation_recheck",java.util.Map.of("reason",reason,"trade",active.tradeId==null?"legacy":active.tradeId,
                    "attempt",menuRecheck.attempts(),"step",step.name(),"context",orderDiagnosticContext()));
            actions.closeMenu();ordersSettle.reset();lastCommand=0;
            reopeningOrders=true;
        }
        return decision!=com.goofy.goofyaddons.features.MenuRecheck.Decision.EXHAUSTED;
    }

    private int findOrder(boolean sell) {
        for(var slot:view.slots()) {
            if(!slot.inPlayerInventory() && !slot.empty()
                    && TradingSafety.orderNameMatches(slot.hoverName(),sell,active.item.name()))return slot.index();
        }
        return -1;
    }
    private boolean ordersReady() {
        if (!TradingSafety.ordersTitle(view.title())) return false;
        if (!ordersSettle.settled(view.containerId(), world.now())) return false;
        if (find("Go Back", false) < 0 && find("Close", true) < 0) return false;
        if (find("Next Page", false) >= 0 || find("Previous Page", false) >= 0) {
            fail("Orders span multiple pages; automatic ownership checks are blocked."); return false;
        }
        return true;
    }
    private boolean ambiguousOrders() {
        if (TradingSafety.ambiguousOrders(view.containerHoverNames(), active.item.name())) {
            fail("Duplicate or paginated orders; manual reconciliation required."); return true;
        }
        return false;
    }
    private boolean orderMatchesPosition(int slot) {
        String tooltip=lore(slot);
        Integer total=OrderLore.total(tooltip);
        if(total==null && recheckOrders("order-fields-unreadable")) return false;
        if (com.goofy.goofyaddons.utils.MenuText.titleContains(view.title(),"Co-op Bazaar Orders")) {
            OrderLore.Creator creator=OrderLore.creator(tooltip,world.username());
            if(creator==OrderLore.Creator.UNREADABLE && recheckOrders("order-creator-unreadable")) return false;
            if(creator!=OrderLore.Creator.OWN) {
                fail("Co-op order belongs to another player or its creator is unreadable; position retained.");return false;
            }
        }
        if (!TradingSafety.orderQuantityMatches(active.quantity,total)) {
            var evidence=new java.util.LinkedHashMap<String,Object>();
            evidence.put("trade",active.tradeId);evidence.put("slot",slot);evidence.put("expected",active.quantity);evidence.put("parsedTotal",total);evidence.put("lore",tooltip);
            services.event("ERROR","order.quantity_rejected",evidence);
            fail("Order quantity is unreadable or differs from tracked ownership; position retained."); return false;
        }
        return true;
    }
    private int capacityFor(String id) {
        // Policy stays here; the three observations come from the snapshot.
        return TradingSafety.conservativeCapacity(view.emptyInventorySlots(), 4) * view.stackLimitFor(id)
                + view.partialStackSpace(id);
    }
    private int find(String text, boolean exact) { return view.firstByHoverName(text, exact); }
    private String lore(int slot) { return view.loreAt(slot); }
    private double unitPrice(int slot) {
        Double price=TradeReceipts.unitPrice(com.goofy.goofyaddons.utils.Chat.strip(lore(slot)));
        return price==null ? -1 : price;
    }
    private int itemCount(String id) { return view.countInInventory(id); }

    void onNotice(String message) {
        rememberSaleClaim(message);
        if (!running || paused || active == null) return;
        if (cancellation.serverNotice(session, message)) return;
        if(!message.contains(active.item.name()))return;
        sellSide.receipt(session, message);
        cancellation.itemNotice(session, message);
    }
    /**
     * Sale claim receipts from this game session, kept while stopped or paused. A sell offer the
     * player claimed by hand settles from its receipt instead of halting as an unknown absence.
     */
    private record SaleClaimNotice(long at, String account, String text) {}
    private final java.util.ArrayDeque<SaleClaimNotice> saleClaims = new java.util.ArrayDeque<>();
    private void rememberSaleClaim(String message) {
        if (message == null || !message.contains("coins from selling")) return;
        if (running && !paused && active != null && step == Step.VERIFY_SALE && trade.claimPending) return; // Our own claim.
        saleClaims.addLast(new SaleClaimNotice(world.now(), world.username(), message));
        while (saleClaims.size() > 32) saleClaims.removeFirst();
    }
    /** Coins from the one receipt claiming this whole offer since it was placed, or null. */
    private Double claimedOutside(GeneralPosition position) {
        if (position == null || position.stage != Stage.SELL_ORDER || !position.submitted
                || position.cancelRequested || position.settlementPending) return null;
        SaleClaimNotice found = null;
        TradeReceipts.SaleClaim match = null;
        for (var notice : saleClaims) {
            if (notice.at() < position.placedAt || !java.util.Objects.equals(notice.account(), world.username())) continue;
            var claim = TradeReceipts.saleClaim(notice.text(), position.item.name());
            if (claim == null || claim.units() != position.quantity) continue;
            if (claim.unitPrice() != null
                    && Math.abs(claim.unitPrice() - position.sellPrice) > Math.max(1, position.sellPrice * 0.001)) continue;
            if (found != null) return null; // Two candidates; ownership stays with the player.
            found = notice; match = claim;
        }
        if (found == null) return null;
        saleClaims.remove(found);
        return match.coins();
    }
    private boolean recordAcquisition() {
        if (active.tradeId == null) { active.tradeId=java.util.UUID.randomUUID().toString(); if(!save()) return false; }
        services.acquire(active);
        return true;
    }
    private boolean recordSale(int units, Double proceeds) {
        if (active.tradeId == null) { active.tradeId=java.util.UUID.randomUUID().toString(); if(!save()) return false; }
        if (active.saleEvent == null) { active.saleEvent=active.tradeId+":legacy-sale:"+active.placedAt; if(!save()) return false; }
        if(services.productionOnly()) {ProductionSaleAccounting.record(active,units,proceeds);if(!save())return false;}
        services.sell(active, units, proceeds);
        return true;
    }
    private void finishWork() {
        if (active != null && positions.contains(active)) active.checkedAt = world.now();
        save();
        active = null;navigationRetry.reset();inputRestarts=0;
        actions.closeMenu();
    }
    private void completePosition() {
        services.finished(active);
        if (active.stage == Stage.BUY_ORDER || active.stage == Stage.PLANNED) {
            cooldownUntil.put(active.item.id(), world.now() + settings().orderTimeoutSeconds * 1000L);
        }
        capital.release(OWNER, active.item.id());
        if(services.productionOnly()){
            active.completed=true;
            if(!active.productionBuy)active.settlementPending=false;
        }else positions.remove(active);
        finishWork();
    }
    private void fail(String message) {
        lastFailure=message;lastFailureParent=active==null?null:active.productionParent;
        services.event("ERROR","general.transaction_blocked",java.util.Map.of("reason",message,"context",orderDiagnosticContext()));
        actions.message(message);
        LOGGER.error(message);
        if(active!=null)lastBlockedItem="Retained: "+active.quantity+"x "+active.item.name();
        paused = true;
        invalidateRequest();
        save();
        active = null;navigationRetry.reset();inputRestarts=0;
        actions.closeMenu();
        services.safetyPause(message);
    }

    private GeneralOrderEntry.Side side() { return trade.selling ? sellSide : buySide; }

    /** The engine's rules, as the operations see them. */
    private final class Session implements GeneralContext {
        @Override public boolean recoveringProduction(){return productionRecovery;}
        @Override public GeneralPosition active() { return active; }
        @Override public GeneralTrade trade() { return trade; }
        @Override public GeneralClaim claim() { return claim; }
        @Override public List<GeneralPosition> positions() { return positions; }
        @Override public com.goofy.goofyaddons.menu.MenuSnapshot view() { return view; }
        @Override public long now() { return world.now(); }
        @Override public String username() { return world.username(); }
        @Override public com.goofy.goofyaddons.menu.GameActions actions() { return actions; }
        @Override public Services services() { return services; }
        @Override public CapitalManager capital() { return capital; }
        @Override public GeneralSettings settings() { return GeneralFlipper.this.settings(); }
        @Override public Step step() { return step; }
        @Override public long stepSince() { return stepSince; }
        @Override public void transition(Step next) { GeneralFlipper.this.transition(next); }
        @Override public void click(int slot) { GeneralFlipper.this.click(slot); }
        @Override public void command(String text) { GeneralFlipper.this.command(text); }
        @Override public void fail(String message) { GeneralFlipper.this.fail(message); }
        @Override public boolean save() { return GeneralFlipper.this.save(); }
        @Override public boolean menu(String title) { return GeneralFlipper.this.menu(title); }
        @Override public boolean priceMenu() { return GeneralFlipper.this.priceMenu(); }
        @Override public boolean loadedSlot(int slot) { return GeneralFlipper.this.loadedSlot(slot); }
        @Override public int find(String text, boolean exact) { return GeneralFlipper.this.find(text, exact); }
        @Override public String lore(int slot) { return GeneralFlipper.this.lore(slot); }
        @Override public double unitPrice(int slot) { return GeneralFlipper.this.unitPrice(slot); }
        @Override public int itemCount(String id) { return GeneralFlipper.this.itemCount(id); }
        @Override public int capacityFor(String id) { return GeneralFlipper.this.capacityFor(id); }
        @Override public int roomFor(String id) { return view.emptyInventorySlots() * view.stackLimitFor(id) + view.partialStackSpace(id); }
        @Override public boolean ordersReady() { return GeneralFlipper.this.ordersReady(); }
        @Override public boolean ambiguousOrders() { return GeneralFlipper.this.ambiguousOrders(); }
        @Override public int findOrder(boolean sell) { return GeneralFlipper.this.findOrder(sell); }
        @Override public boolean orderMatchesPosition(int slot) { return GeneralFlipper.this.orderMatchesPosition(slot); }
        @Override public boolean recheckOrders(String reason) { return GeneralFlipper.this.recheckOrders(reason); }
        @Override public boolean freshQuotes() { return GeneralFlipper.this.freshQuotes(); }
        @Override public double currentAsk() { return GeneralFlipper.this.currentAsk(); }
        @Override public JsonObject products() { return products; }
        @Override public boolean shouldReprice(boolean sell) { return GeneralFlipper.this.shouldReprice(sell); }
        @Override public boolean purchasePurseReady(double purse) { return GeneralFlipper.this.purchasePurseReady(purse); }
        @Override public boolean saleAllowed(double price) { return GeneralFlipper.this.saleAllowed(price); }
        @Override public void restoreFunding(GeneralPosition position) { GeneralFlipper.this.restoreFunding(position); }
        @Override public boolean recordAcquisition() { return GeneralFlipper.this.recordAcquisition(); }
        @Override public boolean recordSale(int units, Double proceeds) { return GeneralFlipper.this.recordSale(units, proceeds); }
        @Override public void skipUnavailable(String reason) { GeneralFlipper.this.skipUnavailable(reason); }
        @Override public void finishWork() { GeneralFlipper.this.finishWork(); }
        @Override public void completePosition() { GeneralFlipper.this.completePosition(); }
        @Override public Double claimedOutside() { return GeneralFlipper.this.claimedOutside(active); }
    }

    void resetProductionAccount(){
        cancelExternalReconciliation();
        invalidateRequest();positions.clear();active=null;loaded=false;blocked=false;running=false;paused=false;productionRecovery=false;productionWorkScope=null;
        instantPosition=null;instantBuying=null;instantSelling=null;clearSnapshot();
    }
    boolean enqueueProduction(String id,String name,int units,double unitPrice,boolean buy,double limit) {
        return enqueueProduction(null,id,name,units,unitPrice,buy,limit);
    }
    boolean enqueueProductionBasket(String parent,List<ProductionBazaarOrders.IngredientOrder> basket){
        restoreBudget();observe();
        if(!services.productionOnly()||blocked||paused||parent==null||!parent.matches("[A-Za-z0-9_-]{8,80}")
            ||basket==null||basket.isEmpty()||positions.size()+basket.size()>32
            ||positions.stream().anyMatch(p->java.util.Objects.equals(parent,p.productionParent)))return false;
        var ids=new java.util.HashSet<String>();
        for(var order:basket)if(order==null||!GeneralPosition.validProduct(order.product(),true)
            ||order.name()==null||order.name().isBlank()||!ids.add(order.product())||capital.occupied(order.product())
            ||order.units()<1||order.units()>4096||!Double.isFinite(order.price())||order.price()<=0
            ||!Double.isFinite(order.limit())||order.limit()<order.price())return false;
        var reserved=new java.util.ArrayList<String>();
        for(var order:basket){
            if(!capital.reserve(OWNER,order.product(),order.units()*order.price(),services.purse())){
                reserved.forEach(id->capital.release(OWNER,id));return false;
            }reserved.add(order.product());
        }
        for(var order:basket){
            var p=new GeneralPosition();p.item=new GeneralItem(order.product(),order.name());p.quantity=order.units();p.unitCost=order.price();p.sellPrice=order.price();
            p.stage=Stage.PLANNED;p.productionBuy=true;p.productionBasket=true;p.productionParent=parent;p.tradeId=java.util.UUID.randomUUID().toString();
            p.productionInventoryBefore=itemCount(order.product());p.purchasePriceKnown=true;p.maximumBuyPrice=order.limit();positions.add(p);
        }
        return save(); // Persist the complete basket before any menu interaction.
    }
    boolean enqueueProduction(String parent,String id,String name,int units,double unitPrice,boolean buy,double limit) {
        restoreBudget();observe();
        if(parent!=null && (!services.productionOnly() || !parent.matches("[A-Za-z0-9_-]{8,80}")))return false;
        if(!GeneralPosition.validProduct(id,services.productionOnly()&&parent!=null) || name==null || name.isBlank())return false;
        if(blocked||paused||(parent==null?!positions.isEmpty():positions.size()>=32 || positions.stream().anyMatch(p->java.util.Objects.equals(p.productionParent,parent)))||units<1||units>4096||unitPrice<=0||!Double.isFinite(unitPrice))return false;
        if(buy && parent==null && itemCount(id)>0 || !buy && itemCount(id)!=units || capital.occupied(id))return false;
        if(buy && !capital.reserve(OWNER,id,units*unitPrice,services.purse()))return false;
        if(!buy)capital.restore(OWNER,id,units*unitPrice,false);
        var p=new GeneralPosition();p.item=new GeneralItem(id,name);p.quantity=units;p.unitCost=unitPrice;p.sellPrice=unitPrice;
        p.stage=buy?Stage.PLANNED:Stage.INVENTORY;p.productionBuy=buy;p.productionParent=parent;p.tradeId=java.util.UUID.randomUUID().toString();
        p.productionInventoryBefore=buy && parent!=null?itemCount(id):0;
        p.purchasePriceKnown=buy;p.maximumBuyPrice=limit;p.minimumSellPrice=limit;positions.add(p);
        if(!buy){capital.purchased(OWNER,id);capital.funding(OWNER,id,null);}
        return save();
    }
    GeneralPosition productionPosition(){return productionRecovery?positions.stream().filter(p->productionWorkScope.contains(p)).findFirst().orElse(null):positions.isEmpty()?null:positions.getFirst();}
    java.util.List<GeneralPosition> productionPositions(){restoreBudget();return blocked?java.util.List.of():java.util.List.copyOf(positions);}
    GeneralPosition productionPosition(String parent){return positions.stream().filter(p->java.util.Objects.equals(p.productionParent,parent)).findFirst().orElse(null);}
    boolean consumeProduction(){return consumeProduction(productionPosition());}
    /** No order transaction is in progress, so another menu may be opened safely. */
    boolean productionIdle(){return active==null;}
    /** Products whose partial claims wait for the run to store them. */
    java.util.List<String> productionAwaitingStorage(String parent){
        return positions.stream().filter(p->java.util.Objects.equals(parent,p.productionParent) && p.productionAwaitingStorage && !p.completed).map(p->p.item.id()).toList();
    }
    /** Products the run may move freely: claimed in full, or waiting to be stored. */
    java.util.Set<String> productionStorable(String parent){
        var ids=new java.util.HashSet<String>();
        for(var p:positions)if(java.util.Objects.equals(parent,p.productionParent) && p.productionBuy
                && (p.completed || p.stage==Stage.INVENTORY || p.productionAwaitingStorage))ids.add(p.item.id());
        return java.util.Set.copyOf(ids);
    }
    /** Records units of a waiting partial claim that the run verified into storage. */
    boolean productionStored(String parent,String product,int units){
        var p=positions.stream().filter(x->java.util.Objects.equals(parent,x.productionParent) && x.item.id().equals(product)).findFirst().orElse(null);
        if(units<0 || p==null || p==active || !p.productionBuy || p.completed || !p.productionAwaitingStorage)return false;
        int before=p.productionInventoryBefore,stored=p.productionStoredUnits;
        // Units held before the order were moved too; they leave the baseline before counting as stored.
        int fromBaseline=Math.min(before,units);
        p.productionInventoryBefore=before-fromBaseline;p.productionStoredUnits=stored+units-fromBaseline;
        if(p.productionStoredUnits>p.quantity){p.productionInventoryBefore=before;p.productionStoredUnits=stored;return false;}
        p.productionAwaitingStorage=false;p.checkedAt=0;
        if(!save()){p.productionInventoryBefore=before;p.productionStoredUnits=stored;p.productionAwaitingStorage=true;return false;}
        services.event("INFO","order.claim_stored",java.util.Map.of("trade",p.tradeId,"item",product,"units",units,"stored",p.productionStoredUnits,"quantity",p.quantity));
        return true;
    }
    boolean consumeProduction(String parent){
        var selected=positions.stream().filter(p->java.util.Objects.equals(parent,p.productionParent)).toList();
        if(selected.isEmpty()||selected.stream().anyMatch(p->!(p.completed||p.productionBuy&&p.stage==Stage.INVENTORY)))return false;
        var before=java.util.List.copyOf(positions);positions.removeAll(selected);
        if(!save()){positions.clear();positions.addAll(before);return false;}
        selected.forEach(p->capital.release(OWNER,p.item.id()));return true;
    }
    private boolean consumeProduction(GeneralPosition p){if(p==null||!(p.completed||p.productionBuy&&p.stage==Stage.INVENTORY))return false;
        positions.remove(p);if(!save()){positions.add(p);return false;}capital.release(OWNER,p.item.id());return true;
    }
    String productionMenuParent() {
        if(active!=null)return active.productionParent;
        return workPositions().stream().filter(p->workDue(p,world.now())).map(p->p.productionParent==null?"legacy":p.productionParent).findFirst().orElse(null);
    }
    void yieldProductionMenu() {
        // Only an unstarted orders navigation can yield. Never extend a submitted transaction.
        if(services.productionOnly() && active!=null && step==Step.OPEN_ORDERS)stepSince=world.now();
    }
    double productionCommitment(String parent) {
        return positions.stream().filter(p->java.util.Objects.equals(parent,p.productionParent)&&!p.completed).mapToDouble(GeneralPosition::cost).sum();
    }
    double productionBuyCommitment(String parent) {
        return positions.stream().filter(p->java.util.Objects.equals(parent,p.productionParent)&&!p.completed&&p.productionBuy).mapToDouble(GeneralPosition::cost).sum();
    }
    /**
     * Drops every craft order record of one reviewed job, once the player has confirmed in game
     * that those orders were claimed or cancelled by hand. Null when stopped state or saving fails.
     */
    List<GeneralPosition> releaseProduction(String parent) {
        restoreBudget();
        if(!services.productionOnly() || running && !paused || active!=null || blocked || parent==null) return null;
        var selected=positions.stream().filter(p->parent.equals(p.productionParent)).toList();
        if(selected.isEmpty()) return List.of();
        var before=List.copyOf(positions);positions.removeAll(selected);
        if(!save()){positions.clear();positions.addAll(before);return null;}
        selected.forEach(p->capital.release(OWNER,p.item.id()));
        if(lastFailureParent!=null && lastFailureParent.equals(parent)){paused=false;lastFailure=null;lastFailureParent=null;lastBlockedItem=null;}
        return selected;
    }
    /** One line per ordinary (non-craft) position, for the player to check against /bz before releasing. */
    public List<String> generalPositionSummaries(){
        restoreBudget();
        return positions.stream().filter(p->p.productionParent==null).map(p->(p.tradeId==null?"?":p.tradeId.substring(0,Math.min(8,p.tradeId.length())))+" · "+p.item.name()+" · "+p.quantity+"x · "+p.stage
            +(p.submitted?" · submitted":"")+(p.settlementPending?" · sale settling":"")+(p.cancelRequested?" · cancel requested":"")).toList();
    }
    /**
     * Drops ordinary flip records after the player confirmed in game that their orders were
     * claimed or cancelled by hand. A lost confirm click or an unconfirmed sale used to leave a
     * position that failed every start, with no way to clear it. Null when trading is running,
     * the state is unreadable or saving fails.
     */
    public List<GeneralPosition> releaseGeneral(String reference) {
        restoreBudget();
        if(services.productionOnly() || running && !paused || active!=null || blocked || reference==null) return null;
        boolean all=reference.equalsIgnoreCase("all");
        if(!all && reference.length()<4) return null;
        var selected=positions.stream().filter(p->p.productionParent==null && (all || p.tradeId!=null && p.tradeId.regionMatches(true,0,reference,0,reference.length()))).toList();
        if(selected.isEmpty()) return List.of();
        var before=List.copyOf(positions);positions.removeAll(selected);
        if(!save()){positions.clear();positions.addAll(before);return null;}
        selected.forEach(p->capital.release(OWNER,p.item.id()));
        services.event("INFO","general.positions_released",java.util.Map.of("released",selected.stream().map(p->p.tradeId+" "+p.item.id()+" "+p.stage).toList()));
        if(lastFailureParent==null){paused=false;lastFailure=null;lastBlockedItem=null;}
        return selected;
    }
    boolean orphanProductionOrders(java.util.Set<String> parents) {
        restoreBudget();return blocked || positions.stream().anyMatch(p->p.productionParent==null || !parents.contains(p.productionParent));
    }
    boolean productionFailed(String parent){return blocked || paused && java.util.Objects.equals(lastFailureParent,parent);}
    boolean resumeProductionSales(){
        restoreBudget();
        if(!services.productionOnly() || blocked || active!=null)return false;
        var sales=ProductionBazaarOrders.resumableSales(positions);if(sales.isEmpty())return false;
        var parents=sales.stream().map(ProductionBazaarOrders.SavedSale::parent).collect(java.util.stream.Collectors.toSet());
        var scoped=positions.stream().filter(p->parents.contains(p.productionParent) && ProductionBazaarOrders.resumableSale(p)).collect(java.util.stream.Collectors.toSet());
        startProductionScope(scoped,false);
        return true;
    }
    private void startProductionScope(java.util.Set<GeneralPosition> scoped,boolean recovery){
        invalidateRequest();bedrockRecovery.reset();running=true;paused=false;
        lastBlockedItem=null;lastFailure=null;lastFailureParent=null;nextPoll=0;
        productionRecovery=recovery;productionWorkScope=scoped;
        for(var p:scoped){p.checkedAt=0;restoreFunding(p);}
        active=null;navigationRetry.reset();claim.reset();inputRestarts=0;
    }
    boolean productionRecoveryRemaining(){return productionRecovery && positions.stream().anyMatch(p->productionWorkScope.contains(p));}
    boolean recoverProductionBuy() {
        restoreBudget();
        if(!services.productionOnly() || blocked || positions.isEmpty() || active!=null)return false;
        if(positions.stream().anyMatch(p->p.productionBuy
                ? !p.purchasePriceKnown || p.settlementPending || p.stage==Stage.PLANNED && !p.submitted
                : !ProductionBazaarOrders.resumableSale(p) && (p.stage!=Stage.INVENTORY || p.submitted || p.settlementPending)))return false;
        var scoped=positions.stream().filter(p->p.productionBuy || !p.submitted && p.stage==Stage.INVENTORY).collect(java.util.stream.Collectors.toSet());
        if(scoped.isEmpty())return false;
        startProductionScope(scoped,true);
        for(var p:scoped)if(!p.completed) {
            // The existing claim/cancel engine verifies live ownership, inventory and refunds.
            if(p.productionBuy && p.stage!=Stage.INVENTORY)p.cancelRequested=true;
            p.checkedAt=0;
            if(!save()){running=false;return false;}
        }
        return true;
    }

    /** A user-confirmed external claim/move/sale is archived before releasing its journal ownership. */
    void beginExternalReconciliation(String reference) {
        restoreBudget();requireExternalIdle();
        if(externalReconciliation!=null)throw new IllegalStateException("An external order inspection is already pending.");
        if(reference==null || !reference.matches("[A-Za-z0-9-]{8,64}"))
            throw new IllegalArgumentException("Use the exact child order ID or a unique prefix of at least eight characters.");
        var matches=positions.stream().filter(p->p.tradeId!=null && p.tradeId.regionMatches(true,0,reference,0,reference.length())).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("The child order reference must match exactly one retained order.");
        var selected=matches.getFirst();
        if(!externalEligible(selected))throw new IllegalStateException("Only an interrupted, submitted ingredient buy with a known original cost can be reconciled externally.");
        String account=world.username(),scope=services.accountScope();
        if(!world.inWorld() || account==null || account.isBlank() || scope==null || scope.isBlank())
            throw new IllegalStateException("The current SkyBlock account and profile must equal the pinned trading scope.");
        externalReconciliation=new ExternalReconciliation(selected,account,scope,world.now());
    }

    ProductionBazaarOrders.ExternalInspection inspectExternal(boolean ownsMenu) {
        var pending=externalReconciliation;
        if(pending==null)return externalFailed("No external order inspection is pending.");
        try {
            requireExternalIdentity(pending);
            if(world.now()-pending.startedAt>20_000)return externalFailed("Fresh order inspection timed out; the retained order and capital are unchanged.");
            if(!ownsMenu)return externalPending("Waiting for the order inspection menu lease.");
            observe();
            if(pending.attempts==0){requestExternalOrders(pending);return externalPending("Opening fresh Bazaar orders.");}
            if(!TradingSafety.ordersTitle(view.title()) || view.containerId()==pending.previousContainer
                    || view.serverObservation()>=0 && pending.previousObservation>=0 && view.serverObservation()<=pending.previousObservation
                    || view.firstByHoverName("Go Back",false)<0 && view.firstByHoverName("Close",true)<0) {
                if(world.now()-pending.requestedAt>=5_000) {
                    if(pending.attempts>=3)return externalFailed("Fresh Bazaar orders did not load; the retained order and capital are unchanged.");
                    requestExternalOrders(pending);
                }
                return externalPending("Waiting for a fresh, loaded Bazaar orders menu.");
            }
            // LiveMenu may not expose a packet counter. A newly opened container plus a
            // stable complete snapshot still proves a fresh inspection, rather than a cached view.
            if(!view.equals(pending.stableView)) {
                pending.observation=view.serverObservation();pending.container=view.containerId();pending.settle.reset();
                pending.stableView=view;
                pending.evidence=null;pending.readyView=null;
            }
            if(!pending.settle.settled(view.containerId(),world.now()))return externalPending("Waiting for the fresh Bazaar orders capture to settle.");
            requireExternalAbsence(pending.original,view);
            if(pending.evidence==null) {
                var p=pending.original;
                pending.evidence=new ProductionBazaarOrders.ExternalOrderEvidence(java.util.UUID.randomUUID().toString(),pending.account,pending.scope,
                    p.tradeId,p.productionParent,p.item.id(),p.item.name(),p.quantity,p.unitCost,p.cost(),p.placedAt,p.confirmedCancelRefund,
                    world.now(),view.containerId(),view.serverObservation());
                pending.readyView=view;
            }
            return new ProductionBazaarOrders.ExternalInspection(false,false,pending.evidence,
                "No owned order or accessible ingredient stock remains; save the original external-handling evidence before release.");
        }catch(IllegalStateException unsafe){return externalFailed(unsafe.getMessage());}
    }

    boolean finishExternalReconciliation(ProductionBazaarOrders.ExternalOrderEvidence evidence) {
        var pending=externalReconciliation;
        if(pending==null || evidence==null || !evidence.equals(pending.evidence))return false;
        try {
            requireExternalIdentity(pending);observe();
            if(world.now()-evidence.observedAt()>10_000 || !view.equals(pending.readyView))
                throw new IllegalStateException("The live order/inventory capture changed before the evidence was saved; ownership retained.");
            requireExternalAbsence(pending.original,view);
        }catch(IllegalStateException unsafe){externalFailed(unsafe.getMessage());return false;}
        var before=List.copyOf(positions);positions.remove(pending.original);
        if(!save()){positions.clear();positions.addAll(before);externalReconciliation=null;return false;}
        capital.release(OWNER,pending.original.item.id());
        services.event("WARN","order.external_reconciled",java.util.Map.of("child",evidence.child(),"parent",evidence.parent(),
            "product",evidence.product(),"originalUnits",evidence.units(),"originalCost",evidence.cost(),"outcome","USER_CONFIRMED_EXTERNAL_HANDLING"));
        externalReconciliation=null;actions.closeMenu();return true;
    }

    void cancelExternalReconciliation(){externalReconciliation=null;}
    private void requireExternalIdle() {
        if(!services.productionOnly() || running || active!=null || instantPosition!=null || navigationRetry.pending() || productionRecovery || blocked)
            throw new IllegalStateException("Stop production and trading before external order reconciliation; no active transaction may be discarded.");
    }
    private void requireExternalIdentity(ExternalReconciliation pending) {
        requireExternalIdle();
        if(!world.inWorld() || !java.util.Objects.equals(pending.account,world.username())
                || !java.util.Objects.equals(pending.scope,services.accountScope()) || !positions.contains(pending.original)
                || !externalEligible(pending.original) || !pending.originalIdentity.equals(externalIdentity(pending.original)))
            throw new IllegalStateException("The account, profile or exact child ownership changed; reconciliation refused.");
    }
    private static boolean externalEligible(GeneralPosition p) {
        return p.productionBuy && p.productionParent!=null && p.tradeId!=null && p.stage==Stage.BUY_ORDER && p.submitted
            && p.cancelRequested && !p.settlementPending && !p.completed && p.purchasePriceKnown
            && p.quantity>0 && Double.isFinite(p.unitCost) && p.unitCost>0;
    }
    private static String externalIdentity(GeneralPosition p) {
        return p.tradeId+"|"+p.productionParent+"|"+p.item+"|"+p.quantity+"|"+p.unitCost+"|"+p.placedAt+"|"+p.confirmedCancelRefund;
    }
    private void requireExternalAbsence(GeneralPosition p,com.goofy.goofyaddons.menu.MenuSnapshot snapshot) {
        if(!snapshot.cursorEmpty() || snapshot.carried()!=null && !snapshot.carried().empty())
            throw new IllegalStateException("Clear the cursor before external order reconciliation.");
        if(snapshot.firstByHoverName("Next Page",false)>=0 || snapshot.firstByHoverName("Previous Page",false)>=0)
            throw new IllegalStateException("Orders span multiple pages; external reconciliation requires a single complete order capture.");
        if(snapshot.countInInventory(p.item.id())>0)
            throw new IllegalStateException("The ingredient remains in accessible inventory; move or sell it before reconciling external handling.");
        for(var slot:snapshot.slots()) {
            if(slot.empty() || slot.inPlayerInventory())continue;
            boolean order=TradingSafety.orderNameMatches(slot.hoverName(),false,p.item.name())
                || TradingSafety.orderNameMatches(slot.hoverName(),true,p.item.name());
            if(!order && !p.item.id().equals(slot.bazaarProductId()))continue;
            if(com.goofy.goofyaddons.utils.MenuText.titleContains(snapshot.title(),"Co-op Bazaar Orders")) {
                var creator=OrderLore.creator(slot.lore(),world.username());
                if(creator==OrderLore.Creator.OTHER)continue;
                if(creator==OrderLore.Creator.UNREADABLE)
                    throw new IllegalStateException("A matching co-op order has an unreadable creator; ownership remains uncertain.");
            }
            throw new IllegalStateException("A matching owned buy order or sell offer still exists; recover it instead of externally reconciling it.");
        }
    }
    private void requestExternalOrders(ExternalReconciliation pending) {
        pending.previousContainer=view.containerId();pending.previousObservation=view.serverObservation();
        pending.requestedAt=world.now();pending.attempts++;pending.evidence=null;pending.readyView=null;pending.settle.reset();
        actions.closeMenu();actions.command("managebazaarorders");
    }
    private ProductionBazaarOrders.ExternalInspection externalPending(String reason) {
        return new ProductionBazaarOrders.ExternalInspection(true,false,null,reason);
    }
    private ProductionBazaarOrders.ExternalInspection externalFailed(String reason) {
        externalReconciliation=null;return new ProductionBazaarOrders.ExternalInspection(false,true,null,reason);
    }
    private static final class ExternalReconciliation {
        final GeneralPosition original;final String originalIdentity,account,scope;final long startedAt;
        final com.goofy.goofyaddons.features.MenuSettle settle=new com.goofy.goofyaddons.features.MenuSettle();
        int attempts,previousContainer,container=-1;long requestedAt,previousObservation,observation=-1;
        ProductionBazaarOrders.ExternalOrderEvidence evidence;
        com.goofy.goofyaddons.menu.MenuSnapshot readyView,stableView;
        ExternalReconciliation(GeneralPosition original,String account,String scope,long now) {
            this.original=original;this.originalIdentity=externalIdentity(original);this.account=account;this.scope=scope;this.startedAt=now;
        }
    }
    boolean productionFailed(){return blocked||paused;}

    private void load() {
        loaded = true;
        try {
            positions.addAll(GeneralPosition.validated(repository.load()));
        } catch (Exception failure) {
            blocked = true;
            services.failure("general.state_load_failed",failure);
            LOGGER.error("Cannot read general order state; file preserved", failure);
        }
    }
    private boolean save() {
        if (!loaded || blocked) return !blocked;
        try {
            repository.save(List.copyOf(positions));
            return true;
        } catch (Exception failure) {
            blocked = true;
            services.failure("general.state_save_failed",failure);
            LOGGER.error("Unable to persist general order state; trading blocked", failure);
            services.safetyPause("Cannot persist general order state.");
            return false;
        }
    }
}
