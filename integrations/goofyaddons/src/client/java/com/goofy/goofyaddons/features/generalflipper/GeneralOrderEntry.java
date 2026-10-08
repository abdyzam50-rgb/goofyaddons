package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.ConfirmationCheck;
import com.goofy.goofyaddons.features.MenuObservationStability;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.Step;
import com.goofy.goofyaddons.features.generalflipper.GeneralPosition.Stage;

/**
 * Entering one buy order or sell offer: product page, amount, price, confirmation and
 * placement verification.
 *
 * <p>The navigation and evidence rules are shared. What differs between buying and
 * selling (price limits, capital, inventory, how a placement changes ownership) is asked
 * of the {@link Side}, so neither side can skip a check the other performs.
 */
final class GeneralOrderEntry {
    /** The side-specific half of entering an order. */
    interface Side {
        boolean selling();
        /** Checks that must pass before the confirmation item is read. */
        boolean confirmationPreconditions(GeneralContext ctx);
        double expectedPrice(GeneralPosition position);
        /** A purse reading for the confirmation, or -1 when this side does not spend coins. */
        double confirmationPurse(GeneralContext ctx);
        boolean confirmationPurseReady(GeneralContext ctx, double purse);
        /** Final ownership and limit checks; failing ones call {@link GeneralContext#fail}. */
        boolean confirmationAllowed(GeneralContext ctx, double expectedPrice, double purse);
        /** Adopts the live top price. False when the position was dropped instead. */
        boolean acceptPrice(GeneralContext ctx, double price);
        void placementVisible(GeneralContext ctx);
        /** Whether the order filled before it could be seen; true when that was handled. */
        boolean filledBeforeVisible(GeneralContext ctx);
    }

    private final MenuObservationStability confirmationStability = new MenuObservationStability();

    /** Every step transition starts its own confirmation observation window. */
    void stepChanged() { confirmationStability.reset(); }

    void openProduct(GeneralContext ctx) {
        ctx.command("bz " + ctx.active().item.name());
        if (skipUnmetProduct(ctx)) return;
        int create = ctx.find(createLabel(ctx), false);
        if (ctx.menu("Bazaar") || productMenuMatches(ctx, create)) ctx.transition(Step.PRODUCT);
    }

    void product(GeneralContext ctx) {
        var active = ctx.active();
        int create = ctx.find(createLabel(ctx), false);
        if (skipUnmetProduct(ctx)) return;
        if (productMenuMatches(ctx, create)) {
            if (ctx.trade().selling) {
                int quantity = ctx.itemCount(active.item.id());
                if (quantity <= 0 || quantity > active.quantity) { ctx.fail("Tracked inventory quantity does not match."); return; }
                active.quantity = quantity;
            }
            ctx.click(create);
            ctx.transition(Step.QUANTITY);
            return;
        }
        if (create < 0 && ctx.menu("Bazaar")) {
            int item = ctx.find(active.item.name(), true);
            if (item >= 0) ctx.click(item);
        }
    }

    void quantity(GeneralContext ctx, boolean signOpen) {
        if (signOpen) ctx.transition(Step.SIGN);
        else if (ctx.menu("How many")) {
            int custom = ctx.find("Custom Amount", false);
            if (custom < 0) custom = ctx.find("Custom", false);
            if (custom >= 0) { ctx.click(custom); ctx.transition(Step.SIGN); }
        } else if (ctx.priceMenu()) ctx.transition(Step.PRICE);
    }

    void sign(GeneralContext ctx, boolean signOpen) {
        if (signOpen) {
            if (!ctx.actions().writeSign(Integer.toString(ctx.active().quantity))) {
                ctx.fail("Could not write the order amount onto the sign; no order submitted."); return;
            }
            ctx.transition(Step.PRICE);
        } else if (ctx.priceMenu()) ctx.transition(Step.PRICE);
    }

    void price(GeneralContext ctx, Side side) {
        if (!ctx.priceMenu() || !ctx.loadedSlot(12) || (!side.selling() && !ctx.freshQuotes())) return;
        double price = ctx.unitPrice(12);
        if (price <= 0) { ctx.fail("Unable to read the transaction price."); return; }
        if (!side.acceptPrice(ctx, price)) return;
        ctx.click(12); // Current top order price, matching the calculator.
        ctx.transition(Step.CONFIRM);
    }

