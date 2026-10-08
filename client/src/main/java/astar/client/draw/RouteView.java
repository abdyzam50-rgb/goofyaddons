package astar.client.draw;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the route overlay can draw, in world coordinates. The overlay only talks to this, so
 * the way it's drawn ({@link ShaderRouteView} or the plain {@link GizmoRouteView} lines) can
 * change in one place.
 */
public interface RouteView {

    /** The one colour everything is drawn in, from now on. */
    void colour(OverlayColour colour);

    /**
     * The route through the points, in order, at the height of the feet. {@code walked} is how
     * far along it the player already is, in blocks (0 for a walk still to come): a view may
     * leave that part out.
     */
    void route(List<Vec3> points, double walked);

    /** Highlights the block the player stands on at a key node, given as its box. */
    void keyNode(AABB floor);

    /**
     * The small marker the player follows, on the route a few blocks ahead of them, given at
     * the height of the feet.
     */
    void lead(Vec3 at);

    /**
     * A teleport, as a line through the points (from where it's cast, through each block a
     * cast puts the player in, to where they land) with an arrow on the last stretch: an
     * etherwarp or an Instant Transmission, in the route's colour.
     */
    void teleport(List<Vec3> points, boolean etherwarp);

    /** A small mark where one cast of an Instant Transmission puts the player. */
    void castPoint(Vec3 at);

    /** Highlights the block a teleport lands on, given as its box. */
    void landing(AABB floor, boolean etherwarp);

    /** A short text label floating at a point. */
    void label(Vec3 at, String text);
}
