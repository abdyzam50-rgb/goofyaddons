package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlotViewTest {
    @Test void laterAdapterUpdatesCannotChangeCapturedTransactionEvidence() {
        var lore = new ArrayList<>(List.of("Click to claim!"));
        var enchants = new LinkedHashMap<String, Integer>();
        enchants.put("ultimate_wise", 1);
        var slot = new SlotView(11, false, 11, false, "Book", "Book", lore,
                SlotView.ENCHANTED_BOOK, enchants, 1, 1);
        var slots = new ArrayList<SlotView>();
        for (int index = 0; index < 12; index++) slots.add(SlotView.empty(index, false, index));
        slots.set(11, slot);
        var snapshot = new MenuSnapshot(3, "Anvil", true, slots);
        lore.clear();
        enchants.put("ultimate_wise", 5);

        assertEquals(List.of("Click to claim!"), snapshot.slot(11).loreLines());
        assertEquals(1, snapshot.slot(11).enchantments().get("ultimate_wise"));
        assertThrows(UnsupportedOperationException.class, () -> slot.loreLines().clear());
        assertThrows(UnsupportedOperationException.class, () -> slot.enchantments().clear());
    }

    @Test void absentComponentsRemainDifferentFromPresentEmptyComponents() {
        assertNull(SlotView.empty(0, false, 0).loreLines());
        assertNull(SlotView.empty(0, false, 0).enchantments());
        assertEquals(List.of(), SlotView.named(0, "Control", List.of()).loreLines());
    }
}
