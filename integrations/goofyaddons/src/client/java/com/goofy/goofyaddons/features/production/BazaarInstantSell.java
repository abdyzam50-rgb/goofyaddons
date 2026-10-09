package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.transaction.ProductIdentity;
import com.goofy.goofyaddons.features.transaction.RecoveryRules;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;

/**
 * Sells one product's whole inventory amount with Bazaar "Sell Instantly", for a production
 * run's output.
 *
 * <p>"Sell Instantly" sells every unit of the product in the inventory, so the run only uses
 * it when it made all of them. The product page must name the product, the inventory must
 * still hold exactly the expected amount, and the quoted unit price must reach the floor before
 * anything is clicked. The intent is recorded before the click, which is the irreversible step.
 * The sale counts only when the units are gone and the purse rose. Anything else, including a
 * price-warning confirmation screen, ends in review: an instant sale is never repeated.
 */
public final class BazaarInstantSell {
    public enum Result { WAITING, SOLD, BLOCKED, UNCERTAIN }
    private enum Step { OPEN, PRODUCT, VERIFY, DONE }

    private final String productId, name;
    private final int amount;
    private final double minimumProceeds;
    private final BazaarInstantBuy.Intent intent;
    private Step step = Step.OPEN;
    private long started, nextCommand, stepAt;
    private int opens;
    private double purseBefore = Double.NaN, proceeds;
    private String failure;

    public BazaarInstantSell(String productId, String name, int amount, double minimumProceeds, BazaarInstantBuy.Intent intent) {
        if (!ProductionRecipe.validId(productId) || productId.contains(";") || name == null || name.isBlank()
                || amount < 1 || amount > 71680 || !Double.isFinite(minimumProceeds) || minimumProceeds < 0 || intent == null)
            throw new IllegalArgumentException("Invalid instant sale");
        this.productId = productId; this.name = name; this.amount = amount; this.minimumProceeds = minimumProceeds; this.intent = intent;
    }

    public String failure() { return failure; }
    /** Coins the verified sale added to the purse; zero until it is verified. */
    public double proceeds() { return proceeds; }

    public Result tick(MenuSnapshot menu, GameActions actions, double purse, long now) {
        if (step == Step.DONE) return Result.SOLD;
        if (started == 0) started = now;
        if (step == Step.VERIFY) return verify(menu, purse, now);
        if (now - started > 45_000) return block("Instant sale of " + name + " did not reach the product page; nothing sold");
        // Only a click can misplace a held item; with no menu on screen the step is a chat command,
        // and the server clears the cursor when it opens the Bazaar.
        if (menu != null && menu.title() != null && !menu.cursorEmpty()) return block("Instant sale needs an empty cursor");
        if (step == Step.OPEN) {
            if (menu != null && productControl(menu) >= 0) { step = Step.PRODUCT; stepAt = now; return tick(menu, actions, purse, now); }
            if (now >= nextCommand) {
                if (opens >= 3) return block("Bazaar product page for " + name + " did not open");
                opens++; nextCommand = now + RecoveryRules.INPUT_RESTART_MS; actions.command("bz " + name);
            }
            return Result.WAITING;
        }
        int control = productControl(menu);
        if (control < 0) return Result.WAITING;
        if (count(menu) != amount) return block("Inventory holds a different amount of " + name + " than the run made; sell it by hand");
        Double unit = BazaarInstantBuy.unitPrice(menu.slot(control).lore());
        if (unit == null) return block("Instant sale price for " + name + " is unreadable");
        if (unit * amount < minimumProceeds - 1e-6) return block(String.format(java.util.Locale.ROOT,
                "Instant sale of %d %s would pay about %,.0f coins, below the %,.0f floor", amount, name, unit * amount, minimumProceeds));
        if (!Double.isFinite(purse)) return block("Purse is unreadable; nothing sold");
        try { intent.record("Instant sale intent: " + amount + " " + productId + " for at least " + Math.round(minimumProceeds) + " coins"); }
        catch (Exception journal) { return block("Instant sale intent could not be saved; nothing sold"); }
        purseBefore = purse; step = Step.VERIFY; stepAt = now;
        actions.click(control, false);
        return Result.WAITING;
    }

    private Result verify(MenuSnapshot menu, double purse, long now) {
        if (menu != null && menu.title() != null && Chat.strip(menu.title()).toLowerCase(java.util.Locale.ROOT).contains("confirm"))
            return uncertain("Unexpected instant sale confirmation screen; inspect it before continuing");
        if (menu != null && Double.isFinite(purse) && count(menu) == 0 && purse > purseBefore) {
            proceeds = purse - purseBefore; step = Step.DONE; return Result.SOLD;
        }
        return now - stepAt > RecoveryRules.RECEIPT_GRACE_MS ? uncertain("Instant sale of " + name + " was not confirmed by inventory and purse") : Result.WAITING;
    }

    private int productControl(MenuSnapshot menu) {
        if (menu == null) return -1;
        int control = menu.firstByHoverName("Sell Instantly", true);
        if (control < 0) return -1;
        return ProductIdentity.productPage(menu, productId, name, control) || ProductIdentity.truncatedProductPage(menu, name, control) ? control : -1;
    }

    private int count(MenuSnapshot menu) {
        int total = 0;
        for (var slot : menu.slots())
            if (slot.inPlayerInventory() && slot.containerSlot() < 36 && !slot.empty() && productId.equals(slot.customId())) total += slot.count();
        return total;
    }

    private Result block(String reason) { failure = reason; return Result.BLOCKED; }
    private Result uncertain(String reason) { failure = reason; return Result.UNCERTAIN; }
}
