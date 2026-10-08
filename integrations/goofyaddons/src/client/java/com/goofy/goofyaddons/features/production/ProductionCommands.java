package com.goofy.goofyaddons.features.production;

import net.fabricmc.fabric.api.client.command.v2.*;
import com.mojang.brigadier.arguments.*;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.menu.LiveActions;

public final class ProductionCommands {
    private ProductionCommands() {}
    public static void register() {
        com.goofy.goofyaddons.commands.GoofyCommands.register(dispatcher-> {
            dispatcher.register(ClientCommands.literal("craft")
                .then(ClientCommands.argument("output",StringArgumentType.word())
                    .executes(c->queue(StringArgumentType.getString(c,"output"),1))
                    .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,16))
                        .executes(c->queue(StringArgumentType.getString(c,"output"),IntegerArgumentType.getInteger(c,"batches"))))));
            dispatcher.register(ClientCommands.literal("production")
                .then(ClientCommands.literal("jobs").executes(c->{
                    try {var jobs=FeatureManager.INSTANCE.crafting().journal();
                        if(jobs.isEmpty())new LiveActions().message("No production jobs recorded.");
                        for(var job:jobs)new LiveActions().message(job.id().substring(0,Math.min(8,job.id().length()))+" · "+job.recipeKey()+" · "+job.state());
                    }catch(Exception failure){new LiveActions().message("Production journal unreadable; file preserved.");}return 1;
                }))
                .then(ClientCommands.literal("recipes").then(ClientCommands.argument("output",StringArgumentType.word()).executes(c->{
                    var rows=RecipeCatalog.instance().forOutput(StringArgumentType.getString(c,"output").toUpperCase(java.util.Locale.ROOT));
                    for(var row:rows)new LiveActions().message(row.key()+" · "+row.ingredients()+" · "+row.requirement());
                    if(rows.isEmpty())new LiveActions().message("No supported recipes for that product ID.");return 1;
                }))));
        });
    }
    private static int queue(String output,int count){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing a craft.");return 0;}
        return FeatureManager.INSTANCE.crafting().queue(output.toUpperCase(java.util.Locale.ROOT),count)?1:0;
    }
}
