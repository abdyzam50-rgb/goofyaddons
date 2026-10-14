package astar.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The window's pixel-art pieces, drawn as flat boxes ({@link Panels}) in the theme's colours so
 * every theme recolours them and they stay sharp at any GUI scale: panels with their corner
 * pixels cut, chunky segmented buttons in five states, the switch and the
 * sparkle. One unit is one pixel of the art.
 */
public final class Pixel {

    private Pixel() {}

    /** How a button is drawn. */
    public enum State { NORMAL, HOVER, PRESSED, ACTIVE, DISABLED }

    /**
     * A box with its four corner pixels cut, so its edge reads as drawn by hand: a 1-unit
     * {@code edge} round a {@code fill}.
     */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge,
            int fill) {
        if (w < 3 || h < 3) {
            return;
        }
        Panels.rect(g, x + 1, y + 1, w - 2, h - 2, 0, fill);
        outline(g, x, y, w, h, edge);
    }

    /** Just the cut-corner edge. */
    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge) {
        Panels.rect(g, x + 1, y, w - 2, 1, 0, edge);
        Panels.rect(g, x + 1, y + h - 1, w - 2, 1, 0, edge);
        Panels.rect(g, x, y + 1, 1, h - 2, 0, edge);
        Panels.rect(g, x + w - 1, y + 1, 1, h - 2, 0, edge);
    }

    /**
     * A chunky button: a raised fill split into segments by dark joints, a cut-corner edge and
     * a lit top line, coloured for its state. Its text is the caller's (pressed text sits one
     * unit lower).
     */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, State s,
            Theme.Colours c) {
        int fill;
        int edge;
        int joint;
        int top;
        switch (s) {
            case HOVER -> {
                fill = Theme.mix(c.terrainLight, c.slate, 0.45) | 0xFF000000;
                edge = c.slateLight;
                joint = c.terrainLight;
                top = Theme.mix(fill, c.slateLight, 0.5) | 0xFF000000;
            }
            case PRESSED -> {
                fill = c.panelBorder;
                edge = c.accent;
                joint = c.bgPanel;
                top = c.bgPanel;
            }
            case ACTIVE -> {
                fill = c.terrainLight;
                edge = c.accent;
                joint = c.panelBorder;
                top = c.highlight;
            }
            case DISABLED -> {
                fill = c.bgPanel;
                edge = c.panelBorder;
                joint = Theme.mix(c.bgPanel, c.bg, 0.6) | 0xFF000000;
                top = fill;
            }
            default -> {
                fill = c.terrainLight;
                edge = c.slate;
                joint = c.panelBorder;
                top = Theme.mix(fill, c.slate, 0.4) | 0xFF000000;
            }
        }
        panel(g, x, y, w, h, edge, fill);
        // Joints about every 22 units, evenly.
        int n = Math.max(1, Math.round((w - 2) / 22f));
        for (int i = 1; i < n; i++) {
            Panels.rect(g, x + 1 + (w - 2) * i / n, y + 1, 1, h - 2, 0, joint);
        }
        if (s == State.PRESSED) {
            // Pushed in: a shadow along the top instead of light.
            Panels.rect(g, x + 1, y + 1, w - 2, 1, 0, c.bg);
        } else {
            Panels.rect(g, x + 1, y + 1, w - 2, 1, 0, top);
            Panels.rect(g, x + 1, y + h - 2, w - 2, 1, 0,
                    Theme.mix(fill, c.bg, 0.45) | 0xFF000000);
        }
    }

    /**
     * An on/off switch {@code w} by {@code h}: a block slid to the left in slate when off, to
     * the right in the highlight with the accent filling the track behind it when on.
     */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on,
            boolean hot, Theme.Colours c) {
        int edge = on ? c.accent : hot ? c.slateLight : c.slate;
        panel(g, x, y, w, h, edge, on ? c.accentDeep : c.bgPanel);
        int k = h - 2;
        int kx = on ? x + w - 1 - k : x + 1;
        int knob = on ? c.highlight : hot ? c.slateLight : c.slate;
        Panels.rect(g, kx, y + 1, k, k, 0, knob);
        // A small diagonal notch on the block, like the mockup's grip.
        int mark = on ? c.accent : Theme.mix(knob, c.bg, 0.45) | 0xFF000000;
        int mx = kx + k / 2 - 1;
        int my = y + 1 + k / 2 - 1;
        Panels.rect(g, mx, my, 1, 1, 0, mark);
        Panels.rect(g, mx + 1, my + 1, 1, 1, 0, mark);
    }

    /**
     * A four-pointed sparkle centred on {@code cx, cy}, {@code r} units from the centre to each
     * tip: a bright core, arms in {@code arm} fading out to the tips.
     */
    public static void sparkle(GuiGraphicsExtractor g, int cx, int cy, int r, int arm,
            int core, int bg) {
        for (int i = 1; i <= r; i++) {
            int colour = Theme.mix(arm, bg, (i - 1) / (double) (r + 1)) | 0xFF000000;
            Panels.rect(g, cx + i, cy, 1, 1, 0, colour);
            Panels.rect(g, cx - i, cy, 1, 1, 0, colour);
            Panels.rect(g, cx, cy + i, 1, 1, 0, colour);
            Panels.rect(g, cx, cy - i, 1, 1, 0, colour);
        }
        int glow = Theme.mix(arm, bg, 0.4) | 0xFF000000;
        Panels.rect(g, cx - 1, cy - 1, 1, 1, 0, glow);
        Panels.rect(g, cx + 1, cy - 1, 1, 1, 0, glow);
        Panels.rect(g, cx - 1, cy + 1, 1, 1, 0, glow);
        Panels.rect(g, cx + 1, cy + 1, 1, 1, 0, glow);
        Panels.rect(g, cx, cy, 1, 1, 0, core);
    }

    /** A small chevron pointing down, 5 wide and 3 high, its top left at {@code x, y}. */
    public static void chevron(GuiGraphicsExtractor g, int x, int y, int colour) {
        Panels.rect(g, x, y, 1, 1, 0, colour);
        Panels.rect(g, x + 4, y, 1, 1, 0, colour);
        Panels.rect(g, x + 1, y + 1, 1, 1, 0, colour);
        Panels.rect(g, x + 3, y + 1, 1, 1, 0, colour);
        Panels.rect(g, x + 2, y + 2, 1, 1, 0, colour);
    }
}
