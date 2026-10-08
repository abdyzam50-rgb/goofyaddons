package astar.client.ui;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A quiet background for the A* window, made like the loading screen: a random maze, an A*
 * search spreading through it from its start to a blinking goal, the route drawn in, held a moment,
 * then faded out for a new maze. One texture pixel a maze cell, shown in whole units so it stays
 * crisp, painted again only when the picture changes, and faint so the settings read over it.
 */
public final class MazeBackground {
    /** A maze cell's size, in units. */
    public static final int CELL = 6;
    private static final long FADE_IN_MS = 600;
    private static final long PAUSE_MS = 700;
    private static final long PATH_MS = 900;
    private static final long HOLD_MS = 1800;
    private static final long FADE_OUT_MS = 700;

    /** The seed the lighter walls are picked with, so they're the same every time. */
    private static final long LIGHT_WALL_SEED = 0x4153544152L;

    private static MazeBackground shared;
    /** Which cells are drawn terrain-light if they're walls. */
    private boolean[] lightWall;

    private final Random random = new Random();
    private final Identifier id = Identifier.fromNamespaceAndPath("astar", "maze/background");
    private DynamicTexture texture;
    private int cols;
    private int rows;
    private boolean[] floor;
    /** The search step each cell was put on the open list and closed on, or MAX_VALUE. */
    private int[] openedAt;
    private int[] closedAt;
    private int[] path;
    private int start;
    private int goal;
    private int steps;
    private long born = -1;
    private long paintedKey = Long.MIN_VALUE;
    private int paintedColour;

    /** The one background, so it carries on where it was each time the window opens. */
    public static MazeBackground get() {
        if (shared == null) {
            shared = new MazeBackground();
        }
        return shared;
    }

    /**
     * Draws it filling {@code (x, y, w, h)} (in units, on the cell grid; the cells left over
     * are shared out as a margin) at this opacity, coloured for the theme on show.
     */
    public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
        int c = Math.max(5, (w / CELL - 1) | 1);
        int r = Math.max(5, (h / CELL - 1) | 1);
        long now = System.currentTimeMillis();
        if (c != cols || r != rows || floor == null) {
            cols = c;
            rows = r;
            texture = null;
            make(now);
        }
        long age = now - born;
        long search = searchMs();
        long pathAt = FADE_IN_MS + PAUSE_MS + search;
        long end = pathAt + PATH_MS + HOLD_MS + FADE_OUT_MS;
        if (age >= end) {
            make(now);
            age = 0;
        }
        float fade = age < FADE_IN_MS ? age / (float) FADE_IN_MS
                : age > end - FADE_OUT_MS ? (end - age) / (float) FADE_OUT_MS : 1;
        // How far along it is: search steps shown, and route cells shown.
        int step = (int) Math.clamp((age - FADE_IN_MS - PAUSE_MS) * steps / Math.max(1, search),
                -1, steps);
        int shown = age < pathAt ? 0
                : (int) Math.min(path.length, (age - pathAt) * path.length / PATH_MS + 1);
        Theme.Colours th = Theme.shown();
        int colour = th.accent * 31 ^ th.slate * 17 ^ th.terrain;
        long key = step;
        if (texture == null) {
            texture = new DynamicTexture(() -> "astar maze", cols, rows, false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            paintedKey = Long.MIN_VALUE;
        }
        if (key != paintedKey || colour != paintedColour) {
            paint(step, th);
            texture.upload();
            paintedKey = key;
            paintedColour = colour;
        }
        int dw = cols * CELL;
        int dh = rows * CELL;
        int ox = x + (w - dw) / 2;
        int oy = y + (h - dh) / 2;
        int a = Math.round(Math.clamp(alpha * fade, 0, 1) * 255);
        if (a <= 0) {
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, id, ox, oy, 0, 0, dw, dh, cols, rows, cols, rows,
                a << 24 | 0xFFFFFF);
        // Drawn like the loading screen's map: the route a thin line through small hollow
        // boxes, the start a hollow box with a core, the goal a four-point star.
        int m = Math.round(Math.clamp(alpha * fade * 2, 0, 1) * 255) << 24;
        int line = m | (th.slateLight & 0xFFFFFF);
        for (int k = 0; k + 1 < shown && k + 1 < path.length; k++) {
            link(g, ox, oy, path[k], path[k + 1], line);
        }
        for (int k = 0; k < shown && k < path.length; k++) {
            if (k % 3 == 0 || k == path.length - 1) {
                node(g, ox, oy, path[k] % cols, path[k] / cols, m | (th.highlight & 0xFFFFFF));
            }
        }
        startBox(g, ox, oy, start % cols, start / cols, m | (th.highlight & 0xFFFFFF));
        if (shown > 0 || age / 300 % 2 == 0) {
            star(g, ox, oy, goal % cols, goal / cols, m | (th.highlight & 0xFFFFFF));
        }
    }

