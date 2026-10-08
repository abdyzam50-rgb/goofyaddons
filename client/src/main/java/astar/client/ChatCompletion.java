package astar.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Makes the chat box understand {@code .A*} commands the way it understands slash commands:
 * the suggestion list and Tab completion ({@code CommandSuggestionsMixin}), arguments coloured
 * as you type, the commands kept in the game's command history across restarts
 * ({@code ChatComponentMixin}), and Up/Down stepping through only the {@code .A*} commands
 * that start with what's typed ({@code ChatScreenMixin}).
 */
public final class ChatCompletion {

    /** The prefixes, longest first so {@code .astar} isn't read as {@code .a} plus more. */
    private static final List<String> PREFIXES = List.of(".astar", ".a*");
    /** How far the crosshair looks for a block to suggest as coordinates. */
    private static final double LOOK_RANGE = 256;

    /** The {@code .A*} commands with no {@code astar} word in front, for parsing chat text. */
    private static CommandDispatcher<FabricClientCommandSource> commands;

    private ChatCompletion() {}

    /** Takes the commands under {@code astar} so chat text after the prefix parses directly. */
    static void use(CommandNode<FabricClientCommandSource> astar) {
        commands = new CommandDispatcher<>();
        for (CommandNode<FabricClientCommandSource> child : astar.getChildren()) {
            commands.getRoot().addChild(child);
        }
    }

    /**
     * Where the command starts in chat text that's an {@code .A*} command (after the prefix and
     * its space), or -1 if it isn't one.
     */
    public static int commandStart(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (lower.startsWith(prefix + " ")) {
                int i = prefix.length();
                while (i < text.length() && text.charAt(i) == ' ') {
                    i++;
                }
                return i;
            }
        }
        return -1;
    }

    /** Parses chat text that {@link #commandStart} says is an {@code .A*} command. */
    public static ParseResults<FabricClientCommandSource> parse(String text, int start,
            FabricClientCommandSource source) {
        StringReader reader = new StringReader(text);
        reader.setCursor(start);
        return commands.parse(reader, source);
    }

    public static CompletableFuture<Suggestions> suggest(
            ParseResults<FabricClientCommandSource> parse, int cursor) {
        return commands.getCompletionSuggestions(parse, cursor);
    }

    /**
     * Suggests {@code .A* } while a chat line is still the start of the prefix ({@code .},
     * {@code .a}, {@code .A*}), or null if it isn't.
     */
    public static CompletableFuture<Suggestions> suggestPrefix(String typed) {
        if (typed.isEmpty() || typed.indexOf(' ') >= 0) {
            return null;
        }
        String lower = typed.toLowerCase(Locale.ROOT);
        if (!".a*".startsWith(lower) && !".astar".startsWith(lower)) {
            return null;
        }
        SuggestionsBuilder b = new SuggestionsBuilder(typed, 0);
        b.suggest(".A* ");
        return b.buildFuture();
    }

    /** Whether chat text is an {@code .A*} command, or the start of one. */
    public static boolean isDotText(String text) {
        return text.startsWith(".");
    }

    /**
     * Where Up ({@code dir} -1) or Down (+1) goes in the chat history from {@code pos}, among
     * entries that start with {@code typed} (ignoring case), each command once (its newest
     * time). Returns -1 when there's nothing further up and {@code history.size()} past the
     * newest.
     */
    public static int step(ArrayListDeque<String> history, int pos, int dir, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        int size = history.size();
        for (int i = pos + dir; i >= 0 && i < size; i += dir) {
            String entry = history.get(i);
            if (entry.toLowerCase(Locale.ROOT).startsWith(lower) && newest(history, i)) {
                return i;
            }
        }
        return dir < 0 ? -1 : size;
    }

    private static boolean newest(ArrayListDeque<String> history, int i) {
        String entry = history.get(i);
        for (int j = i + 1; j < history.size(); j++) {
            if (history.get(j).equals(entry)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Suggests the block the crosshair is on as the coordinates from {@code axis} (0 x, 1 y,
     * 2 z) on: where you'd stand on it, so {@code .A* } and Tab walks to where you're looking.
     */
    static CompletableFuture<Suggestions> suggestLookedAt(
            CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b, int axis) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            HitResult hit = client.player.pick(LOOK_RANGE, 1, false);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos p = block.getBlockPos().above();
                int[] xyz = {p.getX(), p.getY(), p.getZ()};
                StringBuilder s = new StringBuilder();
                for (int i = axis; i < 3; i++) {
                    s.append(i > axis ? " " : "").append(xyz[i]);
                }
                if (s.toString().startsWith(b.getRemaining())) {
                    b.suggest(s.toString());
                }
            }
        }
        return b.buildFuture();
    }
}
