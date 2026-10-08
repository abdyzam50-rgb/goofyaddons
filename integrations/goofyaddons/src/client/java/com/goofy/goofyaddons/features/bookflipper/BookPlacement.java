package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.MenuObservationStability;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.features.transaction.RecoveryRules;

/**
 * The irreversible half of entering a book order: the selected price, the confirmation
 * click, and verifying the order the server then lists.
 *
 * <p>A confirmation is clicked only for the price this engine selected moments earlier,
 * after the intent is saved. A submitted order that does not appear is rechecked and then
 * retained; it is never submitted again because its result is unknown.
 */
final class BookPlacement {
    private final MenuObservationStability confirmationStability = new MenuObservationStability();
    private Task confirmationTask;
    private double confirmationPrice;
    private boolean confirmationSelling;
    private long confirmationSelectedAt;
    private Task submittedTask;
    private Task.BookState submittedNextState;
    private boolean submittedSelling;
    private int submittedUnits;
    private double submittedPrice;
    private long submittedAt;

    Task submittedTask() { return submittedTask; }
    int submittedUnits() { return submittedUnits; }
    double submittedPrice() { return submittedPrice; }
    boolean submittedSelling() { return submittedSelling; }

    void reset() {
        submittedTask = null; submittedNextState = null; submittedSelling = false;
        submittedUnits = 0; submittedPrice = 0; submittedAt = 0;
        confirmationTask = null; confirmationPrice = 0; confirmationSelling = false; confirmationSelectedAt = 0;
        confirmationStability.reset();
    }

    /** The price a navigation click is about to accept; the confirmation must show exactly this. */
    void priceSelected(Task task, double price, boolean selling, long now) {
        confirmationTask = task; confirmationPrice = price; confirmationSelling = selling;
        confirmationSelectedAt = now; confirmationStability.reset();
    }

    /** Forgets a selected price whose confirmation will not be clicked. */
    void abandonConfirmation() { confirmationTask = null; confirmationStability.reset(); }

    /**
     * Saves the intent, clicks the confirmation once and moves to placement verification.
     * False when any check or the checkpoint failed and nothing was clicked.
     */
    boolean submit(BookContext ctx, Task task, boolean sale, Task.BookState nextState) {
        if (!verifyConfirmation(ctx)) return false;
        if (!recordSubmission(ctx, task, sale)) return false;
        ctx.actions().click(13, false);
        confirmationTask = null;
        ctx.yieldFor(1000);
        submittedNextState = nextState;
        return true;
    }

    /** Sets where a verified buy goes next, once the route is known after the click. */
    void nextState(Task.BookState next) { submittedNextState = next; }

    private boolean verifyConfirmation(BookContext ctx) {
        Task task = confirmationTask;
        boolean sale = ctx.state() == State.SELL || ctx.state() == State.REPLACE_SELL;
        if (!sale && task != null && !java.util.Set.of(Task.ActionSchedule.NONE, Task.ActionSchedule.SELECTED_STORE_BUYORDER, Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER).contains(task.actionSchedule)) {
            ctx.safetyHalt("Book buy schedule is incompatible with submission; no confirmation clicked."); return false;
        }
        if (task == null || confirmationSelling != sale || !ctx.tasks().contains(task)
                || ctx.now() - confirmationSelectedAt > RecoveryRules.SELECTION_EXPIRY_MS || !sale && task != ctx.activeTask()) {
            ctx.safetyHalt("Book confirmation has no matching price-selection intent."); return false;
        }
        var stack = ctx.menu().slot(13);
        var lore = stack.loreLines();
        String text = lore == null ? "" : String.join("\n", lore);
        if (!confirmationStability.ready(ctx.menu().containerId(),
                ctx.menuTitle() + "\n" + stack.hoverName() + "\n" + text, !stack.empty() && lore != null, ctx.now())) return false;
        int quantity = sale ? 1 : task.getAmountToOrder();
        String item = task.getBook().getRomanLevel(sale ? task.getBook().sellLevel() : task.getBook().level());
        boolean previewMatches = com.goofy.goofyaddons.features.ConfirmationCheck.matches(ctx.menuTitle(), sale,
                stack.hoverName(), text, item, quantity, confirmationPrice);
        boolean profitAllowed = ctx.bookPriceAllowed(task, confirmationPrice, sale);
        ctx.services().event(previewMatches && profitAllowed ? "INFO" : "ERROR", "books.confirmation_check", java.util.Map.of(
                "trade", task.getProfitTradeId(), "expectedItem", item, "expectedUnits", quantity, "expectedUnitPrice", confirmationPrice,
                "previewMatches", previewMatches, "profitAllowed", profitAllowed, "context", ctx.services().diagnosticContext()));
        if (!previewMatches || !profitAllowed) {
            ctx.safetyHalt(!previewMatches ? "Book confirmation item, quantity or price could not be verified."
                    : sale ? "Book sale confirmation price or market data could not be verified."
                    : "Book buy confirmation no longer meets minimum net profit."); return false;
        }
        if (sale) {
            if (ctx.scanner().findLoreInv(item).size() != 1) {
                ctx.safetyHalt("Book sale inventory changed before confirmation."); return false;
            }
        } else {
            double purse = ctx.services().purse();
            if (!ctx.purchasePurseReady(purse)) return false;
            double cost = confirmationPrice * quantity;
            int empty = ctx.scanner().getEmptyInventorySlots();
            String product = task.getBook().id();
            double hold = Math.max(ctx.capital().cost("books", product),
                    task.getReservedUnitCost() * task.getBook().getQtyAmount(task.getBook().level()));
            // One compound condition used to cover four separate causes and halt with one
            // message, so a field report could not say which of them fired. Each arm now
            // names itself, and the ledger numbers go into the event either way.
            String cause = quantity > empty ? "inventory-capacity" : cost > purse ? "cost-exceeds-purse"
                    : ctx.capital().refusal(product, hold, cost, purse);
            if (cause == null && !ctx.capital().resize("books", product, hold, cost, purse)) cause = "ledger-resize-refused";
            if (cause != null) {
                java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("trade", task.getProfitTradeId()); detail.put("cause", cause);
                detail.put("units", quantity); detail.put("emptySlots", empty);
                detail.put("orderCost", cost); detail.put("holdRequested", hold); detail.put("purse", purse);
                detail.put("committed", ctx.capital().committed());
                detail.put("pending", ctx.capital().pending());
                detail.put("capitalLimit", ctx.capital().limit());
                detail.put("reserve", ctx.capital().reserve());
                ctx.services().event("ERROR", "books.capital_check_failed", detail);
                if (java.util.Set.of("cost-exceeds-purse", "purse-below-reserve", "purse-minus-pending-too-low", "capital-limit-reached").contains(cause))
                    ctx.deferBookPurchase(task, cause);
                else ctx.safetyHalt("Book buy blocked before confirmation (" + cause + "); position retained.");
                return false;
            }
        }
        return true;
    }

