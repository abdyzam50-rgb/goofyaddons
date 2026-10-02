package com.goofy.goofyaddons.features;

import com.google.gson.JsonObject;

/** Pure decisions shared by live execution and regression tests. */
public final class TradingSafety {
    private TradingSafety() {}

    public static boolean ordersTitle(String title) {
        if (title==null) return false;
        String normalized=title.replaceAll("§.", "").replace(' ',' ').strip().replaceAll("\\s+", " ");
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
        String clean = message.replaceAll("§.", "");
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                "^\\[Bazaar] Claimed ([\\d,]+(?:\\.\\d+)?) coins from selling ([\\d,]+)x "
                        + java.util.regex.Pattern.quote(item) + "[!.]?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(clean);
        if (!match.matches()) return false;
        try { return Integer.parseInt(match.group(2).replace(",", "")) == expectedUnits; }
        catch (NumberFormatException invalid) { return false; }
    }

    public static boolean cancellationReceipt(String message, String item) {
        String clean = message.replaceAll("§.", "");
        return clean.matches("(?i)^\\[Bazaar] Cancelled (?:buy|sell) order for (?:[\\d,]+x )?"
                + java.util.regex.Pattern.quote(item) + "[!.]?$");
    }

    public static boolean saleComplete(boolean claimPending, boolean matchingReceipt, boolean orderAbsent, int inventory) {
        return claimPending && matchingReceipt && orderAbsent && inventory == 0;
    }

    public static boolean orderQuantityMatches(int expected, Integer observed) {
        return expected > 0 && observed != null && expected == observed;
    }

    public static boolean holdingLimit(long heldSince, long now, int maxSeconds,
                                       double cost, double netExit, double maxLossPercent) {
        return heldSince > 0 && now - heldSince >= maxSeconds * 1000L
                || Double.isFinite(netExit) && netExit > 0 && cost > 0
                && (cost - netExit) / cost * 100 >= maxLossPercent;
    }
}
