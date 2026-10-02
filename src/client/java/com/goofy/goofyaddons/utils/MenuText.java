package com.goofy.goofyaddons.utils;

/** Pure menu-text decisions shared by both engines. */
public final class MenuText {
    /** Every container menu ends with the player's 36 inventory slots. */
    public static final int PLAYER_INVENTORY_SLOTS = 36;

    private MenuText() {}

    /**
     * Exclusive end of the container region for a menu with this many slots.
     * Single-sourced because "the last 36 are mine" is the assumption that breaks
     * first when a menu is not the shape the caller expected.
     */
    public static int containerEnd(int slotCount) {
        return Math.max(0, slotCount - PLAYER_INVENTORY_SLOTS);
    }

    /** Whether a screen title carries this label, ignoring formatting codes. */
    public static boolean titleContains(String title, String label) {
        return title != null && label != null && Chat.strip(title).contains(label);
    }
}
