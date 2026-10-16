package astar.client;

import astar.client.ui.Panels;
import astar.client.ui.Pixel;
import astar.client.ui.PixelText;
import astar.client.ui.Theme;
import astar.client.ui.Ui;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's HUD: small pixel-art widgets (the route, where the player is, their speed, frames
 * and ping, which map this is), each turned on and sized in the window's Widgets page and
 * moved in the {@link HudEditor}; and toasts in the bottom right corner when a trip starts,
 * finds its route and ends. Kept in {@code config/astar-hud.txt}.
 */
final class Hud {

    private Hud() {}

    /** One widget: what it shows, whether it's on, where it is and how big. */
    static final class Widget {
        final String id;
        final String label;
        final String about;
        final boolean defOn;
        final double defX;
        final double defY;
        /** Its lines, the first its name. */
        final Supplier<List<String>> lines;
        boolean on;
        /** Where it is: 0 at the left (top) edge of the screen, 1 at the right (bottom). */
        double fx;
        double fy;
        /** Screen pixels per unit, 1 to 4, or 0 for the window's own. */
        int size;

        Widget(String id, String label, String about, boolean on, double fx, double fy,
                Supplier<List<String>> lines) {
            this.id = id;
            this.label = label;
            this.about = about;
            this.defOn = on;
            this.defX = fx;
            this.defY = fy;
            this.lines = lines;
            this.on = on;
            this.fx = fx;
            this.fy = fy;
        }

        void resetPlace() {
            fx = defX;
            fy = defY;
            size = 0;
        }
    }

    static final List<Widget> WIDGETS = List.of(
            new Widget("route", "Route", "Where .A* is going and how far is left.", true, 0, 0,
                    Hud::routeLines),
            new Widget("coords", "Coordinates", "Your block and which way you face.", false, 0,
                    0.32, Hud::coordLines),
            new Widget("speed", "Speed", "How fast you're moving, in blocks a second.", false, 0,
                    0.5, Hud::speedLines),
            new Widget("performance", "Performance", "Frames a second and ping.", false, 1, 0,
                    Hud::perfLines),
            new Widget("map", "Map", "Which saved map .A* thinks this is.", false, 1, 0.2,
                    () -> {
                        Places.Place p = Places.current();
                        return List.of("Map", p == null ? "Unknown" : p.name);
                    }));

    /** Whether trips pop up toasts. */
    static boolean toasts = true;

    /** Set while the editor is open: it draws the widgets itself. */
    static boolean editing;

    private static AstarClient client;
    private static double speed;
    private static Vec3 lastPos;

