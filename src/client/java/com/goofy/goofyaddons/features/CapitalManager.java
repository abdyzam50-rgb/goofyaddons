package com.goofy.goofyaddons.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Client-thread ledger shared by both engines. Keys are actual Bazaar product IDs. */
public class CapitalManager {
    public static final CapitalManager INSTANCE = new CapitalManager();
    private record Allocation(String owner, double cost, double pending) {}
    private final Map<String, Allocation> allocations = new HashMap<>();
    private final LongSupplier clock;
    private double limit = 300_000_000;
    private double reserve = 50_000_000;
    private long lastPurchaseMs;

    public CapitalManager() { this(System::currentTimeMillis); }
    CapitalManager(LongSupplier clock) { this.clock = clock; }

    public void configure(double limit, double reserve) {
        this.limit = limit;
        this.reserve = reserve;
    }

    public double available(double purse) {
        if (!Double.isFinite(purse) || purse < reserve) return 0;
        double committed = allocations.values().stream().mapToDouble(Allocation::cost).sum();
        double pending = allocations.values().stream().mapToDouble(Allocation::pending).sum();
        return Math.max(0, Math.min(limit - committed, purse - reserve - pending));
    }
    public double committed() { return allocations.values().stream().mapToDouble(Allocation::cost).sum(); }
    public double pending() { return allocations.values().stream().mapToDouble(Allocation::pending).sum(); }
    public double limit() { return limit; }
    public double reserve() { return reserve; }

    /**
     * Why a reservation of {@code cost} would be refused at {@code purse}, or null when it fits.
     * The two arms of {@link #available} are measured on different bases - the configured capital
     * limit against coins already in orders, the purse against coins still in hand - so a refusal
     * is ambiguous unless the losing arm says so itself.
     */
    public String refusal(String product, double cost, double purse) {
        // The scoreboard reports an unreadable purse as -1, which is finite but not a balance.
        if (!Double.isFinite(purse) || purse < 0) return "purse-unreadable";
        if (purse < reserve) return "purse-below-reserve";
        if (!Double.isFinite(cost) || cost <= 0) return "cost-unreadable";
        double committed = committed();
        Allocation own = allocations.get(product);
        if (own != null) committed -= own.cost();
        double pending = pending();
        if (own != null) pending -= own.pending();
        if (cost > limit - committed) return "capital-limit-reached";
        if (cost > purse - reserve - pending) return "purse-minus-pending-too-low";
        return null;
    }
    public int positionCount() { return allocations.size(); }

    public boolean reserve(String owner, String product, double cost, double purse) {
        if (!Double.isFinite(cost) || cost <= 0 || allocations.containsKey(product)
                || cost > available(purse)) return false;
        allocations.put(product, new Allocation(owner, cost, cost));
        return true;
    }

    public boolean owns(String owner, String product) {
        Allocation value = allocations.get(product);
        return value != null && value.owner.equals(owner);
    }

    public double cost(String owner, String product) {
        Allocation value = allocations.get(product);
        return value != null && value.owner.equals(owner) ? value.cost : 0;
    }

    public boolean occupied(String product) {
        return allocations.containsKey(product);
    }

    public void restore(String owner, String product, double cost, boolean pending) {
        if (!Double.isFinite(cost) || cost <= 0) return;
        Allocation existing = allocations.get(product);
        if (existing != null && !existing.owner.equals(owner)) return;
        allocations.put(product, new Allocation(owner, cost, pending ? cost : 0));
    }

    public boolean resize(String owner, String product, double cost, double purse) {
        Allocation old = allocations.get(product);
        if (old == null || !old.owner.equals(owner) || !Double.isFinite(cost) || cost <= 0) return false;
        allocations.remove(product);
        boolean fits = cost <= available(purse);
        allocations.put(product, fits ? new Allocation(owner, cost, cost) : old);
        return fits;
    }

    public void purchased(String owner, String product) {
        Allocation value = allocations.get(product);
        // Re-checking an order that was already placed must not restart the settle window.
        if (value != null && value.owner.equals(owner) && value.pending > 0) {
            allocations.put(product, new Allocation(owner, value.cost, 0));
            lastPurchaseMs = clock.getAsLong();
        }
    }

    public boolean purchaseSettling() {
        // Wait for the server's purse update before another engine can purchase.
        return clock.getAsLong() - lastPurchaseMs < 1000;
    }

    public void release(String owner, String product) {
        if (owns(owner, product)) allocations.remove(product);
    }

    public void releaseMissing(String owner, Set<String> products) {
        allocations.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)
                && !products.contains(entry.getKey()));
    }
}
