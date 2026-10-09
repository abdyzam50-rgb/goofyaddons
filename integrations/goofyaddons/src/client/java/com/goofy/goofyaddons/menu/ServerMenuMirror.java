package com.goofy.goofyaddons.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Client-thread bridge from actual server packets to the independent confirmed menu store. */
public final class ServerMenuMirror {
    private static final ConfirmedMenuStore STORE=new ConfirmedMenuStore();
    private ServerMenuMirror() {}
    public static void clear(){STORE.clear();}
    public static void content(int id,List<ItemStack> items,ItemStack cursor){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(id==mc.player.containerMenu.containerId) {
            var menu=mc.player.containerMenu;if(items.size()!=menu.slots.size()){clear();return;}
            var views=new ArrayList<SlotView>();
            for(int i=0;i<items.size();i++){var slot=menu.slots.get(i);views.add(LiveMenu.view(i,slot.container==mc.player.getInventory(),slot.getContainerSlot(),items.get(i)));}
            STORE.content(id,views,LiveMenu.view(-1,false,-1,cursor));
        } else if(id==0) {
            var menu=mc.player.inventoryMenu;
            for(int i=0;i<Math.min(items.size(),menu.slots.size());i++){var slot=menu.slots.get(i);if(slot.container==mc.player.getInventory())inventory(slot.getContainerSlot(),items.get(i));}
        }
    }
    public static void slot(int id,int index,ItemStack item){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(id==mc.player.containerMenu.containerId && index>=0 && index<mc.player.containerMenu.slots.size()) {
            var slot=mc.player.containerMenu.slots.get(index);
            STORE.slot(id,index,LiveMenu.view(index,slot.container==mc.player.getInventory(),slot.getContainerSlot(),item));
        } else if(id==0 && index>=0 && index<mc.player.inventoryMenu.slots.size()
                && (mc.player.containerMenu==mc.player.inventoryMenu || index>=36 && index<=45)) {
            // Matches the client: with another menu open it applies window-0 updates to the hotbar
            // and offhand only, so mirroring the rest would leave this copy holding items nobody sees.
            var slot=mc.player.inventoryMenu.slots.get(index);if(slot.container==mc.player.getInventory())inventory(slot.getContainerSlot(),item);
        }
    }
    public static void inventory(int slot,ItemStack item){STORE.inventory(slot,LiveMenu.view(-1,true,slot,item));}
    public static void cursor(ItemStack item){STORE.cursor(LiveMenu.view(-1,false,-1,item));}
    /** Replaces this copy with the client's own view; only for before any click, when the client holds no predictions. */
    public static void adopt(MenuSnapshot local){if(local!=null && local.carried()!=null)STORE.content(local.containerId(),local.slots(),local.carried());}
    public static MenuSnapshot read(){
        var mc=Minecraft.getInstance();return mc.player==null?null:STORE.read(mc.player.containerMenu.containerId,mc.gui.screen()==null?null:mc.gui.screen().getTitle().getString());
    }
}
