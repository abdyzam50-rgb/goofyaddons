package com.goofy.goofyaddons.features;

public enum TradingMode {
    BOOKS, GENERAL, BOTH;

    public TradingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
