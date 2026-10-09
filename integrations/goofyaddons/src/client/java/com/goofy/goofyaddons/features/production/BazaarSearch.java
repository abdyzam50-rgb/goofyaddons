package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.transaction.RecoveryRules;
import com.goofy.goofyaddons.menu.GameActions;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.utils.Chat;

/**
 * Reaches a Bazaar product page by name. "/bz <name>" opens a search results page, so the
 * product is one more click: the single result whose product ID matches, falling back to a
 * unique exact name only when that result has no ID. The command is
 * resent only after a pause, since sending it closes whatever menu is open.
 */
final class BazaarSearch {
    private final String productId,name;
    private int opens, clicked = Integer.MIN_VALUE;
    private long nextCommand;

    BazaarSearch(String productId,String name) { this.productId=productId;this.name = name; }

    /** Null while the product page is still on its way, or why it could not be reached. */
    String step(MenuSnapshot menu, boolean held, GameActions actions, long now) {
        int result = held ? -1 : resultSlot(menu, productId,name);
        if (result >= 0 && menu.containerId() != clicked) {
            clicked = menu.containerId(); nextCommand = now + RecoveryRules.INPUT_RESTART_MS;
            actions.click(result, false);
            return null;
        }
        if (now < nextCommand) return null;
        if (opens >= 3) return !held && result < 0 && resultsPage(menu)
                ? "Bazaar search did not list " + name + " by its exact name"
                : "Bazaar product page for " + name + " did not open";
        opens++; nextCommand = now + RecoveryRules.INPUT_RESTART_MS;
        actions.command("bz " + name);
        return null;
    }

    private static boolean resultsPage(MenuSnapshot menu) {
        return menu != null && menu.title() != null && Chat.strip(menu.title()).startsWith("Bazaar");
    }

    static int resultSlot(MenuSnapshot menu, String productId,String name) {
        if (!resultsPage(menu)) return -1;
        int identified=-1,found = -1;boolean ambiguousName=false;
        for (var slot : menu.slots()) {
            if (slot.empty() || slot.inPlayerInventory()) continue;
            if(slot.customId()!=null && !slot.customId().isBlank()) {
                if(!productId.equals(slot.customId()))continue;
                if(identified>=0)return -1;
                identified=slot.index();continue;
            }
            if (slot.hoverName()==null || !Chat.strip(slot.hoverName()).equalsIgnoreCase(name)) continue;
            if (found >= 0) ambiguousName=true;
            found = slot.index();
        }
        return identified>=0?identified:ambiguousName?-1:found;
    }
}
