package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;

import java.util.List;

/**
 * A read-only look at a long-untouched order, so a missed fill notice cannot leave a route
 * waiting forever. It never clicks a slot; it only decides whether there is work.
 */
final class BookOrderCheck {
    void tick(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.VERIFY_ORDER);
        if (task == null) {
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }

        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command("managebazaarorders");
        }

        if (ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle())) ctx.clock().start(ctx.delay());
        if (ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle())
                && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            boolean sale = task.awaitingSale();
            String item = task.getBook().getRomanLevel(sale ? task.getBook().sellLevel() : task.getBook().level());
            List<Integer> slot = ctx.scanner().findContainer((sale ? "SELL " : "BUY ") + item);
            if (slot.isEmpty()) {
                if (ctx.recheckBookOrders(task, "recheck-order-absent")) return;
                ctx.safetyHalt("Tracked book order is no longer listed; reconcile before continuing.");
                return;
            }
            var lore = ctx.menu().slot(slot.getFirst()).loreLines();
            String text = lore == null ? "" : String.join("\n", lore);
            var fill = com.goofy.goofyaddons.features.generalflipper.OrderLore.fill(text);
            if (fill == null) {
                if (ctx.recheckBookOrders(task, "recheck-fill-unreadable")) return;
                ctx.safetyHalt("Tracked book order progress is unreadable; reconcile before continuing.");
                return;
            }
            ctx.services().event("INFO", "books.order_rechecked", java.util.Map.of("trade", task.getProfitTradeId(),
                    "item", item, "selling", sale, "filled", fill.filled(), "total", fill.total()));
            task.markOrderObserved(ctx.now());
            if (fill.filled() > 0) {
                ctx.debug("[BazaarFlipper] VERIFY_ORDER: " + item + " shows " + fill.filled() + "/" + fill.total() + ", routing to collect");
                task.setBookState(sale ? Task.BookState.REPLACE_SELL : Task.BookState.OUTBID);
            } else {
                ctx.debug("[BazaarFlipper] VERIFY_ORDER: " + item + " still unfilled, continuing to wait");
                task.setBookState(sale ? Task.BookState.SELL_ORDER : Task.BookState.IN_BUY_ORDER);
            }
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
        }
    }
}