    void confirm(GeneralContext ctx, Side side) {
        var view = ctx.view();
        var active = ctx.active();
        if (!ctx.menu("Confirm")) return;
        if (!TradingSafety.confirmationTitle(view.title(), side.selling())) {
            ctx.fail("Unexpected confirmation type; no order submitted."); return;
        }
        if (!side.confirmationPreconditions(ctx)) return;
        int confirm = 13;
        if (!ctx.loadedSlot(confirm)) return;
        if (!confirmationStability.ready(view.containerId(),
                view.title() + "\n" + view.slot(confirm).hoverName() + "\n" + ctx.lore(confirm),
                !ctx.lore(confirm).isBlank(), ctx.now())) return;
        double expectedPrice = side.expectedPrice(active);
        double purse = side.confirmationPurse(ctx);
        if (!side.confirmationPurseReady(ctx, purse)) return;
        if (!ConfirmationCheck.matches(view.title(), side.selling(),
                view.slot(confirm).hoverName(), ctx.lore(confirm), active.item.name(), active.quantity, expectedPrice)) {
            ctx.fail("Confirmation item, quantity or price is unreadable or differs; no order submitted."); return;
        }
        if (!side.confirmationAllowed(ctx, expectedPrice, purse)) return;
        active.submitted = true;
        if (side.selling()) active.saleEvent = java.util.UUID.randomUUID().toString();
        active.placedAt = ctx.now();
        if (!ctx.save()) return; // Persist intent before the irreversible click.
        ctx.click(confirm);
        ctx.services().event("INFO", "order.submitted", java.util.Map.of("trade", active.tradeId, "item", active.item.id(),
                "units", active.quantity, "side", side.selling() ? "SELL" : "BUY",
                "unitPrice", side.selling() ? active.sellPrice : active.unitCost, "cost", active.cost()));
        ctx.transition(Step.VERIFY_ORDER);
        ctx.actions().closeMenu();
    }

    void verifyPlacement(GeneralContext ctx, Side side) {
        var active = ctx.active();
        if (ctx.now() - ctx.stepSince() < 2000) return; // Allow escrow/setup to reach the server before opening orders.
        ctx.command("managebazaarorders");
        if (!ctx.ordersReady()) return;
        if (ctx.ambiguousOrders()) return;
        int order = ctx.findOrder(side.selling());
        if (order >= 0) {
            if (!ctx.orderMatchesPosition(order)) return;
            ctx.services().event("INFO", "order.verified", java.util.Map.of("trade", active.tradeId, "item", active.item.id(),
                    "units", active.quantity, "side", side.selling() ? "SELL" : "BUY"));
            active.stage = side.selling() ? Stage.SELL_ORDER : Stage.BUY_ORDER;
            side.placementVisible(ctx);
            ctx.finishWork();
        } else if (!side.filledBeforeVisible(ctx)) {
            ctx.recheckOrders("placement-not-visible");
        }
    }

    private static String createLabel(GeneralContext ctx) {
        return ctx.trade().selling ? "Create Sell Offer" : "Create Buy Order";
    }

    private boolean skipUnmetProduct(GeneralContext ctx) {
        var active = ctx.active();
        var view = ctx.view();
        int create = ctx.find("Create Buy Order", false);
        if (!ctx.trade().selling && active.stage == Stage.PLANNED && !active.submitted && !active.cancelRequested) {
            // Only inspect controls on a verified product, or its exact search-result entry.
            String reason = null;
            if (productMenuMatches(ctx, create)) {
                reason = com.goofy.goofyaddons.features.access.ActionRequirements.blocked(view.slot(create).hoverName() + "\n" + ctx.lore(create),
                        ctx.services().skillLevels(), com.goofy.goofyaddons.features.access.ActionRequirements.Action.BUY);
                var icon = view.slot(13);
                if (reason == null && icon != null) reason = BazaarAccess.unmet(icon.lore());
            } else {
                var icon = view.slot(13);
                if (icon != null && !icon.inPlayerInventory() && !icon.empty() && (active.item.id().equals(icon.customId())
                        || active.item.name().equals(com.goofy.goofyaddons.utils.Chat.strip(icon.hoverName()))))
                    reason = BazaarAccess.unmet(icon.lore());
            }
            if (reason == null && ctx.menu("Bazaar")) {
                int item = ctx.find(active.item.name(), true);
                if (item >= 0) reason = BazaarAccess.unmet(ctx.lore(item));
            }
            if (reason != null) {
                ctx.services().excludeProduct(active.item.id(), reason); ctx.skipUnavailable(reason); return true;
            }
        }
        return false;
    }

    /** Hypixel truncates product breadcrumb titles; verify the actual product and control. */
    private boolean productMenuMatches(GeneralContext ctx, int create) {
        var active = ctx.active();
        var view = ctx.view();
        if (create < 0) return false;
        var control = view.slot(create);
        if (control == null || control.inPlayerInventory() || control.empty()) return false;
        var icon = view.slot(13);
        if (icon != null && !icon.empty() && !icon.inPlayerInventory()) {
            if (icon.customId() != null && !icon.customId().isBlank()) {
                return active.item.id().equals(icon.customId()) && control.hasLoreLine(active.item.name());
            }
            if (active.item.name().equals(com.goofy.goofyaddons.utils.Chat.strip(icon.hoverName()))
                    && control.hasLoreLine(active.item.name())) return true;
        }
        // Keep support for older layouts with full titles, but never contradict readable identity.
        return ctx.menu(active.item.name()) && (control.loreLines() == null || control.loreLines().isEmpty()
                || control.hasLoreLine(active.item.name()));
    }
}
