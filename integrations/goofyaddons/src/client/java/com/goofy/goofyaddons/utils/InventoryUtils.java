package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {
    public static void dragSplit(int[] slots){
        dragSplit(slots,false);
    }
    public static void dragSplit(int[] slots,boolean right){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.gameMode==null || slots.length<2)return;
        var menu=mc.player.containerMenu;
        var carried=menu.getCarried();
        if(carried.isEmpty())return;
        if(carried.getCount()<slots.length)throw new IllegalArgumentException("Not enough cursor items for drag targets");
        int share=right?1:carried.getCount()/slots.length;
        var seen=new java.util.HashSet<Integer>();
        // Validate every cell before sending any QUICK_CRAFT packet. A top-up drag
        // requires matching components, permission to place, and room for its share.
        for(int index:slots) {
            if(index<0 || index>=menu.slots.size() || !seen.add(index))
                throw new IllegalArgumentException("Invalid or duplicate drag target");
            var slot=menu.slots.get(index);var existing=slot.getItem();
            boolean compatible=existing.isEmpty() || net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing,carried);
            if(!validDragTarget(existing.getCount(),slot.getMaxStackSize(carried),share,compatible,slot.mayPlace(carried)))
                throw new IllegalArgumentException("Drag target changed before input");
        }
        Diagnostics.event("INFO","menu.drag_split",java.util.Map.of("slots",java.util.Arrays.toString(slots),"container",menu.containerId,"right",right));
        int mode=right?4:0;
        mc.gameMode.handleContainerInput(menu.containerId,-999,mode,ContainerInput.QUICK_CRAFT,mc.player);
        for(int slot:slots)mc.gameMode.handleContainerInput(menu.containerId,slot,mode+1,ContainerInput.QUICK_CRAFT,mc.player);
        mc.gameMode.handleContainerInput(menu.containerId,-999,mode+2,ContainerInput.QUICK_CRAFT,mc.player);
    }

    static boolean validDragTarget(int count,int limit,int share,boolean compatible,boolean mayPlace) {
        return compatible && mayPlace && share>0 && count>=0 && count<=limit-share;
    }

    public static void clickSlot(int slot, boolean shift) {clickSlot(slot,shift,0);}

    public static void clickSlot(int slot,boolean shift,int button) {
        if(button<0 || button>1 || shift && button!=0)throw new IllegalArgumentException("Invalid inventory input");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        AbstractContainerMenu menu = minecraft.player.containerMenu;

        ContainerInput input = shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;

        Diagnostics.event("INFO","menu.click",java.util.Map.of("slot",slot,"button",button,"input",input.name(),"container",menu.containerId,"context",Diagnostics.snapshot()));
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, minecraft.player);
    }
}
