package astar.client.mixin;

import astar.client.ChatCompletion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.ArrayListDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With {@code .} or the start of an {@code .A*} command typed, Up and Down step through only
 * the history that starts with it (each command once), like a shell's history search; with
 * the box empty they go through everything, as usual.
 */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {

    @Shadow private String historyBuffer;
    @Shadow private int historyPos;
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;

    @Inject(method = "moveInHistory", at = @At("HEAD"), cancellable = true)
    private void astar$searchHistory(int dir, CallbackInfo ci) {
        ArrayListDeque<String> history = Minecraft.getInstance().gui.hud.getChat().getRecentChat();
        int max = history.size();
        boolean browsing = historyPos >= 0 && historyPos < max;
        String typed = browsing ? historyBuffer : input.getValue();
        if (!ChatCompletion.isDotText(typed)) {
            return;
        }
        ci.cancel();
        int next = ChatCompletion.step(history, browsing ? historyPos : max, dir, typed);
        if (next < 0) {
            return;
        }
        if (next >= max) {
            if (browsing) {
                historyPos = max;
                input.setValue(historyBuffer);
            }
            return;
        }
        if (!browsing) {
            historyBuffer = input.getValue();
        }
        input.setValue(history.get(next));
        commandSuggestions.setAllowSuggestions(false);
        historyPos = next;
    }
}
