package com.goofy.goofyaddons.features.production;

import net.fabricmc.fabric.api.client.command.v2.*;
import com.mojang.brigadier.arguments.*;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.menu.LiveActions;
import com.goofy.goofyaddons.menu.LiveWorld;

public final class ProductionCommands {
    private ProductionCommands() {}
    public static void register() {
        com.goofy.goofyaddons.commands.GoofyCommands.register(dispatcher-> {
            dispatcher.register(ClientCommands.literal("craft")
                .then(ClientCommands.argument("output",StringArgumentType.string())
                    .executes(c->queue(StringArgumentType.getString(c,"output"),1))
                    .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,1024))
                        .executes(c->queue(StringArgumentType.getString(c,"output"),IntegerArgumentType.getInteger(c,"batches"))))));
            dispatcher.register(testCommands());
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
                    actions.message("Production clears every detected compactor recipe before buying or crafting. Keep compactors in the hotbar for automatic opening; bulk compaction is not enabled yet.");return 1;
                }).then(ClientCommands.literal("inspect").executes(c->{
                    try {PersonalCompactors.capture();new LiveActions().message("Saved compactor GUI controls to config/goofyaddons-compactor-menu.json.");}
                    catch(java.io.IOException missing){new LiveActions().message("Open the Personal Compactor menu first, then run this command.");}return 1;
                })))
                .then(ClientCommands.literal("jobs").executes(c->{
                    try {String account=new LiveWorld().username();
                        var jobs=FeatureManager.INSTANCE.crafting().journal().stream()
                            .filter(j->j.account().equals(account) && j.state()!=ProductionJobs.State.DONE && j.state()!=ProductionJobs.State.CANCELLED)
                            .sorted(java.util.Comparator.comparingInt(j->j.state()==ProductionJobs.State.REVIEW?0:1)).toList();
                        if(jobs.isEmpty())new LiveActions().message("No unresolved production jobs for this account.");
                        else new LiveActions().message(jobs.stream().filter(j->j.state()==ProductionJobs.State.REVIEW).count()+" REVIEW jobs. After checking inventory/workstations/listings, use production acknowledge <ID>, or production acknowledge all for every REVIEW job shown.");
                        for(var job:jobs)new LiveActions().message(job.id().substring(0,Math.min(8,job.id().length()))+" · "+job.recipeKey()+" · "+job.state()+(job.reason()==null?"":" · "+job.reason()));
                    }catch(Exception failure){new LiveActions().message("Production journal unreadable; file preserved.");}return 1;
                }))
                .then(ClientCommands.literal("recoverorders").executes(c->{
                    var manager=FeatureManager.INSTANCE;
                    if(!manager.prepareCrafting()){new LiveActions().message("Stop trading before recovering craft orders.");return 0;}
                    if(!manager.production().recoverOrders())return 0;
                    return manager.startProductionTest()?1:0;
                }))
                .then(ClientCommands.literal("release").then(ClientCommands.argument("job",StringArgumentType.word()).executes(c->{
                    var manager=FeatureManager.INSTANCE;var actions=new LiveActions();
                    if(!manager.canReloadConfig() || manager.production().queued() || manager.crafting().queued() || manager.auction().queued()){actions.message("Stop production/trading before releasing a reviewed job's orders.");return 0;}
                    try {
                        String account=new LiveWorld().username();
                        if(!new LiveWorld().inWorld())throw new IllegalArgumentException("Join the account whose job you reviewed");
                        String reference=StringArgumentType.getString(c,"job");
                        var reviews=manager.crafting().journal().stream().filter(j->j.account().equals(account) && j.state()==ProductionJobs.State.REVIEW
                            && (reference.equals("all") || reference.length()>=8 && j.id().startsWith(reference))).toList();
                        if(reviews.isEmpty() || !reference.equals("all") && reviews.size()!=1)throw new IllegalArgumentException("Name exactly one REVIEW job (production jobs lists them), or use all");
                        int count=0;
                        for(var job:reviews) {
                            var released=manager.production().releaseOrders(job.id());
                            if(released==null){actions.message("Craft order journal could not be changed for "+job.id().substring(0,8)+"; nothing released.");return 0;}
                            manager.crafting().productionJobs().acknowledgeReview(job.id(),account);
                            com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.review_released",java.util.Map.of("job",job.id(),"released",released));
                            actions.message("Released "+job.id().substring(0,8)+" · "+job.recipeKey()+(released.isEmpty()?" · no order records":" · "+String.join(", ",released)));
                            count++;
                        }
                        actions.message(count+" REVIEW jobs released. Only use this after checking /bz orders: no sale, refund or profit is recorded for released orders.");
                        return 1;
                    }catch(Exception failure){actions.message("Nothing released: "+failure.getMessage());return 0;}
                })))
                .then(ClientCommands.literal("reconcileorder").then(ClientCommands.argument("child",StringArgumentType.word())
                    .then(ClientCommands.literal("external").executes(c->{
                        var manager=FeatureManager.INSTANCE;
                        if(!manager.prepareCrafting()){new LiveActions().message("Stop trading before reconciling a manually handled craft order.");return 0;}
                        if(!manager.production().reconcileExternalOrder(StringArgumentType.getString(c,"child")))return 0;
                        if(manager.startProductionTest())return 1;
                        manager.production().stop();return 0;
                    }))))
                .then(ClientCommands.literal("acknowledge").then(ClientCommands.argument("job",StringArgumentType.word()).executes(c->{
                    var manager=FeatureManager.INSTANCE;var actions=new LiveActions();
                    if(!manager.canReloadConfig() || manager.production().queued() || manager.crafting().queued() || manager.auction().queued()){actions.message("Stop production/trading before acknowledging a reviewed job.");return 0;}
                    try {
                        String account=new LiveWorld().username();
                        if(!new LiveWorld().inWorld())throw new IllegalArgumentException("Join the account whose job you reviewed");
                        String reference=StringArgumentType.getString(c,"job");
                        if(reference.equals("all")) {
                            if(manager.production().hasRetainedOrders()){actions.message("Retained craft Bazaar orders still own items/coins. Acknowledge a specific reviewed ID without its own orders, or reconcile retained orders before acknowledging all.");return 0;}
                            var reviewed=manager.crafting().productionJobs().acknowledgeAllReviews(account);
                            actions.message(reviewed.size()+" REVIEW jobs acknowledged for this account. Evidence retained; active jobs unchanged; no item, purchase, sale or profit assumed.");
                            for(var job:reviewed){
                                var stored=manager.production().releaseStorage(job.id());
                                com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.review_acknowledged",java.util.Map.of("job",job.id(),"storageReleased",stored));
                                if(!stored.isEmpty())actions.message("Released storage records for "+job.id().substring(0,8)+": "+String.join(", ",stored));
                            }
                            return 1;
                        }
                        var job=manager.crafting().productionJobs().acknowledgeReview(reference,account,manager.production()::hasRetainedOrders);
                        long remaining=manager.crafting().journal().stream().filter(j->j.account().equals(account)&&j.state()==ProductionJobs.State.REVIEW).count();
                        actions.message("Review acknowledged for "+job.id().substring(0,8)+". "+remaining+" REVIEW jobs remain. Journal evidence retained; no item, purchase, sale or profit assumed.");
                        var stored=manager.production().releaseStorage(job.id());
                        if(!stored.isEmpty())actions.message("Released storage records: "+String.join(", ",stored));
                        com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.review_acknowledged",java.util.Map.of("job",job.id(),"storageReleased",stored));return 1;
                    }catch(Exception failure){actions.message("Review was not acknowledged: "+failure.getMessage());return 0;}
                })))
                .then(ClientCommands.literal("run").then(ClientCommands.argument("output",StringArgumentType.string())
                    .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,1024))
                        .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.CRAFT,IntegerArgumentType.getInteger(c,"batches"),-1,0,0))
                        .then(sale((c,price,fee)->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.CRAFT,IntegerArgumentType.getInteger(c,"batches"),-1,price,fee))))))
                .then(ClientCommands.literal("pipeline").then(ClientCommands.argument("output",StringArgumentType.string())
                    .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,1024))
                        .executes(c->pipeline(StringArgumentType.getString(c,"output"),IntegerArgumentType.getInteger(c,"batches"),null))
                        .then(ClientCommands.argument("strategy",StringArgumentType.word())
                            .executes(c->pipeline(StringArgumentType.getString(c,"output"),IntegerArgumentType.getInteger(c,"batches"),StringArgumentType.getString(c,"strategy")))
                            .then(ClientCommands.argument("sell",StringArgumentType.word())
                                .executes(c->pipeline(StringArgumentType.getString(c,"output"),IntegerArgumentType.getInteger(c,"batches"),
                                        StringArgumentType.getString(c,"strategy")+" "+StringArgumentType.getString(c,"sell"))))))))
                .then(ClientCommands.literal("test").then(ClientCommands.argument("output",StringArgumentType.string())
                    .executes(c->test(StringArgumentType.getString(c,"output"),0))
                    .then(ClientCommands.argument("binPrice",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .executes(c->test(StringArgumentType.getString(c,"output"),LongArgumentType.getLong(c,"binPrice"))))))
                .then(ClientCommands.literal("testah").then(ClientCommands.argument("output",StringArgumentType.string())
                    .executes(c->testAuction(StringArgumentType.getString(c,"output")))
                    .then(ClientCommands.argument("binPrice",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .executes(c->test(StringArgumentType.getString(c,"output"),LongArgumentType.getLong(c,"binPrice"),false,true)))))
                .then(ClientCommands.literal("forge").then(ClientCommands.argument("output",StringArgumentType.string())
                    .then(ClientCommands.argument("slot",IntegerArgumentType.integer(1,7))
                        .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.FORGE,1,IntegerArgumentType.getInteger(c,"slot")-1,0,0))
                        .then(sale((c,price,fee)->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.FORGE,1,IntegerArgumentType.getInteger(c,"slot")-1,price,fee))))))
                .then(ClientCommands.literal("kat").then(ClientCommands.argument("output",StringArgumentType.greedyString())
                    .executes(c->run(StringArgumentType.getString(c,"output"),ProductionRecipe.Kind.KAT,1,-1,0,0))))
                .then(ClientCommands.literal("claim").then(ClientCommands.argument("job",StringArgumentType.word())
                    .executes(c->claim(StringArgumentType.getString(c,"job"),0,0))
                    .then(sale((c,price,fee)->claim(StringArgumentType.getString(c,"job"),price,fee)))))
                .then(ClientCommands.literal("requirements").executes(c->{
                    var manager=FeatureManager.INSTANCE;var actions=new LiveActions();var unlocks=manager.observedUnlocks();
                    actions.message(manager.accountRequirementsStatus());
                    actions.message("Catacombs: "+(unlocks.containsKey("catacombs")?unlocks.get("catacombs"):"unobserved")+" · Analyzed mutations: "+unlocks.keySet().stream().filter(key->key.startsWith("mutation")).count()+" · Crop Analyzer milestone: "+(unlocks.containsKey("cropanalyzermilestone")?unlocks.get("cropanalyzermilestone"):"unobserved"));
                    actions.message("Heart of the Mountain: "+(unlocks.containsKey("hotm")?unlocks.get("hotm"):"unobserved"));
                    var names=java.util.List.of("Zombie","Spider","Wolf","Enderman","Blaze","Vampire");
                    actions.message("Slayers: "+names.stream().map(name->name+" "+(unlocks.containsKey(name.toLowerCase(java.util.Locale.ROOT)+"slayer")?unlocks.get(name.toLowerCase(java.util.Locale.ROOT)+"slayer"):"unobserved")).collect(java.util.stream.Collectors.joining(" · ")));return 1;
                }))
                .then(ClientCommands.literal("status").executes(c->{new LiveActions().message(FeatureManager.INSTANCE.production().activity());return 1;}))
                .then(ClientCommands.literal("recipes").executes(c->{
                    var catalog=RecipeCatalog.instance();
                    new LiveActions().message(catalog.recipes().stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).count()+" craft routes · "+catalog.unsupportedCrafts().size()+" source recipes await exact identity support. Use production recipes ITEM_ID for details.");return 1;
                }).then(ClientCommands.argument("output",StringArgumentType.string()).executes(c->{
                    var rows=RecipeCatalog.instance().forOutput(StringArgumentType.getString(c,"output").toUpperCase(java.util.Locale.ROOT));
                    for(var row:rows)new LiveActions().message(row.key()+" · "+row.ingredients()+" · "+row.requirement());
                    if(rows.isEmpty()) {
                        String id=StringArgumentType.getString(c,"output").toUpperCase(java.util.Locale.ROOT);
                        var unsupported=RecipeCatalog.instance().unsupportedCrafts().stream().filter(r->r.outputId().equals(id)).toList();
                        if(unsupported.isEmpty())new LiveActions().message("No supported recipes for that product ID.");
                        else for(var row:unsupported)new LiveActions().message(row.outputId()+" · "+row.reason());
                    }return 1;
                }))));
        });
    }
    static final String TEST_HELP=".a* goofyaddon test: buy <item> <units> <instant|order> · sell <item> <instant|offer> · trade <item> <units> <instant|order> <instant|offer>"
        +" · general buy|sell|flip … · book <book> <instant|order> <instant|offer> · pipeline <item> <batches> <instant|order> <instant|offer> · compactor";
    private static String arg(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c,String name){return StringArgumentType.getString(c,name);}
    private static int units(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> c){return IntegerArgumentType.getInteger(c,"units");}
    /**
     * Tests for each part of trading. Buying and selling are separate parts, each with its own
     * mode, so they can be mixed: Bazaar trades on the production steps, a general flip, a book
     * flip, or a craft pipeline.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> testCommands() {
        return ClientCommands.literal("test").executes(c->{new LiveActions().message(TEST_HELP);return 1;})
            .then(ClientCommands.literal("buy").then(ClientCommands.argument("item",StringArgumentType.string())
                .then(ClientCommands.argument("units",IntegerArgumentType.integer(1,4096))
                    .then(ClientCommands.argument("buy",StringArgumentType.word())
                        .executes(c->trade(arg(c,"item"),units(c),ProductionRun.TradePart.BUY,arg(c,"buy"),null))))))
            .then(ClientCommands.literal("sell").then(ClientCommands.argument("item",StringArgumentType.string())
                .then(ClientCommands.argument("sell",StringArgumentType.word())
                    .executes(c->trade(arg(c,"item"),0,ProductionRun.TradePart.SELL,null,arg(c,"sell"))))))
            .then(ClientCommands.literal("trade").then(ClientCommands.argument("item",StringArgumentType.string())
                .then(ClientCommands.argument("units",IntegerArgumentType.integer(1,4096))
                    .then(ClientCommands.argument("buy",StringArgumentType.word())
                        .then(ClientCommands.argument("sell",StringArgumentType.word())
                            .executes(c->trade(arg(c,"item"),units(c),ProductionRun.TradePart.BOTH,arg(c,"buy"),arg(c,"sell"))))))))
            .then(ClientCommands.literal("compactor")
                .executes(c->trade("DWARVEN_COMPACTOR",1,ProductionRun.TradePart.BOTH,"instant","instant")))
            .then(ClientCommands.literal("general")
                .then(ClientCommands.literal("buy").then(ClientCommands.argument("item",StringArgumentType.string())
                    .then(ClientCommands.argument("units",IntegerArgumentType.integer(1,4096))
                        .then(ClientCommands.argument("buy",StringArgumentType.word())
                            .executes(c->general(arg(c,"item"),units(c),com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart.BUY,arg(c,"buy"),null))))))
                .then(ClientCommands.literal("sell").then(ClientCommands.argument("item",StringArgumentType.string())
                    .then(ClientCommands.argument("sell",StringArgumentType.word())
                        .executes(c->general(arg(c,"item"),0,com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart.SELL,null,arg(c,"sell"))))))
                .then(ClientCommands.literal("flip").then(ClientCommands.argument("item",StringArgumentType.string())
                    .then(ClientCommands.argument("units",IntegerArgumentType.integer(1,4096))
                        .then(ClientCommands.argument("buy",StringArgumentType.word())
                            .then(ClientCommands.argument("sell",StringArgumentType.word())
                                .executes(c->general(arg(c,"item"),units(c),com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart.BOTH,arg(c,"buy"),arg(c,"sell")))))))))
            .then(ClientCommands.literal("book").then(ClientCommands.argument("book",StringArgumentType.string())
                .then(ClientCommands.argument("buy",StringArgumentType.word())
                    .then(ClientCommands.argument("sell",StringArgumentType.word())
                        .executes(c->book(arg(c,"book"),arg(c,"buy"),arg(c,"sell")))))))
            .then(ClientCommands.literal("pipeline").then(ClientCommands.argument("item",StringArgumentType.string())
                .then(ClientCommands.argument("batches",IntegerArgumentType.integer(1,1024))
                    .then(ClientCommands.argument("buy",StringArgumentType.word())
                        .then(ClientCommands.argument("sell",StringArgumentType.word())
                            .executes(c->pipeline(arg(c,"item"),IntegerArgumentType.getInteger(c,"batches"),arg(c,"buy")+" "+arg(c,"sell"))))))));
    }

    /** A general flip test of one Bazaar product on the real general flipper. */
    static int general(String output,int units,com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart part,String buy,String sell){
        var actions=new LiveActions();
        String id=output.trim().toUpperCase(java.util.Locale.ROOT).replace(' ','_');
        boolean instantBuy,instantSell;
        try{instantBuy=part==com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart.SELL||buyMode(buy);
            instantSell=part==com.goofy.goofyaddons.features.generalflipper.GeneralFlipper.TestPart.BUY||sellMode(sell);}
        catch(IllegalArgumentException invalid){actions.message(invalid.getMessage());return 0;}
        if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null) {
            actions.message("Fetching Bazaar prices…");
            com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch().whenComplete((market,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
                if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null)actions.message("Bazaar prices could not be fetched; try again.");
                else general(output,units,part,buy,sell);
            }));
            return 1;
        }
        if(!onBazaar(id)){actions.message(RecipeCatalog.instance().name(id)+" is not a Bazaar product.");return 0;}
        // A configured item keeps its configured Bazaar name; anything else uses the catalog name.
        String name=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE.general.items.stream().filter(i->i.id().equals(id)).map(com.goofy.goofyaddons.features.generalflipper.GeneralItem::name)
            .findFirst().orElse(RecipeCatalog.instance().name(id));
        return FeatureManager.INSTANCE.startGeneralTest(new com.goofy.goofyaddons.features.generalflipper.GeneralItem(id,name),units,instantBuy,instantSell,part)?1:0;
    }

    /** One flip of a configured book on the real book flipper. */
    static int book(String book,String buy,String sell){
        var actions=new LiveActions();
        boolean instantBuy,instantSell;
        try{instantBuy=buyMode(buy);instantSell=sellMode(sell);}
        catch(IllegalArgumentException invalid){actions.message(invalid.getMessage());return 0;}
        if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null) {
            actions.message("Fetching Bazaar prices…");
            com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch().whenComplete((market,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
                if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null)actions.message("Bazaar prices could not be fetched; try again.");
                else book(book,buy,sell);
            }));
            return 1;
        }
        return FeatureManager.INSTANCE.startBookTest(book,instantBuy,instantSell)?1:0;
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
     * a fresh lowest BIN supplied and validated by Coflnet.
     */
    private static int test(String output,long price){
        return test(output,price,false);
    }
    private static int test(String output,long price,boolean marketPricing){
        return test(output,price,marketPricing,false);
    }
    public static int testAuction(String output){return test(output,0,false,true);}
    static String testItem(String output,boolean auctionOnly) {
        String id=output.trim().toUpperCase(java.util.Locale.ROOT).replace(' ','_');
        var recipes=RecipeCatalog.instance().forOutput(id).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT).toList();
        if(!ProductionRecipe.validId(id)||recipes.isEmpty())throw new IllegalArgumentException("No verified crafting recipe for "+id);
        if(auctionOnly&&recipes.stream().anyMatch(r->r.outputCount()!=1))throw new IllegalArgumentException("AH tests require a recipe producing one item per batch");
        return id;
    }
    private static int test(String output,long price,boolean marketPricing,boolean auctionOnly){
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing production.");return 0;}
        String id;
        try{id=testItem(output,auctionOnly);}catch(IllegalArgumentException invalid){new LiveActions().message(invalid.getMessage());return 0;}
        var menu=new com.goofy.goofyaddons.menu.LiveWorld().menu();
        if((price>0||auctionOnly) && menu!=null && menu.slots().stream().anyMatch(s->s.inPlayerInventory() && !s.empty() && id.equals(ProductionMenus.productId(s)))) {
            new LiveActions().message("Move the "+RecipeCatalog.instance().name(id)+" you already hold out of your inventory first, so the listing picks the crafted one.");return 0;
        }
        if(price==0&&auctionOnly){priceFromAuctions(id,true);return 1;}
        if(price==0 && com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null){withBazaarQuotes(id);return 1;}
        if(price==0 && onBazaar(id))price=ProductionRun.SELL_ON_BAZAAR;
        else if(price==0){priceFromAuctions(id,false);return 1;}
        if(!FeatureManager.INSTANCE.production().queue(id,ProductionRecipe.Kind.CRAFT,1,-1,price,price>0?ProductionPlanner.listingFeeLimit(price):0,true,marketPricing))return 0;
        FeatureManager.INSTANCE.startProductionTest();return 1;
    }
    private static final java.net.http.HttpClient AUCTION_HTTP=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(java.time.Duration.ofSeconds(2));
    /** Gets a price-only reference for fee budgeting; a fresh Coflnet price is rechecked before listing. */
    private static void priceFromAuctions(String id,boolean auctionOnly){
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
                        actions.message(String.format(java.util.Locale.ROOT,"Coflnet reference %,d coins%s. The fresh price is rechecked before opening Create Auction.",quote.lowest(),
                                quote.secondLowest()==null?"":String.format(java.util.Locale.ROOT,", next %,d",quote.secondLowest())));
                        test(id,price,true,auctionOnly);
                    }catch(RuntimeException failure){
                        actions.message("No automatic price for "+RecipeCatalog.instance().name(id)+": "+failure.getMessage()+". Use: .a* goofyaddon production "+(auctionOnly?"testah":"test")+" "+id+" <price>");
                    }
                }));
        }catch(RuntimeException failure){actions.message("No automatic price: "+failure.getMessage()+". Use: .a* goofyaddon production "+(auctionOnly?"testah":"test")+" "+id+" <price>");}
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
    /** One named craft through the automatic pipeline (order or instant buys, craft, Bazaar sale) for a chosen batch count. */
    public static int pipeline(String output,int batches,String strategy){
        var actions=new LiveActions();
        if(!FeatureManager.INSTANCE.prepareCrafting()){actions.message("Stop trading and resolve config/order recovery before starting a pipeline test.");return 0;}
        String id;BazaarStrategy chosen;
        try{id=testItem(output,false);chosen=strategy(strategy);}catch(IllegalArgumentException invalid){actions.message(invalid.getMessage());return 0;}
        if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null) {
            actions.message("Fetching Bazaar prices…");
            com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch().whenComplete((market,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
                if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null)actions.message("Bazaar prices could not be fetched; try again.");
                else pipeline(output,batches,strategy);
            }));
            return 1;
        }
        if(!onBazaar(id)){actions.message("Pipeline tests sell on the Bazaar, and "+RecipeCatalog.instance().name(id)+" is not a Bazaar product. Use production testah for auction items.");return 0;}
        if(!FeatureManager.INSTANCE.production().queuePipeline(id,batches,chosen))return 0;
        return FeatureManager.INSTANCE.startProductionTest()?1:0;
    }
    /**
     * "order", "instant", a strategy name such as ORDER_OFFER, or a buy and a sell mode such as
     * "order instant"; empty for the planner's pick.
     */
    static BazaarStrategy strategy(String text){
        if(text==null)return null;
        var words=text.trim().split("\\s+");
        if(words.length==2)return BazaarStrategy.of(buyMode(words[0]),sellMode(words[1]));
        String key=text.trim().toUpperCase(java.util.Locale.ROOT).replace('-','_');
        if(key.equals("ORDER"))return BazaarStrategy.ORDER_OFFER;
        if(key.equals("INSTANT"))return BazaarStrategy.INSTANT_INSTANT;
        try{return BazaarStrategy.valueOf(key);}
        catch(IllegalArgumentException unknown){throw new IllegalArgumentException("Strategy must be order, instant, ORDER_OFFER, INSTANT_OFFER, ORDER_INSTANT, INSTANT_INSTANT, or a buy and a sell mode such as \"order instant\"");}
    }
    /** True for an instant buy, false for a buy order. */
    public static boolean buyMode(String text){
        return switch(text.trim().toLowerCase(java.util.Locale.ROOT)){
            case "instant","insta","instabuy","i" -> true;
            case "order","buyorder","o" -> false;
            default -> throw new IllegalArgumentException("Buy mode must be instant or order, not "+text);
        };
    }
    /** True for an instant sell, false for a sell offer. */
    public static boolean sellMode(String text){
        return switch(text.trim().toLowerCase(java.util.Locale.ROOT)){
            case "instant","insta","instasell","i" -> true;
            case "offer","selloffer","o" -> false;
            default -> throw new IllegalArgumentException("Sell mode must be instant or offer, not "+text);
        };
    }

    /**
     * A Bazaar trade test with no crafting: buy units of one product, sell every unit of it
     * held, or buy and then sell. Each half picks its own mode.
     */
    public static int trade(String output,int units,ProductionRun.TradePart part,String buy,String sell){
        var actions=new LiveActions();
        if(!FeatureManager.INSTANCE.prepareCrafting()){actions.message("Stop trading and resolve config/order recovery before starting a trade test.");return 0;}
        String id=output.trim().toUpperCase(java.util.Locale.ROOT).replace(' ','_');
        BazaarStrategy chosen;
        try {
            if(!ProductionRecipe.validId(id))throw new IllegalArgumentException("Unknown product "+id);
            chosen=BazaarStrategy.of(part.buys()?buyMode(buy):true,part.sells()?sellMode(sell):true);
        }catch(IllegalArgumentException invalid){actions.message(invalid.getMessage());return 0;}
        if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null) {
            actions.message("Fetching Bazaar prices…");
            com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.fetch().whenComplete((market,error)->new com.goofy.goofyaddons.menu.LiveWorld().onClientThread(()->{
                if(com.goofy.goofyaddons.features.bookflipper.helper.BazaarApi.latestFresh()==null)actions.message("Bazaar prices could not be fetched; try again.");
                else trade(output,units,part,buy,sell);
            }));
            return 1;
        }
        if(!onBazaar(id)){actions.message(RecipeCatalog.instance().name(id)+" is not a Bazaar product.");return 0;}
        if(!FeatureManager.INSTANCE.production().queueTrade(id,units,part,chosen))return 0;
        return FeatureManager.INSTANCE.startProductionTest()?1:0;
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
