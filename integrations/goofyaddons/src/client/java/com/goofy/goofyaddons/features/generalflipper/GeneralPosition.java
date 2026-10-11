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
    /** Units of this production buy the run moved from the inventory into storage between claims. */
    int productionStoredUnits;
    /** A partial claim filled the inventory; the run must store these units before the next claim. */
    boolean productionAwaitingStorage;
    String saleEvent;
    boolean purchasePriceKnown;
    com.goofy.goofyaddons.features.profit.ExecutionLedger.Forecast forecast;
    Double confirmedCancelRefund;
    boolean settlementPending;
    boolean instantBuy,instantSell;
    boolean productionBuy;
    boolean productionBasket;
    boolean completed;
    Double verifiedProceeds;
    int productionSoldUnits;
    String productionSaleEvent;
    boolean productionProceedsUnknown;
    double maximumBuyPrice;
    double minimumSellPrice;
    /** BUY, SELL or BOTH for a player-started flip test, which skips the profit filters; null otherwise. */
    String test;
    double cost() { return quantity * unitCost; }
    int acquiredInventory(int total) { return productionBuy ? total - productionInventoryBefore + productionStoredUnits : total; }

    /** Ordinary flips keep books in their own engine; scoped craft jobs may use one exact Bazaar enchantment. */
    static boolean validProduct(String id,boolean production) {
        if(id==null || !id.matches("[A-Z0-9_]+"))return false;
        if(!id.startsWith("ENCHANTMENT_"))return true;
        if(!production || !id.matches("ENCHANTMENT_[A-Z][A-Z0-9_]*_[1-9][0-9]*"))return false;
        try{return Integer.parseInt(id.substring(id.lastIndexOf('_')+1))<=100;}
        catch(NumberFormatException invalid){return false;}
    }

    /** Validate the full batch before an engine can adopt any ownership from a storage adapter. */
    static java.util.List<GeneralPosition> validated(java.util.List<GeneralPosition> positions) {
        if (positions == null) throw new IllegalArgumentException("Missing order state");
        var ids = new java.util.HashSet<String>();
        var parents=new java.util.HashMap<String,Boolean>();
        for (GeneralPosition position : positions) {
            if (position == null || position.item == null || position.item.id() == null || position.item.name() == null
                    || position.quantity <= 0 || position.quantity > 4096 || !Double.isFinite(position.unitCost)
                    || position.unitCost <= 0 || !Double.isFinite(position.cost())
                    || !validProduct(position.item.id(),position.productionParent!=null)
                    || position.item.name().isBlank()
                    || position.confirmedCancelRefund != null && (!position.cancelRequested || position.stage != GeneralPosition.Stage.BUY_ORDER
                        || !position.purchasePriceKnown || !Double.isFinite(position.confirmedCancelRefund)
                        || Math.abs(position.confirmedCancelRefund - position.cost()) > 0.51)
                    || !Double.isFinite(position.maximumBuyPrice) || position.maximumBuyPrice<0
                    || !Double.isFinite(position.minimumSellPrice) || position.minimumSellPrice<0
                    || position.productionSoldUnits<0 || position.productionSoldUnits>4096
                    || (position.productionSoldUnits>0 || position.productionSaleEvent!=null || position.productionProceedsUnknown) && position.productionBuy
                    || position.productionProceedsUnknown && position.verifiedProceeds!=null
                    || position.productionBuy && position.maximumBuyPrice<=0
                    || position.verifiedProceeds!=null && (!Double.isFinite(position.verifiedProceeds)||position.verifiedProceeds<0)
                    || position.productionParent!=null && (!position.productionParent.matches("[A-Za-z0-9_-]{8,80}") || position.instantBuy || position.instantSell
                        || parents.containsKey(position.productionParent) && (!position.productionBuy || !position.productionBasket || !parents.get(position.productionParent)))
                    || position.productionBasket && (!position.productionBuy || position.productionParent==null)
                    || position.productionInventoryBefore<0 || position.productionInventoryBefore>4096
                    || position.productionInventoryBefore>0 && (!position.productionBuy || position.productionParent==null)
                    || position.productionStoredUnits<0 || position.productionStoredUnits>position.quantity
                    || (position.productionStoredUnits>0 || position.productionAwaitingStorage) && (!position.productionBuy || position.productionParent==null)
                    || position.test!=null && (!java.util.Set.of("BUY","SELL","BOTH").contains(position.test) || position.productionParent!=null)
                    || position.rejectedReason!=null && (!position.productionBuy || position.submitted || !position.completed || position.stage!=Stage.PLANNED)
                    || position.stage == null || !ids.add(position.item.id())) {
                throw new IllegalArgumentException("Invalid saved order position");
            }
            if(position.productionParent!=null)parents.put(position.productionParent,position.productionBuy&&position.productionBasket);
        }
        return java.util.List.copyOf(positions);
    }
}
