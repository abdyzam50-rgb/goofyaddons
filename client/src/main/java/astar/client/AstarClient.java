package astar.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import astar.client.draw.OverlayColour;
import astar.client.ui.Theme;
import astar.movement.Scenario;
import astar.movement.exec.AimController;
import astar.pathing.Easing;
import astar.pathing.Tuning;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Records movement traces for calibrating the route executor's physics against the game.
 *
 * <ul>
 *   <li>{@code /trace start} records while you play; {@code /trace stop} ends it.
 *   <li>{@code /trace play <script>} stands still until you're on the ground, then plays a
 *       scripted run of inputs and records it.
 *   <li>{@code /trace list} names the scripts.
 * </ul>
 * Traces go to {@code .minecraft/astar-traces/}; your own scripts go in
 * {@code .minecraft/config/astar-scenarios/<name>.txt}.
 *
 * <p>{@code /trace course <x> <y> <z>} builds a flat test course there ({@link TestCourse}) in a
 * world with cheats on; after that, scripts with an {@code at} line start from their place on
 * the course.
 *
 * <p>A script with a {@code from} line teleports the player to that place in the world first
 * (commands must be allowed), which is how the executor's runs are played in a real map.
 *
 * <p>For unattended runs, {@code -Dastar.trace.commands="difficulty peaceful;time set day"}
 * runs those commands once a world is joined, {@code -Dastar.trace.course=x,y,z} builds the
 * course first, and {@code -Dastar.trace.autoplay=walk,sprint} plays those scripts one
 * after another, and {@code -Dastar.trace.autoquit=true} closes the game when they're done.
 * {@code -Dastar.shapes.dump=<file>} writes every block state's collision shape there
 * ({@link ShapeDump}) on the first tick.
 *
 * <p>{@code .A* <x> <y> <z>} walks the player there by itself ({@link Navigator}); any
 * movement key or mouse turn pauses it, {@code .A* resume} carries on and {@code .A* stop}
 * ends it, and {@code .A* show} hides or shows the route drawn in the world ({@code .A*
 * show <colour>} picks its colour: red, orange, yellow, green, cyan, blue, purple, pink, white)
 * ({@link RouteOverlay}). Routes can reach past what the game has loaded, through chunks saved
 * while exploring and the bundled Dwarven Mines map ({@link Places}); {@code .A* cache}
 * says what's saved, {@code on}/{@code off} turn saving on or off and {@code forget} drops the
 * saved chunks of the place you're in. {@code .A* warp <x> <y> <z>} also etherwarps where
 * that's quicker ({@link Warps}, {@link WarpCast}), {@code .A* it <x> <y> <z>} casts Instant
 * Transmission ({@link TransmitCast}) and {@code .A* aotv <x> <y> <z>} does both: with an
 * Aspect of the Void in the hotbar, or in single player, where a {@code /tp} stands in for the
 * cast; {@code -Dastar.goto.warp=warp|it|aotv} makes the unattended trips below do the same.
 * {@code -Dastar.goto=x,y,z} does the same unattended, after the scripts (or
 * {@code x,y,z;x,y,z;...}: one trip after another, with commands such as {@code tp @s x y z}
 * between them), and
 * autoquit waits for it; {@code -Dastar.goto.commands="40:tp @s ~ ~ ~-3"} runs commands so
 * many ticks into it, to disturb it.
 */
public final class AstarClient implements ClientModInitializer {

    /** How long a script waits for the player to stand still before giving up. */
    private static final int MAX_WAIT_TICKS = 100;

