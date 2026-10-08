package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.features.transaction.ClaimEvidence;

/**
 * Claiming books from a filled buy order and coins from a completed sell offer.
 *
 * <p>A buy claim is acknowledged only by the exact expected increase of input books in the
 * inventory, never by generic chat. A sale claim needs its receipt, or the order's removal
 * together with the book's absence, before the sale is recorded.
 */
final class BookClaim {
    private Task pendingBuy;
    private int buyBefore;
    private int buyExpected;
    private Double buyUnitPrice;
    private String buyEvent;
    private Task pendingSale;
    private boolean saleReceipt;
    private Double saleProceeds;
    /** When the sale claim was clicked, so a slow receipt does not read as an unexplained loss. */
    private long saleClaimAt;
    /** A claim click was sent from the orders menu and its inventory change is awaited. */
    private boolean attempted;
    private boolean received;

    Task pendingBuy() { return pendingBuy; }
    Task pendingSale() { return pendingSale; }
    int buyBefore() { return buyBefore; }
    int buyExpected() { return buyExpected; }
    boolean saleReceipt() { return saleReceipt; }
    long saleClaimAt() { return saleClaimAt; }
    Double saleProceeds() { return saleProceeds; }
    boolean attempted() { return attempted; }

    /** Clears the claim-attempt latch once its items arrived; false while still waiting. */
    boolean settledAttempt() {
        if (!attempted) return true;
        if (!received) return false;
        attempted = false;
        received = false;
        return true;
    }

    void resetAttempt() { attempted = false; received = false; }

    void reset() {
        pendingBuy = null; buyBefore = 0; buyExpected = 0; buyUnitPrice = null; buyEvent = null;
        clearSale();
    }

    void beginBuy(BookContext ctx, Task task, int amount, int slot) {
        if (amount <= 0) return;
        pendingBuy = task;
        buyBefore = inputBooksInInventory(ctx, task);
        buyExpected = amount;
        var lore = ctx.menu().slot(slot).loreLines();
        buyUnitPrice = lore == null ? null : TradeReceipts.unitPrice(String.join("\n", lore));
        buyEvent = java.util.UUID.randomUUID().toString();
        attempted = true;
        received = false;
    }

    /**
     * Checks a pending buy claim against the inventory. False while the claim is still
     * unacknowledged or after it halted; true when no claim is pending or it was assigned.
     */
    boolean verifyBuy(BookContext ctx) {
        if (pendingBuy == null) return true;
        int observed = inputBooksInInventory(ctx, pendingBuy);
        var outcome = ClaimEvidence.observe(buyBefore, buyExpected, observed);
        if (outcome == ClaimEvidence.Outcome.PENDING) return false;
        if (outcome == ClaimEvidence.Outcome.EXCESS) {
            ctx.safetyHalt("Book claim quantity differs from the expected inventory increase; ownership retained.");
            return false;
        }
        ctx.accounting().acquire(pendingBuy.getProfitTradeId(), "books", pendingBuy.getBook().name(),
                buyEvent, buyExpected, buyUnitPrice == null ? null : buyUnitPrice * buyExpected);
        assign(ctx, pendingBuy, buyExpected);
        pendingBuy = null;
        received = true;
        return true;
    }

    void beginSale(Task task, long now) {
        pendingSale = task;
        saleReceipt = false;
        saleProceeds = null;
        saleClaimAt = now;
    }

    void clearSale() { pendingSale = null; saleReceipt = false; saleProceeds = null; saleClaimAt = 0; }

    void claimedMessage(String message) {
        if (pendingSale != null && TradingSafety.claimReceipt(message,
                pendingSale.getBook().getRomanLevel(pendingSale.getBook().sellLevel()), 1)) {
            saleReceipt = true;
            saleProceeds = TradeReceipts.saleProceeds(message,
                    pendingSale.getBook().getRomanLevel(pendingSale.getBook().sellLevel()), 1);
        }
        // Buy claims are acknowledged by an observed inventory increase, never generic chat.
    }

    private static int inputBooksInInventory(BookContext ctx, Task task) {
        return (int) ctx.scanner().matchingBookInInventory(task.getBook()).stream()
                .filter(slot -> ctx.scanner().getLevel(slot) == task.getBook().level()).count();
    }

    private static void assign(BookContext ctx, Task task, int amount) {
        if (amount > task.getAmountToOrder()) {
            int requiredAmount = task.getAmountToOrder();
            int remainder = amount - requiredAmount;
            ctx.debug("[BazaarFlipper] handleItemAssigning: received " + amount + " of " + task.getBook() + " vs amountToOrder=" + task.getAmountToOrder() + " -> assignBook(newAmount=" + requiredAmount + "), handleBookList(amount=" + remainder + ")");
            task.assignBook(task.getBook(), task.getBook().level(), 0, requiredAmount);
            ctx.handleBookList(task.getBook(), 0, task.getBook().level(), remainder);
            return;
        }
        task.assignBook(task.getBook(), task.getBook().level(), 0, amount);
    }
}
