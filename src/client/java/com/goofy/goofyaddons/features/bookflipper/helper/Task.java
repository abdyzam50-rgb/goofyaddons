package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Task {
    public enum BookState {
        BAZAAR_ORDER_CHECK,
        SELECTED,
        IN_BUY_ORDER,
        OUTBID,
        STORE,
        ANVIL,
        COMBINE,
        SELL,
        SELL_ORDER,
        REPLACE_SELL,
        /** Waiting too long on a placed order; needs a read-only look at the orders list. */
        VERIFY_ORDER
    }

    public enum ActionSchedule {
        NONE,
        SELECTED_COMBINE_STORE_BUYORDER,
        SELECTED_STORE_BUYORDER,
        ANVIL_SELL,
        STORE_ANVIL
    }

    public boolean instaSell = false;
    public boolean instaBuy = false;
    public ActionSchedule actionSchedule = ActionSchedule.NONE;
    private Book book;
    private int amountToOrder;
    private double reservedUnitCost;
    private final String profitTradeId;
    private BookState bookState;
    private long orderWaitSince;
    private boolean awaitingSale;
    private boolean recovered;
    public boolean recovered(){return recovered;}
    public void markRecovered(){recovered=true;}
    // book location will be represented in integars, 0 = Inventory, 1 = EnderChest, 2 = EnderChestPage2
    public List<BookList> bookList = new ArrayList<>();


    public Task(Book book, boolean instaBuy, boolean instaSell) {
        this(book,instaBuy,instaSell,java.util.UUID.randomUUID().toString());
    }

    public Task(Book book, boolean instaBuy, boolean instaSell, String tradeId) {
        if(tradeId==null || tradeId.isBlank())throw new IllegalArgumentException("Missing trade identity");
        profitTradeId=tradeId;
        this.book = book;
        this.instaBuy = instaBuy;
        this.instaSell = instaSell;
        amountToOrder = book.getQtyAmount(book.level());
    }

    public Book getBook() {
        return book;
    }
    public String getProfitTradeId() { return profitTradeId; }

    public BookState getBookState() {
        return bookState;
    }

    public void setBookState(BookState bookState) {
        this.bookState = bookState;
        // Entering a wait restarts its clock, and records which side is being waited on,
        // so a later re-check knows whether to look for a BUY or a SELL entry. Promotion
        // to VERIFY_ORDER deliberately leaves both alone.
        if (bookState == BookState.IN_BUY_ORDER || bookState == BookState.SELL_ORDER) {
            orderWaitSince = System.currentTimeMillis();
            awaitingSale = bookState == BookState.SELL_ORDER;
        }
    }

    /** When the current order wait began, or 0 when this task is not waiting. */
    public long orderWaitSince() { return orderWaitSince; }

    /** Whether the wait is on a sell offer rather than a buy order. */
    public boolean awaitingSale() { return awaitingSale; }

    /** Records that the order was seen, restarting the wait without changing state. */
    public void markOrderObserved(long now) { orderWaitSince = now; }

    /**
     * How often this route has been outbid and re-placed, and when it was last placed. The
     * general engine has carried a reprice budget and cooldown since it was written; the book
     * engine re-placed an outbid order immediately and without limit, so a contested book
     * churned cancel/re-place laps indefinitely at a fraction of a coin more each time.
     */
    private int reprices;
    private long lastPlacedAt;

    public int reprices() { return reprices; }
    public long lastPlacedAt() { return lastPlacedAt; }

    /**
     * Records that an order for this route was actually submitted. Counted on submission
     * rather than on the decision to re-place, so the budget allows exactly
     * maxBookReprices re-placements after the first order, and a decision that never
     * reaches the Bazaar costs nothing.
     */
    public void recordPlacement(long now) {
        if (lastPlacedAt > 0) reprices++;
        lastPlacedAt = now;
    }

    /** Clears the reprice budget once the route has actually moved on (filled, or restarted). */
    public void resetReprices() { reprices = 0; }

    // -1 will indicate failure, 0 will indicate success
    public int assignBook(Book book, int level, int location, int amountOfBook) {
        if (amountOfBook == 0) return 0;
        if (amountOfBook < 0 || !this.book.equals(book)) return -1;
        if (level < book.level() || level > book.sellLevel() || location < 0 || location > 2) return -1;
        int amount = parseBookLevel(level);
        long totalAmount = (long) amount * amountOfBook;

        if (totalAmount > amountToOrder) return -1;

        amountToOrder -= totalAmount;

        for (int i = 0; i < amountOfBook; i++) {
            bookList.add(new BookList(book, level, location));
        }
        // we sort the list here by location
        bookList.sort(Comparator.comparingInt(bookList -> bookList.location));

        return 0;
    }

    public void setReservedUnitCost(double cost) {
        reservedUnitCost = cost;
    }

    public double getReservedUnitCost() {
        return reservedUnitCost;
    }

    public int getAmountToOrder() {
        return amountToOrder;
    }

    /** A written-off book never remains a hypothetical asset or reduces future input needs. */
    public int loseBook(BookList entry) {
        if (!bookList.remove(entry)) return 0;
        int units = book.baseUnits(entry.level);
        amountToOrder += units;
        return units;
    }

    public boolean acceptFound(BookList entry) {
        int units = book.baseUnits(entry.level);
        if (!book.equals(entry.book) || units <= 0 || units > amountToOrder
                || entry.location < 0 || entry.location > 2 || bookList.contains(entry)) return false;
        amountToOrder -= units;
        entry.found = true;
        bookList.add(entry);
        bookList.sort(Comparator.comparingInt(b -> b.location));
        return true;
    }

    public boolean isCombinable() {
        Set<Integer> seen = new HashSet<>();
        return bookList.stream().anyMatch(book -> !seen.add(book.level));
    }

    /** Partial inputs must wait for the live buy order, never be listed as a finished book. */
    public void finishCombining() {
        boolean finished = amountToOrder == 0 && bookList.size() == 1
                && bookList.getFirst().level == book.sellLevel() && bookList.getFirst().location == 0;
        if (finished) {
            actionSchedule = ActionSchedule.NONE;
            setBookState(BookState.SELL);
        } else if ((amountToOrder == 0 || isCombinable()) && bookList.stream().anyMatch(b -> b.location != 0)) {
            setBookState(BookState.ANVIL);
        } else if (actionSchedule == ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER) {
            setBookState(BookState.STORE);
        } else if (amountToOrder > 0) {
            setBookState(BookState.IN_BUY_ORDER);
        } else {
            throw new IllegalStateException("Book inputs do not form the required output");
        }
    }


    private int parseBookLevel(int level) {
        if (level == book.level()) return 1;
        return 1 << (level - book.level());
    }

}
