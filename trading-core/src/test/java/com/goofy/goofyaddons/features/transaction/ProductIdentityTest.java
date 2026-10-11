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
    @Test void bookIdentityRequiresOneExactEnchantmentAndLevel(){
        var book=SlotView.enchantedBook(13,false,13,"overload",4,List.of(),"Overload IV");
        assertTrue(ProductIdentity.matches(book,"ENCHANTMENT_OVERLOAD_4"));
        assertFalse(ProductIdentity.matches(book,"ENCHANTMENT_OVERLOAD_5"));
        assertFalse(ProductIdentity.matches(book,"ENCHANTMENT_POWER_4"));
        var mixed=new SlotView(13,false,13,false,"Overload IV","Overload IV",List.of(),"ENCHANTED_BOOK",java.util.Map.of("overload",4,"power",4),1,1);
        assertFalse(ProductIdentity.matches(mixed,"ENCHANTMENT_OVERLOAD_4"));
    }


    private static MenuSnapshot results(SlotView... items) {
        var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
        for(var s:items)slots.set(s.index(),s);
        return new MenuSnapshot(3,"Bazaar ➜ \"Gold\"",true,slots);
    }
    private static SlotView result(int index,String id,String name){return new SlotView(index,false,index,false,name,name,List.of(),id,null,1,64);}

    @Test void searchResultPrefersIdentityThenAUniqueExactName() {
        assertEquals(12,ProductIdentity.searchResult(results(result(11,"ENCHANTED_GOLD","Gold Ingot"),result(12,"GOLD_INGOT","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(-1,ProductIdentity.searchResult(results(result(11,"ENCHANTED_GOLD","Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(12,ProductIdentity.searchResult(results(result(11,null,"Enchanted Gold Ingot"),result(12,null,"Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(-1,ProductIdentity.searchResult(results(result(11,null,"Gold Ingot"),result(12,null,"Gold Ingot")),"GOLD_INGOT","Gold Ingot"));
        assertEquals(-1,ProductIdentity.searchResult(new MenuSnapshot(1,"Auction House",true,List.of(result(11,"GOLD_INGOT","Gold Ingot"))),"GOLD_INGOT","Gold Ingot"));
    }
    @Test void searchResultFindsTheExactBookLevel() {
        var four=SlotView.enchantedBook(11,false,11,"sharpness",4,List.of(),"Sharpness IV");
        var five=SlotView.enchantedBook(12,false,12,"sharpness",5,List.of(),"Sharpness V");
        assertEquals(12,ProductIdentity.searchResult(results(four,five),"ENCHANTMENT_SHARPNESS_5","Sharpness V"));
    }
}