    private TraceRecorder recorder;
    private Scenario scenario;
    private ScriptedInput scripted;
    private int frame;
    private int waited;
    private float startYaw;
    private final ArrayDeque<String> autoplay = new ArrayDeque<>();
    private final boolean autoquit = Boolean.getBoolean("astar.trace.autoquit");
    private int ticksInWorld;
    /** Ticks between looks at whether to warm up .A*'s copy ({@link LiveMap#warmUp}). */
    private static final int WARM_EVERY = 20;
    private int warmTicks;
    private TestCourse course;
    private String pendingCourse = System.getProperty("astar.trace.course", "");
    private String pendingCommands = System.getProperty("astar.trace.commands", "");
    private String pendingShapes = System.getProperty("astar.shapes.dump", "");
    private String pendingGoto = System.getProperty("astar.goto", "");
    /**
     * Commands to run while an unattended goto drives, each after so many ticks of it, to
     * disturb it: {@code 40:tp @s ~ ~ ~-3;80:fill 10 64 10 10 65 10 stone}. {@code 60:screenshot
     * name} saves a screenshot instead, {@code 30:thirdperson} (or {@code thirdperson front})
     * moves the camera behind (or in front of) the player, {@code hidegui} hides the HUD and
     * {@code screen <page>} (and {@code closescreen}) opens (and closes) the mod's window
     * ({@link AstarScreen}) on that page. {@code chat <text>} sends that chat line, {@code type
     * <text>} opens chat with it typed in and {@code key tab|up|down|enter}
     * presses that key there.
     */
    private final List<String> gotoCommands = new ArrayList<>(List.of(
            System.getProperty("astar.goto.commands", "").split(";")));
    private int gotoTicks;
    private Navigator navigator;
    private final TradingPathfinder tradingPathfinder=new TradingPathfinder(this);
    private final RouteOverlay overlay = new RouteOverlay(() -> navigator);
    /** Opens the mod's window ({@link AstarScreen}). */
    final KeyMapping routeKey = new KeyMapping("key.astar.route_screen",
            InputConstants.KEY_G, KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("astar", "astar")));
    private int courseTicks;
    /**
     * Where the script's {@code at} or {@code from} line puts the player, or null to start
     * where it is.
     */
    private Vec3 target;

    /** The chat commands, typed as {@code .A* ...} or {@code .Astar ...} rather than with a slash. */
    private final CommandDispatcher<FabricClientCommandSource> dotCommands =
            new CommandDispatcher<>();
    private static final String USAGE = "A* commands (.A* or .Astar): .A* <x> <y> <z>, stop,"
            + " resume, show [colour|look], map [name], cache, config, hand, ease,"
            + " warp|it|aotv <x> <y> <z>, goofyaddon.";

    /** What follows {@code .A*} or {@code .Astar} in a chat message, or null if it's chat. */
    static String dotCommand(String message) {
        String m = message.strip();
        String lower = m.toLowerCase(Locale.ROOT);
        for (String prefix : new String[] {".astar", ".a*"}) {
            if (lower.startsWith(prefix)
                    && (m.length() == prefix.length() || m.charAt(prefix.length()) == ' ')) {
                return m.substring(prefix.length()).strip();
            }
        }
        return null;
    }

    /** Runs a {@code .A*} command (what follows the prefix), saying in chat if it's wrong. */
    void runDotCommand(Minecraft client, String rest) {
        if (client.getConnection() == null) {
            return;
        }
        // Fabric makes the game's own suggestion provider the client command source.
        var source = (FabricClientCommandSource) client.getConnection().getSuggestionsProvider();
        try {
            dotCommands.execute(rest.isEmpty() ? "astar" : "astar " + rest, source);
        } catch (CommandSyntaxException e) {
            source.sendError(Component.literal("\".A* " + rest + "\" isn't something I know ("
                    + e.getRawMessage().getString() + "). Type .A* for the list."));
        } catch (RuntimeException e) {
            source.sendError(Component.literal(".A* " + rest + " failed: " + e));
        }
    }

    /**
     * Runs a step of {@code astar.goto.commands}: {@code goto ...} or {@code .A* ...} as an
     * A* command, anything else as a game command.
     */
    private void runStep(Minecraft client, String command) {
        String rest = dotCommand(command);
        if (rest == null && command.startsWith("goto")) {
            rest = command.substring(4).strip();
        }
        if (rest != null) {
            runDotCommand(client, rest);
        } else {
            client.getConnection().sendCommand(command);
        }
    }

    @Override
    public void onInitializeClient() {
        // So the first .A*'s walk isn't laid out by code the JVM hasn't compiled yet.
        WalkWarmUp.start();
        com.goofy.goofyaddons.features.access.BazaarNpcAccess.installPathfinder(tradingPathfinder);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) ->
                dispatcher.register(literal("trace")
                        .then(literal("start").executes(c -> start(feedback(c.getSource()))))
                        .then(literal("stop").executes(c -> stop(feedback(c.getSource()))))
                        .then(literal("list").executes(c -> list(feedback(c.getSource()))))
                        .then(literal("course").then(argument("x", IntegerArgumentType.integer())
                                .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                .executes(c -> buildCourse(feedback(c.getSource()), new BlockPos(
                                        IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "y"),
                                        IntegerArgumentType.getInteger(c, "z"))))))))
                        .then(literal("play").then(argument("script", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    scriptNames().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(c -> play(feedback(c.getSource()),
                                        StringArgumentType.getString(c, "script")))))));
        dotCommands.register(literal("astar")
                        .executes(c -> say(c.getSource(), USAGE))
                        .then(literal("stop").executes(c -> stopGoto(feedback(c.getSource()))))
                        .then(literal("resume").executes(c -> resumeGoto(
                                feedback(c.getSource()))))
                        .then(literal("cache")
                                .executes(c -> say(c.getSource(), Places.describe()))
                                .then(literal("on").executes(c -> {
                                    Places.setRecording(true);
                                    return say(c.getSource(), Places.describe());
                                }))
                                .then(literal("off").executes(c -> {
                                    Places.setRecording(false);
                                    return say(c.getSource(), Places.describe());
                                }))
                                .then(literal("maps").executes(c -> maps(c.getSource())))
                                .then(literal("forget").executes(c -> say(c.getSource(),
                                        Places.forget()))
                                        .then(argument("map", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    Places.maps().forEach(m -> b.suggest(
                                                            m.name()));
                                                    return b.buildFuture();
                                                })
                                                .executes(c -> say(c.getSource(),
                                                        Places.forget(StringArgumentType
                                                                .getString(c, "map"))))))
                                .then(literal("rename").then(argument("name",
                                        StringArgumentType.word()).executes(c -> say(
                                                c.getSource(), Places.rename(StringArgumentType
                                                        .getString(c, "name")))))))
                        .then(literal("hand")
                                .executes(c -> say(c.getSource(), describeHand()))
                                .then(literal("left").executes(c -> hand(c.getSource(),
                                        "left")))
                                .then(literal("right").executes(c -> hand(c.getSource(),
                                        "right"))))
                        .then(ConfigCommand.build())
                        .then(com.goofy.goofyaddons.commands.GoofyCommands.root())
                        .then(literal("ease")
                                .executes(c -> say(c.getSource(), describeEase()))
                                .then(argument("turns", StringArgumentType.word())
                                        .suggests(AstarClient::suggestEasings)
                                        .executes(c -> ease(c.getSource(),
                                                StringArgumentType.getString(c, "turns"),
                                                AstarConfig.flicks().label))
                                        .then(argument("flicks", StringArgumentType.word())
                                                .suggests(AstarClient::suggestEasings)
                                                .executes(c -> ease(c.getSource(),
                                                        StringArgumentType.getString(c, "turns"),
                                                        StringArgumentType.getString(c,
                                                                "flicks"))))))
                        .then(literal("map")
                                .executes(c -> say(c.getSource(), Places.describe()))
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Places.maps().forEach(m -> b.suggest(m.name()));
                                            return b.buildFuture();
                                        })
                                        .executes(c -> say(c.getSource(), Places.choose(
                                                StringArgumentType.getString(c, "name"))))))
                        .then(showCommand())
                        .then(teleporting("warp", Warps.Mode.ETHER))
                        .then(teleporting("it", Warps.Mode.TRANSMIT))
                        .then(teleporting("aotv", Warps.Mode.BOTH))
                        .then(coordinate("x", 0)
                                .then(coordinate("y", 1)
                                .then(coordinate("z", 2)
                                .executes(c -> startGoto(feedback(c.getSource()), new BlockPos(
                                        IntegerArgumentType.getInteger(c, "x"),
                                        IntegerArgumentType.getInteger(c, "y"),
                                        IntegerArgumentType.getInteger(c, "z"))))))));
        ChatCompletion.use(dotCommands.getRoot().getChild("astar"));
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String rest = dotCommand(message);
            if (rest == null) {
                return true;
            }
            runDotCommand(Minecraft.getInstance(), rest);
            return false;
        });
        for (String name : System.getProperty("astar.trace.autoplay", "").split(",")) {
            if (!name.isBlank()) {
                autoplay.add(name.strip());
            }
        }
        AstarConfig.load();
        overlay.register();
        KeyMappingHelper.registerKeyMapping(routeKey);
        CastLog.register();
        PracticeAotv.register();
        AstarLoading.register();
        ClientChunkEvents.CHUNK_LOAD.register(Places::loaded);
        ClientChunkEvents.CHUNK_UNLOAD.register(Places::unloading);
        ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::endTick);
    }

    /**
     * {@code .A* show} hides or shows the route; {@code .A* show <colour>} draws it all in
     * that colour (kept for later games) and shows it, and {@code .A* show ribbon} or
     * {@code lines} picks how it's drawn.
     */
    private LiteralArgumentBuilder<FabricClientCommandSource> showCommand() {
        var show = literal("show").executes(c -> {
            c.getSource().sendFeedback(Component.literal(overlay.toggle()
                    ? "Showing the route in " + overlay.colour().id() + "." : "Route hidden."));
            return 1;
        });
        for (RouteOverlay.Style style : RouteOverlay.Style.values()) {
            show.then(literal(style.id()).executes(c -> {
                overlay.style(style);
                c.getSource().sendFeedback(Component.literal("Route drawn as "
                        + style.label.toLowerCase(Locale.ROOT) + ": " + style.about + "."));
                return 1;
            }));
        }
        for (OverlayColour colour : OverlayColour.values()) {
            show.then(literal(colour.id()).executes(c -> {
                overlay.colour(colour);
                c.getSource().sendFeedback(Component.literal("Route drawn in " + colour.id()
                        + "."));
                return 1;
            }));
        }
        return show;
    }

    private int start(Feedback source) {
        if (busy(source)) {
            return 0;
        }
        if (Minecraft.getInstance().player == null) {
            source.error(Component.literal("Join a world first."));
            return 0;
        }
        try {
            recorder = TraceRecorder.start(traceDir(), "", Minecraft.getInstance());
        } catch (IOException e) {
            source.error(Component.literal("Couldn't start a trace: " + e.getMessage()));
            return 0;
        }
        source.info(Component.literal("Recording to " + recorder.file().getFileName()
                + ". /trace stop ends it."));
        return 1;
    }

    private int buildCourse(Feedback source, BlockPos origin) {
        if (busy(source)) {
            return 0;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            source.error(Component.literal("Join a world first."));
            return 0;
        }
        course = new TestCourse(origin);
        for (String command : course.commands()) {
            client.getConnection().sendCommand(command);
        }
        client.getConnection().sendCommand(course.teleport(2.5, 0, -12.5, -90));
        courseTicks = 0;
        source.info(Component.literal("Built the test course at " + origin.toShortString()
                + " (it needs cheats on). Scripts with an \"at\" line now start on it."));
        return 1;
    }

    private int stop(Feedback source) {
        if (recorder == null && scenario == null) {
            source.error(Component.literal("Nothing is recording."));
            return 0;
        }
        finish(scenario != null ? "stopped by /trace stop before the script ended" : null);
        return 1;
    }

    private int list(Feedback source) {
        source.info(Component.literal("Scripts: " + String.join(", ", scriptNames())));
        source.info(Component.literal("Your own go in " + scriptDir()).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private int play(Feedback source, String name) {
        if (busy(source)) {
            return 0;
        }
        Scenario s;
        try {
            s = loadScript(name);
        } catch (IOException | IllegalArgumentException e) {
            source.error(Component.literal(e.getMessage()));
            return 0;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            source.error(Component.literal("Join a world first."));
            return 0;
        }
        if (client.options.autoJump().get()) {
            source.info(Component.literal("Auto-jump is on, so the game may add jumps the"
                    + " script didn't ask for. Turn it off in Controls for clean traces.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        scenario = s;
        frame = 0;
        waited = 0;
        scripted = new ScriptedInput();
        client.player.input = scripted;
        target = null;
        if (s.start() != null && s.start().world()) {
            Scenario.Start at = s.start();
            client.getConnection().sendCommand("tp @s " + at.x() + " " + at.y() + " " + at.z()
                    + " " + at.yaw() + " 0");
            target = new Vec3(at.x(), at.y(), at.z());
        } else if (course != null && s.start() != null) {
            Scenario.Start at = s.start();
            client.getConnection().sendCommand(course.teleport(at.x(), at.y(), at.z(),
                    at.yaw()));
            BlockPos o = course.origin();
            target = new Vec3(o.getX() + at.x(), o.getY() + at.y(), o.getZ() + at.z());
        }
        source.info(Component.literal("Playing " + s.name() + " (" + s.ticks() + " ticks"
                + (s.description().isEmpty() ? "" : ": " + s.description())
                + ") once you're standing still on the ground."));
        return 1;
    }

    /** {@code .A* <name> x y z}: a trip that teleports as well as walks. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
            teleporting(String name, Warps.Mode mode) {
        return literal(name).then(coordinate("x", 0)
                .then(coordinate("y", 1)
                .then(coordinate("z", 2)
                .executes(c -> startGoto(feedback(c.getSource()), new BlockPos(
                        IntegerArgumentType.getInteger(c, "x"),
                        IntegerArgumentType.getInteger(c, "y"),
                        IntegerArgumentType.getInteger(c, "z")), mode)))));
    }

    /** A coordinate of {@code .A*}, which Tab fills in from the block the crosshair is on. */
    private static RequiredArgumentBuilder<FabricClientCommandSource, Integer> coordinate(
            String name, int axis) {
        return argument(name, IntegerArgumentType.integer())
                .suggests((c, b) -> ChatCompletion.suggestLookedAt(c, b, axis));
    }

    private int startGoto(Feedback source, BlockPos goal) {
        return startGoto(source, goal, null);
    }

    /** @param warp which teleports the trip uses, or null to walk only */
    private int startGoto(Feedback source, BlockPos goal, Warps.Mode warp) {
        if(com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning()) {
            source.error(Component.literal("Stop trading with the toggle key before starting a manual route."));return 0;
        }
        if (busy(source)) {
            return 0;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            source.error(Component.literal("Join a world first."));
            return 0;
        }
        if (client.options.autoJump().get()) {
            source.info(Component.literal("Auto-jump is on, so the game may add jumps the route"
                    + " doesn't want. Turn it off in Controls.").withStyle(ChatFormatting.YELLOW));
        }
        navigator = new Navigator(goal, text -> say(client, text), warp);
        source.info(Component.literal("Planning a route to " + goal.toShortString() + "..."));
        navigator.plan(client);
        return 1;
    }

    Navigator navigator() {
        return navigator;
    }

    private int stopGoto(Feedback source) {
        if (!going()) {
            source.error(Component.literal("Not going anywhere."));
            return 0;
        }
        navigator.cancel(Minecraft.getInstance(), "by .A* stop");
        return 1;
    }

    private int resumeGoto(Feedback source) {
        if (navigator == null || navigator.state() != Navigator.State.PAUSED) {
            source.error(Component.literal("Nothing is paused."));
            return 0;
        }
        source.info(Component.literal("Planning again from here to "
                + navigator.goal().toShortString() + "..."));
        navigator.resume(Minecraft.getInstance());
        return 1;
    }

    /** Runs the {@code astar.goto.commands} that are due. */
    private void runDuringGoto(Minecraft client) {
        gotoTicks++;
        gotoCommands.removeIf(c -> {
            int colon = c.indexOf(':');
            if (colon < 0 || Integer.parseInt(c.substring(0, colon).strip()) != gotoTicks) {
                return c.isBlank();
            }
            String command = c.substring(colon + 1).strip();
            if (command.startsWith("screenshot ")) {
                // Not a game command: saves screenshots/<name>.png, to see the overlay.
                Screenshot.grab(client.gameDirectory, command.substring(11).strip() + ".png",
                        client.gameRenderer.mainRenderTarget(), 1,
                        text -> System.out.println("[astar] " + text.getString()));
            } else if (command.startsWith("thirdperson")) {
                client.options.setCameraType(command.endsWith("front")
                        ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
            } else if (command.startsWith("screen ")) {
                if(com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning())
                    say(client,Component.literal("Stop trading with the toggle key before opening settings."));
                else client.gui.setScreen(new AstarScreen(this, overlay, command.substring(7).strip()));
            } else if (command.startsWith("chat ")) {
                // Typed into chat, as a player would: tries the .A* commands end to end.
                client.getConnection().sendChat(command.substring(5));
            } else if (command.startsWith("type ")) {
                // Opens chat with this typed in (a trailing | marks where it ends, to keep
                // spaces), to see the suggestions; key tab|up|down|enter then presses those keys.
                String typed = command.substring(5);
                client.gui.setScreen(new ChatScreen(typed.endsWith("|")
                        ? typed.substring(0, typed.length() - 1) : typed, false));
            } else if (command.startsWith("key ")) {
                // The key's scancode and its keycode, which chat's history keys look at.
                KeyEvent key = switch (command.substring(4).strip()) {
                    case "up" -> new KeyEvent(InputConstants.KEY_UP, 0x40000052, 0);
                    case "down" -> new KeyEvent(InputConstants.KEY_DOWN, 0x40000051, 0);
                    case "enter" -> new KeyEvent(InputConstants.KEY_RETURN, '\r', 0);
                    default -> new KeyEvent(InputConstants.KEY_TAB, '\t', 0);
                };
                if (client.gui.screen() != null) {
                    client.gui.screen().keyPressed(key);
                }
            } else if (command.startsWith("theme ")) {
                Theme theme = Theme.valueOf(command.substring(6).strip().toUpperCase(Locale.ROOT));
                Theme.use(theme);
                overlay.colour(theme.route);
            } else if (command.startsWith("search ")) {
                if (client.gui.screen() instanceof AstarScreen window) {
                    window.searchFor(command.substring(7));
                }
            } else if (command.equals("closescreen")) {
                client.gui.setScreen(null);
            } else if (command.equals("hidegui")) {
                if (!client.gui.hud.isHidden()) {
                    client.gui.hud.toggle();
                }
            } else {
                runStep(client, command);
            }
            return true;
        });
    }

    /** Whether a {@code .A*} is planning, driving or paused. */
    private boolean going() {
        return navigator != null && navigator.state() != Navigator.State.DONE;
    }

    boolean canStartTrading(){return recorder==null && scenario==null;}
    void cancelRouteForTrading(){if(going())navigator.cancel(Minecraft.getInstance(),"trading started");}
    boolean beginTradingRoute(BlockPos goal) {
        var mc=Minecraft.getInstance();
        if(going() || !canStartTrading() || mc.player==null || mc.level==null)return false;
        navigator=new Navigator(goal,text->say(mc,text));navigator.plan(mc);return true;
    }
    private void startTick(Minecraft client) {
        if(com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning() && !canStartTrading())
            com.goofy.goofyaddons.features.FeatureManager.INSTANCE.safetyPause("Stop movement recording/playback before trading");
        tradingPathfinder.beforeTick(client);
        if(going() && com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning() && !tradingPathfinder.owns())
            navigator.cancel(client,"trader owns the controls");
        CastLog.tick(client);
        if (going() && !client.isPaused()) {
            if (client.player == null) {
                navigator.cancel(client, "left the world");
            } else {
                navigator.tick(client);
            }
            if (navigator.state() == Navigator.State.DRIVING) {
                runDuringGoto(client);
            }
            if (navigator.state() == Navigator.State.DONE) {
                System.out.println("[astar] goto " + navigator.goal().toShortString() + ": "
                        + navigator.outcome() + "; " + navigator.events());
            }
        }
        // While the game is paused the player doesn't move, so there's no tick to record.
        if ((recorder == null && scenario == null) || client.isPaused()) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || (recorder != null && player != recorder.player())
                || (scenario != null && player.input != scripted)) {
            finish("the player was replaced (respawn, world change or disconnect)");
            return;
        }
        try {
            if (scenario != null && recorder == null) {
                boolean placed = target == null || (waited >= 5
                        && player.position().distanceToSqr(target) < 0.01);
                if (!placed || !standingStill(player)) {
                    if (++waited > MAX_WAIT_TICKS) {
                        say(client, Component.literal("Gave up on " + scenario.name() + ": stand still"
                                + " on the ground first.").withStyle(ChatFormatting.RED));
                        finish(null);
                    }
                    return;
                }
                startYaw = player.getYRot();
                recorder = TraceRecorder.start(traceDir(), scenario.name(), client);
                say(client, Component.literal("Recording " + scenario.name() + " to "
                        + recorder.file().getFileName()));
            }
            if (scenario != null) {
                if (frame == scenario.ticks()) {
                    recorder.event("script ended");
                    finish(null);
                    return;
                }
                Scenario.Frame f = scenario.frames().get(frame++);
                scripted.set(f.keys());
                player.setYRot(startYaw + f.yaw());
                if (!Float.isNaN(f.pitch())) {
                    player.setXRot(f.pitch());
                }
            }
            recorder.startTick();
        } catch (IOException e) {
            fail(client, e);
        }
    }

    private void endTick(Minecraft client) {
        if (going()) {
            navigator.blendCamera(client);
        }
        while (routeKey.consumeClick()) {
            if (client.player != null && client.gui.screen() == null) {
                if(com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning())
                    say(client,Component.literal("Stop trading with the toggle key before opening settings."));
                else client.gui.setScreen(new AstarScreen(this, overlay));
            }
        }
        if (!pendingShapes.isBlank()) {
            Path file = Path.of(pendingShapes);
            pendingShapes = "";
            try {
                int n = ShapeDump.write(file);
                System.out.println("[astar] wrote " + n + " block states' shapes to "
                        + file.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("[astar] couldn't write block shapes: " + e);
            }
        }
        if (!autoplay.isEmpty() || autoquit || !pendingGoto.isBlank()) {
            runUnattended(client);
        }
        // Now and then, while no trip is planning, the copy .A* plans on is made ahead of
        // time around the player (LiveMap.warmUp), so .A*, and the next stretch of a long
        // trip, plan at once.
        boolean planning = going() && navigator.state() == Navigator.State.PLANNING;
        if (client.player != null && client.level != null && !planning
                && ++warmTicks % WARM_EVERY == 0) {
            LiveMap.warmUp(client.level, client.player.blockPosition(),
                    client.options.getEffectiveRenderDistance());
        }
        if (recorder == null || client.isPaused()) {
            return;
        }
        try {
            recorder.endTick();
        } catch (IOException e) {
            fail(client, e);
        }
    }

    /** Plays the {@code astar.trace.autoplay} scripts in turn, then quits if asked to. */
    private void runUnattended(Minecraft client) {
        ticksInWorld = client.player == null ? 0 : ticksInWorld + 1;
        courseTicks++;
        // Give the world a couple of seconds to load around the player first, and the course
        // a couple more to be built.
        if (ticksInWorld < 40 || courseTicks < 40 || recorder != null || scenario != null
                || going()) {
            return;
        }
        if (!pendingCommands.isBlank()) {
            for (String command : pendingCommands.split(";")) {
                if (!command.isBlank()) {
                    runStep(client, command.strip());
                }
            }
            pendingCommands = "";
            courseTicks = 0;
            return;
        }
        if (!pendingCourse.isBlank()) {
            String[] xyz = pendingCourse.split(",");
            pendingCourse = "";
            buildCourse(chat(client, "the course"), new BlockPos(Integer.parseInt(xyz[0].strip()),
                    Integer.parseInt(xyz[1].strip()), Integer.parseInt(xyz[2].strip())));
            return;
        }
        if (!autoplay.isEmpty()) {
            String name = autoplay.poll();
            play(chat(client, name), name);
        } else if (!pendingGoto.isBlank()) {
            // One goal, or several in turn: "x,y,z;x,y,z".
            int semi = pendingGoto.indexOf(';');
            String[] xyz = (semi < 0 ? pendingGoto : pendingGoto.substring(0, semi)).split(",");
            pendingGoto = semi < 0 ? "" : pendingGoto.substring(semi + 1);
            if (xyz.length == 1) {
                // A command between trips, such as "tp @s 168 202 283" back to the start.
                runStep(client, xyz[0].strip());
                courseTicks = 0;
                return;
            }
            startGoto(chat(client, "the goto"), new BlockPos(Integer.parseInt(xyz[0].strip()),
                    Integer.parseInt(xyz[1].strip()), Integer.parseInt(xyz[2].strip())),
                    switch (System.getProperty("astar.goto.warp", "")) {
                        case "true", "warp" -> Warps.Mode.ETHER;
                        case "it" -> Warps.Mode.TRANSMIT;
                        case "aotv" -> Warps.Mode.BOTH;
                        default -> null;
                    });
        } else if (autoquit) {
            client.stop();
        }
    }

    /** Messages for an unattended step go to chat, and errors say what was skipped. */
    private static Feedback chat(Minecraft client, String what) {
        return new Feedback() {
            @Override
            public void info(Component text) {
                say(client, text);
            }

            @Override
            public void error(Component text) {
                say(client, Component.literal("Skipped " + what + ": ").append(text)
                        .withStyle(ChatFormatting.RED));
            }
        };
    }

    /** Ends the trace and gives the keyboard back. */
    private void finish(String why) {
        Minecraft client = Minecraft.getInstance();
        if (scenario != null && client.player != null && client.player.input == scripted) {
            client.player.input = new KeyboardInput(client.options);
        }
        scenario = null;
        scripted = null;
        if (recorder != null) {
            TraceRecorder r = recorder;
            recorder = null;
            try {
                if (why != null) {
                    r.event(why);
                }
                r.close();
                say(client, Component.literal("Saved " + r.ticks() + " ticks to " + r.file()
                        + (why == null ? "" : " (" + why + ")")));
            } catch (IOException e) {
                say(client, Component.literal("Couldn't finish the trace: " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private void fail(Minecraft client, IOException e) {
        say(client, Component.literal("Trace stopped, couldn't write: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
        finish(null);
    }

    /** Where a command's messages go: the command's own feedback, or chat when unattended. */
    private interface Feedback {
        void info(Component text);

        void error(Component text);
    }

    private static CompletableFuture<Suggestions> suggestEasings(
            CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        for (Easing e : Easing.values()) {
            b.suggest(e.label);
        }
        return b.buildFuture();
    }

    /** Sets the turning curves from {@code .A* ease} (mouse.ease_turns and ease_flicks). */
    private static int ease(FabricClientCommandSource source, String turns, String flicks) {
        Easing t = Easing.named(turns);
        Easing f = Easing.named(flicks);
        if (t == null || f == null) {
            return say(source, "No curve called " + (t == null ? turns : flicks) + ".");
        }
        AstarConfig.load();
        Tuning.EASE_TURNS.set(t.label);
        Tuning.EASE_FLICKS.set(f.label);
        AstarConfig.save();
        return say(source, describeEase());
    }

    private static String describeEase() {
        return "Turning with " + AstarConfig.turns().label + ", flicking with "
                + AstarConfig.flicks().label + ".";
    }

    /** Sets the hand from {@code .A* hand} (mouse.hand). */
    private static int hand(FabricClientCommandSource source, String hand) {
        AstarConfig.load();
        Tuning.HAND.set(hand);
        AstarConfig.save();
        return say(source, describeHand());
    }

    private static String describeHand() {
        return "Moving the mouse " + (AstarConfig.hand() == AimController.Hand.LEFT ? "left"
                : "right") + "-handed.";
    }

    /** {@code .A* cache maps}: the maps saved for this server and dimension. */
    private static int maps(FabricClientCommandSource source) {
        var maps = Places.maps();
        if (maps.isEmpty()) {
            return say(source, "No maps saved here yet.");
        }
        source.sendFeedback(Component.literal("Maps saved here:"));
        for (Places.MapInfo m : maps) {
            source.sendFeedback(Component.literal("  " + m.name()).withStyle(m.current()
                    ? ChatFormatting.GOLD : ChatFormatting.WHITE).append(Component.literal(
                    String.format(" %d chunks, %.1f MB%s%s", m.chunks(), m.bytes() / 1e6,
                            m.bundled() ? ", on the bundled map" : "",
                            m.current() ? " (you're here)" : ""))
                    .withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int say(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal(text));
        return 1;
    }

    private static Feedback feedback(FabricClientCommandSource source) {
        return new Feedback() {
            @Override
            public void info(Component text) {
                source.sendFeedback(text);
            }

            @Override
            public void error(Component text) {
                source.sendError(text);
            }
        };
    }

    private boolean busy(Feedback source) {
        if(com.goofy.goofyaddons.features.FeatureManager.INSTANCE.isMacroRunning()) {
            source.error(Component.literal("Stop trading with the toggle key before recording movement or starting a manual route."));return true;
        }
        if (recorder != null || scenario != null) {
            source.error(Component.literal("A trace is already running; /trace stop ends it."));
            return true;
        }
        if (going()) {
            source.error(Component.literal("Already going to " + navigator.goal().toShortString()
                    + "; .A* stop ends it."));
            return true;
        }
        return false;
    }

    private static boolean standingStill(LocalPlayer player) {
        return player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 1e-6;
    }

    private static void say(Minecraft client, Component text) {
        if (client.player != null) {
            client.player.sendSystemMessage(text);
        }
    }

    private static Scenario loadScript(String name) throws IOException {
        Path file = scriptDir().resolve(name + ".txt");
        if (Files.isRegularFile(file)) {
            return Scenario.parse(name, Files.readString(file, StandardCharsets.UTF_8));
        }
        return Scenario.builtIn(name);
    }

    private static List<String> scriptNames() {
        List<String> names = new ArrayList<>(Scenario.BUILT_IN);
        try (Stream<Path> files = Files.list(scriptDir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .filter(n -> !names.contains(n))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            // No scripts folder yet: only the built-in ones.
        }
        return names;
    }

    private static Path traceDir() {
        return FabricLoader.getInstance().getGameDir().resolve("astar-traces");
    }

    private static Path scriptDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("astar-scenarios");
    }
}
