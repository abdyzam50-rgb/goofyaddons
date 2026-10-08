package com.goofy.goofyaddons.storage;

import java.util.Locale;

/**
 * Whose trading records these are: one Minecraft account on one SkyBlock profile.
 *
 * <p>Positions, costs and profit belong to a profile, not to the game installation. Two
 * profiles of one account, or two accounts sharing an installation, must never see each
 * other's orders, funds or claims.
 *
 * @param player the Minecraft account's UUID, or {@code name:<username>} when it is unknown
 * @param profile the SkyBlock profile's name, as the server announces it on join
 */
public record AccountScope(String player, String profile) {
    public AccountScope {
        if (player == null || player.isBlank()) throw new IllegalArgumentException("Account scope needs a player");
        if (profile == null || profile.isBlank()) throw new IllegalArgumentException("Account scope needs a profile");
        player = player.strip();
        profile = profile.strip();
    }

    /** The folder name for each part: lower-case letters, digits, '-' and '_' only. */
    public String playerFolder() { return folder(player); }
    public String profileFolder() { return folder(profile); }

    public String label() { return profile + " (" + player + ")"; }

    private static String folder(String text) {
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (clean.isEmpty()) clean = "unnamed";
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
