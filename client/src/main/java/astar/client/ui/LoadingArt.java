package astar.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * An A* search spreading over a map, then the route it finds, again and again from other starts:
 * over the logo's map, filling the loading screen ({@code assets/astar/loading/route-search.bin}),
 * and over the "A" as the A* window's logo, played once when it opens
 * ({@code assets/astar/loading/astar-a.bin}). Frames are palette indices; each palette entry is a
 * grey {@code floor} plus a {@code chroma}, part fixed and part the theme's hue ({@code gain}), so
 * the search and the route take the theme's colour while the map stays near grey. Read straight
 * from the jar, not through the resource manager, so it can draw while the game's own resources
 * still load.
 */
public final class LoadingArt {
    /**
     * The frames and their timing. The loading screen's art ({@code axis} set) has one chroma
     * strength per entry, coloured {@code chroma * (gain * T + (1 - gain) * axis)} for the theme's
     * hue T scaled to 0..1; the logo's has a chroma colour per entry, coloured
     * {@code L(chroma) * (gain * T / L(T) + (1 - gain))}, with its background left clear.
     */
    private record Frames(int w, int h, int[] floor, int[][] chroma, float[] gain, float[] axis,
            int[] seqFrame, int[] seqMs, int[] pathStarts, int[] pathFound, byte[] pixels) {
    }

    private static final CompletableFuture<Frames> READING = new CompletableFuture<>();
    private static final CompletableFuture<Frames> READING_LOGO = new CompletableFuture<>();
    private static LoadingArt screen;
    private static LoadingArt logo;

    private final Frames f;
    private final DynamicTexture[] textures = new DynamicTexture[2];
    private final Identifier[] ids = new Identifier[2];
    private final int[] shownFrame = {-1, -1};
    private final int[] shownColour = new int[2];
    private final java.util.Random random = new java.util.Random();
    private int lastPath = -1;
    private int step;
    private long stepStart = -1;
    private long lastDrawn;
    private boolean finishing;
    private int lutColour;
    private int[] lut;

    private LoadingArt(Frames f, String name) {
        this.f = f;
        ids[0] = Identifier.fromNamespaceAndPath("astar", name + "/a");
        ids[1] = Identifier.fromNamespaceAndPath("astar", name + "/b");
    }

