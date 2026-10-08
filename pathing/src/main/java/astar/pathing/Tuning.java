package astar.pathing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings a player can change: what .A*'s moves cost when it plans, and how the camera
 * and mouse move when it walks and teleports. One list, read by the planner, the executor and
 * the mod, kept in a plain text file ({@link #write}, {@link #read}) that the mod loads at
 * start and the player can edit, and changed in the game with {@code .A* config}.
 *
 * <p>Every value starts at what .A* did before it could be changed, so a game with no file
 * behaves as before. Values out of range are clamped to it.
 */
public final class Tuning {
    private Tuning() {}

    /** What changing a setting touches. */
    public enum Effect {
        /** How routes are planned: the next route plans with it (the map's graph is redone). */
        ROUTES,
        /** How teleports are planned: the next teleporting route plans with it. */
        HOPS,
        /** How the camera moves or the route is drawn: at once. */
        NOW,
        /** How the mouse turns: from the next route on. */
        NEXT_ROUTE
    }

    private static final Map<String, Option> ALL = new LinkedHashMap<>();
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    /** Bumped on every change, so callers can tell when to look again. */
    private static volatile int version;

    /** One setting. */
    public abstract static sealed class Option permits Num, Flag, Choice {
        public final String section;
        public final String name;
        public final String help;
        public final Effect effect;

        Option(String section, String name, Effect effect, String help) {
            this.section = section;
            this.name = name;
            this.effect = effect;
            this.help = help;
            ALL.put(key(), this);
        }

        /** "section.name", as the file and commands name it. */
        public String key() {
            return section + "." + name;
        }

        /** The value, as the file writes it. */
        public abstract String text();

        /** The default, as the file writes it. */
        public abstract String defaultText();

        /** What values it takes, for the file's comments and {@code .A* config}. */
        public abstract String range();

        /**
         * Sets it from {@code text}.
         *
         * @return null, or a note on how the value was changed to fit (clamped, say)
         * @throws IllegalArgumentException if {@code text} isn't a value it takes
         */
        public abstract String set(String text);

        /** Back to the default. */
        public abstract void reset();

        /** Whether it isn't at its default. */
        public boolean changed() {
            return !text().equals(defaultText());
        }
    }

    /** A number, clamped to [min, max]. */
    public static final class Num extends Option {
        public final double def;
        public final double min;
        public final double max;
        private volatile double value;

        Num(String section, String name, double def, double min, double max, Effect effect,
                String help) {
            super(section, name, effect, help);
            this.def = def;
            this.min = min;
            this.max = max;
            this.value = def;
        }

        public double get() {
            return value;
        }

        @Override
        public String text() {
            return number(value);
        }

        @Override
        public String defaultText() {
            return number(def);
        }

        @Override
        public String range() {
            return number(min) + " to " + number(max);
        }

        @Override
        public String set(String text) {
            double v;
            try {
                v = Double.parseDouble(text.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key() + " takes a number (" + range()
                        + "), not \"" + text.strip() + "\"");
            }
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException(key() + " takes a number (" + range() + ")");
            }
            double c = Math.max(min, Math.min(max, v));
            // The default as the file writes it is the default itself (1.414214 is sqrt(2)).
            value = number(c).equals(number(def)) ? def : c;
            version++;
            return c == v ? null : key() + " was " + number(v) + ", clamped to " + number(c)
                    + " (" + range() + ")";
        }

        @Override
        public void reset() {
            value = def;
            version++;
        }
    }

    /** On or off. */
    public static final class Flag extends Option {
        public final boolean def;
        private volatile boolean value;

        Flag(String section, String name, boolean def, Effect effect, String help) {
            super(section, name, effect, help);
            this.def = def;
            this.value = def;
        }

        public boolean get() {
            return value;
        }

        @Override
        public String text() {
            return Boolean.toString(value);
        }

        @Override
        public String defaultText() {
            return Boolean.toString(def);
        }

        @Override
        public String range() {
            return "true or false";
        }

        @Override
        public String set(String text) {
            String t = text.strip().toLowerCase(Locale.ROOT);
            switch (t) {
                case "true", "on", "yes", "1" -> value = true;
                case "false", "off", "no", "0" -> value = false;
                default -> throw new IllegalArgumentException(key() + " takes true or false,"
                        + " not \"" + text.strip() + "\"");
            }
            version++;
            return null;
        }

        @Override
        public void reset() {
            value = def;
            version++;
        }
    }

    /** One of a few names. */
    public static final class Choice extends Option {
        public final String def;
        public final List<String> choices;
        private volatile String value;

        Choice(String section, String name, String def, List<String> choices, Effect effect,
                String help) {
            super(section, name, effect, help);
            this.def = def;
            this.choices = List.copyOf(choices);
            this.value = def;
        }

        public String get() {
            return value;
        }

        @Override
        public String text() {
            return value;
        }

        @Override
        public String defaultText() {
            return def;
        }

        @Override
        public String range() {
            return String.join(", ", choices);
        }

        @Override
        public String set(String text) {
            String t = unquote(text.strip());
            for (String c : choices) {
                if (c.equalsIgnoreCase(t)) {
                    value = c;
                    version++;
                    return null;
                }
            }
            throw new IllegalArgumentException(key() + " takes one of " + range() + ", not \""
                    + t + "\"");
        }

        @Override
        public void reset() {
            value = def;
            version++;
        }
    }

    private static void section(String name, String about) {
        SECTIONS.put(name, about);
    }

    private static List<String> easings() {
        List<String> names = new ArrayList<>();
        for (Easing e : Easing.values()) {
            names.add(e.label);
        }
        return names;
    }

    // ---- path: what moves cost when .A* plans. ----

    static {
        section("path", "What each move costs when .A* plans a route, in blocks walked: a"
                + " straight step costs 1, so a move that costs 3 is taken only if it saves"
                + " three steps. Raise a cost to see that move less, lower it to see it more."
                + " The map's move graph is redone with the new costs (a few seconds on a big"
                + " map), kept per set of costs.");
    }

    public static final Num WALK = new Num("path", "walk", 1, 0.1, 10, Effect.ROUTES,
            "One straight step along x or z.");
    public static final Num DIAGONAL = new Num("path", "diagonal", Math.sqrt(2), 0.1, 20,
            Effect.ROUTES, "One diagonal step (1.414 is its true length).");
    public static final Num JUMP = new Num("path", "jump", 2, 0.1, 100, Effect.ROUTES,
            "Jumping up a block.");
    public static final Num DROP = new Num("path", "drop", 2, 0.1, 100, Effect.ROUTES,
            "Stepping off a ledge, plus drop_per_block for each block fallen. High, so routes"
                    + " walk down stairs and slopes rather than dropping.");
    public static final Num DROP_PER_BLOCK = new Num("path", "drop_per_block", 1, 0, 50,
            Effect.ROUTES, "Added to a drop for each block fallen.");
    public static final Num SWIM = new Num("path", "swim", 20, 0.1, 1000, Effect.ROUTES,
            "Each block swum. High, so routes keep out of water.");
    public static final Num CLIMB = new Num("path", "climb", 20, 0.1, 1000, Effect.ROUTES,
            "Each block climbed on ladders and vines. High, as .A* can't climb yet.");
    public static final Num WALL = new Num("path", "wall", 0.3, 0, 10, Effect.ROUTES,
            "Added to a step right beside a wall or a ledge, so routes keep to the middle of"
                    + " the way. 0 hugs walls.");
    public static final Num TURN = new Num("path", "turn", 0.1, 0, 5, Effect.ROUTES,
            "Added per 45 degrees a route turns, so it takes fewer, gentler turns.");

    // ---- teleport: etherwarp and Instant Transmission. ----

    static {
        section("teleport", "Etherwarp (.A* warp) and Instant Transmission (.A* it), with"
                + " .A* aotv for both. Costs are in blocks walked, like path's.");
    }

    public static final Num ETHERWARP_COST = new Num("teleport", "etherwarp_cost", 8, 0.5, 500,
            Effect.HOPS, "One etherwarp: stopping, aiming, the click and the server's"
                    + " answer. Lower it to etherwarp more.");
    public static final Num ETHERWARP_MIN = new Num("teleport", "etherwarp_min", 35, 3, 57,
            Effect.HOPS, "The shortest etherwarp, in blocks; shorter hops are walked."
                    + " Changing it finds the map's hops again.");
    public static final Num IT_COST = new Num("teleport", "it_cost", 5, 0.5, 500,
            Effect.HOPS, "One Instant Transmission cast. Lower it to cast more.");
    public static final Num IT_MIN = new Num("teleport", "it_min", 10, 1, 12, Effect.HOPS,
            "The shortest cast, in blocks; shorter ones are walked. Changing it finds the"
                    + " map's casts again.");
    public static final Num AIM_TIME = new Num("teleport", "aim_time", 1, 0.25, 4,
            Effect.NOW, "How long turning onto a cast on the ground takes, times the"
                    + " usual: 0.5 is twice as quick, 2 twice as slow.");
    public static final Num AIM_VARIATION = new Num("teleport", "aim_variation", 0.1, 0, 0.5,
            Effect.NOW, "How much each of those turns' times varies, as a share either way.");

    // ---- camera: where .A* looks while it walks. ----

    static {
        section("camera", "Where the camera looks while .A* walks.");
    }

    public static final Num LOOK_AHEAD = new Num("camera", "look_ahead", 2.5, 0, 30,
            Effect.NOW, "How far ahead along the route the camera looks standing still, in"
                    + " blocks.");
    public static final Num LOOK_AHEAD_PER_SPEED = new Num("camera", "look_ahead_per_speed", 7,
            0, 50, Effect.NOW, "And how much further per block a tick of speed (about 4"
                    + " blocks walking, 7 sprinting with Speed VII).");
    public static final Num STRAFE_LOOK = new Num("camera", "strafe_look", 3, 0, 30,
            Effect.NOW, "How far ahead it looks while strafing round a turn, standing.");
    public static final Num STRAFE_LOOK_PER_SPEED = new Num("camera", "strafe_look_per_speed", 6,
            0, 50, Effect.NOW, "And further per block a tick of speed.");
    public static final Num RIDGE_LOOK = new Num("camera", "ridge_look", 1.2, 0, 20,
            Effect.NOW, "How far ahead it looks on a narrow ridge, standing (closer, as a"
                    + " player watches their feet there).");
    public static final Num RIDGE_LOOK_PER_SPEED = new Num("camera", "ridge_look_per_speed", 2,
            0, 50, Effect.NOW, "And further per block a tick of speed.");
    public static final Num VIEW_HEIGHT = new Num("camera", "view_height", 1, -1, 3,
            Effect.NOW, "How high over the route's floor the camera aims, in blocks. 0"
                    + " looks at the floor; the overlay's line is drawn at this height.");
    public static final Num PITCH_MIN = new Num("camera", "pitch_min", -10, -90, 90,
            Effect.NOW, "The furthest it looks up, in degrees (negative is up).");
    public static final Num PITCH_MAX = new Num("camera", "pitch_max", 45, -90, 90,
            Effect.NOW, "The furthest it looks down, in degrees.");
    public static final Num PITCH_SPEED = new Num("camera", "pitch_speed", 90, 5, 1000,
            Effect.NOW, "The fastest it tilts up or down, in degrees a second.");
    public static final Flag STRAFE = new Flag("camera", "strafe", true, Effect.NOW,
            "Whether the keys steer the body round turns (W with A or D) while the camera"
                    + " looks further ahead. false turns the camera through every turn, which"
                    + " keeps to the route less closely (1 of 70 Mines stretches went off it).");
    public static final Num STRAFE_LEAD = new Num("camera", "strafe_lead", 40, 0, 90,
            Effect.NOW, "The furthest the camera leads the way the body goes while"
                    + " strafing, in degrees.");
    public static final Num HOLD = new Num("camera", "hold", 2, 0, 30, Effect.NOW,
            "How far, in degrees, where it wants to look may stray before a resting hand"
                    + " moves the mouse. 0 follows every little change.");
    public static final Num HOLD_STRAFING = new Num("camera", "hold_strafing", 6, 0, 30,
            Effect.NOW, "The same while the keys steer (strafing).");
    public static final Num DRIFT = new Num("camera", "drift", 0.35, 0, 10, Effect.NOW,
            "How far the camera wanders left and right like a hand does, in degrees. 0 for"
                    + " none.");
    public static final Num DRIFT_PITCH = new Num("camera", "drift_pitch", 0.4, 0, 10,
            Effect.NOW, "The same up and down.");
    public static final Num AIR_TURN_ACCEL = new Num("camera", "air_turn_accel", 1500, 50,
            100000, Effect.NOW, "How quickly a turn may speed up in the air, in degrees a"
                    + " second squared: lower turns more gently over jumps and drops.");

    // ---- mouse: how the hand turns the camera. ----

    static {
        section("mouse", "How the hand on the mouse turns the camera, walking and"
                + " teleporting.");
    }

    public static final Choice HAND = new Choice("mouse", "hand", "right",
            List.of("right", "left"), Effect.NEXT_ROUTE,
            "Which hand moves the mouse: it shapes which big turns overshoot.");
    public static final Choice EASE_TURNS = new Choice("mouse", "ease_turns", "easeInOutSine",
            easings(), Effect.NEXT_ROUTE, "The easing curve of ordinary turns (see easings.net).");
    public static final Choice EASE_FLICKS = new Choice("mouse", "ease_flicks", "easeOutSine",
            easings(), Effect.NEXT_ROUTE, "The curve of flicks: very big turns, and turns between"
                    + " casts in the air.");
    public static final Num STIFFNESS = new Num("mouse", "stiffness", 25, 1, 200, Effect.NEXT_ROUTE,
            "How tightly small turns close on where the camera should look (a turn settles in"
                    + " about 4 / stiffness seconds).");
    public static final Num TURN_SPEED = new Num("mouse", "turn_speed", 540, 30, 5000,
            Effect.NEXT_ROUTE, "The fastest a steady turn goes, in degrees a second.");
    public static final Num TURN_ACCEL = new Num("mouse", "turn_accel", 4000, 50, 100000,
            Effect.NEXT_ROUTE, "How quickly a steady turn speeds up or slows down, in degrees a"
                    + " second squared.");
    public static final Num SWEEP_OVER = new Num("mouse", "sweep_over", 30, 1, 360,
            Effect.NEXT_ROUTE, "Turns bigger than this, in degrees, are one quick eased sweep of"
                    + " the hand.");
    public static final Num FLICK_OVER = new Num("mouse", "flick_over", 75, 1, 360,
            Effect.NEXT_ROUTE, "Sweeps bigger than this are flicks (ease_flicks).");
    public static final Num SWEEP_TIME = new Num("mouse", "sweep_time", 0.1, 0, 2,
            Effect.NEXT_ROUTE, "A sweep's shortest time, in seconds.");
    public static final Num SWEEP_TIME_PER_DOUBLING = new Num("mouse", "sweep_time_per_doubling",
            0.07, 0, 2, Effect.NEXT_ROUTE, "How much longer a sweep takes each time the turn"
                    + " doubles, in seconds (Fitts's law).");
    public static final Num UNDERSHOOT = new Num("mouse", "undershoot", 0.95, 0.5, 1,
            Effect.NEXT_ROUTE, "How much of the turn a sweep makes before the steady turn takes"
                    + " over (1 for all of it).");
    public static final Num OVERSHOOT_OUT = new Num("mouse", "overshoot_out", 0.4, 0, 1,
            Effect.NEXT_ROUTE, "The share of big outward turns the hand carries past the target"
                    + " and brings back.");
    public static final Num OVERSHOOT_IN = new Num("mouse", "overshoot_in", 0.2, 0, 1,
            Effect.NEXT_ROUTE, "The same for turns across the body.");

    // ---- overlay ----

    static {
        section("overlay", "The route drawn in the world (.A* show).");
    }

    public static final Num LEAD_AHEAD = new Num("overlay", "lead_ahead", 4, 0, 30,
            Effect.NOW, "How far ahead of the player the lead box is drawn, in blocks.");

    // ---- cache: the maps .A* keeps beyond what the game has loaded. ----

    static {
        section("cache", "The maps .A* keeps so it can plan past the render distance: chunks"
                + " saved as you explore, one map per place (each Hypixel island is its own),"
                + " under .minecraft/astar/places/<server>/<dimension>/<map>/, and a whole-map"
                + " copy with its move graph for the map you're on. .A* cache maps lists"
                + " them.");
    }

    public static final Flag SAVE_CHUNKS = new Flag("cache", "save_chunks", true, Effect.NOW,
            "Whether chunks you see are saved to the map you're on (.A* cache on|off).");
    public static final Flag AUTO_DETECT = new Flag("cache", "auto_detect", true, Effect.NOW,
            "Whether the map you're on is told from its blocks. false waits for .A* map"
                    + " <name> (or another mod) to say which it is, and saves nothing until"
                    + " then.");
    public static final Num MATCH = new Num("cache", "match", 0.75, 0.3, 1, Effect.NOW,
            "How alike a loaded chunk and a saved one must be, as a share of their blocks,"
                    + " to count as the same map. Higher keeps similar islands apart.");
    public static final Num MATCHES_NEEDED = new Num("cache", "matches_needed", 4, 1, 64,
            Effect.NOW, "How many chunks must match before settling on a map.");
    public static final Num NEW_AFTER = new Num("cache", "new_after", 30, 4, 1000, Effect.NOW,
            "How many chunks may load with no map matching before a new map starts.");
    public static final Flag BUNDLED_MINES = new Flag("cache", "bundled_mines", true,
            Effect.NOW, "Whether the Dwarven Mines map that comes with the mod is used, so"
                    + " .A* plans across the Mines on your first visit.");
    public static final Flag WHOLE_MAP = new Flag("cache", "whole_map", true, Effect.NOW,
            "Whether a copy of the whole map you're on is made in the background, with its"
                    + " move graph and heuristic tables, so any trip on it plans in one go"
                    + " (about 200 MB of memory for the Mines).");
    public static final Num WHOLE_MAP_SIZE = new Num("cache", "whole_map_size", 64, 4, 256,
            Effect.NOW, "The biggest whole-map copy, in millions of blocks; bigger maps plan"
                    + " in stretches instead.");
    public static final Flag KEEP_FILES = new Flag("cache", "keep_files", true, Effect.NOW,
            "Whether each map's graph, heuristic tables and teleports are kept in files beside"
                    + " its chunks (nav.bin, warps.bin, casts.bin), so they're read back in"
                    + " well under a second instead of built again.");

    /** Every setting, in the file's order. */
    public static List<Option> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    /** The sections, in order, with what each is for. */
    public static Map<String, String> sections() {
        return Collections.unmodifiableMap(SECTIONS);
    }

    /** The setting named "section.name", or just "name" when only one has it; or null. */
    public static Option find(String key) {
        String k = key.strip().toLowerCase(Locale.ROOT);
        Option o = ALL.get(k);
        if (o != null) {
            return o;
        }
        Option only = null;
        for (Option a : ALL.values()) {
            if (a.name.equals(k)) {
                if (only != null) {
                    return null;
                }
                only = a;
            }
        }
        return only;
    }

    /** Changes on every set or reset. */
    public static int version() {
        return version;
    }

    /** Every setting back to its default. */
    public static void resetAll() {
        for (Option o : ALL.values()) {
            o.reset();
        }
    }

    /**
     * A key for everything that changes how routes are planned: equal keys plan the same.
     * Only the settings off their defaults are in it, so it stays short.
     */
    public static String routeKey() {
        StringBuilder b = new StringBuilder();
        for (Option o : ALL.values()) {
            if (o.effect == Effect.ROUTES && o.changed()) {
                b.append(b.isEmpty() ? "" : ", ").append(o.key()).append('=').append(o.text());
            }
        }
        return b.toString();
    }

    /** The file's text: every setting with what it does, its default and its range. */
    public static String write() {
        StringBuilder b = new StringBuilder();
        b.append("# A* pathfinder settings.\n");
        b.append("#\n");
        b.append("# Edit a value and run .A* config reload in the game, or change it there\n");
        b.append("# with .A* config set <name> <value> (written back here).\n");
        b.append("# .A* config reset <name> puts one back; delete this file to put them all\n");
        b.append("# back. Values out of range are clamped to it.\n");
        String last = null;
        for (Option o : ALL.values()) {
            if (!o.section.equals(last)) {
                last = o.section;
                b.append('\n');
                wrap(b, SECTIONS.get(o.section));
                b.append('[').append(o.section).append("]\n");
            }
            b.append('\n');
            wrap(b, o.help + " Default " + o.defaultText() + "; " + o.range() + ".");
            b.append(o.name).append(" = ").append(o instanceof Choice ? '"' + o.text() + '"'
                    : o.text()).append('\n');
        }
        return b.toString();
    }

    /**
     * Sets every setting the file's {@code text} names; the rest go back to their defaults.
     *
     * @return what was wrong with it, one line each (empty if nothing)
     */
    public static List<String> read(String text) {
        List<String> problems = new ArrayList<>();
        Map<Option, String> found = new LinkedHashMap<>();
        String section = "";
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
                if (!SECTIONS.containsKey(section)) {
                    problems.add("line " + (i + 1) + ": no section called [" + section + "]");
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                problems.add("line " + (i + 1) + ": expected name = value");
                continue;
            }
            String name = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).strip();
            Option o = ALL.get(name.contains(".") || section.isEmpty() ? name
                    : section + "." + name);
            if (o == null) {
                problems.add("line " + (i + 1) + ": no setting called " + (section.isEmpty()
                        || name.contains(".") ? name : section + "." + name));
                continue;
            }
            found.put(o, value);
        }
        for (Option o : ALL.values()) {
            String value = found.get(o);
            if (value == null) {
                o.reset();
                continue;
            }
            try {
                String note = o.set(unquote(value));
                if (note != null) {
                    problems.add(note);
                }
            } catch (IllegalArgumentException e) {
                o.reset();
                problems.add(e.getMessage() + "; kept the default " + o.defaultText());
            }
        }
        return problems;
    }

    /** {@code v} as the file writes it: no ".0" on whole numbers, at most 6 decimals. */
    static String number(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.6f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"")
                || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** {@code text} as comment lines of at most 90 characters. */
    private static void wrap(StringBuilder b, String text) {
        StringBuilder line = new StringBuilder("#");
        for (String word : text.split(" ")) {
            if (line.length() + 1 + word.length() > 90 && line.length() > 1) {
                b.append(line).append('\n');
                line = new StringBuilder("#");
            }
            line.append(' ').append(word);
        }
        b.append(line).append('\n');
    }
}
