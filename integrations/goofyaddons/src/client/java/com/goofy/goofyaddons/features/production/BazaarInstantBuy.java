package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.transaction.ProductIdentity;
import com.goofy.goofyaddons.features.transaction.RecoveryRules;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;

/**
 * Buys one exact amount of one product with Bazaar "Buy Instantly", for production inputs.
 *
 * <p>Only the player's opt-in setting lets a production run use this. The product page and
 * its control must name the product, and the quoted unit price times the amount must fit
 * the spending limit before anything is clicked. The intent is recorded before the amount
 * is written, because writing the sign is the irreversible step. The purchase counts only
 * when the inventory gains exactly the amount and the purse falls by no more than the
 * limit. Anything else, including an unexpected confirmation screen, ends in review: an
 * instant buy is never repeated automatically.
 */
public final class BazaarInstantBuy {
    public enum Result { WAITING, BOUGHT, BLOCKED, UNCERTAIN }
    /** Durably records the purchase intent; throws when it could not be saved. */
    @FunctionalInterface public interface Intent { void record(String reason) throws Exception; }
    private enum Step { OPEN, PRODUCT, AMOUNT, SIGN, CONFIRM, VERIFY, DONE }

    private final String productId, name;
    private final BazaarSearch search;
    private final NavigationRetry navigation=new NavigationRetry();
    private final int amount;
    private final double maximumCost;
    private final Intent intent;
    private Step step = Step.OPEN;
    private long started, stepAt, heldSince;
    private int before = -1, confirmContainer = Integer.MIN_VALUE;
    private double purseBefore = Double.NaN, spent;
    private String failure;

    public BazaarInstantBuy(String productId, String name, int amount, double maximumCost, Intent intent) {
        if (!ProductionRecipe.validId(productId) || productId.contains(";") || name == null || name.isBlank()
                || amount < 1 || amount > 71680 || !Double.isFinite(maximumCost) || maximumCost <= 0 || intent == null)
            throw new IllegalArgumentException("Invalid instant buy");
        this.productId = productId; this.name = name; search = new BazaarSearch(productId,name); this.amount = amount; this.maximumCost = maximumCost; this.intent = intent;
    }

    public String failure() { return failure; }
    public String productId() { return productId; }
    public int amount() { return amount; }
    /** Coins the verified purchase took from the purse; zero until it is verified. */
    public double spent() { return spent; }