    private static Frames read(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic == 0x41534C44) { // "ASLD"
            return readMap(in);
        }
        if (magic != 0x41534C32) { // "ASL2"
            throw new IOException("not a loading animation");
        }
        int w = in.readUnsignedShort();
        int h = in.readUnsignedShort();
        int count = in.readUnsignedShort();
        int colours = in.readUnsignedByte();
        int[] floor = new int[colours];
        int[][] chroma = new int[colours][3];
        float[] gain = new float[colours];
        for (int i = 0; i < colours; i++) {
            floor[i] = in.readUnsignedByte();
            for (int c = 0; c < 3; c++) {
                chroma[i][c] = in.readUnsignedByte();
            }
            gain[i] = in.readUnsignedByte() / 100f;
        }
        int steps = in.readUnsignedShort();
        int[] seqFrame = new int[steps];
        int[] seqMs = new int[steps];
        for (int i = 0; i < steps; i++) {
            seqFrame[i] = in.readUnsignedShort();
            seqMs[i] = in.readUnsignedShort();
        }
        // Each path: the step its start appears on, and the step it is found and whole on.
        int paths = in.readUnsignedByte();
        int[] starts = new int[paths];
        int[] found = new int[paths];
        for (int p = 0; p < paths; p++) {
            starts[p] = in.readUnsignedShort();
            found[p] = in.readUnsignedShort();
        }
        byte[] pixels = new InflaterInputStream(in).readNBytes(count * w * h);
        if (pixels.length != count * w * h) {
            throw new IOException("loading animation cut short");
        }
        return new Frames(w, h, floor, chroma, gain, null, seqFrame, seqMs, starts, found,
                pixels);
    }

    /** The loading screen's map art, whose paths are found from the timing. */
    private static Frames readMap(DataInputStream in) throws IOException {
        int w = in.readUnsignedShort();
        int h = in.readUnsignedShort();
        int count = in.readUnsignedShort();
        int colours = in.readUnsignedByte();
        int[] floor = new int[colours];
        int[][] chroma = new int[colours][1];
        float[] gain = new float[colours];
        for (int i = 0; i < colours; i++) {
            floor[i] = in.readUnsignedByte();
            chroma[i][0] = in.readUnsignedByte();
            gain[i] = in.readUnsignedByte() / 100f;
        }
        float[] axis = new float[3];
        for (int i = 0; i < 3; i++) {
            axis[i] = in.readFloat();
        }
        int steps = in.readUnsignedShort();
        int[] seqFrame = new int[steps];
        int[] seqMs = new int[steps];
        for (int i = 0; i < steps; i++) {
            seqFrame[i] = in.readUnsignedShort();
            seqMs[i] = in.readUnsignedShort();
        }
        // A path starts with its start square blinking (a frame, another, the first again), and
        // is found where its last frame comes back after the burst at the goal.
        java.util.List<Integer> starts = new java.util.ArrayList<>();
        for (int i = 0; i + 2 < steps; i++) {
            if (seqFrame[i] == seqFrame[i + 2] && seqFrame[i] != seqFrame[i + 1]
                    && (starts.isEmpty() || i > starts.getLast() + 2)) {
                starts.add(i);
            }
        }
        if (starts.isEmpty() || starts.getFirst() != 0) {
            starts.addFirst(0);
        }
        int[] pathStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        int[] pathFound = new int[pathStarts.length];
        for (int p = 0; p < pathStarts.length; p++) {
            int end = p + 1 < pathStarts.length ? pathStarts[p + 1] : steps;
            pathFound[p] = end - 1;
            for (int i = pathStarts[p] + 3; i < end; i++) {
                if (i >= 4 && seqFrame[i] == seqFrame[i - 4]) {
                    pathFound[p] = i;
                    break;
                }
            }
        }
        byte[] pixels = new InflaterInputStream(in).readNBytes(count * w * h);
        if (pixels.length != count * w * h) {
            throw new IOException("loading animation cut short");
        }
        return new Frames(w, h, floor, chroma, gain, axis, seqFrame, seqMs, pathStarts,
                pathFound, pixels);
    }

    /**
     * Starts unpacking the animation on its own thread, so no loading screen waits on it (it is
     * a couple of megabytes unpacked, kept for the whole game, so no later screen does either).
     */
    public static void preload() {
        Thread t = new Thread(() -> {
            unpack("route-search.bin", READING);
            unpack("astar-a.bin", READING_LOGO);
        }, "astar loading art");
        t.setDaemon(true);
        t.start();
    }

    private static void unpack(String name, CompletableFuture<Frames> into) {
        try (InputStream in = LoadingArt.class.getResourceAsStream(
                "/assets/astar/loading/" + name)) {
            if (in == null) {
                throw new IOException("missing");
            }
            into.complete(read(new DataInputStream(in)));
        } catch (IOException | RuntimeException e) {
            into.complete(null);
        }
    }

    /** The loading screen's animation, or null while it is still being unpacked or couldn't be. */
    public static LoadingArt get() {
        if (screen == null && READING.isDone() && READING.getNow(null) != null) {
            screen = new LoadingArt(READING.getNow(null), "loading");
        }
        return screen;
    }

    /** The window's logo: the search over the "A", on its own clock. */
    public static LoadingArt logo() {
        if (logo == null && READING_LOGO.isDone() && READING_LOGO.getNow(null) != null) {
            logo = new LoadingArt(READING_LOGO.getNow(null), "logo");
        }
        return logo;
    }

    public int width() {
        return f.w;
    }

    public int height() {
        return f.h;
    }

    /** Whether the animation has been drawn lately, so a loading screen is up. */
    public boolean playing() {
        return System.currentTimeMillis() - lastDrawn < 3000;
    }

    /**
     * Starts again on a path picked at random (not the one just shown), for a new loading
     * screen; and loops on from there until {@link #finish}.
     */
    public void restart() {
        int path;
        do {
            path = random.nextInt(f.pathStarts.length);
        } while (f.pathStarts.length > 1 && path == lastPath);
        lastPath = path;
        step = f.pathStarts[path];
        stepStart = -1;
        finishing = false;
    }

    /**
     * Asks the animation to stop once the path it is on has been found (or, if it is past that,
     * the next one), and hold there: the loading screen can then go.
     */
    public void finish() {
        finishing = true;
    }

    /** Back to looping, when more loading comes along before the screen has gone. */
    public void carryOn() {
        finishing = false;
    }

    /** Whether it has stopped on a found path after {@link #finish}. */
    public boolean finished() {
        return finishing && step == pathFound(step) && stepStart >= 0
                && System.currentTimeMillis() - stepStart >= f.seqMs[step];
    }

    /**
     * Moves the loop on by the clock, so a path always takes the same few seconds however often
     * the game draws. Only after a long stall (the game busy loading for a while) does it pick
     * up from where it was instead of jumping a whole stretch ahead.
     */
    private void advance(long now) {
        if (stepStart < 0) {
            stepStart = now;
        }
        for (int moves = 0; now - stepStart >= f.seqMs[step]; moves++) {
            if (finishing && step == pathFound(step)) {
                return;
            }
            if (moves == 8) {
                stepStart = now;
                return;
            }
            stepStart += f.seqMs[step];
            step = (step + 1) % f.seqMs.length;
        }
    }

    /** The step on which the path that {@code step} belongs to is found and whole. */
    private int pathFound(int step) {
        int found = f.pathFound[0];
        for (int i = 0; i < f.pathStarts.length; i++) {
            if (step >= f.pathStarts[i]) {
                found = f.pathFound[i];
            }
        }
        return found;
    }

    /**
     * Draws the moment at {@code (x, y)}, stretched to {@code dw} by {@code dh}, coloured for the
     * theme on show, at this opacity (0 to 1). Each frame melts into the next over its time on
     * screen, so the search spreads smoothly whatever the game's frame rate.
     */
    public void draw(GuiGraphicsExtractor g, int x, int y, int dw, int dh, float alpha) {
        long now = System.currentTimeMillis();
        lastDrawn = now;
        advance(now);
        int colour = Theme.shown().accent;
        boolean held = finishing && step == pathFound(step);
        int frame = f.seqFrame[step];
        int next = f.seqFrame[(step + 1) % f.seqFrame.length];
        float t = held ? 0 : Math.clamp((now - stepStart) / (float) f.seqMs[step], 0, 1);
        int a = Math.round(Math.clamp(alpha, 0, 1) * 255);
        int w = f.w;
        int h = f.h;
        g.blit(RenderPipelines.GUI_TEXTURED, texture(0, frame, colour), x, y, 0, 0, dw, dh, w, h,
                w, h, a << 24 | 0xFFFFFF);
        int b = Math.round(t * a);
        if (b > 0 && next != frame) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture(1, next, colour), x, y, 0, 0, dw, dh, w,
                    h, w, h, b << 24 | 0xFFFFFF);
        }
    }

    /**
     * One of the two textures, holding {@code frame} in {@code colour}: the other one if it
     * already does (the frame shown next becomes the frame shown), else this one, repainted.
     */
    private Identifier texture(int which, int frame, int colour) {
        for (int i = 0; i < 2; i++) {
            if (shownFrame[i] == frame && shownColour[i] == colour && textures[i] != null) {
                if (i != which && shownFrame[which] != frame) {
                    // Swap them so the other slot is free for the next frame.
                    DynamicTexture tex = textures[i];
                    textures[i] = textures[which];
                    textures[which] = tex;
                    Identifier id = ids[i];
                    ids[i] = ids[which];
                    ids[which] = id;
                    int fr = shownFrame[i];
                    shownFrame[i] = shownFrame[which];
                    shownFrame[which] = fr;
                    int c = shownColour[i];
                    shownColour[i] = shownColour[which];
                    shownColour[which] = c;
                }
                return ids[which];
            }
        }
        if (textures[which] == null) {
            textures[which] = new DynamicTexture(() -> "astar loading", f.w, f.h, false);
            Minecraft.getInstance().getTextureManager().register(ids[which], textures[which]);
        }
        paint(textures[which].getPixels(), frame, colour);
        textures[which].upload();
        shownFrame[which] = frame;
        shownColour[which] = colour;
        return ids[which];
    }

    /** The art's darkest colour, for the screen around it. */
    public int background() {
        return palette(Theme.shown().accent)[0] | 0xFF000000;
    }

    private void paint(NativeImage image, int frame, int colour) {
        int[] lut = palette(colour);
        int at = frame * f.w * f.h;
        for (int y = 0; y < f.h; y++) {
            for (int x = 0; x < f.w; x++) {
                image.setPixel(x, y, lut[f.pixels[at++] & 0xFF]);
            }
        }
    }

    /** Each palette entry as ARGB for this theme colour. */
    private int[] palette(int colour) {
        if (lut != null && lutColour == colour) {
            return lut;
        }
        int[] lut = f.axis != null ? mapPalette(colour) : logoPalette(colour);
        this.lut = lut;
        lutColour = colour;
        return lut;
    }

    /** The map art: {@code floor + chroma * (gain * T + (1 - gain) * axis)}, T scaled 0..1. */
    private int[] mapPalette(int colour) {
        int[] c = {(colour >> 16) & 255, (colour >> 8) & 255, colour & 255};
        int lo = Math.min(c[0], Math.min(c[1], c[2]));
        int hi = Math.max(c[0], Math.max(c[1], c[2]));
        // A grey (Graphite) has no hue, so it gets an even, dimmer grey.
        float[] hue = hi - lo < 40 ? new float[] {0.6f, 0.6f, 0.6f}
                : new float[] {(c[0] - lo) / (float) (hi - lo), (c[1] - lo) / (float) (hi - lo),
                        (c[2] - lo) / (float) (hi - lo)};
        int[] lut = new int[f.floor.length];
        for (int i = 0; i < lut.length; i++) {
            int argb = 0xFF000000;
            for (int ch = 0; ch < 3; ch++) {
                float d = f.gain[i] * hue[ch] + (1 - f.gain[i]) * f.axis[ch];
                int v = Math.clamp(Math.round(f.floor[i] + f.chroma[i][0] * d), 0, 255);
                argb |= v << (16 - 8 * ch);
            }
            lut[i] = argb;
        }
        return lut;
    }

    /**
     * The logo: {@code floor + L(chroma) * (gain * T / L(T) + (1 - gain))}, where T is the
     * theme's hue (its weakest channel taken off) and L is brightness, so every theme lights it
     * as much as its own blue did; Ocean shows the colours it was drawn in. Its background (the
     * first two entries) is left clear, so the window shows through.
     */
    private int[] logoPalette(int colour) {
        boolean original = colour == Theme.OCEAN.colours.accent;
        float[] hue = direction(colour);
        int[] lut = new int[f.floor.length];
        for (int i = 0; i < lut.length; i++) {
            int[] ch = f.chroma[i];
            float strength = luma(ch[0], ch[1], ch[2]);
            int argb = i < 2 ? 0 : 0xFF000000;
            for (int c = 0; c < 3; c++) {
                float v = original ? f.floor[i] + ch[c]
                        : f.floor[i] + strength * (f.gain[i] * hue[c] + (1 - f.gain[i]));
                argb |= Math.clamp(Math.round(v), 0, 255) << (16 - 8 * c);
            }
            lut[i] = argb;
        }
        return lut;
    }

    private static float luma(float r, float g, float b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    /**
     * The theme colour as a hue of brightness 1: its weakest channel taken off, over what is
     * left's brightness. A grey (Graphite) has none, so it stays grey.
     */
    private static float[] direction(int colour) {
        int[] c = {(colour >> 16) & 255, (colour >> 8) & 255, colour & 255};
        int lo = Math.min(c[0], Math.min(c[1], c[2]));
        int hi = Math.max(c[0], Math.max(c[1], c[2]));
        float l = luma(c[0] - lo, c[1] - lo, c[2] - lo);
        if (hi - lo < 40 || l < 1) {
            return new float[] {1, 1, 1};
        }
        return new float[] {(c[0] - lo) / l, (c[1] - lo) / l, (c[2] - lo) / l};
    }
}
