package astar.movement.exec;

import astar.core.DefaultCostModel;
import astar.pathing.ArrayBlockView;
import astar.pathing.BlockView;
import astar.pathing.EntityProfile;
import astar.pathing.TerrainCosts;
import astar.pathing.Tuning;
import astar.pathing.WorldPathfinder;

/** How .A* searches: the pathfinder the mod plans with, so the route tool can time the same. */
public final class GotoPathfinder {

    /**
     * The costs .A* plans with, from the player's settings ({@link Tuning}). By default
     * climbing and swimming cost 20 a block, well above walking, so routes go around ladders
     * and water, which the executor can't do yet, unless there's no other way; and a drop costs
     * 2 plus 1 per block fallen, twice the library's, so routes walk down stairs and slopes
     * rather than stepping off ledges unless going round is much longer (on 200 Mines routes
     * that halves the one-block drops for 1% more walking).
     */
    public static DefaultCostModel costs() {
        return new DefaultCostModel(Tuning.WALK.get(), Tuning.DIAGONAL.get(), Tuning.JUMP.get(),
                Tuning.DROP.get(), Tuning.DROP_PER_BLOCK.get(),
                DefaultCostModel.DEFAULT.stepInPlace(), Tuning.SWIM.get(), Tuning.CLIMB.get());
    }

    private GotoPathfinder() {}

    /**
     * Doors stay shut (adventure mode can't open them), so routes go around them. 16 headings
     * and a small cost per turn ({@link Tuning#TURN}), so open ground is crossed in fewer, gentler turns. Jumps and
     * drops go diagonally too. Steps along walls and ledges cost a little more ({@link
     * Tuning#WALL}), so routes keep to the middle of the way, as a player walks.
     */
    public static WorldPathfinder create(ArrayBlockView view) {
        DefaultCostModel costs = costs();
        return new WorldPathfinder(view,
                EntityProfile.DEFAULT.withOpensDoors(false), 16, costs,
                TerrainCosts.DEFAULT.withWall(Tuning.WALL.get()), costs.heuristic(16),
                Tuning.TURN.get());
    }

    /**
     * The same rules for a rough route across a whole map (saved chunks, say): 8 headings and
     * no turn cost, as it only guides where the next stretch goes, and a map-sized view has no
     * move graph to make 16 headings cheap.
     */
    public static WorldPathfinder across(BlockView view) {
        DefaultCostModel costs = costs();
        return new WorldPathfinder(view, EntityProfile.DEFAULT.withOpensDoors(false), 8, costs,
                TerrainCosts.DEFAULT.withWall(Tuning.WALL.get()), costs.heuristic(8), 0);
    }
}
