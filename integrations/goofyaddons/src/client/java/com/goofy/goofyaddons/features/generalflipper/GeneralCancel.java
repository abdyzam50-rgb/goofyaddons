package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.Step;
import com.goofy.goofyaddons.features.generalflipper.GeneralPosition.Stage;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.google.gson.JsonObject;

import static com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.OWNER;

/**
 * Cancelling a buy order or sell offer, and verifying what the cancellation returned.
 *
 * <p>Fills can land at any point between opening an order's options and the server
 * processing the cancellation. Each outcome is accepted only on fresh inventory, order
 * and receipt evidence; a cancellation is never repeated because its result is unknown.
 */
final class GeneralCancel {
    void detail(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        if (ctx.view().title() == null) {
            ctx.command("managebazaarorders");
            return;
        }
        if (ctx.menu("Order")) {
            int cancel = ctx.find("Cancel Order", false);
            if (cancel >= 0) {
                active.cancelRequested = true;
                if (!ctx.save()) return;
                ctx.capital().funding(OWNER, active.item.id(), null);
                ctx.click(cancel);
                ctx.actions().closeMenu();
                ctx.transition(Step.VERIFY_CANCEL);
                return;
            }
        }
        // Partial buy claims leave the order in the list. Reopen its options
        // only after the expected inventory delta and the server's options hint.
        if (!trade.reopenedCancelOptions && ctx.ordersReady() && !ctx.ambiguousOrders()) {
            int order = ctx.findOrder(trade.selling);
            boolean mayOpen = order >= 0 && (trade.selling
                    ? OrderLore.canOpenSellOptionsAfterClaim(ctx.lore(order), trade.cancelSoldUnits, trade.claimedProceeds != null)
                    : OrderLore.canOpenOptionsAfterClaim(ctx.lore(order), trade.inventoryBefore, ctx.itemCount(active.item.id()), trade.expectedClaim));
            if (mayOpen) {
                if (!ctx.orderMatchesPosition(order)) return;
                ctx.services().event("INFO", "order.claim_then_open_options", java.util.Map.of("trade", active.tradeId,
                        "inventoryBefore", trade.inventoryBefore, "inventoryNow", ctx.itemCount(active.item.id()), "expectedClaim", trade.expectedClaim));
                trade.reopenedCancelOptions = true;
                ctx.click(order);
                return;
            }
        }
        // A fully filled order can be claimed directly without an order detail screen.
        if (!trade.selling && ctx.ordersReady() && ctx.itemCount(active.item.id()) > trade.inventoryBefore && ctx.findOrder(false) < 0) {
            // More units may fill between reading the order and claiming it. Use the
            // acknowledged whole position, not the earlier partial-fill estimate.
            trade.expectedClaim = active.quantity - trade.inventoryBefore;
            ctx.actions().closeMenu();
            ctx.transition(Step.VERIFY_CANCEL);
            return;
        }
        ctx.claim().retry(ctx);
    }

