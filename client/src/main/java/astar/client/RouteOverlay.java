package astar.client;

import astar.client.draw.GizmoRouteView;
import astar.client.draw.OverlayColour;
import astar.client.draw.RouteView;
import astar.client.draw.ShaderRouteView;
import astar.core.PathStep;
import astar.movement.exec.Journey;
import astar.pathing.PathAnalysis;
import astar.pathing.Tuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Draws the {@code .A*} route in the world while it's being walked: a line through every
 * node from start to goal, the block underfoot at each key node (a turn, or where a jump or
 * drop starts or ends) highlighted, and a small marker riding the line a few blocks ahead of
 * the player, in view in first person, so the player is seen following it. The line's corners
 * are rounded off so it reads as one smooth curve, and the marker sits exactly on it; where the
 * camera looks is up to the walking, not the marker.
 *
 * <p>On a trip with teleports, the rest of the trip too: the walks still to come, and each
 * teleport not yet cast as an arrow from where it's cast to where it lands (magenta for an
 * etherwarp, orange for an Instant Transmission, with a mark at each cast of an air chain),
 * the block it lands on and a numbered label.
 *
 * <p>Drawn once a frame from where the player is this frame, so the marker glides between game
 * ticks. Everything goes through a {@link RouteView}, in one of two {@link Style}s: a band on
 * the ground on the mod's own shaders ({@link ShaderRouteView}, the default) or the plain lines at eye height
 * ({@link GizmoRouteView}).
 */
final class RouteOverlay {

    private final Supplier<Navigator> navigator;
    /** How the route is drawn. */
    enum Style {
        RIBBON("Ribbon", "A soft band on the ground with arrows", ShaderRouteView.Look.RIBBON),
        FLOOR("Painted floor", "The blocks along the way painted, with step arrows",
                ShaderRouteView.Look.FLOOR),
        COMET("Comet", "Only the next few blocks, fading ahead", ShaderRouteView.Look.COMET),
        CRUMBS("Breadcrumbs", "Glowing dots with a pulse running to the goal",
                ShaderRouteView.Look.CRUMBS),
        TUBE("Light tube", "A glowing tube at waist height", ShaderRouteView.Look.TUBE),
        KEYS("Key nodes only", "Just the turns, jumps and drops, and where teleports land",
                ShaderRouteView.Look.KEYS),
        /** Thin lines at eye height, as the first overlay drew it. */
        LINES("Lines", "Thin lines and boxes at eye height", null),
        /** Nothing drawn; .A* still walks the route. */
        NONE("None", "Nothing drawn, the route is only walked", null);

        final String label;
        final String about;
        /** How the shader view draws it; null for the gizmo lines. */
        final ShaderRouteView.Look look;

        Style(String label, String about, ShaderRouteView.Look look) {
            this.label = label;
            this.about = about;
            this.look = look;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ShaderRouteView ribbon = new ShaderRouteView();
    private final RouteView lines = new GizmoRouteView();
    private RouteView view = ribbon;
    private Style style = Style.RIBBON;
    /** How far along {@link #line} the player is this frame, in blocks. */
    private double walked;
    /** The path the lists below were made from, so they're only rebuilt after a reroute. */
    private List<PathStep> drawn;
    private final List<Vec3> line = new ArrayList<>();
    /** How far along {@link #line} each of its points is, in blocks. */
    private double[] along = new double[0];
    /** The segment of {@link #line} the player was last nearest, where the next search starts. */
    private int near;
    /** The longest a segment of a line may be before its corners are rounded off, in blocks. */
    private static final double SMOOTH_STEP = 0.5;
    /** How many times each corner is cut: more is rounder. */
    private static final int SMOOTH_PASSES = 5;
    /** The cosine of the sharpest bend drawn between two pieces of a line (2 degrees). */
    private static final double BEND = Math.cos(Math.toRadians(2));
    private final List<AABB> keys = new ArrayList<>();
    private boolean shown = true;
    private OverlayColour colour = OverlayColour.CYAN;

    RouteOverlay(Supplier<Navigator> navigator) {
        this.navigator = navigator;
        try {
            OverlayColour saved = OverlayColour.named(Files.readString(colourFile()).strip());
            if (saved != null) {
                colour = saved;
            }
        } catch (IOException | RuntimeException e) {
            // None chosen yet.
        }
        try {
            style = Style.valueOf(Files.readString(styleFile()).strip().toUpperCase(Locale.ROOT));
        } catch (IOException | RuntimeException e) {
            // None chosen yet: the band.
        }
        useStyle();
        ribbon.colour(colour);
        lines.colour(colour);
    }

    private void useStyle() {
        view = style == Style.LINES ? lines : ribbon;
        if (style.look != null) {
            ribbon.look(style.look);
        }
    }

    private static Path styleFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar-overlay-style.txt");
    }

