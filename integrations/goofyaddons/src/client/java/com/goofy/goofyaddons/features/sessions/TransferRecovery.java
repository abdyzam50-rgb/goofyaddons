package com.goofy.goofyaddons.features.sessions;

import com.goofy.goofyaddons.features.FeatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.world.scores.DisplaySlot;

/** Never reconnects a disconnected client; preserves ownership through an in-game server transfer. */
public final class TransferRecovery {
    public static final TransferRecovery INSTANCE=new TransferRecovery();
    private final TransferGate gate=new TransferGate();
    private TransferRecovery() {}
    public void register() {
        for(String warning:java.util.List.of("Scheduled Reboot","Game Update"))com.goofy.goofyaddons.event.ChatHook.onMessage(warning,text->{
            if(!FeatureManager.INSTANCE.canReloadConfig())com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.event("transfer","Server restart announced; trading will reconcile after any transfer.");
        });
    }
    public void worldChanged() {
        var manager=FeatureManager.INSTANCE;
        if(manager.canReloadConfig() || manager.hasSafetyBlock())return;
        gate.begin(System.currentTimeMillis());manager.pause();
    }
    public static boolean skyblockReady(Minecraft mc) {
        if(mc.player==null || mc.level==null || !mc.player.containerMenu.getCarried().isEmpty())return false;
        var objective=mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective!=null && objective.getDisplayName().getString().toUpperCase(java.util.Locale.ROOT).contains("SKYBLOCK")
            && new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse()>=0;
    }
    public boolean tick() {
        var mc=Minecraft.getInstance();var manager=FeatureManager.INSTANCE;long now=System.currentTimeMillis();
        if(!gate.pending() && !manager.canReloadConfig() && !manager.hasSafetyBlock() && (mc.player==null || mc.level==null))worldChanged();
        if(!gate.pending())return false;
        if(mc.gui.screen() instanceof DisconnectedScreen){gate.cancel();manager.stop();return true;}
        var result=gate.observe(!manager.canReloadConfig(),manager.hasSafetyBlock(),skyblockReady(mc)&&mc.gui.screen()==null,now);
        switch(result) {
            case RESUME -> {manager.restartAfterTransfer();com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.event("transfer","Server transfer complete; rechecking saved inventory, storage and Bazaar orders before trading resumes.");}
            case TIMED_OUT -> manager.safetyPause("Server transfer did not reach a readable SkyBlock world; reconnect or resume manually");
            default -> {}
        }
        return result!=TransferGate.Result.CANCELLED;
    }
}
