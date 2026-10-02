package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ordering that decides whether a flip ever completes.
 *
 * <p>Each case is a situation where the previous table serviced the wrong task and the
 * loop could not close: proceeds left uncollected, finished books left unlisted, capital
 * committed to new orders instead.
 */
class BookScheduleTest {
    private static final Book WISE = new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise", 0, 0);
    private static final Book JERRY = new Book("ENCHANTMENT_ULTIMATE_JERRY", 1, 5, "Ultimate Jerry", 0, 0);

    private static Task task(Book book, Task.BookState state) {
        Task task = new Task(book, false, false);
        task.setBookState(state);
        return task;
    }

    private static Task next(List<Task> tasks) {
        return BookSchedule.next(tasks, true, false);
    }

    @Test void collectingASettledSaleComesBeforeCommittingCoinsToANewOrder() {
        Task sale = task(WISE, Task.BookState.REPLACE_SELL);
        Task newOrder = task(JERRY, Task.BookState.SELECTED);
        assertSame(sale, next(List.of(newOrder, sale)));
        assertSame(sale, next(List.of(sale, newOrder)), "order in the list must not decide it");
    }

    @Test void repricingCanNeverIndefinitelyDelayCollectingASale() {
        // The starvation case: the monitor re-queues outbid work every 20 seconds, so
        // under the old ordering a settled sale could wait behind it forever.
        Task sale = task(WISE, Task.BookState.REPLACE_SELL);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) tasks.add(task(JERRY, Task.BookState.OUTBID));
        tasks.add(sale);
        assertSame(sale, next(tasks));
    }

    @Test void listingAFinishedBookComesBeforeStartingNewWork() {
        Task sell = task(WISE, Task.BookState.SELL);
        Task newOrder = task(JERRY, Task.BookState.SELECTED);
        assertSame(sell, next(List.of(newOrder, sell)));
    }

    @Test void startupReconciliationIsNotStarvedByNewWork() {
        Task reconcile = task(WISE, Task.BookState.BAZAAR_ORDER_CHECK);
        Task newOrder = task(JERRY, Task.BookState.SELECTED);
        assertSame(reconcile, next(List.of(newOrder, reconcile)));
    }

    @Test void progressTowardsASaleOutranksHousekeeping() {
        assertSame(next(List.of(task(WISE, Task.BookState.STORE), task(JERRY, Task.BookState.COMBINE))).getBook(), JERRY);
        assertSame(next(List.of(task(WISE, Task.BookState.STORE), task(JERRY, Task.BookState.ANVIL))).getBook(), JERRY);
        assertSame(next(List.of(task(WISE, Task.BookState.ANVIL), task(JERRY, Task.BookState.COMBINE))).getBook(), JERRY);
    }

    @Test void theFullOrderingRunsFromCollectingDownToCommitting() {
        List<Task.BookState> highestFirst = List.of(
                Task.BookState.REPLACE_SELL, Task.BookState.BAZAAR_ORDER_CHECK, Task.BookState.SELL,
                Task.BookState.OUTBID, Task.BookState.COMBINE, Task.BookState.ANVIL,
                Task.BookState.STORE, Task.BookState.SELECTED);
        for (int higher = 0; higher < highestFirst.size(); higher++) {
            for (int lower = higher + 1; lower < highestFirst.size(); lower++) {
                Task winner = task(WISE, highestFirst.get(higher));
                Task loser = task(JERRY, highestFirst.get(lower));
                assertSame(winner, next(List.of(loser, winner)),
                        highestFirst.get(higher) + " must outrank " + highestFirst.get(lower));
            }
        }
    }

    @Test void claimingIsHeldBackUntilStartupReconciliationFinishes() {
        Task outbid = task(WISE, Task.BookState.OUTBID);
        assertNull(BookSchedule.next(List.of(outbid), false, false),
                "claiming before reconciliation could adopt an unverified position");
        assertSame(outbid, BookSchedule.next(List.of(outbid), true, false));
    }

    @Test void claimingIsHeldBackWhileTheInventoryIsFull() {
        Task outbid = task(WISE, Task.BookState.OUTBID);
        assertNull(BookSchedule.next(List.of(outbid), true, true),
                "a claim into a full inventory cannot succeed");
    }

    @Test void aFullInventoryStillLetsOtherWorkProceed() {
        Task outbid = task(WISE, Task.BookState.OUTBID);
        Task store = task(JERRY, Task.BookState.STORE);
        assertSame(store, BookSchedule.next(List.of(outbid, store), true, true),
                "storing is how a full inventory gets unblocked");
    }

    @Test void statesTheEngineCannotActOnAreNeverChosen() {
        assertFalse(BookSchedule.actionable(Task.BookState.IN_BUY_ORDER));
        assertFalse(BookSchedule.actionable(Task.BookState.SELL_ORDER));
        assertNull(next(List.of(task(WISE, Task.BookState.IN_BUY_ORDER), task(JERRY, Task.BookState.SELL_ORDER))),
                "a task waiting on the server is not work");
    }

    @Test void equalRanksKeepTheEarliestTaskSoOrderingIsStable() {
        Task first = task(WISE, Task.BookState.SELL);
        Task second = task(JERRY, Task.BookState.SELL);
        assertSame(first, next(List.of(first, second)));
        assertSame(first, next(List.of(first, second)), "repeated calls must not rotate");
    }

    @Test void noTasksMeansNoWork() {
        assertNull(next(List.of()));
    }

    @Test void everyActionableStateHasARank() {
        for (Task.BookState state : Task.BookState.values()) {
            boolean waiting = state == Task.BookState.IN_BUY_ORDER || state == Task.BookState.SELL_ORDER;
            assertEquals(!waiting, BookSchedule.actionable(state), state + " rank presence");
        }
    }
}