    Style style() {
        return style;
    }

    /** Draws the route this way from now on (and in later games), and shows it. */
    void style(Style style) {
        this.style = style;
        useStyle();
        shown = true;
        try {
            Files.writeString(styleFile(), style.id());
        } catch (IOException e) {
            // Kept for this game only.
        }
    }

    boolean shown() {
        return shown;
    }

    void shown(boolean shown) {
        this.shown = shown;
    }

    /** Where the chosen colour is kept between games. */
    private static Path colourFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar-overlay-colour.txt");
    }

    OverlayColour colour() {
        return colour;
    }

    /** Draws everything in this colour from now on (and in later games), and shows it. */
    void colour(OverlayColour colour) {
        this.colour = colour;
        ribbon.colour(colour);
        lines.colour(colour);
        shown = true;
        try {
            Files.writeString(colourFile(), colour.id());
        } catch (IOException e) {
            // Kept for this game only.
        }
    }

    void register() {
        LevelExtractionEvents.END_EXTRACTION.register(this::draw);
        // The shader view's quads go to the game when it gathers what to draw.
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
            if (view == ribbon) {
                ribbon.submit(ctx);
            }
        });
    }

    /** Turns the overlay on or off; returns whether it's now on. */
    boolean toggle() {
        shown = !shown;
        return shown;
    }

    private void draw(LevelExtractionContext ctx) {
        ribbon.begin();
        Navigator nav = navigator.get();
        Journey journey = nav == null ? null : nav.journey();
        Navigator.Trip trip = nav == null ? null : nav.trip();
        if (!shown || style == Style.NONE || journey == null && trip == null) {
            drawn = null;
            tripDrawn = null;
            return;
        }
        if (journey != null && journey.path() != drawn) {
            rebuild(journey, nav.origin());
        }
        if (trip == null) {
            tripDrawn = null;
        } else if (tripDrawn == null || trip.hops() != tripDrawn.hops()
                || trip.leg() != tripDrawn.leg()) {
            rebuildTrip(trip, nav.origin());
        }
        Vec3 lead = journey == null ? null
                : leadAt(ctx.deltaTracker().getGameTimeDeltaPartialTick(false));
        // Gizmos only land while a collector is open; this is the one drawn this frame.
        try (Gizmos.TemporaryCollection c = Minecraft.getInstance().levelRenderer
                .collectPerFrameRenderThreadGizmos()) {
            if (journey != null) {
                view.route(line, walked);
                keys.forEach(view::keyNode);
            }
            if (trip != null) {
                for (List<Vec3> w : walks) {
                    view.route(w, 0);
                }
                for (Label l : walkLabels) {
                    view.label(l.at(), l.text());
                }
                for (Hop h : teleports) {
                    view.teleport(h.points(), h.etherwarp());
                    h.casts().forEach(view::castPoint);
                    view.landing(h.landing(), h.etherwarp());
                    view.label(h.labelAt(), h.label());
                }
            }
            if (lead != null) {
                view.lead(lead);
            }
        }
    }

    /** A teleport of the trip, ready to draw. */
    private record Hop(List<Vec3> points, List<Vec3> casts, AABB landing, boolean etherwarp,
            Vec3 labelAt, String label) {}

    /** The trip the lists below were made from. */
    private Navigator.Trip tripDrawn;
    /** The walks still to come after the one under way, cell to cell. */
    private final List<List<Vec3>> walks = new ArrayList<>();
    /** The teleports still to come. */
    private final List<Hop> teleports = new ArrayList<>();
    /** Where each of those walks starts, numbered with the teleports. */
    private final List<Label> walkLabels = new ArrayList<>();

    private record Label(Vec3 at, String text) {}

    /**
     * The rest of a trip with teleports: every walk after the one under way (that one is drawn
     * as it's walked), and every teleport not yet cast. Walks and teleports are numbered
     * together in the order they're made, from the first of the trip.
     */
    private void rebuildTrip(Navigator.Trip trip, BlockPos o) {
        tripDrawn = trip;
        walks.clear();
        teleports.clear();
        walkLabels.clear();
        // Step numbers: leg k (if it walks at all), then hop k, and so on.
        int[] hopNumber = new int[trip.hops().size()];
        int number = 0;
        for (int k = 0; k < trip.legs().size(); k++) {
            Journey.Prepared l = trip.legs().get(k);
            List<Vec3> pts = new ArrayList<>();
            if (l != null) {
                for (PathStep s : l.path()) {
                    pts.add(new Vec3(s.pos().x() + 0.5 + o.getX(), s.pos().y() + o.getY(),
                            s.pos().z() + 0.5 + o.getZ()));
                }
            }
            // A step or two onto a cast spot isn't a walk of its own.
            double walked = length(pts);
            if (walked >= 1) {
                number++;
            }
            if (!pts.isEmpty() && k > trip.leg()) {
                walks.add(smooth(pts));
                if (walked >= 1) {
                    walkLabels.add(new Label(pts.get(0).add(0, 2.2, 0),
                            number + ". walk " + Math.round(walked) + " blocks"));
                }
            }
            if (k < hopNumber.length) {
                hopNumber[k] = ++number;
            }
        }
        Level level = Minecraft.getInstance().level;
        for (int k = trip.leg(); k < trip.hops().size(); k++) {
            Warps.Hop h = trip.hops().get(k);
            // Through the middle of the body, where the player is seen to go.
            List<Vec3> pts = new ArrayList<>();
            List<Vec3> casts = new ArrayList<>();
            pts.add(middle(h.from(), o));
            for (astar.core.BlockPoint b : h.through()) {
                Vec3 m = middle(b, o);
                pts.add(m);
                casts.add(m);
            }
            Vec3 end = middle(h.to(), o);
            if (!pts.get(pts.size() - 1).equals(end)) {
                pts.add(end);
            }
            AABB floor = floorUnder(level, new BlockPos(h.to().x() + o.getX(),
                    h.to().y() + o.getY(), h.to().z() + o.getZ()));
            String what = !h.transmit() ? "etherwarp"
                    : h.chain() > 1 ? "IT x" + h.chain() + " chained" : "IT";
            teleports.add(new Hop(pts, casts, floor, !h.transmit(), pts.get(0).add(0, 1.2, 0),
                    hopNumber[k] + ". " + what));
        }
    }

    /** How far a walk goes across the ground, in blocks. */
    private static double length(List<Vec3> pts) {
        double d = 0;
        for (int i = 1; i < pts.size(); i++) {
            d += Math.hypot(pts.get(i).x - pts.get(i - 1).x, pts.get(i).z - pts.get(i - 1).z);
        }
        return d;
    }

    private static Vec3 middle(astar.core.BlockPoint b, BlockPos o) {
        return new Vec3(b.x() + 0.5 + o.getX(), b.y() + 1.0 + o.getY(), b.z() + 0.5 + o.getZ());
    }

    /**
     * The box of the block the player stands on in a path cell: the cell's own block when it's
     * a slab, stair or other block at least half high that the feet stand in, else the block
     * below (so a carpet counts as the block it lies on).
     */
    private static AABB floorUnder(Level level, BlockPos cell) {
        if (level != null) {
            VoxelShape own = level.getBlockState(cell).getCollisionShape(level, cell);
            if (!own.isEmpty() && own.max(Direction.Axis.Y) >= 0.5) {
                return own.bounds().move(cell);
            }
            BlockPos below = cell.below();
            VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
            if (!under.isEmpty()) {
                return under.bounds().move(below);
            }
        }
        return new AABB(cell.below());
    }

    private void rebuild(Journey journey, BlockPos o) {
        drawn = journey.path();
        line.clear();
        // As it's walked: around each rounded corner along its curve.
        List<Vec3> track = new ArrayList<>();
        for (double[] p : journey.plan().track()) {
            track.add(new Vec3(p[0] + o.getX(), p[1] + o.getY(), p[2] + o.getZ()));
        }
        line.addAll(smooth(track));
        along = new double[line.size()];
        for (int i = 1; i < line.size(); i++) {
            along[i] = along[i - 1] + line.get(i).distanceTo(line.get(i - 1));
        }
        near = 0;
        keys.clear();
        Level level = Minecraft.getInstance().level;
        for (PathStep s : PathAnalysis.keyNodes(drawn)) {
            keys.add(floorUnder(level, new BlockPos(s.pos().x() + o.getX(),
                    s.pos().y() + o.getY(), s.pos().z() + o.getZ())));
        }
    }

    /**
     * The point on the line {@link Tuning#LEAD_AHEAD} blocks further along than the point
     * nearest the player this frame; null with no player or no line.
     */
    private Vec3 leadAt(float partialTick) {
        var player = Minecraft.getInstance().player;
        walked = 0;
        if (player == null || line.size() < 2) {
            return null;
        }
        Vec3 at = player.getPosition(partialTick);
        // Near where it was last frame: a later stretch of the line that passes close by (a
        // switchback, or the same spot a floor below) doesn't steal the marker.
        int from = Math.max(0, near - 4);
        int to = Math.min(line.size() - 2, near + 60);
        int best = near;
        double bestD = Double.MAX_VALUE;
        double bestT = 0;
        for (int i = from; i <= to; i++) {
            Vec3 a = line.get(i);
            Vec3 ab = line.get(i + 1).subtract(a);
            double len2 = ab.lengthSqr();
            double t = len2 == 0 ? 0 : Math.clamp(at.subtract(a).dot(ab) / len2, 0, 1);
            double d = a.add(ab.scale(t)).distanceToSqr(at);
            if (d < bestD) {
                bestD = d;
                best = i;
                bestT = t;
            }
        }
        // Far off it (knocked away, or the search lost it): look along the whole line.
        if (bestD > 9) {
            for (int i = 0; i < line.size() - 1; i++) {
                Vec3 a = line.get(i);
                Vec3 ab = line.get(i + 1).subtract(a);
                double len2 = ab.lengthSqr();
                double t = len2 == 0 ? 0 : Math.clamp(at.subtract(a).dot(ab) / len2, 0, 1);
                double d = a.add(ab.scale(t)).distanceToSqr(at);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                    bestT = t;
                }
            }
        }
        near = best;
        walked = along[best] + bestT * (along[best + 1] - along[best]);
        double s = walked + Tuning.LEAD_AHEAD.get();
        if (s >= along[along.length - 1]) {
            return line.get(line.size() - 1);
        }
        int i = best;
        while (along[i + 1] < s) {
            i++;
        }
        double span = along[i + 1] - along[i];
        return line.get(i).lerp(line.get(i + 1), span == 0 ? 0 : (s - along[i]) / span);
    }

    /**
     * The line with its corners rounded off: cut into pieces no longer than
     * {@link #SMOOTH_STEP}, then each corner cut {@link #SMOOTH_PASSES} times (Chaikin), so a
     * corner bends over about a block instead of in one point. The ends stay where they are.
     */
    private static List<Vec3> smooth(List<Vec3> pts) {
        if (pts.size() < 3) {
            return pts;
        }
        List<Vec3> fine = new ArrayList<>();
        fine.add(pts.get(0));
        for (int i = 1; i < pts.size(); i++) {
            Vec3 a = pts.get(i - 1);
            Vec3 b = pts.get(i);
            int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / SMOOTH_STEP));
            for (int k = 1; k <= n; k++) {
                fine.add(a.lerp(b, (double) k / n));
            }
        }
        for (int pass = 0; pass < SMOOTH_PASSES; pass++) {
            List<Vec3> cut = new ArrayList<>(fine.size() * 2);
            cut.add(fine.get(0));
            for (int i = 0; i < fine.size() - 1; i++) {
                Vec3 a = fine.get(i);
                Vec3 b = fine.get(i + 1);
                if (i > 0) {
                    cut.add(a.lerp(b, 0.25));
                }
                if (i < fine.size() - 2) {
                    cut.add(a.lerp(b, 0.75));
                }
            }
            cut.add(fine.get(fine.size() - 1));
            fine = cut;
        }
        // Straight runs need only their ends: fewer lines to draw each frame. A point is kept
        // once the line bends more than BEND past it, so no drawn corner is sharper than that.
        List<Vec3> kept = new ArrayList<>();
        kept.add(fine.get(0));
        for (int i = 1; i < fine.size() - 1; i++) {
            Vec3 u = fine.get(i).subtract(kept.get(kept.size() - 1));
            Vec3 v = fine.get(i + 1).subtract(fine.get(i));
            if (u.dot(v) < BEND * Math.sqrt(u.lengthSqr() * v.lengthSqr())) {
                kept.add(fine.get(i));
            }
        }
        kept.add(fine.get(fine.size() - 1));
        return kept;
    }
}
