package com.goofy.goofyaddons.utils;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

import java.lang.reflect.Field;

/** Writes an amount onto a Bazaar quantity sign. Both engines used identical reflection. */
public final class SignEntry {
    private SignEntry() {}

    /**
     * Returns false when the line could not be written, so callers never assume the
     * amount reached the sign. Closing the screen is left to the caller, which knows
     * whether submitting an unwritten sign would be safe.
     */
    public static boolean writeFirstLine(AbstractSignEditScreen screen, String text) throws Exception {
        if (screen == null || text == null) return false;
        Field messages = AbstractSignEditScreen.class.getDeclaredField("messages");
        messages.setAccessible(true);
        String[] lines = (String[]) messages.get(screen);
        if (lines == null || lines.length == 0) return false;
        lines[0] = text;
        return true;
    }
}
