package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.bookflipper.helper.TradeBudget;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.features.Feature;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.profit.ProfitTracker;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.features.TransactionWatchdog;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.features.bookflipper.helper.BookCombiner;
import com.goofy.goofyaddons.features.bookflipper.helper.BookOutbidFlow;
import com.goofy.goofyaddons.features.bookflipper.helper.BookRetirement;
import com.goofy.goofyaddons.features.bookflipper.helper.BookActionRetry;
import com.goofy.goofyaddons.features.bookflipper.helper.BookPricePolicy;
import com.goofy.goofyaddons.features.bookflipper.helper.BookStartupOrders;
import com.goofy.goofyaddons.features.bookflipper.helper.BookSaleSettlement;
import com.goofy.goofyaddons.features.bookflipper.helper.BookSellCancellation;
import com.goofy.goofyaddons.features.bookflipper.helper.InventoryMemory;
import com.goofy.goofyaddons.features.bookflipper.helper.BookLocations;
import com.goofy.goofyaddons.features.bookflipper.helper.BookPopulation;
import com.goofy.goofyaddons.features.bookflipper.helper.BookTransfer;
import com.goofy.goofyaddons.features.bookflipper.helper.BookJournal;
import net.fabricmc.loader.api.FabricLoader;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.BookList;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipCalculator;
import com.goofy.goofyaddons.features.bookflipper.helper.FlipItem;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.menu.LiveMenu;
import com.goofy.goofyaddons.menu.LiveActions;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BazaarFlipper implements Feature {
    private enum State {
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

    private State state = State.START;
    private State lastState = null;
    private Clock clock = new Clock();
    private FlipCalculator flipCalculator = new FlipCalculator();
    private ScoreboardUtils scoreboardUtils = new ScoreboardUtils();
    private final InventoryScanner inventoryScanner = new InventoryScanner(() -> this.observedMenu);
    private BazaarMonitor bazaarMonitor = new BazaarMonitor();
    private boolean running = false;
    private boolean paused = false;
    private boolean journalLoaded;
    private final Set<String> exposedBooks = new HashSet<>();
    private boolean recoveryRequired;
    private boolean recoveryFileError;
    private List<BookJournal.Position> recoveryPositions=List.of();
    private com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck recoveryCheck;
    private boolean modePaused;
    private BookJournal bookJournal;
    private final TransactionWatchdog watchdog = new TransactionWatchdog();
    private final Map<String, Long> heldSince = new HashMap<>();
    private Task pendingSaleClaim;
    private boolean saleClaimReceipt;
    private Task pendingBuyClaim;
    private int buyClaimBefore;
    private int buyClaimExpected;
    private Double buyClaimUnitPrice;
    private String buyClaimEvent;
    private Double saleClaimProceeds;
    /** When the sale claim was clicked, so a slow receipt does not read as an unexplained loss. */
    private long saleClaimAt;
    private static final long SALE_RECEIPT_GRACE_MS = 10_000;
    private final com.goofy.goofyaddons.features.MenuSettle ordersSettle=new com.goofy.goofyaddons.features.MenuSettle();
    private long yieldAfterMs;
    private long nextFetchMs;
    private boolean recoveryAwaitQuotes;
    private long recoveryQuoteRetryAt;
    private static final long FETCH_RETRY_MS = 20000;
    private List<FlipItem> flipItemList = new ArrayList<>();
    private boolean needToStoreExcessBook = false;
    private boolean usingSecondPage = false;
    private boolean isStartUpCheckCompleted = false;
    private boolean inventoryIsFull = false;
    private Minecraft minecraft = Minecraft.getInstance();
    private boolean checkedFirstPage = false;

    private final BookTransfer bookTransfer = new BookTransfer();
    private final BookCombiner bookCombiner = new BookCombiner();
    private final InventoryMemory inventoryMemory = new InventoryMemory();
    private final BookLocations bookLocations = new BookLocations();
    private final BookPopulation bookPopulation = new BookPopulation();
    private com.goofy.goofyaddons.menu.MenuSnapshot observedMenu;
    private final BookOutbidFlow outbidFlow = new BookOutbidFlow();
    private final BookRetirement retirement = new BookRetirement();
    private final BookSellCancellation sellCancellation = new BookSellCancellation();
    private final com.goofy.goofyaddons.menu.NavigationRetry navigationRetry = new com.goofy.goofyaddons.menu.NavigationRetry();
    private long signSubmittedAt;
    private int signRestarts;
    private Task retiringTask;
    private final java.util.Map<String,Long> retiredUntil=new java.util.HashMap<>();
    public java.util.Set<String> retirementExclusions(){
        long now=System.currentTimeMillis();retiredUntil.values().removeIf(until->until<=now);
        return java.util.Set.copyOf(retiredUntil.keySet());
    }
    private final BookStartupOrders startupOrders = new BookStartupOrders();
    private int reconciliationPage;
    private long reconciliationStarted;
    private long reconciliationMismatchSince = -1;
    private final com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation purseObservation = new com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation();
    private boolean purseWaitLogged;
    private boolean attemptedToClaim = false;
    private boolean didReceiveItems = false;

    private boolean overFlowProt = false;
    private int tick;

    private final com.goofy.goofyaddons.features.MenuRecheck menuRecheck=new com.goofy.goofyaddons.features.MenuRecheck();
    private Task activeTask = null;
    private final com.goofy.goofyaddons.features.MenuObservationStability confirmationStability=new com.goofy.goofyaddons.features.MenuObservationStability();
    private Task confirmationTask;
    private Task submittedBookTask;
    private Task.BookState submittedNextState;
    private boolean submittedBookSelling;
    private int submittedBookUnits;
    private double submittedBookPrice;
    private long submittedBookAt;
    private double confirmationPrice;
    private boolean confirmationSelling;
    private long confirmationSelectedAt;
    private Set<Task> listOfTaskToChange = new HashSet<>();
    private List<BookList> bookLists = new ArrayList<>();
    private List<Task> taskList = new ArrayList<>();

    public BazaarFlipper() {
        ChatHook.onMessage("Sold",message->{if(running&&!paused&&retiringTask!=null)retirement.receipt(retiringTask,message);});
        ChatHook.onMessage("filled", this::handleFilledMessage);
        ChatHook.onMessage("Claimed", this::handleClaimedMessage);
        ChatHook.onMessage("", message -> {
            if (!running || paused || !BookActionRetry.slowdownMessage(message)) return;
            long now=System.currentTimeMillis();
            navigationRetry.slowdown(now);
            if (bookCombiner.pending()) {
                bookCombiner.slowdown(now);
                Diagnostics.event("WARN","books.anvil_slowdown",java.util.Map.of("phase",bookCombiner.progress()));
            }
            if (bookTransfer.pending()) {
                bookTransfer.slowdown(now);
                Diagnostics.event("WARN","books.transfer_slowdown",java.util.Map.of("state",state.name()));
            }
        });
        bazaarMonitor.hook(this::handleOutbid);
    }

    @Override
    public String name() {
        return "BazaarFlipper";
    }
    public java.util.Map<String,Object> diagnosticState() {
        var state=new java.util.LinkedHashMap<String,Object>();
        state.put("state",this.state.name());state.put("recoveryRequired",recoveryRequired);
        state.put("recovery",java.util.Map.of("pending",recoveryPending(),"fileError",recoveryFileError,"records",recoveryPositions,"progress",recoveryCheck==null?"not checking":recoveryCheck.progress()));state.put("inventoryFull",inventoryIsFull);
        state.put("buyClaimPending",pendingBuyClaim!=null);state.put("buyClaimBefore",buyClaimBefore);state.put("buyClaimExpected",buyClaimExpected);
        state.put("navigationPending",navigationRetry.pending());
        state.put("sellCancelPending",sellCancellation.pending());
        state.put("saleClaimPending",pendingSaleClaim!=null);state.put("saleReceipt",saleClaimReceipt);
        state.put("ordersContainer",ordersSettle.container());state.put("storagePage",usingSecondPage?2:1);
        state.put("combine",bookCombiner.diagnosticState());state.put("transferPending",bookTransfer.pending());
        state.put("slotMemory",inventoryMemory.diagnosticState());state.put("reconciliationPage",reconciliationPage);
        state.put("extraBookStacks",bookLists.size());
        state.put("submittedTrade",submittedBookTask==null?"none":submittedBookTask.getProfitTradeId());
        state.put("submittedUnits",submittedBookUnits);state.put("submittedPrice",submittedBookPrice);
        state.put("submittedSelling",submittedBookSelling);state.put("menuRechecks",menuRecheck.attempts());
        state.put("tasks",taskList.stream().map(task->java.util.Map.of("trade",task.getProfitTradeId(),"item",task.getBook().id(),"state",task.getBookState().name(),"remaining",task.getAmountToOrder(),
                "inputLevel",task.getBook().level(),"outputLevel",task.getBook().sellLevel(),
                "plannedCost",task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()),
                "retiring",task.retiring(),"lastProgressAt",task.progressAt(),"holdings",task.bookList.stream().map(book->java.util.Map.of("level",book.level,"region",book.location,"slot",book.slot)).toList())).toList());
        return state;
    }
    public boolean hasRetainedTasks() { return !taskList.isEmpty() || !bookLists.isEmpty(); }
    public String taskItem() {
        Task task=retiringTask!=null?retiringTask:pendingBuyClaim;
        if (task==null) task=switch (state) {
            case BAZAAR_NAVIGATION -> activeTask;
            case VERIFY_PLACEMENT -> submittedBookTask;
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
        if (pendingBuyClaim != null) return "Verifying book claim";
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
                CapitalManager.INSTANCE.release("books", task.getBook().id());
        }
        recoveryRequired |= !exposedBooks.isEmpty() || !bookLists.isEmpty();
        if (journalLoaded) checkpoint();
        watchdog.reset();
        debug("[BazaarFlipper] stop: resetting state, was " + state + " with " + taskList.size() + " active task(s)");
        recoveryCheck=null;
        bookCombiner.reset();
        bookTransfer.reset();
        inventoryMemory.reset();
        startupOrders.reset();
        bookPopulation.reset();
        reconciliationPage = 0;
        reconciliationMismatchSince = -1;
        observedMenu = null;

        clearTransactionState();retirement.reset();retiringTask=null;sellCancellation.reset();navigationRetry.reset();signSubmittedAt=0;signRestarts=0;
        checkedFirstPage = false;
        isStartUpCheckCompleted = false;
        didReceiveItems = false;
        state = State.START;
        taskList.clear();
        bookLists.clear();
        tick = 0;
        listOfTaskToChange.clear();
        running = false;
        paused = false;
        activeTask = null;
        lastState = null;
        attemptedToClaim = false;
        needToStoreExcessBook = false;
        inventoryIsFull = false;
        overFlowProt = false;
        usingSecondPage = false;
        retirement.reset();retiringTask=null;sellCancellation.reset();navigationRetry.reset();signSubmittedAt=0;signRestarts=0;
        bookBuyRetryAt.clear();
        nextFetchMs = 0;recoveryAwaitQuotes=false;recoveryQuoteRetryAt=0;
        yieldAfterMs = 0;
        clock.stop();
        flipItemList.clear();
        flipCalculator.reset();
        bazaarMonitor.stop();
        bazaarMonitor.reset();
        ChatUtils.clientMessage("BazaarFlipper: Stopped");
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
        if (!running || !paused || minecraft.player == null || minecraft.level == null) return;
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
            if(BazaarApi.latestFresh()!=null)recoveryAwaitQuotes=false;
            else if(System.currentTimeMillis()>=recoveryQuoteRetryAt) {
                recoveryQuoteRetryAt=System.currentTimeMillis()+FETCH_RETRY_MS;bazaarMonitor.refresh();
            }
        }
        handleTaskStateChange();
        if(GoofyConfig.INSTANCE.liquidateStaleBooks)promoteOrphanCleanup();else promoteCompletedExtras();
        promoteStalledOrders();
        bazaarMonitor.onTick();
        if (!checkHoldingLimits() || paused) return;
        if(GoofyConfig.INSTANCE.liquidateStaleBooks && state==State.IDLE && isStartUpCheckCompleted) {
            for(Task task:taskList)if(!task.retiring() && task.stale(System.currentTimeMillis(),GoofyConfig.INSTANCE.bookStaleSeconds*1000L)) {
                task.retire();ProfitTracker.INSTANCE.retire(task.getProfitTradeId());
                Diagnostics.event("INFO","books.retirement_queued",java.util.Map.of("trade",task.getProfitTradeId(),"item",task.getBook().id(),"reason","no-progress"));
            }
        }
        if (state == State.FETCHING && !flipCalculator.isRunning()
                && flipCalculator.getFlipItemsList().isEmpty() && System.currentTimeMillis() >= nextFetchMs) refreshFlips();
        if (isStartUpCheckCompleted) {
            CapitalManager.INSTANCE.releaseMissing("books", java.util.stream.Stream.concat(taskList.stream().map(task -> task.getBook().id()),bookLists.stream().map(book -> book.book.id()))
                    .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Override
    public boolean canYield() {
        if (paused || !running) return true;
        if (retirement.pending() || sellCancellation.pending() || navigationRetry.pending()) return false;
        if (bookCombiner.pending() || bookTransfer.pending() || pendingBuyClaim != null || pendingSaleClaim != null) return false;
        if (reconciliationPage != 0) return false;
        return (state == State.START || state == State.FETCHING || state == State.IDLE)
                && System.currentTimeMillis() >= yieldAfterMs
                && (minecraft.player == null || minecraft.player.containerMenu.getCarried().isEmpty());
    }

    @Override
    public boolean needsMenu() {
        if (!running || paused || recoveryAwaitQuotes) return false;
        if (state == State.FETCHING) return GoofyConfig.INSTANCE.marketAnalysis.automaticSelection
                ? !automaticFlips().isEmpty() : !flipCalculator.isRunning() && !flipCalculator.getFlipItemsList().isEmpty();
        if (state == State.IDLE) return taskList.stream().anyMatch(Task::retiring) || needToStoreExcessBook || System.currentTimeMillis() >= nextFetchMs
                || taskList.stream().anyMatch(task -> com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule
                        .actionable(task.getBookState())
                        && (task.getBookState()!=Task.BookState.SELECTED || mayOpenBookOrder(task)));
        return true;
    }

    @Override
    public void yieldMenu() {
        if (canYield() && minecraft.player != null && minecraft.screen != null) minecraft.player.closeContainer();
    }

    @Override
    public void onTick() {
        if (!running || paused) return;
        if (minecraft.player == null || minecraft.level == null) {
            stop();
            return;
        }
        if(state==State.RECOVERY_CHECK) { checkSavedBooks(); return; }
        if(recoveryAwaitQuotes)return;
        if (System.currentTimeMillis() < yieldAfterMs) return;
        observedMenu = LiveMenu.read();
        int visiblePage = visibleStoragePage(observedMenu);
        inventoryMemory.observe(observedMenu,visiblePage,System.currentTimeMillis());
        var startupMissing = state == State.STARTUP_BAZAAR_CHECK
                ? startupOrders.missingBuys(taskList,observedMenu,System.currentTimeMillis()) : null;
        if (!checkpoint()) return;
        if (sellCancellation.pending()) {
            var result = sellCancellation.observe(observedMenu,System.currentTimeMillis());
            if (result == BookSellCancellation.Result.BLOCKED) {
                safetyHalt("Cancelled book sell offer did not return exactly one output; ownership retained.");
            } else if (result == BookSellCancellation.Result.RETURNED) {
                Diagnostics.event("INFO","books.sell_cancel_verified",java.util.Map.of("trade",sellCancellation.task().getProfitTradeId()));
                sellCancellation.reset();
                bookPopulation.reset(); reconciliationPage=0; reconciliationMismatchSince=-1;
                state=State.IDLE; watchdog.reset(); clock.stop();
                checkpoint(); minecraft.player.closeContainer();
            } else if (minecraft.screen == null) {
                clock.start(randomizer());
                if (clock.shouldFire()) Diagnostics.command("managebazaarorders");
            }
            return;
        }
        var navigationObservation=navigationRetry.observe(observedMenu,minecraft.screen instanceof AbstractSignEditScreen,
                new com.goofy.goofyaddons.menu.LiveActions(),System.currentTimeMillis());
        if(navigationObservation==com.goofy.goofyaddons.menu.NavigationRetry.Result.EXHAUSTED) {
            safetyHalt("Book menu navigation was not acknowledged after three retries; ownership retained.");return;
        }
        if(navigationObservation==com.goofy.goofyaddons.menu.NavigationRetry.Result.RETRIED)
            Diagnostics.event("WARN","books.navigation_retry",java.util.Map.of("state",state.name(),"item",taskItem()));
        if(navigationObservation!=com.goofy.goofyaddons.menu.NavigationRetry.Result.READY)return;
        if(signSubmittedAt>0 && state==State.BAZAAR_NAVIGATION) {
            if(containerNameCheck("How much do you want to pay") || containerNameCheck("Confirm")) {
                signSubmittedAt=0;signRestarts=0;
            } else if(System.currentTimeMillis()-signSubmittedAt>=8000) {
                if(signRestarts>=2) {safetyHalt("Book quantity input did not advance after two navigation restarts; order retained.");return;}
                signRestarts++;signSubmittedAt=0;confirmationTask=null;
                minecraft.player.closeContainer();clock.stop();
                Diagnostics.event("WARN","books.input_navigation_restart",java.util.Map.of("trade",activeTask.getProfitTradeId(),"attempt",signRestarts));
                return;
            } else return;
        }
        String progress = state + ":" + bookCombiner.progress() + ":" + bookTransfer.pending()
                + ":" + checkedFirstPage + ":" + (minecraft.screen == null ? "closed" : minecraft.screen.getTitle().getString());
        if(state==State.IDLE && (retiringTask!=null || taskList.stream().anyMatch(Task::retiring))) {
            serviceRetirement();return;
        }
        if (watchdog.stalled(progress, canYield(), System.currentTimeMillis())) {
            safetyHalt("Book transaction stopped making progress; positions preserved."); return;
        }
        if (pendingBuyClaim != null) {
            int observed = inputBooksInInventory(pendingBuyClaim);
            if (observed < buyClaimBefore + buyClaimExpected) return;
            if (observed != buyClaimBefore + buyClaimExpected) {
                safetyHalt("Book claim quantity differs from the expected inventory increase; ownership retained.");
                return;
            }
            ProfitTracker.INSTANCE.acquire(pendingBuyClaim.getProfitTradeId(), "books", pendingBuyClaim.getBook().name(),
                    buyClaimEvent, buyClaimExpected, buyClaimUnitPrice == null ? null : buyClaimUnitPrice * buyClaimExpected);
            handleItemAssigning(pendingBuyClaim, buyClaimExpected);
            pendingBuyClaim = null;
            didReceiveItems = true;
        }
        if (isStartUpCheckCompleted && !bookCombiner.pending() && !bookTransfer.pending()
                && pendingSaleClaim == null && submittedBookTask == null && !reconcileBookLocations(visiblePage)) return;
        if (containerNameCheck("Confirm") && !TradingSafety.confirmationTitle(minecraft.screen.getTitle().getString(),state==State.SELL || state==State.REPLACE_SELL)) {
            safetyHalt("Unexpected book confirmation type; position retained without another click.");return;
        }
        if (containerNameCheck("Confirm") && BazaarApi.latestFresh() == null) {
            safetyHalt("Book confirmation blocked because Bazaar quotes expired."); return;
        }
        if (minecraft.screen != null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) {
            if (!ordersSettle.settled(minecraft.player.containerMenu.containerId, System.currentTimeMillis())) return;
            List<String> names = minecraft.player.containerMenu.slots.stream()
                    .filter(slot -> slot.container != minecraft.player.getInventory())
                    .map(slot -> com.goofy.goofyaddons.utils.Chat.strip(slot.getItem().getHoverName().getString())).toList();
            for (Task task : taskList) {
                if(com.goofy.goofyaddons.utils.MenuText.titleContains(minecraft.screen.getTitle().getString(),"Co-op Bazaar Orders")) {
                    for(var slot:minecraft.player.containerMenu.slots) {
                        String name=com.goofy.goofyaddons.utils.Chat.strip(slot.getItem().getHoverName().getString());
                        if(!java.util.Set.of("BUY "+task.getBook().getRomanLevel(task.getBook().level()),"SELL "+task.getBook().getRomanLevel(task.getBook().sellLevel())).contains(name)) continue;
                        var lore=slot.getItem().get(net.minecraft.core.component.DataComponents.LORE);
                        String text=lore==null?"":String.join("\n",lore.lines().stream().map(line->line.getString()).toList());
                        var creator=com.goofy.goofyaddons.features.generalflipper.OrderLore.creator(text,minecraft.getUser().getName());
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
                ChatUtils.clientMessage("BazaarFlipper: Started");
                state = State.FETCHING;
                debug("[BazaarFlipper] START: going from start to fetching");
            }

            case FETCHING -> {
                if (!GoofyConfig.INSTANCE.marketAnalysis.automaticSelection && flipCalculator.isRunning()) return;
                flipItemList.clear();
                flipItemList.addAll(GoofyConfig.INSTANCE.marketAnalysis.automaticSelection?automaticFlips():flipCalculator.getFlipItemsList());
                if (flipItemList.isEmpty()) {
                    if (System.currentTimeMillis() >= nextFetchMs) refreshFlips();
                    return;
                }
                debug("[BazaarFlipper] FETCHING: list wasn't empty, printing the list and going into processData");
                flipItemList.forEach(flipItem -> {
                    debug("[BazaarFlipper] FETCHING: " + flipItem.book() + " | Cost: " + flipItem.totalCost() + " | Buy: " + flipItem.instaBuy() + " | Sell: " + flipItem.instaSell());
                });
                processData();
            }

            case STARTUP_CHECK -> {
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command(checkedFirstPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    if (!BookTransfer.pageMatches(
                            minecraft.screen.getTitle().getString(), checkedFirstPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage)) {
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
                        minecraft.player.closeContainer();
                        return;
                    }

                    bookLocations.reconcile(trackedBooks(),inventoryMemory,startupPage);
                    debug("[BazaarFlipper] STARTUP_CHECK: finished both pages, going to STARTUP_BAZAAR_CHECK");
                    minecraft.player.closeContainer();
                    state = State.STARTUP_BAZAAR_CHECK;
                }
            }

            case STARTUP_BAZAAR_CHECK -> {
                Task task = taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
                if (task == null) {
                    debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: no task left in BAZAAR_ORDER_CHECK, startup complete, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    isStartUpCheckCompleted = true;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command("managebazaarorders");
                }

                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString()))) clock.start(randomizer());
                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    reportUnclaimedSales();
                    // Waiting for chat message to appear here
                    if (attemptedToClaim) {
                        if (!didReceiveItems) return;
                        attemptedToClaim = false;
                        didReceiveItems = false;
                    }

                    if (startupMissing == null) return;
                    for (var missing : startupMissing) {
                        BookStartupOrders.scheduleMissingBuy(missing);
                        Diagnostics.event("INFO","books.startup_order_accounted",java.util.Map.of(
                                "item",missing.getBook().id(),"buyOrderPresent",false,
                                "remaining",missing.getAmountToOrder(),"nextState",missing.getBookState().name(),
                                "container",observedMenu.containerId()));
                    }
                    task = taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
                    if (task == null) {
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                        isStartUpCheckCompleted = true;
                        return;
                    }
                    List<Integer> slot = inventoryScanner.findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));
                    if (slot.isEmpty()) return;

                    exposedBooks.add(task.getBook().id());
                    if (!checkpoint()) return;
                    if (!bookOrderAdoptable(task, slot.getFirst(), "startup-order-amount-unreadable")) return;
                    int amount = inventoryScanner.checkOrder(slot.getFirst());
                    if (amount > inventoryScanner.getEmptyInventorySlots()) {
                        debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: not enough empty inventory slots to claim " + amount + " items, going to IDLE");
                        overFlowProt = true;
                        state = State.IDLE;
                        return;
                    }
                    beginBuyClaim(task, amount, slot.getFirst());
                    InventoryUtils.clickSlot(slot.getFirst(), false);

                    if (amount > 0) {
                        debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: claiming " + amount + " of " + task.getBook());
                        // Assigned only after the expected inventory delta is verified.
                    }

                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }
            }

            case IDLE -> {
                if (needToStoreExcessBook) {
                    state = State.STORE;
                    return;
                }

                Task taskToHandle = com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule
                        .next(taskList, isStartUpCheckCompleted, inventoryIsFull, this::mayOpenBookOrder);

                if (taskToHandle == null) {
                    if (System.currentTimeMillis() >= nextFetchMs) state = State.FETCHING;
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

            case BAZAAR_NAVIGATION -> {
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command("bz " + activeTask.getBook().name().replace("Ultimate", ""));
                }

                if (containerNameCheck("Bazaar")) clock.start(randomizer());
                if (containerNameCheck("Bazaar") && inventoryScanner.isMenuLoaded(53) && clock.shouldFire()) {
                    List<Integer> slots = inventoryScanner.findContainer(activeTask.getBook().getRomanLevel(activeTask.getBook().level()));
                    if (slots.isEmpty()) return;
                    navigationClick(slots.getFirst());return;
                }

                if (containerNameCheck(activeTask.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(activeTask.getBook().name()) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if (activeTask.instaBuy) {
                        safetyHalt("Instant book purchases need manual confirmation; order retained."); return;
                    }
                    navigationClick(activeTask.instaBuy ? 10 : 15);return;
                }

                if (containerNameCheck("How many do you want")) clock.start(randomizer());
                if (containerNameCheck("How many do you want") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    navigationClick(16);return;
                }

                if (minecraft.screen instanceof SignEditScreen) clock.start(randomizer());
                if (minecraft.screen instanceof SignEditScreen && clock.shouldFire()) {
                    handleSign();
                }

                if (containerNameCheck("How much do you want to pay")) clock.start(randomizer());
                if (containerNameCheck("How much do you want to pay") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    double purse = scoreboardUtils.getPurse();
                    if (!purchasePurseReady(purse)) return;
                    double unitPrice = inventoryScanner.getUnitPrice(12);
                    double requiredCoins = unitPrice * activeTask.getAmountToOrder();
                    double fullCost = Math.max(unitPrice, activeTask.getReservedUnitCost())
                            * activeTask.getBook().getQtyAmount(activeTask.getBook().level());
                    if (!bookPriceAllowed(activeTask, unitPrice, false)) {
                        safetyHalt("Book buy price no longer meets the minimum net profit."); return;
                    }
                    if (!Double.isFinite(requiredCoins) || requiredCoins <= 0 || unitPrice <= 0) {
                        safetyHalt("Book purchase cost could not be verified.");return;
                    }
                    if (!CapitalManager.INSTANCE.resize("books", activeTask.getBook().id(),
                            Math.max(fullCost, CapitalManager.INSTANCE.cost("books", activeTask.getBook().id())),
                            requiredCoins, purse)) {
                        deferBookPurchase(activeTask,"insufficient-spendable-capital");return;
                    }
                    activeTask.setReservedUnitCost(Math.max(unitPrice, activeTask.getReservedUnitCost()));
                    if (!checkpoint()) return;
                    bazaarMonitor.add(activeTask.getBook(), unitPrice, false);
                    confirmationTask=activeTask;confirmationPrice=unitPrice;confirmationSelling=false;confirmationSelectedAt=System.currentTimeMillis();confirmationStability.reset();
                    navigationClick(12);return;
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if(!verifyBookConfirmation()) return;
                    if (!recordBookSubmission(activeTask,false)) return;
                    InventoryUtils.clickSlot(13, false);
                    confirmationTask=null;
                    yieldAfterMs = System.currentTimeMillis() + 1000;
                    // first we check if the order was an insta buy
                    if (activeTask.instaBuy) {
                        debug("[BazaarFlipper] BAZAAR_NAVIGATION: insta bought " + activeTask.getBook() + ", going to " + (activeTask.bookList.getLast().location != 0 ? "ANVIL" : "COMBINE"));
                        activeTask.setBookState(activeTask.bookList.getLast().location != 0 ? Task.BookState.ANVIL : Task.BookState.COMBINE);
                        return;
                    }

                    debug("[BazaarFlipper] BAZAAR_NAVIGATION: submitted buy order for " + activeTask.getBook() + ", schedule was " + activeTask.actionSchedule);
                    activeTask.recordPlacement(System.currentTimeMillis());
                    switch (activeTask.actionSchedule) {
                        case SELECTED_COMBINE_STORE_BUYORDER -> submittedNextState=Task.BookState.ANVIL;

                        case SELECTED_STORE_BUYORDER -> submittedNextState=Task.BookState.STORE;

                        case NONE -> submittedNextState=Task.BookState.IN_BUY_ORDER;
                    }
                    state = State.VERIFY_PLACEMENT;
                }
            }

            case VERIFY_PLACEMENT -> verifyBookPlacement();

            case OUTBID -> {
                Task task = taskInState(Task.BookState.OUTBID);
                if (task == null) {
                    debug("[BazaarFlipper] OUTBID: no task left in OUTBID, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                outbidFlow.selectTrade(task.getProfitTradeId());
                if (outbidFlow.cancellationTimedOut(System.currentTimeMillis())) {
                    safetyHalt("Outbid book cancellation was not verified; no replacement submitted."); return;
                }
                boolean navigatingOutbid = minecraft.screen == null
                        || !TradingSafety.ordersTitle(minecraft.screen.getTitle().getString()) && !containerNameCheck("Order");
                if (navigatingOutbid) clock.start(randomizer());
                if (navigatingOutbid && clock.shouldFire()) {
                    var navigation = outbidFlow.navigate(task.getBook(), observedMenu);
                    if (navigation != null) {
                        if (navigation.fallback()) Diagnostics.event("WARN", "books.outbid_navigation_fallback",
                                java.util.Map.of("item",task.getBook().id(),"context",Diagnostics.detailedSnapshot()));
                        if (navigation.command() != null) Diagnostics.command(navigation.command());
                        else InventoryUtils.clickSlot(navigation.slot(), false);
                        return;
                    }
                }

                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString()))) clock.start(randomizer());
                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    reportUnclaimedSales();
                    // Waiting for chat message to appear here
                    if (attemptedToClaim) {
                        if (!didReceiveItems) return;
                        attemptedToClaim = false;
                        didReceiveItems = false;
                    }

                    List<Integer> slot = inventoryScanner.findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));

                    if (slot.isEmpty()) {
                        if(!outbidFlow.freshAfterCancellation(minecraft.player.containerMenu.containerId)
                                && recheckBookOrders(task,"missing-buy-order")) return;
                        // first we check if we have all the required books
                        if (task.getAmountToOrder() == 0) {
                            debug("[BazaarFlipper] OUTBID: no BUY order and amount requirement already met, going to ANVIL for " + task.getBook());
                            task.resetReprices();
                            task.setBookState(Task.BookState.ANVIL);
                            return;
                        }

                        // we check if we can combine the books
                        if (task.isCombinable()) {
                            debug("[BazaarFlipper] OUTBID: task is combinable, scheduling SELECTED_COMBINE_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        // if we cannot we check if we have any book in our inventory
                        if (!task.bookList.isEmpty() && task.bookList.getFirst().location == 0) {
                            debug("[BazaarFlipper] OUTBID: book found in inventory, scheduling SELECTED_STORE_BUYORDER for " + task.getBook());
                            task.actionSchedule = Task.ActionSchedule.SELECTED_STORE_BUYORDER;
                            task.setBookState(Task.BookState.SELECTED);
                            return;
                        }
                        debug("[BazaarFlipper] OUTBID: re-placing buy order for " + task.getBook());
                        activeTask = task;
                        task.setBookState(Task.BookState.SELECTED);
                        return;
                    }

                    if (outbidFlow.cancellationSent()) return; // Wait for removal; never re-claim/cancel stale packets.
                    if (!bookOrderAdoptable(task, slot.getFirst(), "outbid-order-amount-unreadable")) return;
                    int amount = inventoryScanner.checkOrder(slot.getFirst());
                    if (amount > inventoryScanner.getEmptyInventorySlots()) {
                        debug("[BazaarFlipper] OUTBID: not enough empty inventory slots to claim " + amount + " items, going to IDLE and marking inventory full");
                        state = State.IDLE;
                        inventoryIsFull = true;
                        return;
                    }
                    beginBuyClaim(task, amount, slot.getFirst());
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                    if (amount > 0) {
                        debug("[BazaarFlipper] OUTBID: claiming " + amount + " of " + task.getBook());
                        // Assigned only after the expected inventory delta is verified.
                    }
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if (outbidFlow.cancellationSent()) return;
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    outbidFlow.sentCancellation(minecraft.player.containerMenu.containerId,System.currentTimeMillis());
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }
            }

            case STORE -> {
                Task task = taskInState(Task.BookState.STORE);
                if (task == null && !needToStoreExcessBook) {
                    debug("[BazaarFlipper] STORE: no task left in STORE and nothing excess to store, going to IDLE");

                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command(usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {
                    BookList bookList = null;
                    if (needToStoreExcessBook) {
                        for (BookList book : bookLists) {
                            if (book.location != 0) continue;
                            bookList = book;
                            break;
                        }
                    } else {
                        for (BookList book : task.bookList) {
                            if (book.location != 0) continue;
                            bookList = book;
                            break;
                        }
                    }

                    if (bookList != null)
                        debug("[BazaarFlipper] STORE: handling level " + bookList.level + " " + bookList.book + " (excess=" + needToStoreExcessBook + ")");

                    if (bookList == null) {
                        if (needToStoreExcessBook) {
                            debug("[BazaarFlipper] STORE: finished storing excess books");
                            needToStoreExcessBook = false;
                            return;
                        }

                        debug("[BazaarFlipper] STORE: finished storing for " + task.getBook() + ", schedule was " + task.actionSchedule);
                        switch (task.actionSchedule) {
                            case SELECTED_STORE_BUYORDER, SELECTED_COMBINE_STORE_BUYORDER -> {
                                task.setBookState(Task.BookState.IN_BUY_ORDER);
                                task.actionSchedule = Task.ActionSchedule.NONE;
                            }
                            case STORE_ANVIL -> {
                                task.setBookState(Task.BookState.ANVIL);
                                task.actionSchedule = Task.ActionSchedule.NONE;
                            }
                        }
                        return;
                    }

                    var result = transferBook(bookList, usingSecondPage ? 2 : 1);
                    if (result == BookTransfer.Result.NO_SPACE) {
                        if (usingSecondPage) {
                            safetyHalt("Book storage is full on both pages; free space before trading continues.");
                            return;
                        }
                        usingSecondPage = true;
                        minecraft.player.closeContainer();
                    }

                }
            }

            case ANVIL -> {
                Task task = taskInState(Task.BookState.ANVIL);
                if (task == null) {
                    debug("[BazaarFlipper] ANVIL: no task left in ANVIL, going to IDLE");

                    usingSecondPage = false;
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command(usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage);
                }

                if (containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack"))
                    clock.start(randomizer());
                if ((containerNameCheck("Ender Chest") || containerNameCheck("Jumbo Backpack") || containerNameCheck("Greater Backpack")) && inventoryScanner.isMenuLoaded(8) && clock.shouldFire()) {

                    // if we have all the amount we pull out everything
                    if (task.getAmountToOrder() == 0) {
                        // here we check the amount we'll pull out and assign book one by one
                        BookList bookToHandle = null;
                        for (int i = 0; i < task.bookList.size(); i++) {
                            BookList bookList = task.bookList.get(i);
                            if (bookList.location == 0) continue;
                            long remaining = task.bookList.stream().filter(entry -> entry.location != 0).count();
                            if (!bookTransfer.pending() && remaining > inventoryScanner.getEmptyInventorySlots()) {
                                safetyHalt("Insufficient inventory space to retrieve tracked books; free space before resuming.");
                                return;
                            }
                            // here we check if we should move to the second page
                            boolean needsSecondPage = bookList.location == 2;
                            if (needsSecondPage != usingSecondPage) {
                                debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
                                usingSecondPage = needsSecondPage;
                                minecraft.player.closeContainer();
                                return;
                            }


                            bookToHandle = bookList;
                            break;
                        }

                        // first we handle if we have no books to pull out
                        if (bookToHandle == null) {
                            debug("[BazaarFlipper] ANVIL: nothing left to pull out for " + task.getBook() + ", schedule was " + task.actionSchedule);
                            switch (task.actionSchedule) {
                                case ANVIL_SELL -> task.setBookState(Task.BookState.SELL);
                                case SELECTED_COMBINE_STORE_BUYORDER, NONE -> task.setBookState(Task.BookState.COMBINE);
                            }
                            usingSecondPage = false;

                            return;
                        }

                        transferBook(bookToHandle, 0);
                        return;
                    }

                    HashMap<Integer, Integer> futureItem = new HashMap<>();
                    BookList bookList = null;

                    // this is loop to handle what item to pick
                    for (int i = 0; i < task.bookList.size(); i++) {
                        BookList bookList1 = task.bookList.get(i);
                        BookList bookList2 = i + 1 >= task.bookList.size() ? null : task.bookList.get(i + 1);

                        if (bookList2 == null || bookList1.level != bookList2.level) {
                            if (futureItem.getOrDefault(bookList1.level, 0) >= 1) {
                                int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 2 ? -2 : 1, Integer::sum);
                                if (newCount == 0) futureItem.merge(bookList1.level + 1, 1, Integer::sum);
                                debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " has a pending partner from a prior merge, projected level " + (bookList1.level + 1) + " count now " + futureItem.getOrDefault(bookList1.level + 1, 0));

                                if (bookList1.location == 0) {
                                    debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " already in inventory, nothing to pull, continuing scan");
                                    continue;
                                }
                                bookList = bookList1;
                                debug("[BazaarFlipper] ANVIL lookahead: picked unpaired level " + bookList1.level + " (location=" + bookList1.location + ") to pull for merge partner");
                                break;
                            }
                            debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " unpaired and no pending partner, skipping");
                            continue;
                        }
                        int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 1 ? -1 : 1, Integer::sum);
                        if (newCount == 0) futureItem.merge(bookList1.level + 2, 1, Integer::sum);
                        debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + ", projected level " + (bookList1.level + 1) + " count now " + newCount + (newCount == 0 ? " (rolled over to level " + (bookList1.level + 2) + ")" : ""));

                        i++;

                        if (bookList1.location != 0) {
                            bookList = bookList1;
                            debug("[BazaarFlipper] ANVIL lookahead: picked first of pair, level " + bookList1.level + " (location=" + bookList1.location + ") to pull");
                            break;
                        }
                        if (bookList2.location != 0) {
                            bookList = bookList2;
                            debug("[BazaarFlipper] ANVIL lookahead: picked second of pair, level " + bookList2.level + " (location=" + bookList2.location + ") to pull");
                            break;
                        }
                        debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + " already fully in inventory, continuing scan");
                    }

                    if (bookList == null) {
                        debug("[BazaarFlipper] ANVIL: no more books to pull for merging on " + task.getBook() + ", schedule was " + task.actionSchedule);
                        switch (task.actionSchedule) {
                            case ANVIL_SELL -> task.setBookState(Task.BookState.SELL);
                            case SELECTED_COMBINE_STORE_BUYORDER, NONE -> task.setBookState(Task.BookState.COMBINE);
                        }

                        usingSecondPage = false;
                        return;
                    }

                    debug("[BazaarFlipper] ANVIL: merge lookahead picked level " + bookList.level + " (location=" + bookList.location + ") to pull, projected future counts=" + futureItem);

                    // here we check if we should move to the second page
                    boolean needsSecondPage = bookList.location == 2;
                    if (needsSecondPage != usingSecondPage) {
                        debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
                        usingSecondPage = needsSecondPage;
                        minecraft.player.closeContainer();
                        return;
                    }

                    // Books already in the inventory hold the slots they need. Counting them
                    // again sent the task to STORE and straight back here, cycling.
                    long toRetrieve = task.bookList.stream().filter(entry -> entry.location != 0).count();
                    if (!bookTransfer.pending() && toRetrieve > inventoryScanner.getEmptyInventorySlots()) {
                        safetyHalt("Insufficient inventory space to retrieve tracked books; free space before resuming.");
                        return;
                    }

                    transferBook(bookList, 0);

                }
            }

            case COMBINE -> {
                Task task = taskInState(Task.BookState.COMBINE);
                if (task == null) {
                    bookCombiner.reset();
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }
                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) Diagnostics.command("anvil");
                if (!containerNameCheck("Anvil")) return;
                clock.start(randomizer());
                if (!inventoryScanner.isMenuLoaded(8) || !clock.shouldFire()) return;
                int previousRetries = bookCombiner.actionRetries();
                var result = bookCombiner.tick(task, observedMenu,
                        new LiveActions(), System.currentTimeMillis());
                if (bookCombiner.actionRetries() > previousRetries) Diagnostics.event("WARN","books.anvil_action_retried",
                        java.util.Map.of("phase",bookCombiner.progress(),"attempt",bookCombiner.actionRetries(),
                                "item",task.getBook().id(),"context",Diagnostics.detailedSnapshot()));
                switch (result) {
                    case MERGED -> {
                        combineInputsAbsent = 0;
                        debug("[BazaarFlipper] COMBINE: verified input consumption and output arrival for " + task.getBook());
                    }
                    case NO_PAIR -> {
                        combineInputsAbsent = 0;
                        try {
                            task.finishCombining();
                        } catch (IllegalStateException invalidModel) {
                            safetyHalt(invalidModel.getMessage() + "; ownership retained.");
                            return;
                        }
                        minecraft.player.closeContainer();
                        state = State.IDLE;
                    }
                    case INPUTS_MISSING -> {
                        var decision = com.goofy.goofyaddons.features.bookflipper.helper.CombineRecovery
                                .decide(++combineInputsAbsent, COMBINE_ABSENT_TICKS,
                                        true);
                        if (decision != com.goofy.goofyaddons.features.bookflipper.helper.CombineRecovery.Action.WAIT) {
                            combineInputsAbsent = 0;
                            safetyHalt("Book model and inventory disagree before combining; inspect books and live orders. No buy order cancelled or replaced.");
                        }
                    }
                    case BLOCKED -> {
                        if (bookCombiner.canReconcilePhysicalTimeout(observedMenu)) {
                            Diagnostics.event("WARN","books.combine_reconciliation",java.util.Map.of(
                                    "combine",bookCombiner.diagnosticState(),"memory",inventoryMemory.diagnosticState()));
                            bookCombiner.reset();
                            reconciliationPage = 1; reconciliationStarted = System.currentTimeMillis();
                            state = State.IDLE; minecraft.player.closeContainer(); clock.stop();
                        } else safetyHalt(bookCombiner.failure());
                    }
                    case WAITING -> { }
                }
            }

            case SELL -> {
                Task task = taskInState(Task.BookState.SELL);
                if (task == null) {
                    debug("[BazaarFlipper] SELL: no task left in SELL, going to FETCHING");
                    minecraft.player.closeContainer();
                    state = State.FETCHING;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command("managebazaarorders");
                }

                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString()))) clock.start(randomizer());
                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = new ArrayList<>();

                    slot.addAll(inventoryScanner.findContainer("SELL " + task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (!slot.isEmpty() && !task.instaSell) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    slot.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(task.getBook().sellLevel())));
                    if(slot.isEmpty() && recheckBookOrders(task,"missing-sell-order")) return;

                    if (!slot.isEmpty()) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    } else {
                        initSelfRecovery();
                        return;
                    }
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    if (!checkpoint()) return;
                    sellCancellation.start(task,observedMenu.containerId(),System.currentTimeMillis());
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }

                if (containerNameCheck(task.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(task.getBook().name()) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if (task.instaSell) {
                        safetyHalt("Instant book sales need manual confirmation; tracked book retained.");
                        return;
                    }
                    navigationClick(16);return;
                }

                if (containerNameCheck("At what price are you selling")) clock.start(randomizer());
                if (containerNameCheck("At what price are you selling") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    selectSalePrice(task,true);
                    return;
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if(!verifyBookConfirmation()) return;
                    if (!recordBookSubmission(task,true)) return;
                    InventoryUtils.clickSlot(13, false);
                    confirmationTask=null;
                    yieldAfterMs = System.currentTimeMillis() + 1000;
                    debug("[BazaarFlipper] SELL: submitted sell order for " + task.getBook());
                    submittedNextState=Task.BookState.SELL_ORDER;
                    state=State.VERIFY_PLACEMENT;

                    debug("[BazaarFlipper] SELL: TaskSize:" + taskList.size());
                }
            }

            case VERIFY_ORDER -> {
                Task task = taskInState(Task.BookState.VERIFY_ORDER);
                if (task == null) {
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command("managebazaarorders");
                }

                if (minecraft.screen != null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) clock.start(randomizer());
                if (minecraft.screen != null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())
                        && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    // Read-only by construction: this state never clicks a slot. Its whole
                    // job is to decide whether a parked wait should continue or has work.
                    boolean sale = task.awaitingSale();
                    String item = task.getBook().getRomanLevel(sale ? task.getBook().sellLevel() : task.getBook().level());
                    List<Integer> slot = inventoryScanner.findContainer((sale ? "SELL " : "BUY ") + item);
                    if (slot.isEmpty()) {
                        if (recheckBookOrders(task, "recheck-order-absent")) return;
                        safetyHalt("Tracked book order is no longer listed; reconcile before continuing.");
                        return;
                    }
                    var lore = minecraft.player.containerMenu.slots.get(slot.getFirst()).getItem()
                            .get(net.minecraft.core.component.DataComponents.LORE);
                    String text = lore == null ? "" : String.join("\n", lore.lines().stream().map(line -> line.getString()).toList());
                    var fill = com.goofy.goofyaddons.features.generalflipper.OrderLore.fill(text);
                    if (fill == null) {
                        if (recheckBookOrders(task, "recheck-fill-unreadable")) return;
                        safetyHalt("Tracked book order progress is unreadable; reconcile before continuing.");
                        return;
                    }
                    Diagnostics.event("INFO", "books.order_rechecked", java.util.Map.of("trade", task.getProfitTradeId(),
                            "item", item, "selling", sale, "filled", fill.filled(), "total", fill.total()));
                    task.markOrderObserved(System.currentTimeMillis());
                    if (fill.filled() > 0) {
                        debug("[BazaarFlipper] VERIFY_ORDER: " + item + " shows " + fill.filled() + "/" + fill.total() + ", routing to collect");
                        task.setBookState(sale ? Task.BookState.REPLACE_SELL : Task.BookState.OUTBID);
                    } else {
                        debug("[BazaarFlipper] VERIFY_ORDER: " + item + " still unfilled, continuing to wait");
                        task.setBookState(sale ? Task.BookState.SELL_ORDER : Task.BookState.IN_BUY_ORDER);
                    }
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                }
            }

            case REPLACE_SELL -> {
                Task task = taskInState(Task.BookState.REPLACE_SELL);
                if (task == null) {
                    debug("[BazaarFlipper] REPLACE_SELL: no task left in REPLACE_SELL, going to IDLE");
                    minecraft.player.closeContainer();
                    state = State.IDLE;
                    return;
                }

                if (minecraft.screen == null) clock.start(randomizer());
                if (minecraft.screen == null && clock.shouldFire()) {
                    Diagnostics.command("managebazaarorders");
                }

                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString()))) clock.start(randomizer());
                if ((minecraft.screen!=null && TradingSafety.ordersTitle(minecraft.screen.getTitle().getString())) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = new ArrayList<>();

                    slot.addAll(inventoryScanner.findContainer("SELL " + task.getBook().getRomanLevel(task.getBook().sellLevel())));

                    if (pendingSaleClaim == task) {
                        var settlement=BookSaleSettlement.check(!slot.isEmpty(),
                                !inventoryScanner.findLoreInv(task.getBook().getRomanLevel(task.getBook().sellLevel())).isEmpty(),
                                saleClaimReceipt,System.currentTimeMillis()-saleClaimAt,SALE_RECEIPT_GRACE_MS);
                        if (settlement==BookSaleSettlement.Result.COMPLETE) {
                            completeBookSale(task);
                            state=State.IDLE;
                            minecraft.player.closeContainer();
                        } else if (settlement==BookSaleSettlement.Result.UNCONFIRMED) {
                            Diagnostics.event("ERROR","books.sale_claim_unconfirmed",java.util.Map.of(
                                    "trade",task.getProfitTradeId(),"orderPresent",!slot.isEmpty(),
                                    "receiptSeen",saleClaimReceipt,"context",Diagnostics.detailedSnapshot()));
                            safetyHalt("Book sale claim did not settle; ownership retained without repeating the claim.");
                        }
                        return;
                    }

                    if (!slot.isEmpty()) {
                        net.minecraft.world.item.component.ItemLore lore = minecraft.player.containerMenu.slots.get(slot.getFirst())
                                .getItem().get(net.minecraft.core.component.DataComponents.LORE);
                        com.goofy.goofyaddons.features.generalflipper.OrderLore.Fill fill = lore == null ? null
                                : com.goofy.goofyaddons.features.generalflipper.OrderLore.fill(String.join("\n", lore.lines().stream().map(line -> line.getString()).toList()));
                        if (fill != null && fill.filled() == fill.total() && fill.total() == 1) {
                            pendingSaleClaim = task;
                            saleClaimReceipt = false;
                            saleClaimProceeds = null;
                            saleClaimAt = System.currentTimeMillis();
                        }
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    slot.addAll(inventoryScanner.findLoreInv(task.getBook().getRomanLevel(task.getBook().sellLevel())));
                    if(slot.isEmpty() && recheckBookOrders(task,"missing-sell-order")) return;

                    if (!slot.isEmpty()) {
                        InventoryUtils.clickSlot(slot.getFirst(), false);
                        return;
                    }

                    Diagnostics.event("ERROR","books.sale_claim_unconfirmed",java.util.Map.of(
                            "trade",task.getProfitTradeId(),"claimPending",false,"receiptSeen",false,
                            "context",Diagnostics.detailedSnapshot()));
                    safetyHalt("Tracked book sell order/inventory missing without a matching claim; position retained.");
                    return;
                }

                if (containerNameCheck("Order")) clock.start(randomizer());
                if (containerNameCheck("Order") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    List<Integer> slot = inventoryScanner.findContainer("Cancel Order");
                    if (slot.isEmpty()) return;
                    if (!checkpoint()) return;
                    sellCancellation.start(task,observedMenu.containerId(),System.currentTimeMillis());
                    InventoryUtils.clickSlot(slot.getFirst(), false);
                }

                if (containerNameCheck(task.getBook().name())) clock.start(randomizer());
                if (containerNameCheck(task.getBook().name()) && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    navigationClick(16);return;
                }

                if (containerNameCheck("At what price are you selling")) clock.start(randomizer());
                if (containerNameCheck("At what price are you selling") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    selectSalePrice(task,false);
                    return;
                }

                if (containerNameCheck("Confirm")) clock.start(randomizer());
                if (containerNameCheck("Confirm") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) {
                    if(!verifyBookConfirmation()) return;
                    if (!recordBookSubmission(task,true)) return;
                    InventoryUtils.clickSlot(13, false);
                    confirmationTask=null;
                    yieldAfterMs = System.currentTimeMillis() + 1000;
                    debug("[BazaarFlipper] REPLACE_SELL: submitted replacement sell order for " + task.getBook());
                    submittedNextState=Task.BookState.SELL_ORDER;
                    state=State.VERIFY_PLACEMENT;
                    debug("[BazaarFlipper] REPLACE_SELL: TaskSize:" + taskList.size());
                }
            }
        }

        } finally { checkpoint(); }
    }

    private void completeBookSale(Task task) {
        ProfitTracker.INSTANCE.sell(task.getProfitTradeId(),"books",task.getBook().name(),
                task.getProfitTradeId()+":sale",task.getBook().getQtyAmount(task.getBook().level()),saleClaimProceeds);
        taskList.remove(task);
        resizeRetainedExtras(task.getBook(),task.getReservedUnitCost());
        pendingSaleClaim=null;saleClaimReceipt=false;saleClaimProceeds=null;saleClaimAt=0;
    }

    private void promoteOrphanCleanup() {
        if(!isStartUpCheckCompleted || state!=State.IDLE || submittedBookTask!=null || pendingBuyClaim!=null || pendingSaleClaim!=null)return;
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
            double hold=Math.max(1,CapitalManager.INSTANCE.cost("books",original.id()));
            task.setReservedUnitCost(hold/book.getQtyAmount(from));
            CapitalManager.INSTANCE.restore("books",book.id(),hold,false);
            taskList.add(task);bookLists.removeAll(extras);exposedBooks.add(book.id());
            ProfitTracker.INSTANCE.acquire(task.getProfitTradeId(),"books",book.name(),task.getProfitTradeId()+":cleanup-holdings",units,null);
            ProfitTracker.INSTANCE.retire(task.getProfitTradeId());
            Diagnostics.event("INFO","books.retirement_queued",java.util.Map.of("trade",task.getProfitTradeId(),"item",book.id(),"reason","unassigned-leftovers"));
        }
        needToStoreExcessBook=bookLists.stream().anyMatch(b->b.location==0);
    }

    private void promoteCompletedExtras() {
        if(!isStartUpCheckCompleted || submittedBookTask!=null || pendingBuyClaim!=null || pendingSaleClaim!=null)return;
        for(Book book:bookLists.stream().map(b->b.book).distinct().toList()) {
            if(taskList.stream().anyMatch(t->t.getBook().id().equals(book.id())))continue;
            int units=bookLists.stream().filter(b->b.book.equals(book)).mapToInt(b->book.baseUnits(b.level)).sum();
            double unitCost=CapitalManager.INSTANCE.cost("books",book.id())/Math.max(1,units);
            Task task=com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryPlan.completedExtraCycle(book,bookLists,unitCost);
            if(task==null)continue;
            for(var holding:task.bookList) {
                var original=bookLists.stream().filter(b->b.book.equals(book) && b.level==holding.level
                        && b.location==holding.location && b.slot==holding.slot).findFirst().orElseThrow();
                bookLists.remove(original);
            }
            taskList.add(task);exposedBooks.add(book.id());
            ProfitTracker.INSTANCE.recoverHoldings(task.getProfitTradeId(),"books",book.name(),book.getQtyAmount(book.level()));
            Diagnostics.event("INFO","books.extra_cycle_resumed",java.util.Map.of("item",book.id(),"trade",task.getProfitTradeId()));
        }
    }

    private BookTransfer.Result transferBook(BookList book, int target) {
        int previousRetries=bookTransfer.actionRetries();
        var result = bookTransfer.tick(book, target,
                usingSecondPage ? GoofyConfig.INSTANCE.secondPage : GoofyConfig.INSTANCE.firstPage,
                observedMenu, new LiveActions(), System.currentTimeMillis(),inventoryMemory);
        if (bookTransfer.actionRetries()>previousRetries) Diagnostics.event("WARN","books.transfer_action_retried",
                java.util.Map.of("item",book.book.id(),"level",book.level,"destination",target,
                        "attempt",bookTransfer.actionRetries(),"memory",inventoryMemory.diagnosticState()));
        if (result == BookTransfer.Result.BLOCKED) {
            String reason = bookTransfer.failure();
            if (inventoryMemory.fresh(0) && (reason.startsWith("Book transfer timed out")
                    || reason.startsWith("Unexpected book quantities"))) {
                // Inventory movement has no outstanding monetary submission. Reinspect actual locations
                // instead of keeping a missing physical book in a permanently pending transfer.
                Diagnostics.event("WARN","books.transfer_reconciliation",java.util.Map.of(
                        "reason",reason,"memory",inventoryMemory.diagnosticState()));
                bookTransfer.reset(); inventoryMemory.finishMove();
                reconciliationPage = 1; reconciliationStarted = System.currentTimeMillis();
                state = State.IDLE;
                if (minecraft.screen != null) minecraft.player.closeContainer();
                clock.stop();
                return BookTransfer.Result.WAITING;
            }
            safetyHalt(reason);
        }
        if (result == BookTransfer.Result.NO_SPACE && target == 0)
            safetyHalt("Insufficient inventory space for book retrieval; location retained.");
        return result;
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
        if (BookTransfer.pageMatches(menu.title(),GoofyConfig.INSTANCE.firstPage)) return 1;
        if (BookTransfer.pageMatches(menu.title(),GoofyConfig.INSTANCE.secondPage)) return 2;
        return 0;
    }

    /** Inspect remembered storage when inventory disagrees, before allowing another book action. */
    private boolean reconcileBookLocations(int visiblePage) {
        if (!inventoryMemory.fresh(0)) return false;
        long now = System.currentTimeMillis();
        if (reconciliationPage != 0) {
            if (now - reconciliationStarted >= 30_000) {
                safetyHalt("Could not observe storage to reconcile book locations; slot history retained."); return false;
            }
            if (visiblePage != reconciliationPage) {
                if (minecraft.screen != null) { minecraft.player.closeContainer(); clock.stop(); }
                else {
                    clock.start(randomizer());
                    if (clock.shouldFire()) Diagnostics.command(reconciliationPage == 1 ? GoofyConfig.INSTANCE.firstPage : GoofyConfig.INSTANCE.secondPage);
                }
                return false;
            }
            if (!inventoryMemory.fresh(visiblePage)) return false;
        }
        var result = bookLocations.reconcile(trackedBooks(),inventoryMemory,visiblePage);
        var population = bookPopulation.inspect(trackedBooks(),taskList.stream().map(Task::getBook).toList(),inventoryMemory);
        if (result.corrected() > 0) {
            for (var task : taskList) task.bookList.sort(Comparator.comparingInt(book -> book.location));
            Diagnostics.event("WARN","books.locations_reconciled",java.util.Map.of(
                    "corrected",result.corrected(),"page",visiblePage,"memory",inventoryMemory.diagnosticState()));
            if (!checkpoint()) return false;
        }
        int inspectPage = result.inspectPage() > 0 ? result.inspectPage() : population.inspectPage();
        if (inspectPage > 0) {
            if (reconciliationPage == 0) reconciliationStarted = now;
            reconciliationPage = inspectPage;
            if (minecraft.screen != null) minecraft.player.closeContainer();
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
                Diagnostics.event("WARN","books.locations_settling",java.util.Map.of("memory",inventoryMemory.diagnosticState()));
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
            minecraft.player.closeContainer(); clock.stop();
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
                ProfitTracker.INSTANCE.writeOff(owner.getProfitTradeId(),"books",owner.getBook().name(),
                        java.util.UUID.randomUUID().toString(),units);
                changed.add(owner);
            } else bookLists.remove(missing);
            Diagnostics.event("WARN","books.book_written_off",java.util.Map.of("item",missing.book.id(),
                    "level",missing.level,"lastRegion",missing.location,"lastSlot",missing.slot,"baseUnits",units));
        }
        for (var found : difference.found()) {
            var owner = taskList.stream().filter(task -> task.getBook().equals(found.book)
                    && task.getAmountToOrder() >= found.book.baseUnits(found.level)).findFirst().orElse(null);
            if (owner != null && owner.acceptFound(found)) {
                ProfitTracker.INSTANCE.acquire(owner.getProfitTradeId(),"books",owner.getBook().name(),
                        java.util.UUID.randomUUID().toString(),found.book.baseUnits(found.level),0.0);
                changed.add(owner);
            } else {
                bookLists.add(found);
                if (found.location == 0) needToStoreExcessBook = true;
            }
            Diagnostics.event("INFO","books.book_found",java.util.Map.of("item",found.book.id(),
                    "level",found.level,"region",found.location,"slot",found.slot));
        }
        // Existing orders must be observed before any replacement or amended route is submitted.
        for (var task : changed) { task.actionSchedule = Task.ActionSchedule.NONE; task.setBookState(Task.BookState.BAZAAR_ORDER_CHECK); }
        bookPopulation.reset();
        reconciliationPage = 0; reconciliationMismatchSince = -1;
        if (!checkpoint()) return;
        if (minecraft.screen != null) minecraft.player.closeContainer();
        state = State.IDLE; clock.stop();
    }

    private boolean containerNameCheck(String name) {
        if (minecraft.screen == null) return false;
        return com.goofy.goofyaddons.utils.MenuText.titleContains(minecraft.screen.getTitle().getString(), name);
    }

    private void lastStateCheck() {
        if (state == lastState) return;
        outbidFlow.reset();
        startupOrders.reset();
        if (minecraft.screen != null) minecraft.player.closeContainer();
        tick = 0;
        attemptedToClaim = false;
        Diagnostics.event("INFO","books.transition",java.util.Map.of("from",lastState==null?"none":lastState.name(),"to",state.name()));
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
        if(GoofyConfig.INSTANCE.marketAnalysis.automaticSelection) {
            flipItemList.clear();flipItemList.addAll(automaticFlips());
        }
        double purse = scoreboardUtils.getPurse();
        // Money Check
        debug("[BazaarFlipper] PROCESSDATA: purse=" + purse + ", flipItemList size=" + flipItemList.size());
        if (!Double.isFinite(purse) || purse < 0) {
            state = State.IDLE;
            return;
        }
        if (CapitalManager.INSTANCE.purchaseSettling()) { state = State.IDLE; return; }
        var eligible=flipItemList.stream().filter(f->!retirementExclusions().contains(f.book().id())).toList();
        for (FlipItem flipItem : TradeBudget.select(eligible, taskList,
                CapitalManager.INSTANCE.available(purse), CapitalManager.INSTANCE.occupiedProducts())) {
            if (taskList.size() >= GoofyConfig.INSTANCE.maxActiveBooks) break;
            if (!CapitalManager.INSTANCE.owns("books", flipItem.book().id())
                    && !CapitalManager.INSTANCE.reserve("books", flipItem.book().id(), flipItem.totalCost(), purse)) continue;
            debug("[BazaarFlipper] PROCESSDATA: creating task for " + flipItem.book().getRomanLevel(flipItem.book().level()) + " (cost=" + flipItem.totalCost() + ", instaBuy=" + flipItem.instaBuy() + ", instaSell=" + flipItem.instaSell() + ")");
            Task task = new Task(flipItem.book(), flipItem.instaBuy(), flipItem.instaSell());
            task.setReservedUnitCost(flipItem.totalCost() / flipItem.book().getQtyAmount(flipItem.book().level()));
            taskList.add(task);

            Iterator<BookList> iterator = bookLists.iterator();

            while (iterator.hasNext()) {
                BookList bookList = iterator.next();

                if (!bookList.book.equals(flipItem.book())) continue;

                int attempt = task.assignBook(bookList.book, bookList.level, bookList.location, 1);

                if (attempt != -1) {
                    if (bookList.found) ProfitTracker.INSTANCE.acquire(task.getProfitTradeId(),"books",task.getBook().name(),
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
        nextFetchMs = System.currentTimeMillis() + FETCH_RETRY_MS;
        if(GoofyConfig.INSTANCE.marketAnalysis.automaticSelection) {flipCalculator.reset();return;}
        flipCalculator.Refresh();
    }
    private List<FlipItem> automaticFlips() {
        var market=BazaarApi.latestFresh();
        return market==null?List.of():com.goofy.goofyaddons.features.marketanalysis.AutomaticSelection.books(
                com.goofy.goofyaddons.features.FeatureManager.INSTANCE.automaticReport(),System.currentTimeMillis(),
                market.getAsJsonObject("products"),GoofyConfig.INSTANCE.bazaarTaxPercentage,GoofyConfig.INSTANCE.minNetProfit);
    }

    private int randomizer() {
        return com.goofy.goofyaddons.utils.ActionDelay.next();
    }

    /**
     * Gives a long-untouched order a read-only look. Without this, a task whose fill
     * notice was missed waited forever: the scheduler reported no work, its capital
     * stayed reserved, and nothing surfaced a problem.
     */
    private void promoteStalledOrders() {
        Task stalled = com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule.staleOrder(
                taskList, System.currentTimeMillis(), GoofyConfig.INSTANCE.bookOrderRecheckSeconds * 1000L);
        if (stalled == null) return;
        Diagnostics.event("INFO", "books.order_recheck_due", java.util.Map.of("trade", stalled.getProfitTradeId(),
                "waitedMs", System.currentTimeMillis() - stalled.orderWaitSince(), "selling", stalled.awaitingSale()));
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

    private void handleClaimedMessage(String string) {
        if (!running || paused) return;
        if (pendingSaleClaim != null && TradingSafety.claimReceipt(string,
                pendingSaleClaim.getBook().getRomanLevel(pendingSaleClaim.getBook().sellLevel()), 1)) {
            saleClaimReceipt = true;
            saleClaimProceeds = TradeReceipts.saleProceeds(string,
                    pendingSaleClaim.getBook().getRomanLevel(pendingSaleClaim.getBook().sellLevel()), 1);
            return;
        }
        // Buy claims are acknowledged by an observed inventory increase, never generic chat.
    }

    private int inputBooksInInventory(Task task) {
        return (int) inventoryScanner.matchingBookInInventory(task.getBook()).stream()
                .filter(slot -> inventoryScanner.getLevel(slot) == task.getBook().level()).count();
    }

    private void beginBuyClaim(Task task, int amount, int slot) {
        if (amount <= 0) return;
        pendingBuyClaim = task;
        buyClaimBefore = inputBooksInInventory(task);
        buyClaimExpected = amount;
        var lore = minecraft.player.containerMenu.slots.get(slot).getItem().get(net.minecraft.core.component.DataComponents.LORE);
        buyClaimUnitPrice = lore == null ? null : TradeReceipts.unitPrice(String.join("\n", lore.lines().stream().map(line -> line.getString()).toList()));
        buyClaimEvent = java.util.UUID.randomUUID().toString();
        attemptedToClaim = true;
        didReceiveItems = false;
    }

    private void handleItemAssigning(Task task, int amount) {

        if (amount > task.getAmountToOrder()) {
            int requiredAmount = task.getAmountToOrder();
            int remainder = amount - requiredAmount;
            debug("[BazaarFlipper] handleItemAssigning: received " + amount + " of " + task.getBook() + " vs amountToOrder=" + task.getAmountToOrder() + " -> assignBook(newAmount=" + requiredAmount + "), handleBookList(amount=" + remainder + ")");
            task.assignBook(task.getBook(), task.getBook().level(), 0, requiredAmount);
            handleBookList(task.getBook(), 0, task.getBook().level(), remainder);
            return;
        }
        task.assignBook(task.getBook(), task.getBook().level(), 0, amount);
    }

    private void handleSign() {
        String amountToOrder = String.valueOf(activeTask.getAmountToOrder());
        if (!(minecraft.screen instanceof AbstractSignEditScreen signScreen)) return;
        try {
            if (!com.goofy.goofyaddons.utils.SignEntry.writeFirstLine(signScreen, amountToOrder)) {
                safetyHalt("Could not write the book order amount onto the sign; order retained."); return;
            }
            debug("[BazaarFlipper] handleSign: wrote \"" + amountToOrder + "\" onto sign for " + activeTask.getBook());
            minecraft.setScreen(null);
            signSubmittedAt=System.currentTimeMillis();
        } catch (Exception failure) {
            // Leaving the sign open with an unknown amount must never reach a confirmation.
            Diagnostics.failure("books.sign_write_failed",failure);
            safetyHalt("Writing the book order amount onto the sign failed; order retained.");
        }
    }

    private void handleFilledMessage(String string) {
        if (!running || !string.startsWith("[Bazaar] Your ")
                || !(string.contains("Buy Order") || string.contains("Sell Offer"))) return;
        String stripped;
        boolean isSellOffer = false;

        if (string.contains("Buy Order")) {
            stripped = string.replace("[Bazaar] Your Buy Order for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            ChatUtils.clientMessage("BazaarFlipper: Buy order complete for " + stripped);
        } else {
            stripped = string.replace("[Bazaar] Your Sell Offer for ", "").replace(" was filled!", "");
            stripped = stripped.substring(stripped.indexOf(' ') + 1);
            ChatUtils.clientMessage("BazaarFlipper: Sell offer complete for " + stripped);
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
        if (!attemptedToClaim) {
            tick = 0;
            return;
        }
        tick++;
        if (tick != 1200) return;
        initSelfRecovery();
    }

    private void debug(String string) {
        Diagnostics.event("INFO","books.progress",java.util.Map.of("detail",string));
    }

    private void initSelfRecovery() {
        safetyHalt("Book transaction could not be verified; blind restart blocked.");
    }

    public boolean restoreBudget() {
        if (!journalLoaded || recoveryRequired && !running) {
            journalLoaded=true;
            try {
                recoveryPositions=journal().read();
                var savedIds=recoveryPositions.stream().map(p->p.book().id()).collect(java.util.stream.Collectors.toSet());
                CapitalManager.INSTANCE.releaseMissing("books",savedIds);
                exposedBooks.retainAll(savedIds);
                for(var position:recoveryPositions)CapitalManager.INSTANCE.restore("books",position.book().id(),position.cost(),true);
                recoveryRequired=!recoveryPositions.isEmpty();recoveryFileError=false;recoveryCheck=null;
            } catch(Exception invalid) {
                Diagnostics.failure("books.journal_load_failed",invalid);recoveryRequired=true;recoveryFileError=true;
                ChatUtils.clientMessage("Book journal unreadable; file preserved. Fix the file and press J again.");
            }
        }
        return !recoveryFileError;
    }
    public boolean recoveryPending() {return recoveryRequired && !recoveryFileError && !recoveryPositions.isEmpty();}
    public void restartRecovery() {
        recoveryCheck=new com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck(recoveryPositions);
        state=State.RECOVERY_CHECK;running=true;paused=false;modePaused=false;
        ChatUtils.clientMessage("Checking saved book records against inventory, storage and Bazaar orders.");
    }
    private void checkSavedBooks() {
        if(recoveryCheck==null)restartRecovery();
        var result=recoveryCheck.tick(LiveMenu.read(),new com.goofy.goofyaddons.menu.LiveActions(),
                GoofyConfig.INSTANCE.firstPage,GoofyConfig.INSTANCE.secondPage,minecraft.getUser().getName(),System.currentTimeMillis());
        if(result==com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck.Result.WAITING)return;
        if(result==com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryCheck.Result.BLOCKED) {safetyHalt(recoveryCheck.reason());return;}
        try {
            var present=recoveryCheck.present();
            // Validate the complete plan before changing the journal or any runtime task.
            var plan=com.goofy.goofyaddons.features.bookflipper.helper.BookRecoveryPlan.build(
                    recoveryPositions,recoveryCheck.snapshots(),minecraft.getUser().getName());
            journal().backupVerified(recoveryPositions);
            journal().reconcileVerified(recoveryPositions,present);
            var resumedRecords=plan.routes().stream().map(r->new BookJournal.Position(r.task().getBook(),
                    Math.max(1,r.task().getReservedUnitCost()*r.task().getBook().getQtyAmount(r.task().getBook().level())),
                    r.task().getProfitTradeId(),r.task().retiring(),r.task().progressAt(),r.task().orphanCleanup())).toList();
            journal().write(resumedRecords);recoveryPositions=resumedRecords;
            clearTransactionState();bookCombiner.reset();bookTransfer.reset();
            listOfTaskToChange.clear();activeTask=null;attemptedToClaim=false;didReceiveItems=false;
            inventoryIsFull=false;overFlowProt=false;usingSecondPage=false;tick=0;
            reconciliationPage=0;reconciliationMismatchSince=-1;
            startupOrders.reset();bookPopulation.reset();outbidFlow.reset();
            taskList.clear();bookLists.clear();bazaarMonitor.reset();inventoryMemory.reset();
            for(var page:recoveryCheck.snapshots().entrySet()) {
                int region=page.getKey()<3?page.getKey():0;
                inventoryMemory.observe(page.getValue(),region,System.currentTimeMillis()-1500);
                inventoryMemory.observe(page.getValue(),region,System.currentTimeMillis());
            }
            CapitalManager.INSTANCE.releaseMissing("books",present);
            exposedBooks.clear();exposedBooks.addAll(present);
            for(var recovered:plan.routes()) {
                Task task=recovered.task();taskList.add(task);
                ProfitTracker.INSTANCE.recoverHoldings(task.getProfitTradeId(),"books",task.getBook().name(),recovered.acquiredUnits());
                CapitalManager.INSTANCE.restore("books",task.getBook().id(),
                        task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()),false);
                if(recovered.orderPrice()!=null)bazaarMonitor.add(task.getBook(),recovered.orderPrice(),recovered.selling());
            }
            bookLists.addAll(plan.extras());
            needToStoreExcessBook=bookLists.stream().anyMatch(b->b.location==0);
            recoveryRequired=false;recoveryCheck=null;
            isStartUpCheckCompleted=!plan.routes().isEmpty();checkedFirstPage=isStartUpCheckCompleted;
            state=plan.routes().isEmpty()?State.START:State.IDLE;lastState=null;clock.stop();watchdog.reset();
            nextFetchMs=System.currentTimeMillis()+FETCH_RETRY_MS;
            if(!checkpoint())return;
            recoveryPositions=List.of();
            recoveryAwaitQuotes=!plan.routes().isEmpty() && BazaarApi.latestFresh()==null;
            recoveryQuoteRetryAt=System.currentTimeMillis()+FETCH_RETRY_MS;
            bazaarMonitor.start();if(recoveryAwaitQuotes)bazaarMonitor.refresh();running=false;
            Diagnostics.event("INFO","books.recovery_resumed",java.util.Map.of("present",present,
                    "tasks",plan.routes().size(),"extras",plan.extras().size()));
            ChatUtils.clientMessage(plan.routes().isEmpty()?"Saved book records were stale; continuing startup.":
                    "Resumed "+plan.routes().size()+" saved book positions and "+plan.extras().size()+" extra books.");
        } catch(Exception failure) {
            Diagnostics.failure("books.recovery_resume_failed",failure);safetyHalt("Cannot resume saved books: "+failure.getMessage()+". Ownership file kept; press J to recheck.");
        }
    }

    /**
     * Resolved on first use. Touching FabricLoader in a field initialiser made
     * FeatureManager's static initialiser unusable outside a running game, which in turn
     * meant no test could reach any code path that calls safetyPause.
     */
    private BookJournal journal() {
        if (bookJournal == null) {
            bookJournal = new BookJournal(FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-book-orders.json"));
        }
        return bookJournal;
    }

    private boolean checkpoint() {
        // A rejected read-only recovery plan must never overwrite the saved evidence.
        if(state==State.RECOVERY_CHECK && recoveryRequired)return false;
        if (!journalLoaded || recoveryRequired && taskList.isEmpty() && bookLists.isEmpty()) return !recoveryRequired;
        try {
            Map<String, BookJournal.Position> positions = new HashMap<>();
            exposedBooks.retainAll(taskList.stream().map(task -> task.getBook().id()).collect(java.util.stream.Collectors.toSet()));
            rememberObservedBooks();
            for (Task task : taskList) positions.put(task.getBook().id(), new BookJournal.Position(task.getBook(),
                    Math.max(1, task.getReservedUnitCost() * task.getBook().getQtyAmount(task.getBook().level())),task.getProfitTradeId(),task.retiring(),task.progressAt(),task.orphanCleanup()));
            for (BookList extra : bookLists) {
                BookJournal.Position previous = positions.get(extra.book.id());
                positions.put(extra.book.id(), previous == null ? new BookJournal.Position(extra.book,
                        GoofyConfig.INSTANCE.maxTradingCapital) : previous);
            }
            journal().writeTracked(positions.values().stream().sorted(Comparator.comparing(position -> position.book().id())).toList(), exposedBooks);
            return true;
        } catch (Exception failed) {
            Diagnostics.failure("books.journal_save_failed",failed);
            recoveryRequired = true;
            paused = true;
            flipCalculator.reset();
            bazaarMonitor.stop();
            FeatureManager.INSTANCE.safetyPause("Cannot save book ownership journal; trading blocked.");
            return false;
        }
    }

    private void safetyHalt(String reason) {
        paused=true; // Latch this engine before fallible evidence capture/cleanup.
        Diagnostics.event("ERROR","books.transaction_blocked",java.util.Map.of("reason",reason,"context",Diagnostics.detailedSnapshot()));
        rememberObservedBooks();
        recoveryRequired |= !exposedBooks.isEmpty() || !bookLists.isEmpty();
        checkpoint();
        paused = true;
        clock.stop();
        flipCalculator.reset();
        bazaarMonitor.stop();
        if (minecraft.player != null && minecraft.screen != null) minecraft.player.closeContainer();
        FeatureManager.INSTANCE.safetyPause(reason);
    }

    private void serviceRetirement() {
        if(retiringTask==null)retiringTask=taskList.stream().filter(Task::retiring).findFirst().orElse(null);
        if(retiringTask==null)return;
        ProfitTracker.INSTANCE.retire(retiringTask.getProfitTradeId());
        // Live quotes are required before an exit click, but absence never clears ownership.
        if(BazaarApi.latestFresh()==null)return;
        var result=retirement.tick(retiringTask,observedMenu,new com.goofy.goofyaddons.menu.LiveActions(),inventoryMemory,
                GoofyConfig.INSTANCE.firstPage,GoofyConfig.INSTANCE.secondPage,minecraft.getUser().getName(),System.currentTimeMillis(),
                new BookRetirement.Receipts(){
                    public boolean checkpoint(){return BazaarFlipper.this.checkpoint();}
                    public void acquired(Task task,int units,double price,String event){
                        ProfitTracker.INSTANCE.acquire(task.getProfitTradeId(),"books",task.getBook().name(),event,units,price*units);
                    }
                    public void sold(Task task,int units,double proceeds,String event){
                        ProfitTracker.INSTANCE.sell(task.getProfitTradeId(),"books",task.getBook().name(),event,units,proceeds);
                        Diagnostics.event("INFO","books.retirement_sale",java.util.Map.of("trade",task.getProfitTradeId(),"baseUnits",units,"proceeds",proceeds));
                    }
                });
        if(result==BookRetirement.Result.BLOCKED){safetyHalt(retirement.reason());return;}
        if(result==BookRetirement.Result.COLLECT_SALE){
            retiringTask.cancelRetirement();retiringTask.progress(System.currentTimeMillis());retiringTask.setBookState(Task.BookState.REPLACE_SELL);
        } else if(result==BookRetirement.Result.COMPLETE){
            Task done=retiringTask;retiredUntil.put(done.getBook().id(),System.currentTimeMillis()+30*60_000L);
            taskList.remove(done);exposedBooks.remove(done.getBook().id());
            CapitalManager.INSTANCE.release("books",done.getBook().id());
            bazaarMonitor.finish(done.getBook(),false);bazaarMonitor.finish(done.getBook(),true);
            listOfTaskToChange.remove(done);
            Diagnostics.event("INFO","books.retirement_complete",java.util.Map.of("trade",done.getProfitTradeId(),"item",done.getBook().id()));
        } else return;
        retirement.reset();retiringTask=null;watchdog.reset();clock.stop();
        if(minecraft.screen!=null)minecraft.player.closeContainer();
        yieldAfterMs=System.currentTimeMillis()+1000;checkpoint();
    }

    private boolean checkHoldingLimits() {
        long now = System.currentTimeMillis();
        com.google.gson.JsonObject latest = BazaarApi.latestFresh();
        for (Task task : taskList) {
            if(task.retiring() || task.getBookState()==Task.BookState.REPLACE_SELL)continue;
            long since = heldSince.computeIfAbsent(task.getBook().id(), ignored -> now);
            double cost = task.getReservedUnitCost() * task.getBook().getQtyAmount(task.getBook().level());
            if(task.recovered()) {
                Double known=ProfitTracker.INSTANCE.knownCost(task.getProfitTradeId(),task.getBook().getQtyAmount(task.getBook().level()));
                cost=known==null?0:known;
            }
            double exit = -1;
            if (latest != null) {
                com.google.gson.JsonObject product = latest.getAsJsonObject("products").getAsJsonObject(task.getBook().getLevel(task.getBook().sellLevel()));
                if (product != null) exit = com.goofy.goofyaddons.features.generalflipper.GeneralCalculator.topPrice(product, "buy_summary")
                        * (1 - GoofyConfig.INSTANCE.bazaarTaxPercentage / 100);
            }
            if (TradingSafety.holdingLimit(since, now, GoofyConfig.INSTANCE.maxBookHoldingSeconds,
                    cost, exit, GoofyConfig.INSTANCE.maxBookDrawdownPercentage)) {
                if(GoofyConfig.INSTANCE.liquidateStaleBooks) {task.retire();ProfitTracker.INSTANCE.retire(task.getProfitTradeId());}
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
            CapitalManager.INSTANCE.release("books", book.id());
            debug("[BazaarFlipper] resizeRetainedExtras: no extras left for " + book + ", released its allocation");
            return;
        }
        CapitalManager.INSTANCE.restore("books", book.id(), value, false);
        Diagnostics.event("INFO", "books.extra_exposure_resized", java.util.Map.of(
                "item", book.id(), "retainedValue", value));
        debug("[BazaarFlipper] resizeRetainedExtras: " + book + " allocation reduced to " + value + " for retained extras");
    }

    private void rememberObservedBooks() {
        for (Task task : taskList) if (!task.bookList.isEmpty()) exposedBooks.add(task.getBook().id());
        for (BookList book : bookLists) exposedBooks.add(book.book.id());
        // A claim click or a submitted order may already have reached the server.
        // A selected price alone has not, so confirmationTask is deliberately absent.
        if (pendingBuyClaim != null) exposedBooks.add(pendingBuyClaim.getBook().id());
        if (pendingSaleClaim != null) exposedBooks.add(pendingSaleClaim.getBook().id());
        if (submittedBookTask != null) exposedBooks.add(submittedBookTask.getBook().id());
    }

    private void navigationClick(int slot) {
        if (!observedMenu.loaded(slot) || !observedMenu.cursorEmpty()) return;
        navigationRetry.sent(observedMenu,slot,System.currentTimeMillis());
        InventoryUtils.clickSlot(slot,false);
    }

    /**
     * Per-transaction evidence is only meaningful for the tasks that produced it.
     * {@link #stop()} discards every task, so leaving any of this set would let a
     * later run wait on, or account for, a task that no longer exists.
     */
    private void clearTransactionState() {
        navigationRetry.reset();signSubmittedAt=0;signRestarts=0;
        reportedUnclaimedSales = false;
        combineInputsAbsent = 0;
        pendingBuyClaim = null;
        buyClaimBefore = 0;
        buyClaimExpected = 0;
        buyClaimUnitPrice = null;
        buyClaimEvent = null;
        pendingSaleClaim = null;
        saleClaimReceipt = false;
        saleClaimProceeds = null;
        saleClaimAt = 0;
        submittedBookTask = null;
        submittedNextState = null;
        submittedBookSelling = false;
        submittedBookUnits = 0;
        submittedBookPrice = 0;
        submittedBookAt = 0;
        confirmationTask = null;
        confirmationPrice = 0;
        confirmationSelling = false;
        confirmationSelectedAt = 0;
        confirmationStability.reset();
        menuRecheck.reset();
        purseObservation.reset();
        purseWaitLogged = false;
        ordersSettle.reset();
        heldSince.clear();
    }

    private boolean recordBookSubmission(Task task,boolean sale) {
        // Save the uncertainty barrier before the server can receive a confirmation click.
        exposedBooks.add(task.getBook().id());
        if (!checkpoint()) return false;
        submittedBookTask=task;submittedBookSelling=sale;submittedBookAt=System.currentTimeMillis();
        submittedBookUnits=sale?1:task.getAmountToOrder();submittedBookPrice=confirmationPrice;
        Diagnostics.event("INFO","order.submission_intent",java.util.Map.of("engine","books","trade",task.getProfitTradeId(),
                "units",submittedBookUnits,"unitPrice",submittedBookPrice,"selling",sale));
        return true;
    }

    private void verifyBookPlacement() {
        Task task=submittedBookTask;
        if(task==null) {safetyHalt("Book submission intent missing; ownership retained.");return;}
        if(System.currentTimeMillis()-submittedBookAt<2000) return;
        if(minecraft.screen==null) {
            clock.start(randomizer());
            if(clock.shouldFire()) {Diagnostics.command("managebazaarorders");clock.stop();}
            return;
        }
        boolean orders=TradingSafety.ordersTitle(minecraft.screen.getTitle().getString());
        if(!orders || !inventoryScanner.isMenuLoaded(35) || inventoryScanner.findContainer("Close").isEmpty()) {
            if(!recheckBookOrders(task,"placement-menu-not-ready")) safetyHalt("Cannot load fresh book order verification; intent retained.");
            return;
        }
        String item=task.getBook().getRomanLevel(submittedBookSelling?task.getBook().sellLevel():task.getBook().level());
        var slots=inventoryScanner.findContainer((submittedBookSelling?"SELL ":"BUY ")+item);
        if(slots.isEmpty()) {
            if(!recheckBookOrders(task,"submitted-order-not-visible")) safetyHalt("Submitted book order still absent after fresh-menu checks; intent retained without resubmitting.");
            return;
        }
        var stack=minecraft.player.containerMenu.slots.get(slots.getFirst()).getItem();
        var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
        String text=lore==null?"":String.join("\n",lore.lines().stream().map(line->line.getString()).toList());
        Integer units=com.goofy.goofyaddons.features.generalflipper.OrderLore.total(text);
        Double price=TradeReceipts.unitPrice(text);
        if((units==null || price==null) && recheckBookOrders(task,"placement-fields-unreadable")) return;
        if(!TradingSafety.orderMatchesIntent(submittedBookUnits,submittedBookPrice,units,price)) {
            safetyHalt("Submitted book order amount/price differs or is unreadable; intent retained.");return;
        }
        Diagnostics.event("INFO","order.verified",java.util.Map.of("engine","books","trade",task.getProfitTradeId(),"item",item,"units",submittedBookUnits));
        if(!submittedBookSelling) {
            CapitalManager.INSTANCE.purchased("books",task.getBook().id());
            var book=task.getBook();
            if(submittedBookUnits==book.getQtyAmount(book.level())) ProfitTracker.INSTANCE.beginExecution(task.getProfitTradeId(),"books",
                    book.getLevel(book.level()),book.getLevel(book.sellLevel()),submittedBookUnits,1,submittedBookAt);
        }
        task.setBookState(submittedNextState);
        submittedBookTask=null;submittedNextState=null;
        state=State.IDLE;clock.stop();
    }

    /**
     * A06: the engine used to locate an existing BUY order by display name and claim it
     * without reading its amount. Adoption now requires a readable total that this route
     * could actually have ordered; anything else is retried as an observation, then
     * retained rather than claimed.
     */
    private boolean bookOrderAdoptable(Task task, int slot, String reason) {
        var lore = minecraft.player.containerMenu.slots.get(slot).getItem().get(net.minecraft.core.component.DataComponents.LORE);
        String text = lore == null ? "" : String.join("\n", lore.lines().stream().map(line -> line.getString()).toList());
        Integer total = com.goofy.goofyaddons.features.generalflipper.OrderLore.total(text);
        if (total == null && recheckBookOrders(task, reason)) return false;
        if (!TradingSafety.adoptableOrderTotal(total, task.getBook().getQtyAmount(task.getBook().level()))) {
            Diagnostics.event("ERROR","books.order_amount_rejected",java.util.Map.of("trade",task.getProfitTradeId(),
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
    private boolean mayOpenBookOrder(Task task) {
        long now=System.currentTimeMillis();
        if(task.retiring())return false;
        if(now<bookBuyRetryAt.getOrDefault(task.getProfitTradeId(),0L)) return false;
        double cost=task.getReservedUnitCost()*task.getAmountToOrder();
        double hold=Math.max(CapitalManager.INSTANCE.cost("books",task.getBook().id()),
                task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()));
        if(cost>0 && CapitalManager.INSTANCE.refusal(task.getBook().id(),hold,cost,scoreboardUtils.getPurse())!=null)
            return parkBook(task,"insufficient-spendable-capital",now);
        if(task.lastPlacedAt()>0) {
            if(task.reprices()>=GoofyConfig.INSTANCE.maxBookReprices) return parkBook(task,"reprice-budget-spent",now);
            if(now-task.lastPlacedAt()<GoofyConfig.INSTANCE.bookRepriceCooldownSeconds*1000L)
                return parkBook(task,"reprice-cooldown",now);
        }
        // A route already holding capital is not counted against itself.
        long active=com.goofy.goofyaddons.features.bookflipper.helper.BookSchedule.activePositions(taskList);
        if(active>=GoofyConfig.INSTANCE.maxActiveBooks && !CapitalManager.INSTANCE.occupied(task.getBook().id()))
            return parkBook(task,"max-active-books",now);
        return true;
    }

    private final java.util.Map<String,Long> bookBuyRetryAt=new java.util.HashMap<>();

    private void deferBookPurchase(Task task,String reason) {
        long now=System.currentTimeMillis();
        bookBuyRetryAt.put(task.getProfitTradeId(),now+20_000);
        parkBook(task,reason,now);
        confirmationTask=null;confirmationStability.reset();
        if(minecraft.screen!=null)minecraft.player.closeContainer();
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
            Diagnostics.event("INFO","books.order_deferred",java.util.Map.of("engine","books",
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
        var slots = minecraft.player.containerMenu.slots;
        for (int i = 0; i < Math.max(0, slots.size() - 36); i++) {
            var stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
            names.add(stack.getHoverName().getString().replaceAll("\u00a7.", ""));
            lores.add(lore == null ? java.util.List.of()
                    : lore.lines().stream().map(line -> line.getString().replaceAll("\u00a7.", "")).toList());
        }
        var found = com.goofy.goofyaddons.features.bookflipper.helper.OrphanSales.scan(names, lores);
        if (found.isEmpty()) return;
        long coins = com.goofy.goofyaddons.features.bookflipper.helper.OrphanSales.total(found);
        Diagnostics.event("WARN","books.unclaimed_sales",java.util.Map.of("engine","books",
                "offers",found.size(),"coins",coins,
                "detail",found.stream().map(sale -> sale.units()+"x "+sale.item()+"="+sale.coins()).toList()));
        ChatUtils.clientMessage("BazaarFlipper: " + String.format("%,d", coins) + " coins unclaimed across "
                + found.size() + " filled sell offer(s). Collect them in /managebazaarorders.");
    }

    private boolean reportedUnclaimedSales;

    private int combineInputsAbsent;
    private static final int COMBINE_ABSENT_TICKS = 10;

    private boolean recheckBookOrders(Task task,String reason) {
        var decision=menuRecheck.missing(state+":"+task.getProfitTradeId(),System.currentTimeMillis());
        if(decision==com.goofy.goofyaddons.features.MenuRecheck.Decision.REOPEN) {
            Diagnostics.event("WARN","order.observation_recheck",java.util.Map.of("engine","books","reason",reason,
                    "trade",task.getProfitTradeId(),"attempt",menuRecheck.attempts(),"context",Diagnostics.detailedSnapshot()));
            minecraft.player.closeContainer();ordersSettle.reset();clock.stop();
        }
        return decision!=com.goofy.goofyaddons.features.MenuRecheck.Decision.EXHAUSTED;
    }

    private boolean verifyBookConfirmation() {
        Task task=confirmationTask;
        boolean sale=state==State.SELL || state==State.REPLACE_SELL;
        if(!sale && task!=null && !java.util.Set.of(Task.ActionSchedule.NONE,Task.ActionSchedule.SELECTED_STORE_BUYORDER,Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER).contains(task.actionSchedule)) {
            safetyHalt("Book buy schedule is incompatible with submission; no confirmation clicked.");return false;
        }
        if(task==null || confirmationSelling!=sale || !taskList.contains(task)
                || System.currentTimeMillis()-confirmationSelectedAt>30000 || !sale && task!=activeTask) {
            safetyHalt("Book confirmation has no matching price-selection intent.");return false;
        }
        var stack=minecraft.player.containerMenu.slots.get(13).getItem();
        var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
        String text=lore==null?"":String.join("\n",lore.lines().stream().map(line->line.getString()).toList());
        if(!confirmationStability.ready(minecraft.player.containerMenu.containerId,
                minecraft.screen.getTitle().getString()+"\n"+stack.getHoverName().getString()+"\n"+text,!stack.isEmpty() && lore!=null,System.currentTimeMillis())) return false;
        int quantity=sale?1:task.getAmountToOrder();
        String item=task.getBook().getRomanLevel(sale?task.getBook().sellLevel():task.getBook().level());
        boolean previewMatches=com.goofy.goofyaddons.features.ConfirmationCheck.matches(minecraft.screen.getTitle().getString(),sale,
                stack.getHoverName().getString(),text,item,quantity,confirmationPrice);
        boolean profitAllowed=bookPriceAllowed(task,confirmationPrice,sale);
        Diagnostics.event(previewMatches && profitAllowed?"INFO":"ERROR","books.confirmation_check",java.util.Map.of(
                "trade",task.getProfitTradeId(),"expectedItem",item,"expectedUnits",quantity,"expectedUnitPrice",confirmationPrice,
                "previewMatches",previewMatches,"profitAllowed",profitAllowed,"context",Diagnostics.detailedSnapshot()));
        if(!previewMatches || !profitAllowed) {
            safetyHalt(!previewMatches ? "Book confirmation item, quantity or price could not be verified."
                    : sale ? "Book sale confirmation price or market data could not be verified."
                    : "Book buy confirmation no longer meets minimum net profit.");return false;
        }
        if(sale) {
            if(inventoryScanner.findLoreInv(item).size()!=1) {
                safetyHalt("Book sale inventory changed before confirmation.");return false;
            }
        } else {
            double purse=scoreboardUtils.getPurse();
            if (!purchasePurseReady(purse)) return false;
            double cost=confirmationPrice*quantity;
            int empty=inventoryScanner.getEmptyInventorySlots();
            String product=task.getBook().id();
            double hold=Math.max(CapitalManager.INSTANCE.cost("books",product),
                    task.getReservedUnitCost()*task.getBook().getQtyAmount(task.getBook().level()));
            // One compound condition used to cover four separate causes and halt with one
            // message, so a field report could not say which of them fired. Each arm now
            // names itself, and the ledger numbers go into the event either way.
            String cause=quantity>empty?"inventory-capacity":cost>purse?"cost-exceeds-purse"
                    :CapitalManager.INSTANCE.refusal(product,hold,cost,purse);
            if(cause==null && !CapitalManager.INSTANCE.resize("books",product,hold,cost,purse)) cause="ledger-resize-refused";
            if(cause!=null) {
                java.util.Map<String,Object> detail=new java.util.LinkedHashMap<>();
                detail.put("trade",task.getProfitTradeId());detail.put("cause",cause);
                detail.put("units",quantity);detail.put("emptySlots",empty);
                detail.put("orderCost",cost);detail.put("holdRequested",hold);detail.put("purse",purse);
                detail.put("committed",CapitalManager.INSTANCE.committed());
                detail.put("pending",CapitalManager.INSTANCE.pending());
                detail.put("capitalLimit",CapitalManager.INSTANCE.limit());
                detail.put("reserve",CapitalManager.INSTANCE.reserve());
                Diagnostics.event("ERROR","books.capital_check_failed",detail);
                if(java.util.Set.of("cost-exceeds-purse","purse-below-reserve","purse-minus-pending-too-low","capital-limit-reached").contains(cause))
                    deferBookPurchase(task,cause);
                else safetyHalt("Book buy blocked before confirmation ("+cause+"); position retained.");
                return false;
            }
        }
        return true;
    }

    private boolean purchasePurseReady(double purse) {
        var result = purseObservation.observe(purse, System.currentTimeMillis());
        if (result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.READY)
            purseWaitLogged = false;
        else if (!purseWaitLogged) {
            purseWaitLogged = true;
            Diagnostics.event("WARN", "books.purse_observation_wait", java.util.Map.of(
                    "purse", purse, "reason", com.goofy.goofyaddons.utils.ScoreboardUtils.purseStatus(), "state", state.name()));
        }
        if (result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.TIMED_OUT)
            safetyHalt("Purse remained unreadable for 10 seconds; no purchase submitted. Restore the scoreboard before resuming.");
        return result == com.goofy.goofyaddons.features.bookflipper.helper.PurseObservation.Result.READY;
    }

    private void selectSalePrice(Task task,boolean finishBuyMonitor) {
        double price=inventoryScanner.getUnitPrice(12);
        if (!bookPriceAllowed(task,price,true)) {
            safetyHalt("Book sale price or market data could not be verified; book retained.");return;
        }
        if (finishBuyMonitor) bazaarMonitor.finish(task.getBook(),false);
        bazaarMonitor.add(task.getBook(),price,true);
        confirmationTask=task;confirmationPrice=price;confirmationSelling=true;
        confirmationSelectedAt=System.currentTimeMillis();confirmationStability.reset();
        navigationClick(12);
    }

    private boolean bookPriceAllowed(Task task, double price, boolean sale) {
        com.google.gson.JsonObject latest = BazaarApi.latestFresh();
        com.google.gson.JsonObject product = latest == null ? null
                : latest.getAsJsonObject("products").getAsJsonObject(task.getBook().getLevel(task.getBook().sellLevel()));
        double exit = sale ? price : product == null ? -1
                : com.goofy.goofyaddons.features.generalflipper.GeneralCalculator.topPrice(product, "buy_summary");
        var check=BookPricePolicy.check(sale,latest!=null,price,task.getReservedUnitCost(),
                task.getBook().getQtyAmount(task.getBook().level()),exit,
                GoofyConfig.INSTANCE.bazaarTaxPercentage,GoofyConfig.INSTANCE.minNetProfit);
        var detail=new java.util.LinkedHashMap<String,Object>();
        detail.put("trade",task.getProfitTradeId());detail.put("item",task.getBook().id());detail.put("selling",sale);
        detail.put("price",Double.isFinite(price)?price:"unreadable");detail.put("quotesFresh",latest!=null);
        detail.put("expectedExit",Double.isFinite(exit)?exit:"unreadable");
        detail.put("estimatedInputCost",check.estimatedCost()==null?"unavailable":check.estimatedCost());
        detail.put("estimatedNet",check.estimatedNet()==null?"unavailable":check.estimatedNet());
        detail.put("minimumProfit",GoofyConfig.INSTANCE.minNetProfit);detail.put("taxPercentage",GoofyConfig.INSTANCE.bazaarTaxPercentage);
        detail.put("allowed",check.allowed());detail.put("reason",check.reason());
        Diagnostics.event(check.allowed()?"INFO":"WARN","books.price_check",detail);
        return check.allowed();
    }

}
