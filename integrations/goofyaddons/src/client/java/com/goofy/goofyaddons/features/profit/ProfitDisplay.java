package com.goofy.goofyaddons.features.profit;

import java.util.Locale;

public final class ProfitDisplay {
    private ProfitDisplay() {}

    public static String coins(Double value) {
        if (value == null || !Double.isFinite(value)) return "--";
        double abs=Math.abs(value);
        if (abs>=1e9) return String.format(Locale.ROOT,"%.2fb",value/1e9);
        if (abs>=1e6) return String.format(Locale.ROOT,"%.2fm",value/1e6);
        if (abs>=1e3) return String.format(Locale.ROOT,"%.1fk",value/1e3);
        return String.format(Locale.ROOT,"%.0f",value);
    }

    /**
     * A coin amount carrying its own sign, so direction never depends on colour alone.
     * Zero stays unsigned; an unknown value stays "--".
     */
    public static String signedCoins(Double value) {
        String text=coins(value);
        if (value==null || !Double.isFinite(value) || value<=0) return text;
        return "+"+text;
    }

    public static String duration(long millis) {
        long seconds=Math.max(0,millis/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
    }

    public record Bounds(int x,int y,int width,int height) {}

    /** One panel. Two meant the lower one could shrink to nothing and still be drawn. */
    public static Bounds bounds(int screenWidth,int screenHeight,boolean left) {
        int width=Math.max(0,Math.min(250,screenWidth-16));
        // The card is exactly as tall as what it will draw, so there is never dead space
        // below the last row and the footer always sits on the bottom edge.
        int height=contentHeight(layout(Math.max(0,Math.min(302,screenHeight-16))));
        return new Bounds(left ? 8 : Math.max(8,screenWidth-width-8),8,width,height);
    }

    // Section heights, shared by the layout decision and the renderer so they cannot drift.
    public static final int HEADER_HEIGHT=46;   // wordmark, status pill, mode line, rule
    public static final int HERO_HEIGHT=40;     // caption plus the one large number
    public static final int TASK_HEIGHT=44;     // rule, caption, activity, item
    public static final int RATE_HEIGHT=15;     // profit per hour
    public static final int DETAIL_ROW_HEIGHT=13;
    /** The rule and gap above the detail rows; reserved so the last row clears the footer. */
    public static final int DETAIL_HEADER_HEIGHT=7;
    public static final int FOOTER_HEIGHT=17;
    public static final int PADDING=12;

    /** The most detail rows the panel will ever show, in the order {@code ProfitHud} draws them. */
    public static final int MAX_DETAIL_ROWS=7;

    // Cumulative room each section needs, in priority order. Thresholds rather than greedy
    // subtraction: greedy was not monotone, so a taller panel could drop the rate line the
    // moment the task block became affordable, and content flickered as a window resized.
    private static final int FOOTER_AT=FOOTER_HEIGHT;
    private static final int HERO_AT=FOOTER_AT+HERO_HEIGHT;
    private static final int TASK_AT=HERO_AT+TASK_HEIGHT;
    private static final int RATE_AT=TASK_AT+RATE_HEIGHT;

    /**
     * Which sections fit, decided rather than clipped.
     *
     * <p>The panel used to draw a fixed set of rows and let the scissor cut off whatever did
     * not fit, so on a short window or a high HUD scale rows vanished with nothing to say
     * they had. The footer comes first because it carries warnings; then the one headline
     * number, what the engine is doing, the rate, and finally the detail rows.
     */
    public record Layout(boolean hero,boolean task,boolean rate,int detailRows,boolean footer) {
        public boolean anything() { return hero || task || rate || detailRows>0 || footer; }
    }

    public static Layout layout(int panelHeight) {
        int room=panelHeight-HEADER_HEIGHT;
        if (room<FOOTER_AT) return new Layout(false,false,false,0,false);
        int afterRate=room-RATE_AT-DETAIL_HEADER_HEIGHT;
        int rows=afterRate<DETAIL_ROW_HEIGHT ? 0 : Math.min(MAX_DETAIL_ROWS,afterRate/DETAIL_ROW_HEIGHT);
        return new Layout(room>=HERO_AT,room>=TASK_AT,room>=RATE_AT,rows,true);
    }

    /** Exact height the given layout draws, header included. */
    public static int contentHeight(Layout layout) {
        if (!layout.anything()) return 0;
        return HEADER_HEIGHT
                +(layout.hero() ? HERO_HEIGHT : 0)
                +(layout.task() ? TASK_HEIGHT : 0)
                +(layout.rate() ? RATE_HEIGHT : 0)
                +(layout.detailRows()>0 ? DETAIL_HEADER_HEIGHT+layout.detailRows()*DETAIL_ROW_HEIGHT : 0)
                +(layout.footer() ? FOOTER_HEIGHT : 0);
    }

    /** Smallest panel worth drawing: the header plus something under it. */
    public static boolean worthDrawing(int width,int height) {
        return width>=140 && layout(height).anything();
    }
}
