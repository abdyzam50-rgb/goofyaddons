package astar.client.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;

/**
 * The lettering the window and the HUD widgets share, as picked ({@link Ui#lettering}): as
 * written, or in capitals drawn a letter at a time with {@link #TRACK} more between them.
 * {@link #width} measures the same way, so fitting and wrapping line up with what's drawn.
 */
public final class PixelText {

    private PixelText() {}

    /** Units between letters, on top of the font's own gap. */
    public static final int TRACK = 1;
    /** Each letter's width in each font, measured once. */
    private static final Map<FontDescription, Map<Integer, Integer>> ADVANCES = new HashMap<>();

    public static void draw(GuiGraphicsExtractor g, Font font, String s, FontDescription f,
            int at, int top, int colour) {
        if (!Ui.lettering().caps) {
            g.text(font, Ui.text(s, f), at, top, colour, false);
            return;
        }
        String up = s.toUpperCase(Locale.ROOT);
        int x = at;
        for (int i = 0; i < up.length(); ) {
            int cp = up.codePointAt(i);
            if (cp != ' ') {
                g.text(font, Ui.text(Character.toString(cp), f), x, top, colour, false);
            }
            x += advance(font, cp, f) + TRACK;
            i += Character.charCount(cp);
        }
    }

    public static int width(Font font, String s, FontDescription f) {
        if (!Ui.lettering().caps) {
            return font.width(Ui.text(s, f));
        }
        String up = s.toUpperCase(Locale.ROOT);
        int w = 0;
        int n = 0;
        for (int i = 0; i < up.length(); ) {
            int cp = up.codePointAt(i);
            w += advance(font, cp, f);
            n++;
            i += Character.charCount(cp);
        }
        return w + Math.max(0, n - 1) * TRACK;
    }

    private static int advance(Font font, int cp, FontDescription f) {
        return ADVANCES.computeIfAbsent(f, k -> new HashMap<>()).computeIfAbsent(cp,
                c -> font.width(Ui.text(Character.toString(c), f)));
    }
}
