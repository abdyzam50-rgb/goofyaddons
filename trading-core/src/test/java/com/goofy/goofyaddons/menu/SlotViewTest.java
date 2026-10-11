package com.goofy.goofyaddons.menu;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test void bazaarBookIdentityIncludesExactEnchantmentAndLevelWithoutChangingTheRawBookId() {
        var book=SlotView.enchantedBook(23,false,23,"quick_bite",1,List.of(),"Enchanted Book");
        assertEquals("ENCHANTMENT_QUICK_BITE_1",book.bazaarProductId());assertTrue(book.enchantedBook());
        assertEquals(SlotView.ENCHANTED_BOOK,book.customId());
        assertEquals("ENCHANTMENT_QUICK_BITE_2",SlotView.enchantedBook(23,false,23,"quick_bite",2,List.of(),"Enchanted Book").bazaarProductId());
        var ordinary=new SlotView(0,true,0,false,"Sword","Sword",List.of(),"ASPECT_OF_THE_END",Map.of("sharpness",5),1,1);
        assertEquals("ASPECT_OF_THE_END",ordinary.bazaarProductId());assertFalse(ordinary.enchantedBook());
        assertNull(SlotView.empty(0,false,0).bazaarProductId());
    }

    @Test void ambiguousOrUnreadableBooksCannotMatchAnExactBazaarVariant() {
        var unknownLevel=new LinkedHashMap<String,Integer>();unknownLevel.put("quick_bite",null);
        for(var enchantments:List.of(Map.<String,Integer>of(),Map.of("quick_bite",1,"sharpness",5),
                Map.of("quick_bite",0),Map.of("quick_bite",-1),Map.of("quick_bite",101),Map.of("quick bite",1),unknownLevel)) {
            var book=new SlotView(23,false,23,false,"Enchanted Book","Enchanted Book",List.of(),SlotView.ENCHANTED_BOOK,enchantments,1,1);
            assertEquals(SlotView.ENCHANTED_BOOK,book.bazaarProductId());assertTrue(book.enchantedBook());
        }
        var absent=new SlotView(23,false,23,false,"Enchanted Book","Enchanted Book",List.of(),SlotView.ENCHANTED_BOOK,null,1,1);
        assertEquals(SlotView.ENCHANTED_BOOK,absent.bazaarProductId());
    }
}
