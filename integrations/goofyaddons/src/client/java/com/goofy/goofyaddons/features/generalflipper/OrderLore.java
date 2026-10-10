package com.goofy.goofyaddons.features.generalflipper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrderLore {
    public record Fill(int filled, int total) {}
    private static final Pattern FILLED = Pattern.compile("Filled:\\s*([\\d,]+(?:\\.\\d+)?[kKmMbB]?)\\s*/\\s*([\\d,]+(?:\\.\\d+)?[kKmMbB]?)(?=\\s|\\(|$)");
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
            Integer totalValue = fractionTotal(match.group(2),explicitTotal(clean(lore)));
            if(totalValue==null || !match.group(1).matches("[\\d,]+"))return null;
            int filled = number(match.group(1));
            int total = totalValue;
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
    private static final Pattern OWNER = Pattern.compile("(?m)^\\s*By:\\s*(?:\\[[^\\]\\r\\n]+]\\s*)?([A-Za-z0-9_]{1,16})\\s*$");
    private static final Pattern CREATOR_FIELD = Pattern.compile("(?m)^\\s*By:");

    public static boolean ownOrder(String lore,String username) {
        if(username==null || username.isBlank()) return false;
        Matcher owner=OWNER.matcher(clean(lore));
        return owner.find() && username.equalsIgnoreCase(owner.group(1));
    }

    /** Who a co-op order belongs to. UNREADABLE is retryable; OTHER never is. */
    public enum Creator { OWN, OTHER, UNREADABLE }

    /**
     * Both engines open-coded this three-way decision: own the order, someone
     * else's, or no readable creator field at all. Only the last may be rechecked,
     * because a missing field can mean the menu had not finished loading.
     */
    public static Creator creator(String lore,String username) {
        if(ownOrder(lore,username)) return Creator.OWN;
        return CREATOR_FIELD.matcher(clean(lore)).find() ? Creator.OTHER : Creator.UNREADABLE;
    }
    public static boolean canOpenSellOptionsAfterClaim(String lore,int soldUnits,boolean matchingSaleReceipt) {
        return soldUnits>=0 && (soldUnits==0 || matchingSaleReceipt)
                && clean(lore).contains("Click to view options!");
    }
    public static Integer total(String lore) {
        Fill fill=fill(lore);
        Matcher fraction=FILLED.matcher(clean(lore));
        boolean hasFraction=fraction.find();
        if(!hasFraction && Pattern.compile("(?im)^\\s*Filled:[^\\r\\n]*/").matcher(clean(lore)).find())return null;
        if(hasFraction && fill==null) {
            Integer exact=explicitTotal(clean(lore));
            // Rounded filled counts cannot prove a claim; exact total still proves ownership.
            if(exact==null || fractionTotal(fraction.group(2),exact)==null || !roundedWithinTotal(fraction.group(1),exact))return null;
        }
        Integer explicit=explicitTotal(clean(lore));
        if(TOTAL.matcher(clean(lore)).find() && explicit==null) return null;
        if(fill!=null && explicit!=null && fill.total()!=explicit) return null;
        return explicit!=null ? explicit : fill==null ? null : fill.total();
    }
    private static Integer fractionTotal(String token,Integer explicit) {
        if(token.matches("[\\d,]+")) {
            try {int n=number(token);return n>0 && (explicit==null || explicit==n)?n:null;}
            catch(NumberFormatException bad){return null;}
        }
        return explicit!=null && roundedMatches(token,explicit)?explicit:null;
    }
    private static boolean roundedWithinTotal(String token,int exact) {
        if(!token.matches("[\\d,]+(?:\\.\\d+)?[kKmMbB]"))return false;
        try {
            char suffix=Character.toLowerCase(token.charAt(token.length()-1));
            int scale=suffix=='k'?1000:suffix=='m'?1000000:1000000000;
            var shown=new java.math.BigDecimal(token.substring(0,token.length()-1).replace(",",""));
            var halfStep=java.math.BigDecimal.valueOf(5).scaleByPowerOfTen(-shown.scale()-1);
            return shown.signum()>0 && shown.subtract(halfStep).multiply(java.math.BigDecimal.valueOf(scale))
                    .compareTo(java.math.BigDecimal.valueOf(exact))<=0;
        }catch(NumberFormatException bad){return false;}
    }
    private static boolean roundedMatches(String token,int exact) {
        if(!token.matches("[\\d,]+(?:\\.\\d+)?[kKmMbB]"))return false;
        try {
            char suffix=Character.toLowerCase(token.charAt(token.length()-1));
            int scale=suffix=='k'?1000:suffix=='m'?1000000:1000000000;
            var displayed=new java.math.BigDecimal(token.substring(0,token.length()-1).replace(",",""));
            return displayed.signum()>0 && java.math.BigDecimal.valueOf(exact).divide(java.math.BigDecimal.valueOf(scale))
                    .setScale(displayed.scale(),java.math.RoundingMode.HALF_UP).compareTo(displayed)==0;
        }catch(NumberFormatException bad){return false;}
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
