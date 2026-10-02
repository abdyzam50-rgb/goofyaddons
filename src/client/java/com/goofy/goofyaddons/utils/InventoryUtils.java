package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

public class InventoryUtils {

    public static void clickSlot(int slot, boolean shift) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        AbstractContainerMenu menu = minecraft.player.containerMenu;

        ContainerInput input = shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;

        Diagnostics.event("INFO","menu.click",java.util.Map.of("slot",slot,"input",input.name(),"container",menu.containerId,"context",Diagnostics.snapshot()));
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, input, minecraft.player);
    }
}