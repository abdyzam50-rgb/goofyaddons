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
                .then(ClientCommands.literal("run").then(ClientCommands.argument("output",StringArgumentType.word())
                    .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,16))
                        .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.CRAFT,IntegerArgumentType.getInteger(c,"batches"),-1,0,0))
                        .then(sale((c,price,fee)->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.CRAFT,IntegerArgumentType.getInteger(c,"batches"),-1,price,fee))))))
                .then(ClientCommands.literal("forge").then(ClientCommands.argument("output",StringArgumentType.word())
                    .then(ClientCommands.argument("slot",IntegerArgumentType.integer(1,7))
                        .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.FORGE,1,IntegerArgumentType.getInteger(c,"slot")-1,0,0))
                        .then(sale((c,price,fee)->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.FORGE,1,IntegerArgumentType.getInteger(c,"slot")-1,price,fee))))))
                .then(ClientCommands.literal("kat").then(ClientCommands.argument("output",StringArgumentType.greedyString())
                    .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.KAT,1,-1,0,0))))
                .then(ClientCommands.literal("claim").then(ClientCommands.argument("job",StringArgumentType.word())
                    .executes(c->claim(StringArgumentType.getString(c,"job"),0,0))
                    .then(sale((c,price,fee)->claim(StringArgumentType.getString(c,"job"),price,fee)))))
                .then(ClientCommands.literal("status").executes(c->{new LiveActions().message(FeatureManager.INSTANCE.production().activity());return 1;}))
                .then(ClientCommands.literal("recipes").then(ClientCommands.argument("output",StringArgumentType.word()).executes(c->{
                    var rows=RecipeCatalog.instance().forOutput(StringArgumentType.getString(c,"output").toUpperCase(java.util.Locale.ROOT));
                    for(var row:rows)new LiveActions().message(row.key()+" · "+row.ingredients()+" · "+row.requirement());
                    if(rows.isEmpty())new LiveActions().message("No supported recipes for that product ID.");return 1;
                }))));
        });
    }
    @FunctionalInterface private interface Sale {int run(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,long price,long fee);}
    /** "<binPrice> <maximumFee>": list the output as a BIN once it is made. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource,Long> sale(Sale then) {
        return ClientCommands.argument("binPrice",LongArgumentType.longArg(1,1_000_000_000_000L))
            .then(ClientCommands.argument("maximumFee",LongArgumentType.longArg(0,1_000_000_000_000L))
                .executes(c->then.run(c,LongArgumentType.getLong(c,"binPrice"),LongArgumentType.getLong(c,"maximumFee"))));
    }
    private static int run(String output,ProductionRecipe.Kind kind,int batches,int slot,long price,long fee){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing production.");return 0;}
        return FeatureManager.INSTANCE.production().queue(output.toUpperCase(java.util.Locale.ROOT).replace(' ','_'),kind,batches,slot,price,fee)?1:0;
    }
    private static int claim(String job,long price,long fee){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before claiming.");return 0;}
        return FeatureManager.INSTANCE.production().claim(job,price,fee)?1:0;
    }
    private static int queue(String output,int count){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing a craft.");return 0;}
        return FeatureManager.INSTANCE.crafting().queue(output.toUpperCase(java.util.Locale.ROOT),count)?1:0;
    }
}
