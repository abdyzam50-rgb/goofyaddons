package astar.client.mixin;

import astar.client.ChatCompletion;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives {@code .A*} commands in the chat box what slash commands get: the suggestion list as
 * you type, Tab to fill one in, the grey hint of what comes next, and coloured arguments. The
 * game only does that for text starting with {@code /}; this parses text starting with
 * {@code .A*} or {@code .Astar} against the A* commands instead ({@link ChatCompletion}).
 * While the line is still the start of the prefix ({@code .}, {@code .a}), Tab fills in
 * {@code .A* }.
 */
@Mixin(CommandSuggestions.class)
abstract class CommandSuggestionsMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private int commandUsagePosition;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private CommandSuggestions.SuggestionsList suggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;

    @Shadow
    private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parse,
            Suggestions found) {}

    @Shadow
    private void recomputeUsageBoxWidth() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void astar$dotCommands(CallbackInfo ci) {
        String text = input.getValue();
        int cursor = input.getCursorPosition();
        int start = ChatCompletion.commandStart(text);
        CompletableFuture<Suggestions> prefix = start < 0 && cursor == text.length()
                ? ChatCompletion.suggestPrefix(text) : null;
        if (start < 0 && prefix == null || minecraft.player == null) {
            return;
        }
        ci.cancel();
        if (currentParse != null && !currentParse.getReader().getString().equals(text)) {
            currentParse = null;
        }
        currentParseIsCommand = false;
        currentParseIsMessage = false;
        if (!keepSuggestions) {
            input.setSuggestion(null);
            suggestions = null;
        }
        commandUsage.clear();
        if (prefix != null) {
            // Like ordinary chat: ready for Tab, but no list popping up over every "." typed.
            pendingSuggestions = prefix;
            recomputeUsageBoxWidth();
            commandUsagePosition = 0;
            return;
        }
        // Fabric makes the game's suggestion provider the client command source.
        var source = (FabricClientCommandSource) minecraft.player.connection
                .getSuggestionsProvider();
        if (currentParse == null) {
            currentParse = (ParseResults) ChatCompletion.parse(text, start, source);
        }
        if (cursor < start) {
            pendingSuggestions = null;
            return;
        }
        if (suggestions == null || !keepSuggestions) {
            ParseResults<ClientSuggestionProvider> parse = currentParse;
            pendingSuggestions = ChatCompletion.suggest((ParseResults) parse, cursor);
            pendingSuggestions.thenAccept(found -> {
                if (pendingSuggestions.isDone()) {
                    updateUsageInfo(parse, found);
                }
            });
        }
    }
}
