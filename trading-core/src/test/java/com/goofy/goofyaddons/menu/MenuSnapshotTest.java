package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first executable specification of what the engines see. Each case here is a menu
 * shape that is awkward or expensive to reach on a live server.
 */
class MenuSnapshotTest {
    private static final String WISE = "ultimate_wise";

    /** A chest-sized menu: {@code containerSlots} container slots then 36 player slots. */
    private static MenuSnapshot menu(int containerSlots, List<SlotView> placed) {
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) slots.add(SlotView.empty(i, false, i));
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(containerSlots + i, true, i));
        List<SlotView> result = new ArrayList<>(slots);
        for (SlotView slot : placed) result.set(slot.index(), slot);
        return new MenuSnapshot(7, "Ender Chest", true, result);
    }

    private static SlotView book(int index, boolean inInventory, int containerSlot, int level, String loreLine) {
        return SlotView.enchantedBook(index, inInventory, containerSlot, WISE, level,
                List.of("§9" + loreLine, "§7Some flavour text"), "Enchanted Book");
    }

    @Test void theContainerRegionStopsBeforeThePlayerInventory() {
        MenuSnapshot snapshot = menu(18, List.of());
        assertEquals(18, snapshot.containerEnd());
        assertEquals(54, snapshot.slots().size());
    }

    @Test void booksAreFoundSeparatelyInStorageAndInInventory() {
        MenuSnapshot snapshot = menu(18, List.of(
                book(3, false, 3, 1, "Ultimate Wise I"),
                book(18 + 5, true, 5, 1, "Ultimate Wise I")));
        assertEquals(List.of(3), snapshot.bookLoreInContainer("Ultimate Wise I"));
        assertEquals(List.of(23), snapshot.bookLoreInInventory("Ultimate Wise I"));
        assertEquals(List.of(), snapshot.bookLoreInContainer("Ultimate Wise II"));
    }

    @Test void formattingCodesInLoreDoNotHideAMatch() {
        MenuSnapshot snapshot = menu(9, List.of(
                SlotView.enchantedBook(2, false, 2, WISE, 1,
                        List.of("§9§lUltimate Wise I"), "Enchanted Book")));
        assertEquals(List.of(2), snapshot.bookLoreInContainer("Ultimate Wise I"));
    }

    @Test void aLookalikeMultiEnchantmentBookIsNeverThisRoutesBook() {
        // The dangerous case: same name, same lore line, two enchantments.
        Map<String, Integer> two = new LinkedHashMap<>();
        two.put(WISE, 1);
        two.put("ultimate_jerry", 1);
        SlotView lookalike = new SlotView(4, false, 4, false, "Enchanted Book", "Enchanted Book",
                List.of("§9Ultimate Wise I"), SlotView.ENCHANTED_BOOK, two, 1, 1);
        MenuSnapshot snapshot = menu(9, List.of(lookalike));
        assertEquals(List.of(), snapshot.matchingBookInContainer(WISE), "two enchantments is not our book");
        // Lore matching is deliberately looser, which is why callers use both.
        assertEquals(List.of(4), snapshot.bookLoreInContainer("Ultimate Wise I"));
    }

    @Test void aZeroLevelOrAbsentEnchantmentIsNotAMatch() {
        MenuSnapshot zero = menu(9, List.of(SlotView.enchantedBook(1, false, 1, WISE, 0, List.of(), "Book")));
        assertEquals(List.of(), zero.matchingBookInContainer(WISE));
        MenuSnapshot other = menu(9, List.of(SlotView.enchantedBook(1, false, 1, "ultimate_jerry", 3, List.of(), "Book")));
        assertEquals(List.of(), other.matchingBookInContainer(WISE));
    }

    @Test void anItemThatIsNotAHypixelBookIsNeverScannedAsOne() {
        SlotView notABook = new SlotView(1, false, 1, false, "Ultimate Wise I", "Ultimate Wise I",
                List.of("Ultimate Wise I"), "SKYBLOCK_MENU", Map.of(WISE, 1), 1, 1);
        MenuSnapshot snapshot = menu(9, List.of(notABook));
        assertEquals(List.of(), snapshot.bookLoreInContainer("Ultimate Wise I"));
        assertEquals(List.of(), snapshot.matchingBookInContainer(WISE));
        // It is still findable by name, which is how order entries are located.
        assertEquals(List.of(1), snapshot.namedInContainer("Ultimate Wise I"));
    }

    @Test void nameMatchingUsesTheCustomNameAndIgnoresUnnamedStacks() {
        SlotView unnamed = new SlotView(2, false, 2, false, null, "BUY Ultimate Wise I",
                List.of(), null, null, 1, 1);
        MenuSnapshot snapshot = menu(9, List.of(
                SlotView.named(1, "§6BUY Ultimate Wise I", List.of("§7Filled: §a0§7/§a16")),
                unnamed));
        assertEquals(List.of(1), snapshot.namedInContainer("BUY Ultimate Wise I"));
        assertEquals(List.of(), snapshot.namedInContainer("Nothing Like This"));
    }

    @Test void theNameSweepNeverReachesIntoThePlayerInventory() {
        MenuSnapshot snapshot = menu(9, List.of(
                new SlotView(9 + 2, true, 2, false, "BUY Ultimate Wise I", "BUY Ultimate Wise I",
                        List.of(), null, null, 1, 1)));
        assertEquals(List.of(), snapshot.namedInContainer("BUY Ultimate Wise I"),
                "an inventory item must never be read as an order entry");
    }

    @Test void armourAndOffhandAreExcludedFromLoreScansButNotFromBookMatching() {
        // Upstream inconsistency, preserved on purpose. A menu that exposes armour gives
        // a player slot a containerSlot of 36+; lore scans bound themselves to 0-35,
        // book matching does not. Both queries key off the player-inventory flag, so the
        // extra slot's effect on the last-36 container bound does not matter here.
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) slots.add(SlotView.empty(i, false, i));
        for (int i = 0; i < 36; i++) slots.add(SlotView.empty(9 + i, true, i));
        slots.add(book(45, true, 38, 1, "Ultimate Wise I"));
        MenuSnapshot snapshot = new MenuSnapshot(7, "Inventory", true, slots);

        assertEquals(List.of(), snapshot.bookLoreInInventory("Ultimate Wise I"),
                "lore scans bound themselves to the 36 main slots");
        assertEquals(List.of(45), snapshot.matchingBookInInventory(WISE),
                "book matching does not, which is the inconsistency this pins down");
        assertEquals(0, snapshot.emptyContainerSlots() - 9,
                "the armour slot is the player's, so it is not a free container slot");
    }

    @Test void emptySlotCountsSeparateTheContainerFromThePlayer() {
        MenuSnapshot snapshot = menu(18, List.of(
                book(0, false, 0, 1, "Ultimate Wise I"),
                book(18, true, 0, 1, "Ultimate Wise I"),
                book(19, true, 1, 1, "Ultimate Wise I")));
        assertEquals(17, snapshot.emptyContainerSlots());
        assertEquals(34, snapshot.emptyInventorySlots());
    }

    @Test void aMenuIsOnlyLoadedOnceTheProbedSlotHoldsSomething() {
        MenuSnapshot snapshot = menu(18, List.of(book(8, false, 8, 1, "Ultimate Wise I")));
        assertTrue(snapshot.loaded(8));
        assertFalse(snapshot.loaded(7), "an empty slot is not a loaded menu");
        assertFalse(snapshot.loaded(-1));
        assertFalse(snapshot.loaded(999), "a slot past the menu is never loaded");
    }

    @Test void levelReadsTheEnchantmentAndFailsClosedWhenThereIsNone() {
        MenuSnapshot snapshot = menu(9, List.of(
                book(1, false, 1, 4, "Ultimate Wise IV"),
                SlotView.named(2, "Not A Book", List.of("no data here"))));
        assertEquals(4, snapshot.levelAt(1));
        assertEquals(-1, snapshot.levelAt(2), "no enchantment data must read as unknown");
        assertEquals(-1, snapshot.levelAt(0), "an empty slot has no level");
        assertEquals(-1, snapshot.levelAt(500));
    }

    @Test void anUnreadableEnchantmentLevelReadsAsUnknownRatherThanZero() {
        SlotView broken = new SlotView(1, false, 1, false, "Book", "Book", List.of(),
                SlotView.ENCHANTED_BOOK, Map.of(WISE, -1), 1, 1);
        MenuSnapshot snapshot = menu(9, List.of(broken));
        assertEquals(-1, snapshot.levelAt(1));
        assertEquals(List.of(), snapshot.matchingBookInContainer(WISE), "a negative level is not a match");
    }

    @Test void anUnloadedAnvilIsNotEvidenceOfAMismatch() {
        MenuSnapshot empty = menu(36, List.of());
        assertFalse(empty.anvilInputsMismatch("Ultimate Wise I"), "absent inputs prove nothing");
        MenuSnapshot tooSmall = menu(9, List.of());
        assertFalse(tooSmall.anvilInputsMismatch("Ultimate Wise I"), "a menu this small is not an anvil");
    }

    @Test void anAnvilWithTheWrongInputIsReportedAsAMismatch() {
        MenuSnapshot matching = menu(36, List.of(
                book(29, false, 29, 1, "Ultimate Wise I"),
                book(33, false, 33, 1, "Ultimate Wise I")));
        assertFalse(matching.anvilInputsMismatch("Ultimate Wise I"), "both inputs are the expected book");

        MenuSnapshot mixed = menu(36, List.of(
                book(29, false, 29, 1, "Ultimate Wise I"),
                book(33, false, 33, 2, "Ultimate Wise II")));
        assertTrue(mixed.anvilInputsMismatch("Ultimate Wise I"), "a different level must be caught");
    }

    @Test void anInputWithNoLoreComponentIsTreatedAsUnloadedNotMismatched() {
        SlotView noLore = new SlotView(33, false, 33, false, "Book", "Book", null,
                SlotView.ENCHANTED_BOOK, Map.of(WISE, 1), 1, 1);
        MenuSnapshot snapshot = menu(36, List.of(book(29, false, 29, 1, "Ultimate Wise I"), noLore));
        assertFalse(snapshot.anvilInputsMismatch("Ultimate Wise I"));

        // A present-but-empty lore is different: it is loaded, and it does not match.
        SlotView emptyLore = new SlotView(33, false, 33, false, "Book", "Book", List.of(),
                SlotView.ENCHANTED_BOOK, Map.of(WISE, 1), 1, 1);
        MenuSnapshot loaded = new MenuSnapshot(7, "Anvil", true,
                replace(menu(36, List.of(book(29, false, 29, 1, "Ultimate Wise I"))).slots(), emptyLore));
        assertTrue(loaded.anvilInputsMismatch("Ultimate Wise I"));
    }

    @Test void theEnchantmentKeyIsDerivedFromTheHypixelBookId() {
        assertEquals("ultimate_wise", MenuSnapshot.enchantmentKey("ENCHANTMENT_ULTIMATE_WISE"));
        assertEquals("overload", MenuSnapshot.enchantmentKey("ENCHANTMENT_OVERLOAD"));
    }

    @Test void aSnapshotWithoutSlotsAnswersEverythingSafely() {
        MenuSnapshot none = new MenuSnapshot(0, null, true, null);
        assertEquals(0, none.containerEnd());
        assertEquals(List.of(), none.namedInContainer("anything"));
        assertEquals(List.of(), none.bookLoreInInventory("anything"));
        assertEquals(0, none.emptyInventorySlots());
        assertFalse(none.loaded(0));
        assertEquals(-1, none.levelAt(0));
        assertEquals("", none.loreAt(0));
        assertNull(none.slot(0));
        assertFalse(none.anvilInputsMismatch("anything"));
    }

    @Test void loreIsExposedJoinedForTheParsersThatExpectText() {
        MenuSnapshot snapshot = menu(9, List.of(
                SlotView.named(1, "BUY Ultimate Wise I", List.of("§7Filled: §a8§7/§a16", "§7Click to view options!"))));
        assertEquals("§7Filled: §a8§7/§a16\n§7Click to view options!", snapshot.loreAt(1));
        assertEquals("", snapshot.loreAt(0), "an empty slot has no lore");
    }

    private static List<SlotView> replace(List<SlotView> slots, SlotView slot) {
        List<SlotView> copy = new ArrayList<>(slots);
        copy.set(slot.index(), slot);
        return copy;
    }
}