    public Result tick(MenuSnapshot menu, boolean signOpen, GameActions actions, double purse, long now) {
        if (step == Step.DONE) return Result.BOUGHT;
        if (started == 0) started = now;
        if (stepAt == 0) stepAt = now;
        if (step == Step.VERIFY) return verify(menu, purse, now);
        if(navigation.pending()) {
            var acknowledgement=navigation.observe(menu,signOpen,actions,now);
            if(acknowledgement==NavigationRetry.Result.EXHAUSTED)return block("Bazaar navigation was not acknowledged; nothing bought");
            if(acknowledgement!=NavigationRetry.Result.READY)return Result.WAITING;
        }
        // The overall deadline covers reaching the amount; the sign and confirmation have their own.
        if ((step == Step.OPEN || step == Step.PRODUCT || step == Step.AMOUNT) && now - started > 45_000)
            return block("Instant buy of " + name + " did not reach the amount sign; nothing bought");
        // Hypixel menus can leave a clicked button on the cursor until the server resyncs it, so
        // a held item pauses clicks for a moment before it blocks. Opening the Bazaar is a command.
        boolean held = menu != null && menu.title() != null && !menu.cursorEmpty();
        if (!held) heldSince = 0; else if (heldSince == 0) heldSince = now;
        if (held && step != Step.OPEN)
            return now - heldSince > RecoveryRules.INPUT_RESTART_MS ? block("Instant buy needs an empty cursor") : Result.WAITING;
        switch (step) {
            case OPEN -> {
                if (!held && menu != null && productControl(menu) >= 0) { step = Step.PRODUCT; stepAt = now; return tick(menu, signOpen, actions, purse, now); }
                String stuck = search.step(menu, held, actions, now);
                if (stuck != null) return block(held ? "Instant buy needs an empty cursor" : stuck);
            }
            case PRODUCT -> {
                if (menu == null) return Result.WAITING;
                int control = productControl(menu);
                if (control < 0) return Result.WAITING;
                Double unit = unitPrice(menu.slot(control).lore());
                if (unit == null) return block("Instant buy price for " + name + " is unreadable");
                if (unit * amount > maximumCost + 1e-6) return block(String.format(java.util.Locale.ROOT,
                        "Instant buy of %d %s would cost about %,.0f coins, above the %,.0f limit", amount, name, unit * amount, maximumCost));
                if (!Double.isFinite(purse) || purse < unit * amount) return block("Purse cannot cover the instant buy");
                if (menu.emptyInventorySlots() * 64 < amount) return block("Not enough inventory space for " + amount + " " + name);
                before = count(menu); purseBefore = purse;
                actions.click(control, false); navigation.sent(menu,control,now); step = Step.AMOUNT; stepAt = now;
            }
            case AMOUNT -> {
                if (signOpen) { step = Step.SIGN; stepAt = now; return tick(menu, true, actions, purse, now); }
                if (menu == null || menu.title() == null) return Result.WAITING;
                // Hypixel cuts long titles ("Enchanted Gold Ingot ➜ Instant"), so the amount menu is
                // recognised by its Custom Amount control once the product page is gone.
                int custom = productControl(menu) < 0 ? menu.firstByHoverName("Custom Amount", true) : -1;
                if (custom < 0)
                    return now - stepAt > RecoveryRules.INPUT_RESTART_MS ? block("Instant buy amount menu did not open") : Result.WAITING;
                // "Buy only one!" and "Buy a stack!" quote their exact total, so they skip the sign and
                // are checked against the limit by their own price.
                int preset = amount == 1 ? menu.firstByHoverName("Buy only one!", true) : amount == 64 ? menu.firstByHoverName("Buy a stack!", true) : -1;
                if (preset >= 0) {
                    Double total = quotedTotal(menu.slot(preset).lore(), name, amount);
                    if (total == null) return block("Instant buy preset for " + name + " is unreadable");
                    String refused = refuse(total, purse);
                    if (refused != null) return block(refused);
                    try { intent.record("Instant buy intent: " + amount + " " + productId + " up to " + Math.round(maximumCost) + " coins"); }
                    catch (Exception journal) { return block("Instant buy intent could not be saved; nothing bought"); }
                    step = Step.CONFIRM; stepAt = now;
                    actions.click(preset, false);
                    return Result.WAITING;
                }
                actions.click(custom, false); navigation.sent(menu,custom,now); step = Step.SIGN; stepAt = now;
            }
            case SIGN -> {
                if (!signOpen) return now - stepAt > RecoveryRules.INPUT_RESTART_MS ? block("Instant buy amount sign did not open") : Result.WAITING;
                try { intent.record("Instant buy intent: " + amount + " " + productId + " up to " + Math.round(maximumCost) + " coins"); }
                catch (Exception journal) { return block("Instant buy intent could not be saved; nothing bought"); }
                step = Step.CONFIRM; stepAt = now;
                if (!actions.writeSign(Integer.toString(amount))) return uncertain("Instant buy amount could not be confirmed on the sign");
            }
            case CONFIRM -> {
                // Hypixel asks on a "Confirm Instant Buy" screen whose one item quotes the product,
                // amount and total. A purchase that completes without it is accepted on the same proof.
                Result proven = proof(menu, purse);
                if (proven != null) return proven;
                if (menu == null || menu.title() == null || !Chat.strip(menu.title()).toLowerCase(java.util.Locale.ROOT).startsWith("confirm instant buy"))
                    return now - stepAt > RecoveryRules.INPUT_RESTART_MS ? uncertain("Instant buy confirmation for " + name + " did not open; check whether anything was bought") : Result.WAITING;
                int quote = -1; Double total = null;
                for (int i = 0; i < menu.slots().size(); i++) {
                    var slot = menu.slot(i);
                    if (slot == null || slot.empty() || slot.inPlayerInventory()) continue;
                    Double quoted = quotedTotal(slot.lore(), name, amount);
                    if (quoted == null) continue;
                    if (quote >= 0) return uncertain("Instant buy confirmation for " + name + " shows more than one quote");
                    quote = i; total = quoted;
                }
                if (quote < 0) return now - stepAt > RecoveryRules.INPUT_RESTART_MS ? uncertain("Instant buy confirmation for " + name + " does not quote " + amount + " units") : Result.WAITING;
                String refused = refuse(total, purse);
                if (refused != null) return block(refused);
                confirmContainer = menu.containerId(); step = Step.VERIFY; stepAt = now;
                actions.click(quote, false);
            }
            default -> { }
        }
        return Result.WAITING;
    }

