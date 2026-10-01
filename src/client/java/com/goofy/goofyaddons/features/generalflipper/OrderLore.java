package com.goofy.goofyaddons.features.generalflipper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OrderLore {
    record Fill(int filled, int total) {}
    private static final Pattern FILLED = Pattern.compile("Filled:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");
    private static final Pattern CLAIMABLE = Pattern.compile("You have\\s+([\\d,]+)\\s+(?:items?|units?)", Pattern.CASE_INSENSITIVE);

    static Fill fill(String lore) {
        Matcher match = FILLED.matcher(lore);
        if (!match.find()) return null;
        try {
            int filled = number(match.group(1));
            int total = number(match.group(2));
            return total > 0 && filled <= total ? new Fill(filled, total) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    static int claimable(String lore, boolean inventoryAlreadyClaimed) {
        Matcher match = CLAIMABLE.matcher(lore);
        if (match.find()) {
            try { return number(match.group(1)); } catch (NumberFormatException ignored) { return 0; }
        }
        Fill filled = fill(lore);
        return inventoryAlreadyClaimed || filled == null ? 0 : filled.filled();
    }

    private static int number(String value) { return Integer.parseInt(value.replace(",", "")); }
}
