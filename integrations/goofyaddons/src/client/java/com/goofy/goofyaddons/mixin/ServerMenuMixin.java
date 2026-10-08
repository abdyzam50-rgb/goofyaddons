package com.goofy.goofyaddons.mixin;

import com.goofy.goofyaddons.menu.ServerMenuMirror;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ServerMenuMixin {
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void goofyContent(ClientboundContainerSetContentPacket packet,CallbackInfo ci){ServerMenuMirror.content(packet.containerId(),packet.items(),packet.carriedItem());}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void goofySlot(ClientboundContainerSetSlotPacket packet,CallbackInfo ci){ServerMenuMirror.slot(packet.getContainerId(),packet.getSlot(),packet.getItem());}
    @Inject(method="handleSetCursorItem",at=@At("TAIL"))
    private void goofyCursor(ClientboundSetCursorItemPacket packet,CallbackInfo ci){ServerMenuMirror.cursor(packet.contents());}
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL"))
    private void goofyInventory(ClientboundSetPlayerInventoryPacket packet,CallbackInfo ci){ServerMenuMirror.inventory(packet.slot(),packet.contents());}
    @Inject(method="handleOpenScreen",at=@At("HEAD"))
    private void goofyOpen(ClientboundOpenScreenPacket packet,CallbackInfo ci){
        // HEAD can run on the network thread before vanilla reschedules; only clear on client thread.
        if(net.minecraft.client.Minecraft.getInstance().isSameThread())ServerMenuMirror.clear();
    }
    @Inject(method="handleContainerClose",at=@At("TAIL"))
    private void goofyClose(ClientboundContainerClosePacket packet,CallbackInfo ci){ServerMenuMirror.clear();}
}
