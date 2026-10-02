package com.goofy.goofyaddons.features;

import com.google.gson.JsonObject;

/** Pure decisions shared by live execution and regression tests. */
public final class TradingSafety {
    private TradingSafety() {}

    public static boolean ordersTitle(String title) {
        if (title==null) return false;
        String normalized=com.goofy.goofyaddons.utils.Chat.strip(title).replace(' ',' ').strip().replaceAll("\\s+", " ");
        return java.util.Set.of("Your Bazaar Orders", "Bazaar Orders", "Co-op Bazaar Orders", "Manage Orders",
                "Bazaar ➜ Orders", "Bazaar → Orders", "Bazaar ➜ Manage Orders", "Bazaar → Manage Orders").contains(normalized);
    }

    public static boolean ambiguousOrders(java.util.List<String> names, String item) {
        return names.stream().filter(name -> name.equals("BUY " + item)).count() > 1
                || names.stream().filter(name -> name.equals("SELL " + item)).count() > 1
                || names.stream().anyMatch(name -> name.contains("Next Page") || name.contains("Previous Page"));
    }

    public static boolean fresh(long sourceMs, long nowMs) {
        return sourceMs > 0 && sourceMs <= nowMs + 5000 && nowMs - sourceMs <= 60000;
    }

    public static long sourceTime(JsonObject root, long nowMs) {
        if (root == null || !root.has("lastUpdated")) throw new IllegalStateException("Missing Bazaar timestamp");
        long source = root.get("lastUpdated").getAsLong();
        if (!fresh(source, nowMs)) throw new IllegalStateException("Stale or future Bazaar timestamp");
        return source;
    }

    public static int conservativeCapacity(int emptySlots, int reservedSlots) {
        // Unknown products may be unstackable. Never assume an empty slot holds 64.
        return Math.max(0, emptySlots - reservedSlots);
    }

    public static boolean claimReceipt(String message, String item, int expectedUnits) {
        return com.goofy.goofyaddons.features.profit.TradeReceipts.saleProceeds(message, item, expectedUnits) != null;
    }

    public static boolean cancellationReceipt(String message, String item) {
        String clean = com.goofy.goofyaddons.utils.Chat.strip(message);
        return clean.matches("(?i)^\\[Bazaar] Cancelled (?:buy|sell) order for (?:[\\d,]+x )?"
                + java.util.regex.Pattern.quote(item) + "[!.]?$");
    }

    public static boolean saleComplete(boolean claimPending, boolean matchingReceipt, boolean orderAbsent, int inventory) {
        return claimPending && matchingReceipt && orderAbsent && inventory == 0;
    }

    public static boolean confirmationTitle(String title,boolean selling) {
        if(title==null) return false;
        String clean=com.goofy.goofyaddons.utils.Chat.strip(title).strip();
        return selling ? java.util.Set.of("Confirm Sell Offer","Confirm Sell Order").contains(clean) : clean.equals("Confirm Buy Order");
    }
    public static boolean combinedBookArrived(int previousOutput,int currentOutput,boolean cursorEmpty) {
        return previousOutput>=0 && currentOutput==previousOutput+1 && cursorEmpty;
    }
    public static boolean orderQuantityMatches(int expected, Integer observed) {
        return expected > 0 && observed != null && expected == observed;
    }

    public static boolean orderMatchesIntent(int expectedUnits,double expectedPrice,Integer units,Double price) {
        return orderQuantityMatches(expectedUnits,units) && Double.isFinite(expectedPrice) && expectedPrice>0
                && price!=null && Double.isFinite(price) && price>0 && Math.abs(price-expectedPrice)<=0.000001;
    }

    public static boolean holdingLimit(long heldSince, long now, int maxSeconds,
                                       double cost, double netExit, double maxLossPercent) {
        return heldSince > 0 && now - heldSince >= maxSeconds * 1000L
                || Double.isFinite(netExit) && netExit > 0 && cost > 0
                && (cost - netExit) / cost * 100 >= maxLossPercent;
    }
}
