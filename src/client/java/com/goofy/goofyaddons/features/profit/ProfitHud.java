package com.goofy.goofyaddons.features.profit;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi;
import com.goofy.goofyaddons.utils.ScoreboardUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ProfitHud {
    // Ink and surfaces for the dark panel, then four reserved status roles. Status colour
    // always accompanies a word, and a signed value, so nothing is carried by colour alone.
    private static final int TEXT=0xFFE8EDF5, MUTED=0xFF9CAABE;
    private static final int SURFACE=0xFF161D29, PILL=0xFF263244, RULE=0xFF334054;
    private static final int GOOD=0xFF72DBAF, WARNING=0xFFF2C14E, CRITICAL=0xFFF08087;
    private ProfitHud() {}
    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("goofyaddons","profit"),(graphics,delta)->{ if (Minecraft.getInstance().screen==null) render(graphics); });
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->
                ScreenEvents.afterExtract(screen).register((current,graphics,mouseX,mouseY,delta)->{
                    graphics.nextStratum();
                    render(graphics);
                }));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(
                ClientCommands.literal("goofyprofit").executes(context->{
                    var s=ProfitTracker.INSTANCE.summary();
                    if (ProfitTracker.INSTANCE.error()!=null) {
                        context.getSource().sendFeedback(Component.literal(ProfitTracker.INSTANCE.error()+". Reliable stats unavailable.")); return 0;
                    }
                    context.getSource().sendFeedback(Component.literal("Confirmed profit: "+ProfitDisplay.coins(s.profit())
                            +" | /h: "+ProfitDisplay.coins(s.perHour())+" | Incomplete claims: "+s.incomplete()));
                    return 1;
                }).then(ClientCommands.literal("hud").executes(context->{
                    GoofyConfig.INSTANCE.profitHudEnabled=!GoofyConfig.INSTANCE.profitHudEnabled;
                    GoofyConfig.save();
                    context.getSource().sendFeedback(Component.literal("Profit HUD "+(GoofyConfig.INSTANCE.profitHudEnabled?"shown":"hidden")));
                    return 1;
                })).then(ClientCommands.literal("reset").executes(context->{
                    boolean ok=ProfitTracker.INSTANCE.resetSession();
                    context.getSource().sendFeedback(Component.literal(ok?"Session stats reset. Open trade costs retained.":"Cannot reset: profit ledger has an error; file preserved."));
                    return ok?1:0;
                })).then(ClientCommands.literal("left").executes(context->side("LEFT")))
                  .then(ClientCommands.literal("right").executes(context->side("RIGHT")))
                  .then(ClientCommands.literal("scale").then(ClientCommands.argument("size",DoubleArgumentType.doubleArg(0.75,3.0))
                          .executes(context->{
                              GoofyConfig.INSTANCE.profitHudScale=DoubleArgumentType.getDouble(context,"size");
                              GoofyConfig.save();
                              return 1;
                          })))));
    }
    private static int side(String side) { GoofyConfig.INSTANCE.profitHudSide=side; GoofyConfig.save(); return 1; }
    private static void render(GuiGraphicsExtractor g) {
        Minecraft mc=Minecraft.getInstance();
        if (!GoofyConfig.INSTANCE.profitHudEnabled || mc.player==null || mc.options.hideGui) return;
        float scale=(float)(GoofyConfig.INSTANCE.profitHudScale/mc.getWindow().getGuiScale());
        int screenWidth=(int)(g.guiWidth()/scale), screenHeight=(int)(g.guiHeight()/scale);
        boolean left="LEFT".equals(GoofyConfig.INSTANCE.profitHudSide);
        var bounds=ProfitDisplay.bounds(screenWidth,screenHeight,left);
        if (!ProfitDisplay.worthDrawing(bounds.width(),bounds.height())) return;

        boolean scissored=false;
        g.pose().pushMatrix();
        try {
            g.pose().scale(scale,scale);
            g.enableScissor(bounds.x(),bounds.y(),bounds.x()+bounds.width(),bounds.y()+bounds.height());
            scissored=true;
            panel(g,mc,bounds);
        } finally {
            // Cleanup must happen even if reading the ledger or the engines throws, or every
            // later element on the screen inherits this panel's clip and transform.
            if (scissored) g.disableScissor();
            g.pose().popMatrix();
        }
    }

    private static void panel(GuiGraphicsExtractor g,Minecraft mc,ProfitDisplay.Bounds bounds) {
        var layout=ProfitDisplay.layout(bounds.height());
        var manager=FeatureManager.INSTANCE;
        var summary=ProfitTracker.INSTANCE.summary();
        String ledgerError=ProfitTracker.INSTANCE.error();
        int x=bounds.x(), y=bounds.y(), w=bounds.width();
        int inner=x+ProfitDisplay.PADDING, innerWidth=w-ProfitDisplay.PADDING*2;

        rounded(g,x,y,w,bounds.height(),10,SURFACE);

        // Header: the status reads as a word as well as a colour, never colour alone.
        String status=manager.status();
        int accent=switch (status) {
            case "RUNNING" -> GOOD;
            case "STOPPED" -> MUTED;
            case "PAUSED" -> WARNING;
            default -> CRITICAL;
        };
        text(g,"GOOFYADDONS",inner,y+13,TEXT);
        int pillWidth=mc.font.width(status)+12;
        rounded(g,x+w-pillWidth-ProfitDisplay.PADDING,y+9,pillWidth,17,5,PILL);
        text(g,status,x+w-pillWidth-ProfitDisplay.PADDING+6,y+13,accent);
        text(g,fit(mc,manager.modeLabel()+"  ·  SESSION",innerWidth),inner,y+30,MUTED);
        rule(g,inner,y+42,innerWidth);

        int row=y+ProfitDisplay.HEADER_HEIGHT;

        // The one number the panel leads with. Signed, so direction survives without colour.
        if (layout.hero()) {
            boolean known=ledgerError==null;
            text(g,summary.incomplete()>0 ? "Confirmed subtotal" : "Confirmed profit",inner,row,MUTED);
            String hero=known ? ProfitDisplay.signedCoins(summary.profit()) : "--";
            hero(g,hero,inner,row+12,!known ? MUTED : summary.profit()<0 ? CRITICAL : GOOD);
            row+=ProfitDisplay.HERO_HEIGHT;
        }

        if (layout.rate()) {
            Double perHour=summary.perHour();
            stat(g,mc,"Profit / hour",ledgerError==null && perHour!=null ? ProfitDisplay.signedCoins(perHour) : "--",
                    inner,row,innerWidth,TEXT);
            row+=ProfitDisplay.RATE_HEIGHT;
        }

        // What it is doing right now, above the detail rows because this is what gets watched.
        if (layout.task()) {
            rule(g,inner,row+3,innerWidth);
            text(g,"CURRENT TASK",inner,row+11,MUTED);
            text(g,fit(mc,manager.activity(),innerWidth),inner,row+23,accent);
            text(g,fit(mc,manager.taskItem(),innerWidth),inner,row+34,TEXT);
            row+=ProfitDisplay.TASK_HEIGHT;
        }

        if (layout.detailRows()>0) {
            rule(g,inner,row+3,innerWidth);
            row+=7;
            // Highest value first: whatever is cut is cut from the bottom.
            String[][] details={
                    {"Books",ledgerError==null ? ProfitDisplay.coins(summary.books()) : "--"},
                    {"General",ledgerError==null ? ProfitDisplay.coins(summary.general()) : "--"},
                    {"Claims / incomplete",summary.settlements()+" / "+summary.incomplete()},
                    {"Spendable",spendable()},
                    {"Reserved budget",ProfitDisplay.coins(CapitalManager.INSTANCE.committed())},
                    {"Tracked positions",Integer.toString(CapitalManager.INSTANCE.positionCount())},
                    {"Active time",ProfitDisplay.duration(summary.activeMillis())}};
            for (int i=0;i<layout.detailRows() && i<details.length;i++) {
                stat(g,mc,details[i][0],details[i][1],inner,row,innerWidth,TEXT);
                row+=ProfitDisplay.DETAIL_ROW_HEIGHT;
            }
        }

        if (layout.footer()) {
            String note=ledgerError;
            if (note==null && BazaarApi.latestFresh()==null) note="Price data stale - waiting";
            if (note==null && summary.incomplete()>0) note="Excludes incomplete claims";
            if (note==null && summary.activeMillis()<60000) note="Rate appears after 1 active minute";
            int footerY=y+bounds.height()-ProfitDisplay.FOOTER_HEIGHT+4;
            rule(g,inner,footerY-5,innerWidth);
            text(g,fit(mc,note==null ? "/goofyprofit hud | reset | left | right" : note,innerWidth),
                    inner,footerY,note==null ? MUTED : WARNING);
        }
    }

    private static String spendable() {
        double purse=new ScoreboardUtils().getPurse();
        return purse<0 ? "--" : ProfitDisplay.coins(CapitalManager.INSTANCE.available(purse));
    }

    /** A label/value pair: label in muted ink, value right-aligned in primary ink. */
    private static void stat(GuiGraphicsExtractor g,Minecraft mc,String label,String value,
                             int x,int y,int width,int valueColor) {
        int valueWidth=mc.font.width(value);
        text(g,fit(mc,label,Math.max(0,width-valueWidth-8)),x,y,MUTED);
        text(g,value,x+width-valueWidth,y,valueColor);
    }

    /** The hero figure, at an integer scale so the bitmap font stays crisp. */
    private static void hero(GuiGraphicsExtractor g,String value,int x,int y,int color) {
        g.pose().pushMatrix();
        try {
            g.pose().scale(2,2);
            g.text(Minecraft.getInstance().font,value,x/2,y/2,color,false);
        } finally {
            g.pose().popMatrix();
        }
    }

    private static void text(GuiGraphicsExtractor g,String value,int x,int y,int color) {
        g.text(Minecraft.getInstance().font,value,x,y,color,false);
    }

    /** Truncates to the available width so a long value can never collide with its label. */
    private static String fit(Minecraft mc,String value,int width) {
        if (width<=0) return "";
        return mc.font.width(value)<=width ? value
                : mc.font.plainSubstrByWidth(value,Math.max(0,width-12))+"...";
    }

    private static void rule(GuiGraphicsExtractor g,int x,int y,int width) {
        g.fill(x,y,x+width,y+1,RULE);
    }

    private static void rounded(GuiGraphicsExtractor g,int x,int y,int width,int height,int radius,int color) {
        int r=Math.min(radius,Math.min(width,height)/2);
        g.fill(x+r,y,x+width-r,y+height,color);
        g.fill(x,y+r,x+width,y+height-r,color);
        for (int i=0;i<r;i++) {
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(r-i-.5,2)));
            g.fill(x+inset,y+i,x+width-inset,y+i+1,color);
            g.fill(x+inset,y+height-i-1,x+width-inset,y+height-i,color);
        }
    }
}
