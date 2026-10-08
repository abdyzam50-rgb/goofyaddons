package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.profit.TradeReceipts;

/**
 * The sell side: exit limits when entering a sell offer, and settling a completed sale.
 *
 * <p>Profit and margin are entry filters. Existing stock exits at a readable market price
 * within the drawdown guard; age cannot block an exit.
 */
final class GeneralSell implements GeneralOrderEntry.Side {
    @Override public boolean selling() { return true; }
    @Override public boolean confirmationPreconditions(GeneralContext ctx) { return true; }
    @Override public double expectedPrice(GeneralPosition position) { return position.sellPrice; }
    @Override public double confirmationPurse(GeneralContext ctx) { return -1; }
    @Override public boolean confirmationPurseReady(GeneralContext ctx, double purse) { return true; }

    @Override public boolean confirmationAllowed(GeneralContext ctx, double expectedPrice, double purse) {
        var active = ctx.active();
        if (ctx.itemCount(active.item.id()) != active.quantity || !ctx.saleAllowed(expectedPrice)) {
            ctx.fail("Inventory or holding limit changed before sale confirmation; no order submitted."); return false;
        }
        return true;
    }

    @Override public boolean acceptPrice(GeneralContext ctx, double price) {
        if (!ctx.saleAllowed(price)) {
            ctx.fail("Drawdown limit reached at the live sell price; position retained."); return false;
        }
        ctx.active().sellPrice = price;
        return true;
    }

    @Override public void placementVisible(GeneralContext ctx) {}
    @Override public boolean filledBeforeVisible(GeneralContext ctx) { return false; }

    /** A completed sale settles only on its receipt, the order's removal and an unchanged inventory. */
    void settle(GeneralContext ctx) {
        var trade = ctx.trade();
        ctx.command("managebazaarorders");
        if (!ctx.ordersReady() || ctx.ambiguousOrders()) return;
        if (TradingSafety.saleComplete(trade.claimPending, trade.receipt, ctx.findOrder(true) < 0, ctx.itemCount(ctx.active().item.id()))) {
            if (!ctx.recordSale(trade.claimUnits, trade.claimedProceeds)) return;
            ctx.completePosition();
        } else if (ctx.now() - ctx.stepSince() < 10000) {
            ctx.claim().retry(ctx);
        } else {
            ctx.fail("Sale claim was not confirmed by its receipt and inventory/order updates; position retained.");
        }
    }

    /** Coins named by the receipt for the sale this visit clicked. */
    void receipt(GeneralContext ctx, String message) {
        var trade = ctx.trade();
        if (ctx.step() == GeneralFlipper.Step.VERIFY_SALE && trade.claimPending) {
            Double coins = TradeReceipts.saleProceeds(message, ctx.active().item.name(), trade.claimUnits);
            if (coins != null) { trade.receipt = true; trade.claimedProceeds = coins; }
        }
    }
}
