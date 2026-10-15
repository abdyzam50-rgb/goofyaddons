package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.Step;
import com.goofy.goofyaddons.features.generalflipper.GeneralPosition.Stage;

import static com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.OWNER;

/**
 * Reads a retained position's orders and inventory and decides which operation runs next:
 * enter a buy, claim and cancel a buy, claim a completed sale, cancel a sell offer for
 * repricing, or enter a sell offer for acquired stock.
 *
 * <p>It never changes ownership on its own beyond adopting what the orders menu shows.
 */
final class GeneralOrderReview {
    void inspect(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        var settings = ctx.settings();
        var capital = ctx.capital();
        var services = ctx.services();
        if (!ctx.ordersReady() || ctx.ambiguousOrders()) return;
        int buy = ctx.findOrder(false);
        int sell = ctx.findOrder(true);
        int inventory = active.acquiredInventory(ctx.itemCount(active.item.id()));
        if(inventory<0){ctx.fail("Previously held production ingredients changed; manual reconciliation required.");return;}
        if(ctx.recoveringProduction() && !active.productionBuy) {
            if(active.stage!=Stage.INVENTORY || active.submitted || active.settlementPending
                    || buy>=0 || sell>=0 || inventory!=active.quantity) {
                ctx.fail("Unsold output recovery needs the exact tracked inventory and no outstanding order; evidence retained.");return;
            }
            // Only release an unsubmitted output after live orders/inventory verification.
            // The parent keeps its cost basis; this is neither a sale nor an acquisition.
            active.completed=true;
            if(ctx.save())ctx.finishWork();
            return;
        }
        if (buy < 0 && sell < 0 && active.stage != Stage.PLANNED && (active.stage != Stage.INVENTORY || inventory == 0)
                && ctx.recheckOrders("tracked-order-absent")) return;
        if (buy < 0 && sell < 0 && inventory == 0 && active.stage == Stage.BUY_ORDER && active.cancelRequested
                && active.confirmedCancelRefund != null && Double.isFinite(active.confirmedCancelRefund)
                && Math.abs(active.confirmedCancelRefund - active.cost()) <= 0.51) {
            trade.selling = false; trade.receipt = true; trade.inventoryBefore = ctx.itemCount(active.item.id()); trade.expectedClaim = 0;
            ctx.transition(Step.VERIFY_CANCEL); return;
        }
        OrderLore.Fill saleFill = sell < 0 ? null : OrderLore.fill(ctx.lore(sell));
        boolean fullySold = saleFill != null && saleFill.filled() == active.quantity;
        long held = active.heldSince > 0 ? active.heldSince : active.placedAt;
        double netExit = ctx.freshQuotes() ? ctx.currentAsk() * (1 - services.taxPercentage() / 100) : -1;
        boolean holdingLimitHit = !services.productionOnly() && active.stage != Stage.PLANNED && !fullySold
                && TradingSafety.holdingLimit(held, ctx.now(), settings.maxHoldingSeconds,
                    active.unitCost, netExit, settings.maxDrawdownPercentage);
        // Cancel outstanding buys before checking whether acquired stock may be sold.
        // Age requests an exit; it must not prevent claiming/refunding that exit.
        if (holdingLimitHit && buy < 0 && TradingSafety.holdingLimit(0, ctx.now(),
                settings.maxHoldingSeconds, active.unitCost, netExit, settings.maxDrawdownPercentage)) {
            ctx.fail("Drawdown limit reached for " + active.item.name() + ". GeneralPosition retained for review."); return;
        }
        if (active.stage == Stage.PLANNED && !active.submitted) {
            if (buy >= 0 || sell >= 0 || inventory > 0) {
                String reason="Pre-existing order/inventory for " + active.item.name();
                if(!services.productionOnly())ctx.skipUnownedConflict(reason);
                else ctx.fail(reason+"; reconcile the retained craft position before retrying.");
                return;
            }
            if (services.excludedProducts().contains(active.item.id())) {
                ctx.skipUnavailable("Excluded by account/category access filter"); return;
            }
            trade.selling = false;
            ctx.actions().closeMenu();
            ctx.transition(Step.OPEN_PRODUCT);
            return;
        }
        if (buy >= 0 && sell >= 0) { ctx.fail("Both buy and sell orders exist for one tracked item; manual reconciliation required."); return; }
        if (buy >= 0) { reviewBuy(ctx, buy, inventory, holdingLimitHit); return; }
        if (sell >= 0) { reviewSell(ctx, sell, inventory); return; }
        if (inventory > 0) {
            if (inventory > active.quantity) { ctx.fail("Inventory exceeds tracked quantity; manual reconciliation required."); return; }
            active.quantity = inventory;
            active.stage = Stage.INVENTORY;
            active.cancelRequested = false;
            if (!ctx.recordAcquisition()) return;
            ctx.restoreFunding(active);
            if(services.productionOnly() && active.productionBuy){ctx.finishWork();return;}
            if(services.productionOnly() && (!ctx.freshQuotes() || !ctx.saleAllowed(ctx.currentAsk()))) {
                ctx.finishWork();return; // Retain verified stock while a fresh profitable price is unavailable.
            }
            trade.selling = true;
            ctx.actions().closeMenu();
            ctx.transition(Step.OPEN_PRODUCT);
        } else if (active.stage == Stage.SELL_ORDER && ctx.claimedOutside() instanceof Double coins) {
            // Claimed by hand while trading was stopped: the receipt names this offer's item,
            // units and price, so record the sale rather than halting on its absence.
            ctx.services().event("INFO", "order.sale_claimed_outside", java.util.Map.of("trade", active.tradeId == null ? "none" : active.tradeId,
                    "item", active.item.id(), "units", active.quantity, "proceeds", coins));
            if (!ctx.recordSale(active.quantity, coins)) return;
            ctx.completePosition();
        } else if (active.stage == Stage.SELL_ORDER || active.cancelRequested) {
            ctx.fail("Tracked sell/cancel position is absent; ownership is uncertain. GeneralPosition retained for manual reconciliation.");
        } else if (ctx.now() - active.placedAt > 5000) {
            ctx.fail("Tracked buy order and inventory are both missing; manual reconciliation required.");
        }
    }

