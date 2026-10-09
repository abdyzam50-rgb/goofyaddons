package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.BookList;
import com.goofy.goofyaddons.features.bookflipper.helper.BookTransfer;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;

import java.util.HashMap;

/**
 * Moving tracked books between the inventory and the two storage pages: storing while a
 * buy order fills, and retrieving the books a combine or sale needs.
 *
 * <p>Each move is one {@link BookTransfer}, verified by slot memory. Inventory movement
 * carries no monetary submission, so a transfer that cannot be confirmed reinspects the
 * real locations instead of staying pending.
 */
final class BookStorage {
    private boolean usingSecondPage;

    boolean usingSecondPage() { return usingSecondPage; }
    void reset() { usingSecondPage = false; }

    void store(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.STORE);
        if (task == null && !ctx.needToStoreExcessBook()) {
            ctx.debug("[BazaarFlipper] STORE: no task left in STORE and nothing excess to store, going to IDLE");
            usingSecondPage = false;
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }

        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command(usingSecondPage ? ctx.settings().secondPage() : ctx.settings().firstPage());
        }

        if (storageOpen(ctx)) ctx.clock().start(ctx.delay());
        if (storageOpen(ctx) && ctx.scanner().isMenuLoaded(8) && ctx.clock().shouldFire()) {
            BookList bookList = null;
            if (ctx.needToStoreExcessBook()) {
                for (BookList book : ctx.extras()) {
                    if (book.location != 0) continue;
                    bookList = book;
                    break;
                }
            } else {
                for (BookList book : task.bookList) {
                    if (book.location != 0) continue;
                    bookList = book;
                    break;
                }
            }

            if (bookList != null)
                ctx.debug("[BazaarFlipper] STORE: handling level " + bookList.level + " " + bookList.book + " (excess=" + ctx.needToStoreExcessBook() + ")");

            if (bookList == null) {
                if (ctx.needToStoreExcessBook()) {
                    ctx.debug("[BazaarFlipper] STORE: finished storing excess books");
                    ctx.needToStoreExcessBook(false);
                    return;
                }

                ctx.debug("[BazaarFlipper] STORE: finished storing for " + task.getBook() + ", schedule was " + task.actionSchedule);
                switch (task.actionSchedule) {
                    case SELECTED_STORE_BUYORDER, SELECTED_COMBINE_STORE_BUYORDER -> {
                        task.setBookState(Task.BookState.IN_BUY_ORDER);
                        task.actionSchedule = Task.ActionSchedule.NONE;
                    }
                    case STORE_ANVIL -> {
                        task.setBookState(Task.BookState.ANVIL);
                        task.actionSchedule = Task.ActionSchedule.NONE;
                    }
                    default -> { }
                }
                return;
            }

            var result = transfer(ctx, bookList, usingSecondPage ? 2 : 1);
            if (result == BookTransfer.Result.NO_SPACE) {
                if (usingSecondPage) {
                    ctx.safetyHalt("Book storage is full on both pages; free space before trading continues.");
                    return;
                }
                usingSecondPage = true;
                ctx.actions().closeMenu();
            }
        }
    }

    void retrieve(BookContext ctx) {
        Task task = ctx.taskInState(Task.BookState.ANVIL);
        if (task == null) {
            ctx.debug("[BazaarFlipper] ANVIL: no task left in ANVIL, going to IDLE");
            usingSecondPage = false;
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }

        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) {
            ctx.actions().command(usingSecondPage ? ctx.settings().secondPage() : ctx.settings().firstPage());
        }

        if (storageOpen(ctx)) ctx.clock().start(ctx.delay());
        if (!storageOpen(ctx) || !ctx.scanner().isMenuLoaded(8) || !ctx.clock().shouldFire()) return;

        // if we have all the amount we pull out everything
        if (task.getAmountToOrder() == 0) {
            retrieveAll(ctx, task);
            return;
        }

        HashMap<Integer, Integer> futureItem = new HashMap<>();
        BookList bookList = null;

        // this is loop to handle what item to pick
        for (int i = 0; i < task.bookList.size(); i++) {
            BookList bookList1 = task.bookList.get(i);
            BookList bookList2 = i + 1 >= task.bookList.size() ? null : task.bookList.get(i + 1);

            if (bookList2 == null || bookList1.level != bookList2.level) {
                if (futureItem.getOrDefault(bookList1.level, 0) >= 1) {
                    int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 2 ? -2 : 1, Integer::sum);
                    if (newCount == 0) futureItem.merge(bookList1.level + 1, 1, Integer::sum);
                    ctx.debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " has a pending partner from a prior merge, projected level " + (bookList1.level + 1) + " count now " + futureItem.getOrDefault(bookList1.level + 1, 0));

                    if (bookList1.location == 0) {
                        ctx.debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " already in inventory, nothing to pull, continuing scan");
                        continue;
                    }
                    bookList = bookList1;
                    ctx.debug("[BazaarFlipper] ANVIL lookahead: picked unpaired level " + bookList1.level + " (location=" + bookList1.location + ") to pull for merge partner");
                    break;
                }
                ctx.debug("[BazaarFlipper] ANVIL lookahead: level " + bookList1.level + " unpaired and no pending partner, skipping");
                continue;
            }
            int newCount = futureItem.merge(bookList1.level + 1, futureItem.getOrDefault(bookList1.level + 1, 0) == 1 ? -1 : 1, Integer::sum);
            if (newCount == 0) futureItem.merge(bookList1.level + 2, 1, Integer::sum);
            ctx.debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + ", projected level " + (bookList1.level + 1) + " count now " + newCount + (newCount == 0 ? " (rolled over to level " + (bookList1.level + 2) + ")" : ""));

            i++;

            if (bookList1.location != 0) {
                bookList = bookList1;
                ctx.debug("[BazaarFlipper] ANVIL lookahead: picked first of pair, level " + bookList1.level + " (location=" + bookList1.location + ") to pull");
                break;
            }
            if (bookList2.location != 0) {
                bookList = bookList2;
                ctx.debug("[BazaarFlipper] ANVIL lookahead: picked second of pair, level " + bookList2.level + " (location=" + bookList2.location + ") to pull");
                break;
            }
            ctx.debug("[BazaarFlipper] ANVIL lookahead: matched pair at level " + bookList1.level + " already fully in inventory, continuing scan");
        }

        if (bookList == null) {
            ctx.debug("[BazaarFlipper] ANVIL: no more books to pull for merging on " + task.getBook() + ", schedule was " + task.actionSchedule);
            retrievalFinished(task);
            usingSecondPage = false;
            return;
        }

        ctx.debug("[BazaarFlipper] ANVIL: merge lookahead picked level " + bookList.level + " (location=" + bookList.location + ") to pull, projected future counts=" + futureItem);

        // here we check if we should move to the second page
        boolean needsSecondPage = bookList.location == 2;
        if (needsSecondPage != usingSecondPage) {
            ctx.debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
            usingSecondPage = needsSecondPage;
            ctx.actions().closeMenu();
            return;
        }

        // Books already in the inventory hold the slots they need. Counting them
        // again sent the task to STORE and straight back here, cycling.
        long toRetrieve = task.bookList.stream().filter(entry -> entry.location != 0).count();
        if (!ctx.transfer().pending() && toRetrieve > ctx.scanner().getEmptyInventorySlots()) {
            ctx.safetyHalt("Insufficient inventory space to retrieve tracked books; free space before resuming.");
            return;
        }

        transfer(ctx, bookList, 0);
    }

    /** The route holds every book it needs: pull each stored one into the inventory. */
    private void retrieveAll(BookContext ctx, Task task) {
        // here we check the amount we'll pull out and assign book one by one
        BookList bookToHandle = null;
        for (int i = 0; i < task.bookList.size(); i++) {
            BookList bookList = task.bookList.get(i);
            if (bookList.location == 0) continue;
            long remaining = task.bookList.stream().filter(entry -> entry.location != 0).count();
            if (!ctx.transfer().pending() && remaining > ctx.scanner().getEmptyInventorySlots()) {
                ctx.safetyHalt("Insufficient inventory space to retrieve tracked books; free space before resuming.");
                return;
            }
            // here we check if we should move to the second page
            boolean needsSecondPage = bookList.location == 2;
            if (needsSecondPage != usingSecondPage) {
                ctx.debug("[BazaarFlipper] ANVIL: level " + bookList.level + " is on page " + bookList.location + ", switching usingSecondPage from " + usingSecondPage + " to " + needsSecondPage);
                usingSecondPage = needsSecondPage;
                ctx.actions().closeMenu();
                return;
            }
            bookToHandle = bookList;
            break;
        }

        // first we handle if we have no books to pull out
        if (bookToHandle == null) {
            ctx.debug("[BazaarFlipper] ANVIL: nothing left to pull out for " + task.getBook() + ", schedule was " + task.actionSchedule);
            retrievalFinished(task);
            usingSecondPage = false;
            return;
        }

        transfer(ctx, bookToHandle, 0);
    }

    private static void retrievalFinished(Task task) {
        switch (task.actionSchedule) {
            case ANVIL_SELL -> task.setBookState(Task.BookState.SELL);
            case SELECTED_COMBINE_STORE_BUYORDER, NONE -> task.setBookState(Task.BookState.COMBINE);
            default -> { }
        }
    }

    private static boolean storageOpen(BookContext ctx) {
        return ctx.containerNameCheck("Ender Chest") || ctx.containerNameCheck("Jumbo Backpack") || ctx.containerNameCheck("Greater Backpack");
    }

    BookTransfer.Result transfer(BookContext ctx, BookList book, int target) {
        var bookTransfer = ctx.transfer();
        int previousRetries = bookTransfer.actionRetries();
        var result = bookTransfer.tick(book, target,
                usingSecondPage ? ctx.settings().secondPage() : ctx.settings().firstPage(),
                ctx.menu(), ctx.actions(), ctx.now(), ctx.inventoryMemory());
        if (bookTransfer.actionRetries() > previousRetries) ctx.services().event("WARN", "books.transfer_action_retried",
                java.util.Map.of("item", book.book.id(), "level", book.level, "destination", target,
                        "attempt", bookTransfer.actionRetries(), "memory", ctx.inventoryMemory().diagnosticState()));
        if (result == BookTransfer.Result.BLOCKED) {
            String reason = bookTransfer.failure();
            if (ctx.inventoryMemory().fresh(0) && (reason.startsWith("Book transfer timed out")
                    || reason.startsWith("Unexpected book quantities"))) {
                // Inventory movement has no outstanding monetary submission. Reinspect actual locations
                // instead of keeping a missing physical book in a permanently pending transfer.
                ctx.services().event("WARN", "books.transfer_reconciliation", java.util.Map.of(
                        "reason", reason, "memory", ctx.inventoryMemory().diagnosticState()));
                bookTransfer.reset(); ctx.inventoryMemory().finishMove();
                ctx.startLocationReconciliation();
                ctx.state(State.IDLE);
                if (ctx.screenOpen()) ctx.actions().closeMenu();
                ctx.clock().stop();
                return BookTransfer.Result.WAITING;
            }
            ctx.safetyHalt(reason);
        }
        if (result == BookTransfer.Result.NO_SPACE && target == 0)
            ctx.safetyHalt("Insufficient inventory space for book retrieval; location retained.");
        return result;
    }
}
