package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeneralCalculator {
    public record Candidate(GeneralItem item, int quantity, double bid, double ask, double profit, double score) {
        public double cost() { return quantity * bid; }
    }

    private GeneralCalculator() {}

    public static List<Candidate> calculate(JsonObject products, GeneralSettings settings,
                                            double taxPercentage, double available, int inventoryCapacity) {
        if (products == null || !Double.isFinite(available) || available <= 0 || inventoryCapacity <= 0
                || !Double.isFinite(taxPercentage) || taxPercentage < 0 || taxPercentage >= 100) return List.of();
        List<Candidate> result = new ArrayList<>();
        for (GeneralItem item : settings.items) {
            JsonObject product = products.getAsJsonObject(item.id());
            if (product == null) continue;
            double bid = topPrice(product, "sell_summary");
            double ask = topPrice(product, "buy_summary");
            JsonObject quick = product.getAsJsonObject("quick_status");
            if (quick == null || bid <= 0 || ask <= bid) continue;
            double flow = Math.min(number(quick, "sellMovingWeek"), number(quick, "buyMovingWeek"));
            if (flow < settings.minWeeklyVolume || flow <= 0) continue;
            double net = ask * (1 - taxPercentage / 100) - bid;
            if (net <= 0 || net / bid * 100 < settings.minMarginPercentage) continue;
            // Avoid placing more than about one hour of historical market flow.
            int quantity = (int) Math.min(Math.min(settings.maxItemsPerOrder, inventoryCapacity),
                    Math.min(Math.floor(Math.min(settings.maxCoinsPerItem, available) / bid), Math.floor(flow / 168)));
            if (quantity <= 0 || net * quantity < settings.minProfitPerBatch) continue;
            result.add(new Candidate(item, quantity, bid, ask, net * quantity,
                    net * quantity * Math.log10(flow + 1) / Math.sqrt(bid * quantity)));
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return List.copyOf(result);
    }

    public static double topPrice(JsonObject product, String side) {
        JsonArray summary = product.getAsJsonArray(side);
        if (summary == null || summary.isEmpty()) return -1;
        double value = summary.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
        return Double.isFinite(value) && value > 0 ? value : -1;
    }

    private static double number(JsonObject object, String key) {
        if (!object.has(key)) return 0;
        double value = object.get(key).getAsDouble();
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
