package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.BookSaleSettlement;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.features.transaction.RecoveryRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Selling a route's output book, replacing an undercut sell offer, and settling a
 * completed sale.
 *
 * <p>A live sell offer is cancelled before a replacement is entered, and the cancelled
 * book must return before anything else happens. A completed sale is recorded once, on
 * its receipt or the order's verified removal.
 */
final class BookSell {
    private com.goofy.goofyaddons.features.production.BazaarInstantSell instant;
    private Task instantTask;
    void reset(){instant=null;instantTask=null;}
    void sell(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.SELL);
        if (task == null) {
            ctx.debug("[BazaarFlipper] SELL: no task left in SELL, going to FETCHING");
            ctx.actions().closeMenu();
            ctx.state(State.FETCHING);
            return;
        }

        if(task.instaSell){instant(ctx,task);return;}
        openOrders(ctx);

        if (ordersOpen(ctx)) ctx.clock().start(ctx.delay());
        if (ordersOpen(ctx) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            List<Integer> slot = new ArrayList<>(ctx.scanner().findContainer("SELL " + output(task)));

            if (!slot.isEmpty() && !task.instaSell) {
                ctx.actions().click(slot.getFirst(), false);
                return;
            }

            slot.addAll(ctx.scanner().findLoreInv(output(task)));
            if (slot.isEmpty() && ctx.recheckBookOrders(task, "missing-sell-order")) return;

            if (!slot.isEmpty()) {
                ctx.actions().click(slot.getFirst(), false);
                return;
            } else {
                ctx.initSelfRecovery();
                return;
            }
        }

        if (!cancelOffer(ctx, task)) return;

