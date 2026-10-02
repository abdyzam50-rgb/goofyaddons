package com.goofy.goofyaddons.features.generalflipper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrderLore {
    public record Fill(int filled, int total) {}
    private static final Pattern FILLED = Pattern.compile("Filled:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)");
    private static final Pattern TOTAL = Pattern.compile("(?im)^\\s*(?:Order amount|Offer amount|Amount|Quantity):\\s*([\\d,]+)(?:x|\\s|$)");
    private static final Pattern CLAIMABLE = Pattern.compile("You have\\s+([\\d,]+)\\s+(?:items?|units?)", Pattern.CASE_INSENSITIVE);

    public static Fill fill(String lore) {
        Matcher match = FILLED.matcher(clean(lore));
        if (!match.find()) {
            String text=clean(lore);
            Integer quantity=explicitTotal(text);
            boolean noFillField=!Pattern.compile("(?i)Filled\\s*:").matcher(text).find();
            boolean noClaimHint=!Pattern.compile("(?i)You have").matcher(text).find();
            return quantity!=null && noFillField && noClaimHint && text.contains("Click to view options!") ? new Fill(0,quantity) : null;
        }
        try {
            int filled = number(match.group(1));
            int total = number(match.group(2));
            return total > 0 && filled <= total ? new Fill(filled, total) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    public static int claimable(String lore, boolean inventoryAlreadyClaimed) {
        Matcher match = CLAIMABLE.matcher(clean(lore));
        if (match.find()) {
            try { return number(match.group(1)); } catch (NumberFormatException ignored) { return 0; }
        }
        Fill filled = fill(lore);
        return inventoryAlreadyClaimed || filled == null ? 0 : filled.filled();
    }

    public static int claimable(String lore,int alreadyInInventory) {
        Matcher explicit=CLAIMABLE.matcher(clean(lore));
        if(explicit.find()) return claimable(lore,false);
        Fill fill=fill(lore);
        return fill==null ? 0 : Math.max(0,fill.filled()-Math.max(0,alreadyInInventory));
    }
    public static boolean ownOrder(String lore,String username) {
        if(username==null || username.isBlank()) return false;
        Matcher owner=Pattern.compile("(?m)^\\s*By:\\s*(?:\\[[^\\]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16})\\s*$").matcher(clean(lore));
        return owner.find() && username.equalsIgnoreCase(owner.group(1));
    }
    public static boolean canOpenSellOptionsAfterClaim(String lore,int soldUnits,boolean matchingSaleReceipt) {
        return soldUnits>=0 && (soldUnits==0 || matchingSaleReceipt)
                && clean(lore).contains("Click to view options!");
    }
    public static Integer total(String lore) {
        Fill fill=fill(lore);
        if(FILLED.matcher(clean(lore)).find() && fill==null) return null;
        Integer explicit=explicitTotal(clean(lore));
        if(TOTAL.matcher(clean(lore)).find() && explicit==null) return null;
        if(fill!=null && explicit!=null && fill.total()!=explicit) return null;
        return explicit!=null ? explicit : fill==null ? null : fill.total();
    }
    private static Integer explicitTotal(String text) {
        Matcher match=TOTAL.matcher(text);Integer total=null;
        while(match.find()) {
            int value;
            try { value=number(match.group(1)); } catch(NumberFormatException bad) { return null; }
            if(value<=0 || total!=null && total!=value) return null;
            total=value;
        }
        return total;
    }
    private static String clean(String lore) { return com.goofy.goofyaddons.utils.Chat.strip(lore).replace('\u00a0',' '); }
    public static boolean canOpenOptionsAfterClaim(String lore,int before,int current,int expected) {
        return current>=before+expected && current>=before && expected>=0
                && clean(lore).contains("Click to view options!")
                && claimable(lore,true)==0;
    }
    private static int number(String value) { return Integer.parseInt(value.replace(",", "")); }
}
