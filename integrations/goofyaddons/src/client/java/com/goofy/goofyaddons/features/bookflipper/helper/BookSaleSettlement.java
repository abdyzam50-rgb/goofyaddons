package com.goofy.goofyaddons.features.bookflipper.helper;

/** A matching receipt and observed disappearance settle a sale; absence alone never does. */
public final class BookSaleSettlement {
    private BookSaleSettlement() {}
    public enum Result { WAITING, COMPLETE, UNCONFIRMED }
    public static Result check(boolean orderPresent,boolean bookInInventory,boolean receiptSeen,
                               long age,long grace) {
        if (receiptSeen && !orderPresent && !bookInInventory) return Result.COMPLETE;
        return age>=0 && age<grace?Result.WAITING:Result.UNCONFIRMED;
    }
}
