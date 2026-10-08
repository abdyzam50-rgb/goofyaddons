package astar.client;

import astar.core.AStarSearch;
import astar.core.BlockPoint;
import astar.core.MoveGraph;
import astar.core.MoveType;
import astar.core.PathStep;
import astar.core.SearchListener;
import astar.core.SearchResult;
import astar.movement.exec.GotoPathfinder;
import astar.pathing.ArrayBlockView;
import astar.pathing.Flights;
import astar.pathing.HopGraph;
import astar.pathing.NavGraph;
import astar.pathing.TransmitHops;
import astar.pathing.Tuning;
import astar.pathing.WarpHops;
import astar.pathing.WorldPathfinder;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes that teleport as well as walk, for {@code .A* warp}, {@code .A* it} and {@code
 * .A* aotv}: the sure etherwarp hops ({@link WarpHops}) and Instant Transmission casts
 * ({@link TransmitHops}) of the map, worked out once over the whole map's move graph and kept,
 * and a search over walking and those together. Plain Java, run off the game thread.
 */
final class Warps {
    private Warps() {}

    /** Which teleports routes use. */
    enum Mode {
        /** Etherwarp ({@code .A* warp}). */
        ETHER(true, false, "etherwarps"),
        /** Instant Transmission ({@code .A* it}). */
        TRANSMIT(false, true, "transmissions"),
        /** Both ({@code .A* aotv}). */
        BOTH(true, true, "teleports");

        final boolean ether;
        final boolean transmit;
        final String plural;

        Mode(boolean ether, boolean transmit, String plural) {
            this.ether = ether;
            this.transmit = transmit;
            this.plural = plural;
        }
    }

    /**
     * One hop of a route: from where it's cast, to where it lands. An etherwarp aims at a point
     * (aimX, aimY, aimZ); an Instant Transmission ({@code transmit}) turns the view to (yaw,
     * pitch) and lands in the air above {@code to} or on it; {@code chain} casts in a row at
     * that view, each from where the last left the player in the air (1 for a single cast).
     * {@code through} is the block each cast puts the feet in, for drawing (empty for an
     * etherwarp). {@code yaws} and {@code pitches} are each cast's view: the same for a chain at
     * one view, different for an air line ({@link Flights}), which turns between casts.
     */
    record Hop(BlockPoint from, BlockPoint to, boolean transmit, double aimX, double aimY,
            double aimZ, float yaw, float pitch, int chain, List<BlockPoint> through,
            float[] yaws, float[] pitches) {}

    /**
     * A route split where it hops: {@code legs.get(i)} is walked, then {@code hops.get(i)} is
     * cast (there's one leg more than hops). A leg of one step is no walk at all: the hop
     * before it lands where the next one is cast.
     */
    record Route(List<List<PathStep>> legs, List<Hop> hops, SearchResult result, double buildMs) {}

    private static WarpHops kept;
    private static ArrayBlockView keptWorld;
    /** Hops of {@link #kept} that failed in the game, left out of routes from then on. */
    private static final java.util.Set<Integer> banned = new java.util.HashSet<>();
    /**
     * Hops from a trip's start or onto its goal ({@link WarpHops#ends}) that failed in the
     * game, by their ends (packed).
     */
    private static final java.util.Set<List<Long>> bannedEnds = new java.util.HashSet<>();
    /** Spots (packed) a cast from failed in the game: no air line starts there from then on. */
    private static final java.util.Set<Long> bannedFlights = new java.util.HashSet<>();
    /** The shortest hop {@link #kept} was built with ({@link Tuning#ETHERWARP_MIN}). */
    private static double keptMin;
    private static TransmitHops keptCasts;
    /** The shortest cast {@link #keptCasts} was built with ({@link Tuning#IT_MIN}). */
    private static double keptCastsMin;
    private static ArrayBlockView keptCastsWorld;
    /** Casts of {@link #keptCasts} that failed in the game. */
    private static final java.util.Set<Integer> bannedCasts = new java.util.HashSet<>();

    /** Where a whole map's hops are kept on disk, and the map they're for. */
    private static java.nio.file.Path hopDir;
    private static ArrayBlockView hopDirWorld;

