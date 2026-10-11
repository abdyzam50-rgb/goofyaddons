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
 * limit. Units that did not fit go to the player's stash, so a paid purchase that is short
 * runs /pickupstash once before deciding. Anything else, including an unexpected
 * confirmation screen, ends in review: an instant buy is never repeated automatically.
 */
public final class BazaarInstantBuy {
    public enum Result { WAITING, BOUGHT, BLOCKED, UNCERTAIN }
    /** Durably records the purchase intent; throws when it could not be saved. */
    @FunctionalInterface public interface Intent { void record(String reason) throws Exception; }
    private enum Step { OPEN, PRODUCT, AMOUNT, SIGN, CONFIRM, VERIFY, DONE }

    private final String productId, name;
    private final BazaarSearch search;
    private final NavigationRetry navigation=new NavigationRetry();
    private final int requested;
    private final boolean fill;
    private int amount;
    private double maximumCost;
    private final Intent intent;
    /** How long a paid but short purchase waits for the inventory before picking up the stash. */
    static final long STASH_AFTER_MS = 1_500;
    private Step step = Step.OPEN;
    private boolean stashPicked;
    private long started, stepAt, heldSince;
    private int before = -1, confirmContainer = Integer.MIN_VALUE;
    private double purseBefore = Double.NaN, spent;
    private String failure;

    public BazaarInstantBuy(String productId, String name, int amount, double maximumCost, Intent intent) {
        this(productId,name,amount,maximumCost,intent,false);
    }

    /**
     * With {@code fill}, an amount larger than the inventory buys what "Fill my inventory!" quotes
     * instead, at the same per-unit limit; {@link #amount()} then reports what was bought.
     */
    public BazaarInstantBuy(String productId, String name, int amount, double maximumCost, Intent intent, boolean fill) {
        if (!ProductionRecipe.validId(productId) || productId.contains(";") || name == null || name.isBlank()
                || amount < 1 || amount > 71680 || !Double.isFinite(maximumCost) || maximumCost <= 0 || intent == null)
            throw new IllegalArgumentException("Invalid instant buy");
        this.productId = productId; this.name = name; search = new BazaarSearch(productId,name); this.amount = amount; this.requested = amount; this.fill = fill; this.maximumCost = maximumCost; this.intent = intent;
    }

    /** Once submitted, verify its outcome before permitting any new market decision. */
    public boolean submitted(){return step==Step.CONFIRM||step==Step.VERIFY||step==Step.DONE;}
    public String failure() { return failure; }
    public String productId() { return productId; }
    public int amount() { return amount; }
    /** The amount asked for, before any "Fill my inventory!" reduction. */
    public int requested() { return requested; }
    /** Coins the verified purchase took from the purse; zero until it is verified. */
    public double spent() { return spent; }
    public double maximumCost() { return maximumCost; }
    public boolean usedStash() { return stashPicked; }

