package com.goofy.goofyaddons.features.bookflipper.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

public class BazaarMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(BazaarMonitor.class);
    private final Supplier<CompletableFuture<JsonObject>> fetch;
    private final Executor clientThread;
    private final LongSupplier now;
    private boolean running;
    private static final long DURATION = 20000;
    private long startMs;
    private long lastUpdated;
    private int generation;
    private CompletableFuture<JsonObject> request;
    private final List<BazaarMonitorItem> monitorItemList = new ArrayList<>();
    private final List<Consumer<BazaarMonitorItem>> hookList = new ArrayList<>();

    public BazaarMonitor() {
        this(BazaarApi::fetch, command -> Minecraft.getInstance().execute(command));
    }

    BazaarMonitor(Supplier<CompletableFuture<JsonObject>> fetch, Executor clientThread) {
        this(fetch, clientThread, System::currentTimeMillis);
    }

    BazaarMonitor(Supplier<CompletableFuture<JsonObject>> fetch, Executor clientThread, LongSupplier now) {
        this.fetch = fetch;
        this.clientThread = clientThread;
        this.now = now;
    }

    public void add(Book book, double price, boolean isSellOrder) {
        add(book, price, isSellOrder, DURATION);
    }

    /** Only reports this order as outbid once it has stood for {@code delayMs}. */
    public void add(Book book, double price, boolean isSellOrder, long delayMs) {
        finish(book, isSellOrder);
        monitorItemList.add(new BazaarMonitorItem(book, price, isSellOrder, now.getAsLong(), Math.max(DURATION, delayMs)));
    }

    /**
     * How long a re-placed order waits before it may be chased again. Each consecutive outbid without
     * a fill doubles the wait (20s, 40s, 80s, 160s, then 320s), so two bots can't trade 0.1 coin
     * outbids forever while every other book waits behind the cancel/re-place cycle.
     */
    public static long outbidBackoff(int consecutiveOutbids) {
        return DURATION << Math.min(Math.max(consecutiveOutbids, 0), 4);
    }

    public void finish(Book book, boolean isSellOffer) {
        monitorItemList.removeIf(item -> item.isSellOrder == isSellOffer && item.book.equals(book));
    }

    public void reset() {
        stop();
        monitorItemList.clear();
        lastUpdated = 0;
    }

    public void hook(Consumer<BazaarMonitorItem> hook) {
        hookList.add(hook);
    }

    public void onTick() {
        if (!running || monitorItemList.isEmpty() || request != null) return;
        if (now.getAsLong() - startMs < DURATION) return;
        startMs = now.getAsLong();
        refresh();
    }

    public void start() {
        if (running) return;
        running = true;
        startMs = now.getAsLong();
    }

    public void stop() {
        running = false;
        generation++;
        if (request != null) request.cancel(true);
        request = null;
    }

    public void refresh() {
        if (!running || request != null) return;
        int run = generation;
        try {
            request = fetch.get();
            request.whenComplete((root, error) -> clientThread.execute(() -> {
                if (run != generation || !running) return;
                try {
                    if (error != null) throw new IllegalStateException("Bazaar request failed", error);
                    long updated = com.goofy.goofyaddons.features.TradingSafety.sourceTime(root,now.getAsLong());
                    if (updated <= lastUpdated) return;
                    JsonObject products = root.getAsJsonObject("products");
                    // Hooks may remove monitors. Iterate a snapshot on the client thread.
                    for (BazaarMonitorItem item : List.copyOf(monitorItemList)) {
                        if (!monitorItemList.contains(item) || now.getAsLong() - item.time < item.delay) continue;
                        if (!isOutbid(item.price, bestPrice(products, item), item.isSellOrder)) continue;
                        monitorItemList.remove(item);
                        for (Consumer<BazaarMonitorItem> hook : List.copyOf(hookList)) hook.accept(item);
                    }
                    lastUpdated = updated;
                } catch (Exception failure) {
                    LOGGER.warn("Order monitoring failed; retrying later", failure);
                } finally {
                    request = null;
                }
            }));
        } catch (Exception failure) {
            request = null;
            LOGGER.warn("Order monitoring failed; retrying later", failure);
        }
    }

    /** Best competing price, or NaN when this product's data is missing or malformed. */
    private static double bestPrice(JsonObject products, BazaarMonitorItem item) {
        try {
            JsonObject product = products.getAsJsonObject(item.book.getLevel(
                    item.isSellOrder ? item.book.sellLevel() : item.book.level()));
            if (product == null) return Double.NaN;
            JsonArray orders = product.getAsJsonArray(item.isSellOrder ? "buy_summary" : "sell_summary");
            if (orders == null || orders.isEmpty()) return Double.NaN;
            return orders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
        } catch (RuntimeException malformed) {
            return Double.NaN;
        }
    }

    static boolean isOutbid(double ownPrice, double bestPrice, boolean sellOrder) {
        if (!Double.isFinite(ownPrice) || !Double.isFinite(bestPrice) || ownPrice <= 0 || bestPrice <= 0) return false;
        // Same-price competition does not warrant cancelling an order.
        return sellOrder ? bestPrice < ownPrice - 0.01 : bestPrice > ownPrice + 0.01;
    }

    public static class BazaarMonitorItem {
        public final boolean isSellOrder;
        public final Book book;
        private final double price;
        private final long time;
        private final long delay;

        public BazaarMonitorItem(Book book, double price, boolean isSellOrder) {
            this(book, price, isSellOrder, System.currentTimeMillis(), DURATION);
        }

        private BazaarMonitorItem(Book book, double price, boolean isSellOrder, long time, long delay) {
            this.time = time;
            this.delay = delay;
            this.book = book;
            this.price = price;
            this.isSellOrder = isSellOrder;
        }
    }
}
