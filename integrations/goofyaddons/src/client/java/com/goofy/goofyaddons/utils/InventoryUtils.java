package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {

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