package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarMonitor;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.BookCombiner;
import com.goofy.goofyaddons.features.bookflipper.helper.BookList;
import com.goofy.goofyaddons.features.bookflipper.helper.BookSellCancellation;
import com.goofy.goofyaddons.features.bookflipper.helper.BookTransfer;
import com.goofy.goofyaddons.features.bookflipper.helper.InventoryMemory;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.menu.GameActions;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.utils.Clock;
import com.goofy.goofyaddons.utils.InventoryScanner;

import java.util.List;

/**
 * What a single book operation may see and do.
 *
 * <p>The engine implements this. Every operation reads the tick's one captured menu,
 * shares the engine's action clock, and goes through the engine's checkpoint and safety
 * halt, so moving a state into its own class cannot change how it observes or fails.
 */
interface BookContext {
    long now();
    boolean signOpen();
    String username();
    GameActions actions();
    BookServices services();
    CapitalManager capital();
    BookAccounting accounting();
    BookSettings settings();

    State state();
    void state(State next);
    Clock clock();
    /** The configured randomized action delay. */
    int delay();
    /** Releases the menu to other work for this long before the next action. */
    void yieldFor(long millis);

    MenuSnapshot menu();
    InventoryScanner scanner();
    InventoryMemory inventoryMemory();
    boolean screenOpen();
    String menuTitle();
    boolean containerNameCheck(String name);
    void navigationClick(int slot);

    List<Task> tasks();
    List<BookList> extras();
    Task activeTask();
    void activeTask(Task task);
    Task taskInState(Task.BookState state);
    void handleBookList(Book book, int location, int level, int amount);
    /** Records that ownership of this product may already have reached the server. */
    void expose(String product);

    BazaarMonitor monitor();
    BookTransfer transfer();
    BookCombiner combiner();
    BookSellCancellation sellCancellation();
    BookClaim claim();
    BookPlacement placement();

    boolean checkpoint();
    void safetyHalt(String reason);
    void initSelfRecovery();
    boolean recheckBookOrders(Task task, String reason);
    boolean bookOrderAdoptable(Task task, int slot, String reason);
    void reportUnclaimedSales();
    void fundedHoldings(Task task);
    /** Shrinks a finished route's allocation to the extras it left behind. */
    void resizeRetainedExtras(Book book, double unitCost);
    void skipBookRequirement(Task task, String reason);
    void deferBookPurchase(Task task, String reason);
    boolean purchasePurseReady(double purse);
    boolean bookPriceAllowed(Task task, double price, boolean sale);
    /** Reinspects remembered storage before any further book action. */
    void startLocationReconciliation();
    void debug(String text);

    void inventoryFull();
    void overflowProtection();
    void startupComplete();
    boolean needToStoreExcessBook();
    void needToStoreExcessBook(boolean value);
}
