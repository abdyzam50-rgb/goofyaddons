package astar.client;

import astar.movement.exec.AimController.Hand;
import astar.pathing.Easing;
import astar.pathing.Tuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's settings file, config/astar.toml: every {@link Tuning} setting with what it
 * does, read at start and by {@code .A* config reload}, and written back by {@code .A*
 * config set} and {@code reset}. A game with no file writes one with the defaults (and the hand
 * and curves chosen before, from the files they were kept in), so there's one to edit.
 */
final class AstarConfig {
    private AstarConfig() {}

    private static boolean loaded;

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar.toml");
    }

    /** Reads the file once, the first time any setting is needed. */
    static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (Files.exists(file())) {
            for (String problem : reload()) {
                System.out.println("[astar] " + file().getFileName() + ": " + problem);
            }
            return;
        }
        migrate();
        save();
    }

    /**
     * Reads the file again: the settings it names take its values, the rest their defaults.
     *
     * @return what was wrong with it, one line each; or why it couldn't be read
     */
    static synchronized List<String> reload() {
        loaded = true;
        try {
            return Tuning.read(Files.readString(file()));
        } catch (IOException e) {
            List<String> problems = new ArrayList<>();
            problems.add("couldn't read " + file() + ": " + e.getMessage());
            return problems;
        }
    }

    /** Writes every setting to the file; false if it couldn't be. */
    static synchronized boolean save() {
        Path f = file();
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(tmp, Tuning.write());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.out.println("[astar] couldn't save " + f + ": " + e);
            return false;
        }
    }

    /**
     * The hand, turning curves and chunk saving chosen before there was a settings file.
     */
    private static void migrate() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        try {
            Boolean saving = Places.oldRecording();
            if (saving != null) {
                Tuning.SAVE_CHUNKS.set(saving.toString());
            }
        } catch (RuntimeException e) {
            // None chosen.
        }
        try {
            Hand h = Hand.named(Files.readString(dir.resolve("astar-mouse-hand.txt")));
            if (h != null) {
                Tuning.HAND.set(h.name().toLowerCase());
            }
        } catch (IOException | RuntimeException e) {
            // None chosen.
        }
        try {
            String[] saved = Files.readString(dir.resolve("astar-mouse-ease.txt")).split(",");
            Easing t = Easing.named(saved[0]);
            Easing f = saved.length > 1 ? Easing.named(saved[1]) : null;
            if (t != null && f != null) {
                Tuning.EASE_TURNS.set(t.label);
                Tuning.EASE_FLICKS.set(f.label);
            }
        } catch (IOException | RuntimeException e) {
            // None chosen.
        }
    }

    /** The curve for ordinary turns ({@link Tuning#EASE_TURNS}). */
    static Easing turns() {
        load();
        Easing e = Easing.named(Tuning.EASE_TURNS.get());
        return e == null ? Easing.EASE_IN_OUT_SINE : e;
    }

    /** The curve for flicks ({@link Tuning#EASE_FLICKS}). */
    static Easing flicks() {
        load();
        Easing e = Easing.named(Tuning.EASE_FLICKS.get());
        return e == null ? Easing.EASE_OUT_SINE : e;
    }

    /** The hand that moves the mouse ({@link Tuning#HAND}). */
    static Hand hand() {
        load();
        Hand h = Hand.named(Tuning.HAND.get());
        return h == null ? Hand.RIGHT : h;
    }
}