    static void register(AstarClient astar) {
        client = astar;
        load();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("astar", "widgets"),
                (g, delta) -> draw(g));
    }

    // ---- What the widgets show ---------------------------------------------------------------

    private static List<String> speedLines() {
        return List.of("Speed", String.format(Locale.ROOT, "%.1f b/s", speed));
    }

    private static List<String> routeLines() {
        Navigator nav = client == null ? null : client.navigator();
        if (nav == null || nav.state() == Navigator.State.DONE) {
            return List.of("Route", "Idle");
        }
        BlockPos goal = nav.goal();
        String state = switch (nav.state()) {
            case PLANNING -> "Planning";
            case DRIVING -> "Going";
            case PAUSED -> "Paused";
            case DONE -> "Idle";
        };
        List<String> lines = new ArrayList<>(List.of("Route", state,
                "To " + goal.getX() + " " + goal.getY() + " " + goal.getZ()));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            double d = Math.sqrt(player.blockPosition().distSqr(goal));
            lines.add(String.format(Locale.ROOT, "%.0f blocks left", d));
        }
        return lines;
    }

    private static final String[] FACING = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private static List<String> coordLines() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of("Coordinates", "-");
        }
        BlockPos at = player.blockPosition();
        float yaw = ((player.getYRot() % 360) + 360) % 360;
        String facing = FACING[Math.round(yaw / 45) % 8];
        return List.of("Coordinates", at.getX() + " " + at.getY() + " " + at.getZ(),
                "Facing " + facing);
    }

    private static List<String> perfLines() {
        Minecraft mc = Minecraft.getInstance();
        int ping = 0;
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            ping = info == null ? 0 : info.getLatency();
        }
        return List.of("Performance", mc.getFps() + " fps", ping + " ms ping");
    }

    // ---- Each tick ---------------------------------------------------------------------------

    private static Navigator lastNav;
    private static Navigator.State lastState;
    private static boolean announcedDrive;

    /** Measures the speed and turns trips' changes into toasts. */
    static void tick(Minecraft mc, Navigator nav) {
        if (mc.player != null) {
            Vec3 pos = mc.player.position();
            double now = lastPos == null ? 0
                    : Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z) * 20;
            // Smoothed a little, so it reads steadily.
            speed += (now - speed) * 0.35;
            lastPos = pos;
        } else {
            lastPos = null;
            speed = 0;
        }
        if (nav != lastNav) {
            lastNav = nav;
            lastState = null;
            announcedDrive = false;
        }
        if (nav == null || nav.state() == lastState) {
            return;
        }
        Navigator.State s = nav.state();
        BlockPos goal = nav.goal();
        String to = goal.getX() + " " + goal.getY() + " " + goal.getZ();
        switch (s) {
            case PLANNING -> {
                if (lastState == null) {
                    toast(Toast.INFO, "Planning", "A route to " + to);
                }
            }
            case DRIVING -> {
                if (!announcedDrive) {
                    announcedDrive = true;
                    toast(Toast.OK, "Route found", "Going to " + to);
                }
            }
            case PAUSED -> toast(Toast.WARN, "Paused", "Waiting to go on");
            case DONE -> {
                String o = nav.outcome();
                if (o.startsWith("stopped")) {
                    toast(Toast.WARN, "Stopped", o.replaceFirst("^stopped:?\\s*", ""));
                } else {
                    toast(Toast.OK, "Arrived", o.isEmpty() ? to : o);
                }
            }
        }
        lastState = s;
    }

    // ---- Toasts ------------------------------------------------------------------------------

    record Toast(int kind, String title, String text, long born) {
        static final int INFO = 0;
        static final int OK = 1;
        static final int WARN = 2;
    }

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static final long LIFE = 3500;
    private static final long SLIDE = 220;

    static void toast(int kind, String title, String text) {
        if (!toasts) {
            return;
        }
        TOASTS.add(new Toast(kind, title, text, System.currentTimeMillis()));
        // At most three at once: the oldest goes.
        while (TOASTS.size() > 3) {
            TOASTS.removeFirst();
        }
    }

    /** Shows one even when toasts are off: the Widgets page's "Try it". */
    static void sample() {
        TOASTS.add(new Toast(Toast.OK, "Route found", "Going to 31 101 -27",
                System.currentTimeMillis()));
        while (TOASTS.size() > 3) {
            TOASTS.removeFirst();
        }
    }

    // ---- Drawing -----------------------------------------------------------------------------

    /** Screen pixels per unit of the window and the toasts, as the window works it out. */
    static int windowPx() {
        return Math.clamp(Minecraft.getInstance().getWindow().getHeight() / 400, 1, 4);
    }

    static int px(Widget w) {
        return w.size == 0 ? windowPx() : w.size;
    }

    /** GUI coordinates per unit. */
    static float scale(int px) {
        return px / (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    private static final int PAD = 6;

    /** A widget's size in units: {width, height}. */
    static int[] size(Widget w) {
        int px = px(w);
        var font = Minecraft.getInstance().font;
        List<String> lines = w.lines.get();
        int most = PixelText.width(font, lines.getFirst(), Ui.font("small", px));
        for (int i = 1; i < lines.size(); i++) {
            most = Math.max(most, PixelText.width(font, lines.get(i), Ui.font("body", px)));
        }
        return new int[] {most + 2 * PAD + 3, 2 * PAD + 9 + 11 * (lines.size() - 1)};
    }

    /** The gap kept between the widgets and the screen's edges, in GUI coordinates. */
    static final float EDGE = 3;

    /** Where a widget's top left is, in GUI coordinates: {x, y, w, h}. */
    static float[] box(Widget w, int screenW, int screenH) {
        int[] s = size(w);
        float k = scale(px(w));
        float bw = s[0] * k;
        float bh = s[1] * k;
        return new float[] {EDGE + (float) (w.fx * Math.max(0, screenW - bw - 2 * EDGE)),
                EDGE + (float) (w.fy * Math.max(0, screenH - bh - 2 * EDGE)), bw, bh};
    }

    private static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || editing || mc.gui.hud.isHidden()) {
            return;
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        for (Widget w : WIDGETS) {
            if (w.on) {
                drawWidget(g, w, sw, sh, false);
            }
        }
        drawToasts(g, sw, sh);
    }

    /** One widget at its place; {@code lit} outlines it, as the editor does the one held. */
    static void drawWidget(GuiGraphicsExtractor g, Widget w, int sw, int sh, boolean lit) {
        Theme.Colours th = Theme.shown();
        var font = Minecraft.getInstance().font;
        int px = px(w);
        float k = scale(px);
        float[] b = box(w, sw, sh);
        int[] s = size(w);
        List<String> lines = w.lines.get();
        g.pose().pushMatrix();
        g.pose().translate(b[0], b[1]);
        g.pose().scale(k, k);
        Pixel.panel(g, 0, 0, s[0], s[1], lit ? th.accent
                : Theme.mix(th.panelBorder, th.slate, 0.35) | 0xFF000000, th.bgPanel);
        // An accent strip down the left, like a route's line.
        Panels.rect(g, 1, 2, 2, s[1] - 4, 0, th.accent);
        FontDescription small = Ui.font("small", px);
        FontDescription body = Ui.font("body", px);
        PixelText.draw(g, font, lines.getFirst(), small, PAD + 3, PAD, th.textDim);
        for (int i = 1; i < lines.size(); i++) {
            PixelText.draw(g, font, lines.get(i), body, PAD + 3, PAD + 9 + 11 * (i - 1) + 1,
                    i == 1 ? th.text : th.textDim);
        }
        g.pose().popMatrix();
    }

    private static void drawToasts(GuiGraphicsExtractor g, int sw, int sh) {
        long now = System.currentTimeMillis();
        TOASTS.removeIf(t -> now - t.born() > LIFE);
        if (TOASTS.isEmpty()) {
            return;
        }
        Theme.Colours th = Theme.shown();
        var font = Minecraft.getInstance().font;
        int px = windowPx();
        float k = scale(px);
        FontDescription strong = Ui.font("strong", px);
        FontDescription small = Ui.font("small", px);
        int uw = Math.round(sw / k);
        int uh = Math.round(sh / k);
        int w = 160;
        int h = 30;
        g.pose().pushMatrix();
        g.pose().scale(k, k);
        int bottom = uh - 8;
        // Newest at the bottom.
        for (Iterator<Toast> it = TOASTS.reversed().iterator(); it.hasNext(); ) {
            Toast t = it.next();
            long age = now - t.born();
            double in = ease(Math.min(1, age / (double) SLIDE));
            double out = ease(Math.min(1, Math.max(0, (age - (LIFE - SLIDE)) / (double) SLIDE)));
            int x = uw - 8 - w + (int) Math.round((1 - in + out) * (w + 12));
            int y = bottom - h;
            int ink = switch (t.kind()) {
                case Toast.OK -> 0xFF4FC07A;
                case Toast.WARN -> 0xFFE0A040;
                default -> th.slateLight;
            };
            Pixel.panel(g, x, y, w, h, Theme.mix(th.panelBorder, th.slate, 0.35) | 0xFF000000,
                    th.bgPanel);
            Panels.rect(g, x + 1, y + 2, 2, h - 4, 0, ink);
            Pixel.sparkle(g, x + 14, y + h / 2, 4, ink, th.highlight, th.bgPanel);
            PixelText.draw(g, font, t.title(), strong, x + 26, y + 6, th.text);
            PixelText.draw(g, font, fit(font, t.text(), small, w - 32), small, x + 26, y + 18,
                    th.textDim);
            // How long it has left, along the bottom.
            int left = (int) Math.round((w - 4) * Math.max(0, 1 - age / (double) LIFE));
            Panels.rect(g, x + 2, y + h - 2, left, 1, 0, ink);
            bottom = y - 4;
        }
        g.pose().popMatrix();
    }

    private static String fit(net.minecraft.client.gui.Font font, String s, FontDescription f,
            int w) {
        if (PixelText.width(font, s, f) <= w) {
            return s;
        }
        int end = s.length();
        while (end > 0 && PixelText.width(font, s.substring(0, end) + "...", f) > w) {
            end--;
        }
        return s.substring(0, end).stripTrailing() + "...";
    }

    private static double ease(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }

    // ---- Kept between games ------------------------------------------------------------------

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar-hud.txt");
    }

    static Widget find(String id) {
        for (Widget w : WIDGETS) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return null;
    }

    private static void load() {
        try {
            if (!Files.exists(file())) {
                return;
            }
            for (String line : Files.readAllLines(file())) {
                String[] p = line.strip().split("\\s+");
                if (p.length == 2 && p[0].equals("toasts")) {
                    toasts = p[1].equals("on");
                } else if (p.length == 6 && p[0].equals("widget")) {
                    Widget w = find(p[1]);
                    if (w != null) {
                        w.on = p[2].equals("on");
                        w.fx = Math.clamp(Double.parseDouble(p[3]), 0, 1);
                        w.fy = Math.clamp(Double.parseDouble(p[4]), 0, 1);
                        w.size = Math.clamp(Integer.parseInt(p[5]), 0, 4);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[astar] couldn't read " + file() + ": " + e);
        }
    }

    static void save() {
        StringBuilder sb = new StringBuilder("# The A* HUD: toasts on|off; widget <id> on|off"
                + " <x 0-1> <y 0-1> <size 1-4, 0 = the window's>\n");
        sb.append("toasts ").append(toasts ? "on" : "off").append('\n');
        for (Widget w : WIDGETS) {
            sb.append(String.format(Locale.ROOT, "widget %s %s %.4f %.4f %d%n", w.id,
                    w.on ? "on" : "off", w.fx, w.fy, w.size));
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), sb.toString());
        } catch (IOException e) {
            System.out.println("[astar] couldn't save " + file() + ": " + e);
        }
    }
}
