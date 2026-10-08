package com.goofy.goofyaddons.features.sessions;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Visible countdown and an explicit cancellation control while disconnected. */
public final class ScheduledRestScreen extends Screen {
    public ScheduledRestScreen(){super(Component.literal("GoofyAddons scheduled rest"));}
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Cancel schedule / return to menu"),button->onClose())
                .bounds(width/2-130,height/2+35,260,20).build());
    }
    @Override public void onClose(){SessionScheduler.INSTANCE.manualStop();}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta) {
        extractBackground(graphics,x,y,delta);
        graphics.centeredText(font,title,width/2,height/2-45,0xffffff);
        graphics.centeredText(font,SessionScheduler.INSTANCE.status(),width/2,height/2-15,0xa2f5ff);
        graphics.centeredText(font,"Keep Minecraft and your PC running to reconnect.",width/2,height/2+5,0xaaaaaa);
        super.extractRenderState(graphics,x,y,delta);
    }
}