    /** A thin line between two cells' middles, as the loading screen joins its route nodes. */
    private void link(GuiGraphicsExtractor g, int ox, int oy, int from, int to, int argb) {
        int ax = ox + (from % cols) * CELL + CELL / 2;
        int ay = oy + (from / cols) * CELL + CELL / 2;
        int bx = ox + (to % cols) * CELL + CELL / 2;
        int by = oy + (to / cols) * CELL + CELL / 2;
        Panels.rect(g, Math.min(ax, bx), Math.min(ay, by), Math.abs(bx - ax) + 1,
                Math.abs(by - ay) + 1, 0, argb);
    }

    /** A small hollow box on the route. */
    private void node(GuiGraphicsExtractor g, int ox, int oy, int cx, int cy, int argb) {
        int x = ox + cx * CELL + CELL / 2 - 2;
        int y = oy + cy * CELL + CELL / 2 - 2;
        Panels.framed(g, x, y, 5, 5, 0, 1, argb, 0);
    }

    /** The start: a bigger hollow box with a core, as the loading screen draws it. */
    private void startBox(GuiGraphicsExtractor g, int ox, int oy, int cx, int cy, int argb) {
        int x = ox + cx * CELL + CELL / 2 - 4;
        int y = oy + cy * CELL + CELL / 2 - 4;
        Panels.framed(g, x, y, 9, 9, 0, 2, argb, 0);
        Panels.rect(g, x + 4, y + 4, 1, 1, 0, argb);
    }

    /** The goal: a four-point star, its arms tapering, as the loading screen draws it. */
    private void star(GuiGraphicsExtractor g, int ox, int oy, int cx, int cy, int argb) {
        int x = ox + cx * CELL + CELL / 2;
        int y = oy + cy * CELL + CELL / 2;
        Panels.rect(g, x - 1, y - 1, 3, 3, 0, argb);
        for (int i = 2; i <= 6; i++) {
            Panels.rect(g, x - i, y, 1, 1, 0, argb);
            Panels.rect(g, x + i, y, 1, 1, 0, argb);
            Panels.rect(g, x, y - i, 1, 1, 0, argb);
            Panels.rect(g, x, y + i, 1, 1, 0, argb);
        }
        for (int i = 1; i <= 2; i++) {
            Panels.rect(g, x - i, y - i, 1, 1, 0, argb);
            Panels.rect(g, x + i, y - i, 1, 1, 0, argb);
            Panels.rect(g, x - i, y + i, 1, 1, 0, argb);
            Panels.rect(g, x + i, y + i, 1, 1, 0, argb);
        }
    }

    /** One maze cell filled with this colour, if it is inside the maze. */
    private void cell(GuiGraphicsExtractor g, int ox, int oy, int cx, int cy, int argb) {
        if (cx >= 0 && cy >= 0 && cx < cols && cy < rows) {
            Panels.rect(g, ox + cx * CELL, oy + cy * CELL, CELL, CELL, 0, argb);
        }
    }

    /** How long the search spreads for: a few seconds, whatever the maze's size. */
    private long searchMs() {
        return Math.clamp(steps * 12L, 1500, 4000);
    }

