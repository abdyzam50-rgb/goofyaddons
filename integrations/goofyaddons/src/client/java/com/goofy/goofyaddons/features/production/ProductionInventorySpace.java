package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.features.transaction.ProductIdentity;

/** Incoming market items may have unique components despite a vanilla stack limit of 64. */
final class ProductionInventorySpace {
    private ProductionInventorySpace() {}
    static int limit(RecipeCatalog catalog,MenuSnapshot menu,String id) {
        int limit=catalog.unstackable(id)?1:64;SlotView first=null;
        if(menu!=null)for(var slot:menu.slots())if(ProductIdentity.matches(slot,id)) {
            limit=Math.min(limit,Math.max(1,Math.min(64,slot.maxStackSize())));
            if(slot.metadata().uuid()!=null&&!slot.metadata().uuid().isBlank())limit=1;
            if(slot.inPlayerInventory()) {
                if(first!=null&&!first.canStackWith(slot))limit=1;
                first=slot;
            }
        }
        return limit;
    }
    static int slots(RecipeCatalog catalog,MenuSnapshot menu,String id,int units) {
        int limit=limit(catalog,menu,id);return Math.toIntExact((units+(long)limit-1)/limit);
    }
    static int capacity(RecipeCatalog catalog,MenuSnapshot menu,String id) {
        if(menu==null)return 0;int limit=limit(catalog,menu,id),partial=0;
        if(limit>1)for(var slot:menu.slots())if(slot.inPlayerInventory()&&slot.containerSlot()>=0&&slot.containerSlot()<36&&ProductIdentity.matches(slot,id))
            partial+=Math.max(0,limit-slot.count());
        return menu.emptyInventorySlots()*limit+partial;
    }
}
