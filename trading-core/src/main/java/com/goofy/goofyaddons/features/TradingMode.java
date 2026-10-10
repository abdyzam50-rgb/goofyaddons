package com.goofy.goofyaddons.features;

public enum TradingMode {
    BOOKS, GENERAL, BOTH, CRAFT;

    public String label() {
        return switch(this){case BOOKS->"Book flips";case GENERAL->"General flips";case BOTH->"Books + general";case CRAFT->"Craft flips";};
    }

    public TradingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
