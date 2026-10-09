package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.features.transaction.ProductIdentity;
import com.goofy.goofyaddons.features.transaction.RecoveryRules;

import java.util.List;

/**
 * Entering a buy order for a route's input book: search, product page, amount, price and
 * confirmation. Placement itself is verified by {@link BookPlacement}.
 */
final class BookBuy {
    private long signSubmittedAt;
    private int signRestarts;

    void reset() { signSubmittedAt = 0; signRestarts = 0; }

    /** Forgets a written amount whose menu is being reopened. */
    void menuReopened() { signSubmittedAt = 0; }

    /**
     * After the amount sign is written, waits for the price or confirmation menu. Restarts
     * navigation twice when it never appears, then halts. True when the tick may continue.
     */
    boolean signProgress(BookContext ctx) {
        if (signSubmittedAt <= 0 || ctx.state() != State.BAZAAR_NAVIGATION) return true;
        if (ctx.containerNameCheck("How much do you want to pay") || ctx.containerNameCheck("Confirm")) {
            signSubmittedAt = 0; signRestarts = 0;
            return true;
        }
        if (ctx.now() - signSubmittedAt >= RecoveryRules.INPUT_RESTART_MS) {
            if (signRestarts >= RecoveryRules.MAX_INPUT_RESTARTS) { ctx.safetyHalt("Book quantity input did not advance after two navigation restarts; order retained."); return false; }
            signRestarts++; signSubmittedAt = 0; ctx.placement().abandonConfirmation();
            ctx.actions().closeMenu(); ctx.clock().stop();
            ctx.services().event("WARN", "books.input_navigation_restart", java.util.Map.of("trade", ctx.activeTask().getProfitTradeId(), "attempt", signRestarts));
        }
        return false;
    }

    void tick(BookContext ctx) {
        Task task = ctx.activeTask();
        String prerequisite = com.goofy.goofyaddons.features.access.RouteRequirements.book(task.getBook().id(), ctx.services().observedSkills());
        if (prerequisite != null) { ctx.skipBookRequirement(task, prerequisite); return; }
        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command("bz " + task.getBook().name().replace("Ultimate", ""));
        }

        if (ctx.containerNameCheck("Bazaar")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Bazaar") && ctx.scanner().isMenuLoaded(53) && ctx.clock().shouldFire()) {
            List<Integer> slots = ctx.scanner().findContainer(task.getBook().getRomanLevel(task.getBook().level()));
            if (slots.isEmpty()) return;
            ctx.navigationClick(slots.getFirst()); return;
        }

        boolean productPage = productPage(ctx, task);
        if (productPage) ctx.clock().start(ctx.delay());
        if (productPage && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            if (task.instaBuy) {
                ctx.safetyHalt("Instant book purchases need manual confirmation; order retained."); return;
            }
            // The tick's captured menu, like every other decision in this engine.
            var button = ctx.menu().slot(15);
            if (button != null) {
                String reason = com.goofy.goofyaddons.features.access.ActionRequirements.blocked(button.hoverName() + "\n" + button.lore(),
                        ctx.services().observedSkills(), com.goofy.goofyaddons.features.access.ActionRequirements.Action.BUY);
                if (reason != null) { ctx.skipBookRequirement(task, reason); return; }
            }
            ctx.navigationClick(task.instaBuy ? 10 : 15); return;
        }

        if (ctx.containerNameCheck("How many do you want")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("How many do you want") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            ctx.navigationClick(16); return;
        }

        if (ctx.signOpen()) ctx.clock().start(ctx.delay());
        if (ctx.signOpen() && ctx.clock().shouldFire()) {
            writeAmount(ctx, task);
        }

        if (ctx.containerNameCheck("How much do you want to pay")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("How much do you want to pay") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            double purse = ctx.services().purse();
            if (!ctx.purchasePurseReady(purse)) return;
            double unitPrice = ctx.scanner().getUnitPrice(12);
            double requiredCoins = unitPrice * task.getAmountToOrder();
            double fullCost = Math.max(unitPrice, task.getReservedUnitCost())
                    * task.getBook().getQtyAmount(task.getBook().level());
            if (!ctx.bookPriceAllowed(task, unitPrice, false)) {
                ctx.safetyHalt("Book buy price no longer meets the minimum net profit."); return;
            }
            if (!Double.isFinite(requiredCoins) || requiredCoins <= 0 || unitPrice <= 0) {
                ctx.safetyHalt("Book purchase cost could not be verified."); return;
            }
            if (!ctx.capital().resize("books", task.getBook().id(),
                    Math.max(fullCost, ctx.capital().cost("books", task.getBook().id())),
                    requiredCoins, purse)) {
                ctx.deferBookPurchase(task, "insufficient-spendable-capital"); return;
            }
            task.setReservedUnitCost(Math.max(unitPrice, task.getReservedUnitCost()));
            if (!ctx.checkpoint()) return;
            ctx.monitor().add(task.getBook(), unitPrice, false);
            ctx.placement().priceSelected(task, unitPrice, false, ctx.now());
            ctx.navigationClick(12); return;
        }

        if (ctx.containerNameCheck("Confirm")) ctx.clock().start(ctx.delay());
        if (ctx.containerNameCheck("Confirm") && ctx.scanner().isMenuLoaded(35) && ctx.clock().shouldFire()) {
            if (!ctx.placement().submit(ctx, task, false, null)) return;
            // first we check if the order was an insta buy
            if (task.instaBuy) {
                ctx.debug("[BazaarFlipper] BAZAAR_NAVIGATION: insta bought " + task.getBook() + ", going to " + (task.bookList.getLast().location != 0 ? "ANVIL" : "COMBINE"));
                task.setBookState(task.bookList.getLast().location != 0 ? Task.BookState.ANVIL : Task.BookState.COMBINE);
                return;
            }

            ctx.debug("[BazaarFlipper] BAZAAR_NAVIGATION: submitted buy order for " + task.getBook() + ", schedule was " + task.actionSchedule);
            task.recordPlacement(ctx.now());
            switch (task.actionSchedule) {
                case SELECTED_COMBINE_STORE_BUYORDER -> ctx.placement().nextState(Task.BookState.ANVIL);
                case SELECTED_STORE_BUYORDER -> ctx.placement().nextState(Task.BookState.STORE);
                case NONE -> ctx.placement().nextState(Task.BookState.IN_BUY_ORDER);
                default -> { }
            }
            ctx.state(State.VERIFY_PLACEMENT);
        }
    }

    /**
     * The product page for this route's input book. A full title is accepted as before; a
     * truncated breadcrumb only when the icon and the buy control both name the exact book,
     * and only on a product-sized page, never the larger search results.
     */
    private static boolean productPage(BookContext ctx, Task task) {
        if (ctx.containerNameCheck(task.getBook().name())) return true;
        var menu = ctx.menu();
        return ctx.screenOpen() && menu != null && menu.containerEnd() == 36
                && ProductIdentity.truncatedProductPage(menu, task.getBook().getRomanLevel(task.getBook().level()), 15);
    }

    private void writeAmount(BookContext ctx, Task task) {
        String amountToOrder = String.valueOf(task.getAmountToOrder());
        if (!ctx.signOpen()) return;
        try {
            if (!ctx.actions().writeSign(amountToOrder)) { ctx.safetyHalt("Could not write the book order amount onto the sign; order retained."); return; }
            signSubmittedAt = ctx.now();
        } catch (Exception failure) {
            ctx.services().failure("books.sign_write_failed", failure);
            ctx.safetyHalt("Writing the book order amount onto the sign failed; order retained.");
        }
    }
}
