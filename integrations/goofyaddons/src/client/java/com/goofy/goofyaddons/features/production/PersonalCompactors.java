package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;

/** Live compactor detection and input-conflict checks. No GUI slot is inferred from tier. */
public final class PersonalCompactors {
    private static MenuSnapshot lastConfiguration;
    private PersonalCompactors() {}
    public static void observe(MenuSnapshot menu) {
        if(menu==null){lastConfiguration=null;return;}
        String title=Chat.strip(menu.title());
        if(title!=null && title.startsWith("Personal Compactor"))lastConfiguration=menu;
    }
    public static void capture()throws java.io.IOException {
        if(lastConfiguration==null)throw new java.io.IOException("Open the Personal Compactor menu first");
        var path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-compactor-menu.json");
        java.nio.file.Files.writeString(path,AuctionCommands.describe(lastConfiguration));
    }
    public static List<SlotView> detect(MenuSnapshot menu) {
        if(menu==null)return List.of();
        String title=Chat.strip(menu.title());boolean bag=title!=null && (title.equals("Accessory Bag")||title.startsWith("Accessory Bag ("));
        return menu.slots().stream().filter(s->!s.empty() && (s.inPlayerInventory() && s.containerSlot()<36 || bag && !s.inPlayerInventory()))
            .filter(s->CompactorData.tier(s.customId())>0).sorted(Comparator.comparingInt((SlotView s)->CompactorData.tier(s.customId())).reversed()).toList();
    }
    public static Map<String,Object> status(MenuSnapshot menu) {
        var found=detect(menu);var first=found.isEmpty()?null:found.getFirst();
        return Map.of("detected",found.size(),"tier",first==null?0:CompactorData.tier(first.customId()),
            "capacity",first==null?0:CompactorData.capacity(CompactorData.tier(first.customId())),
            "configurationReadable",first!=null && first.metadata().compactor()!=null,
            "status",first==null?"Not observed in inventory or the open Accessory Bag":first.metadata().compactor()==null?"Compactor configuration unreadable":
                "Detected Personal Compactor "+CompactorData.tier(first.customId())+"; production clears all detected recipe filters before buying or crafting",
            "automaticBulkEnabled",false);
    }
    public static String conflict(MenuSnapshot menu,Collection<String> inputs,RecipeCatalog catalog) {
        for(var slot:detect(menu)) {
            var data=slot.metadata().compactor();
            if(data==null || data.active()==null)return "Personal Compactor state is unreadable; verify it before buying or crafting inputs";
            if(!data.active())continue;
            for(String output:data.recipes().values()) {
                var options=catalog.forOutput(output);
                if(options.isEmpty())return "Active Personal Compactor recipe "+output+" is unverified; disable it before production";
                if(options.stream().anyMatch(r->r.ingredients().keySet().stream().anyMatch(inputs::contains)))
                    return "Personal Compactor can convert required inputs into "+catalog.name(output)+"; remove or disable that recipe before continuing";
            }
        }
        return null;
    }
}
