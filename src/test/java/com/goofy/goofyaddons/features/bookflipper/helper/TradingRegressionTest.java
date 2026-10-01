package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TradingRegressionTest {
    private final Book book = new Book("ENCHANTMENT_TEST", 1, 2, "Test", 0, 0);

    @BeforeEach
    void config() {
        GoofyConfig.INSTANCE = new GoofyConfig();
        GoofyConfig.INSTANCE.books = List.of(book);
    }

    private JsonObject products(double buyPrice, double salePrice) {
        JsonObject products = new JsonObject();
        products.add("ENCHANTMENT_TEST_1", product(buyPrice, buyPrice + 1));
        products.add("ENCHANTMENT_TEST_2", product(salePrice - 1, salePrice));
        return products;
    }

    private JsonObject product(double bid, double ask) {
        return JsonParser.parseString("{\"sell_summary\":[{\"pricePerUnit\":" + bid
                + ",\"orders\":3}],\"buy_summary\":[{\"pricePerUnit\":" + ask
                + ",\"orders\":3}],\"quick_status\":{\"sellMovingWeek\":1000,\"buyMovingWeek\":1000}}")
                .getAsJsonObject();
    }

    private JsonObject response(double bid, double sale, long timestamp) {
        JsonObject root = new JsonObject();
        root.add("products", products(bid, sale));
        root.addProperty("lastUpdated", timestamp);
        return root;
    }

    @Test
    void taxRejectsAnApparentlyProfitableFlip() {
        assertEquals(1, FlipCalculator.calculate(products(100, 201), List.of(book), 0, 0).size());
        assertTrue(FlipCalculator.calculate(products(100, 201), List.of(book), 1.25, 0).isEmpty());
    }

    @Test
    void minimumNetProfitAndMissingOrdersAreRespected() {
        assertTrue(FlipCalculator.calculate(products(100, 250), List.of(book), 1.25, 50).isEmpty());
        JsonObject missing = products(100, 250);
        missing.getAsJsonObject("ENCHANTMENT_TEST_1").remove("sell_summary");
        assertTrue(FlipCalculator.calculate(missing, List.of(book), 1.25, 0).isEmpty());
    }

    @Test
    void failedRequestsReleaseBusyStateOnlyOnTheClientExecutor() {
        ArrayDeque<Runnable> clientQueue = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        FlipCalculator calculator = new FlipCalculator(() -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("network"));
        }, clientQueue::add);
        calculator.Refresh();
        assertTrue(calculator.isRunning());
        clientQueue.remove().run();
        assertFalse(calculator.isRunning());
        calculator.Refresh();
        clientQueue.remove().run();
        assertEquals(2, calls.get());
    }

    @Test
    void resetIgnoresQueuedResultsFromThePreviousRun() {
        ArrayDeque<Runnable> clientQueue = new ArrayDeque<>();
        FlipCalculator calculator = new FlipCalculator(
                () -> CompletableFuture.completedFuture(response(100, 250, 1)), clientQueue::add);
        calculator.Refresh();
        calculator.reset();
        calculator.Refresh();
        clientQueue.remove().run();
        assertTrue(calculator.isRunning());
        assertTrue(calculator.getFlipItemsList().isEmpty());
        clientQueue.remove().run();
        assertFalse(calculator.isRunning());
        assertEquals(1, calculator.getFlipItemsList().size());
    }

    @Test
    void emptyAndMalformedResponsesDoNotLeaveCalculatorBusy() {
        FlipCalculator calculator = new FlipCalculator(
                () -> CompletableFuture.completedFuture(new JsonObject()), Runnable::run);
        calculator.Refresh();
        assertFalse(calculator.isRunning());
        calculator.Refresh();
        assertFalse(calculator.isRunning());
    }

    @Test
    void outbidDetectionRequiresABetterPrice() {
        assertFalse(BazaarMonitor.isOutbid(100, 100, false));
        assertFalse(BazaarMonitor.isOutbid(100, 99, false));
        assertTrue(BazaarMonitor.isOutbid(100, 101, false));
        assertFalse(BazaarMonitor.isOutbid(100, 101, true));
        assertTrue(BazaarMonitor.isOutbid(100, 99, true));
        assertFalse(BazaarMonitor.isOutbid(100, Double.NaN, false));
    }

    @Test
    void monitorRetriesFailuresAndIgnoresEqualPriceCompetition() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        BazaarMonitor monitor = new BazaarMonitor(() -> {
            if (calls.getAndIncrement() == 0) return CompletableFuture.failedFuture(new RuntimeException("network"));
            return CompletableFuture.completedFuture(response(100, 250, calls.get()));
        }, Runnable::run, now::get);
        AtomicInteger notices = new AtomicInteger();
        monitor.hook(item -> notices.incrementAndGet());
        monitor.add(book, 100, false);
        monitor.start();
        now.set(21000);
        monitor.refresh();
        monitor.refresh();
        assertEquals(2, calls.get());
        assertEquals(0, notices.get()); // Three orders at the same price.
    }

    @Test
    void stoppingMonitorInvalidatesAQueuedCallback() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        AtomicLong now = new AtomicLong();
        BazaarMonitor monitor = new BazaarMonitor(
                () -> CompletableFuture.completedFuture(response(101, 250, 1)), queue::add, now::get);
        AtomicInteger notices = new AtomicInteger();
        monitor.hook(item -> notices.incrementAndGet());
        monitor.add(book, 100, false);
        monitor.start();
        now.set(21000);
        monitor.refresh();
        monitor.stop();
        queue.remove().run();
        assertEquals(0, notices.get());
    }

    @Test
    void monitorHooksCanRemoveOtherMonitorsWithoutConcurrentModification() {
        AtomicLong now = new AtomicLong();
        BazaarMonitor monitor = new BazaarMonitor(
                () -> CompletableFuture.completedFuture(response(101, 250, 1)), Runnable::run, now::get);
        AtomicInteger notices = new AtomicInteger();
        monitor.hook(item -> { notices.incrementAndGet(); monitor.finish(book, true); });
        monitor.add(book, 100, false);
        monitor.add(book, 250, true);
        monitor.start();
        now.set(21000);
        monitor.refresh();
        assertEquals(1, notices.get());
    }

    @Test
    void candidateBatchesCannotSpendTheSamePurseTwice() {
        Book other = new Book("ENCHANTMENT_OTHER", 1, 2, "Other", 0, 0);
        List<FlipItem> items = List.of(new FlipItem(book, 60, 2, false, false),
                new FlipItem(other, 60, 1, false, false));
        assertEquals(1, TradeBudget.select(items, List.of(), 100).size());
        Task pending = new Task(other, false, false);
        pending.setBookState(Task.BookState.SELECTED);
        pending.setReservedUnitCost(30);
        assertTrue(TradeBudget.select(items, List.of(pending), 100).isEmpty());
        pending.setBookState(Task.BookState.IN_BUY_ORDER);
        assertEquals(1, TradeBudget.select(items, List.of(pending), 100).size());
    }

    @Test
    void pendingSellsKeepOwnershipOfTheirEnchantments() {
        Task selling = new Task(book, false, false);
        selling.setBookState(Task.BookState.SELL_ORDER);
        Book route = new Book(book.id(), 1, 3, book.name(), 0, 0);
        assertTrue(TradeBudget.select(List.of(new FlipItem(route, 50, 1, false, false)),
                List.of(selling), 100).isEmpty());
    }

    @Test
    void assignmentAcceptsEqualBooksButRejectsInvalidQuantitiesAndLevels() {
        Task task = new Task(book, false, false);
        Book equivalent = new Book(book.id(), 1, 2, book.name(), 0, 0);
        assertEquals(-1, task.assignBook(book, 1, 0, -1));
        assertEquals(-1, task.assignBook(book, 3, 0, 1));
        assertEquals(0, task.assignBook(equivalent, 1, 0, 1));
        assertEquals(1, task.getAmountToOrder());
        assertEquals(-1, task.assignBook(book, 1, 0, Integer.MAX_VALUE));
        assertEquals(1, task.getAmountToOrder());
    }
}
