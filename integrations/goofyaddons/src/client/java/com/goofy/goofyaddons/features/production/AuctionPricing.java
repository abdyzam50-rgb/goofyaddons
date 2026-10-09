package com.goofy.goofyaddons.features.production;

import com.google.gson.JsonObject;

/**
 * Turns a live lowest-BIN quote (fetched by the companion from Coflnet) into a listing price,
 * so a production run's output is priced from the market instead of a typed number.
 *
 * <p>The listing undercuts the lowest BIN by one coin. When the lowest BIN is under half the
 * second lowest it is treated as an outlier (a mispriced or bait listing), and no price is
 * chosen: the player decides.
 */
public final class AuctionPricing {
    public record Quote(String item, long lowest, Long secondLowest, long fetchedAt) {}
    /** How old a quote may be when a run is queued. */
    static final long MAX_AGE_MS = 5 * 60_000;

    private AuctionPricing() {}
    public static void validateObserved(Quote quote,String product,double observed,long now) {
        if(quote==null || !product.equals(quote.item()) || quote.fetchedAt()>now+5000 || now-quote.fetchedAt()>MAX_AGE_MS)
            throw new IllegalArgumentException("Fresh matching Coflnet price required");
        listingPrice(quote); // Retain the extreme-lowest outlier check.
        if(!Double.isFinite(observed) || observed<1 || observed<quote.lowest()*0.9 || observed>quote.lowest()*1.1)
            throw new IllegalArgumentException("Observed BIN price differs from Coflnet by more than 10%; refresh and review");
    }

    static Quote parse(JsonObject body, String item, long now) {
        if (body == null || !"goofy-ah-price/1".equals(text(body, "protocol")) || !item.equals(text(body, "item")))
            throw new IllegalArgumentException("Auction price reply is not for " + item);
        double lowest = body.get("lowest").getAsDouble();
        var secondValue = body.get("secondLowest");
        Double second = secondValue == null || secondValue.isJsonNull() ? null : secondValue.getAsDouble();
        long at = body.get("fetchedAt").getAsLong();
        if (!Double.isFinite(lowest) || lowest < 1 || lowest > 1e12 || second != null && (!Double.isFinite(second) || second < lowest || second > 1e12))
            throw new IllegalArgumentException("Auction price for " + item + " is invalid");
        if (at > now + 5_000 || now - at > MAX_AGE_MS) throw new IllegalArgumentException("Auction price for " + item + " is stale");
        return new Quote(item, (long) Math.floor(lowest), second == null ? null : (long) Math.floor(second), at);
    }

    /** One coin under the lowest BIN, or an exception naming why no price is safe to choose. */
    public static long listingPrice(Quote quote) {
        if (quote.secondLowest() != null && quote.lowest() * 2 < quote.secondLowest())
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
                    "the lowest BIN (%,d) is under half the next one (%,d); give a price yourself", quote.lowest(), quote.secondLowest()));
        return Math.max(1, quote.lowest() - 1);
    }

    private static String text(JsonObject body, String key) {
        var value = body.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
