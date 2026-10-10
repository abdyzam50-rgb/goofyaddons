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
    private final BazaarSearch search;
    private final int amount;
    private final double minimumProceeds,maximumProceeds;
    private final BazaarInstantBuy.Intent intent;
    private Step step = Step.OPEN;
    private long started, stepAt, heldSince;
    private double purseBefore = Double.NaN, proceeds;
    private String failure;

    public BazaarInstantSell(String productId,String name,int amount,double minimumProceeds,BazaarInstantBuy.Intent intent) {
        this(productId,name,amount,minimumProceeds,Double.POSITIVE_INFINITY,intent);
    }
    public BazaarInstantSell(String productId,String name,int amount,double minimumProceeds,double maximumProceeds,BazaarInstantBuy.Intent intent) {
        if (!ProductionRecipe.validId(productId) || productId.contains(";") || name == null || name.isBlank()
                || amount < 1 || amount > 71680 || !Double.isFinite(minimumProceeds) || minimumProceeds < 0 || Double.isNaN(maximumProceeds)||maximumProceeds<minimumProceeds||intent == null)
            throw new IllegalArgumentException("Invalid instant sale");
        this.productId = productId; this.name = name; search = new BazaarSearch(productId,name); this.amount = amount; this.minimumProceeds = minimumProceeds;this.maximumProceeds=maximumProceeds; this.intent = intent;
    }

    public String failure() { return failure; }
    public int amount(){return amount;}
    /** Coins the verified sale added to the purse; zero until it is verified. */
    public double proceeds() { return proceeds; }

    public Result tick(MenuSnapshot menu, GameActions actions, double purse, long now) {
        if (step == Step.DONE) return Result.SOLD;
        if (started == 0) started = now;
        if (step == Step.VERIFY) return verify(menu, purse, now);
        if (now - started > 45_000) return block("Instant sale of " + name + " did not reach the product page; nothing sold");
        // Hypixel menus can leave a clicked button on the cursor until the server resyncs it, so
        // a held item pauses clicks for a moment before it blocks. Opening the Bazaar is a command.
        boolean held = menu != null && menu.title() != null && !menu.cursorEmpty();
        if (!held) heldSince = 0; else if (heldSince == 0) heldSince = now;
        if (held && step != Step.OPEN)
            return now - heldSince > RecoveryRules.INPUT_RESTART_MS ? block("Instant sale needs an empty cursor") : Result.WAITING;
        if (step == Step.OPEN) {
            if (!held && menu != null && productControl(menu) >= 0) { step = Step.PRODUCT; stepAt = now; return tick(menu, actions, purse, now); }
            String stuck = search.step(menu, held, actions, now);
            return stuck == null ? Result.WAITING : block(held ? "Instant sale needs an empty cursor" : stuck);
        }
        int control = productControl(menu);
        if (control < 0) return Result.WAITING;
        if (count(menu) != amount) return block("Inventory holds a different amount of " + name + " than the run made; sell it by hand");
        Double quoted = quotedProceeds(menu.slot(control).lore(), amount);
        if (quoted == null) return block("Instant sale price for " + name + " is unreadable: " + Chat.strip(menu.slot(control).lore()).replace('\n', ' '));
        if (quoted < minimumProceeds - 1e-6) return block(String.format(java.util.Locale.ROOT,
                "Instant sale of %d %s would pay about %,.0f coins, below the %,.0f floor", amount, name, quoted, minimumProceeds));
        if (!Double.isFinite(purse) || purse < 0) return Result.WAITING;
        try { intent.record("Instant sale intent: " + amount + " " + productId + " for at least " + Math.round(minimumProceeds) + " coins"); }
        catch (Exception journal) { return block("Instant sale intent could not be saved; nothing sold"); }
        purseBefore = purse; step = Step.VERIFY; stepAt = now;
        actions.click(control, false);
        return Result.WAITING;
    }

    private Result verify(MenuSnapshot menu, double purse, long now) {
        if (menu != null && menu.title() != null && Chat.strip(menu.title()).toLowerCase(java.util.Locale.ROOT).contains("confirm"))
            return uncertain("Unexpected instant sale confirmation screen; inspect it before continuing");
        if (menu != null && Double.isFinite(purse) && purse >= 0 && count(menu) == 0 && purse > purseBefore) {
            double delta=purse-purseBefore;
            if(delta+0.51<minimumProceeds||delta>maximumProceeds+0.51)return uncertain("Sale purse delta is outside the quoted range; profit is unverified");
            proceeds=delta;step=Step.DONE;return Result.SOLD;
        }
        return now - stepAt > RecoveryRules.RECEIPT_GRACE_MS ? uncertain("Instant sale of " + name + " was not confirmed by inventory and purse") : Result.WAITING;
    }

    private int productControl(MenuSnapshot menu) {
        if (menu == null) return -1;
        int control = menu.firstByHoverName("Sell Instantly", true);
        if (control < 0) return -1;
        if (ProductIdentity.productPage(menu, productId, name, control)) return control;
        // As in book cleanup's instant sale: "Sell Instantly" need not name the product in its lore,
        // so the product icon alone identifies the page.
        var icon = menu.slot(ProductIdentity.ICON_SLOT);
        if (icon == null || icon.empty() || icon.inPlayerInventory()) return -1;
        boolean named = icon.customId() != null && !icon.customId().isBlank() ? productId.equals(icon.customId()) : name.equals(Chat.strip(icon.hoverName()));
        return named ? control : -1;
    }

    /** The sale's total from the control's lore: a unit price times the amount, or a quoted total. */
    static Double quotedProceeds(String lore, int amount) {
        Double unit = BazaarInstantBuy.unitPrice(lore);
        if (unit != null) return unit * amount;
        if (lore == null) return null;
        var total = java.util.regex.Pattern.compile("(?im)^\\s*(?:total|price|you earn|earn):\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*coins").matcher(Chat.strip(lore));
        if (!total.find()) return null;
        try {
            double value = Double.parseDouble(total.group(1).replace(",", ""));
            return Double.isFinite(value) && value > 0 ? value : null;
        } catch (NumberFormatException invalid) { return null; }
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
