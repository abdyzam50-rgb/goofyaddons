package astar.client;

import astar.client.ui.LoadingArt;
import astar.client.ui.Theme;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;

/**
 * The mod's loading screen ({@link LoadingArt}) in place of the game's: over the Mojang Studios
 * screen while the game starts and reloads its resources, and behind the text of the screens
 * shown while joining a world or server. Themed like the A* window.
 */
public final class AstarLoading extends Overlay {
    private final LoadingOverlay inner;
    private final Field fadeIn;
    private final Field fadeInStart;
    private final Field fadeOutStart;
    private final Field progress;
    private final ReloadInstance reload;

    private AstarLoading(LoadingOverlay inner) throws ReflectiveOperationException {
        this.inner = inner;
        fadeIn = field("fadeIn");
        fadeInStart = field("fadeInStart");
        fadeOutStart = field("fadeOutStart");
        progress = field("currentProgress");
        reload = (ReloadInstance) field("reload").get(inner);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = LoadingOverlay.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    static void register() {
        LoadingArt.preload();
        // The game puts up its loading overlay itself; swap it for ours, which keeps the game's
        // one for the work (waiting on the reload, telling the game it's done).
        ClientTickEvents.START_CLIENT_TICK.register(client -> wrap(client));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            wrap(client);
            if (loading(screen)) {
                // More loading before the last screen's path was done: keep going under this one.
                if (client.gui.overlay() instanceof Finish) {
                    client.gui.setOverlay(null);
                }
                LoadingArt art = LoadingArt.get();
                if (art != null && art.playing()) {
                    art.carryOn();
                } else if (art != null) {
                    art.restart();
                }
                ScreenEvents.remove(screen).register(s -> {
                    // The game closes it the moment the world is ready; ours finishes its path
                    // over the world first, then fades.
                    LoadingArt shown = LoadingArt.get();
                    if (shown != null && shown.playing() && client.gui.overlay() == null) {
                        client.gui.setOverlay(new Finish());
                    }
                });
            }
            if (screen instanceof ConnectScreen) {
                // It has a Cancel button and its own status line: ours goes behind them.
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) ->
                        draw(g, 1, -1));
            } else if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen
                    || screen instanceof GenericMessageScreen) {
                // Only text and a bar: ours in place of all of it, with its title under ours.
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, a) -> {
                    g.nextStratum();
                    int below = draw(g, 1, progress(s));
                    Component title = s instanceof LevelLoadingScreen
                            ? Component.translatable("multiplayer.downloadingTerrain")
                            : s.getTitle();
                    g.centeredText(client.font, title, g.guiWidth() / 2, below, 0xFFE6E9EF);
                });
            }
        });
    }

    /** The screens shown while joining a world or server, or leaving one. */
    private static boolean loading(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen;
    }

    /** How far a world's terrain is along loading, 0 to 1, or -1 if it isn't one or can't say. */
    private static float progress(Screen screen) {
        if (screen instanceof LevelLoadingScreen) {
            try {
                Field f = LevelLoadingScreen.class.getDeclaredField("smoothedProgress");
                f.setAccessible(true);
                return f.getFloat(screen);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return -1;
            }
        }
        return -1;
    }

    private static void wrap(Minecraft client) {
        if (client.gui.overlay() instanceof LoadingOverlay loading && LoadingArt.get() != null) {
            try {
                LoadingArt.get().restart();
                client.gui.setOverlay(new AstarLoading(loading));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A game whose loading screen we don't know: leave it be.
            }
        }
    }

    @Override
    public boolean isPausing() {
        return inner.isPausing();
    }

    /** Longest the screen stays up after loading to let a path finish, in milliseconds. */
    private static final long MAX_FINISH_MS = 8000;
    private long doneAt = -1;

    @Override
    public void tick() {
        // Once the game has loaded, ours finishes the path it is on before the game's overlay
        // is told, which is what starts the fade out.
        LoadingArt art = LoadingArt.get();
        if (art != null && reload.isDone()) {
            if (doneAt < 0) {
                doneAt = Util.getMillis();
                art.finish();
            }
            if (!art.finished() && Util.getMillis() - doneAt < MAX_FINISH_MS) {
                return;
            }
        }
        inner.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // The game's own steps (fading in over the screen behind, the smoothed progress,
        // fading out and closing), but ours drawn instead of the Mojang Studios logo on red.
        Minecraft mc = Minecraft.getInstance();
        try {
            long now = Util.getMillis();
            boolean fades = fadeIn.getBoolean(inner);
            if (fades && fadeInStart.getLong(inner) == -1) {
                fadeInStart.setLong(inner, now);
            }
            long outStart = fadeOutStart.getLong(inner);
            long inStart = fadeInStart.getLong(inner);
            float out = outStart > -1 ? (now - outStart) / 1000f : -1;
            float in = inStart > -1 ? (now - inStart) / 500f : -1;
            Screen behind = mc.gui.screen();
            float alpha;
            if (out >= 0) {
                // Our path is already complete when this starts, so no second of holding it
                // the way the game holds its logo: straight into the fade.
                if (behind != null) {
                    behind.extractRenderStateWithTooltipAndSubtitles(g, 0, 0, a);
                    mc.gui.hud.extractDeferredSubtitles();
                }
                alpha = 1 - Math.clamp(out, 0, 1);
            } else if (fades) {
                if (behind != null && in < 1) {
                    behind.extractRenderStateWithTooltipAndSubtitles(g, mouseX, mouseY, a);
                    mc.gui.hud.extractDeferredSubtitles();
                }
                alpha = Math.clamp(in, 0.15f, 1);
            } else {
                alpha = 1;
            }
            float shown = Math.clamp(progress.getFloat(inner) * 0.95f
                    + reload.getActualProgress() * 0.05f, 0, 1);
            progress.setFloat(inner, shown);
            g.nextStratum();
            draw(g, alpha, out < 0 ? shown : -1);
            if (out >= 1) {
                mc.gui.setOverlay(null);
            }
        } catch (IllegalAccessException e) {
            // Not ours to draw after all: the game's own then.
            inner.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    /**
     * The whole animation, as big as the screen fits, with a bar along the bottom for how far along
     * loading is ({@code progress} 0 to 1, or none if below 0). Returns where a line of text can go, in GUI
     * units: on a dark band above the bar, so it reads over the art.
     */
    static int draw(GuiGraphicsExtractor g, float alpha, float progress) {
        LoadingArt art = LoadingArt.get();
        if (art == null || alpha <= 0) {
            return g.guiHeight() / 2;
        }
        var window = Minecraft.getInstance().getWindow();
        int sw = window.getWidth();
        int sh = window.getHeight();
        float k = 1f / window.getGuiScale();
        // The whole picture, as big as fits: in whole screen pixels per art pixel if that
        // leaves only a thin margin (its own darkest colour, so it reads as the art's edge),
        // else stretched to fit exactly.
        float fit = Math.min(sw / (float) art.width(), sh / (float) art.height());
        int whole = Math.max(1, (int) fit);
        float scale = whole / fit >= 0.92f ? whole : fit;
        int w = Math.round(art.width() * scale);
        int h = Math.round(art.height() * scale);
        int a = Math.round(Math.clamp(alpha, 0, 1) * 255);

        g.pose().pushMatrix();
        g.pose().scale(k, k);
        g.fill(0, 0, sw, sh, a << 24 | (art.background() & 0xFFFFFF));
        art.draw(g, (sw - w) / 2, (sh - h) / 2, w, h, alpha);
        // A band under the text, darkening towards the bottom, so it reads over the art.
        int lineH = Math.round(9 / k);
        int band = Math.round(lineH * 2.6f);
        int bg = art.background() & 0xFFFFFF;
        for (int i = 0; i < band; i += 2) {
            int shade = Math.round(a * 0.8f * i / band);
            g.fill(0, sh - band + i, sw, sh - band + i + 2, shade << 24 | bg);
        }
        if (progress >= 0) {
            Theme.Colours th = Theme.shown();
            int barH = Math.max(2, Math.round(sh / 270f));
            int filled = Math.round(sw * Math.clamp(progress, 0, 1));
            g.fill(0, sh - barH, sw, sh, a << 24 | (th.panelBorder & 0xFFFFFF));
            g.fill(0, sh - barH, filled, sh, a << 24 | (th.accent & 0xFFFFFF));
        }
        g.pose().popMatrix();
        return g.guiHeight() - 9 - Math.round(band * k / 3);
    }

    /**
     * Ours, staying up over the world a moment after the game closes its loading screen, to
     * finish the path it was on; then it fades out.
     */
    private static final class Finish extends Overlay {
        private static final long FADE_MS = 400;
        private final long start = Util.getMillis();
        private long doneAt = -1;

        @Override
        public boolean isPausing() {
            return false;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            LoadingArt art = LoadingArt.get();
            long now = Util.getMillis();
            if (art == null) {
                Minecraft.getInstance().gui.setOverlay(null);
                return;
            }
            art.finish();
            if (doneAt < 0 && (art.finished() || now - start > MAX_FINISH_MS)) {
                doneAt = now;
            }
            float alpha = doneAt < 0 ? 1 : 1 - (now - doneAt) / (float) FADE_MS;
            if (alpha <= 0) {
                Minecraft.getInstance().gui.setOverlay(null);
                return;
            }
            g.nextStratum();
            draw(g, alpha, -1);
        }
    }
}
