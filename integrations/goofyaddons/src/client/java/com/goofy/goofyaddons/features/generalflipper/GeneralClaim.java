package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.bookflipper.helper.BookActionRetry;

/**
 * Claiming goods or coins from an order in the orders menu.
 *
 * <p>Only the original claim may be repeated, and only while every observation still says
 * it was not applied. Placement and cancellation confirmations are never replayed on the
 * basis of a missing acknowledgement.
 */
final class GeneralClaim {
    private final BookActionRetry retry = new BookActionRetry();
    private String title, lore;
    private int slot;

    void reset() { retry.reset(); }
    boolean coolingDown(long now) { return retry.coolingDown(now); }
    void slowdown(long now) { retry.slowdown(now); }

    /** Remembers exactly what was clicked, so a retry can prove nothing changed since. */
    void arm(GeneralContext ctx, int order) {
        slot = order;
        title = ctx.view().title();
        lore = ctx.lore(order);
        retry.sent(order, false, ctx.now());
    }

    void retry(GeneralContext ctx) {
        var trade = ctx.trade();
        boolean unchanged = java.util.Objects.equals(title, ctx.view().title())
                && ctx.findOrder(trade.selling) == slot && java.util.Objects.equals(lore, ctx.lore(slot))
                && ctx.itemCount(ctx.active().item.id()) == trade.inventoryBefore && !trade.receipt && trade.claimedProceeds == null;
        if (retry.retry(ctx.view(), unchanged, ctx.actions(), ctx.now())) {
            ctx.services().event("WARN", "general.claim_retried", java.util.Map.of(
                    "trade", ctx.active().tradeId, "attempt", retry.retries(), "slot", slot));
        }
    }
}
