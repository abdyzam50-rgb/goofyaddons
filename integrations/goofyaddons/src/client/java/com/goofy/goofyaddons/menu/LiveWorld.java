package com.goofy.goofyaddons.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

/** The real game, and the only observation path into it. */
public final class LiveWorld implements GameWorld {
    @Override public boolean screenOpen() {return Minecraft.getInstance().gui.screen()!=null;}
    @Override public String screenTitle() {
        var screen=Minecraft.getInstance().gui.screen();return screen==null?null:screen.getTitle().getString();
    }

    @Override public boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }

    @Override public String username() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getUser() == null ? null : minecraft.getUser().getName();
    }

    @Override public String playerId() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID().toString();
    }

    @Override public long now() { return System.currentTimeMillis(); }

    @Override public boolean signEditorOpen() {
        return Minecraft.getInstance().gui.screen() instanceof AbstractSignEditScreen;
    }

    @Override public MenuSnapshot menu() {
        return LiveMenu.read();
    }

    @Override public void onClientThread(Runnable work) {
        Minecraft.getInstance().execute(work);
    }
}