    private Result verify(MenuSnapshot menu, double purse, long now) {
        Result proven = proof(menu, purse);
        if (proven != null) return proven;
        // After the confirmation click Hypixel may redraw that screen under a new menu id; it is
        // never clicked again, so only a confirmation the run never saw is a surprise.
        if (confirmContainer == Integer.MIN_VALUE && menu != null && menu.title() != null
                && Chat.strip(menu.title()).toLowerCase(java.util.Locale.ROOT).contains("confirm"))
            return uncertain("Unexpected instant buy confirmation screen; inspect it before continuing");
        return now - stepAt > RecoveryRules.RECEIPT_GRACE_MS ? uncertain("Instant buy of " + name + " was not confirmed by inventory and purse") : Result.WAITING;
    }

    /** Bought when inventory and purse show exactly this purchase, review when they show more, else null. */
    private Result proof(MenuSnapshot menu, double purse) {
        if (menu == null || !Double.isFinite(purse)) return null;
        int gained = count(menu) - before;
        double spent = purseBefore - purse;
        if (gained == amount && spent > 0 && spent <= maximumCost + 0.51) { this.spent = spent; step = Step.DONE; return Result.BOUGHT; }
        if (gained > amount || spent > maximumCost + 0.51) return uncertain("Instant buy changed inventory or purse more than expected");
        return null;
    }

    private String refuse(double total, double purse) {
        if (total > maximumCost + 1e-6) return String.format(java.util.Locale.ROOT,
                "Instant buy of %d %s would cost %,.0f coins, above the %,.0f limit", amount, name, total, maximumCost);
        return !Double.isFinite(purse) || purse < total ? "Purse cannot cover the instant buy" : null;
    }

    private int productControl(MenuSnapshot menu) {
        int control = menu.firstByHoverName("Buy Instantly", true);
        if (control < 0) return -1;
        return ProductIdentity.productPage(menu, productId, name, control) ? control : -1;
    }

    private int count(MenuSnapshot menu) {
        int total = 0;
        for (var slot : menu.slots())
            if (slot.inPlayerInventory() && slot.containerSlot() < 36 && !slot.empty() && productId.equals(slot.customId())) total += slot.count();
        return total;
    }

    /** "Price per unit: 1,234.5 coins" from the Buy Instantly control. */
    static Double unitPrice(String lore) {
        if (lore == null) return null;
        var matcher = java.util.regex.Pattern.compile("(?i)price per unit:\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*coins").matcher(Chat.strip(lore));
        if (!matcher.find()) return null;
        try {
            double value = Double.parseDouble(matcher.group(1).replace(",", ""));
            return Double.isFinite(value) && value > 0 ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    /** The quoted total of an amount button or confirmation item, when it names this product and amount. */
    static Double quotedTotal(String lore, String name, int amount) {
        if (lore == null) return null;
        String text = Chat.strip(lore);
        if (!text.lines().map(String::strip).anyMatch(name::equalsIgnoreCase)) return null;
        var units = java.util.regex.Pattern.compile("(?im)^\\s*amount:\\s*([0-9][0-9,]*)x\\s*$").matcher(text);
        if (!units.find() || !Integer.toString(amount).equals(units.group(1).replace(",", ""))) return null;
        var price = java.util.regex.Pattern.compile("(?im)^\\s*price:\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*coins\\s*$").matcher(text);
        if (!price.find()) return null;
        try {
            double value = Double.parseDouble(price.group(1).replace(",", ""));
            return Double.isFinite(value) && value > 0 ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    private Result block(String reason) { failure = reason; return Result.BLOCKED; }
    private Result uncertain(String reason) { failure = reason; return Result.UNCERTAIN; }
}
