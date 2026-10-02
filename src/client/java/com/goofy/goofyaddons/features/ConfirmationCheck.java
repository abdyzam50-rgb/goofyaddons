package com.goofy.goofyaddons.features;

import com.goofy.goofyaddons.features.generalflipper.OrderLore;
import java.util.regex.Pattern;

/** Supported confirmation evidence; absent, conflicting or unknown fields fail closed. */
public final class ConfirmationCheck {
    private ConfirmationCheck() {}
    private static String clean(String value) { return value==null ? "" : value.replaceAll("§.","").replace('\u00a0',' ').strip(); }
    public static boolean matches(String title,boolean selling,String buttonName,String lore,String expectedItem,int quantity,double price) {
        if(!TradingSafety.confirmationTitle(title,selling) || expectedItem==null || quantity<=0 || !Double.isFinite(price) || price<=0) return false;
        String text=clean(lore),item=clean(expectedItem);
        Integer observed=OrderLore.total(text);
        var quantityFields=Pattern.compile("(?im)^\\s*(?:Order amount|Offer amount|Amount|Quantity):\\s*(.*?)\\s*$").matcher(text);
        while(quantityFields.find()) {
            String value=quantityFields.group(1);
            if(!value.matches("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:x|\\s+(?:items?|units?))?")) return false;
        }
        // Identity is the exact product name or an explicit item field, never substring matching.
        boolean identity=clean(buttonName).equals(item);
        var fields=Pattern.compile("(?im)^\\s*(?:Item|Product):\\s*(.+?)\\s*$").matcher(text);
        while(fields.find()) {
            if(!fields.group(1).equals(item)) return false;
            identity=true;
        }
        var action=Pattern.compile("(?im)^\\s*(Buying|Selling|Order):\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+))x\\s+(.+?)\\s*$").matcher(text);
        int actionFields=0;
        while(action.find()) {
            actionFields++;
            boolean sideConflict=!action.group(1).equalsIgnoreCase("Order") && selling!=action.group(1).equalsIgnoreCase("Selling");
            if(sideConflict || !action.group(3).equals(item)) return false;
            int amount;
            try { amount=Integer.parseInt(action.group(2).replace(",","")); } catch(NumberFormatException bad) { return false; }
            if(amount<=0 || observed!=null && observed!=amount) return false;
            observed=amount;identity=true;
        }
        var actionMarkers=Pattern.compile("(?im)^\\s*(?:Buying|Selling|Order):").matcher(text);
        int actionMarkerCount=0;while(actionMarkers.find()) actionMarkerCount++;
        if(actionFields!=actionMarkerCount || !identity || observed==null || observed!=quantity) return false;
        boolean priced=false;
        int priceFields=0;
        var prices=Pattern.compile("(?im)^\\s*(?:Unit price|Price per unit):\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*coins\\s*$").matcher(text);
        while(prices.find()) {
            double unit=Double.parseDouble(prices.group(1).replace(",",""));
            if(!samePrice(unit,price)) return false;
            priced=true;priceFields++;
        }
        var totals=Pattern.compile("(?im)^\\s*(?:Total cost|Total price|Total value):\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*coins\\s*$").matcher(text);
        while(totals.find()) {
            double total=Double.parseDouble(totals.group(1).replace(",",""));
            if(!totalMatches(total,price*quantity)) return false;
            priced=true;priceFields++;
        }
        var markers=Pattern.compile("(?im)^\\s*(?:Unit price|Price per unit|Total cost|Total price|Total value):").matcher(text);
        int markerCount=0;while(markers.find()) markerCount++;
        return priced && markerCount==priceFields;
    }
    /** Hypixel rounds the displayed total to whole coins (11,984,225.6 shows as 11,984,226). */
    static boolean totalMatches(double shown,double exact) {
        return Double.isFinite(shown) && Double.isFinite(exact) && Math.abs(shown-exact)<=0.5+1e-6;
    }
    private static boolean samePrice(double a,double b) { return Double.isFinite(a) && a>0 && Math.abs(a-b)<=0.000001; }
    public static boolean buyAllowed(double price,int units,double exit,double tax,double margin,double minProfit,double itemLimit) {
        double cost=price*units,net=exit*(1-tax/100)-price;
        return Double.isFinite(cost) && Double.isFinite(net) && price>0 && units>0 && exit>0
                && tax>=0 && tax<100 && net>0 && net/price*100>=margin
                && net*units>=minProfit && cost<=itemLimit;
    }
}
