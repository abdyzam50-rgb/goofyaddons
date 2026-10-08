package astar.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import astar.pathing.Tuning;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code .A* config}: the player's settings ({@link Tuning}), seen and changed in the game
 * and kept in config/astar.toml ({@link AstarConfig}).
 *
 * <ul>
 *   <li>{@code .A* config}: the sections, the settings changed, and where the file is.
 *   <li>{@code .A* config list [section]}: every setting in a section with its value.
 *   <li>{@code .A* config get <name>}: one setting, what it does, its default and range.
 *   <li>{@code .A* config set <name> <value>}: changes it and writes the file.
 *   <li>{@code .A* config reset <name|section|all>}: back to the defaults.
 *   <li>{@code .A* config reload}: reads the file again after editing it by hand.
 * </ul>
 *
 * A name is "section.name", or just the name.
 */
final class ConfigCommand {
    private ConfigCommand() {}

    static LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return literal("config")
                .executes(c -> overview(c.getSource()))
                .then(literal("list")
                        .executes(c -> overview(c.getSource()))
                        .then(argument("section", StringArgumentType.word())
                                .suggests(ConfigCommand::suggestSections)
                                .executes(c -> list(c.getSource(),
                                        StringArgumentType.getString(c, "section")))))
                .then(literal("get")
                        .then(argument("name", StringArgumentType.word())
                                .suggests(ConfigCommand::suggestNames)
                                .executes(c -> get(c.getSource(),
                                        StringArgumentType.getString(c, "name")))))
                .then(literal("set")
                        .then(argument("name", StringArgumentType.word())
                                .suggests(ConfigCommand::suggestNames)
                                .executes(c -> get(c.getSource(),
                                        StringArgumentType.getString(c, "name")))
                                .then(argument("value", StringArgumentType.greedyString())
                                        .suggests(ConfigCommand::suggestValues)
                                        .executes(c -> set(c.getSource(),
                                                StringArgumentType.getString(c, "name"),
                                                StringArgumentType.getString(c, "value"))))))
                .then(literal("reset")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    b.suggest("all");
                                    Tuning.sections().keySet().forEach(b::suggest);
                                    return suggestNames(c, b);
                                })
                                .executes(c -> reset(c.getSource(),
                                        StringArgumentType.getString(c, "name")))))
                .then(literal("reload").executes(c -> reload(c.getSource())));
    }

    private static int overview(FabricClientCommandSource source) {
        AstarConfig.load();
        source.sendFeedback(Component.literal("Settings, kept in ").append(
                Component.literal(AstarConfig.file().toString()).withStyle(ChatFormatting.GRAY))
                .append(":"));
        for (var e : Tuning.sections().entrySet()) {
            long n = Tuning.all().stream().filter(o -> o.section.equals(e.getKey())).count();
            long changed = Tuning.all().stream()
                    .filter(o -> o.section.equals(e.getKey()) && o.changed()).count();
            source.sendFeedback(Component.literal("  " + e.getKey()).withStyle(
                    ChatFormatting.AQUA).append(Component.literal(" (" + n
                    + (n == 1 ? " setting" : " settings")
                    + (changed > 0 ? ", " + changed + " changed" : "") + ")")
                    .withStyle(ChatFormatting.GRAY)));
        }
        List<Tuning.Option> changed = Tuning.all().stream().filter(Tuning.Option::changed)
                .toList();
        if (!changed.isEmpty()) {
            source.sendFeedback(Component.literal("Changed:"));
            for (Tuning.Option o : changed) {
                source.sendFeedback(line(o));
            }
        }
        source.sendFeedback(Component.literal(".A* config list <section>, get, set <name>"
                + " <value>, reset <name|section|all>, reload").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int list(FabricClientCommandSource source, String section) {
        AstarConfig.load();
        String s = section.toLowerCase(Locale.ROOT);
        String about = Tuning.sections().get(s);
        if (about == null) {
            source.sendError(Component.literal("No section called " + section + ". There's "
                    + String.join(", ", Tuning.sections().keySet()) + "."));
            return 0;
        }
        source.sendFeedback(Component.literal("[" + s + "] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(about).withStyle(ChatFormatting.GRAY)));
        for (Tuning.Option o : Tuning.all()) {
            if (o.section.equals(s)) {
                source.sendFeedback(line(o));
            }
        }
        return 1;
    }

    private static int get(FabricClientCommandSource source, String name) {
        AstarConfig.load();
        Tuning.Option o = find(source, name);
        if (o == null) {
            return 0;
        }
        source.sendFeedback(line(o));
        source.sendFeedback(Component.literal("  " + o.help).withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal("  Default " + o.defaultText() + "; "
                + o.range() + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int set(FabricClientCommandSource source, String name, String value) {
        AstarConfig.load();
        Tuning.Option o = find(source, name);
        if (o == null) {
            return 0;
        }
        String note;
        try {
            note = o.set(value);
        } catch (IllegalArgumentException e) {
            source.sendError(Component.literal(e.getMessage() + "."));
            return 0;
        }
        saved(source, AstarConfig.save());
        source.sendFeedback(line(o).append(Component.literal(" " + when(o.effect))
                .withStyle(ChatFormatting.GRAY)));
        if (note != null) {
            source.sendFeedback(Component.literal(note + ".").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int reset(FabricClientCommandSource source, String name) {
        AstarConfig.load();
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("all")) {
            Tuning.resetAll();
            saved(source, AstarConfig.save());
            source.sendFeedback(Component.literal("Every setting is back to its default."));
            return 1;
        }
        if (Tuning.sections().containsKey(n)) {
            Tuning.all().stream().filter(o -> o.section.equals(n)).forEach(Tuning.Option::reset);
            saved(source, AstarConfig.save());
            source.sendFeedback(Component.literal("[" + n + "] is back to its defaults."));
            return 1;
        }
        Tuning.Option o = find(source, name);
        if (o == null) {
            return 0;
        }
        o.reset();
        saved(source, AstarConfig.save());
        source.sendFeedback(line(o).append(Component.literal(" (the default) " + when(o.effect))
                .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int reload(FabricClientCommandSource source) {
        List<String> problems = AstarConfig.reload();
        long changed = Tuning.all().stream().filter(Tuning.Option::changed).count();
        source.sendFeedback(Component.literal("Read " + AstarConfig.file().getFileName() + ": "
                + changed + (changed == 1 ? " setting" : " settings") + " off the default."));
        for (String p : problems) {
            source.sendFeedback(Component.literal(p).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static void saved(FabricClientCommandSource source, boolean ok) {
        if (!ok) {
            source.sendError(Component.literal("Couldn't write " + AstarConfig.file()
                    + "; the change holds for this game only."));
        }
    }

    /** When a change to a setting with this effect shows. */
    private static String when(Tuning.Effect effect) {
        return switch (effect) {
            case ROUTES -> "(from the next .A*; the map's graph is redone for it)";
            case HOPS -> "(from the next teleporting .A*)";
            case NOW -> "(now)";
            case NEXT_ROUTE -> "(from the next .A*)";
        };
    }

    private static MutableComponent line(Tuning.Option o) {
        MutableComponent c = Component.literal("  " + o.key() + " = ")
                .append(Component.literal(o.text()).withStyle(o.changed() ? ChatFormatting.GOLD
                        : ChatFormatting.WHITE));
        if (o.changed()) {
            c.append(Component.literal(" (default " + o.defaultText() + ")")
                    .withStyle(ChatFormatting.GRAY));
        }
        return c;
    }

    private static Tuning.Option find(FabricClientCommandSource source, String name) {
        Tuning.Option o = Tuning.find(name);
        if (o == null) {
            source.sendError(Component.literal("No setting called " + name + ". .A* config"
                    + " list <section> shows them."));
        }
        return o;
    }

    private static CompletableFuture<Suggestions> suggestSections(
            CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        Tuning.sections().keySet().forEach(b::suggest);
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNames(
            CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        String typed = b.getRemaining().toLowerCase(Locale.ROOT);
        for (Tuning.Option o : Tuning.all()) {
            if (o.key().startsWith(typed) || o.name.startsWith(typed)) {
                b.suggest(o.key());
            }
        }
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(
            CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        Tuning.Option o = Tuning.find(StringArgumentType.getString(c, "name"));
        if (o instanceof Tuning.Choice ch) {
            ch.choices.forEach(b::suggest);
        } else if (o instanceof Tuning.Flag) {
            b.suggest("true");
            b.suggest("false");
        } else if (o != null) {
            b.suggest(o.text());
            if (o.changed()) {
                b.suggest(o.defaultText());
            }
        }
        return b.buildFuture();
    }
}
