package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filled sell offers sitting in the Bazaar with coins nobody has collected.
 *
 * <p>The engine reconciles its BUY orders at startup and ignores SELL entries entirely, so a
 * sale that filled after a session ended is stranded: the coins stay in the Bazaar and the
 * profit ledger never learns the flip completed. Four field sessions left 26,240,759 coins
 * across seven fully filled offers this way, which is also why no session ever logged a
 * completed sale even though the sales themselves had worked.
 *
 * <p>Pure over the menu text so it can be tested against captured lore. It reports; it does
 * not decide to click anything.
 */
public final class OrphanSales {
    private OrphanSales() {}

    /** One fully filled sell offer with coins waiting. */
    public record Unclaimed(String item, int units, long coins) {}

    private static final Pattern CLAIMABLE = Pattern.compile("You have ([\\d,]+) coins to claim");
    private static final Pattern AMOUNT = Pattern.compile("(?i)Offer amount:\\s*([\\d,]+)x");
    private static final Pattern FILLED = Pattern.compile("(?i)Filled:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");

    /**
     * Reads one orders-menu entry, or null when it is not a fully filled sell offer with
     * claimable coins. Fail-closed on every field it cannot read: a partially filled offer,
     * an offer with no claim line, and a buy order all return null, because claiming those
     * means something different.
     */
    public static Unclaimed unclaimed(String name, List<String> lore) {
        if (name == null || lore == null || !name.startsWith("SELL ")) return null;
        String item = name.substring("SELL ".length()).strip();
        if (item.isEmpty()) return null;
        String text = String.join("\n", lore);
        Matcher claim = CLAIMABLE.matcher(text);
        Matcher filled = FILLED.matcher(text);
        Matcher amount = AMOUNT.matcher(text);
        if (!claim.find() || !filled.find() || !amount.find()) return null;
        try {
            long coins = Long.parseLong(claim.group(1).replace(",", ""));
            int done = Integer.parseInt(filled.group(1).replace(",", ""));
            int total = Integer.parseInt(filled.group(2).replace(",", ""));
            int units = Integer.parseInt(amount.group(1).replace(",", ""));
            if (coins <= 0 || total <= 0 || done != total || units != total) return null;
            return new Unclaimed(item, units, coins);
        } catch (NumberFormatException unreadable) { return null; }
    }

    /** Every fully filled sell offer in the menu, in slot order. */
    public static List<Unclaimed> scan(List<String> names, List<List<String>> lores) {
        List<Unclaimed> found = new ArrayList<>();
        for (int i = 0; i < names.size() && i < lores.size(); i++) {
            Unclaimed one = unclaimed(names.get(i), lores.get(i));
            if (one != null) found.add(one);
        }
        return found;
    }

    public static long total(List<Unclaimed> sales) {
        return sales.stream().mapToLong(Unclaimed::coins).sum();
    }
}