    public Result tick(MenuSnapshot menu, boolean signOpen, GameActions actions, double purse, long now) {
        if (step == Step.DONE) return Result.BOUGHT;
        if (started == 0) started = now;
        if (stepAt == 0) stepAt = now;
        if (step == Step.VERIFY) return verify(menu, purse, actions, now);
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
                String unmet=com.goofy.goofyaddons.features.generalflipper.BazaarAccess.unmet(menu.slot(control).lore());
                if(unmet!=null)return block("Cannot buy "+name+": "+unmet);
                Double unit = unitPrice(menu.slot(control).lore());
                if (unit == null) return block("Instant buy price for " + name + " is unreadable");
                if (unit * amount > maximumCost + 1e-6) return block(String.format(java.util.Locale.ROOT,
                        "Instant buy of %d %s would cost about %,.0f coins, above the %,.0f limit", amount, name, unit * amount, maximumCost));
                if (!Double.isFinite(purse) || purse < unit * amount) return block("Purse cannot cover the instant buy");
                if (!fill && menu.emptyInventorySlots() * 64 < amount) return block("Not enough inventory space for " + amount + " " + name);
                if (fill && menu.emptyInventorySlots() == 0) return block("No free inventory slot to buy " + name + " into");
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
                // Storage-backed runs buy what fits, store it, and come back for the rest.
                int filler = fill ? menu.firstByHoverName("Fill my inventory!", true) : -1;
                Integer fits = filler < 0 ? null : quotedAmount(menu.slot(filler).lore(), name);
                if (filler >= 0 && fits == null) return block("Fill my inventory amount for " + name + " is unreadable");
                if (fits != null && fits < amount) {
                    if (fits < 1) return block("No inventory room to buy " + name);
                    maximumCost = maximumCost * fits / amount;
                    amount = fits;
                    preset = filler;
                }
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
                // A paid purchase without a confirmation screen may have sent units to the stash.
                if (paidButShort(menu, purse)) { step = Step.VERIFY; return verify(menu, purse, actions, now); }
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

    private Result verify(MenuSnapshot menu, double purse, GameActions actions, long now) {
        Result proven = proof(menu, purse);
        if (proven != null) return proven;
        // Hypixel sends instant-bought units that do not fit to the stash. Once the purse shows
        // the payment and the inventory stays short, pick them up once, then prove it again.
        if (!stashPicked && paidButShort(menu, purse) && now - stepAt > STASH_AFTER_MS) {
            if (menu.emptyInventorySlots() == 0)
                return uncertain("Part of the " + name + " instant buy went to your stash and the inventory has no free slot; make room and run /pickupstash");
            stashPicked = true; stepAt = now;
            actions.command("pickupstash");
            return Result.WAITING;
        }
        // After the confirmation click Hypixel may redraw that screen under a new menu id; it is
        // never clicked again, so only a confirmation the run never saw is a surprise.
        if (confirmContainer == Integer.MIN_VALUE && menu != null && menu.title() != null
                && Chat.strip(menu.title()).toLowerCase(java.util.Locale.ROOT).contains("confirm"))
            return uncertain("Unexpected instant buy confirmation screen; inspect it before continuing");
        return now - stepAt > RecoveryRules.RECEIPT_GRACE_MS ? uncertain("Instant buy of " + name + " was not confirmed by inventory and purse"
                + (stashPicked ? " after /pickupstash; check your stash" : "")) : Result.WAITING;
    }

    /** The purse shows a payment within the limit but the inventory holds fewer units than bought. */
    private boolean paidButShort(MenuSnapshot menu, double purse) {
        if (menu == null || !Double.isFinite(purse) || purse < 0) return false;
        double spent = purseBefore - purse;
        return spent > 0 && spent <= maximumCost + 0.51 && count(menu) - before < amount;
    }

    /** Bought when inventory and purse show exactly this purchase, review when they show more, else null. */
    private Result proof(MenuSnapshot menu, double purse) {
        // ScoreboardUtils uses -1 for a temporarily unreadable balance. It is not a debit.
        if (menu == null || !Double.isFinite(purse) || purse < 0) return null;
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
            if (slot.inPlayerInventory() && slot.containerSlot() < 36 && !slot.empty() && ProductIdentity.matches(slot,productId)) total += slot.count();
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

    /** The amount an amount button quotes, when it names this product. */
    static Integer quotedAmount(String lore, String name) {
        if (lore == null) return null;
        String text = Chat.strip(lore);
        if (!text.lines().map(String::strip).anyMatch(name::equalsIgnoreCase)) return null;
        var units = java.util.regex.Pattern.compile("(?im)^\\s*amount:\\s*([0-9][0-9,]*)x\\s*$").matcher(text);
        if (!units.find()) return null;
        try { return Integer.parseInt(units.group(1).replace(",", "")); }
        catch (NumberFormatException invalid) { return null; }
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
