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
 * and receipt evidence; an unknown result never authorizes another cancellation.
 * An explicit rejection can retry up to three attempts after a fresh exact owned-order
 * observation. A silent click can reopen twice, only when fresh evidence proves that
 * the original owned order is still live and inventory has not changed.
 * Late successful receipts take precedence throughout that retry chain.
 */
final class GeneralCancel {
    private static final String CANCEL_REJECTED="[Bazaar] There was an error cancelling your order!";
    private static final long REJECTION_COOLDOWN_MS=2000, ACK_WAIT_MS=5000;
    private String attemptedTrade;
    private boolean cancelIssued,rejected;
    private int rejections,cancelContainer;
    private long rejectedAt,cancelSentAt;
    private int missingAckRetries;

    void begin(GeneralPosition position) {
        if(!java.util.Objects.equals(attemptedTrade,position.tradeId)){rejections=0;missingAckRetries=0;}
        attemptedTrade=position.tradeId;cancelIssued=false;rejected=false;
    }
    java.util.Map<String,Object> diagnosticState() {
        return java.util.Map.of("issued",cancelIssued,"rejected",rejected,"rejections",rejections,
            "container",cancelContainer,"rejectedAt",rejectedAt,"sentAt",cancelSentAt,"missingAckRetries",missingAckRetries);
    }
    private void confirmed(){cancelIssued=false;rejected=false;rejections=0;missingAckRetries=0;}

    /** A late receipt must reach verification before reviewing the retry can clear it. */
    boolean resumeVerification(GeneralContext ctx) {
        if(ctx.active().cancelRequested && ctx.trade().receipt) {
            ctx.transition(Step.VERIFY_CANCEL);return true;
        }
        return false;
    }

