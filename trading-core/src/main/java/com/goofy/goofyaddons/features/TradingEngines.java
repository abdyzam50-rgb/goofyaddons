package com.goofy.goofyaddons.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Engine composition policy; the client supplies implementations and observed queue state. */
public final class TradingEngines {
    private final Feature books, general, crafting, auction;

    public TradingEngines(Feature books, Feature general, Feature crafting, Feature auction) {
        this.books = Objects.requireNonNull(books);
        this.general = Objects.requireNonNull(general);
        this.crafting = Objects.requireNonNull(crafting);
        this.auction = Objects.requireNonNull(auction);
        var distinct = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Feature, Boolean>());
        if (!distinct.add(books) || !distinct.add(general) || !distinct.add(crafting) || !distinct.add(auction)) {
            throw new IllegalArgumentException("Each engine role requires a distinct implementation");
        }
    }

    /** Priority is not permission to preempt: MenuScheduler retains an in-flight owner. */
    public List<Feature> enabled(TradingMode mode, boolean craftingQueued, boolean auctionQueued) {
        Objects.requireNonNull(mode);
        var result = new ArrayList<Feature>();
        if (auctionQueued) result.add(auction);
        if (craftingQueued) result.add(crafting);
        if (mode != TradingMode.GENERAL) result.add(books);
        if (mode != TradingMode.BOOKS) result.add(general);
        return List.copyOf(result);
    }
}
