package com.goofy.goofyaddons.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Engine composition policy; the client supplies implementations and observed queue state. */
public final class TradingEngines {
    private final Feature books, general, crafting, auction, production;

    public TradingEngines(Feature books, Feature general, Feature crafting, Feature auction) {
        this(books, general, crafting, auction, null);
    }

    /** {@code production} runs queued production loops; it may be null when there is none. */
    public TradingEngines(Feature books, Feature general, Feature crafting, Feature auction, Feature production) {
        this.production = production;
        this.books = Objects.requireNonNull(books);
        this.general = Objects.requireNonNull(general);
        this.crafting = Objects.requireNonNull(crafting);
        this.auction = Objects.requireNonNull(auction);
        var distinct = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Feature, Boolean>());
        if (!distinct.add(books) || !distinct.add(general) || !distinct.add(crafting) || !distinct.add(auction)
                || production != null && !distinct.add(production)) {
            throw new IllegalArgumentException("Each engine role requires a distinct implementation");
        }
    }

    /** Priority is not permission to preempt: MenuScheduler retains an in-flight owner. */
    public List<Feature> enabled(TradingMode mode, boolean craftingQueued, boolean auctionQueued) {
        return enabled(mode, false, craftingQueued, auctionQueued);
    }

    /** A queued production run comes first: it delegates its craft and listing to the features after it. */
    public List<Feature> enabled(TradingMode mode, boolean productionQueued, boolean craftingQueued, boolean auctionQueued) {
        Objects.requireNonNull(mode);
        var result = new ArrayList<Feature>();
        if ((productionQueued || mode==TradingMode.CRAFT) && production != null) result.add(production);
        if (auctionQueued) result.add(auction);
        if (craftingQueued) result.add(crafting);
        if (mode == TradingMode.BOOKS || mode == TradingMode.BOTH) result.add(books);
        if (mode == TradingMode.GENERAL || mode == TradingMode.BOTH) result.add(general);
        return List.copyOf(result);
    }
}
