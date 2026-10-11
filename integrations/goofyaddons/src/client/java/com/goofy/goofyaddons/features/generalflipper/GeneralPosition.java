package com.goofy.goofyaddons.features.generalflipper;

/** Saved trade state, independent of the menu-navigation step. Field names are the existing journal contract. */
public final class GeneralPosition {
    enum Stage { PLANNED, BUY_ORDER, INVENTORY, SELL_ORDER, RECONCILE }
    GeneralItem item;
    int quantity;
    double unitCost;
    double sellPrice;
    Stage stage;
    boolean submitted;
    boolean cancelRequested;
    String rejectedReason;
    int reprices;
    long placedAt;
    long heldSince;
    long checkedAt;
    String tradeId;
    String productionParent;
    int productionInventoryBefore;
    String saleEvent;
    boolean purchasePriceKnown;
    com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast forecast;
    Double confirmedCancelRefund;
    boolean settlementPending;
    boolean instantBuy,instantSell;
    boolean productionBuy;
    boolean completed;
    Double verifiedProceeds;
    double maximumBuyPrice;
    double minimumSellPrice;
    double cost() { return quantity * unitCost; }
    int acquiredInventory(int total) { return total - (productionBuy ? productionInventoryBefore : 0); }

    /** Validate the full batch before an engine can adopt any ownership from a storage adapter. */
    static java.util.List<GeneralPosition> validated(java.util.List<GeneralPosition> positions) {
        if (positions == null) throw new IllegalArgumentException("Missing order state");
        var ids = new java.util.HashSet<String>();
        var parents=new java.util.HashSet<String>();
        for (GeneralPosition position : positions) {
            if (position == null || position.item == null || position.item.id() == null || position.item.name() == null
                    || position.quantity <= 0 || position.quantity > 4096 || !Double.isFinite(position.unitCost)
                    || position.unitCost <= 0 || !Double.isFinite(position.cost())
                    || !position.item.id().matches("[A-Z0-9_]+") || position.item.id().startsWith("ENCHANTMENT_")
                    || position.item.name().isBlank()
                    || position.confirmedCancelRefund != null && (!position.cancelRequested || position.stage != GeneralPosition.Stage.BUY_ORDER
                        || !position.purchasePriceKnown || !Double.isFinite(position.confirmedCancelRefund)
                        || Math.abs(position.confirmedCancelRefund - position.cost()) > 0.51)
                    || !Double.isFinite(position.maximumBuyPrice) || position.maximumBuyPrice<0
                    || !Double.isFinite(position.minimumSellPrice) || position.minimumSellPrice<0
                    || position.productionBuy && position.maximumBuyPrice<=0
                    || position.verifiedProceeds!=null && (!Double.isFinite(position.verifiedProceeds)||position.verifiedProceeds<0)
                    || position.productionParent!=null && (!position.productionParent.matches("[A-Za-z0-9_-]{8,80}") || position.instantBuy || position.instantSell || !parents.add(position.productionParent))
                    || position.productionInventoryBefore<0 || position.productionInventoryBefore>4096
                    || position.productionInventoryBefore>0 && (!position.productionBuy || position.productionParent==null)
                    || position.rejectedReason!=null && (!position.productionBuy || position.submitted || !position.completed || position.stage!=Stage.PLANNED)
                    || position.stage == null || !ids.add(position.item.id())) {
                throw new IllegalArgumentException("Invalid saved order position");
            }
        }
        return java.util.List.copyOf(positions);
    }
}
