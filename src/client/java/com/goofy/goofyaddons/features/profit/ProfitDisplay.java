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
    public static String duration(long millis) {
        long seconds=Math.max(0,millis/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
    }
    public record Bounds(int x,int y,int width,int height) {}
    public static Bounds bounds(int screenWidth,int screenHeight,boolean left) {
        int width=Math.max(0,Math.min(250,screenWidth-16));
        int height=Math.max(0,Math.min(232,screenHeight-86));
        return new Bounds(left ? 8 : Math.max(8,screenWidth-width-8),8,width,height);
    }
    public static Bounds taskBounds(int screenWidth,int screenHeight,boolean left) {
        Bounds profit=bounds(screenWidth,screenHeight,left);
        int y=profit.y()+profit.height()+6;
        return new Bounds(profit.x(),y,profit.width(),Math.max(0,Math.min(64,screenHeight-y-8)));
    }
}
