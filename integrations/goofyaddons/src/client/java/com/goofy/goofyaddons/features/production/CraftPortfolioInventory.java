package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.MenuSnapshot;
import java.util.List;
import java.util.Set;

/** Hold only future slots: owned items already consume the observed inventory. */
final class CraftPortfolioInventory {
    record Allocation(int requiredSlots,Set<String> products){}
    static int reserved(MenuSnapshot menu,List<Allocation> jobs) {
        if(menu==null)return 36;
        int reserved=4; // The shared claim engine retains four empty slots.
        for(var job:jobs) {
            long occupied=menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && !s.empty() && job.products().contains(s.customId())).count();
            reserved+=Math.max(0,job.requiredSlots()-(int)occupied);
        }
        return reserved;
    }
}
