package com.goofy.goofyaddons.features.generalflipper;

import java.util.List;

public class GeneralSettings {
    public List<GeneralItem> items = List.of(
            new GeneralItem("ENCHANTED_SUGAR", "Enchanted Sugar"),
            new GeneralItem("ENCHANTED_REDSTONE", "Enchanted Redstone"),
            new GeneralItem("ENCHANTED_GOLD", "Enchanted Gold"),
            new GeneralItem("ENCHANTED_LAPIS_LAZULI", "Enchanted Lapis Lazuli"),
            new GeneralItem("ENCHANTED_COAL", "Enchanted Coal"),
            new GeneralItem("ENCHANTED_IRON", "Enchanted Iron"));
    public double maxCoinsPerItem = 25_000_000;
    public int maxItemsPerOrder = 256;
    public int maxActiveItems = 3;
    public double minProfitPerBatch = 25_000;
    public double minMarginPercentage = 2;
    public double minWeeklyVolume = 10000;
    public int refreshSeconds = 20;
    public int orderTimeoutSeconds = 180;
    public int repriceCooldownSeconds = 60;
    public int maxReprices = 3;
    public int maxHoldingSeconds = 21600;
    public double maxDrawdownPercentage = 15;

    public void validate() {
        if (items == null || items.stream().anyMatch(item -> item == null || item.id() == null
                || !item.id().matches("[A-Z0-9_]+") || item.id().startsWith("ENCHANTMENT_")
                || item.name() == null || item.name().isBlank())
                || items.stream().map(GeneralItem::id).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Invalid or duplicate general-flip items");
        }
        if (!Double.isFinite(maxCoinsPerItem) || maxCoinsPerItem <= 0
                || maxItemsPerOrder < 1 || maxItemsPerOrder > 4096 || maxActiveItems < 1 || maxActiveItems > 10
                || !Double.isFinite(minProfitPerBatch) || minProfitPerBatch < 0
                || !Double.isFinite(minMarginPercentage) || minMarginPercentage < 0 || minMarginPercentage > 100
                || !Double.isFinite(minWeeklyVolume) || minWeeklyVolume < 0
                || refreshSeconds < 10 || orderTimeoutSeconds < 30 || repriceCooldownSeconds < 30
                || maxReprices < 0 || maxReprices > 10 || maxHoldingSeconds < 60
                || !Double.isFinite(maxDrawdownPercentage) || maxDrawdownPercentage <= 0 || maxDrawdownPercentage > 100) {
            throw new IllegalArgumentException("Invalid general-flipper limits");
        }
    }
}
