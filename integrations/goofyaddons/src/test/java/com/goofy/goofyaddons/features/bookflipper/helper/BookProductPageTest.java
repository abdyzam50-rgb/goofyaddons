package com.goofy.goofyaddons.features.bookflipper.helper;
import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BookProductPageTest {
 private final Book book=new Book("ENCHANTMENT_TURBO_SUNFLOWER",1,5,"Turbo-Sunflower",0,0);
 private MenuSnapshot page(int size,String level) {
  var slots=new ArrayList<SlotView>();for(int i=0;i<size;i++)slots.add(SlotView.empty(i,i>=size-36,i));
  slots.set(13,SlotView.named(13,"Turbo-Sunflower "+level,List.of()));
  slots.set(15,SlotView.named(15,"Create Buy Order",List.of("Turbo-Sunflower "+level)));
  slots.set(16,SlotView.named(16,"Create Sell Offer",List.of("Turbo-Sunflower "+level)));
  return new MenuSnapshot(7,"Turbo-Sunflower ➜ Turbo-Sunflow",true,slots);
 }
 @Test void buyAndSellUseTheirOwnExactLevelsDespiteSharedEnchantmentName() {
  assertTrue(BookProductPage.matches(page(72,"I"),book,false));
  assertFalse(BookProductPage.matches(page(72,"I"),book,true));
  assertTrue(BookProductPage.matches(page(72,"V"),book,true));
  assertFalse(BookProductPage.matches(page(72,"V"),book,false));
 }
 @Test void searchSizedLookalikesAndContradictoryEnchantmentsDoNotAuthoriseClicks() {
  assertFalse(BookProductPage.matches(page(90,"I"),book,false));
  var source=page(72,"I");var slots=new ArrayList<>(source.slots());
  slots.set(13,SlotView.enchantedBook(13,false,13,"turbo_sunflower",2,List.of(),"Turbo-Sunflower I"));
  assertFalse(BookProductPage.matches(new MenuSnapshot(7,source.title(),true,slots),book,false));
 }
}
