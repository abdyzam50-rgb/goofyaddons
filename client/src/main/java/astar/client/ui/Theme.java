package astar.client.ui;

import astar.client.draw.OverlayColour;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The screen's colour sets, one per route colour ({@link OverlayColour}): the loading screen's
 * colour tokens ({@link Colours}) turned to the route's hue. Picking one also draws the route in
 * its colour, so the screen and the route match. Kept in {@code config/astar-theme.txt}.
 */
public enum Theme {
    CRIMSON("Crimson", OverlayColour.RED),
    EMBER("Ember", OverlayColour.ORANGE),
    AMBER("Amber", OverlayColour.YELLOW),
    EMERALD("Emerald", OverlayColour.GREEN),
    GLACIER("Glacier", OverlayColour.CYAN),
    OCEAN("Ocean", OverlayColour.BLUE),
    VIOLET("Violet", OverlayColour.PURPLE),
    ROSE("Rose", OverlayColour.PINK),
    GRAPHITE("Graphite", OverlayColour.WHITE),
    /** Every hue in turn, like RGB lighting; the logo keeps playing. */
    RAINBOW("Rainbow", OverlayColour.RAINBOW);

    public final String label;
    /** The route colour it goes with. */
    public final OverlayColour route;
    /** Its colours (for Rainbow, the ones it starts from): see {@link Colours}. */
    public final Colours colours;

    Theme(String label, OverlayColour route) {
        this.label = label;
        this.route = route;
        colours = route == OverlayColour.WHITE ? Colours.OCEAN.grey()
                : Colours.OCEAN.turned(route.line);
    }

    /** This theme's colours now: for Rainbow, the hue it has reached. */
    public Colours colours() {
        return this == RAINBOW ? Colours.OCEAN.turned(OverlayColour.rainbow(
                System.currentTimeMillis())) : colours;
    }

    /**
     * The colour tokens every screen draws with, in one place. Ocean's are the loading screen's
     * (a near-black navy map, desaturated slate structure, blue only as a rare accent); every
     * other theme has the same tokens turned to its own hue, so each token keeps its role and
     * lightness in every theme. Only the active search, the goal, the start and the current
     * selection use the accent or highlight; everything else is slate or darker.
     */
    public static final class Colours {
        /** Ocean: the loading screen's colours. */
        public static final Colours OCEAN = new Colours(new int[] {0x02040A, 0x0A0F18, 0x1C2533,
                0x141A24, 0x222C3A, 0x384860, 0x50627E, 0xC8D0DC, 0x6E7A8C, 0x1E4A8A, 0x2868B0,
                0x2888F0, 0xE0E8F8});
        /** The route colour Ocean goes with, which the other themes' hues are measured from. */
        private static final int OCEAN_ROUTE = 0x4A7FD4;

        /** The window's background, and the maze's corridors. */
        public final int bg;
        /** The sidebar, cards, text boxes and buttons. */
        public final int bgPanel;
        /** Their borders. */
        public final int panelBorder;
        /** The maze's walls. */
        public final int terrain;
        /** About one maze wall in ten, picked once. */
        public final int terrainLight;
        /** Idle nodes, icons, dividers, unselected markers, switches and handles when off. */
        public final int slate;
        /** Hover, and the route's dots. */
        public final int slateLight;
        public final int text;
        /** Descriptions and quieter words. */
        public final int textDim;
        public final int accentDeep;
        /** The current selection, the search's frontier, switches and handles when on. */
        public final int accent;
        /** The maze's start. */
        public final int accentBright;
        /** The middle of what's on, and the maze's goal. */
        public final int highlight;

        private Colours(int[] rgb) {
            this(rgb[0], rgb[1], rgb[2], rgb[3], rgb[4], rgb[5], rgb[6], rgb[7], rgb[8], rgb[9],
                    rgb[10], rgb[11], rgb[12]);
        }

        private Colours(int bg, int bgPanel, int panelBorder, int terrain, int terrainLight,
                int slate, int slateLight, int text, int textDim, int accentDeep, int accent,
                int accentBright, int highlight) {
            this.bg = 0xFF000000 | bg;
            this.bgPanel = 0xFF000000 | bgPanel;
            this.panelBorder = 0xFF000000 | panelBorder;
            this.terrain = 0xFF000000 | terrain;
            this.terrainLight = 0xFF000000 | terrainLight;
            this.slate = 0xFF000000 | slate;
            this.slateLight = 0xFF000000 | slateLight;
            this.text = 0xFF000000 | text;
            this.textDim = 0xFF000000 | textDim;
            this.accentDeep = 0xFF000000 | accentDeep;
            this.accent = 0xFF000000 | accent;
            this.accentBright = 0xFF000000 | accentBright;
            this.highlight = 0xFF000000 | highlight;
        }