    void detail(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        if(resumeVerification(ctx))return;
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
                cancelIssued=true;rejected=false;cancelContainer=ctx.view().containerId();cancelSentAt=ctx.now();
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
            trade.expectedClaim = active.quantity - active.acquiredInventory(trade.inventoryBefore);
            ctx.actions().closeMenu();
            ctx.transition(Step.VERIFY_CANCEL);
            return;
        }
        ctx.claim().retry(ctx);
    }

    /**
     * A buy claim larger than the inventory: the server claims what fits. Accept an arrival
     * no larger than the room, once the count settles, then wait for the run to store it.
     */
    void partial(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        int count = ctx.itemCount(active.item.id());
        int gained = count - trade.inventoryBefore;
        if (gained < 0) { ctx.fail("Inventory fell during a partial buy claim; manual reconciliation required."); return; }
        if (gained > trade.expectedClaim || active.acquiredInventory(count) > active.quantity) {
            ctx.fail("A partial buy claim returned more than the inventory room; position retained."); return;
        }
        if (gained == 0) { ctx.claim().retry(ctx); return; }
        // The server can deliver one claim over several inventory updates.
        if (count != trade.partialCount) { trade.partialCount = count; trade.partialSince = ctx.now(); return; }
        if (ctx.now() - trade.partialSince < 750) return;
        active.productionAwaitingStorage = true;
        if (!ctx.save()) return;
        ctx.services().event("INFO", "order.partial_claim_verified", java.util.Map.of("trade", active.tradeId,
                "item", active.item.id(), "units", gained, "acquired", active.acquiredInventory(count), "quantity", active.quantity));
        ctx.actions().closeMenu();
        ctx.finishWork();
    }

    void verify(GeneralContext ctx) {
        var active = ctx.active();
        var trade = ctx.trade();
        var settings = ctx.settings();
        int count = ctx.itemCount(active.item.id());
        int acquired=active.acquiredInventory(count);
        if(acquired<0){ctx.fail("Previously held production ingredients changed; manual reconciliation required.");return;}
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
                int claimable = OrderLore.claimable(ctx.lore(order), acquired);
                if (claimable > 0) {
                    if (claimable > ctx.capacityFor(active.item.id()) || acquired + claimable > active.quantity) {
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
        if(rejected && !trade.receipt) {
            retryObservedOrder(ctx,count,true);
            return;
        }
        if(cancelIssued && !trade.receipt && count==trade.inventoryBefore && ctx.now()-cancelSentAt>=ACK_WAIT_MS) {
            retryObservedOrder(ctx,count,false);
            return;
        }
        if(trade.receipt)rejected=false; // A delayed successful receipt takes precedence over an earlier error.
        long elapsed = ctx.now() - ctx.stepSince();
        boolean itemsArrived = com.goofy.goofyaddons.features.transaction.ClaimEvidence.arrived(trade.inventoryBefore, trade.expectedClaim, count);
        if (com.goofy.goofyaddons.features.transaction.ClaimEvidence.outstanding(trade.inventoryBefore, trade.expectedClaim, count)) {
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
        confirmed();
        if (trade.selling && trade.cancelSoldUnits > 0) {
            if (!ctx.recordSale(trade.cancelSoldUnits, trade.claimedProceeds)) return;
            trade.cancelSoldUnits = 0;
            active.settlementPending = false;
        }
        if (acquired == 0) {
            ctx.capital().funding(OWNER, active.item.id(), 0.0);
            long held = active.heldSince > 0 ? active.heldSince : active.placedAt;
            boolean agedOut = held > 0 && ctx.now() - held >= settings.maxHoldingSeconds * 1000L;
            if (!ctx.services().productionOnly() && !trade.selling && !agedOut && active.reprices < settings.maxReprices && ctx.freshQuotes()) {
                JsonObject product = ctx.products().getAsJsonObject(active.item.id());
                double bid = product == null ? -1 : com.goofy.goofyaddons.features.market.BazaarQuotes.bestBid(product);
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
        if (acquired > active.quantity) { ctx.fail("Inventory exceeds tracked quantity; manual reconciliation required."); return; }
        active.quantity = acquired;
        active.cancelRequested = false;
        active.confirmedCancelRefund = null;
        active.stage = Stage.INVENTORY;
        if (!trade.selling && !ctx.recordAcquisition()) return;
        if (trade.selling) active.reprices++;
        ctx.restoreFunding(active);
        ctx.finishWork();
    }

    /** Retry only after fresh exact ownership evidence proves the original order is still live. */
    private void retryObservedOrder(GeneralContext ctx,int inventory,boolean explicitRejection) {
        var active=ctx.active();var trade=ctx.trade();
        String reason=explicitRejection?"cancel-rejection":"cancel-unacknowledged";
        if(explicitRejection && ctx.now()-rejectedAt<REJECTION_COOLDOWN_MS)return;
        if(!trade.selling && com.goofy.goofyaddons.features.transaction.ClaimEvidence.outstanding(trade.inventoryBefore,trade.expectedClaim,inventory)) {
            ctx.command("managebazaarorders");
            if(ctx.ordersReady())ctx.recheckOrders(reason+"-claim-pending");
            return;
        }
        ctx.command("managebazaarorders");
        if(!ctx.ordersReady() || ctx.ambiguousOrders())return;
        if(ctx.view().containerId()==cancelContainer) {ctx.recheckOrders(reason+"-stale-menu");return;}
        if(ctx.findOrder(!trade.selling)>=0) {ctx.fail("Both buy and sell orders exist during cancellation recovery; position retained.");return;}
        int order=ctx.findOrder(trade.selling);
        if(order<0) {
            // No rejection authorizes inventing a refund or assuming a disappeared order was cancelled.
            ctx.recheckOrders(reason+"-order-absent");return;
        }
        if(!ctx.orderMatchesPosition(order))return;
        double price=ctx.unitPrice(order),expected=trade.selling?active.sellPrice:active.unitCost;
        if(price<=0) {ctx.recheckOrders(reason+"-price-unreadable");return;}
        if(Math.abs(price-expected)>.051) {ctx.fail("Order price differs during cancellation recovery; position retained.");return;}
        var fill=OrderLore.fill(ctx.lore(order));
        if(fill==null){ctx.recheckOrders(reason+"-fill-unreadable");return;}
        if(trade.selling && trade.cancelSoldUnits>0 && (trade.claimedProceeds==null || fill.filled()!=trade.cancelSoldUnits)) {
            ctx.fail("Sold units changed during cancellation recovery; retained sale receipts and position for review.");return;
        }
        if(trade.selling && trade.cancelSoldUnits>0
                && !OrderLore.canOpenSellOptionsAfterClaim(ctx.lore(order),trade.cancelSoldUnits,true)) {
            ctx.recheckOrders(reason+"-sale-claim-pending");return;
        }
        if(!explicitRejection) {
            if(missingAckRetries>=2) {ctx.fail("Cancellation remained unacknowledged after two verified menu reopens; tracked order retained.");return;}
            missingAckRetries++;
        }
        ctx.services().event("WARN",explicitRejection?"order.cancel_rejection_retry":"order.cancel_unacknowledged_retry",java.util.Map.of("trade",active.tradeId,
            "attempt",explicitRejection?rejections+1:missingAckRetries,"side",trade.selling?"SELL":"BUY","unitPrice",price,"units",active.quantity,
            "filled",fill.filled(),"container",ctx.view().containerId()));
        rejected=false;cancelIssued=false;
        ctx.claim().reset();
        if(trade.selling) {
            // Keep any confirmed partial-sale proceeds; reviewing the offer again would discard them.
            if(trade.cancelSoldUnits==0 && fill.filled()>0) {
                trade.cancelSoldUnits=fill.filled();trade.claimedProceeds=null;trade.receipt=false;
                trade.inventoryBefore=inventory;trade.expectedClaim=active.quantity-fill.filled();
                if(trade.expectedClaim>ctx.capacityFor(active.item.id())) {
                    ctx.fail("Insufficient inventory space during cancellation recovery; sell offer retained.");return;
                }
                active.settlementPending=true;if(!ctx.save())return;
            }
            trade.reopenedCancelOptions=false;
            ctx.claim().arm(ctx,order);ctx.transition(Step.CANCEL_DETAIL);ctx.click(order);
        } else {
            // Fresh review claims fills that landed during the rejected cancellation before reopening options.
            ctx.transition(Step.ORDERS);
        }
    }

    /**
     * Notices that arrive before the item-name filter: the server's claim hint and a buy
     * cancellation refund. True when the notice was fully handled.
     */
    boolean serverNotice(GeneralContext ctx, String message) {
        var active = ctx.active();
        var trade = ctx.trade();
        var step = ctx.step();
        if(step==Step.VERIFY_CANCEL && active.cancelRequested && cancelIssued
                && com.goofy.goofyaddons.utils.Chat.strip(message).equals(CANCEL_REJECTED)) {
            cancelIssued=false;
            if(trade.receipt || active.confirmedCancelRefund!=null)return true;
            rejected=true;rejectedAt=ctx.now();rejections++;
            ctx.services().event("WARN","order.cancel_rejected",java.util.Map.of("trade",active.tradeId,
                "attempt",rejections,"side",trade.selling?"SELL":"BUY","units",active.quantity,
                "unitPrice",trade.selling?active.sellPrice:active.unitCost));
            if(rejections>=3)ctx.fail("Cancellation was rejected after three attempts; tracked order and capital retained for recovery.");
            else {ctx.actions().closeMenu();ctx.transition(Step.VERIFY_CANCEL);}
            return true;
        }
        if (!trade.selling && step == Step.VERIFY_CANCEL && active.cancelRequested
                && com.goofy.goofyaddons.utils.Chat.strip(message).equals("[Bazaar] You have goods to claim on this order!")) {
            trade.cancelNeedsClaim = true;
            return true;
        }
        if (!trade.selling && active.stage == Stage.BUY_ORDER && active.purchasePriceKnown && active.cancelRequested
                && (step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL || step == Step.ORDERS && (rejections > 0 || missingAckRetries > 0))
                && trade.expectedClaim == 0 && trade.inventoryBefore == active.productionInventoryBefore) {
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
        if ((step == Step.CANCEL_DETAIL || step == Step.VERIFY_CANCEL || step == Step.ORDERS && (rejections > 0 || missingAckRetries > 0)) && active.cancelRequested
                && TradingSafety.cancellationReceipt(message, active.item.name())) trade.receipt = true;
    }
}