    /** A live buy order: keep waiting, or claim what filled and cancel the rest. */
    private void reviewBuy(GeneralContext ctx, int buy, int inventory, boolean holdingLimitHit) {
        var active = ctx.active();
        var trade = ctx.trade();
        if (!ctx.orderMatchesPosition(buy)) return;
        active.stage = Stage.BUY_ORDER;
        if (active.heldSince == 0) active.heldSince = active.placedAt;
        ctx.capital().purchased(OWNER, active.item.id());
        int claimable = OrderLore.claimable(ctx.lore(buy), inventory);
        boolean cancel = holdingLimitHit || active.cancelRequested || claimable + inventory >= active.quantity || inventory > 0 || ctx.shouldReprice(false)
                || ctx.now() - active.placedAt >= ctx.settings().orderTimeoutSeconds * 1000L;
        if (!cancel) { ctx.finishWork(); return; }
        trade.selling = false;
        trade.inventoryBefore = ctx.itemCount(active.item.id());
        trade.expectedClaim = claimable;
        int room = ctx.capacityFor(active.item.id());
        if (claimable > room && active.productionBuy && ctx.services().productionStorage()) {
            // A storage-backed craft claims what fits, stores it, then claims the rest of the
            // same order. Less than a stack of room means storing comes first.
            if (room < Math.min(claimable, 64)) {
                active.productionAwaitingStorage = true;
                if (!ctx.save()) return;
                ctx.services().event("INFO", "order.claim_needs_storage", java.util.Map.of("trade", active.tradeId,
                        "item", active.item.id(), "claimable", claimable, "room", room, "stored", active.productionStoredUnits));
                ctx.actions().closeMenu();
                ctx.finishWork();
                return;
            }
            // The server fills the whole inventory, including the slots the engine keeps spare.
            trade.expectedClaim = Math.min(claimable, ctx.roomFor(active.item.id()));
            trade.partialClaim = true;
            trade.partialCount = -1;
            trade.receipt = false;
            ctx.services().event("INFO", "order.partial_claim", java.util.Map.of("trade", active.tradeId,
                    "item", active.item.id(), "claimable", claimable, "room", room, "stored", active.productionStoredUnits));
            ctx.claim().arm(ctx, buy);
            ctx.transition(Step.PARTIAL_CLAIM);
            ctx.click(buy);
            return;
        }
        if (claimable > room) {
            ctx.fail("Insufficient inventory space to claim the tracked buy order."); return;
        }
        trade.receipt = false;
        trade.reopenedCancelOptions = false;
        ctx.claim().arm(ctx, buy);
        ctx.transition(Step.CANCEL_DETAIL);
        ctx.click(buy);
    }

    /** A live sell offer: claim a completed sale, cancel to reprice, or keep waiting. */
    private void reviewSell(GeneralContext ctx, int sell, int inventory) {
        var active = ctx.active();
        var trade = ctx.trade();
        var settings = ctx.settings();
        if (!ctx.orderMatchesPosition(sell)) return;
        active.stage = Stage.SELL_ORDER;
        OrderLore.Fill filled = OrderLore.fill(ctx.lore(sell));
        int soldUnits = filled == null ? -1 : filled.filled();
        if (soldUnits >= active.quantity) {
            trade.receipt = false;
            trade.claimPending = true;
            active.settlementPending = true;
            trade.claimUnits = active.quantity;
            trade.inventoryBefore = inventory;
            trade.claimedProceeds = null;
            if (!ctx.save()) return;
            ctx.claim().arm(ctx, sell);
            ctx.transition(Step.VERIFY_SALE);
            ctx.click(sell); // Claim completed sale, never sell arbitrary inventory.
            ctx.actions().closeMenu();
            return;
        }
        if (soldUnits >= 0 && ctx.shouldReprice(true) && (ctx.services().productionOnly() || active.reprices < settings.maxReprices) && ctx.freshQuotes()
                && ctx.saleAllowed(ctx.currentAsk())) {
            ctx.services().event("INFO","order.sell_reprice",java.util.Map.of("trade",active.tradeId,"parent",active.productionParent==null?"none":active.productionParent,"oldPrice",active.sellPrice,"newPrice",ctx.currentAsk(),"remaining",active.quantity-soldUnits));
            trade.selling = true;
            trade.inventoryBefore = inventory;
            trade.expectedClaim = Math.max(0, active.quantity - soldUnits);
            trade.cancelSoldUnits = soldUnits;
            trade.claimedProceeds = null;
            if (trade.expectedClaim > ctx.capacityFor(active.item.id())) {
                ctx.fail("Insufficient inventory space to cancel the sell offer."); return;
            }
            trade.receipt = false;
            trade.reopenedCancelOptions = false;
            active.settlementPending = soldUnits > 0;
            if (!ctx.save()) return;
            ctx.claim().arm(ctx, sell);
            ctx.transition(Step.CANCEL_DETAIL);
            ctx.click(sell);
            return;
        }
        ctx.finishWork();
    }
}
