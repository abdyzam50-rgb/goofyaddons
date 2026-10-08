package astar.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * The screens' look: their font (the game's own pixel letters, at sizes that keep every letter
 * pixel a whole number of screen pixels). Colours are {@link Theme.Colours}.
 */
public final class Ui {

    private Ui() {}

    /** {@code assets/astar/font/ui.json}. */
    public static final FontDescription FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("astar", "ui"));

    /** Text in the screens' font. */
    public static MutableComponent text(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(FONT));
    }

    /** The same text in the screens' font. */
    public static MutableComponent text(Component c) {
        return c.copy().withStyle(st -> st.withFont(FONT));
    }

    /**
     * The screen font of a kind ({@code body}, {@code strong}, {@code small} or {@code title})
     * made for {@code px} screen pixels per GUI unit (1 to 4), so its letters land on whole
     * pixels: {@code assets/astar/font/<kind><px>.json}.
     */
    public static FontDescription font(String kind, int px) {
        return new FontDescription.Resource(Identifier.fromNamespaceAndPath("astar",
                kind + Math.clamp(px, 1, 4)));
    }

    /** Text in this font: {@code strong} and {@code title} in bold. */
    public static MutableComponent text(String s, FontDescription font) {
        boolean bold = font instanceof FontDescription.Resource(Identifier id)
                && (id.getPath().startsWith("strong") || id.getPath().startsWith("title"));
        return Component.literal(s).withStyle(st -> st.withFont(font).withBold(bold));
    }

}
