package com.goofy.goofyaddons.menu;

import com.goofy.goofyaddons.utils.MenuText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An open menu as plain data, with the observation queries both engines rely on.
 *
 * <p>Every query here is a faithful translation of the corresponding
 * {@code InventoryScanner} method, which now delegates to this class. The point is not
 * to improve the rules but to make them executable in a test, so the asymmetries are
 * preserved verbatim and called out rather than tidied:
 *
 * <ul>
 *   <li>{@link #namedInContainer} matches a stack's <em>custom name</em>, while the
 *       engines' own order sweeps match the <em>hover name</em>. They are different
 *       predicates and are kept apart.</li>
 *   <li>{@link #bookLoreInInventory} restricts to container slots 0-35, so armour and
 *       the offhand are excluded, but {@link #matchingBookInInventory} does not. That
 *       inconsistency is upstream behaviour and is preserved; see the note on
 *       {@link #matchingBookInInventory}.</li>
 *   <li>{@link #emptyContainerSlots} counts every slot outside the player inventory
 *       rather than the 0..containerEnd region, so a menu whose own slots extend past
 *       that bound is counted differently from {@link #namedInContainer}'s view.</li>
 * </ul>
 */
public record MenuSnapshot(int containerId, String title, boolean cursorEmpty, List<SlotView> slots, SlotView carried,long serverObservation) {

    public MenuSnapshot(int containerId,String title,boolean cursorEmpty,List<SlotView> slots,SlotView carried){
        this(containerId,title,cursorEmpty,slots,carried,-1);
    }

    public MenuSnapshot(int containerId, String title, boolean cursorEmpty, List<SlotView> slots) {
        this(containerId,title,cursorEmpty,slots,cursorEmpty ? SlotView.empty(-1,false,-1) : null);
    }

    public MenuSnapshot {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** Exclusive end of the container region; the last 36 slots are the player's. */
    public int containerEnd() {
        return MenuText.containerEnd(slots.size());
    }

    /** The enchantment key the game stores for a Hypixel book id. */
    public static String enchantmentKey(String bookId) {
        return bookId.substring("ENCHANTMENT_".length()).toLowerCase(Locale.ROOT);
    }

    private boolean containerRegion(int index) {
        return index >= 0 && index < containerEnd();
    }

    private boolean ownInventorySlot(SlotView slot) {
        return slot.inPlayerInventory() && slot.containerSlot() >= 0
                && slot.containerSlot() < MenuText.PLAYER_INVENTORY_SLOTS;
    }

    /** Container slots whose custom name matches exactly, formatting codes removed. */
    public List<Integer> namedInContainer(String name) {
        List<Integer> found = new ArrayList<>();
        int end = containerEnd();
        for (int i = 0; i < end && i < slots.size(); i++) {
            SlotView slot = slots.get(i);
            if (slot.empty() || slot.customName() == null) continue;
            if (!com.goofy.goofyaddons.utils.Chat.strip(slot.customName()).equals(name)) continue;
            found.add(i);
        }
        return found;
    }

    /** Enchanted books in the player's own 36 slots carrying this lore line. */
    public List<Integer> bookLoreInInventory(String loreLine) {
        List<Integer> found = new ArrayList<>();
        for (SlotView slot : slots) {
            if (!ownInventorySlot(slot) || slot.empty()) continue;
            if (!slot.enchantedBook() || !slot.hasLoreLine(loreLine)) continue;
            found.add(slot.index());
        }
        return found;
    }

    /** Enchanted books in the container region carrying this lore line. */
    public List<Integer> bookLoreInContainer(String loreLine) {
        List<Integer> found = new ArrayList<>();
        int end = containerEnd();
        for (int i = 0; i < end && i < slots.size(); i++) {
            SlotView slot = slots.get(i);
            if (slot.empty() || !slot.enchantedBook() || !slot.hasLoreLine(loreLine)) continue;
            found.add(i);
        }
        return found;
    }

    /**
     * A stack is this route's book only when it is an enchanted book carrying exactly
     * one enchantment, and that enchantment is the route's at level 1 or above. The
     * single-enchantment requirement is what keeps a lookalike combined book out.
     */
    private boolean matchesBook(SlotView slot, String enchantmentKey) {
        if (!slot.enchantedBook() || slot.enchantments() == null) return false;
        if (slot.enchantments().size() != 1) return false;
        Integer level = slot.enchantments().get(enchantmentKey);
        return level != null && level > 0;
    }

    public List<Integer> matchingBookInContainer(String enchantmentKey) {
        List<Integer> found = new ArrayList<>();
        int end = containerEnd();
        for (int i = 0; i < end && i < slots.size(); i++) {
            SlotView slot = slots.get(i);
            if (slot.empty() || !matchesBook(slot, enchantmentKey)) continue;
            found.add(i);
        }
        return found;
    }

    /**
     * Matching books anywhere the player owns, <em>including</em> armour and offhand.
     * {@link #bookLoreInInventory} excludes those; this does not. Preserved as-is
     * because narrowing it would change which books the book engine believes it holds.
     */
    public List<Integer> matchingBookInInventory(String enchantmentKey) {
        List<Integer> found = new ArrayList<>();
        for (SlotView slot : slots) {
            if (!slot.inPlayerInventory() || slot.empty()) continue;
            if (!matchesBook(slot, enchantmentKey)) continue;
            found.add(slot.index());
        }
        return found;
    }

    public int emptyInventorySlots() {
        int count = 0;
        for (SlotView slot : slots) if (ownInventorySlot(slot) && slot.empty()) count++;
        return count;
    }

    /** Counts every slot outside the player inventory, not just the container region. */
    public int emptyContainerSlots() {
        int count = 0;
        for (SlotView slot : slots) if (!slot.inPlayerInventory() && slot.empty()) count++;
        return count;
    }

    /** A menu is loaded enough to act on once this slot holds something. */
    public boolean loaded(int slot) {
        return slot >= 0 && slot < slots.size() && !slots.get(slot).empty();
    }

    public String loreAt(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot).lore() : "";
    }

    public SlotView slot(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }

    /**
     * The level this slot's first enchantment reports, or -1 when there is none to read.
     * With more than one enchantment the choice of "first" is whatever order the game
     * hands over, which is why callers only trust it for single-enchantment books.
     */
    public int levelAt(int slot) {
        SlotView view = slot(slot);
        if (view == null || view.empty() || view.enchantments() == null
                || view.enchantments().isEmpty()) return -1;
        Integer level = view.enchantments().values().iterator().next();
        return level == null ? -1 : level;
    }

    /**
     * Stripped hover names of the container region, positionally.
     *
     * <p>Empty slots are included so an index into this list still matches the menu,
     * which is what the ambiguity check relies on.
     */
    public List<String> containerHoverNames() {
        List<String> names = new ArrayList<>();
        int end = containerEnd();
        for (int i = 0; i < end && i < slots.size(); i++) {
            names.add(com.goofy.goofyaddons.utils.Chat.strip(slots.get(i).hoverName()));
        }
        return names;
    }

    /**
     * First container slot whose hover name matches, or -1.
     *
     * <p>Distinct from {@link #namedInContainer}, which reads the custom name. The
     * engines use both against different menus, so they stay separate predicates.
     */
    public int firstByHoverName(String text, boolean exact) {
        int end = containerEnd();
        for (int i = 0; i < end && i < slots.size(); i++) {
            SlotView slot = slots.get(i);
            if (slot.empty()) continue;
            String name = com.goofy.goofyaddons.utils.Chat.strip(slot.hoverName());
            if (exact ? name.equals(text) : name.contains(text)) return i;
        }
        return -1;
    }

    /** Total count of this Hypixel item anywhere the player owns, armour included. */
    public int countInInventory(String customId) {
        if (customId == null) return 0;
        int count = 0;
        for (SlotView slot : slots) {
            if (!slot.inPlayerInventory() || slot.empty()) continue;
            if (customId.equals(slot.customId())) count += slot.count();
        }
        return count;
    }

    /**
     * Stack limit observed for this item, clamped to 1..64, or 1 when none is held.
     *
     * <p>Scans every slot, the container included, because the limit is a property of
     * the item rather than of where it sits. A later match wins, as upstream.
     */
    public int stackLimitFor(String customId) {
        int limit = 1;
        if (customId == null) return limit;
        for (SlotView slot : slots) {
            if (slot.empty() || !customId.equals(slot.customId())) continue;
            limit = Math.max(1, Math.min(64, slot.maxStackSize()));
        }
        return limit;
    }

    /** Room left in part-filled stacks of this item, in the player's 36 main slots. */
    public int partialStackSpace(String customId) {
        if (customId == null) return 0;
        int space = 0;
        for (SlotView slot : slots) {
            if (!ownInventorySlot(slot) || slot.empty()) continue;
            if (!customId.equals(slot.customId())) continue;
            space += Math.max(0, slot.maxStackSize() - slot.count());
        }
        return space;
    }

    /**
     * Whether the anvil's two input slots are not both the expected book.
     *
     * <p>False when either input is absent or carries no lore at all, because an
     * unloaded anvil is not evidence of a mismatch; true when both are present with
     * lore and they do not both carry the expected line.
     */
    public boolean anvilInputsMismatch(String loreLine) {
        if (slots.size() <= 33) return false;
        SlotView first = slots.get(29);
        SlotView second = slots.get(33);
        if (first.empty() || second.empty()) return false;
        if (first.loreLines() == null || second.loreLines() == null) return false;
        return !(first.hasLoreLine(loreLine) && second.hasLoreLine(loreLine));
    }
}