        private int[] all() {
            return new int[] {bg, bgPanel, panelBorder, terrain, terrainLight, slate, slateLight,
                    text, textDim, accentDeep, accent, accentBright, highlight};
        }

        /** The accent at this alpha, 0 to 255. */
        public int accent(int alpha) {
            return alpha << 24 | (accent & 0xFFFFFF);
        }

        /** These tokens turned round the colour wheel from Ocean's route hue to {@code route}'s. */
        Colours turned(int route) {
            double turn = hue(route) - hue(OCEAN_ROUTE);
            int[] c = all();
            for (int i = 0; i < c.length; i++) {
                double[] hsl = hsl(c[i]);
                c[i] = rgb((hsl[0] + turn + 360) % 360, hsl[1], hsl[2]);
            }
            return new Colours(c);
        }

        /** These tokens with no hue at all, each as light as before: Graphite. */
        Colours grey() {
            int[] c = all();
            for (int i = 0; i < c.length; i++) {
                double[] hsl = hsl(c[i]);
                c[i] = rgb(0, 0, hsl[2]);
            }
            return new Colours(c);
        }

        Colours towards(Colours to, double t) {
            int[] a = all();
            int[] b = to.all();
            for (int i = 0; i < a.length; i++) {
                a[i] = mix(a[i], b[i], t);
            }
            return new Colours(a);
        }
    }

    /** A colour's hue, 0 to 360. */
    private static double hue(int rgb) {
        return hsl(rgb)[0];
    }

    /** Hue (0 to 360), saturation and lightness (0 to 1). */
    private static double[] hsl(int rgb) {
        double r = ((rgb >> 16) & 255) / 255.0;
        double g = ((rgb >> 8) & 255) / 255.0;
        double b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double d = max - min;
        if (d == 0) {
            return new double[] {0, 0, l};
        }
        double s = d / (1 - Math.abs(2 * l - 1));
        double h = max == r ? ((g - b) / d + 6) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
        return new double[] {h * 60, s, l};
    }

    private static int rgb(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double[] p = h < 60 ? new double[] {c, x, 0} : h < 120 ? new double[] {x, c, 0}
                : h < 180 ? new double[] {0, c, x} : h < 240 ? new double[] {0, x, c}
                : h < 300 ? new double[] {x, 0, c} : new double[] {c, 0, x};
        int out = 0;
        for (int i = 0; i < 3; i++) {
            out |= (int) Math.clamp(Math.round((p[i] + m) * 255), 0, 255) << (16 - 8 * i);
        }
        return out;
    }

    /** How long a theme takes to fade into the next, in milliseconds. */
    public static final long FADE_MS = 220;
    private static Colours fadeFrom;
    private static long fadeStart;

    /** How far into the last change of theme this moment is, 0 to 1, eased. */
    public static double fade() {
        double t = Math.clamp((System.currentTimeMillis() - fadeStart) / (double) FADE_MS, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** The colours to draw with now: the theme's, or partway there from the last one. */
    public static Colours shown() {
        Colours to = current().colours();
        double t = fade();
        return fadeFrom == null || t >= 1 ? to : fadeFrom.towards(to, t);
    }

    private static Theme current;

    /** The theme in use. */
    public static Theme current() {
        if (current == null) {
            current = OCEAN;
            try {
                current = valueOf(Files.readString(file()).strip().toUpperCase(Locale.ROOT));
            } catch (IOException | IllegalArgumentException e) {
                // None picked yet.
            }
        }
        return current;
    }

    public static void use(Theme theme) {
        if (theme != current()) {
            // The logo plays a fresh search in the new colours, as when the window opens (and
            // for Rainbow, keeps on playing).
            LoadingArt logo = LoadingArt.logo();
            if (logo != null) {
                logo.restart();
                if (theme != RAINBOW) {
                    logo.finish();
                }
            }
        }
        fadeFrom = shown();
        fadeStart = System.currentTimeMillis();
        current = theme;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), theme.name().toLowerCase(Locale.ROOT) + "\n");
        } catch (IOException e) {
            // Picked for this game only.
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar-theme.txt");
    }

    /** {@code a} moved towards {@code b} by {@code t}, alpha too. */
    public static int argb(int a, int b, double t) {
        int alpha = (int) Math.round((a >>> 24) * (1 - t) + (b >>> 24) * t);
        return alpha << 24 | mix(a, b, t);
    }

    /** {@code a} moved towards {@code b} by {@code t}, as RGB. */
    public static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
