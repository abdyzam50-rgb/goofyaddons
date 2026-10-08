package astar.client.mixin;

import astar.client.ChatCompletion;
import net.minecraft.client.CommandHistory;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps {@code .A*} commands in the game's command history (command_history.txt) next to
 * slash commands, so Up brings them back after a restart or a change of server.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

    @Shadow @Final private CommandHistory commandHistory;

    @Inject(method = "addRecentChat", at = @At("TAIL"))
    private void astar$keepDotCommand(String message, CallbackInfo ci) {
        if (ChatCompletion.commandStart(message + " ") >= 0) {
            commandHistory.addCommand(message);
        }
    }
}
