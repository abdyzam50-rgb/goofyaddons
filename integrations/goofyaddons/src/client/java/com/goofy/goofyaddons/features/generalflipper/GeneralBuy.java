package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.ConfirmationCheck;
import com.goofy.goofyaddons.features.generalflipper.GeneralPosition.Stage;

import static com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.OWNER;

/** The buy side of entering an order: entry limits, purse and capital checks, and placement. */
final class GeneralBuy implements GeneralOrderEntry.Side {
    @Override public boolean selling() { return false; }

    @Override public boolean confirmationPreconditions(GeneralContext ctx) {
        var active = ctx.active();
        if (!ctx.freshQuotes()) { ctx.fail("Quotes expired before order confirmation."); return false; }
        if (active.quantity > ctx.capacityFor(active.item.id())) {
            ctx.fail("Not enough inventory capacity for this buy order."); return false;
        }
        return true;
    }

    @Override public double expectedPrice(GeneralPosition position) { return position.unitCost; }
    @Override public double confirmationPurse(GeneralContext ctx) { return ctx.services().purse(); }
    @Override public boolean confirmationPurseReady(GeneralContext ctx, double purse) { return ctx.purchasePurseReady(purse); }

    @Override public boolean confirmationAllowed(GeneralContext ctx, double expectedPrice, double purse) {
        var active = ctx.active();
        var settings = ctx.settings();
        if(ctx.services().productionOnly()) {
            if(expectedPrice>active.maximumBuyPrice || !ctx.capital().resize(OWNER,active.item.id(),active.cost(),purse)){
                ctx.fail("Craft ingredient order exceeds its verified price/capital ceiling; no order submitted.");return false;
            }return true;
        }
        if (!ConfirmationCheck.buyAllowed(expectedPrice, active.quantity, ctx.currentAsk(),
                ctx.services().taxPercentage(), settings.minMarginPercentage, settings.minProfitPerBatch, settings.maxCoinsPerItem)
                || !ctx.capital().resize(OWNER, active.item.id(), active.cost(), purse)) {
            ctx.fail("Price or available capital changed before buy confirmation; no order submitted."); return false;
        }
        return true;
    }

    @Override public boolean acceptPrice(GeneralContext ctx, double price) {
        var active = ctx.active();
        var settings = ctx.settings();
        double purse = ctx.services().purse();
        if (!ctx.purchasePurseReady(purse)) return false;
        if(ctx.services().productionOnly()) {
            if(price>active.maximumBuyPrice || !ctx.capital().resize(OWNER,active.item.id(),price*active.quantity,purse)){
                ctx.fail("Ingredient order price or capital changed; position retained.");return false;
            }active.unitCost=price;active.purchasePriceKnown=true;return true;
        }
        double net = ctx.currentAsk() * (1 - ctx.services().taxPercentage() / 100) - price;
        if (net <= 0 || net / price * 100 < settings.minMarginPercentage
                || net * active.quantity < settings.minProfitPerBatch
                || price * active.quantity > settings.maxCoinsPerItem
                || !ctx.capital().resize(OWNER, active.item.id(), price * active.quantity, purse)) {
            ctx.capital().release(OWNER, active.item.id());
            ctx.positions().remove(active);
            ctx.finishWork();
            return false;
        }
        active.unitCost = price;
        active.purchasePriceKnown = true;
        return true;
    }

    @Override public void placementVisible(GeneralContext ctx) {
        ctx.capital().purchased(OWNER, ctx.active().item.id());
        ctx.services().placed(ctx.active());
    }

    @Override public boolean filledBeforeVisible(GeneralContext ctx) {
        var active = ctx.active();
        if (ctx.itemCount(active.item.id()) < active.quantity) return false;
        ctx.capital().purchased(OWNER, active.item.id());
        active.stage = Stage.INVENTORY;
        if (!ctx.recordAcquisition()) return true;
        ctx.finishWork();
        return true;
    }
}
