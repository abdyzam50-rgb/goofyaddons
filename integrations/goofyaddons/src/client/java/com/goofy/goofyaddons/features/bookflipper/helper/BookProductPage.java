package com.goofy.goofyaddons.features.bookflipper.helper;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.features.transaction.ProductIdentity;
/** Exact route level on the product layout; a shared enchantment name is insufficient. */
public final class BookProductPage {
 private BookProductPage() {}
 public static boolean matches(MenuSnapshot menu,Book book,boolean selling) {
  if(menu==null || menu.title()==null || menu.containerEnd()!=36)return false;
  int level=selling?book.sellLevel():book.level();String id=book.getLevel(level);
  var icon=menu.slot(ProductIdentity.ICON_SLOT);
  if(icon!=null && icon.enchantedBook() && icon.enchantments()!=null && !icon.enchantments().isEmpty()
      && !ProductIdentity.matches(icon,id))return false;
  return ProductIdentity.productPage(menu,id,book.getRomanLevel(level),selling?16:15);
 }
}
