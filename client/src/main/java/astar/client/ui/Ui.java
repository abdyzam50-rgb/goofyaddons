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
                lettering.prefix + kind + Math.clamp(px, 1, 4)));
    }

    /** How the windows' text is lettered, picked on the Themes page. */
    public enum Lettering {
        /** Inter: plain and clean, made for screens. */
        CLEAN("Clean", "inter_", false),
        /** Atkinson Hyperlegible: every letter easy to tell apart. */
        HYPERLEGIBLE("Hyperlegible", "atkinson_", false),
        /** Lexend: wide and open, made to be easy to read. */
        WIDE("Wide", "lexend_", false),
        /** Nunito: soft and rounded. */
        ROUNDED("Rounded", "nunito_", false),
        /** Pixelify Sans, upper and lower case. */
        SOFT("Soft pixel", "soft_", false),
        /** The game's own letters, upper and lower case. */
        MINECRAFT("Minecraft", "", false),
        /** The game's letters in spaced-out capitals, like a pixel-art game's menu. */
        CAPS("Pixel caps", "", true);

        public final String label;
        final String prefix;
        /** Drawn in capitals a letter at a time, spaced out ({@link PixelText}). */
        public final boolean caps;

        Lettering(String label, String prefix, boolean caps) {
            this.label = label;
            this.prefix = prefix;
            this.caps = caps;
        }
    }

    private static Lettering lettering = readLettering();

    public static Lettering lettering() {
        return lettering;
    }

    /** Uses {@code l} from now on, and keeps it for later games. */
    public static void lettering(Lettering l) {
        lettering = l;
        try {
            java.nio.file.Files.createDirectories(letteringFile().getParent());
            java.nio.file.Files.writeString(letteringFile(),
                    l.name().toLowerCase(java.util.Locale.ROOT) + "\n");
        } catch (java.io.IOException e) {
            // Picked for this game only.
        }
    }

    private static java.nio.file.Path letteringFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("astar-font.txt");
    }

    private static Lettering readLettering() {
        try {
            String s = java.nio.file.Files.readString(letteringFile()).strip();
            return Lettering.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (java.io.IOException | RuntimeException e) {
            return Lettering.CLEAN;
        }
    }

    /** Text in this font: {@code strong} and {@code title} in bold. */
    public static MutableComponent text(String s, FontDescription font) {
        boolean bold = font instanceof FontDescription.Resource(Identifier id)
                && (id.getPath().contains("strong") || id.getPath().contains("title"));
        return Component.literal(s).withStyle(st -> st.withFont(font).withBold(bold));
    }

}
