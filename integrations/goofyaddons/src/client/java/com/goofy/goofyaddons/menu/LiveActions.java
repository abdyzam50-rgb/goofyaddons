package com.goofy.goofyaddons.menu;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.utils.ChatUtils;
import com.goofy.goofyaddons.utils.InventoryUtils;
import com.goofy.goofyaddons.utils.SignEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

/** The real effects, and the only place that performs them. */
public final class LiveActions implements GameActions {

    @Override public void click(int slot, boolean shift) {
        InventoryUtils.clickSlot(slot, shift);
    }

    @Override public void rightClick(int slot){InventoryUtils.clickSlot(slot,false,1);}

    /** Guards internally, so callers need no null checks of their own. */
    @Override public void closeMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.gui.screen() != null) minecraft.player.closeContainer();
    }

    @Override public void command(String text) {
        Diagnostics.command(text);
    }

    @Override public void message(String text) {
        ChatUtils.clientMessage(text);
    }

    @Override public boolean writeSign(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof AbstractSignEditScreen sign)) return false;
        try {
            if (!SignEntry.writeFirstLine(sign, text)) return false;
        } catch (Exception failure) {
            // An unwritten sign must never be left open for a later confirmation.
            Diagnostics.failure("menu.sign_write_failed", failure);
            return false;
        }
        minecraft.gui.setScreen(null);
        return true;
    }
}
