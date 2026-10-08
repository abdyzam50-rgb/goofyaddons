package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import java.nio.file.*;
import java.util.*;

public final class AuctionCommands {
    private static MenuSnapshot lastAuctionMenu;
    private AuctionCommands(){}
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            var menu=new LiveWorld().menu();
            if(menu!=null && Set.of("Auction House","Manage Auctions","Auctions Browser","Auction Browser","Create Auction",
                "Create BIN Auction","Confirm BIN Auction","Confirm Auction","BIN Auction View","Confirm Purchase","Auction Duration")
                .contains(com.goofy.goofyaddons.utils.Chat.strip(menu.title())))lastAuctionMenu=menu;
            if(client.player==null)lastAuctionMenu=null;
        });
        registerCommands();
    }
    private static int queue(String product,long price,boolean publish,long maximumFee) {
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing an auction.");return 0;}
        return FeatureManager.INSTANCE.auction().queue(product.toUpperCase(Locale.ROOT),price,publish,maximumFee)?1:0;
    }
    /** Export only GUI facts, with no credentials, raw NBT, player identity or pet UUIDs. */
    private static int inspect() {
        var menu=new LiveWorld().menu();var actions=new LiveActions();
        // Opening chat to type a command can close a container. Preserve the latest auction screen.
        if(lastAuctionMenu!=null)menu=lastAuctionMenu;
        if(menu==null || menu.title()==null){actions.message("Open the auction menu you want to inspect first.");return 0;}
        try {
            capture(menu);
            actions.message("Saved GUI title, controls and descriptions to config/goofyaddons-auction-menu.json.");return 1;
        }catch(java.io.IOException failed){actions.message("Auction menu capture could not be saved.");return 0;}
    }
    public static void capture(MenuSnapshot menu)throws java.io.IOException {
        lastAuctionMenu=menu;
        Path path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-auction-menu.json");
        Files.writeString(path,describe(menu),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
    }
    static String describe(MenuSnapshot menu) {
        var root=new JsonObject();root.addProperty("title",menu.title());var slots=new JsonArray();
        for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty()) {
            var row=new JsonObject();row.addProperty("slot",slot.index());row.addProperty("name",slot.hoverName());
            row.addProperty("productId",slot.customId());row.addProperty("count",slot.count());row.add("lore",new Gson().toJsonTree(slot.loreLines()));slots.add(row);
        }
        root.add("slots",slots);return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    /** Attach commands without initializing live rendering, menus or log files. */
    public static void registerCommands() {
        com.goofy.goofyaddons.commands.GoofyCommands.register(dispatcher->dispatcher.register(
            ClientCommands.literal("auction")
                .then(ClientCommands.literal("inspect").executes(c->inspect()))
                .then(ClientCommands.literal("prepare").then(ClientCommands.argument("product",StringArgumentType.word())
                    .then(ClientCommands.argument("price",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .executes(c->queue(StringArgumentType.getString(c,"product"),LongArgumentType.getLong(c,"price"),false,0)))))
                .then(ClientCommands.literal("sell").then(ClientCommands.argument("product",StringArgumentType.word())
                    .then(ClientCommands.argument("price",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .then(ClientCommands.argument("maximumFee",LongArgumentType.longArg(0,1_000_000_000_000L))
                            .executes(c->queue(StringArgumentType.getString(c,"product"),LongArgumentType.getLong(c,"price"),true,LongArgumentType.getLong(c,"maximumFee")))))))));
    }
}
