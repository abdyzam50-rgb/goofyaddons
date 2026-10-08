package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TradeBudget {
    private TradeBudget() {}

    public static List<FlipItem> select(List<FlipItem> candidates, List<Task> tasks, double purse) {
        if (!Double.isFinite(purse) || purse < 0) return List.of();
        double available = purse;
        Set<String> owned = new HashSet<>();
        for (Task task : tasks) {
            owned.add(task.getBook().id());
            // Existing buy orders have already been deducted from the purse.
            if (task.getBookState() == Task.BookState.SELECTED
                    || task.getBookState() == Task.BookState.BAZAAR_ORDER_CHECK) {
                available -= task.getReservedUnitCost() * task.getAmountToOrder();
            }
        }
        return select(candidates, tasks, available, Set.of());
    }

    /** The shared ledger already accounts for reserves, commitments and pending purchases. */
    public static List<FlipItem> select(List<FlipItem> candidates, List<Task> tasks,
                                        double available, Set<String> occupiedProducts) {
        if (!Double.isFinite(available) || available <= 0) return List.of();
        Set<String> owned = new HashSet<>(occupiedProducts);
        for (Task task : tasks) owned.add(task.getBook().id());
        List<FlipItem> selected = new ArrayList<>();
        for (FlipItem item : candidates) {
            // The engine safety-halts on instant buys/sells, so never plan them.
            if (item.instaBuy() || item.instaSell()) continue;
            if (owned.contains(item.book().id()) || !Double.isFinite(item.totalCost())
                    || item.totalCost() <= 0 || item.totalCost() > available) continue;
            available -= item.totalCost();
            owned.add(item.book().id());
            selected.add(item);
        }
        return List.copyOf(selected);
    }
}
