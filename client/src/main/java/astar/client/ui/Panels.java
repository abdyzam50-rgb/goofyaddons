package astar.client.ui;

import astar.client.draw.RouteShaders;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * The screens' boxes, square like the loading screen's map cells: a corner of radius
 * {@code r} is cut in whole-unit steps ({@link #steps}), so every edge lands on the screen's
 * pixel grid. Drawn as flat strips by the {@code panel} shader ({@link RouteShaders}), which
 * also draws the soft shadows. Kept inside the scissor area set on the graphics, like the
 * game's own fills.
 */
public final class Panels {

    private Panels() {}

    /**
     * How many one-unit steps a corner is cut in: none. Every box on the screens is square, like
     * the map's cells on the loading screen, and the radius asked for is kept only so the calls
     * read the same.
     */
    public static int steps(int radius) {
        return 0;
    }

    /** A box with stepped corners, its colour going from {@code top} to {@code bottom}. */
    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
            int top, int bottom) {
        stepped(g, x, y, w, h, steps(radius), top, bottom);
    }

    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
            int colour) {
        rect(g, x, y, w, h, radius, colour, colour);
    }

    /** A box with a border {@code border} units wide in another colour, its steps following. */
    public static void framed(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
            int border, int edge, int fill) {
        int n = steps(radius);
        stepped(g, x, y, w, h, n, edge, edge);
        stepped(g, x + border, y + border, w - 2 * border, h - 2 * border,
                Math.max(0, n - border), fill, fill);
    }

    /**
     * A box cut in {@code n} steps at each corner: its top and bottom {@code n} rows each one
     * unit narrower at both ends than the row inside them, and the rows between full width.
     */
    private static void stepped(GuiGraphicsExtractor g, int x, int y, int w, int h, int n,
            int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        n = Math.min(n, Math.min(w, h) / 2);
        for (int i = 0; i < n; i++) {
            strip(g, x + n - i, y + i, w - 2 * (n - i), 1, top, bottom, y, h);
            strip(g, x + n - i, y + h - 1 - i, w - 2 * (n - i), 1, top, bottom, y, h);
        }
        strip(g, x, y + n, w, h - 2 * n, top, bottom, y, h);
    }

    /** One flat strip of a box from {@code y0} down {@code h}, its share of the gradient. */
    private static void strip(GuiGraphicsExtractor g, int x, int y, int w, int sh, int top,
            int bottom, int y0, int h) {
        if (w <= 0 || sh <= 0) {
            return;
        }
        int a = top == bottom ? top : lerp(top, bottom, (y - y0) / (float) h);
        int b = top == bottom ? top : lerp(top, bottom, (y + sh - y0) / (float) h);
        g.guiRenderState.addGuiElement(new Shape(new Matrix3x2f(g.pose()), x, y, w, sh, 0, 0, a,
                b, g.scissorStack.peek()));
    }

    private static int lerp(int from, int to, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 255;
            int b = (to >>> shift) & 255;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    /** A soft shadow round a rectangle, fading out over {@code soft} pixels. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
            int soft, int colour) {
        g.guiRenderState.addGuiElement(new Shape(new Matrix3x2f(g.pose()), x, y, w, h, radius,
                soft, colour, colour, g.scissorStack.peek()));
    }

    private record Shape(Matrix3x2fc pose, int x, int y, int w, int h, int radius, int soft,
            int top, int bottom, @Nullable ScreenRectangle scissorArea)
            implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer buf) {
            float hw = w / 2F;
            float hh = h / 2F;
            float cx = x + hw;
            float cy = y + hh;
            // Packed for panel.vsh: half sizes below 1024, radius and softness above.
            int sx = Math.min(1023, Math.round(hw)) + 1024 * Math.min(31, radius);
            int sy = Math.min(1023, Math.round(hh)) + 1024 * Math.min(31, soft);
            float x0 = x - soft;
            float y0 = y - soft;
            float x1 = x + w + soft;
            float y1 = y + h + soft;
            buf.addVertexWith2DPose(pose, x0, y0).setUv(x0 - cx, y0 - cy).setUv2(sx, sy)
                    .setColor(top);
            buf.addVertexWith2DPose(pose, x0, y1).setUv(x0 - cx, y1 - cy).setUv2(sx, sy)
                    .setColor(bottom);
            buf.addVertexWith2DPose(pose, x1, y1).setUv(x1 - cx, y1 - cy).setUv2(sx, sy)
                    .setColor(bottom);
            buf.addVertexWith2DPose(pose, x1, y0).setUv(x1 - cx, y0 - cy).setUv2(sx, sy)
                    .setColor(top);
        }

        @Override
        public RenderPipeline pipeline() {
            return RouteShaders.PANEL;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public @Nullable ScreenRectangle bounds() {
            ScreenRectangle all = new ScreenRectangle(x - soft, y - soft, w + 2 * soft,
                    h + 2 * soft).transformMaxBounds(pose);
            return scissorArea == null ? all : scissorArea.intersection(all);
        }
    }
}
