package com.goofy.goofyaddons.menu;

import java.util.List;
import java.util.Map;

/**
 * One slot of an open container menu, as plain data.
 *
 * <p>This exists so the observation half of both engines can be exercised by tests:
 * nothing here touches Minecraft, so a test can describe any menu, including the ones
 * that are hard to reach on a live server (a wrong storage page, a lookalike
 * multi-enchantment book, a half-loaded orders list).
 *
 * <p>Two fields are deliberately nullable, because the live code distinguishes absent
 * from empty and the distinction changes decisions:
 * <ul>
 *   <li>{@code loreLines} is null when the stack carries no LORE component at all.
 *       {@link MenuSnapshot#anvilInputsMismatch} answers differently for null and for
 *       a present-but-empty lore.</li>
 *   <li>{@code customName} is null when the stack has no custom name, which
 *       {@link MenuSnapshot#namedInContainer} skips before comparing.</li>
 * </ul>
 *
 * <p>{@code customId} is null when there is no CUSTOM_DATA component, and the empty
 * string when the component exists without an {@code id}. {@code enchantments} is null
 * when there is no {@code enchantments} compound; its values are already resolved the
 * way the game resolves them, so an unreadable level is -1 rather than missing.
 */
public record SlotView(
        int index,
        boolean inPlayerInventory,
        int containerSlot,
        boolean empty,
        String customName,
        String hoverName,
        List<String> loreLines,
        String customId,
        Map<String, Integer> enchantments,
        int count,
        int maxStackSize) {

    public static final String ENCHANTED_BOOK = "ENCHANTED_BOOK";

    /** An empty slot carries no name, lore or data. */
    public static SlotView empty(int index, boolean inPlayerInventory, int containerSlot) {
        return new SlotView(index, inPlayerInventory, containerSlot, true, null, "", null, null, null, 0, 64);
    }

    /** A container-menu button or order entry, identified by its custom name. */
    public static SlotView named(int index, String customName, List<String> loreLines) {
        return new SlotView(index, false, index, false, customName, customName, loreLines, null, null, 1, 1);
    }

    /** A Hypixel enchanted book carrying exactly one enchantment. */
    public static SlotView enchantedBook(int index, boolean inPlayerInventory, int containerSlot,
                                         String enchantmentKey, int level, List<String> loreLines, String hoverName) {
        return new SlotView(index, inPlayerInventory, containerSlot, false, hoverName, hoverName, loreLines,
                ENCHANTED_BOOK, Map.of(enchantmentKey, level), 1, 1);
    }

    /** True only for a stack the game would report as a Hypixel enchanted book. */
    public boolean enchantedBook() {
        return ENCHANTED_BOOK.equals(customId);
    }

    /** The lore as the engines read it: raw lines joined by newlines, never null. */
    public String lore() {
        return loreLines == null ? "" : String.join("\n", loreLines);
    }

    /** Whether any lore line equals this text once formatting codes are removed. */
    public boolean hasLoreLine(String text) {
        if (loreLines == null || text == null) return false;
        for (String line : loreLines) {
            if (com.goofy.goofyaddons.utils.Chat.strip(line).equals(text)) return true;
        }
        return false;
    }
}
