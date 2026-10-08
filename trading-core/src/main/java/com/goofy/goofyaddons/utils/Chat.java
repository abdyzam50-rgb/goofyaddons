package com.goofy.goofyaddons.utils;

import java.util.regex.Pattern;

/** Formatting-code removal over a precompiled pattern, for the per-slot and per-tick paths. */
public final class Chat {
    // Identical semantics to replaceAll("§.", ""): '.' does not match a line terminator.
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private Chat() {}

    public static String strip(String text) {
        if (text == null || text.isEmpty()) return "";
        // indexOf first: most strings carry no formatting code, and this avoids a Matcher.
        return text.indexOf('§') < 0 ? text : FORMATTING.matcher(text).replaceAll("");
    }
}
