package com.goofy.goofyaddons.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

/** The real game, and the only observation path into it. */
public final class LiveWorld implements GameWorld {

    @Override public boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    @Override public String username() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getUser() == null ? null : minecraft.getUser().getName();
    }

    @Override public boolean signEditorOpen() {
        return Minecraft.getInstance().screen instanceof AbstractSignEditScreen;
    }

    @Override public MenuSnapshot menu() {
        return LiveMenu.read();
    }

    @Override public void onClientThread(Runnable work) {
        Minecraft.getInstance().execute(work);
    }
}
