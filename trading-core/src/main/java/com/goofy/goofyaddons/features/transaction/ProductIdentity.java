package com.goofy.goofyaddons.features.transaction;

import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.SlotView;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.utils.MenuText;

/**
 * Which product a Bazaar product page is for.
 *
 * <p>SkyBlock truncates long product breadcrumbs, so the title alone can neither confirm nor
 * reject a product. The product icon in slot 13 and the lore of the control about to be
 * clicked still name it in full. Both engines decide product identity here.
 */
public final class ProductIdentity {
    private ProductIdentity() {}

    public static final int ICON_SLOT = 13;

    /**
     * The general engine's check: a readable icon decides, by item id when it has one and by
     * name otherwise, and the control must name the product. A full title is accepted only
     * when the control does not contradict it.
     */
    public static boolean productPage(MenuSnapshot menu, String id, String name, int control) {
        if (control < 0) return false;
        SlotView button = menu.slot(control);
        if (button == null || button.inPlayerInventory() || button.empty()) return false;
        SlotView icon = menu.slot(ICON_SLOT);
        if (icon != null && !icon.empty() && !icon.inPlayerInventory()) {
            if (icon.customId() != null && !icon.customId().isBlank()) {
                return id.equals(icon.customId()) && button.hasLoreLine(name);
            }
            if (icon.hoverName()!=null && !Chat.strip(icon.hoverName()).isBlank())
                return name.equals(Chat.strip(icon.hoverName())) && button.hasLoreLine(name);
        }
        // Keep support for older layouts with full titles, but never contradict readable identity.
        return MenuText.titleContains(menu.title(), name) && (button.loreLines() == null || button.loreLines().isEmpty()
                || button.hasLoreLine(name));
    }

    /**
     * A breadcrumb title too long to show the product, where the icon and the control's lore
     * both name exactly this product.
     */
    public static boolean truncatedProductPage(MenuSnapshot menu, String name, int control) {
        String title = Chat.strip(menu.title());
        if (title == null || !(title.contains("➜") || title.contains("→"))) return false;
        if (menu.slots().size() <= ICON_SLOT) return false;
        SlotView button = menu.slot(control);
        return Chat.strip(menu.slot(ICON_SLOT).hoverName()).equals(name) && button != null && button.hasLoreLine(name);
    }
}
