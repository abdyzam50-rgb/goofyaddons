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
                .then(ClientCommands.literal("flips").executes(c->{FeatureManager.INSTANCE.production().showCraftPlans();return 1;}))
                .then(ClientCommands.literal("compactor").executes(c->{
                    var menu=new com.goofy.goofyaddons.menu.LiveWorld().menu();
                    var found=PersonalCompactors.detect(menu);var actions=new LiveActions();
                    if(found.isEmpty())actions.message("No Personal Compactor observed. Keep it in ordinary inventory or open its Accessory Bag page.");
                    for(var slot:found) {
                        var data=slot.metadata().compactor();
                        actions.message("Personal Compactor "+com.goofy.goofyaddons.menu.CompactorData.tier(slot.customId())+" · "+
                            (data==null || data.active()==null?"state unknown":data.active()?"enabled":"disabled")+
                            (data==null?"":" · "+data.recipes().size()+"/"+com.goofy.goofyaddons.menu.CompactorData.capacity(data.tier())+" recipes"));
                    }
                    actions.message("Automatic bulk configuration is pending GUI verification; existing recipes are preserved.");return 1;
                }).then(ClientCommands.literal("inspect").executes(c->{
                    try {PersonalCompactors.capture();new LiveActions().message("Saved compactor GUI controls to config/goofyaddons-compactor-menu.json.");}
                    catch(java.io.IOException missing){new LiveActions().message("Open the Personal Compactor menu first, then run this command.");}return 1;
                })))
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
                .then(ClientCommands.literal("test").then(ClientCommands.argument("output",StringArgumentType.word())
                    .executes(c->test(StringArgumentType.getString(c,"output"),0))
                    .then(ClientCommands.argument("binPrice",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .executes(c->test(StringArgumentType.getString(c,"output"),LongArgumentType.getLong(c,"binPrice"))))))
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
    /**
     * One craft batch end to end for testing: missing inputs are instant-bought for this run only.
     * With a price the result is listed as a BIN with a fee ceiling for that price; without one, a
     * Bazaar product is sold instantly on the Bazaar and anything else is listed one coin under
     * the matching lowest BIN observed in /ah, validated with Coflnet.
     */
    private static int test(String output,long price){
        return test(output,price,false);
    }
    private static int test(String output,long price,boolean guiPricing){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing production.");return 0;}
        String id=output.toUpperCase(java.util.Locale.ROOT).replace(' ','_');
        var menu=new com.goofy.goofyaddons.menu.LiveWorld().menu();
        if(price>0 && menu!=null && menu.slots().stream().anyMatch(s->s.inPlayerInventory() && !s.empty() && id.equals(ProductionMenus.productId(s)))) {
            new LiveActions().message("Move the "+RecipeCatalog.instance().name(id)+" you already hold out of your inventory first, so the listing picks the crafted one.");return 0;
        }
        if(price==0 && com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null){withBazaarQuotes(id);return 1;}
        if(price==0 && onBazaar(id))price=ProductionRun.SELL_ON_BAZAAR;
        else if(price==0){priceFromAuctions(id);return 1;}
        if(!FeatureManager.INSTANCE.production().queue(id,ProductionRecipe.Kind.CRAFT,1,-1,price,price>0?ProductionPlanner.listingFeeLimit(price):0,true,guiPricing))return 0;
        FeatureManager.INSTANCE.startProductionTest();return 1;
    }
    private static final java.net.http.HttpClient AUCTION_HTTP=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(java.time.Duration.ofSeconds(2));
    /** Gets a price-only reference for fee budgeting; the automatic sale price is observed in /ah. */
    private static void priceFromAuctions(String id){
        var actions=new LiveActions();
        try {
            if(!ProductionRecipe.validId(id))throw new IllegalArgumentException("Unknown product "+id);
            var settings=new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings();
            settings.endpoint=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE.marketAnalysis.endpoint;settings.validate();
            var uri=java.net.URI.create(settings.endpoint).resolve("/v1/ah/price?item="+java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8));
            var request=java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15)).GET().build();
            actions.message("Looking up the lowest BIN for "+RecipeCatalog.instance().name(id)+"…");
            AUCTION_HTTP.sendAsync(request,java.net.http.HttpResponse.BodyHandlers.limiting(java.net.http.HttpResponse.BodyHandlers.ofString(),16*1024))
                .whenComplete((response,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
                    try {
                        if(error!=null)throw new IllegalArgumentException("the calculator could not be reached");
                        com.google.gson.JsonObject body;
                        try{body=com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();}
                        catch(RuntimeException notJson){throw new IllegalArgumentException("the calculator does not offer auction prices; restart Minecraft so it updates");}
                        if(response.statusCode()!=200)throw new IllegalArgumentException(body.has("protocol")||!body.has("error")
                                ?"the calculator does not offer auction prices; restart Minecraft so it updates":body.get("error").getAsString());
                        var quote=AuctionPricing.parse(body,id,System.currentTimeMillis());
                        long price=AuctionPricing.listingPrice(quote);
                        actions.message(String.format(java.util.Locale.ROOT,"Coflnet reference %,d coins%s. The sale price will come from matching BINs observed in /ah.",quote.lowest(),
                                quote.secondLowest()==null?"":String.format(java.util.Locale.ROOT,", next %,d",quote.secondLowest())));
                        test(id,price,true);
                    }catch(RuntimeException failure){
                        actions.message("No automatic price for "+RecipeCatalog.instance().name(id)+": "+failure.getMessage()+". Use: production test "+id+" <price>");
                    }
                }));
        }catch(RuntimeException failure){actions.message("No automatic price: "+failure.getMessage()+". Use: production test "+id+" <price>");}
    }
    /** With traders off nothing keeps Bazaar quotes fresh; fetch them so a Bazaar product is not taken for an auction item. */
    private static void withBazaarQuotes(String id){
        var actions=new LiveActions();
        actions.message("Fetching Bazaar prices…");
        com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch().whenComplete((market,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
            if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null){
                actions.message("Bazaar prices could not be fetched, so it is unknown whether "+RecipeCatalog.instance().name(id)+" sells there. Try again, or use: production test "+id+" <price>");return;
            }
            test(id,0);
        }));
    }
    private static boolean onBazaar(String id){
        var market=com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh();
        return market!=null && market.has("products") && market.getAsJsonObject("products").has(id);
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
