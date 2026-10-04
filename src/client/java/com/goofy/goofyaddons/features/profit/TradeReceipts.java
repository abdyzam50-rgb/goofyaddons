package com.goofy.goofyaddons.features.profit;

import java.util.regex.Pattern;

public final class TradeReceipts {
    private TradeReceipts() {}
    public static Double saleProceeds(String message,String item,int expectedUnits) {
        if(message==null || item==null || expectedUnits<=0) return null;
        // Hypixel appends " at <gross unit price> each" to a sale claim. The pattern anchored
        // at the item name, so every real claim receipt failed to match: the engine armed a
        // sale claim, collected the coins, never recognised its own receipt, and halted with
        // the sale unrecorded. It is why no session ever logged a completed sale.
        var match=Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+(?:\\.\\d+)?) coins from selling ([\\d,]+)x "
                +Pattern.quote(item)+"(?: at [\\d,]+(?:\\.\\d+)? each)?[!.]?$",
                Pattern.CASE_INSENSITIVE).matcher(com.goofy.goofyaddons.utils.Chat.strip(message));
        if (!match.matches()) return null;
        try {
            int units=Integer.parseInt(match.group(2).replace(",",""));
            double coins=Double.parseDouble(match.group(1).replace(",",""));
            return units==expectedUnits && Double.isFinite(coins) && coins>=0 ? coins : null;
        } catch (NumberFormatException bad) { return null; }
    }
    /** Generic refund is usable only when correlated with the entire pending buy's escrow. */
    public static Double buyCancellationRefund(String message,double expectedCoins) {
        if(message==null||!Double.isFinite(expectedCoins)||expectedCoins<=0)return null;
        var match=Pattern.compile("^\\[Bazaar] Cancelled! Refunded ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins from cancelling Buy Order!$",Pattern.CASE_INSENSITIVE)
                .matcher(com.goofy.goofyaddons.utils.Chat.strip(message));
        if(!match.matches())return null;
        try {double value=Double.parseDouble(match.group(1).replace(",",""));return Double.isFinite(value)&&Math.abs(value-expectedCoins)<=0.51?value:null;}
        catch(NumberFormatException bad){return null;}
    }
    public static Double unitPrice(String lore) {
        if(lore==null) return null;
        var match=Pattern.compile("(?:Unit price|Price per unit):\\s*([\\d,]+(?:\\.\\d+)?)",Pattern.CASE_INSENSITIVE)
                .matcher(com.goofy.goofyaddons.utils.Chat.strip(lore));
        if (!match.find()) return null;
        try { double coins=Double.parseDouble(match.group(1).replace(",","")); return Double.isFinite(coins) && coins>0 ? coins : null; }
        catch (NumberFormatException bad) { return null; }
    }
}
