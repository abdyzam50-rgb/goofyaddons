package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.BookOutbidFlow;
import com.goofy.goofyaddons.features.bookflipper.helper.BookStartupOrders;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;

import java.util.List;

/**
 * Claiming what a book buy order filled and cancelling the remainder, at startup and
 * after the order was outbid.
 *
 * <p>Filled books are claimed first and acknowledged by {@link BookClaim}. A cancellation
 * is sent once and must be seen to remove the order before the route is re-planned.
 */
final class BookCancel {
    private final BookOutbidFlow outbidFlow = new BookOutbidFlow();
    private final BookStartupOrders startupOrders = new BookStartupOrders();

    /** Forgets observation windows when the engine changes state. */
    void stateChanged() { outbidFlow.reset(); startupOrders.reset(); }
    void resetStartup() { startupOrders.reset(); }

    /** Read once per tick, before any checkpoint, while checking startup orders. */
    List<Task> startupMissing(BookContext ctx) {
        return startupOrders.missingBuys(ctx.tasks(), ctx.menu(), ctx.now());
    }

    void startup(BookContext ctx, List<Task> startupMissing) {
        Task task = ctx.taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
        if (task == null) {
            ctx.debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: no task left in BAZAAR_ORDER_CHECK, startup complete, going to IDLE");
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            ctx.startupComplete();
            return;
        }

        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command("managebazaarorders");
        }

