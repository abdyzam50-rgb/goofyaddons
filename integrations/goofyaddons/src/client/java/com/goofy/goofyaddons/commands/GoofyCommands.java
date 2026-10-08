package com.goofy.goofyaddons.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.features.TradingMode;
import com.goofy.goofyaddons.features.sessions.SessionScheduler;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Goofy features contribute real Brigadier nodes to A*'s local dot-command dispatcher. */
public final class GoofyCommands {
    private static final CommandDispatcher<FabricClientCommandSource> FEATURES=new CommandDispatcher<>();
    private GoofyCommands() {}
    public static void register(Consumer<CommandDispatcher<FabricClientCommandSource>> registrar) {
        registrar.accept(FEATURES);
    }
    public static LiteralArgumentBuilder<FabricClientCommandSource> root() {
        var root=literal("goofyaddon").executes(c->{
            c.getSource().sendFeedback(Component.literal(".a* goofyaddon: start, stop, toggle, status, mode <books|general|both>, reload, debug export, profit, schedule, craft, production, auction."));return 1;
        });
        FEATURES.getRoot().getChildren().forEach(root::then);
        root.then(literal("start").executes(c->{SessionScheduler.INSTANCE.manualStart();return 1;}));
        root.then(literal("stop").executes(c->{SessionScheduler.INSTANCE.manualStop();return 1;}));
        root.then(literal("toggle").executes(c->{
            if(!FeatureManager.INSTANCE.canReloadConfig() || SessionScheduler.INSTANCE.armed() || com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.active())SessionScheduler.INSTANCE.manualStop();
            else SessionScheduler.INSTANCE.manualStart();return 1;
        }));
        root.then(literal("status").executes(c->{
            c.getSource().sendFeedback(Component.literal(FeatureManager.INSTANCE.status()+" | "+FeatureManager.INSTANCE.activity()+" | "+SessionScheduler.INSTANCE.status()));return 1;
        }));
        var mode=literal("mode").executes(c->{c.getSource().sendFeedback(Component.literal(FeatureManager.INSTANCE.modeLabel()));return 1;});
        for(var choice:TradingMode.values())mode.then(literal(choice.name().toLowerCase(java.util.Locale.ROOT)).executes(c->{
            if(!FeatureManager.INSTANCE.canReloadConfig()) {c.getSource().sendError(Component.literal("Stop trading before changing mode."));return 0;}
            try {
                var gson=new com.google.gson.Gson();
                var cfg=gson.fromJson(gson.toJson(com.goofy.goofyaddons.config.GoofyConfig.INSTANCE),com.goofy.goofyaddons.config.GoofyConfig.class);
                cfg.tradingMode=choice;com.goofy.goofyaddons.config.GoofyConfig.commitSettings(cfg);
                c.getSource().sendFeedback(Component.literal("Trading mode: "+choice));return 1;
            }catch(Exception failure){c.getSource().sendError(Component.literal("Mode not saved: "+failure.getMessage()));return 0;}
        }));
        return root.then(mode);
    }
}
