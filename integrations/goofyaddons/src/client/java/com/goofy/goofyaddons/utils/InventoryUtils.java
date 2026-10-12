package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {
    public static void dragSplit(int[] slots){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.gameMode==null || slots.length<2)return;
        var menu=mc.player.containerMenu;
        if(menu.getCarried().isEmpty())return;
        for(int slot:slots)if(slot<0 || slot>=menu.slots.size() || !menu.slots.get(slot).getItem().isEmpty())
            throw new IllegalArgumentException("Drag target changed before input");
        Diagnostics.event("INFO","menu.drag_split",java.util.Map.of("slots",java.util.Arrays.toString(slots),"container",menu.containerId));
        mc.gameMode.handleContainerInput(menu.containerId,-999,0,ContainerInput.QUICK_CRAFT,mc.player);
        for(int slot:slots)mc.gameMode.handleContainerInput(menu.containerId,slot,1,ContainerInput.QUICK_CRAFT,mc.player);
        mc.gameMode.handleContainerInput(menu.containerId,-999,2,ContainerInput.QUICK_CRAFT,mc.player);
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