        if ((ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle()))) ctx.clock().start(ctx.delay());
        if ((ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle())) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            ctx.reportUnclaimedSales();
            // Waiting for the claimed items to appear.
            if (!ctx.claim().settledAttempt()) return;

            if (startupMissing == null) return;
            for (var missing : startupMissing) {
                ctx.fundedHoldings(missing);
                BookStartupOrders.scheduleMissingBuy(missing);
                ctx.services().event("INFO", "books.startup_order_accounted", java.util.Map.of(
                        "item", missing.getBook().id(), "buyOrderPresent", false,
                        "remaining", missing.getAmountToOrder(), "nextState", missing.getBookState().name(),
                        "container", ctx.menu().containerId()));
            }
            task = ctx.taskInState(Task.BookState.BAZAAR_ORDER_CHECK);
            if (task == null) {
                ctx.actions().closeMenu();
                ctx.state(State.IDLE);
                ctx.startupComplete();
                return;
            }
            List<Integer> slot = ctx.scanner().findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));
            if (slot.isEmpty()) return;

            ctx.expose(task.getBook().id());
            if (!ctx.checkpoint()) return;
            if (!ctx.bookOrderAdoptable(task, slot.getFirst(), "startup-order-amount-unreadable")) return;
            int amount = ctx.scanner().checkOrder(slot.getFirst());
            if (amount > ctx.scanner().getEmptyInventorySlots()) {
                ctx.debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: not enough empty inventory slots to claim " + amount + " items, going to IDLE");
                ctx.overflowProtection();
                ctx.state(State.IDLE);
                return;
            }
            ctx.claim().beginBuy(ctx, task, amount, slot.getFirst());
            ctx.actions().click(slot.getFirst(), false);
            // Assigned only after the expected inventory delta is verified.
            if (amount > 0) ctx.debug("[BazaarFlipper] STARTUP_BAZAAR_CHECK: claiming " + amount + " of " + task.getBook());
        }

        if (ctx.containerNameCheck("Order")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Order") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            List<Integer> slot = ctx.scanner().findContainer("Cancel Order");
            if (slot.isEmpty()) return;
            ctx.capital().funding("books", task.getBook().id(), null);
            ctx.actions().click(slot.getFirst(), false);
        }
    }

    void outbid(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.OUTBID);
        if (task == null) {
            ctx.debug("[BazaarFlipper] OUTBID: no task left in OUTBID, going to IDLE");
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }

        outbidFlow.selectTrade(task.getProfitTradeId());
        if (outbidFlow.cancellationTimedOut(ctx.now())) {
            ctx.safetyHalt("Outbid book cancellation was not verified; no replacement submitted."); return;
        }
        boolean navigatingOutbid = !ctx.screenOpen()
                || !TradingSafety.ordersTitle(ctx.menuTitle()) && !ctx.containerNameCheck("Order");
        if (navigatingOutbid) ctx.clock().start(ctx.delay());
        if (navigatingOutbid && ctx.clock().shouldFire()) {
            var navigation = outbidFlow.navigate(task.getBook(), ctx.menu());
            if (navigation != null) {
                if (navigation.fallback()) ctx.services().event("WARN", "books.outbid_navigation_fallback",
                        java.util.Map.of("item", task.getBook().id(), "context", ctx.services().diagnosticContext()));
                if (navigation.command() != null) ctx.actions().command(navigation.command());
                else ctx.actions().click(navigation.slot(), false);
                return;
            }
        }

        if ((ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle()))) ctx.clock().start(ctx.delay());
        if ((ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle())) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            ctx.reportUnclaimedSales();
            // Waiting for the claimed items to appear.
            if (!ctx.claim().settledAttempt()) return;

            List<Integer> slot = ctx.scanner().findContainer("BUY " + task.getBook().getRomanLevel(task.getBook().level()));

            if (slot.isEmpty()) {
                if (!outbidFlow.freshAfterCancellation(ctx.menu().containerId())
                        && ctx.recheckBookOrders(task, "missing-buy-order")) return;
                ctx.fundedHoldings(task);
                replan(ctx, task);
                return;
            }

            if (outbidFlow.cancellationSent()) return; // Wait for removal; never re-claim/cancel stale packets.
            if (!ctx.bookOrderAdoptable(task, slot.getFirst(), "outbid-order-amount-unreadable")) return;
            int amount = ctx.scanner().checkOrder(slot.getFirst());
            if (amount > ctx.scanner().getEmptyInventorySlots()) {
                ctx.debug("[BazaarFlipper] OUTBID: not enough empty inventory slots to claim " + amount + " items, going to IDLE and marking inventory full");
                ctx.state(State.IDLE);
                ctx.inventoryFull();
                return;
            }
            ctx.claim().beginBuy(ctx, task, amount, slot.getFirst());
            ctx.actions().click(slot.getFirst(), false);
            // Assigned only after the expected inventory delta is verified.
            if (amount > 0) ctx.debug("[BazaarFlipper] OUTBID: claiming " + amount + " of " + task.getBook());
        }

        if (ctx.containerNameCheck("Order")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Order") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            if (outbidFlow.cancellationSent()) return;
            List<Integer> slot = ctx.scanner().findContainer("Cancel Order");
            if (slot.isEmpty()) return;
            outbidFlow.sentCancellation(ctx.menu().containerId(), ctx.now());
            ctx.capital().funding("books", task.getBook().id(), null);
            ctx.actions().click(slot.getFirst(), false);
        }
    }

    /** The outbid order is gone: decide how the route continues with what it holds. */
    private static void replan(BookContext ctx, Task task) {
        // first we check if we have all the required books
        if (task.getAmountToOrder() == 0) {
            ctx.debug("[BazaarFlipper] OUTBID: no BUY order and amount requirement already met, going to ANVIL for " + task.getBook());
            task.resetReprices();
            task.setBookState(Task.BookState.ANVIL);
            return;
        }
        // we check if we can combine the books
        if (task.isCombinable()) {
            ctx.debug("[BazaarFlipper] OUTBID: task is combinable, scheduling SELECTED_COMBINE_STORE_BUYORDER for " + task.getBook());
            task.actionSchedule = Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
            task.setBookState(Task.BookState.SELECTED);
            return;
        }
        // if we cannot we check if we have any book in our inventory
        if (!task.bookList.isEmpty() && task.bookList.getFirst().location == 0) {
            ctx.debug("[BazaarFlipper] OUTBID: book found in inventory, scheduling SELECTED_STORE_BUYORDER for " + task.getBook());
            task.actionSchedule = Task.ActionSchedule.SELECTED_STORE_BUYORDER;
            task.setBookState(Task.BookState.SELECTED);
            return;
        }
        ctx.debug("[BazaarFlipper] OUTBID: re-placing buy order for " + task.getBook());
        ctx.activeTask(task);
        task.setBookState(Task.BookState.SELECTED);
    }
}
