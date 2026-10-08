package com.goofy.goofyaddons.features.bookflipper.helper;

public record Book(String id, int level, int sellLevel, String name, double instaSellPercentage, double instaBuyPercentage) {

    /**
     * Establishes the combining invariant once, at construction. Every later
     * {@code getQtyAmount(level())} on a Book that exists is then in range, so
     * bookkeeping and confirmation paths cannot have it throw underneath them.
     */
    public Book {
        if (level < 1 || sellLevel <= level || sellLevel > 10) {
            throw new IllegalArgumentException("Invalid combining levels: " + level + " -> " + sellLevel);
        }
    }

    public String getLevel(int i) {
        return this.id + "_" + i;
    }

    public String getRomanLevel(int i) {
        return name + " " + toRoman(i);
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    /**
     * How many level-{@link #level()} books one book of this level is worth.
     *
     * <p>Used to value a retained extra against the unit cost its position was bought at.
     * Returns 0 for a level outside this route rather than throwing, so valuing a stale or
     * malformed entry under-counts instead of breaking a capital adjustment.
     */
    public int baseUnits(int atLevel) {
        if (atLevel < level || atLevel > sellLevel) return 0;
        return 1 << (atLevel - level);
    }

    public int getQtyAmount(int level) {
        if (level < 1 || level > sellLevel || sellLevel > 10) {
            throw new IllegalArgumentException("Invalid combining levels");
        }
        return (1 << (sellLevel - level));
    }

}
