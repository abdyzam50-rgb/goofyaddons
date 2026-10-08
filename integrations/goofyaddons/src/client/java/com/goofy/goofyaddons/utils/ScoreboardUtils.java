package com.goofy.goofyaddons.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public class ScoreboardUtils {
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static String purseStatus = "unobserved";
    public static String purseStatus() { return purseStatus; }
    private static double unreadable(String reason) { purseStatus = reason; return -1; }

    public double getPurse() {
        Double purse = (double) -1;
        if (minecraft.player == null) return unreadable("player-absent");
        if (minecraft.level == null) return unreadable("world-absent");

        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (sidebar == null) return unreadable("sidebar-absent");

        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            String fakePlayer = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(fakePlayer);

            if (team == null) continue;
            String line =
                    team.getPlayerPrefix().getString()
                            + fakePlayer
                            + team.getPlayerSuffix().getString();
            if (!line.contains("Purse")) continue;
            double parsed=PurseParser.parse(line);
            if(parsed<0) return unreadable("purse-line-unparseable");
            if(purse>=0) return unreadable("multiple-purse-lines");
            purse=parsed;
        }
        if (purse < 0) return unreadable("purse-line-absent");
        purseStatus = "readable";
        return purse;
    }
}
