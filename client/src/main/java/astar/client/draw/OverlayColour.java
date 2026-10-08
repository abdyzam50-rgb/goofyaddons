package astar.client.draw;

import java.util.Locale;

/**
 * The overlay's colours: one matte hue for everything (the route line, the key-node blocks and
 * the lead box), in shades of it. Flat, a little muted, so it reads against the world without
 * glowing.
 */
public enum OverlayColour {
    RED(0xD9534F),
    ORANGE(0xE8883A),
    YELLOW(0xE6C84A),
    GREEN(0x5DB85C),
    CYAN(0x4FB8C9),
    BLUE(0x4A7FD4),
    PURPLE(0x8E63C9),
    PINK(0xD96FA8),
    WHITE(0xE6E6E6),
    /** Every hue in turn, round and round: see {@link #lineNow()}. */
    RAINBOW(0xD9534F);

    /** How long the rainbow takes to go round once, in milliseconds. */
    public static final long RAINBOW_MS = 12000;

    /** The hue itself, opaque: the route line. */
    public final int line;
    /** A darker shade: edges. */
    public final int edge;
    /** The hue, see-through: the key-node blocks' faces. */
    public final int fill;
    /** A lighter shade: the lead box. */
    public final int lead;

    OverlayColour(int rgb) {
        line = 0xFF000000 | rgb;
        edge = 0xFF000000 | mix(rgb, 0x000000, 0.45);
        fill = 0x38000000 | rgb;
        lead = 0xFF000000 | mix(rgb, 0xFFFFFF, 0.35);
    }

    /** The route line's colour at this moment: the rainbow's hue now, for {@link #RAINBOW}. */
    public int lineNow() {
        return this == RAINBOW ? 0xFF000000 | rainbow(System.currentTimeMillis()) : line;
    }

    /** The lead box's colour at this moment. */
    public int leadNow() {
        return this == RAINBOW ? 0xFF000000 | mix(rainbow(System.currentTimeMillis()), 0xFFFFFF,
                0.35) : lead;
    }

    /**
     * The rainbow's colour at this time: the hue going round once every {@link #RAINBOW_MS},
     * as strong and bright as the other route colours.
     */
    public static int rainbow(long now) {
        double h = (now % RAINBOW_MS) / (double) RAINBOW_MS * 6;
        double s = 0.68;
        double v = 0.88;
        int i = (int) h;
        double f = h - i;
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        double[] rgb = switch (i % 6) {
            case 0 -> new double[] {v, t, p};
            case 1 -> new double[] {q, v, p};
            case 2 -> new double[] {p, v, t};
            case 3 -> new double[] {p, q, v};
            case 4 -> new double[] {t, p, v};
            default -> new double[] {v, p, q};
        };
        return (int) Math.round(rgb[0] * 255) << 16 | (int) Math.round(rgb[1] * 255) << 8
                | (int) Math.round(rgb[2] * 255);
    }

    /** Its name as typed in {@code .A* show}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The colour with this name, or null. */
    public static OverlayColour named(String name) {
        for (OverlayColour c : values()) {
            if (c.id().equals(name.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