    /** A new random maze, a start and goal far apart in it, and the search between them. */
    private void make(long now) {
        born = now;
        int n = cols * rows;
        floor = new boolean[n];
        // A maze carved from (1, 1) by a random walk that backs up at dead ends...
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        floor[cols + 1] = true;
        stack.push(cols + 1);
        int[][] dirs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        while (!stack.isEmpty()) {
            int at = stack.peek();
            int ax = at % cols;
            int ay = at / cols;
            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
            boolean moved = false;
            for (int d : order) {
                int nx = ax + dirs[d][0];
                int ny = ay + dirs[d][1];
                if (nx > 0 && ny > 0 && nx < cols - 1 && ny < rows - 1 && !floor[ny * cols + nx]) {
                    floor[(ay + dirs[d][1] / 2) * cols + ax + dirs[d][0] / 2] = true;
                    floor[ny * cols + nx] = true;
                    stack.push(ny * cols + nx);
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                stack.pop();
            }
        }
        // ...with some walls knocked through, so there is more than one way and the search
        // has choices to make.
        for (int yy = 1; yy < rows - 1; yy++) {
            for (int xx = 1; xx < cols - 1; xx++) {
                int i = yy * cols + xx;
                boolean across = floor[i - 1] && floor[i + 1] && !floor[i - cols] && !floor[i + cols];
                boolean down = floor[i - cols] && floor[i + cols] && !floor[i - 1] && !floor[i + 1];
                if (!floor[i] && (across || down) && random.nextFloat() < 0.12f) {
                    floor[i] = true;
                }
            }
        }
        // Start and goal anywhere, on room cells, but far apart, so the search has a way to go
        // and they turn up in different places each time.
        start = room(1, cols - 2);
        goal = room(1, cols - 2);
        for (int tries = 0; tries < 60 && Math.abs(goal % cols - start % cols)
                + Math.abs(goal / cols - start / cols) < (cols + rows) / 2; tries++) {
            goal = room(1, cols - 2);
        }
        search();
    }

    private int room(int fromX, int toX) {
        int x = (fromX + random.nextInt(Math.max(1, toX - fromX + 1))) | 1;
        int y = (1 + random.nextInt(rows - 2)) | 1;
        x = Math.min(x, cols - 2 - (cols % 2 == 0 ? 1 : 0));
        y = Math.min(y, rows - 2 - (rows % 2 == 0 ? 1 : 0));
        return y * cols + x;
    }

    /** A* from start to goal (Manhattan distance), noting when each cell was opened and closed. */
    private void search() {
        int n = cols * rows;
        openedAt = new int[n];
        closedAt = new int[n];
        Arrays.fill(openedAt, Integer.MAX_VALUE);
        Arrays.fill(closedAt, Integer.MAX_VALUE);
        int[] cost = new int[n];
        int[] from = new int[n];
        Arrays.fill(cost, Integer.MAX_VALUE);
        int gx = goal % cols;
        int gy = goal / cols;
        // Ordered by estimate, then by most recent: ties go deep, as the loading art's do.
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> a[1] != b[1]
                ? Integer.compare(a[1], b[1]) : Integer.compare(b[2], a[2]));
        cost[start] = 0;
        from[start] = -1;
        openedAt[start] = 0;
        int pushes = 0;
        open.add(new int[] {start, Math.abs(start % cols - gx) + Math.abs(start / cols - gy),
                pushes++});
        int step = 0;
        while (!open.isEmpty()) {
            int at = open.poll()[0];
            if (closedAt[at] != Integer.MAX_VALUE) {
                continue;
            }
            closedAt[at] = step++;
            if (at == goal) {
                break;
            }
            int[] next = {at - 1, at + 1, at - cols, at + cols};
            for (int nb : next) {
                if (nb < 0 || nb >= n || !floor[nb] || closedAt[nb] != Integer.MAX_VALUE) {
                    continue;
                }
                int c = cost[at] + 1;
                if (c < cost[nb]) {
                    cost[nb] = c;
                    from[nb] = at;
                    if (openedAt[nb] == Integer.MAX_VALUE) {
                        openedAt[nb] = step;
                    }
                    int h = Math.abs(nb % cols - gx) + Math.abs(nb / cols - gy);
                    open.add(new int[] {nb, c + h, pushes++});
                }
            }
        }
        steps = step;
        int len = 0;
        for (int at = goal; at != -1 && cost[goal] != Integer.MAX_VALUE; at = from[at]) {
            len++;
        }
        path = new int[len];
        int i = len;
        for (int at = goal; i > 0; at = from[at]) {
            path[--i] = at;
        }
    }

    /**
     * Paints the search in the theme's tokens, flat: walls terrain (about one in ten, picked once
     * with a fixed seed, terrain-light), corridors the background, cells the search has closed
     * slate, and its frontier (the active search) the accent. (The route, start and goal are
     * drawn over it.)
     */
    private void paint(int step, Theme.Colours th) {
        if (lightWall == null || lightWall.length != cols * rows) {
            Random seeded = new Random(LIGHT_WALL_SEED);
            lightWall = new boolean[cols * rows];
            for (int i = 0; i < lightWall.length; i++) {
                lightWall[i] = seeded.nextFloat() < 0.1f;
            }
        }
        var pixels = texture.getPixels();
        for (int i = 0; i < cols * rows; i++) {
            int argb;
            if (!floor[i]) {
                argb = lightWall[i] ? th.terrainLight : th.terrain;
            } else if (closedAt[i] <= step) {
                argb = th.slate;
            } else if (openedAt[i] <= step) {
                argb = th.accent;
            } else {
                argb = lightWall[i] ? th.panelBorder : th.bg;
            }
            pixels.setPixel(i % cols, i / cols, argb);
        }
    }
}
