package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import java.util.*;

/** Client-thread projection. Reservations and observed holdings are distinct; closed storage is unknown. */
public record PipelineAccount(long observedAt, TradingMode mode, Double purse, double committed,
                              double pending, double available, int inventoryCapacity,
                              int bookSlots, int generalSlots, Set<String> excludedProducts,
                              boolean ready, String reason) {
    public static PipelineAccount capture(long now,TradingMode mode,CapitalManager capital,double purse,
                                           MenuSnapshot inventory,int maxBooks,int maxGeneral) {
        return capture(now,mode,capital,purse,inventory,maxBooks,maxGeneral,Set.of());
    }
    public static PipelineAccount capture(long now,TradingMode mode,CapitalManager capital,double purse,
                                           MenuSnapshot inventory,int maxBooks,int maxGeneral,Set<String> unavailable) {
        var excluded=new TreeSet<>(capital.occupiedProducts());
        excluded.addAll(unavailable);
        boolean complete=inventory!=null && inventory.slots().stream().filter(SlotView::inPlayerInventory)
                .map(SlotView::containerSlot).filter(s->s>=0 && s<36).distinct().count()==36;
        if(inventory!=null)for(var slot:inventory.slots())if(slot.inPlayerInventory() && !slot.empty()) {
            if(slot.customId()!=null)excluded.add(slot.customId());
            // Armor enchantments are not physical Bazaar books.
            if(slot.enchantedBook() && slot.enchantments()!=null)for(var key:slot.enchantments().keySet())
                excluded.add("ENCHANTMENT_"+key.toUpperCase(Locale.ROOT));
        }
        String reason=!Double.isFinite(purse) || purse<0?"Purse is unreadable":!complete?"Inventory is incomplete":
                !inventory.cursorEmpty()?"Finish the cursor action first":capital.purchaseSettling()?"Waiting for the purchase/purse update":null;
        return new PipelineAccount(now,mode,Double.isFinite(purse)&&purse>=0?purse:null,capital.committed(),capital.pending(),
                reason==null?capital.available(purse):0,complete?TradingSafety.conservativeCapacity(inventory.emptyInventorySlots(),4):0,
                Math.max(0,maxBooks-capital.ownerPositionCount("books")),Math.max(0,maxGeneral-capital.ownerPositionCount("general")),
                Collections.unmodifiableSet(excluded),reason==null,reason);
    }
}
