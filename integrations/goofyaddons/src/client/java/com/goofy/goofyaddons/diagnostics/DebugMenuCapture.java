package com.goofy.goofyaddons.diagnostics;

import com.goofy.goofyaddons.menu.*;
import java.util.*;

/** Selected item ownership facts only; never serialize raw components, tags or account data. */
public final class DebugMenuCapture {
    private DebugMenuCapture() {}
    public static Map<String,Object> describe(MenuSnapshot menu) {
        if(menu==null)return Map.of("available",false);
        var result=new LinkedHashMap<String,Object>();
        result.put("available",true);result.put("title",menu.title());
        result.put("containerId",menu.containerId());result.put("serverObservation",menu.serverObservation());
        result.put("cursorEmpty",menu.cursorEmpty());
        result.put("slots",menu.slots().stream().filter(s->!s.inPlayerInventory()).map(DebugMenuCapture::slot).toList());
        result.put("inventory",menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && !s.empty())
            .map(DebugMenuCapture::slot).toList());
        if(menu.carried()!=null)result.put("carried",slot(menu.carried()));
        result.put("personalCompactors",menu.slots().stream().filter(s->s.inPlayerInventory() && CompactorData.tier(s.customId())>0)
            .map(DebugMenuCapture::slot).toList());
        return result;
    }
    private static Map<String,Object> slot(SlotView slot) {
        var result=new LinkedHashMap<String,Object>();
        result.put("slot",slot.index());result.put("empty",slot.empty());result.put("count",slot.count());
        if(!slot.empty()) {
            if(slot.inPlayerInventory())result.put("inventorySlot",slot.containerSlot());
            result.put("name",slot.hoverName());result.put("productId",slot.customId());
            result.put("lore",slot.loreLines());result.put("enchantments",slot.enchantments());
            addStackFacts(result,slot);
            var compactor=slot.metadata().compactor();
            if(compactor!=null) {
                var settings=new LinkedHashMap<String,Object>();settings.put("tier",compactor.tier());
                settings.put("active",compactor.active());settings.put("recipes",compactor.recipes());
                result.put("compactor",settings);
            }
        }
        return result;
    }
    /** The component fingerprint is normalized and hashed by LiveMenu; its raw input is never exported. */
    static void addStackFacts(Map<String,Object> result,SlotView slot) {
        result.put("maxStackSize",slot.maxStackSize());
        var metadata=slot.metadata();
        if(metadata.vanillaId()!=null)result.put("vanillaId",metadata.vanillaId());
        if(metadata.uuid()!=null)result.put("uuid",metadata.uuid());
        if(metadata.stackIdentity()!=null)result.put("stackIdentity",metadata.stackIdentity());
    }
}
