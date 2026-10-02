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
    private static final int TEXT=0xFFE8EDF5, MUTED=0xFF9CAABE, GREEN=0xFF72DBAF, RED=0xFFF08087;
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
        var bounds=ProfitDisplay.bounds(screenWidth,screenHeight,"LEFT".equals(GoofyConfig.INSTANCE.profitHudSide));
        if (bounds.width()<140 || bounds.height()<80) return;
        g.pose().pushMatrix();
        g.pose().scale(scale,scale);
        int x=bounds.x(),y=bounds.y(),w=bounds.width();
        rounded(g,x,y,w,bounds.height(),10,0xFF161D29);
        g.enableScissor(x,y,x+w,y+bounds.height());
        var manager=FeatureManager.INSTANCE;
        var s=ProfitTracker.INSTANCE.summary();
        text(g,"GOOFYADDONS",x+12,y+12,TEXT);
        String status=manager.status();
        int accent=status.equals("RUNNING")?GREEN:status.equals("STOPPED")?MUTED:RED;
        int statusWidth=mc.font.width(status)+12;
        rounded(g,x+w-statusWidth-10,y+8,statusWidth,18,5,0xFF263244);
        text(g,status,x+w-statusWidth-4,y+13,accent);
        text(g,manager.modeLabel()+"  /  SESSION",x+12,y+29,MUTED);
        g.fill(x+12,y+44,x+w-12,y+45,0xFF334054);
        boolean compact=bounds.height()<232;
        int spacing=compact?11:16, profitSpacing=compact?14:18;
        int row=y+(compact?50:54);
        row(g,s.incomplete()>0?"Confirmed subtotal":"Confirmed profit",ProfitTracker.INSTANCE.error()==null?ProfitDisplay.coins(s.profit()):"--",x,row,w,s.profit()<0?RED:GREEN); row+=profitSpacing;
        row(g,"Profit / hour",ProfitTracker.INSTANCE.error()==null?ProfitDisplay.coins(s.perHour()):"--",x,row,w,s.profit()<0?RED:GREEN); row+=profitSpacing;
        row(g,"Books",ProfitTracker.INSTANCE.error()==null?ProfitDisplay.coins(s.books()):"--",x,row,w,TEXT); row+=spacing;
        row(g,"General",ProfitTracker.INSTANCE.error()==null?ProfitDisplay.coins(s.general()):"--",x,row,w,TEXT); row+=spacing;
        row(g,"Active time",ProfitDisplay.duration(s.activeMillis()),x,row,w,TEXT); row+=spacing;
        row(g,"Claims / incomplete",s.settlements()+" / "+s.incomplete(),x,row,w,s.incomplete()>0?RED:TEXT); row+=spacing;
        row(g,"Tracked positions",Integer.toString(CapitalManager.INSTANCE.positionCount()),x,row,w,TEXT); row+=spacing;
        row(g,"Committed",ProfitDisplay.coins(CapitalManager.INSTANCE.committed()),x,row,w,TEXT); row+=spacing;
        double purse=new ScoreboardUtils().getPurse();
        row(g,"Spendable",purse<0?"--":ProfitDisplay.coins(CapitalManager.INSTANCE.available(purse)),x,row,w,TEXT); row+=spacing;
        row(g,"Price data",BazaarApi.latestFresh()==null?"Waiting / stale":"Fresh",x,row,w,MUTED); row+=19;
        String warning=ProfitTracker.INSTANCE.error();
        if (warning==null && s.incomplete()>0) warning="Profit excludes incomplete claims";
        if (warning==null && s.activeMillis()<60000) warning="Rate appears after 1 active minute";
        text(g,fit(mc,warning==null?"/goofyprofit hud | reset | left | right":warning,w-24),x+12,y+bounds.height()-15,warning==null?MUTED:RED);
        g.disableScissor();
        var task=ProfitDisplay.taskBounds(screenWidth,screenHeight,"LEFT".equals(GoofyConfig.INSTANCE.profitHudSide));
        rounded(g,task.x(),task.y(),task.width(),task.height(),10,0xFF161D29);
        g.enableScissor(task.x(),task.y(),task.x()+task.width(),task.y()+task.height());
        text(g,"CURRENT TASK",task.x()+12,task.y()+10,MUTED);
        text(g,fit(mc,manager.activity(),task.width()-24),task.x()+12,task.y()+26,accent);
        text(g,fit(mc,manager.taskItem(),task.width()-24),task.x()+12,task.y()+42,TEXT);
        g.disableScissor();
        g.pose().popMatrix();
    }
    private static void row(GuiGraphicsExtractor g,String label,String value,int x,int y,int width,int color) {
        text(g,label,x+12,y,MUTED);
        text(g,value,x+width-12-Minecraft.getInstance().font.width(value),y,color);
    }
    private static void text(GuiGraphicsExtractor g,String value,int x,int y,int color) { g.text(Minecraft.getInstance().font,value,x,y,color,false); }
    private static String fit(Minecraft mc,String value,int width) {
        return mc.font.width(value)<=width?value:mc.font.plainSubstrByWidth(value,Math.max(0,width-12))+"...";
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
