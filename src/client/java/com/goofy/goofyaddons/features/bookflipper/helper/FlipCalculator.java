package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.config.GoofyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.goofy.goofyaddons.features.TradingSafety;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class FlipCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlipCalculator.class);
    // All mutable state is owned by the client thread, including completions.
    private final Supplier<CompletableFuture<JsonObject>> fetch;
    private final Executor clientThread;
    private boolean running;
    private int generation;
    private CompletableFuture<JsonObject> request;
    private List<FlipItem> flipItemsList = List.of();

    public FlipCalculator() {
        this(BazaarApi::fetch, command -> Minecraft.getInstance().execute(command));
    }

    FlipCalculator(Supplier<CompletableFuture<JsonObject>> fetch, Executor clientThread) {
        this.fetch = fetch;
        this.clientThread = clientThread;
    }

    public void Refresh() {
        if (running) return;
        running = true;
        flipItemsList = List.of();
        int run = generation;
        List<Book> books = List.copyOf(GoofyConfig.INSTANCE.books);
        double tax = GoofyConfig.INSTANCE.bazaarTaxPercentage;
        double minimumProfit = GoofyConfig.INSTANCE.minNetProfit;
        try {
            request = fetch.get();
            request.whenComplete((root, error) -> clientThread.execute(() -> {
                if (run != generation) return;
                try {
                    if (error != null) throw new IllegalStateException("Bazaar request failed", error);
                    TradingSafety.sourceTime(root, System.currentTimeMillis());
                    flipItemsList = calculate(root.getAsJsonObject("products"), books, tax, minimumProfit);
                } catch (Exception failure) {
                    LOGGER.warn("Bazaar fetch failed; retrying later", failure);
                } finally {
                    running = false;
                    request = null;
                }
            }));
        } catch (Exception failure) {
            running = false;
            request = null;
            LOGGER.warn("Bazaar fetch failed; retrying later", failure);
        }
    }

    public void reset() {
        generation++;
        if (request != null) request.cancel(true);
        request = null;
        running = false;
        flipItemsList = List.of();
    }

    public boolean isRunning() {
        return running;
    }

    static List<FlipItem> calculate(JsonObject products, List<Book> books,
                                    double taxPercentage, double minimumProfit) {
        List<FlipItem> result = new ArrayList<>();
        for (Book book : books) {
            try {
                FlipItem item = evaluate(products, book, taxPercentage, minimumProfit);
                if (item != null) result.add(item);
            } catch (RuntimeException malformed) {
                // One malformed product must not hide every other route.
                LOGGER.warn("Skipping malformed Bazaar data for {}", book.getRomanLevel(book.level()), malformed);
            }
        }
        result.sort(Comparator.comparingDouble(FlipItem::score).reversed());
        return List.copyOf(result);
    }

    private static FlipItem evaluate(JsonObject products, Book book, double taxPercentage, double minimumProfit) {
        JsonObject buy = products.getAsJsonObject(book.getLevel(book.level()));
        JsonObject sell = products.getAsJsonObject(book.getLevel(book.sellLevel()));
        if (buy == null || sell == null) return null;
        double bid = topPrice(buy, "sell_summary");
        double ask = topPrice(buy, "buy_summary");
        double sellBid = topPrice(sell, "sell_summary");
        double sellAsk = topPrice(sell, "buy_summary");
        if (bid <= 0 || ask <= 0 || sellBid <= 0 || sellAsk <= 0) return null;
        boolean instaBuy = (ask - bid) / ask * 100 <= book.instaBuyPercentage();
        boolean instaSell = (sellAsk - sellBid) / sellAsk * 100 <= book.instaSellPercentage();
        // The UI uses the existing top order price. No invented price improvement.
        double cost = (instaBuy ? ask : bid) * book.getQtyAmount(book.level());
        double revenue = (instaSell ? sellBid : sellAsk) * (1 - taxPercentage / 100);
        double profit = revenue - cost;
        if (!Double.isFinite(profit) || profit <= 0 || profit < minimumProfit) return null;
        JsonObject buyQuick = buy.getAsJsonObject("quick_status");
        JsonObject sellQuick = sell.getAsJsonObject("quick_status");
        if (buyQuick == null || sellQuick == null) return null;
        // Historical execution flow, rather than standing order depth.
        double buyFlow = movingWeek(buyQuick, instaBuy ? "buyMovingWeek" : "sellMovingWeek")
                / book.getQtyAmount(book.level());
        double sellFlow = movingWeek(sellQuick, instaSell ? "sellMovingWeek" : "buyMovingWeek");
        double flow = Math.min(buyFlow, sellFlow);
        if (flow <= 0) return null;
        double score = profit * Math.log10(flow + 1) / Math.sqrt(cost);
        return new FlipItem(book, cost, score, instaBuy, instaSell);
    }

    private static double movingWeek(JsonObject quick, String key) {
        if (!quick.has(key)) return 0;
        double value = quick.get(key).getAsDouble();
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    private static double topPrice(JsonObject product, String side) {
        JsonArray orders = product.getAsJsonArray(side);
        if (orders == null || orders.isEmpty()) return -1;
        double price = orders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
        return Double.isFinite(price) ? price : -1;
    }

    public List<FlipItem> getFlipItemsList() {
        return flipItemsList;
    }
}
