package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.Objects;

/** Opens an owned hotbar compactor without moving items or borrowing another hotbar slot. */
final class LiveCompactorOpening {
    private LiveCompactorOpening(){}
    static boolean open(SlotView device) {
        var client=Minecraft.getInstance();var menu=new LiveWorld().menu();
        if(client.player==null||client.gameMode==null||menu==null||!menu.cursorEmpty()
                ||!device.inPlayerInventory()||device.containerSlot()<0||device.containerSlot()>8)return false;
        var current=PersonalCompactors.detect(menu).stream().filter(s->s.inPlayerInventory()
            &&s.containerSlot()==device.containerSlot()&&Objects.equals(s.customId(),device.customId())
            &&Objects.equals(s.metadata().uuid(),device.metadata().uuid())&&Objects.equals(s.metadata().compactor(),device.metadata().compactor())).findFirst().orElse(null);
        if(current==null)return false;
        if(menu.title()!=null) {
            if(!CraftingExecutor.reusableMenu(menu))return false;
            client.player.closeContainer();
        }
        client.player.getInventory().setSelectedSlot(device.containerSlot());
        client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
        return true;
    }
}
