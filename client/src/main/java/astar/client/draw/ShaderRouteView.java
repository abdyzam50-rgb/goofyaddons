package astar.client.draw;

import astar.client.ui.Theme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The route drawn on the mod's own shaders ({@link RouteShaders}): a ribbon lying just over the
 * ground from the player to the goal, with smooth edges, darker borders and arrows gliding
 * along it the way to go, and a faint copy of it through walls nearby; a glowing marker
 * floating over it a few blocks ahead to walk towards, with its ring on the ground; the top of
 * each key node's block outlined with rounded corners; and teleports as beams facing the
 * camera, seen through walls, with an arrowhead where they land.
 *
 * <p>The overlay calls the {@link RouteView} methods once a frame, after {@link #begin}; they
 * only collect quads, which {@link #submit} hands to the game when it gathers what to draw.
 * Labels still go through the game's gizmos, so those calls need a gizmo collector open.
 */
public final class ShaderRouteView implements RouteView {

    /** How far over the floor the ribbon lies, in blocks: clear of carpets and snow. */
    private static final double LIFT = 0.07;
    /** Half the ribbon's width, in blocks. */
    private static final double HALF_WIDTH = 0.3;
    /** How far behind the player the ribbon still shows, in blocks: under their feet. */
    private static final double BEHIND = 0.6;
    /** How fast the arrows glide along the ribbon, in blocks a second. */
    private static final double ARROW_SPEED = 2.2;
    /** Blocks between the arrows, as in route.fsh. */
    private static final double ARROW_GAP = 1.6;
    /** How far through walls the ribbon shows, in blocks, and how strongly (alpha, 0-255). */
    private static final double XRAY_RANGE = 48;
    private static final int XRAY_ALPHA = 0x48;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** A quad: four corners in world coordinates, each with its shape coordinates. */
    private record Quad(Vec3[] at, float[] uv, int colour) {}

    /** How the route itself is drawn; the markers, block tops and teleports stay the same. */
    public enum Look {
        /** A ribbon on the ground with arrows gliding along it. */
        RIBBON,
        /** The tops of the blocks walked over, painted, with arrows at steps up and down. */
        FLOOR,
        /** The ribbon only for the next few blocks, fading out ahead. */
        COMET,
        /** Glowing dots along the way, with a pulse running through them to the goal. */
        CRUMBS,
        /** A thin glowing tube at waist height, with light running along it. */
        TUBE,
        /** No route at all: only the key nodes' blocks and where teleports land. */
        KEYS
    }

    /** How far ahead the comet reaches, in blocks. */
    private static final double COMET_LENGTH = 14;
    /** Blocks between breadcrumbs. */
    private static final double CRUMB_GAP = 0.9;
    /** How high the light tube floats over the feet, and half its width, in blocks. */
    private static final double TUBE_HEIGHT = 0.95;
    private static final double TUBE_HALF = 0.09;

    private Look look = Look.RIBBON;
    private OverlayColour colour = OverlayColour.CYAN;
    private List<Quad> world = new ArrayList<>();
    private List<Quad> xray = new ArrayList<>();

    /** The colours drawn this frame: the route colour's, or partway there from the last. */
    private int lineNow;
    private int leadNow;
    private int fromLine;
    private int fromLead;
    private long fadeStart;

    @Override
    public void colour(OverlayColour colour) {
        boolean first = lineNow == 0;
        fromLine = first ? colour.lineNow() : lineNow;
        fromLead = first ? colour.leadNow() : leadNow;
        fadeStart = System.currentTimeMillis();
        this.colour = colour;
        fade();
    }

    /** Moves the colours along the fade to the route colour, as the window's theme fades. */
    private void fade() {
        double t = Math.clamp((System.currentTimeMillis() - fadeStart)
                / (double) Theme.FADE_MS, 0, 1);
        t = t * t * (3 - 2 * t);
        lineNow = Theme.argb(fromLine, colour.lineNow(), t);
        leadNow = Theme.argb(fromLead, colour.leadNow(), t);
    }

    public void look(Look look) {
        this.look = look;
    }

    /** Starts a new frame: forgets the last one's quads. */
    public void begin() {
        fade();
        // New lists, not cleared ones: the last frame's may still be waiting to be drawn.
        world = new ArrayList<>();
        xray = new ArrayList<>();
    }

    /** Hands this frame's quads to the game; call from {@code LevelRenderEvents.COLLECT_SUBMITS}. */
    public void submit(LevelRenderContext ctx) {
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        PoseStack pose = ctx.poseStack();
        List<Quad> w = world;
        List<Quad> x = xray;
        if (!w.isEmpty()) {
            ctx.submitNodeCollector().submitCustomGeometry(pose, RouteShaders.WORLD,
                    (p, buf) -> emit(w, p, buf, cam));
        }
        if (!x.isEmpty()) {
            ctx.submitNodeCollector().submitCustomGeometry(pose, RouteShaders.XRAY,
                    (p, buf) -> emit(x, p, buf, cam));
        }
    }

    private static void emit(List<Quad> quads, PoseStack.Pose pose, VertexConsumer buf,
            Vec3 cam) {
        for (Quad q : quads) {
            for (int k = 0; k < 4; k++) {
                Vec3 v = q.at[k];
                buf.addVertex(pose, (float) (v.x - cam.x), (float) (v.y - cam.y),
                        (float) (v.z - cam.z)).setUv(q.uv[2 * k], q.uv[2 * k + 1])
                        .setColor(q.colour);
            }
        }
    }

    @Override
    public void route(List<Vec3> points, double walked) {
        switch (look) {
            case RIBBON -> ribbon(points, walked, Double.MAX_VALUE);
            case COMET -> ribbon(points, walked, COMET_LENGTH);
            case FLOOR -> floor(points, walked);
            case CRUMBS -> crumbs(points, walked);
            case TUBE -> tube(points, walked);
            case KEYS -> { }
        }
    }

    /**
     * The ribbon, from just behind the player for {@code reach} blocks ahead of them; when that
     * is short (the comet), it fades out towards its far end.
     */
    private void ribbon(List<Vec3> points, double walked, double reach) {
        boolean comet = reach < Double.MAX_VALUE;
        List<Vec3> pts = from(points, Math.max(0, walked - BEHIND));
        if (comet) {
            pts = upTo(pts, reach + BEHIND, 0.5);
        }
        if (pts.size() < 2) {
            return;
        }
        List<Vec3> sides = sides(pts);
        List<Vec3> band = new ArrayList<>(pts.size());
        for (Vec3 p : pts) {
            band.add(p.add(0, LIFT, 0));
        }
        // Along the ribbon in blocks, moved back by the time so the arrows glide forward.
        // (Kept within one gap of the arrows, the shader's ARROW_GAP, so floats stay exact.)
        double phase = (seconds() * ARROW_SPEED) % ARROW_GAP;
        double along = walked - phase;
        Vec3 eye = camera();
        int ghost = ARGB.color(XRAY_ALPHA, lineNow);
        for (int i = 1; i < band.size(); i++) {
            Vec3 p = band.get(i - 1);
            Vec3 q = band.get(i);
            Vec3 sp = sides.get(i - 1).scale(HALF_WIDTH);
            Vec3 sq = sides.get(i).scale(HALF_WIDTH);
            double next = along + p.distanceTo(q);
            Vec3[] at = {p.subtract(sp), q.subtract(sq), q.add(sq), p.add(sp)};
            float[] uv = {-1, (float) along, -1, (float) next, 1, (float) next, 1, (float) along};
            int c = lineNow;
            if (comet) {
                // Bright at the player, gone at the far end.
                double f = 1 - Math.clamp((along + phase - walked - BEHIND) / reach, 0, 1);
                c = ARGB.color((int) Math.round(255 * Math.pow(f, 0.8)), lineNow);
                ghost = ARGB.color((int) Math.round(XRAY_ALPHA * f), lineNow);
            }
            world.add(new Quad(at, uv, c));
            // Faintly through walls, near enough to matter: round a corner, under a floor.
            if (eye == null || p.distanceTo(eye) <= XRAY_RANGE) {
                xray.add(new Quad(at, uv, ghost));
            }
            along = next;
        }
    }

    /**
     * The blocks walked over, each painted on its top, as high as the feet are there (so slabs
     * and stairs are painted where they're stood on), with a small arrow on each pointing the
     * way to go; an arrow standing up where the way climbs a block and one pointing down where
     * it drops.
     */
    private void floor(List<Vec3> points, double walked) {
        List<Vec3> pts = from(points, Math.max(0, walked - BEHIND));
        if (pts.size() < 2) {
            return;
        }
        Vec3 eye = camera();
        int fill = lineNow;
        int ghost = ARGB.color(XRAY_ALPHA, lineNow);
        // Each block once, where the route first reaches it, with the way it goes there.
        java.util.Map<Long, Vec3[]> cells = new java.util.LinkedHashMap<>();
        for (int i = 1; i < pts.size(); i++) {
            Vec3 p = pts.get(i - 1);
            Vec3 q = pts.get(i);
            double len = p.distanceTo(q);
            Vec3 dir = new Vec3(q.x - p.x, 0, q.z - p.z);
            for (double d = 0; d < len; d += 0.25) {
                Vec3 at = p.lerp(q, d / len);
                int bx = (int) Math.floor(at.x);
                int bz = (int) Math.floor(at.z);
                int by = (int) Math.floor(at.y - 0.05);
                long key = ((long) bx & 0x3FFFFFF) << 38 | ((long) bz & 0x3FFFFFF) << 12
                        | (by & 0xFFF);
                Vec3[] cell = cells.get(key);
                if (cell == null) {
                    cells.put(key, new Vec3[] {at, dir});
                } else if (at.y > cell[0].y) {
                    cell[0] = at;
                }
            }
        }
        Vec3 before = null;
        for (Vec3[] cell : cells.values()) {
            Vec3 at = cell[0];
            Vec3 dir = cell[1];
            double x0 = Math.floor(at.x);
            double z0 = Math.floor(at.z);
            double y = at.y + 0.012;
            Vec3[] tile = {new Vec3(x0 + 0.03, y, z0 + 0.03), new Vec3(x0 + 0.97, y, z0 + 0.03),
                    new Vec3(x0 + 0.97, y, z0 + 0.97), new Vec3(x0 + 0.03, y, z0 + 0.97)};
            world.add(new Quad(tile, shapeUv(4), fill));
            if (eye == null || at.distanceTo(eye) <= XRAY_RANGE) {
                xray.add(new Quad(tile, shapeUv(4), ghost));
            }
            // The arrow, lying on the tile and turned the way to go.
            Vec3 mid = new Vec3(x0 + 0.5, y + 0.006, z0 + 0.5);
            if (dir.lengthSqr() > 1e-6) {
                Vec3 f = dir.normalize().scale(0.3);
                Vec3 r = f.cross(UP);
                world.add(new Quad(new Vec3[] {mid.subtract(f).subtract(r),
                        mid.subtract(f).add(r), mid.add(f).add(r), mid.add(f).subtract(r)},
                        new float[] {9, 59, 11, 59, 11, 61, 9, 61}, ARGB.color(0xE0,
                                leadNow)));
            }
            // A step up or down from the block before.
            if (before != null) {
                double rise = at.y - before.y;
                if (Math.abs(rise) >= 0.6) {
                    Vec3 high = rise > 0 ? new Vec3(x0 + 0.5, at.y + 0.55, z0 + 0.5)
                            : new Vec3(Math.floor(before.x) + 0.5, before.y + 0.55,
                                    Math.floor(before.z) + 0.5);
                    Quad arrow = facing(high, eye, 0.22, 3, leadNow);
                    if (rise < 0) {
                        // Pointing down: the shape upside down.
                        arrow = new Quad(arrow.at, new float[] {9, 31, 11, 31, 11, 29, 9, 29},
                                arrow.colour);
                    }
                    xray.add(arrow);
                }
            }
            before = at;
        }
    }

    /**
     * Glowing dots along the way, floating a little over the ground, and a pulse that runs
     * through them from the player to the goal, lighting and swelling each in turn.
     */
    private void crumbs(List<Vec3> points, double walked) {
        List<Vec3> pts = from(points, walked);
        if (pts.size() < 2) {
            return;
        }
        Vec3 eye = camera();
        double t = seconds();
        // Starts the dots at whole gaps along the route, so they stay put as the player walks.
        double first = CRUMB_GAP - (walked % CRUMB_GAP);
        double along = 0;
        double next = first;
        for (int i = 1; i < pts.size(); i++) {
            Vec3 p = pts.get(i - 1);
            Vec3 q = pts.get(i);
            double len = p.distanceTo(q);
            while (next <= along + len) {
                Vec3 at = p.lerp(q, (next - along) / len).add(0, 0.22, 0);
                // The pulse: 10 blocks apart, 7 blocks a second.
                double w = ((next - t * 7) % 10 + 10) % 10;
                double pulse = Math.exp(-w * w * 0.6);
                int c = ARGB.color((int) Math.round(150 + 105 * pulse),
                        mix(lineNow, leadNow, pulse));
                Quad dot = facing(at, eye, 0.1 + 0.08 * pulse, 5, c);
                world.add(dot);
                if (eye == null || at.distanceTo(eye) <= XRAY_RANGE) {
                    xray.add(new Quad(dot.at, dot.uv, ARGB.color(XRAY_ALPHA, lineNow)));
                }
                next += CRUMB_GAP;
            }
            along += len;
        }
    }

    /**
     * A thin tube of light at waist height, turned to the camera, glowing at its core, with
     * light running along it towards the goal; a faint copy shows through walls nearby.
     */
    private void tube(List<Vec3> points, double walked) {
        List<Vec3> pts = from(points, walked + 0.8);
        if (pts.size() < 2) {
            return;
        }
        Vec3 eye = camera();
        // Kept within one 8-block period, as in route.fsh, so floats stay exact.
        double along = -((seconds() * 6) % 8);
        int ghost = ARGB.color(XRAY_ALPHA + 0x20, lineNow);
        for (int i = 1; i < pts.size(); i++) {
            Vec3 p = pts.get(i - 1).add(0, TUBE_HEIGHT, 0);
            Vec3 q = pts.get(i).add(0, TUBE_HEIGHT, 0);
            Vec3 side = across(p, q, eye).scale(TUBE_HALF);
            double next = along + p.distanceTo(q);
            Vec3[] at = {p.subtract(side), q.subtract(side), q.add(side), p.add(side)};
            float[] uv = {19, (float) along, 19, (float) next, 21, (float) next, 21,
                    (float) along};
            world.add(new Quad(at, uv, lineNow));
            if (eye == null || p.distanceTo(eye) <= XRAY_RANGE) {
                xray.add(new Quad(at, uv, ghost));
            }
            along = next;
        }
    }

    @Override
    public void keyNode(AABB floor) {
        world.add(blockTop(floor, lineNow));
    }

    @Override
    public void lead(Vec3 at) {
        if (look == Look.KEYS) {
            return;
        }
        double t = seconds();
        Vec3 eye = camera();
        Vec3 ground = at.add(0, LIFT + 0.01, 0);
        world.add(flat(ground, 0.34, 1, ARGB.color(0xB0, leadNow)));
        Vec3 high = at.add(0, 0.6 + 0.05 * Math.sin(t * 2.4), 0);
        world.add(facing(high, eye, 0.26, 1, leadNow));
    }

    @Override
    public void teleport(List<Vec3> points, boolean etherwarp) {
        if (points.size() < 2 || look == Look.KEYS) {
            return;
        }
        Vec3 eye = camera();
        Vec3 last = points.get(points.size() - 2);
        Vec3 end = points.get(points.size() - 1);
        // The beam stops where the arrowhead starts.
        Vec3 dir = end.subtract(last);
        double head = Math.min(0.55, dir.length() * 0.5);
        Vec3 base = end.subtract(dir.normalize().scale(head));
        List<Vec3> line = new ArrayList<>(points);
        line.set(line.size() - 1, base);
        int c = ARGB.color(0xE0, lineNow);
        double along = -((seconds() * ARROW_SPEED * 2) % ARROW_GAP);
        for (int i = 1; i < line.size(); i++) {
            Vec3 p = line.get(i - 1);
            Vec3 q = line.get(i);
            Vec3 side = across(p, q, eye).scale(0.11);
            double next = along + p.distanceTo(q);
            xray.add(new Quad(new Vec3[] {p.subtract(side), q.subtract(side), q.add(side),
                    p.add(side)},
                    new float[] {-1, (float) along, -1, (float) next, 1, (float) next, 1,
                            (float) along}, c));
            along = next;
        }
        Vec3 side = across(base, end, eye).scale(0.3);
        xray.add(new Quad(new Vec3[] {base.subtract(side), end.subtract(side), end.add(side),
                base.add(side)}, new float[] {9, 29, 9, 31, 11, 31, 11, 29}, c));
    }

    @Override
    public void castPoint(Vec3 at) {
        if (look == Look.KEYS) {
            return;
        }
        xray.add(facing(at, camera(), 0.2, 1, ARGB.color(0xE0, lineNow)));
    }

    @Override
    public void landing(AABB floor, boolean etherwarp) {
        world.add(blockTop(floor, leadNow));
    }

    @Override
    public void label(Vec3 at, String text) {
        Gizmos.billboardText(text, at, TextGizmo.Style.whiteAndCentered());
    }

    /** The top of a block, outlined: a flat square a hair over it, a little bigger. */
    private static Quad blockTop(AABB floor, int colour) {
        double y = floor.maxY + 0.005;
        double g = 0.02;
        Vec3[] at = {new Vec3(floor.minX - g, y, floor.minZ - g),
                new Vec3(floor.maxX + g, y, floor.minZ - g),
                new Vec3(floor.maxX + g, y, floor.maxZ + g),
                new Vec3(floor.minX - g, y, floor.maxZ + g)};
        return new Quad(at, shapeUv(2), colour);
    }

    /** A shape of this kind lying flat, {@code half} blocks from its middle to each side. */
    private static Quad flat(Vec3 c, double half, int kind, int colour) {
        Vec3[] at = {c.add(-half, 0, -half), c.add(half, 0, -half), c.add(half, 0, half),
                c.add(-half, 0, half)};
        return new Quad(at, shapeUv(kind), colour);
    }

    /** A shape of this kind standing up, turned to face the camera. */
    private static Quad facing(Vec3 c, Vec3 eye, double half, int kind, int colour) {
        Vec3 view = eye == null ? new Vec3(0, 0, 1) : c.subtract(eye);
        Vec3 right = view.cross(UP);
        right = right.lengthSqr() < 1e-9 ? new Vec3(half, 0, 0) : right.normalize().scale(half);
        Vec3 up = right.cross(view);
        up = up.lengthSqr() < 1e-9 ? new Vec3(0, half, 0) : up.normalize().scale(half);
        Vec3[] at = {c.subtract(right).subtract(up), c.add(right).subtract(up),
                c.add(right).add(up), c.subtract(right).add(up)};
        return new Quad(at, shapeUv(kind), colour);
    }

    /** The shape coordinates of a whole shape of this kind, corner by corner (route.fsh). */
    private static float[] shapeUv(int kind) {
        float y = 10 * kind;
        return new float[] {9, y - 1, 11, y - 1, 11, y + 1, 9, y + 1};
    }

    /**
     * The line's first {@code length} blocks, cut into pieces at most {@code step} long so a
     * fade along it is smooth.
     */
    private static List<Vec3> upTo(List<Vec3> pts, double length, double step) {
        List<Vec3> out = new ArrayList<>();
        if (pts.isEmpty()) {
            return out;
        }
        out.add(pts.get(0));
        double s = 0;
        for (int i = 1; i < pts.size() && s < length; i++) {
            Vec3 p = pts.get(i - 1);
            Vec3 q = pts.get(i);
            double len = p.distanceTo(q);
            int pieces = Math.max(1, (int) Math.ceil(len / step));
            for (int k = 1; k <= pieces; k++) {
                double d = len * k / pieces;
                if (s + d > length) {
                    out.add(p.lerp(q, (length - s) / len));
                    return out;
                }
                out.add(p.lerp(q, d / len));
            }
            s += len;
        }
        return out;
    }

    /** {@code a} towards {@code b} by {@code t}, as RGB. */
    private static int mix(int a, int b, double t) {
        return ARGB.color(255, (int) Math.round(ARGB.red(a) + (ARGB.red(b) - ARGB.red(a)) * t),
                (int) Math.round(ARGB.green(a) + (ARGB.green(b) - ARGB.green(a)) * t),
                (int) Math.round(ARGB.blue(a) + (ARGB.blue(b) - ARGB.blue(a)) * t));
    }

    /** The part of the line from {@code start} blocks along it to its end. */
    private static List<Vec3> from(List<Vec3> pts, double start) {
        if (start <= 0) {
            return pts;
        }
        double s = 0;
        for (int i = 1; i < pts.size(); i++) {
            double len = pts.get(i).distanceTo(pts.get(i - 1));
            if (s + len > start) {
                List<Vec3> rest = new ArrayList<>(pts.size() - i + 1);
                rest.add(pts.get(i - 1).lerp(pts.get(i), (start - s) / len));
                rest.addAll(pts.subList(i, pts.size()));
                return rest;
            }
            s += len;
        }
        return List.of();
    }

    /**
     * Which way is sideways at each point, across the ground: square to the line's direction
     * there, averaged over the pieces on either side and lengthened through a bend (up to
     * twice), so the ribbon keeps its width round corners. Where the line goes straight down
     * (a drop) it keeps the sideways it had.
     */
    private static List<Vec3> sides(List<Vec3> pts) {
        int n = pts.size();
        Vec3[] across = new Vec3[n - 1];
        for (int i = 0; i < n - 1; i++) {
            Vec3 d = pts.get(i + 1).subtract(pts.get(i));
            Vec3 flat = new Vec3(d.x, 0, d.z);
            across[i] = flat.lengthSqr() < 1e-6 ? null : flat.cross(UP).normalize();
        }
        Vec3 last = null;
        for (int i = 0; i < n - 1; i++) {
            if (across[i] != null) {
                last = across[i];
            } else if (last != null) {
                across[i] = last;
            }
        }
        for (int i = n - 2; i >= 0; i--) {
            if (across[i] != null) {
                last = across[i];
            } else {
                across[i] = last != null ? last : new Vec3(1, 0, 0);
            }
        }
        List<Vec3> sides = new ArrayList<>(n);
        sides.add(across[0]);
        for (int i = 1; i < n - 1; i++) {
            Vec3 mid = across[i - 1].add(across[i]);
            if (mid.lengthSqr() < 1e-6) {
                sides.add(across[i]);
                continue;
            }
            mid = mid.normalize();
            sides.add(mid.scale(1 / Math.max(0.5, mid.dot(across[i]))));
        }
        sides.add(across[n - 2]);
        return sides;
    }

    /** Sideways to the line from p to q as the camera sees it, of unit length. */
    private static Vec3 across(Vec3 p, Vec3 q, Vec3 eye) {
        Vec3 dir = q.subtract(p);
        Vec3 view = eye == null ? UP : p.lerp(q, 0.5).subtract(eye);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1e-9) {
            side = dir.cross(UP);
        }
        return side.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : side.normalize();
    }

    private static Vec3 camera() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameRenderer == null ? null : mc.gameRenderer.mainCamera().position();
    }

    private static double seconds() {
        return System.nanoTime() / 1e9;
    }
}
