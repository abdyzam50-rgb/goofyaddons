package com.goofy.goofyaddons.features.generalflipper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrderLore {
    public record Fill(int filled, int total) {}
    private static final Pattern FILLED = Pattern.compile("Filled:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");
    private static final Pattern TOTAL = Pattern.compile("(?im)^\\s*(?:Order amount|Amount|Quantity):\\s*([\\d,]+)(?:x|\\s|$)");
    private static final Pattern CLAIMABLE = Pattern.compile("You have\\s+([\\d,]+)\\s+(?:items?|units?)", Pattern.CASE_INSENSITIVE);

    public static Fill fill(String lore) {
        Matcher match = FILLED.matcher(clean(lore));
        if (!match.find()) return null;
        try {
            int filled = number(match.group(1));
            int total = number(match.group(2));
            return total > 0 && filled <= total ? new Fill(filled, total) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    static int claimable(String lore, boolean inventoryAlreadyClaimed) {
        Matcher match = CLAIMABLE.matcher(clean(lore));
        if (match.find()) {
            try { return number(match.group(1)); } catch (NumberFormatException ignored) { return 0; }
        }
        Fill filled = fill(lore);
        return inventoryAlreadyClaimed || filled == null ? 0 : filled.filled();
    }

    public static Integer total(String lore) {
        Fill fill=fill(lore);
        if(FILLED.matcher(clean(lore)).find() && fill==null) return null;
        Matcher match=TOTAL.matcher(clean(lore));
        Integer explicit=null;
        if(match.find()) {
            try { explicit=number(match.group(1)); } catch(NumberFormatException bad) { return null; }
            if(explicit<=0) return null;
        }
        if(fill!=null && explicit!=null && fill.total()!=explicit) return null;
        return explicit!=null ? explicit : fill==null ? null : fill.total();
    }
    private static String clean(String lore) { return lore==null ? "" : lore.replaceAll("§.","").replace('\u00a0',' '); }
    public static boolean canOpenOptionsAfterClaim(String lore,int before,int current,int expected) {
        return current>=before+expected && current>=before && expected>=0
                && clean(lore).contains("Click to view options!")
                && claimable(lore,true)==0;
    }
    private static int number(String value) { return Integer.parseInt(value.replace(",", "")); }
}