        if (ctx.containerNameCheck(task.getBook().name())) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck(task.getBook().name()) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            if (task.instaSell) {
                ctx.safetyHalt("Instant book sales need manual confirmation; tracked book retained.");
                return;
            }
            ctx.navigationClick(16); return;
        }

        if (ctx.containerNameCheck("At what price are you selling")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("At what price are you selling") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            selectPrice(ctx, task, true);
            return;
        }

        confirm(ctx, task, "SELL: submitted sell order for ", "SELL");
    }

    private void instant(BookContext ctx,Task task){
        if(instantTask!=task){instant=null;instantTask=task;}
        var book=task.getBook();
        if(instant==null){
            var quotes=ctx.services().latestQuotes();if(quotes==null)return;
            Double value=com.goofy.goofyaddons.features.production.BazaarStrategy.ORDER_INSTANT.sell(quotes.getAsJsonObject("products").getAsJsonObject(book.getLevel(book.sellLevel())),1);
            if(value==null)return;
            instant=new com.goofy.goofyaddons.features.production.BazaarInstantSell(book.getLevel(book.sellLevel()),output(task),1,value*.97,value*1.03,reason->{
                ctx.expose(book.id());if(!ctx.checkpoint())throw new IllegalStateException("Book sale intent not saved");
            });
        }
        var result=instant.tick(ctx.menu(),ctx.actions(),ctx.services().purse(),ctx.now());
        switch(result){
            case SOLD -> {
                ctx.accounting().sell(task.getProfitTradeId(),"books",book.name(),task.getProfitTradeId()+":sale",book.getQtyAmount(book.level()),instant.proceeds());
                ctx.tasks().remove(task);ctx.resizeRetainedExtras(book,task.getReservedUnitCost());instant=null;
                ctx.checkpoint();ctx.state(State.IDLE);ctx.actions().closeMenu();
            }
            case BLOCKED,UNCERTAIN -> ctx.safetyHalt(instant.failure());
            default -> {}
        }
    }

    void replace(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.REPLACE_SELL);
        if (task == null) {
            ctx.debug("[BazaarFlipper] REPLACE_SELL: no task left in REPLACE_SELL, going to IDLE");
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }

        if(task.instaSell){instant(ctx,task);return;}
        openOrders(ctx);

        if (ordersOpen(ctx)) ctx.clock().start(ctx.delay());
        if (ordersOpen(ctx) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            List<Integer> slot = new ArrayList<>(ctx.scanner().findContainer("SELL " + output(task)));

            if (ctx.claim().pendingSale() == task) {
                settle(ctx, task, !slot.isEmpty());
                return;
            }

            if (!slot.isEmpty()) {
                var lore = ctx.menu().slot(slot.getFirst()).loreLines();
                com.goofy.goofyaddons.features.generalflipper.OrderLore.Fill fill = lore == null ? null
                        : com.goofy.goofyaddons.features.generalflipper.OrderLore.fill(String.join("\n", lore));
                if (fill != null && fill.filled() == fill.total() && fill.total() == 1) ctx.claim().beginSale(task, ctx.now());
                ctx.actions().click(slot.getFirst(), false);
                return;
            }

            slot.addAll(ctx.scanner().findLoreInv(output(task)));
            if (slot.isEmpty() && ctx.recheckBookOrders(task, "missing-sell-order")) return;

            if (!slot.isEmpty()) {
                ctx.actions().click(slot.getFirst(), false);
                return;
            }

            ctx.services().event("ERROR", "books.sale_claim_unconfirmed", java.util.Map.of(
                    "trade", task.getProfitTradeId(), "claimPending", false, "receiptSeen", false,
                    "context", ctx.services().diagnosticContext()));
            ctx.safetyHalt("Tracked book sell order/inventory missing without a matching claim; position retained.");
            return;
        }

        if (!cancelOffer(ctx, task)) return;

        if (ctx.containerNameCheck(task.getBook().name())) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck(task.getBook().name()) && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            ctx.navigationClick(16); return;
        }

        if (ctx.containerNameCheck("At what price are you selling")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("At what price are you selling") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            selectPrice(ctx, task, false);
            return;
        }

        confirm(ctx, task, "REPLACE_SELL: submitted replacement sell order for ", "REPLACE_SELL");
    }

    /** The clicked sale settles on its receipt or the order's removal; otherwise the route halts. */
    private void settle(BookContext ctx, Task task, boolean orderPresent) {
        var claim = ctx.claim();
        var settlement = BookSaleSettlement.check(orderPresent,
                !ctx.scanner().findLoreInv(output(task)).isEmpty(),
                claim.saleReceipt(), ctx.now() - claim.saleClaimAt(), RecoveryRules.RECEIPT_GRACE_MS);
        if (settlement == BookSaleSettlement.Result.COMPLETE) {
            complete(ctx, task);
            ctx.state(State.IDLE);
            ctx.actions().closeMenu();
        } else if (settlement == BookSaleSettlement.Result.UNCONFIRMED) {
            ctx.services().event("ERROR", "books.sale_claim_unconfirmed", java.util.Map.of(
                    "trade", task.getProfitTradeId(), "orderPresent", orderPresent,
                    "receiptSeen", claim.saleReceipt(), "context", ctx.services().diagnosticContext()));
            ctx.safetyHalt("Book sale claim did not settle; ownership retained without repeating the claim.");
        }
    }

    private static void complete(BookContext ctx, Task task) {
        ctx.accounting().sell(task.getProfitTradeId(), "books", task.getBook().name(),
                task.getProfitTradeId() + ":sale", task.getBook().getQtyAmount(task.getBook().level()), ctx.claim().saleProceeds());
        ctx.tasks().remove(task);
        ctx.resizeRetainedExtras(task.getBook(), task.getReservedUnitCost());
        ctx.claim().clearSale();
    }

    private static void selectPrice(BookContext ctx, Task task, boolean finishBuyMonitor) {
        double price = ctx.scanner().getUnitPrice(12);
        if (!ctx.bookPriceAllowed(task, price, true)) {
            ctx.safetyHalt("Book sale price or market data could not be verified; book retained."); return;
        }
        if (finishBuyMonitor) ctx.monitor().finish(task.getBook(), false);
        ctx.monitor().add(task.getBook(), price, true);
        ctx.placement().priceSelected(task, price, true, ctx.now());
        ctx.navigationClick(12);
    }

    /**
     * Cancels the live offer before a replacement; the returned book is verified by the engine.
     * False when the tick must end here: the cancel control is missing or the checkpoint failed.
     */
    private static boolean cancelOffer(BookContext ctx, Task task) {
        if (ctx.containerNameCheck("Order")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Order") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            List<Integer> slot = ctx.scanner().findContainer("Cancel Order");
            if (slot.isEmpty()) return false;
            if (!ctx.checkpoint()) return false;
            ctx.sellCancellation().start(task, ctx.menu().containerId(), ctx.now());
            ctx.actions().click(slot.getFirst(), false);
        }
        return true;
    }

    private static void confirm(BookContext ctx, Task task, String detail, String label) {
        if (ctx.containerNameCheck("Confirm")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Confirm") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            if (!ctx.placement().submit(ctx, task, true, Task.BookState.SELL_ORDER)) return;
            ctx.debug("[BazaarFlipper] " + detail + task.getBook());
            ctx.state(State.VERIFY_PLACEMENT);
            ctx.debug("[BazaarFlipper] " + label + ": TaskSize:" + ctx.tasks().size());
        }
    }

    private static void openOrders(BookContext ctx) {
        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command("managebazaarorders");
        }
    }

    private static boolean ordersOpen(BookContext ctx) {
        return ctx.screenOpen() && TradingSafety.ordersTitle(ctx.menuTitle());
    }

    private static String output(Task task) {
        return task.getBook().getRomanLevel(task.getBook().sellLevel());
    }
}
