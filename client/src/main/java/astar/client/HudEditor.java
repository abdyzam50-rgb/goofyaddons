package astar.client;

import astar.client.ui.Panels;
import astar.client.ui.Pixel;
import astar.client.ui.PixelText;
import astar.client.ui.Theme;
import astar.client.ui.Ui;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

/**
 * Moves the HUD's widgets: drag one to move it (it snaps to the screen's edges and middle and to
 * the other widgets, with a line showing what it snapped to), scroll over one to make it bigger
 * or smaller, right click one to hide it. Escape saves and goes back to the window.
 */
final class HudEditor extends Screen {

    private final Screen back;
    private Hud.Widget held;
    private float grabX;
    private float grabY;
    /** The lines it snapped to, in GUI coordinates; NaN for none. */
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private static final float SNAP = 4;
    private static final float MARGIN = 2;

    HudEditor(Screen back) {
        super(Component.literal("Edit HUD"));
        this.back = back;
    }

    @Override
    protected void init() {
        Hud.editing = true;
    }

    @Override
    public void removed() {
        Hud.editing = false;
        Hud.save();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Hud.Widget at(double mx, double my) {
        for (Hud.Widget w : Hud.WIDGETS.reversed()) {
            if (!w.on) {
                continue;
            }
            float[] b = Hud.box(w, width, height);
            if (mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]) {
                return w;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Hud.Widget w = at(event.x(), event.y());
        if (w == null) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            w.on = false;
            return true;
        }
        float[] b = Hud.box(w, width, height);
        held = w;
        grabX = (float) event.x() - b[0];
        grabY = (float) event.y() - b[1];
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (held == null) {
            return true;
        }
        float[] b = Hud.box(held, width, height);
        float x = (float) event.x() - grabX;
        float y = (float) event.y() - grabY;
        guideX = Float.NaN;
        guideY = Float.NaN;
        // Edges it can snap to: the screen's sides and middle, and every other widget's.
        float[] xs = snapX(b[2]);
        float[] ys = snapY(b[3]);
        float bestX = SNAP;
        for (int i = 0; i < xs.length; i += 2) {
            float d = Math.abs(x - xs[i]);
            if (d < bestX) {
                bestX = d;
                guideX = xs[i + 1];
                x = xs[i];
            }
        }
        float bestY = SNAP;
        for (int i = 0; i < ys.length; i += 2) {
            float d = Math.abs(y - ys[i]);
            if (d < bestY) {
                bestY = d;
                guideY = ys[i + 1];
                y = ys[i];
            }
        }
        float roomX = Math.max(1, width - b[2] - 2 * Hud.EDGE);
        float roomY = Math.max(1, height - b[3] - 2 * Hud.EDGE);
        held.fx = Math.clamp((x - Hud.EDGE) / roomX, 0, 1);
        held.fy = Math.clamp((y - Hud.EDGE) / roomY, 0, 1);
        return true;
    }

    /** Pairs of (where its left goes, where the guide line is) for a widget {@code w} wide. */
    private float[] snapX(float w) {
        java.util.List<Float> list = new java.util.ArrayList<>(java.util.List.of(
                Hud.EDGE, 0f, width - w - Hud.EDGE, (float) width, (width - w) / 2,
                width / 2f));
        for (Hud.Widget o : Hud.WIDGETS) {
            if (o != held && o.on) {
                float[] b = Hud.box(o, width, height);
                list.addAll(java.util.List.of(b[0], b[0], b[0] + b[2] - w, b[0] + b[2],
                        b[0] + b[2] + MARGIN, b[0] + b[2], b[0] - w - MARGIN, b[0]));
            }
        }
        return toArray(list);
    }

    private float[] snapY(float h) {
        java.util.List<Float> list = new java.util.ArrayList<>(java.util.List.of(
                Hud.EDGE, 0f, height - h - Hud.EDGE, (float) height, (height - h) / 2,
                height / 2f));
        for (Hud.Widget o : Hud.WIDGETS) {
            if (o != held && o.on) {
                float[] b = Hud.box(o, width, height);
                list.addAll(java.util.List.of(b[1], b[1], b[1] + b[3] - h, b[1] + b[3],
                        b[1] + b[3] + MARGIN, b[1] + b[3], b[1] - h - MARGIN, b[1]));
            }
        }
        return toArray(list);
    }

    private static float[] toArray(java.util.List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        held = null;
        guideX = Float.NaN;
        guideY = Float.NaN;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Hud.Widget w = at(mx, my);
        if (w != null && scrollY != 0) {
            w.size = Math.clamp(Hud.px(w) + (scrollY > 0 ? 1 : -1), 1, 4);
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // The world shows through, a little dimmed, so the widgets are placed against it.
        g.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Theme.Colours th = Theme.shown();
        Hud.Widget hot = held != null ? held : at(mouseX, mouseY);
        // A faint grid of the middle lines.
        g.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);
        for (Hud.Widget w : Hud.WIDGETS) {
            if (w.on) {
                Hud.drawWidget(g, w, width, height, w == hot);
            }
        }
        int guide = th.accentBright | 0xFF000000;
        if (!Float.isNaN(guideX)) {
            int gx = Math.round(guideX);
            g.fill(gx, 0, gx + 1, height, guide);
        }
        if (!Float.isNaN(guideY)) {
            int gy = Math.round(guideY);
            g.fill(0, gy, width, gy + 1, guide);
        }
        // How to use it, in a bar at the top.
        int px = Hud.windowPx();
        float k = Hud.scale(px);
        FontDescription strong = Ui.font("strong", px);
        FontDescription small = Ui.font("small", px);
        String head = "Edit HUD";
        String help = "Drag to move, scroll to resize, right click to hide, Esc to save";
        int w = Math.max(PixelText.width(font, head, strong), PixelText.width(font, help, small))
                + 24;
        int uw = Math.round(width / k);
        g.pose().pushMatrix();
        g.pose().scale(k, k);
        int x = (uw - w) / 2;
        int y = 6;
        Pixel.panel(g, x, y, w, 30, th.accent, th.bgPanel);
        Panels.rect(g, x + 1, y + 1, w - 2, 1, 0, th.highlight);
        PixelText.draw(g, font, head, strong, x + 12, y + 6, th.text);
        PixelText.draw(g, font, help, small, x + 12, y + 18, th.textDim);
        if (hot != null) {
            // The widget's name and size by the mouse.
            String tag = hot.label + "  " + Hud.px(hot) + "x";
            int tw = PixelText.width(font, tag, small) + 10;
            int tx = Math.round(mouseX / k) + 8;
            int ty = Math.round(mouseY / k) + 8;
            Pixel.panel(g, tx, ty, tw, 14, th.panelBorder, th.bg);
            PixelText.draw(g, font, tag, small, tx + 5, ty + 4, th.highlight);
        }
        g.pose().popMatrix();
    }
}