    void verify(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        var settings = ctx.settings();
        int count = ctx.itemCount(active.item.id());
        // A sell can complete after its options were opened. Cancellation then
        // returns no inventory: the verified full offer needs a coin claim.
        if (trade.selling && trade.cancelSoldUnits == 0 && ctx.ordersReady() && !ctx.ambiguousOrders()) {
            int order = ctx.findOrder(true);
            var fill = order < 0 ? null : OrderLore.fill(ctx.lore(order));
            if (fill != null && fill.filled() == active.quantity) {
                if (!ctx.orderMatchesPosition(order)) return;
                active.cancelRequested = false; active.settlementPending = true;
                trade.claimUnits = active.quantity; trade.claimPending = true; trade.receipt = false;
                trade.claimedProceeds = null; trade.expectedClaim = 0; trade.inventoryBefore = count;
                if (!ctx.save()) return;
                ctx.claim().arm(ctx, order); ctx.transition(Step.VERIFY_SALE); ctx.click(order); ctx.actions().closeMenu();
                ctx.services().event("INFO", "order.cancel_completed_sale", java.util.Map.of("trade", active.tradeId, "units", trade.claimUnits));
                return;
            }
        }
        // A fill can land after opening options but before the server processes
        // cancellation. Claim against fresh ownership/quantity observations,
        // then let CANCEL_DETAIL verify the delta before cancelling again.
        if (!trade.selling && trade.cancelNeedsClaim && ctx.ordersReady() && !ctx.ambiguousOrders()) {
            int order = ctx.findOrder(false);
            if (order >= 0) {
                if (!ctx.orderMatchesPosition(order)) return;
                int claimable = OrderLore.claimable(ctx.lore(order), count);
                if (claimable > 0) {
                    if (claimable > ctx.capacityFor(active.item.id()) || count + claimable > active.quantity) {
                        ctx.fail("Cannot safely claim newly filled units before cancellation; position retained."); return;
                    }
                    trade.inventoryBefore = count; trade.expectedClaim = claimable; trade.receipt = false;
                    trade.reopenedCancelOptions = false; trade.cancelNeedsClaim = false;
                    active.cancelRequested = false; active.confirmedCancelRefund = null;
                    if (!ctx.save()) return;
                    ctx.claim().arm(ctx, order); ctx.transition(Step.CANCEL_DETAIL); ctx.click(order);
                    ctx.services().event("INFO", "order.cancel_fill_claim", java.util.Map.of("trade", active.tradeId, "units", claimable));
                    return;
                }
            }
        }
        long elapsed = ctx.now() - ctx.stepSince();
        boolean itemsArrived = count > trade.inventoryBefore && count >= trade.inventoryBefore + trade.expectedClaim;
        if (trade.expectedClaim > 0 && count < trade.inventoryBefore + trade.expectedClaim) {
            ctx.command("managebazaarorders");
            if (ctx.ordersReady()) ctx.recheckOrders("cancel-inventory-not-visible");
            return;
        }
        if (!itemsArrived && !trade.receipt) {
            ctx.command("managebazaarorders");
            if (ctx.ordersReady()) ctx.recheckOrders("cancel-outcome-not-visible");
            return;
        }
        if (elapsed < 500) return;
        // Reopen orders to verify cancellation before creating a replacement.
        ctx.command("managebazaarorders");
        if (!ctx.ordersReady() || ctx.ambiguousOrders()) return;
        if (ctx.findOrder(trade.selling) >= 0) { ctx.recheckOrders("cancel-not-visible"); return; }
        if (trade.selling && trade.cancelSoldUnits > 0) {
            if (!ctx.recordSale(trade.cancelSoldUnits, trade.claimedProceeds)) return;
            trade.cancelSoldUnits = 0;
            active.settlementPending = false;
        }
        if (count == 0) {
            ctx.capital().funding(OWNER, active.item.id(), 0.0);
            long held = active.heldSince > 0 ? active.heldSince : active.placedAt;
            boolean agedOut = held > 0 && ctx.now() - held >= settings.maxHoldingSeconds * 1000L;
            if (!trade.selling && !agedOut && active.reprices < settings.maxReprices && ctx.freshQuotes()) {
                JsonObject product = ctx.products().getAsJsonObject(active.item.id());
                double bid = product == null ? -1 : GeneralCalculator.topPrice(product, "sell_summary");
                double net = ctx.currentAsk() * (1 - ctx.services().taxPercentage() / 100) - bid;
                if (bid > 0 && net * active.quantity >= settings.minProfitPerBatch
                        && net / bid * 100 >= settings.minMarginPercentage
                        && bid * active.quantity <= settings.maxCoinsPerItem
                        && ctx.capital().resize(OWNER, active.item.id(), bid * active.quantity, ctx.services().purse())) {
                    active.unitCost = bid;
                    active.reprices++;
                    active.stage = Stage.PLANNED;
                    active.submitted = false;
                    active.cancelRequested = false;
                    active.confirmedCancelRefund = null;
                    ctx.finishWork();
                    return;
                }
            }
            ctx.completePosition();
            return;
        }
        if (count > active.quantity) { ctx.fail("Inventory exceeds tracked quantity; manual reconciliation required."); return; }
        active.quantity = count;
        active.cancelRequested = false;
        active.confirmedCancelRefund = null;
        active.stage = Stage.INVENTORY;
        if (!trade.selling && !ctx.recordAcquisition()) return;
        if (trade.selling) active.reprices++;
        ctx.restoreFunding(active);
        ctx.finishWork();
    }

    /**
     * Notices that arrive before the item-name filter: the server's claim hint and a buy
     * cancellation refund. True when the notice was fully handled.
     */
    boolean serverNotice(GeneralContext ctx, String message) {
        var active = ctx.active();
        var trade = ctx.trade();
        var step = ctx.step();
        if (!trade.selling && step == Step.VERIFY_CANCEL && active.cancelRequested
                && com.goofy.goofyaddons.utils.Chat.strip(message).equals("[Bazaar] You have goods to claim on this order!")) {
            trade.cancelNeedsClaim = true;
            return true;
        }
        if (!trade.selling && active.stage == Stage.BUY_ORDER && active.purchasePriceKnown && active.cancelRequested
                && (step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL) && trade.expectedClaim == 0 && trade.inventoryBefore == 0) {
            Double refund = TradeReceipts.buyCancellationRefund(message, active.cost());
            if (refund != null) {
                trade.receipt = true; active.confirmedCancelRefund = refund;
                if (!ctx.save()) return true;
                ctx.services().event("INFO", "order.cancel_refund_verified", java.util.Map.of("trade", active.tradeId, "refund", refund));
            }
        }
        return false;
    }

    /** Receipts naming this item: proceeds of units sold before a cancellation, and the cancellation itself. */
    void itemNotice(GeneralContext ctx, String message) {
        var active = ctx.active();
        var trade = ctx.trade();
        var step = ctx.step();
        if (trade.selling && trade.cancelSoldUnits > 0 && (step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL)) {
            Double coins = TradeReceipts.saleProceeds(message, active.item.name(), trade.cancelSoldUnits);
            if (coins != null) trade.claimedProceeds = coins;
        }
        if ((step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL) && active.cancelRequested
                && TradingSafety.cancellationReceipt(message, active.item.name())) trade.receipt = true;
    }
}
