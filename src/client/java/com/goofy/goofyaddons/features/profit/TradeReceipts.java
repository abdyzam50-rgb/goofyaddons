package com.goofy.goofyaddons.features.profit;

import java.util.regex.Pattern;

public final class TradeReceipts {
    private TradeReceipts() {}
    public static Double saleProceeds(String message,String item,int expectedUnits) {
        var match=Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+(?:\\.\\d+)?) coins from selling ([\\d,]+)x "
                +Pattern.quote(item)+"[!.]?$",Pattern.CASE_INSENSITIVE).matcher(message.replaceAll("§.",""));
        if (!match.matches()) return null;
        try {
            int units=Integer.parseInt(match.group(2).replace(",",""));
            double coins=Double.parseDouble(match.group(1).replace(",",""));
            return units==expectedUnits && Double.isFinite(coins) && coins>=0 ? coins : null;
        } catch (NumberFormatException bad) { return null; }
    }
    public static Double unitPrice(String lore) {
        var match=Pattern.compile("(?:Unit price|Price per unit):\\s*([\\d,]+(?:\\.\\d+)?)",Pattern.CASE_INSENSITIVE)
                .matcher(lore.replaceAll("§.",""));
        if (!match.find()) return null;
        try { double coins=Double.parseDouble(match.group(1).replace(",","")); return Double.isFinite(coins) && coins>0 ? coins : null; }
        catch (NumberFormatException bad) { return null; }
    }
}
