package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.helper.BookPosition;
import com.goofy.goofyaddons.features.bookflipper.helper.TradeBudget;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.TransactionWatchdog;
import com.goofy.goofyaddons.features.bookflipper.helper.BookCombiner;
import com.goofy.goofyaddons.features.bookflipper.helper.BookRetirement;
import com.goofy.goofyaddons.features.bookflipper.helper.BookActionRetry;
import com.goofy.goofyaddons.features.bookflipper.helper.BookPricePolicy;
import com.goofy.goofyaddons.features.bookflipper.helper.BookSellCancellation;
import com.goofy.goofyaddons.features.bookflipper.helper.InventoryMemory;
import com.goofy.goofyaddons.features.bookflipper.helper.BookLocations;
import com.goofy.goofyaddons.features.bookflipper.helper.BookPopulation;
import com.goofy.goofyaddons.features.bookflipper.helper.BookTransfer;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.BookList;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BazaarFlipper implements Feature {
    enum State {
        START,
        RECOVERY_CHECK,
        FETCHING,
        STARTUP_CHECK,
        STARTUP_BAZAAR_CHECK,
        IDLE,
        OUTBID,
        BAZAAR_NAVIGATION,
        VERIFY_PLACEMENT,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        REPLACE_SELL,
        VERIFY_ORDER,

    }

    private final com.goofy.goofyaddons.menu.BedrockMenuRecovery bedrockRecovery=new com.goofy.goofyaddons.menu.BedrockMenuRecovery();
    private State state = State.START;
    private State lastState = null;
    private final Clock clock;
    private final FlipCalculator flipCalculator;
    private final BookServices services;
    private final CapitalManager capital;
    private final BookAccounting accounting;
    private final com.goofy.goofyaddons.menu.GameWorld world;
    private final com.goofy.goofyaddons.menu.GameActions actions;
    private final com.goofy.goofyaddons.features.bookflipper.helper.BookOrderRepository repository;
    private final InventoryScanner inventoryScanner = new InventoryScanner(() -> this.observedMenu);
    private final BazaarMonitor bazaarMonitor;
    private boolean running = false;
    private boolean paused = false;
    private boolean journalLoaded;
    private final Set<String> exposedBooks = new HashSet<>();
    private boolean recoveryRequired;
    private boolean recoveryFileError;
    private List<BookPosition> recoveryPositions=List.of();
    private com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck recoveryCheck;
    private boolean modePaused;
    @Override public void navigationResumed(long elapsed){watchdog.reset();clock.stop();navigationRetry.reset();}
    private final TransactionWatchdog watchdog = new TransactionWatchdog();
    private final Map<String, Long> heldSince = new HashMap<>();
    private final com.goofy.goofyaddons.features.MenuSettle ordersSettle=new com.goofy.goofyaddons.features.MenuSettle();
    private long yieldAfterMs;
    private long nextFetchMs;
    private boolean recoveryAwaitQuotes;
    private long recoveryQuoteRetryAt;
    private static final long FETCH_RETRY_MS = 20000;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean needToStoreExcessBook = false;
    private boolean isStartUpCheckCompleted = false;
    private boolean inventoryIsFull = false;
    private boolean checkedFirstPage = false;

    private final BookTransfer bookTransfer = new BookTransfer();
    private final BookCombiner bookCombiner = new BookCombiner();
    private final InventoryMemory inventoryMemory = new InventoryMemory();
    private final BookLocations bookLocations = new BookLocations();
    private final BookPopulation bookPopulation = new BookPopulation();
    private com.goofy.goofyaddons.menu.MenuSnapshot observedMenu;
    private final BookRetirement retirement = new BookRetirement();
    private final BookSellCancellation sellCancellation = new BookSellCancellation();
    private final com.goofy.goofyaddons.menu.NavigationRetry navigationRetry = new com.goofy.goofyaddons.menu.NavigationRetry();
    private Task retiringTask;
    private final java.util.Map<String,Long> retiredUntil=new java.util.HashMap<>();
    public java.util.Set<String> retirementExclusions(){
        long now=world.now();retiredUntil.values().removeIf(until->until<=now);
        return java.util.Set.copyOf(retiredUntil.keySet());
    }
    private int reconciliationPage;
    private long reconciliationStarted;
    private long reconciliationMismatchSince = -1;
    private final com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation purseObservation = new com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation();
    private boolean purseWaitLogged;

    private boolean overFlowProt = false;
    private int tick;

    private final com.goofy.goofyaddons.features.MenuRecheck menuRecheck=new com.goofy.goofyaddons.features.MenuRecheck();
    private Task activeTask = null;
    private Set<Task> listOfTaskToChange = new HashSet<>();
    private List<BookList> bookLists = new ArrayList<>();
    private List<Task> taskList = new ArrayList<>();
    private final Session session = new Session();
    private final BookClaim claim = new BookClaim();
    private final BookPlacement placement = new BookPlacement();
    private final BookBuy buy = new BookBuy();
    private final BookCancel cancel = new BookCancel();
    private final BookStorage storage = new BookStorage();
    private final BookCombine combine = new BookCombine();
    private final BookSell sell = new BookSell();
    private final BookOrderCheck orderCheck = new BookOrderCheck();

    public BazaarFlipper(com.goofy.goofyaddons.menu.GameWorld world,com.goofy.goofyaddons.menu.GameActions actions,
                         com.goofy.goofyaddons.features.bookflipper.helper.BookOrderRepository repository,BookServices services) {
        this.world=java.util.Objects.requireNonNull(world);
        this.actions=java.util.Objects.requireNonNull(actions);
        this.repository=java.util.Objects.requireNonNull(repository);
        this.services=java.util.Objects.requireNonNull(services);
        this.capital=java.util.Objects.requireNonNull(services.capital());
        this.accounting=java.util.Objects.requireNonNull(services.accounting());
        this.clock=new Clock(world::now);
        this.flipCalculator=new FlipCalculator(services::fetchQuotes,world::onClientThread,()->{
            var cfg=settings();return new FlipCalculator.Inputs(cfg.books(),cfg.bazaarTaxPercentage(),cfg.minNetProfit());
        },world::now);
        this.bazaarMonitor=new BazaarMonitor(services::fetchQuotes,world::onClientThread,world::now);
        bazaarMonitor.hook(this::handleOutbid);
    }

    void soldNotice(String message) {if(running&&!paused&&retiringTask!=null)retirement.receipt(retiringTask,message);}
    void slowdownNotice(String message) {
        if (!running || paused || !BookActionRetry.slowdownMessage(message)) return;
        long now=world.now();navigationRetry.slowdown(now);
        if(bookCombiner.pending()) {bookCombiner.slowdown(now);services.event("WARN","books.anvil_slowdown",java.util.Map.of("phase",bookCombiner.progress()));}
        if(bookTransfer.pending()) {bookTransfer.slowdown(now);services.event("WARN","books.transfer_slowdown",java.util.Map.of("state",state.name()));}
    }
    private BookSettings settings() {return services.settings();}
    private String menuTitle() {return world.screenTitle();}
    private boolean screenOpen() {return world.screenOpen();}
    private boolean cursorEmpty() {var menu=world.menu();return menu!=null && menu.cursorEmpty();}

    @Override
    public String name() {
        return "BazaarFlipper";
    }
    public java.util.Map<String,Object> diagnosticState() {
        var state=new java.util.LinkedHashMap<String,Object>();
        state.put("state",this.state.name());state.put("recoveryRequired",recoveryRequired);
        state.put("recovery",java.util.Map.of("pending",recoveryPending(),"fileError",recoveryFileError,"records",recoveryPositions,"progress",recoveryCheck==null?"not checking":recoveryCheck.progress()));state.put("inventoryFull",inventoryIsFull);
        state.put("buyClaimPending",claim.pendingBuy()!=null);state.put("buyClaimBefore",claim.buyBefore());state.put("buyClaimExpected",claim.buyExpected());
        state.put("navigationPending",navigationRetry.pending());
        state.put("retirement",retirement.diagnosticState());
        state.put("sellCancelPending",sellCancellation.pending());
        state.put("saleClaimPending",claim.pendingSale()!=null);state.put("saleReceipt",claim.saleReceipt());
        state.put("ordersContainer",ordersSettle.container());state.put("storagePage",storage.usingSecondPage()?2:1);
        state.put("combine",bookCombiner.diagnosticState());state.put("transferPending",bookTransfer.pending());
        state.put("slotMemory",inventoryMemory.diagnosticState());state.put("reconciliationPage",reconciliationPage);
        state.put("extraBookStacks",bookLists.size());
        state.put("submittedTrade",placement.submittedTask()==null?"none":placement.submittedTask().getProfitTradeId());
        state.put("submittedUnits",placement.submittedUnits());state.put("submittedPrice",placement.submittedPrice());
        state.put("submittedSelling",placement.submittedSelling());state.put("menuRechecks",menuRecheck.attempts());
        state.put("tasks",taskList.stream().map(task->java.util.Map.of("trade",task.getProfitTradeId(),"item",task.getBook().id(),"state",task.getBookState().name(),"remaining",task.getAmountToOrder(),
                "inputLevel",task.getBook().level(),"outputLevel",task.getBook().sellLevel(),
                "plannedCost",task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()),
                "retiring",task.retiring(),"lastProgressAt",task.progressAt(),"holdings",task.bookList.stream().map(book->java.util.Map.of("level",book.level,"region",book.location,"slot",book.slot)).toList())).toList());
        return state;
    }
    public boolean hasRetainedTasks() { return !taskList.isEmpty() || !bookLists.isEmpty(); }
    public String taskItem() {
        Task task=retiringTask!=null?retiringTask:claim.pendingBuy();
        if (task==null) task=switch (state) {
            case BAZAAR_NAVIGATION -> activeTask;
            case VERIFY_PLACEMENT -> placement.submittedTask();
            case STARTUP_BAZAAR_CHECK -> taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
            case OUTBID -> taskInState(Task.BookState.OUTBID);
            case STORE -> taskInState(Task.BookState.STORE);
            case ANVIL -> taskInState(Task.BookState.ANVIL);
            case COMBINE -> taskInState(Task.BookState.COMBINE);
            case SELL -> taskInState(Task.BookState.SELL);
            case REPLACE_SELL -> taskInState(Task.BookState.REPLACE_SELL);
            case VERIFY_ORDER -> taskInState(Task.BookState.VERIFY_ORDER);
            default -> null;
        };
        return task==null ? "No book selected" : task.getBook().getRomanLevel(task.getBook().level())
                +" -> "+task.getBook().getRomanLevel(task.getBook().sellLevel());
    }
    public String activity() {
        if(retiringTask!=null)return "Selling stalled books: "+retiringTask.getBook().name();
        if (claim.pendingBuy() != null) return "Verifying book claim";
        if(recoveryAwaitQuotes)return "Waiting for fresh quotes to resume saved books";
        if(state==State.RECOVERY_CHECK && recoveryCheck!=null)return recoveryCheck.progress();
        return "Books: " + state.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    @Override
    public void stop() {
        // Planned allocations are reversible; observed/submitted ownership is not.
        rememberObservedBooks();
        for (Task task : taskList) {
            if (!exposedBooks.contains(task.getBook().id()))
                capital.release("books", task.getBook().id());
        }
        recoveryRequired |= !exposedBooks.isEmpty() || !bookLists.isEmpty();
        if (journalLoaded) checkpoint();
        watchdog.reset();
        debug("[BazaarFlipper] stop: resetting state, was " + state + " with " + taskList.size() + " active task(s)");
        recoveryCheck=null;
        bookCombiner.reset();
        bookTransfer.reset();
        inventoryMemory.reset();
        cancel.resetStartup();
        bookPopulation.reset();
        reconciliationPage = 0;
        reconciliationMismatchSince = -1;
        observedMenu = null;

        clearTransactionState();retirement.reset();retiringTask=null;sellCancellation.reset();navigationRetry.reset();
        checkedFirstPage = false;
        isStartUpCheckCompleted = false;
        state = State.START;
        taskList.clear();
        bookLists.clear();
        tick = 0;
        listOfTaskToChange.clear();
        running = false;
        paused = false;
        activeTask = null;
        lastState = null;
        claim.resetAttempt();
        needToStoreExcessBook = false;
        inventoryIsFull = false;
        overFlowProt = false;
        storage.reset();
        retirement.reset();retiringTask=null;sellCancellation.reset();navigationRetry.reset();
        bookBuyRetryAt.clear();
        nextFetchMs = 0;recoveryAwaitQuotes=false;recoveryQuoteRetryAt=0;
        yieldAfterMs = 0;
        clock.stop();
        flipItemList.clear();
        flipCalculator.reset();
        bazaarMonitor.stop();
        bazaarMonitor.reset();
        actions.message("BazaarFlipper: Stopped");
    }

    @Override
    public void start() {
        if (!restoreBudget()) return;
        if (recoveryPending()) { restartRecovery(); return; }
        if (paused) {
            resume();
            return;
        }
        running = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void pause() {
        pause(false);
    }

    /** Leaving this engine's mode is reversible; an existing pause keeps its own reason. */
    public void pauseForMode() {
        pause(true);
    }

    private void pause(boolean forMode) {
        // Returning early must not rewrite why the engine is already paused.
        if (!running || paused) return;
        paused = true;
        modePaused = forMode;
        clock.stop();
        flipCalculator.reset();
        bazaarMonitor.stop();
        watchdog.reset();
        checkpoint();
    }

    @Override
    public void resume() {
        if (!running || !paused || !world.inWorld()) return;
        if (modePaused && !recoveryRequired) {
            paused = false;
            modePaused = false;
            bazaarMonitor.start();
            watchdog.reset();
            return;
        }
        if (taskList.isEmpty() && bookLists.isEmpty() && !recoveryRequired) {
            paused = false;
            state = State.START;
            watchdog.reset();
            return;
        }
        // Travel can invalidate every recorded inventory location and open menu.
        // Reconcile inventory and existing orders through normal startup.
        safetyHalt("Book menus changed during travel; reconcile outstanding book positions before restarting.");
    }

    @Override
    public void poll() {
        if (!running || paused) return;
        if(recoveryAwaitQuotes) {
            if(services.latestQuotes()!=null)recoveryAwaitQuotes=false;
            else if(world.now()>=recoveryQuoteRetryAt) {
                recoveryQuoteRetryAt=world.now()+FETCH_RETRY_MS;bazaarMonitor.refresh();
            }
        }
        handleTaskStateChange();
        if(settings().liquidateStaleBooks())promoteOrphanCleanup();else promoteCompletedExtras();
        promoteStalledOrders();
        bazaarMonitor.onTick();
        if (!checkHoldingLimits() || paused) return;
        if(settings().liquidateStaleBooks() && state==State.IDLE && isStartUpCheckCompleted) {
            for(Task task:taskList)if(!task.retiring() && task.stale(world.now(),settings().bookStaleSeconds()*1000L)) {
                task.retire();accounting.retire(task.getProfitTradeId());
                services.event("INFO","books.retirement_queued",java.util.Map.of("trade",task.getProfitTradeId(),"item",task.getBook().id(),"reason","no-progress"));
            }
        }
        if (state == State.FETCHING && !flipCalculator.isRunning()
                && flipCalculator.getFlipItemsList().isEmpty() && world.now() >= nextFetchMs) refreshFlips();
        if (isStartUpCheckCompleted) {
            capital.releaseMissing("books", java.util.stream.Stream.concat(taskList.stream().map(task -> task.getBook().id()),bookLists.stream().map(book -> book.book.id()))
                    .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Override
    public boolean canYield() {
        if (paused || !running) return true;
        if (retirement.pending() || sellCancellation.pending() || navigationRetry.pending()) return false;
        if (bookCombiner.pending() || bookTransfer.pending() || claim.pendingBuy() != null || claim.pendingSale() != null) return false;
        if (reconciliationPage != 0) return false;
        return (state == State.START || state == State.FETCHING || state == State.IDLE)
                && world.now() >= yieldAfterMs
                && (!world.inWorld() || cursorEmpty());
    }

    @Override
    public boolean needsMenu() {
        if (!running || paused || recoveryAwaitQuotes) return false;
        if (state == State.FETCHING) return settings().automaticSelection()
                ? !automaticFlips().isEmpty() : !flipCalculator.isRunning() && !eligibleManualFlips().isEmpty();
        if (state == State.IDLE) return taskList.stream().anyMatch(Task::retiring) || needToStoreExcessBook || world.now() >= nextFetchMs
                || taskList.stream().anyMatch(task -> com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule
                        .actionable(task.getBookState())
                        && (task.getBookState()!=Task.BookState.SELECTED || mayOpenBookOrder(task)));
        return true;
    }

    @Override
    public void yieldMenu() {
        if (canYield() && world.inWorld() && screenOpen()) actions.closeMenu();
    }

    @Override
    public void onTick() {
        if (!running || paused) return;
        if (!world.inWorld()) {
            stop();
            return;
        }
        if(state==State.RECOVERY_CHECK) { checkSavedBooks(); return; }
        if(recoveryAwaitQuotes)return;
        if (world.now() < yieldAfterMs) return;
        observedMenu = world.menu();
        int visiblePage = visibleStoragePage(observedMenu);
        inventoryMemory.observe(observedMenu,visiblePage,world.now());
        var startupMissing = state == State.STARTUP_BAZAAR_CHECK
                ? cancel.startupMissing(session) : null;
        if (!checkpoint()) return;
        if (sellCancellation.pending()) {
            var result = sellCancellation.observe(observedMenu,world.now());
            if (result == BookSellCancellation.Result.BLOCKED) {
                safetyHalt("Cancelled book sell offer did not return exactly one output; ownership retained.");
            } else if (result == BookSellCancellation.Result.RETURNED) {
                services.event("INFO","books.sell_cancel_verified",java.util.Map.of("trade",sellCancellation.task().getProfitTradeId()));
                sellCancellation.reset();
                bookPopulation.reset(); reconciliationPage=0; reconciliationMismatchSince=-1;
                state=State.IDLE; watchdog.reset(); clock.stop();
                checkpoint(); actions.closeMenu();
            } else if (!screenOpen()) {
                clock.start(randomizer());
                if (clock.shouldFire()) actions.command("managebazaarorders");
            }
            return;
        }
        if((state==State.BAZAAR_NAVIGATION || state==State.SELL)
                && placement.submittedTask()==null && claim.pendingBuy()==null && claim.pendingSale()==null
                && !TradingSafety.ordersTitle(observedMenu.title())) {
            var recovery=bedrockRecovery.observe(state+":"+taskItem(),observedMenu,world.now());
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.EXHAUSTED) {
                safetyHalt("Bazaar action icons failed to load after three menu reopens; ownership retained.");return;
            }
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.REOPEN) {
                navigationRetry.reset();placement.abandonConfirmation();buy.menuReopened();
                actions.closeMenu();clock.stop();watchdog.reset();
                services.event("WARN","books.bedrock_menu_reopen",java.util.Map.of("state",state.name(),"item",taskItem()));return;
            }
            if(recovery==com.goofy.goofyaddons.menu.BedrockMenuRecovery.Result.WAITING)return;
        }
        var navigationObservation=navigationRetry.observe(observedMenu,world.signEditorOpen(),
                actions,world.now());
        if(navigationObservation==com.goofy.goofyaddons.menu.NavigationRetry.Result.EXHAUSTED) {
            safetyHalt("Book menu navigation was not acknowledged after three retries; ownership retained.");return;
        }
        if(navigationObservation==com.goofy.goofyaddons.menu.NavigationRetry.Result.RETRIED)
            services.event("WARN","books.navigation_retry",java.util.Map.of("state",state.name(),"item",taskItem()));
        if(navigationObservation!=com.goofy.goofyaddons.menu.NavigationRetry.Result.READY)return;
        if(!buy.signProgress(session))return;
        String progress = state + ":" + bookCombiner.progress() + ":" + bookTransfer.pending()
                + ":" + checkedFirstPage + ":" + (!screenOpen() ? "closed" : menuTitle());
        if(state==State.IDLE && (retiringTask!=null || taskList.stream().anyMatch(Task::retiring))) {
            serviceRetirement();return;
        }
        if (watchdog.stalled(progress, canYield(), world.now())) {
            safetyHalt("Book transaction stopped making progress; positions preserved."); return;
        }
        if (!claim.verifyBuy(session)) return;
        if (isStartUpCheckCompleted && !bookCombiner.pending() && !bookTransfer.pending()
                && claim.pendingSale() == null && placement.submittedTask() == null && !reconcileBookLocations(visiblePage)) return;
        if (containerNameCheck("Confirm") && !TradingSafety.confirmationTitle(menuTitle(),state==State.SELL || state==State.REPLACE_SELL)) {
            safetyHalt("Unexpected book confirmation type; position retained without another click.");return;
        }
        if (containerNameCheck("Confirm") && services.latestQuotes() == null) {
            safetyHalt("Book confirmation blocked because Bazaar quotes expired."); return;
        }
        if (screenOpen() && TradingSafety.ordersTitle(menuTitle())) {
            if (!ordersSettle.settled(observedMenu.containerId(), world.now())) return;
            List<String> names = observedMenu.slots().stream()
                    .filter(slot -> !slot.inPlayerInventory())
                    .map(slot -> com.goofy.goofyaddons.utils.Chat.strip(slot.hoverName())).toList();
            for (Task task : taskList) {
                if(com.goofy.goofyaddons.utils.MenuText.titleContains(menuTitle(),"Co-op Bazaar Orders")) {
                    for(var slot:observedMenu.slots()) {
                        String name=com.goofy.goofyaddons.utils.Chat.strip(slot.hoverName());
                        if(!java.util.Set.of("BUY "+task.getBook().getRomanLevel(task.getBook().level()),"SELL "+task.getBook().getRomanLevel(task.getBook().sellLevel())).contains(name)) continue;
                        var lore=slot.loreLines();
                        String text=lore==null?"":String.join("\n",lore);
                        var creator=com.goofy.goofyaddons.features.generalflipper.OrderLore.creator(text,world.username());
                        if(creator==com.goofy.goofyaddons.features.generalflipper.OrderLore.Creator.UNREADABLE
                                && recheckBookOrders(task,"order-creator-unreadable")) return;
                        if(creator!=com.goofy.goofyaddons.features.generalflipper.OrderLore.Creator.OWN) {
                            safetyHalt("Co-op book order creator differs or is unreadable; manual reconciliation required.");return;
                        }
                    }
                }
                if (TradingSafety.ambiguousOrders(names, task.getBook().getRomanLevel(task.getBook().level()))
                        || TradingSafety.ambiguousOrders(names, task.getBook().getRomanLevel(task.getBook().sellLevel()))) {
                    safetyHalt("Book orders are duplicate or paginated; manual reconciliation required."); return;
                }
            }
        } else { ordersSettle.reset(); }
        try {
        selfRecoveryTrigger();
        if (paused) return;
        lastStateCheck();

        switch (state) {
            case START -> {
                bazaarMonitor.start();
                actions.message("BazaarFlipper: Started");
                state = State.FETCHING;
                debug("[BazaarFlipper] START: going from start to fetching");
            }

            case FETCHING -> {
                if (!settings().automaticSelection() && flipCalculator.isRunning()) return;
                flipItemList.clear();
                flipItemList.addAll(settings().automaticSelection()?automaticFlips():eligibleManualFlips());
                if (flipItemList.isEmpty()) {
                    if (world.now() >= nextFetchMs) refreshFlips();
                    return;
                }
                debug("[BazaarFlipper] FETCHING: list wasn't empty, printing the list and going into processData");
                flipItemList.forEach(flipItem -> {
                    debug("[BazaarFlipper] FETCHING: " + flipItem.book() + " | Cost: " + flipItem.totalCost() + " | Buy: " + flipItem.instaBuy() + " | Sell: " + flipItem.instaSell());
                });
                processData();
            }

            case STARTUP_CHECK -> {
                if (!screenOpen()) clock.start(randomizer());
                if (!screenOpen() && clock.shouldFire()) {
                    actions.command(checkedFirstPage ? settings().secondPage() : settings().firstPage());
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    if (!BookTransfer.pageMatches(
                            menuTitle(), checkedFirstPage ? settings().secondPage() : settings().firstPage())) {
                        safetyHalt("Startup storage page differs or is unsupported; no book holdings adopted.");
                        return;
                    }
                    int startupPage = checkedFirstPage ? 2 : 1;
                    if (!inventoryMemory.fresh(0) || !inventoryMemory.fresh(startupPage)) return;
                    Set<Integer> counter = new HashSet<>();
                    // in here we check both pages
                    for (Task task : taskList) {
                        List<Integer> slots = inventoryScanner.matchingBookInContainer(task.getBook());
                        if (slots.isEmpty()) continue;
                        debug("[BazaarFlipper] STARTUP_CHECK: found " + slots.size() + " container slot(s) matching " + task.getBook() + " on page " + (checkedFirstPage ? 2 : 1));
                        for (Integer i : slots) {
                            if (counter.contains(i)) continue;

                            int level = inventoryScanner.getLevel(i);

                            if (level == -1) continue;

                            int attempt = task.assignBook(task.getBook(), level, checkedFirstPage ? 2 : 1, 1);

                            if (attempt == 0) {
                                debug("[BazaarFlipper] STARTUP_CHECK: slot " + i + " level " + level + " accepted by assignBook (attempt=0)");
                                counter.add(i);
                                continue;
                            }

                            if (!taskList.stream()
                                    .filter(task1 -> task1.getBook().equals(task.getBook())).skip(1).findAny().isPresent()) {
                                debug("[BazaarFlipper] STARTUP_CHECK: slot " + i + " level " + level + " is excess for " + task.getBook() + " (no other task needs it), marking for store");
                                handleBookList(task.getBook(), checkedFirstPage ? 2 : 1, level, 1);
                                counter.add(i);
                            }
                        }
                    }

                    if (!checkedFirstPage) {
                        // in here we check inventory
                        for (Task task : taskList) {
                            task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK);
                            List<Integer> slots = inventoryScanner.matchingBookInInventory(task.getBook());
                            if (slots.isEmpty()) continue;
                            debug("[BazaarFlipper] STARTUP_CHECK: found " + slots.size() + " inventory slot(s) matching " + task.getBook());
                            for (Integer i : slots) {
                                if (counter.contains(i)) continue;

                                int level = inventoryScanner.getLevel(i);

                                int attempt = task.assignBook(task.getBook(), level, 0, 1);

                                if (attempt == 0) {
                                    debug("[BazaarFlipper] STARTUP_CHECK: inventory slot " + i + " level " + level + " accepted by assignBook (attempt=0)");
                                    counter.add(i);
                                    continue;
                                }

                                if (!taskList.stream().skip(taskList.indexOf(task) + 1).filter(task1 -> task1.getBook().equals(task.getBook())).findAny().isPresent()) {
                                    debug("[BazaarFlipper] STARTUP_CHECK: inventory slot " + i + " level " + level + " is excess for " + task.getBook() + ", marking for store");
                                    handleBookList(task.getBook(), 0, level, 1);
                                    counter.add(i);
                                }
                            }
                        }

                        bookLocations.reconcile(trackedBooks(),inventoryMemory,startupPage);
                        checkedFirstPage = true;
                        debug("[BazaarFlipper] STARTUP_CHECK: finished first page (" + bookLists.size() + " book(s) queued for store), moving to second page");
                        actions.closeMenu();
                        return;
                    }

                    bookLocations.reconcile(trackedBooks(),inventoryMemory,startupPage);
                    debug("[BazaarFlipper] STARTUP_CHECK: finished both pages, going to STARTUP_BAZAAR_CHECK");
                    actions.closeMenu();
                    state = State.STARTUP_BAZAAR_CHECK;
                }
            }

            case STARTUP_BAZAAR_CHECK -> cancel.startup(session, startupMissing);

            case IDLE -> {
                if (needToStoreExcessBook) {
                    state = State.STORE;
                    return;
                }

                Task taskToHandle = com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule
                        .next(taskList, isStartUpCheckCompleted, inventoryIsFull, this::mayOpenBookOrder);

                if (taskToHandle == null) {
                    if (world.now() >= nextFetchMs) state = State.FETCHING;
                    return;
                }
                debug("[BazaarFlipper] IDLE: picked task " + taskToHandle.getBook() + " in state " + taskToHandle.getBookState());
                switch (taskToHandle.getBookState()) {

                    case OUTBID -> state = State.OUTBID;

                    case SELECTED -> {
                        activeTask = taskToHandle;
                        state = State.BAZAAR_NAVIGATION;
                    }

                    case STORE -> state = State.STORE;

                    case ANVIL -> {
                        if (!taskToHandle.bookList.isEmpty() && taskToHandle.bookList.getFirst().level == taskToHandle.getBook().sellLevel()) {
                            if (taskToHandle.bookList.getFirst().location != 0) {
                                debug("[BazaarFlipper] IDLE: " + taskToHandle.getBook() + " already at sell level in container, going to ANVIL to pull out then sell");
                                taskToHandle.actionSchedule = Task.ActionSchedule.ANVIL_SELL;
                                taskToHandle.setBookState(Task.BookState.ANVIL);
                                state = State.ANVIL;
                                return;
                            }
                            debug("[BazaarFlipper] IDLE: " + taskToHandle.getBook() + " already at sell level in inventory, going to SELL");
                            taskToHandle.setBookState(Task.BookState.SELL);
                            state = State.SELL;
                            return;
                        }

                        if (!taskToHandle.bookList.isEmpty() && taskToHandle.bookList.getLast().location == 0) {
                            debug("[BazaarFlipper] IDLE: " + taskToHandle.getBook() + " already in inventory, going to COMBINE");
                            taskToHandle.setBookState(Task.BookState.COMBINE);
                            state = State.COMBINE;
                            taskToHandle.bookList.sort(Comparator.comparingInt(bookList -> bookList.level));
                            return;
                        }
                        state = State.ANVIL;
                    }
                    case SELL -> state = State.SELL;
                    case BAZAAR_ORDER_CHECK -> state = State.STARTUP_BAZAAR_CHECK;

                    case REPLACE_SELL -> state = State.REPLACE_SELL;
                    case VERIFY_ORDER -> state = State.VERIFY_ORDER;
                    case COMBINE -> {
                        state = State.COMBINE;
                        taskToHandle.bookList.sort(Comparator.comparingInt(bookList -> bookList.level));
                    }
                }
            }

            case BAZAAR_NAVIGATION -> buy.tick(session);

            case VERIFY_PLACEMENT -> placement.verify(session);

            case OUTBID -> cancel.outbid(session);

            case STORE -> storage.store(session);

            case ANVIL -> storage.retrieve(session);

            case COMBINE -> combine.tick(session);

            case SELL -> sell.sell(session);

            case VERIFY_ORDER -> orderCheck.tick(session);

            case REPLACE_SELL -> sell.replace(session);

        }

        } finally { checkpoint(); }
    }

    private void promoteOrphanCleanup() {
        if(!isStartUpCheckCompleted || state!=State.IDLE || placement.submittedTask()!=null || claim.pendingBuy()!=null || claim.pendingSale()!=null)return;
        for(Book original:bookLists.stream().map(b->b.book).distinct().toList()) {
            if(taskList.stream().anyMatch(t->t.getBook().id().equals(original.id())))continue;
            var extras=bookLists.stream().filter(b->b.book.id().equals(original.id())).toList();
            int from=Math.min(9,extras.stream().mapToInt(b->b.level).min().orElse(1));
            int to=Math.max(from+1,extras.stream().mapToInt(b->b.level).max().orElse(from));
            int finalFrom=from;
            int units=extras.stream().mapToInt(b->1<<(b.level-finalFrom)).sum();
            while(to<10 && (1<<(to-from))<units)to++;
            if(to>10 || (1<<(to-from))<units)continue;
            Book book=new Book(original.id(),from,to,original.name(),0,0);
            Task task=new Task(book,false,false);task.setBookState(Task.BookState.SELECTED);task.markOrphanCleanup();
            for(BookList extra:extras) {
                if(task.assignBook(book,extra.level,extra.location,1)!=0)throw new IllegalStateException("Orphan cleanup quantity cannot be represented");
                var adopted=task.bookList.stream().filter(b->b.level==extra.level&&b.location==extra.location&&b.slot==-1).findFirst().orElseThrow();
                adopted.slot=extra.slot;adopted.found=extra.found;
            }
            double hold=Math.max(1,capital.cost("books",original.id()));
            task.setReservedUnitCost(hold/book.getQtyAmount(from));
            capital.restore("books",book.id(),hold,false);
            taskList.add(task);bookLists.removeAll(extras);exposedBooks.add(book.id());
            accounting.acquire(task.getProfitTradeId(),"books",book.name(),task.getProfitTradeId()+":cleanup-holdings",units,null);
            accounting.retire(task.getProfitTradeId());
            services.event("INFO","books.retirement_queued",java.util.Map.of("trade",task.getProfitTradeId(),"item",book.id(),"reason","unassigned-leftovers"));
        }
        needToStoreExcessBook=bookLists.stream().anyMatch(b->b.location==0);
    }

    private void promoteCompletedExtras() {
        if(!isStartUpCheckCompleted || placement.submittedTask()!=null || claim.pendingBuy()!=null || claim.pendingSale()!=null)return;
        for(Book book:bookLists.stream().map(b->b.book).distinct().toList()) {
            if(taskList.stream().anyMatch(t->t.getBook().id().equals(book.id())))continue;
            int units=bookLists.stream().filter(b->b.book.equals(book)).mapToInt(b->book.baseUnits(b.level)).sum();
            double unitCost=capital.cost("books",book.id())/Math.max(1,units);
            Task task=com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryPlan.completedExtraCycle(book,bookLists,unitCost);
            if(task==null)continue;
            for(var holding:task.bookList) {
                var original=bookLists.stream().filter(b->b.book.equals(book) && b.level==holding.level
                        && b.location==holding.location && b.slot==holding.slot).findFirst().orElseThrow();
                bookLists.remove(original);
            }
            taskList.add(task);exposedBooks.add(book.id());
            accounting.recoverHoldings(task.getProfitTradeId(),"books",book.name(),book.getQtyAmount(book.level()));
            services.event("INFO","books.extra_cycle_resumed",java.util.Map.of("item",book.id(),"trade",task.getProfitTradeId()));
        }
    }

    private List<BookList> trackedBooks() {
        var books = new ArrayList<BookList>(bookLists);
        for (var task : taskList) {
            boolean listed = task.getBookState() == Task.BookState.SELL_ORDER
                    || task.getBookState() == Task.BookState.REPLACE_SELL || task.getBookState() == Task.BookState.VERIFY_ORDER;
            for (var book : task.bookList)
                if (!(listed && book.location == 0 && book.level == task.getBook().sellLevel())) books.add(book);
        }
        return books;
    }

    private int visibleStoragePage(com.goofy.goofyaddons.menu.MenuSnapshot menu) {
        if (menu == null || !menu.loaded(8)) return 0;
        if (BookTransfer.pageMatches(menu.title(),settings().firstPage())) return 1;
        if (BookTransfer.pageMatches(menu.title(),settings().secondPage())) return 2;
        return 0;
    }

    /** Inspect remembered storage when inventory disagrees, before allowing another book action. */
    private boolean reconcileBookLocations(int visiblePage) {
        if (!inventoryMemory.fresh(0)) return false;
        long now = world.now();
        if (reconciliationPage != 0) {
            if (now - reconciliationStarted >= 30_000) {
                safetyHalt("Could not observe storage to reconcile book locations; slot history retained."); return false;
            }
            if (visiblePage != reconciliationPage) {
                if (screenOpen()) { actions.closeMenu(); clock.stop(); }
                else {
                    clock.start(randomizer());
                    if (clock.shouldFire()) actions.command(reconciliationPage == 1 ? settings().firstPage() : settings().secondPage());
                }
                return false;
            }
            if (!inventoryMemory.fresh(visiblePage)) return false;
        }
        var result = bookLocations.reconcile(trackedBooks(),inventoryMemory,visiblePage);
        var population = bookPopulation.inspect(trackedBooks(),taskList.stream().map(Task::getBook).toList(),inventoryMemory);
        if (result.corrected() > 0) {
            for (var task : taskList) task.bookList.sort(Comparator.comparingInt(book -> book.location));
            services.event("WARN","books.locations_reconciled",java.util.Map.of(
                    "corrected",result.corrected(),"page",visiblePage,"memory",inventoryMemory.diagnosticState()));
            if (!checkpoint()) return false;
        }
        int inspectPage = result.inspectPage() > 0 ? result.inspectPage() : population.inspectPage();
        if (inspectPage > 0) {
            if (reconciliationPage == 0) reconciliationStarted = now;
            reconciliationPage = inspectPage;
            if (screenOpen()) actions.closeMenu();
            clock.stop();
            return false;
        }
        if (!population.missing().isEmpty()) {
            if (reconciliationMismatchSince < 0) {
                reconciliationMismatchSince = now;
                if (reconciliationPage == 0) {
                    reconciliationPage = visiblePage > 0 ? visiblePage : 1;
                    reconciliationStarted = now;
                }
                services.event("WARN","books.locations_settling",java.util.Map.of("memory",inventoryMemory.diagnosticState()));
            }
            if (now - reconciliationMismatchSince >= 5_000) applyBookPopulation(population);
            return false;
        }
        reconciliationMismatchSince = -1;
        if (!population.found().isEmpty()) {
            applyBookPopulation(population);
            return false;
        }
        if (result.unresolved()) {
            safetyHalt("Book discrepancy is outside the physical loss/found policy; ownership retained."); return false;
        }
        if (reconciliationPage != 0) {
            reconciliationPage = 0;
            actions.closeMenu(); clock.stop();
            return false;
        }
        return true;
    }

    private void applyBookPopulation(BookPopulation.Difference difference) {
        var changed = new HashSet<Task>();
        for (var missing : difference.missing()) {
            var owner = taskList.stream().filter(task -> task.bookList.contains(missing)).findFirst().orElse(null);
            int units = missing.book.baseUnits(missing.level);
            if (owner != null) {
                units = owner.loseBook(missing);
                accounting.writeOff(owner.getProfitTradeId(),"books",owner.getBook().name(),
                        java.util.UUID.randomUUID().toString(),units);
                changed.add(owner);
            } else bookLists.remove(missing);
            services.event("WARN","books.book_written_off",java.util.Map.of("item",missing.book.id(),
                    "level",missing.level,"lastRegion",missing.location,"lastSlot",missing.slot,"baseUnits",units));
        }
        for (var found : difference.found()) {
            var owner = taskList.stream().filter(task -> task.getBook().equals(found.book)
                    && task.getAmountToOrder() >= found.book.baseUnits(found.level)).findFirst().orElse(null);
            if (owner != null && owner.acceptFound(found)) {
                accounting.acquire(owner.getProfitTradeId(),"books",owner.getBook().name(),
                        java.util.UUID.randomUUID().toString(),found.book.baseUnits(found.level),0.0);
                changed.add(owner);
            } else {
                bookLists.add(found);
                if (found.location == 0) needToStoreExcessBook = true;
            }
            services.event("INFO","books.book_found",java.util.Map.of("item",found.book.id(),
                    "level",found.level,"region",found.location,"slot",found.slot));
        }
        // Existing orders must be observed before any replacement or amended route is submitted.
        for (var task : changed) { task.actionSchedule = Task.ActionSchedule.NONE; task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK); }
        bookPopulation.reset();
        reconciliationPage = 0; reconciliationMismatchSince = -1;
        if (!checkpoint()) return;
        if (screenOpen()) actions.closeMenu();
        state = State.IDLE; clock.stop();
    }

    private boolean containerNameCheck(String name) {
        if (!screenOpen()) return false;
        return com.goofy.goofyaddons.utils.MenuText.titleContains(menuTitle(), name);
    }

    private void lastStateCheck() {
        if (state == lastState) return;
        cancel.stateChanged();
        if (screenOpen()) actions.closeMenu();
        tick = 0;
        claim.resetAttempt();
        services.event("INFO","books.transition",java.util.Map.of("from",lastState==null?"none":lastState.name(),"to",state.name()));
        clock.stop();
        if (lastState == State.IDLE) {
            inventoryIsFull = false;
        }
        lastState = state;
        menuRecheck.reset();

        if (state == State.FETCHING) {
            flipItemList.clear();
            refreshFlips();
        }
    }

    private void processData() {
        if(settings().automaticSelection()) {
            flipItemList.clear();flipItemList.addAll(automaticFlips());
        }
        double purse = services.purse();
        // Money Check
        debug("[BazaarFlipper] PROCESSDATA: purse=" + purse + ", flipItemList size=" + flipItemList.size());
        if (!Double.isFinite(purse) || purse < 0) {
            state = State.IDLE;
            return;
        }
        if (capital.purchaseSettling()) { state = State.IDLE; return; }
        var denied=com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().excluded();
        var eligible=flipItemList.stream().filter(f->!retirementExclusions().contains(f.book().id())
                && !denied.contains(f.book().getLevel(f.book().level())) && !denied.contains(f.book().getLevel(f.book().sellLevel()))).toList();
        for (FlipItem flipItem : TradeBudget.select(eligible, taskList,
                capital.available(purse), capital.occupiedProducts())) {
            if (taskList.size() >= settings().maxActiveBooks()) break;
            if (!capital.owns("books", flipItem.book().id())
                    && !capital.reserve("books", flipItem.book().id(), flipItem.totalCost(), purse)) continue;
            debug("[BazaarFlipper] PROCESSDATA: creating task for " + flipItem.book().getRomanLevel(flipItem.book().level()) + " (cost=" + flipItem.totalCost() + ", instaBuy=" + flipItem.instaBuy() + ", instaSell=" + flipItem.instaSell() + ")");
            Task task = new Task(flipItem.book(), flipItem.instaBuy(), flipItem.instaSell());
            task.forecast(services.executionForecast(
                    task.getBook().getLevel(task.getBook().level()),task.getBook().getLevel(task.getBook().sellLevel()),1));
            task.setReservedUnitCost(flipItem.totalCost() / flipItem.book().getQtyAmount(flipItem.book().level()));
            taskList.add(task);

            Iterator<BookList> iterator = bookLists.iterator();

            while (iterator.hasNext()) {
                BookList bookList = iterator.next();

                if (!bookList.book.equals(flipItem.book())) continue;

                int attempt = task.assignBook(bookList.book, bookList.level, bookList.location, 1);

                if (attempt != -1) {
                    if (bookList.found) accounting.acquire(task.getProfitTradeId(),"books",task.getBook().name(),
                            java.util.UUID.randomUUID().toString(),bookList.book.baseUnits(bookList.level),0.0);
                    debug("[BazaarFlipper] PROCESSDATA: pre-existing book level " + bookList.level + " (location=" + bookList.location + ") folded into new task for " + task.getBook());
                    iterator.remove();
                }
            }

            if (task.getAmountToOrder() == 0) {
                task.setBookState(Task.BookState.ANVIL);
                continue;
            }

            if (task.isCombinable()) {
                task.setBookState(Task.BookState.SELECTED);
                task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                continue;
            }
            task.setBookState(Task.BookState.SELECTED);
        }

        if (overFlowProt) {
            overFlowProt = false;
            state = State.IDLE;
            return;
        }
        state = taskList.isEmpty() || isStartUpCheckCompleted ? State.IDLE : State.STARTUP_CHECK;
    }

    private void refreshFlips() {
        nextFetchMs = world.now() + FETCH_RETRY_MS;
        if(settings().automaticSelection()) {flipCalculator.reset();return;}
        flipCalculator.Refresh();
    }
    private List<FlipItem> eligibleManualFlips() {
        return flipCalculator.getFlipItemsList().stream().filter(f->com.goofy.goofyaddons.features.access.RouteRequirements.book(f.book().id(),
                services.observedSkills())==null).toList();
    }
    private List<FlipItem> automaticFlips() {
        var market=services.latestQuotes();
        return market==null?List.of():com.goofy.goofyaddons.features.marketanalysis.AutomaticSelection.books(
                services.automaticReport(),world.now(),
                market.getAsJsonObject("products"),settings().bazaarTaxPercentage(),settings().minNetProfit(),services.observedSkills());
    }

    private int randomizer() {
        return services.actionDelay();
    }

    /**
     * Gives a long-untouched order a read-only look. Without this, a task whose fill
     * notice was missed waited forever: the scheduler reported no work, its capital
     * stayed reserved, and nothing surfaced a problem.
     */
    private void promoteStalledOrders() {
        Task stalled = com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule.staleOrder(
                taskList, world.now(), settings().bookOrderRecheckSeconds() * 1000L);
        if (stalled == null) return;
        services.event("INFO", "books.order_recheck_due", java.util.Map.of("trade", stalled.getProfitTradeId(),
                "waitedMs", world.now() - stalled.orderWaitSince(), "selling", stalled.awaitingSale()));
        stalled.setBookState(Task.BookState.VERIFY_ORDER);
    }

    private Task taskInState(Task.BookState bookState) {
        return taskList.stream().filter(task -> task.getBookState() == bookState).findFirst().orElse(null);
    }

    private void handleBookList(Book book, int location, int level, int amount) {
        debug("[BazaarFlipper] handleBookList: queuing " + amount + "x level " + level + " " + book + " at location " + location + (location == 0 ? " (will need storing)" : ""));
        if (location == 0) needToStoreExcessBook = true;
        for (int i = 0; i < amount; i++) {
            bookLists.add(new BookList(book, level, location));
        }
        bookLists.sort(Comparator.comparingInt(bookList -> bookList.location));
    }

    void handleClaimedMessage(String string) {
        if (!running || paused) return;
        claim.claimedMessage(string);
    }

    void handleFilledMessage(String string) {
        if (!running || !string.startsWith("[Bazaar] Your ")
                || !(string.contains("Buy Order") || string.contains("Sell Offer"))) return;
        String stripped;
        boolean isSellOffer = false;

        if (string.contains("Buy Order")) {
            stripped = string.replace("[Bazaar] Your Buy Order for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            actions.message("BazaarFlipper: Buy order complete for " + stripped);
        } else {
            stripped = string.replace("[Bazaar] Your Sell Offer for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            actions.message("BazaarFlipper: Sell offer complete for " + stripped);
            isSellOffer = true;
        }

        debug("[BazaarFlipper] onOrderNotice: parsed \"" + stripped + "\" isSellOffer=" + isSellOffer);

        for (Task task : taskList) {
            if (!stripped.equals(task.getBook().getRomanLevel(task.getBook().level())) && !isSellOffer) continue;
            if (!stripped.equals(task.getBook().getRomanLevel(task.getBook().sellLevel())) && isSellOffer) continue;

            debug("[BazaarFlipper] onOrderNotice: matched task " + task.getBook() + ", queuing state change");
            listOfTaskToChange.add(task);
            bazaarMonitor.finish(task.getBook(), isSellOffer);
        }
    }

    private void handleTaskStateChange() {
        // Notices for finished or removed tasks would otherwise accumulate forever.
        listOfTaskToChange.retainAll(taskList);
        if (listOfTaskToChange.isEmpty()) return;

        if (state == State.OUTBID || state == State.REPLACE_SELL) return;
        for (Task task : new HashSet<>(listOfTaskToChange)) {
            switch (task.getBookState()) {
                case SELL_ORDER -> {
                    debug("[BazaarFlipper] handleTaskStateChange: " + task.getBook()
                            + " sell order complete, moving to REPLACE_SELL");

                    task.setBookState(Task.BookState.REPLACE_SELL);
                    listOfTaskToChange.remove(task);
                }

                case IN_BUY_ORDER -> {
                    debug("[BazaarFlipper] handleTaskStateChange: " + task.getBook()
                            + " outbid, moving to OUTBID");

                    task.setBookState(Task.BookState.OUTBID);
                    listOfTaskToChange.remove(task);
                }
            }
        }
    }

    private void handleOutbid(BazaarMonitor.BazaarMonitorItem book) {
        if (!running || paused) return;
        for (Task task : taskList) {
            if (!book.book.equals(task.getBook())) continue;

            if (book.isSellOrder && (task.getBookState() == Task.BookState.REPLACE_SELL || task.getBookState() == Task.BookState.SELL_ORDER)) {
                debug("[BazaarFlipper] handleOutbid: sell order for " + task.getBook() + " was outbid/undercut, queuing state change");
                listOfTaskToChange.add(task);
            } else if (!book.isSellOrder && task.getBookState() == Task.BookState.IN_BUY_ORDER) {
                debug("[BazaarFlipper] handleOutbid: buy order for " + task.getBook() + " was outbid, queuing state change");
                listOfTaskToChange.add(task);
            }
        }
    }

    private void selfRecoveryTrigger() {
        if (!claim.attempted()) {
            tick = 0;
            return;
        }
        tick++;
        if (tick != 1200) return;
        initSelfRecovery();
    }

    private void debug(String string) {
        services.event("INFO","books.progress",java.util.Map.of("detail",string));
    }

    private void initSelfRecovery() {
        safetyHalt("Book transaction could not be verified; blind restart blocked.");
    }

    public boolean restoreBudget() {
        if (!journalLoaded || recoveryRequired && !running) {
            journalLoaded=true;
            try {
                recoveryPositions=BookPosition.validated(journal().read(),world.now());
                var savedIds=recoveryPositions.stream().map(p->p.book().id()).collect(java.util.stream.Collectors.toSet());
                capital.releaseMissing("books",savedIds);
                exposedBooks.retainAll(savedIds);
                for(var position:recoveryPositions)capital.restore("books",position.book().id(),position.cost(),true);
                recoveryRequired=!recoveryPositions.isEmpty();recoveryFileError=false;recoveryCheck=null;
            } catch(Exception invalid) {
                services.failure("books.journal_load_failed",invalid);recoveryRequired=true;recoveryFileError=true;
                actions.message("Book journal unreadable; file preserved. Fix the file and use the trading toggle again.");
            }
        }
        return !recoveryFileError;
    }
    public boolean recoveryPending() {return recoveryRequired && !recoveryFileError && !recoveryPositions.isEmpty();}
    public void restartRecovery() {
        recoveryCheck=new com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck(recoveryPositions);
        state=State.RECOVERY_CHECK;running=true;paused=false;modePaused=false;
        actions.message("Checking saved book records against inventory, storage and Bazaar orders.");
    }
    private void checkSavedBooks() {
        if(recoveryCheck==null)restartRecovery();
        var result=recoveryCheck.tick(world.menu(),actions,
                settings().firstPage(),settings().secondPage(),world.username(),world.now());
        if(result==com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck.Result.WAITING)return;
        if(result==com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck.Result.BLOCKED) {safetyHalt(recoveryCheck.reason());return;}
        try {
            var present=recoveryCheck.present();
            // Validate the complete plan before changing the journal or any runtime task.
            var plan=com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryPlan.build(
                    recoveryPositions,recoveryCheck.snapshots(),world.username());
            journal().backupVerified(recoveryPositions);
            journal().reconcileVerified(recoveryPositions,present);
            var resumedRecords=plan.routes().stream().map(r->new BookPosition(r.task().getBook(),
                    Math.max(1,r.task().getReservedUnitCost()*r.task().getBook().getQtyAmount(r.task().getBook().level())),
                    r.task().getProfitTradeId(),r.task().retiring(),r.task().progressAt(),r.task().orphanCleanup())).toList();
            journal().write(resumedRecords);recoveryPositions=resumedRecords;
            clearTransactionState();bookCombiner.reset();bookTransfer.reset();
            listOfTaskToChange.clear();activeTask=null;claim.resetAttempt();
            inventoryIsFull=false;overFlowProt=false;storage.reset();tick=0;
            reconciliationPage=0;reconciliationMismatchSince=-1;
            cancel.stateChanged();bookPopulation.reset();
            taskList.clear();bookLists.clear();bazaarMonitor.reset();inventoryMemory.reset();
            for(var page:recoveryCheck.snapshots().entrySet()) {
                int region=page.getKey()<3?page.getKey():0;
                inventoryMemory.observe(page.getValue(),region,world.now()-1500);
                inventoryMemory.observe(page.getValue(),region,world.now());
            }
            capital.releaseMissing("books",present);
            exposedBooks.clear();exposedBooks.addAll(present);
            for(var recovered:plan.routes()) {
                Task task=recovered.task();taskList.add(task);
                accounting.recoverHoldings(task.getProfitTradeId(),"books",task.getBook().name(),recovered.acquiredUnits());
                capital.restore("books",task.getBook().id(),
                        task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()),false);
                if(recovered.orderPrice()==null || recovered.selling())fundedHoldings(task);
                if(recovered.orderPrice()!=null)bazaarMonitor.add(task.getBook(),recovered.orderPrice(),recovered.selling());
            }
            bookLists.addAll(plan.extras());
            needToStoreExcessBook=bookLists.stream().anyMatch(b->b.location==0);
            recoveryRequired=false;recoveryCheck=null;
            isStartUpCheckCompleted=!plan.routes().isEmpty();checkedFirstPage=isStartUpCheckCompleted;
            state=plan.routes().isEmpty()?State.START:State.IDLE;lastState=null;clock.stop();watchdog.reset();
            nextFetchMs=world.now()+FETCH_RETRY_MS;
            if(!checkpoint())return;
            recoveryPositions=List.of();
            recoveryAwaitQuotes=!plan.routes().isEmpty() && services.latestQuotes()==null;
            recoveryQuoteRetryAt=world.now()+FETCH_RETRY_MS;
            bazaarMonitor.start();if(recoveryAwaitQuotes)bazaarMonitor.refresh();running=false;
            services.event("INFO","books.recovery_resumed",java.util.Map.of("present",present,
                    "tasks",plan.routes().size(),"extras",plan.extras().size()));
            actions.message(plan.routes().isEmpty()?"Saved book records were stale; continuing startup.":
                    "Resumed "+plan.routes().size()+" saved book positions and "+plan.extras().size()+" extra books.");
        } catch(Exception failure) {
            services.failure("books.recovery_resume_failed",failure);safetyHalt("Cannot resume saved books: "+failure.getMessage()+". Ownership file kept; use the trading toggle to recheck.");
        }
    }

    private com.goofy.goofyaddons.features.bookflipper.helper.BookOrderRepository journal() {return repository;}

    private boolean checkpoint() {
        // A rejected read-only recovery plan must never overwrite the saved evidence.
        if(state==State.RECOVERY_CHECK && recoveryRequired)return false;
        if (!journalLoaded || recoveryRequired && taskList.isEmpty() && bookLists.isEmpty()) return !recoveryRequired;
        try {
            Map<String, BookPosition> positions = new HashMap<>();
            exposedBooks.retainAll(taskList.stream().map(task -> task.getBook().id()).collect(java.util.stream.Collectors.toSet()));
            rememberObservedBooks();
            for (Task task : taskList) positions.put(task.getBook().id(), new BookPosition(task.getBook(),
                    Math.max(1, task.getReservedUnitCost() * task.getBook().getQtyAmount(task.getBook().level())),task.getProfitTradeId(),task.retiring(),task.progressAt(),task.orphanCleanup()));
            for (BookList extra : bookLists) {
                BookPosition previous = positions.get(extra.book.id());
                positions.put(extra.book.id(), previous == null ? new BookPosition(extra.book,
                        settings().maxTradingCapital()) : previous);
            }
            journal().writeTracked(positions.values().stream().sorted(Comparator.comparing(position -> position.book().id())).toList(), exposedBooks);
            return true;
        } catch (Exception failed) {
            services.failure("books.journal_save_failed",failed);
            recoveryRequired = true;
            paused = true;
            flipCalculator.reset();
            bazaarMonitor.stop();
            services.safetyPause("Cannot save book ownership journal; trading blocked.");
            return false;
        }
    }

    private void safetyHalt(String reason) {
        paused=true; // Latch this engine before fallible evidence capture/cleanup.
        services.event("ERROR","books.transaction_blocked",java.util.Map.of("reason",reason,"context",services.diagnosticContext()));
        rememberObservedBooks();
        recoveryRequired |= !exposedBooks.isEmpty() || !bookLists.isEmpty();
        checkpoint();
        paused = true;
        clock.stop();
        flipCalculator.reset();
        bazaarMonitor.stop();
        if (world.inWorld() && screenOpen()) actions.closeMenu();
        services.safetyPause(reason);
    }

    private void serviceRetirement() {
        if(retiringTask==null)retiringTask=taskList.stream().filter(Task::retiring).findFirst().orElse(null);
        if(retiringTask==null)return;
        accounting.retire(retiringTask.getProfitTradeId());
        // Live quotes are required before an exit click, but absence never clears ownership.
        if(services.latestQuotes()==null)return;
        var result=retirement.tick(retiringTask,observedMenu,actions,inventoryMemory,
                settings().firstPage(),settings().secondPage(),world.username(),world.now(),
                new BookRetirement.Receipts(){
                    public void ordersCleared(Task task){fundedHoldings(task);}
                    public void cancellationSent(Task task){capital.funding("books",task.getBook().id(),null);}
                    public boolean checkpoint(){return BazaarFlipper.this.checkpoint();}
                    public void acquired(Task task,int units,double price,String event){
                        accounting.acquire(task.getProfitTradeId(),"books",task.getBook().name(),event,units,price*units);
                    }
                    public void sold(Task task,int units,double proceeds,String event){
                        accounting.sell(task.getProfitTradeId(),"books",task.getBook().name(),event,units,proceeds);
                        fundedHoldings(task);
                        services.event("INFO","books.retirement_sale",java.util.Map.of("trade",task.getProfitTradeId(),"baseUnits",units,"proceeds",proceeds));
                    }
                });
        if(result==BookRetirement.Result.BLOCKED){safetyHalt(retirement.reason());return;}
        if(result==BookRetirement.Result.COLLECT_SALE){
            retiringTask.cancelRetirement();retiringTask.progress(world.now());retiringTask.setBookState(Task.BookState.REPLACE_SELL);
        } else if(result==BookRetirement.Result.COMPLETE){
            Task done=retiringTask;retiredUntil.put(done.getBook().id(),world.now()+30*60_000L);
            taskList.remove(done);exposedBooks.remove(done.getBook().id());
            capital.release("books",done.getBook().id());
            bazaarMonitor.finish(done.getBook(),false);bazaarMonitor.finish(done.getBook(),true);
            listOfTaskToChange.remove(done);
            services.event("INFO","books.retirement_complete",java.util.Map.of("trade",done.getProfitTradeId(),"item",done.getBook().id()));
        } else return;
        retirement.reset();retiringTask=null;watchdog.reset();clock.stop();
        if(screenOpen())actions.closeMenu();
        yieldAfterMs=world.now()+1000;checkpoint();
    }

    private boolean checkHoldingLimits() {
        long now = world.now();
        com.google.gson.JsonObject latest = services.latestQuotes();
        for (Task task : taskList) {
            if(task.retiring() || task.getBookState()==Task.BookState.REPLACE_SELL)continue;
            long since = heldSince.computeIfAbsent(task.getBook().id(), ignored -> now);
            double cost = task.getReservedUnitCost() * task.getBook().getQtyAmount(task.getBook().level());
            if(task.recovered()) {
                Double known=accounting.knownCost(task.getProfitTradeId(),task.getBook().getQtyAmount(task.getBook().level()));
                cost=known==null?0:known;
            }
            double exit = -1;
            if (latest != null) {
                com.google.gson.JsonObject product = latest.getAsJsonObject("products").getAsJsonObject(task.getBook().getLevel(task.getBook().sellLevel()));
                if (product != null) exit = com.goofy.goofyaddons.features.generalflipper.GeneralCalculator.topPrice(product, "buy_summary")
                        * (1 - settings().bazaarTaxPercentage() / 100);
            }
            if (TradingSafety.holdingLimit(since, now, settings().maxBookHoldingSeconds(),
                    cost, exit, settings().maxBookDrawdownPercentage())) {
                if(settings().liquidateStaleBooks()) {task.retire();accounting.retire(task.getProfitTradeId());}
                else {safetyHalt("Book holding age/drawdown limit reached; inspect " + task.getBook().name());return false;}
            }
        }
        heldSince.keySet().retainAll(taskList.stream().map(task -> task.getBook().id()).collect(java.util.stream.Collectors.toSet()));
        return true;
    }

    /**
     * R05: an extra book outlives its task, and `releaseMissing` keeps its allocation alive
     * because the book id is still in `bookLists`. Nothing resized it, so a completed flip
     * left the whole position's cost committed against a single leftover book. Extras
     * accumulate from overfilled claims, so available capital drained and the engine
     * stopped being able to open positions at all — the loop starving rather than failing.
     *
     * <p>The allocation is now reduced to what the remaining extras are actually worth, at
     * the unit cost the position was bought at, or released when none remain. Only ever a
     * reduction, so it cannot fail against the capital limit.
     */
    private void resizeRetainedExtras(Book book, double unitCost) {
        double value = 0;
        for (BookList extra : bookLists) {
            if (!extra.book.equals(book)) continue;
            value += unitCost * book.baseUnits(extra.level);
        }
        if (!Double.isFinite(value) || value <= 0) {
            capital.release("books", book.id());
            debug("[BazaarFlipper] resizeRetainedExtras: no extras left for " + book + ", released its allocation");
            return;
        }
        capital.restore("books", book.id(), value, false);
        services.event("INFO", "books.extra_exposure_resized", java.util.Map.of(
                "item", book.id(), "retainedValue", value));
        debug("[BazaarFlipper] resizeRetainedExtras: " + book + " allocation reduced to " + value + " for retained extras");
    }

    private void fundedHoldings(Task task) {
        var profit=accounting;
        int observed=task.awaitingSale()?task.getBook().getQtyAmount(task.getBook().level())
                :task.bookList.stream().mapToInt(book->task.getBook().baseUnits(book.level)).sum();
        Double cost=profit.openCost(task.getProfitTradeId());
        if(observed>0 && profit.knownCost(task.getProfitTradeId(),observed)==null)cost=null;
        capital.funding("books",task.getBook().id(),cost);
    }

    private void rememberObservedBooks() {
        for (Task task : taskList) if (!task.bookList.isEmpty()) exposedBooks.add(task.getBook().id());
        for (BookList book : bookLists) exposedBooks.add(book.book.id());
        // A claim click or a submitted order may already have reached the server.
        // A selected price alone has not, so confirmationTask is deliberately absent.
        if (claim.pendingBuy() != null) exposedBooks.add(claim.pendingBuy().getBook().id());
        if (claim.pendingSale() != null) exposedBooks.add(claim.pendingSale().getBook().id());
        if (placement.submittedTask() != null) exposedBooks.add(placement.submittedTask().getBook().id());
    }

    private void navigationClick(int slot) {
        if (!observedMenu.loaded(slot) || !observedMenu.cursorEmpty()) return;
        navigationRetry.sent(observedMenu,slot,world.now());
        actions.click(slot,false);
    }

    /**
     * Per-transaction evidence is only meaningful for the tasks that produced it.
     * {@link #stop()} discards every task, so leaving any of this set would let a
     * later run wait on, or account for, a task that no longer exists.
     */
    private void clearTransactionState() {
        navigationRetry.reset();bedrockRecovery.reset();buy.reset();
        reportedUnclaimedSales = false;
        combine.reset();
        claim.reset();
        placement.reset();
        menuRecheck.reset();
        purseObservation.reset();
        purseWaitLogged = false;
        ordersSettle.reset();
        heldSince.clear();
    }

    /**
     * A06: the engine used to locate an existing BUY order by display name and claim it
     * without reading its amount. Adoption now requires a readable total that this route
     * could actually have ordered; anything else is retried as an observation, then
     * retained rather than claimed.
     */
    private boolean bookOrderAdoptable(Task task, int slot, String reason) {
        var lore = observedMenu.slot(slot).loreLines();
        String text = lore == null ? "" : String.join("\n", lore);
        Integer total = com.goofy.goofyaddons.features.generalflipper.OrderLore.total(text);
        if (total == null && recheckBookOrders(task, reason)) return false;
        if (!TradingSafety.adoptableOrderTotal(total, task.getBook().getQtyAmount(task.getBook().level()))) {
            services.event("ERROR","books.order_amount_rejected",java.util.Map.of("trade",task.getProfitTradeId(),
                    "parsedTotal",total==null?"unreadable":total,"fullRequirement",task.getBook().getQtyAmount(task.getBook().level()),"lore",text));
            safetyHalt("Existing book order amount is unreadable or larger than this route could have ordered; position retained.");
            return false;
        }
        return true;
    }

    /**
     * Whether this route may commit coins to a new buy order now. Three limits the general
     * engine has always had and the book engine had none of: how many routes may hold capital
     * at once, how soon an outbid route may re-place, and how many times it may do so at all.
     * A route re-placed immediately and without limit just churns cancel/re-place laps against
     * whoever outbid it, for a fraction of a coin more each time.
     */
    private void skipBookRequirement(Task task,String reason) {
        com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().deny(task.getBook().getLevel(task.getBook().level()),reason);
        services.invalidateMarketReport();
        task.retire();accounting.retire(task.getProfitTradeId());
        actions.closeMenu();navigationRetry.reset();clock.stop();state=State.IDLE;
        checkpoint();actions.message("Skipping "+task.getBook().name()+": "+reason);
        services.event("INFO","books.requirement_skipped",java.util.Map.of("item",task.getBook().getLevel(task.getBook().level()),"reason",reason));
    }

    private boolean mayOpenBookOrder(Task task) {
        long now=world.now();
        if(task.retiring())return false;
        if(com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().excluded().contains(task.getBook().getLevel(task.getBook().level())))return false;
        if(now<bookBuyRetryAt.getOrDefault(task.getProfitTradeId(),0L)) return false;
        double cost=task.getReservedUnitCost()*task.getAmountToOrder();
        double hold=Math.max(capital.cost("books",task.getBook().id()),
                task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()));
        if(cost>0 && capital.refusal(task.getBook().id(),hold,cost,services.purse())!=null)
            return parkBook(task,"insufficient-spendable-capital",now);
        if(task.lastPlacedAt()>0) {
            if(task.reprices()>=settings().maxBookReprices()) return parkBook(task,"reprice-budget-spent",now);
            if(now-task.lastPlacedAt()<settings().bookRepriceCooldownSeconds()*1000L)
                return parkBook(task,"reprice-cooldown",now);
        }
        // A route already holding capital is not counted against itself.
        long active=com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule.activePositions(taskList);
        if(active>=settings().maxActiveBooks() && !capital.occupied(task.getBook().id()))
            return parkBook(task,"max-active-books",now);
        return true;
    }

    private final java.util.Map<String,Long> bookBuyRetryAt=new java.util.HashMap<>();

    private void deferBookPurchase(Task task,String reason) {
        long now=world.now();
        bookBuyRetryAt.put(task.getProfitTradeId(),now+20_000);
        parkBook(task,reason,now);
        placement.abandonConfirmation();
        if(screenOpen())actions.closeMenu();
        activeTask=null;state=State.IDLE;clock.stop();
        // Keep ownership and journal; release the menu so claims/sales in either engine can run.
        yieldAfterMs=now+1000;
        checkpoint();
    }

    /** Reports a held-back route once per minute rather than on every tick. */
    private boolean parkBook(Task task,String reason,long now) {
        Long last=parkReported.get(task.getProfitTradeId());
        if(last==null || now-last>=60_000) {
            parkReported.put(task.getProfitTradeId(),now);
            services.event("INFO","books.order_deferred",java.util.Map.of("engine","books",
                    "trade",task.getProfitTradeId(),"reason",reason,"reprices",task.reprices(),
                    "item",task.getBook().name()));
        }
        return false;
    }

    private final java.util.Map<String,Long> parkReported=new java.util.HashMap<>();

    /**
     * Re-observes the orders menu before trusting that a tracked order is gone.
     *
     * <p>Do not shortcut this after the engine's own claim. 1.3.8 did, reasoning that a claim
     * consumes the order so its absence is expected, and the field caught it within minutes:
     * claiming the filled part of a partially filled buy order leaves the remainder live, and
     * the menu does not necessarily show it on the very next pass. These rechecks are the
     * settle window that lets the menu agree with reality. Skipping them let the engine read
     * "no order" while a 16x order was still open and place a second 14x order for the same
     * book beside it - 26m committed across two orders, which the duplicate-order check then
     * halted on. They cost about seven seconds per claim and they stay.
     */
    /**
     * Says once per run how many coins are sitting in filled sell offers nobody collected.
     *
     * <p>Startup reconciles BUY orders and ignores SELL entries, so a sale that fills after a
     * session ends is stranded: the coins stay in the Bazaar and the ledger never learns the
     * flip completed. Four field sessions left 26,240,759 coins across seven fully filled
     * offers exactly this way. Reporting it is deliberately all this does - collecting coins
     * the engine has no live task for means attributing a sale to a route from memory, and
     * getting that wrong writes a false number into the profit ledger.
     */
    private void reportUnclaimedSales() {
        if (reportedUnclaimedSales) return;
        reportedUnclaimedSales = true;
        var names = new java.util.ArrayList<String>();
        var lores = new java.util.ArrayList<java.util.List<String>>();
        var slots = observedMenu.slots();
        for (int i = 0; i < Math.max(0, slots.size() - 36); i++) {
            var stack = slots.get(i);
            if (stack.empty()) continue;
            var lore = stack.loreLines();
            names.add(stack.hoverName().replaceAll("\u00a7.", ""));
            lores.add(lore == null ? java.util.List.of()
                    : lore.stream().map(line -> line.replaceAll("\u00a7.", "")).toList());
        }
        var found = com.goofy.goofyaddons.features.bookflipper.helper.OrphanSales.scan(names, lores);
        if (found.isEmpty()) return;
        long coins = com.goofy.goofyaddons.features.bookflipper.helper.OrphanSales.total(found);
        services.event("WARN","books.unclaimed_sales",java.util.Map.of("engine","books",
                "offers",found.size(),"coins",coins,
                "detail",found.stream().map(sale -> sale.units()+"x "+sale.item()+"="+sale.coins()).toList()));
        actions.message("BazaarFlipper: " + String.format("%,d", coins) + " coins unclaimed across "
                + found.size() + " filled sell offer(s). Collect them in /managebazaarorders.");
    }

    private boolean reportedUnclaimedSales;


    private boolean recheckBookOrders(Task task,String reason) {
        var decision=menuRecheck.missing(state+":"+task.getProfitTradeId(),world.now());
        if(decision==com.goofy.goofyaddons.features.MenuRecheck.Decision.REOPEN) {
            services.event("WARN","order.observation_recheck",java.util.Map.of("engine","books","reason",reason,
                    "trade",task.getProfitTradeId(),"attempt",menuRecheck.attempts(),"context",services.diagnosticContext()));
            actions.closeMenu();ordersSettle.reset();clock.stop();
        }
        return decision!=com.goofy.goofyaddons.features.MenuRecheck.Decision.EXHAUSTED;
    }

    private boolean purchasePurseReady(double purse) {
        var result = purseObservation.observe(purse, world.now());
        if (result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.READY)
            purseWaitLogged = false;
        else if (!purseWaitLogged) {
            purseWaitLogged = true;
            services.event("WARN", "books.purse_observation_wait", java.util.Map.of(
                    "purse", purse, "reason", com.goofy.goofyaddons.utils.ScoreboardUtils.purseStatus(), "state", state.name()));
        }
        if (result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.TIMED_OUT)
            safetyHalt("Purse remained unreadable for 10 seconds; no purchase submitted. Restore the scoreboard before resuming.");
        return result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.READY;
    }

    private boolean bookPriceAllowed(Task task, double price, boolean sale) {
        com.google.gson.JsonObject latest = services.latestQuotes();
        com.google.gson.JsonObject product = latest == null ? null
                : latest.getAsJsonObject("products").getAsJsonObject(task.getBook().getLevel(task.getBook().sellLevel()));
        double exit = sale ? price : product == null ? -1
                : com.goofy.goofyaddons.features.generalflipper.GeneralCalculator.topPrice(product, "buy_summary");
        var check=BookPricePolicy.check(sale,latest!=null,price,task.getReservedUnitCost(),
                task.getBook().getQtyAmount(task.getBook().level()),exit,
                settings().bazaarTaxPercentage(),settings().minNetProfit());
        var detail=new java.util.LinkedHashMap<String,Object>();
        detail.put("trade",task.getProfitTradeId());detail.put("item",task.getBook().id());detail.put("selling",sale);
        detail.put("price",Double.isFinite(price)?price:"unreadable");detail.put("quotesFresh",latest!=null);
        detail.put("expectedExit",Double.isFinite(exit)?exit:"unreadable");
        detail.put("estimatedInputCost",check.estimatedCost()==null?"unavailable":check.estimatedCost());
        detail.put("estimatedNet",check.estimatedNet()==null?"unavailable":check.estimatedNet());
        detail.put("minimumProfit",settings().minNetProfit());detail.put("taxPercentage",settings().bazaarTaxPercentage());
        detail.put("allowed",check.allowed());detail.put("reason",check.reason());
        services.event(check.allowed()?"INFO":"WARN","books.price_check",detail);
        return check.allowed();
    }

    /** The engine's rules as one book operation sees them; see {@link BookContext}. */
    private final class Session implements BookContext {
        @Override public long now() { return world.now(); }
        @Override public boolean signOpen() { return world.signEditorOpen(); }
        @Override public String username() { return world.username(); }
        @Override public com.goofy.goofyaddons.menu.GameActions actions() { return actions; }
        @Override public BookServices services() { return services; }
        @Override public CapitalManager capital() { return capital; }
        @Override public BookAccounting accounting() { return accounting; }
        @Override public BookSettings settings() { return BazaarFlipper.this.settings(); }

        @Override public State state() { return state; }
        @Override public void state(State next) { state = next; }
        @Override public Clock clock() { return clock; }
        @Override public int delay() { return randomizer(); }
        @Override public void yieldFor(long millis) { yieldAfterMs = world.now() + millis; }

        @Override public com.goofy.goofyaddons.menu.MenuSnapshot menu() { return observedMenu; }
        @Override public InventoryScanner scanner() { return inventoryScanner; }
        @Override public InventoryMemory inventoryMemory() { return inventoryMemory; }
        @Override public boolean screenOpen() { return BazaarFlipper.this.screenOpen(); }
        @Override public String menuTitle() { return BazaarFlipper.this.menuTitle(); }
        @Override public boolean containerNameCheck(String name) { return BazaarFlipper.this.containerNameCheck(name); }
        @Override public void navigationClick(int slot) { BazaarFlipper.this.navigationClick(slot); }

        @Override public List<Task> tasks() { return taskList; }
        @Override public List<BookList> extras() { return bookLists; }
        @Override public Task activeTask() { return activeTask; }
        @Override public void activeTask(Task task) { activeTask = task; }
        @Override public Task taskInState(Task.BookState bookState) { return BazaarFlipper.this.taskInState(bookState); }
        @Override public void handleBookList(Book book, int location, int level, int amount) { BazaarFlipper.this.handleBookList(book, location, level, amount); }
        @Override public void expose(String product) { exposedBooks.add(product); }

        @Override public BazaarMonitor monitor() { return bazaarMonitor; }
        @Override public BookTransfer transfer() { return bookTransfer; }
        @Override public BookCombiner combiner() { return bookCombiner; }
        @Override public BookSellCancellation sellCancellation() { return sellCancellation; }
        @Override public BookClaim claim() { return claim; }
        @Override public BookPlacement placement() { return placement; }

        @Override public boolean checkpoint() { return BazaarFlipper.this.checkpoint(); }
        @Override public void safetyHalt(String reason) { BazaarFlipper.this.safetyHalt(reason); }
        @Override public void initSelfRecovery() { BazaarFlipper.this.initSelfRecovery(); }
        @Override public boolean recheckBookOrders(Task task, String reason) { return BazaarFlipper.this.recheckBookOrders(task, reason); }
        @Override public boolean bookOrderAdoptable(Task task, int slot, String reason) { return BazaarFlipper.this.bookOrderAdoptable(task, slot, reason); }
        @Override public void reportUnclaimedSales() { BazaarFlipper.this.reportUnclaimedSales(); }
        @Override public void fundedHoldings(Task task) { BazaarFlipper.this.fundedHoldings(task); }
        @Override public void resizeRetainedExtras(Book book, double unitCost) { BazaarFlipper.this.resizeRetainedExtras(book, unitCost); }
        @Override public void skipBookRequirement(Task task, String reason) { BazaarFlipper.this.skipBookRequirement(task, reason); }
        @Override public void deferBookPurchase(Task task, String reason) { BazaarFlipper.this.deferBookPurchase(task, reason); }
        @Override public boolean purchasePurseReady(double purse) { return BazaarFlipper.this.purchasePurseReady(purse); }
        @Override public boolean bookPriceAllowed(Task task, double price, boolean sale) { return BazaarFlipper.this.bookPriceAllowed(task, price, sale); }
        @Override public void startLocationReconciliation() { reconciliationPage = 1; reconciliationStarted = world.now(); }
        @Override public void debug(String text) { BazaarFlipper.this.debug(text); }

        @Override public void inventoryFull() { inventoryIsFull = true; }
        @Override public void overflowProtection() { overFlowProt = true; }
        @Override public void startupComplete() { isStartUpCheckCompleted = true; }
        @Override public boolean needToStoreExcessBook() { return needToStoreExcessBook; }
        @Override public void needToStoreExcessBook(boolean value) { needToStoreExcessBook = value; }
    }
}