    /**
     * Hops for {@code world} are kept in files in {@code dir} (beside its saved move graph),
     * read back instead of built when the world and graph haven't changed.
     */
    static synchronized void keepIn(java.nio.file.Path dir, ArrayBlockView world) {
        hopDir = dir;
        hopDirWorld = world;
    }

    private interface Reader<T> {
        T read(java.io.DataInputStream in) throws java.io.IOException;
    }

    private interface Writer {
        void write(java.io.DataOutputStream out) throws java.io.IOException;
    }

    /** Hops read from file {@code name} for {@code world}; null if there are none that fit. */
    private static <T> T load(ArrayBlockView world, String name, Reader<T> reader) {
        if (hopDir == null || hopDirWorld != world) {
            return null;
        }
        java.nio.file.Path file = hopDir.resolve(name);
        if (!java.nio.file.Files.exists(file)) {
            return null;
        }
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(file),
                        1 << 16))) {
            return reader.read(in);
        } catch (java.io.IOException | RuntimeException e) {
            System.out.println("[astar] couldn't read " + file + ": " + e);
            return null;
        }
    }

    /** Writes hops just built for {@code world} to file {@code name}, if it has a place. */
    private static void save(ArrayBlockView world, String name, Writer writer) {
        if (hopDir == null || hopDirWorld != world) {
            return;
        }
        java.nio.file.Path file = hopDir.resolve(name);
        java.nio.file.Path tmp = hopDir.resolve(name + ".tmp");
        try {
            java.nio.file.Files.createDirectories(hopDir);
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                    new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(tmp),
                            1 << 16))) {
                writer.write(out);
            }
            java.nio.file.Files.move(tmp, file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            System.out.println("[astar] couldn't save " + file + ": " + e);
        }
    }

    /**
     * The hops over {@code graph}: the kept ones if they were built on it or on a graph it
     * patched, else built now (a few seconds for a whole map).
     */
    static synchronized WarpHops hops(ArrayBlockView world, NavGraph graph) {
        double min = Tuning.ETHERWARP_MIN.get();
        if (kept != null && keptWorld == world && keptMin == min) {
            WarpHops onto = kept.onto(graph);
            if (onto != null) {
                kept = onto;
                return onto;
            }
        }
        // Hops over a graph of two places (see joined) aren't the map's: not kept on disk.
        boolean onDisk = graph != joined;
        String key = WarpHops.cacheKey(min);
        String file = min == WarpHops.DEFAULT_MIN_LENGTH ? "warps.bin"
                : "warps-" + Tuning.ETHERWARP_MIN.text() + ".bin";
        kept = onDisk ? load(world, file, in -> WarpHops.read(in, world, graph, key)) : null;
        if (kept == null) {
            kept = WarpHops.build(graph, world, WarpHops.DEFAULT_RANGE, WarpHops.DEFAULT_COST,
                    min, WarpHops.DEFAULT_SPACING);
            WarpHops built = kept;
            if (onDisk) {
                save(world, file, out -> built.write(out, world, key));
            }
        }
        keptWorld = world;
        keptMin = min;
        banned.clear();
        bannedEnds.clear();
        System.out.printf("[astar] etherwarp hops: %,d anchors, %,d sure hops, %s in %.0f ms%n",
                kept.anchors(), kept.count(), kept.raysCast() == 0 ? "read" : "built",
                kept.buildMs());
        return kept;
    }

    /**
     * The Instant Transmission casts over {@code graph}: the kept ones if they were built on it
     * or on a graph it patched, else built now (several seconds for a whole map).
     */
    static synchronized TransmitHops casts(ArrayBlockView world, NavGraph graph) {
        double min = Tuning.IT_MIN.get();
        if (keptCasts != null && keptCastsWorld == world && keptCastsMin == min) {
            TransmitHops onto = keptCasts.onto(graph);
            if (onto != null) {
                keptCasts = onto;
                return onto;
            }
        }
        boolean onDisk = graph != joined;
        String key = TransmitHops.cacheKey(min);
        String file = min == TransmitHops.DEFAULT_MIN_LENGTH ? "casts.bin"
                : "casts-" + Tuning.IT_MIN.text() + ".bin";
        keptCasts = onDisk ? load(world, file, in -> TransmitHops.read(in, world, graph, key))
                : null;
        if (keptCasts == null) {
            keptCasts = TransmitHops.build(graph, world, TransmitHops.DEFAULT_RANGE,
                    TransmitHops.DEFAULT_COST, min, TransmitHops.DEFAULT_SPACING);
            TransmitHops built = keptCasts;
            if (onDisk) {
                save(world, file, out -> built.write(out, world, key));
            }
        }
        keptCastsWorld = world;
        keptCastsMin = min;
        bannedCasts.clear();
        bannedFlights.clear();
        System.out.printf("[astar] instant transmission: %,d spots, %,d sure casts, ready in"
                + " %.0f ms%n", keptCasts.anchors(), keptCasts.count(), keptCasts.buildMs());
        return keptCasts;
    }

    /** The last graph flooded from two places walking doesn't join, and its world. */
    private static NavGraph joined;
    private static ArrayBlockView joinedWorld;

    /** A graph around both {@code from} and {@code to}: the last one if it still fits. */
    private static synchronized NavGraph joined(ArrayBlockView world, WorldPathfinder base,
            BlockPoint from, BlockPoint to) {
        if (joined == null || joinedWorld != world || joined.stale()
                || !joined.covers(from.pack()) || !joined.covers(to.pack())) {
            joined = NavGraph.build(base, from.pack(), to.pack());
            joinedWorld = world;
        }
        return joined;
    }

    /** Whether the hops for this world are built (so a route needn't wait for them). */
    static synchronized boolean ready(ArrayBlockView world, Mode mode) {
        return (!mode.ether || kept != null && keptWorld == world
                        && keptMin == Tuning.ETHERWARP_MIN.get())
                && (!mode.transmit || keptCasts != null && keptCastsWorld == world
                        && keptCastsMin == Tuning.IT_MIN.get());
    }

    /** The cheapest route from {@code from} to {@code to} walking and teleporting. */
    static Route route(ArrayBlockView world, WorldPathfinder base, BlockPoint from,
            BlockPoint to, Mode mode) {
        NavGraph graph = base.graphCovering(from.pack());
        if (!graph.covers(to.pack())) {
            // Walking doesn't join them (up off a floor below, say): the places around both,
            // for the hops to join. Kept for the replans of the trip, so the hops on it (and
            // the ones that failed) carry over.
            graph = joined(world, base, from, to);
        }
        boolean had = ready(world, mode);
        long t0 = System.nanoTime();
        WarpHops hops = mode.ether ? hops(world, graph) : null;
        TransmitHops casts = mode.transmit ? casts(world, graph) : null;
        double buildMs = had ? 0 : (System.nanoTime() - t0) / 1e6;
        Asked asked;
        synchronized (Warps.class) {
            asked = new Asked(world, world.version(), graph, hops, casts, to, mode,
                    java.util.Set.copyOf(banned), java.util.Set.copyOf(bannedEnds),
                    java.util.Set.copyOf(bannedCasts),
                    java.util.Set.copyOf(bannedFlights), Tuning.ETHERWARP_COST.get(),
                    Tuning.IT_COST.get());
        }
        Route again = recent(asked, from);
        if (again != null) {
            System.out.printf("[astar] same route as a recent plan from %s on, not planned"
                    + " again%n", from);
            return again;
        }
        List<HopGraph.Moves> sets = new ArrayList<>();
        if (hops != null) {
            sets.add(hops.moves(asked.banned(), Tuning.ETHERWARP_COST.get()));
        }
        if (casts != null) {
            sets.add(casts.moves(asked.bannedCasts(), Tuning.IT_COST.get()));
        }
        double rate = rate(graph, sets);
        // Hops from right where the player stands and straight onto the goal, worked out for
        // this trip: the map's hops only join its anchors.
        WarpHops.Ends ends = null;
        if (hops != null) {
            long t2 = System.nanoTime();
            ends = hops.ends(world, from.pack(), to.pack(), Tuning.ETHERWARP_MIN.get(),
                    asked.bannedEnds());
            HopGraph.Moves own = ends.moves(Tuning.ETHERWARP_COST.get());
            sets.add(own);
            rate = HopGraph.rate(graph, List.of(own), rate);
            System.out.printf("[astar] etherwarp hops from the start and onto the goal: %d in"
                    + " %.0f ms%n", ends.count(), (System.nanoTime() - t2) / 1e6);
        }
        MoveGraph moves = HopGraph.of(graph, sets);
        AStarSearch search = new AStarSearch(from, to, moves,
                HopGraph.costs(Tuning.TURN.get(), WarpHops.DEFAULT_TURN, to.pack(),
                        HopGraph.DEFAULT_DETOUR, HopGraph.DEFAULT_ETHER_LENGTH,
                        HopGraph.DEFAULT_WALK_DETOUR),
                HopGraph.heuristic(rate), SearchListener.NONE);
        search.runToEnd();
        SearchResult r = search.result();
        if (!r.found()) {
            return new Route(List.of(), List.of(), r, buildMs);
        }
        List<PathStep> path = r.path();
        java.util.Map<Integer, Flights.Flight> flights = java.util.Map.of();
        if (casts != null) {
            java.util.Set<Long> noFlights = asked.bannedFlights();
            long t1 = System.nanoTime();
            Flights.Result air = Flights.straighten(world, moves, path, casts.range(),
                    HopGraph.DEFAULT_DETOUR, HopGraph.DEFAULT_ETHER_LENGTH,
                    HopGraph.DEFAULT_WALK_DETOUR, noFlights);
            System.out.printf("[astar] air lines: %d flights, %.1f blocks saved, %.0f ms%n",
                    air.flights().size(), air.saved(), (System.nanoTime() - t1) / 1e6);
            path = air.path();
            flights = air.flights();
        }
        List<List<PathStep>> legs = new ArrayList<>();
        List<Hop> cast = new ArrayList<>();
        List<PathStep> leg = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            PathStep s = path.get(i);
            if (s.via() == MoveType.WARP || s.via() == MoveType.TRANSMIT) {
                BlockPoint a = leg.get(leg.size() - 1).pos();
                Flights.Flight f = flights.get(i);
                if (s.via() == MoveType.WARP) {
                    float[] aim = hops.aim(a.pack(), s.pos().pack());
                    if (aim == null) {
                        aim = ends.aim(a.pack(), s.pos().pack());
                    }
                    cast.add(new Hop(a, s.pos(), false, aim[0], aim[1], aim[2], 0, 0, 1,
                            List.of(), new float[0], new float[0]));
                } else if (f != null) {
                    List<BlockPoint> through = new ArrayList<>();
                    for (long p : f.through()) {
                        through.add(new BlockPoint(astar.core.Pos.x(p), astar.core.Pos.y(p),
                                astar.core.Pos.z(p)));
                    }
                    cast.add(new Hop(a, s.pos(), true, 0, 0, 0, f.yaw()[0], f.pitch()[0],
                            f.casts(), through, f.yaw(), f.pitch()));
                } else {
                    int k = casts.index(a.pack(), s.pos().pack());
                    float[] view = casts.view(k);
                    List<BlockPoint> through = new ArrayList<>();
                    for (long p : TransmitHops.cells(world, a.pack(), view[0], view[1],
                            casts.chain(k), casts.range())) {
                        through.add(new BlockPoint(astar.core.Pos.x(p), astar.core.Pos.y(p),
                                astar.core.Pos.z(p)));
                    }
                    float[] yaws = new float[casts.chain(k)];
                    float[] pitches = new float[yaws.length];
                    java.util.Arrays.fill(yaws, view[0]);
                    java.util.Arrays.fill(pitches, view[1]);
                    cast.add(new Hop(a, s.pos(), true, 0, 0, 0, view[0], view[1],
                            casts.chain(k), through, yaws, pitches));
                }
                legs.add(leg);
                leg = new ArrayList<>();
                leg.add(new PathStep(s.pos(), null));
            } else {
                leg.add(s);
            }
        }
        legs.add(leg);
        Route route = new Route(legs, cast, r, buildMs);
        keep(asked, route);
        return route;
    }

    /**
     * What a route was planned over and to: the same map (unchanged since), graph and kept
     * hops, goal, mode, hops left out and teleport costs. Another plan asked the same from a spot the route
     * goes through is the rest of that route.
     */
    private record Asked(ArrayBlockView world, int version, NavGraph graph, WarpHops hops,
            TransmitHops casts, BlockPoint to, Mode mode, java.util.Set<Integer> banned,
            java.util.Set<List<Long>> bannedEnds,
            java.util.Set<Integer> bannedCasts, java.util.Set<Long> bannedFlights,
            double etherCost, double castCost) {

        boolean same(Asked o) {
            return world == o.world && version == o.version && graph == o.graph
                    && hops == o.hops && casts == o.casts && to.equals(o.to) && mode == o.mode
                    && banned.equals(o.banned) && bannedEnds.equals(o.bannedEnds)
                    && bannedCasts.equals(o.bannedCasts)
                    && bannedFlights.equals(o.bannedFlights) && etherCost == o.etherCost
                    && castCost == o.castCost;
        }
    }

    private record Planned(Asked asked, Route route) {}

    /** Routes planned lately, newest first. */
    private static final java.util.Deque<Planned> RECENT = new java.util.ArrayDeque<>();
    private static final int RECENT_MOST = 8;

    private static synchronized void keep(Asked asked, Route route) {
        RECENT.addFirst(new Planned(asked, route));
        while (RECENT.size() > RECENT_MOST) {
            RECENT.removeLast();
        }
    }

    /**
     * The rest of a route planned lately for the same {@code asked}, from where it goes
     * through {@code from} (a spot walked to or teleported to on it); null if none does.
     */
    private static synchronized Route recent(Asked asked, BlockPoint from) {
        for (Planned p : RECENT) {
            if (!p.asked().same(asked)) {
                continue;
            }
            List<List<PathStep>> legs = p.route().legs();
            for (int l = 0; l < legs.size(); l++) {
                List<PathStep> leg = legs.get(l);
                for (int k = 0; k < leg.size(); k++) {
                    if (!leg.get(k).pos().equals(from)) {
                        continue;
                    }
                    List<List<PathStep>> rest = new ArrayList<>();
                    List<PathStep> first = new ArrayList<>(leg.subList(k, leg.size()));
                    first.set(0, new PathStep(from, null));
                    rest.add(first);
                    rest.addAll(legs.subList(l + 1, legs.size()));
                    List<PathStep> steps = new ArrayList<>();
                    for (List<PathStep> r : rest) {
                        steps.addAll(steps.isEmpty() ? r : r.subList(1, r.size()));
                    }
                    SearchResult was = p.route().result();
                    return new Route(rest, p.route().hops().subList(l, p.route().hops().size()),
                            new SearchResult(SearchResult.Status.FOUND, steps, was.cost(),
                                    java.util.Set.of(), java.util.Set.of(), 0), 0);
                }
            }
        }
        return null;
    }

    /**
     * The heuristic's rate for these teleports, worked out once per set of kept hops (it's a
     * pass over all of them) and kept.
     */
    private static double rate(NavGraph graph, List<HopGraph.Moves> sets) {
        List<Object> key = new ArrayList<>();
        for (HopGraph.Moves m : sets) {
            key.add(m.from());
            key.add(m.cost().length == 0 ? 0.0 : m.cost()[0]);
        }
        key.add(walkFloor());
        synchronized (Warps.class) {
            if (!key.equals(rateKey)) {
                keptRate = HopGraph.rate(graph, sets, walkFloor());
                rateKey = key;
            }
            return keptRate;
        }
    }

    private static List<Object> rateKey;
    private static double keptRate;

    /**
     * Leaves the hop from {@code from} to {@code to} (grid cells) out of routes from now on:
     * it didn't work in the game.
     */
    static synchronized void ban(Hop hop) {
        if (hop.transmit()) {
            // No air line from there either.
            bannedFlights.add(hop.from().pack());
            if (keptCasts != null) {
                int k = keptCasts.index(hop.from().pack(), hop.to().pack());
                if (k >= 0) {
                    bannedCasts.add(k);
                }
            }
        } else if (kept != null) {
            int k = kept.index(hop.from().pack(), hop.to().pack());
            if (k >= 0) {
                banned.add(k);
            } else {
                bannedEnds.add(List.of(hop.from().pack(), hop.to().pack()));
            }
        }
    }

    /** The least a .A* walking move costs per block of straight-line distance. */
    private static double walkFloor() {
        var c = GotoPathfinder.costs();
        double least = Math.min(c.straightFloor(), c.diagonalFloor() / Math.sqrt(2));
        least = Math.min(least, Math.min(c.stepInPlace(), c.dropPerBlock()));
        return least / Math.sqrt(3);
    }
}
