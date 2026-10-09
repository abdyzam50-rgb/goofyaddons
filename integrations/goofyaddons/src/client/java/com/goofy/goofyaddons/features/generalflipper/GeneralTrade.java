package com.goofy.goofyaddons.features.generalflipper;

/**
 * Evidence gathered while one retained position is being worked on.
 *
 * <p>It belongs to the current visit, not to the saved position: everything here is
 * rebuilt from fresh observations the next time the position is selected.
 */
final class GeneralTrade {
    /** Whether this visit places or settles the sell side. */
    boolean selling;
    /** Inventory count of the item before a claim or cancellation was clicked. */
    int inventoryBefore;
    /** Units the pending claim or cancellation must return to the inventory. */
    int expectedClaim;
    /** A matching chat receipt was seen for the pending claim or cancellation. */
    boolean receipt;
    /** A completed sale was clicked and awaits its receipt and order removal. */
    boolean claimPending;
    int claimUnits;
    /** Units already sold on a sell offer that is being cancelled for repricing. */
    int cancelSoldUnits;
    boolean reopenedCancelOptions;
    /** The server said a buy order gained claimable goods while it was being cancelled. */
    boolean cancelNeedsClaim;
    /** Coins named by the sale receipt, when one was seen. */
    Double claimedProceeds;
}