    private boolean recordSubmission(BookContext ctx, Task task, boolean sale) {
        // Save the uncertainty barrier before the server can receive a confirmation click.
        ctx.expose(task.getBook().id());
        if (!ctx.checkpoint()) return false;
        submittedTask = task; submittedSelling = sale; submittedAt = ctx.now();
        submittedUnits = sale ? 1 : task.getAmountToOrder(); submittedPrice = confirmationPrice;
        ctx.services().event("INFO", "order.submission_intent", java.util.Map.of("engine", "books", "trade", task.getProfitTradeId(),
                "units", submittedUnits, "unitPrice", submittedPrice, "selling", sale));
        return true;
    }

    void verify(BookContext ctx) {
        Task task = submittedTask;
        if (task == null) { ctx.safetyHalt("Book submission intent missing; ownership retained."); return; }
        if (ctx.now() - submittedAt < RecoveryRules.ORDER_SETTLE_MS) return;
        if (!ctx.screenOpen()) {
            ctx.clock().start(ctx.delay());
            if (ctx.clock().shouldFire()) { ctx.actions().command("managebazaarorders"); ctx.clock().stop(); }
            return;
        }
        boolean orders = TradingSafety.ordersTitle(ctx.menuTitle());
        if (!orders || !ctx.scanner().isMenuLoaded(35) || ctx.scanner().findContainer("Close").isEmpty()) {
            if (!ctx.recheckBookOrders(task, "placement-menu-not-ready")) ctx.safetyHalt("Cannot load fresh book order verification; intent retained.");
            return;
        }
        String item = task.getBook().getRomanLevel(submittedSelling ? task.getBook().sellLevel() : task.getBook().level());
        var slots = ctx.scanner().findContainer((submittedSelling ? "SELL " : "BUY ") + item);
        if (slots.isEmpty()) {
            if (!ctx.recheckBookOrders(task, "submitted-order-not-visible")) ctx.safetyHalt("Submitted book order still absent after fresh-menu checks; intent retained without resubmitting.");
            return;
        }
        var lore = ctx.menu().slot(slots.getFirst()).loreLines();
        String text = lore == null ? "" : String.join("\n", lore);
        Integer units = com.goofy.goofyaddons.features.generalflipper.OrderLore.total(text);
        Double price = TradeReceipts.unitPrice(text);
        if ((units == null || price == null) && ctx.recheckBookOrders(task, "placement-fields-unreadable")) return;
        if (!TradingSafety.orderMatchesIntent(submittedUnits, submittedPrice, units, price)) {
            ctx.safetyHalt("Submitted book order amount/price differs or is unreadable; intent retained."); return;
        }
        ctx.services().event("INFO", "order.verified", java.util.Map.of("engine", "books", "trade", task.getProfitTradeId(), "item", item, "units", submittedUnits));
        if (!submittedSelling) {
            ctx.capital().purchased("books", task.getBook().id());
            var book = task.getBook();
            if (submittedUnits == book.getQtyAmount(book.level())) ctx.accounting().beginExecution(task.getProfitTradeId(), "books",
                    book.getLevel(book.level()), book.getLevel(book.sellLevel()), submittedUnits, 1, submittedAt,
                    expectedProfit(ctx, book, submittedUnits, submittedPrice),
                    task.forecast());
        }
        task.setBookState(submittedNextState);
        submittedTask = null; submittedNextState = null;
        ctx.state(State.IDLE); ctx.clock().stop();
    }

    private static Double expectedProfit(BookContext ctx, Book book, int units, double price) {
        var latest = ctx.services().latestQuotes(); var products = latest == null ? null : latest.getAsJsonObject("products");
        var output = products == null ? null : products.getAsJsonObject(book.getLevel(book.sellLevel()));
        double ask = output == null ? -1 : com.goofy.goofyaddons.features.generalflipper.GeneralCalculator.topPrice(output, "buy_summary");
        double expected = ask * (1 - ctx.settings().bazaarTaxPercentage() / 100) - units * price;
        return ask > 0 && Double.isFinite(expected) && expected > 0 ? expected : null;
    }
}
