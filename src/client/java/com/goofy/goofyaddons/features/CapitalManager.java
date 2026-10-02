package com.goofy.goofyaddons.features;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Client-thread ledger shared by both engines. Keys are actual Bazaar product IDs. */
public class CapitalManager {
    public static final CapitalManager INSTANCE = new CapitalManager();
    private record Allocation(String owner, double cost, double pending) {}
    private final Map<String, Allocation> allocations = new HashMap<>();
    private double limit = 300_000_000;
    private double reserve = 50_000_000;
    private long lastPurchaseMs;

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
        if (value != null && value.owner.equals(owner)) {
            allocations.put(product, new Allocation(owner, value.cost, 0));
            lastPurchaseMs = System.currentTimeMillis();
        }
    }

    public boolean purchaseSettling() {
        // Wait for the server's purse update before another engine can purchase.
        return System.currentTimeMillis() - lastPurchaseMs < 1000;
    }

    public void release(String owner, String product) {
        if (owns(owner, product)) allocations.remove(product);
    }

    public void releaseMissing(String owner, Set<String> products) {
        allocations.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)
                && !products.contains(entry.getKey()));
    }
}
