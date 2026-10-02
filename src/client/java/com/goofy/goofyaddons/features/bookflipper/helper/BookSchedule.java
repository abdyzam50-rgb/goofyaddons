package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.List;
import java.util.Map;

/**
 * Which tracked book the engine services next.
 *
 * <p>The policy, highest first: finish and realise value before starting new work. A flip
 * only completes, and only frees its capital, when a sale is collected, so that comes
 * first; committing coins to a new order comes last.
 *
 * <p>This replaces an unlabelled table whose sense was inverted. Under it,
 * REPLACE_SELL — the one state that collects proceeds and removes a task — ranked below
 * every other, while SELECTED (spend capital on a new buy order) ranked near the top. With
 * the outbid monitor re-queueing work every 20 seconds and filled buy orders also routing
 * through OUTBID, something almost always outranked completion, so finished books could
 * sit unsold and settled sales uncollected while the engine kept opening new positions.
 */
public final class BookSchedule {
    private BookSchedule() {}

    private static final Map<Task.BookState, Integer> PRIORITY = Map.of(
            Task.BookState.REPLACE_SELL, 8,        // collect a settled sale: realises coins
            Task.BookState.BAZAAR_ORDER_CHECK, 7,  // startup reconciliation before new work
            Task.BookState.SELL, 6,                // list a finished book
            Task.BookState.OUTBID, 5,              // claim a filled buy, or reprice one
            Task.BookState.COMBINE, 4,             // merge toward something sellable
            Task.BookState.ANVIL, 3,               // retrieve inputs for merging
            Task.BookState.STORE, 2,               // housekeeping
            Task.BookState.SELECTED, 1             // commit coins to a new order, last
    );

    /** Whether this state is something the engine can act on from IDLE. */
    public static boolean actionable(Task.BookState state) {
        return PRIORITY.containsKey(state);
    }

    /**
     * The task to service, or null when none is actionable.
     *
     * <p>OUTBID is held back before startup reconciliation finishes and while the
     * inventory is known full, because claiming into a full inventory cannot succeed.
     * Among equal ranks the earliest task wins, so ordering stays stable across ticks.
     */
    public static Task next(List<Task> tasks, boolean startupComplete, boolean inventoryFull) {
        Task chosen = null;
        int best = Integer.MIN_VALUE;
        for (Task task : tasks) {
            Task.BookState state = task.getBookState();
            if (state == Task.BookState.OUTBID && (!startupComplete || inventoryFull)) continue;
            Integer rank = PRIORITY.get(state);
            if (rank == null || rank <= best) continue;
            chosen = task;
            best = rank;
        }
        return chosen;
    }
}
