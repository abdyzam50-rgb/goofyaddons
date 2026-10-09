package com.goofy.goofyaddons.features.transaction;

import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.SlotView;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductIdentityTest {
    private static MenuSnapshot page(String title, String icon, String controlLore) {
        var slots = new ArrayList<SlotView>();
        for (int i = 0; i < 72; i++) slots.add(SlotView.empty(i, i >= 36, i));
        slots.set(13, SlotView.named(13, icon, List.of()));
        slots.set(15, SlotView.named(15, "Create Buy Order", controlLore == null ? List.of() : List.of(controlLore)));
        return new MenuSnapshot(1, title, true, slots);
    }

    @Test void readableIconDecidesEvenWhenTheTitleNamesTheProduct() {
        assertTrue(ProductIdentity.productPage(page("Bazaar ➜ Enchanted Coal", "Enchanted Coal", "Enchanted Coal"),
                "ENCHANTED_COAL", "Enchanted Coal", 15));
        assertFalse(ProductIdentity.productPage(page("Bazaar ➜ Enchanted Coal", "Enchanted Cobblestone", "Enchanted Cobblestone"),
                "ENCHANTED_COAL", "Enchanted Coal", 15));
    }
    @Test void truncatedTitleIsAcceptedOnlyWithIconAndControlAgreeing() {
        assertTrue(ProductIdentity.truncatedProductPage(page("Bazaar ➜ Ultimate Wi...", "Ultimate Wise I", "Ultimate Wise I"), "Ultimate Wise I", 15));
        assertFalse(ProductIdentity.truncatedProductPage(page("Bazaar ➜ Ultimate Wi...", "Ultimate Wise I", "Ultimate Wise II"), "Ultimate Wise I", 15));
        assertFalse(ProductIdentity.truncatedProductPage(page("Bazaar ➜ Ultimate Wi...", "Ultimate Wise II", "Ultimate Wise I"), "Ultimate Wise I", 15));
        assertFalse(ProductIdentity.truncatedProductPage(page("Ultimate Wi...", "Ultimate Wise I", "Ultimate Wise I"), "Ultimate Wise I", 15),
                "a title without a breadcrumb is not a product page");
    }
    @Test void missingControlIsNeverAProductPage() {
        assertFalse(ProductIdentity.productPage(page("Bazaar ➜ Enchanted Coal", "Enchanted Coal", "Enchanted Coal"),
                "ENCHANTED_COAL", "Enchanted Coal", -1));
    }
    @Test void fullTitleCannotOverrideAConflictingReadableIconName() {
        assertFalse(ProductIdentity.productPage(page("Bazaar ➜ Gold Ingot", "Enchanted Gold Ingot", "Gold Ingot"),
                "GOLD_INGOT", "Gold Ingot", 15));
    }
}
