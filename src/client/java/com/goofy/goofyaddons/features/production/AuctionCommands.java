package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import java.nio.file.*;
import java.util.*;

public final class AuctionCommands {
    private AuctionCommands(){}
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(
            ClientCommands.literal("goofyauction")
                .then(ClientCommands.literal("inspect").executes(c->inspect()))
                .then(ClientCommands.literal("prepare").then(ClientCommands.argument("product",StringArgumentType.word())
                    .then(ClientCommands.argument("price",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .executes(c->queue(StringArgumentType.getString(c,"product"),LongArgumentType.getLong(c,"price"),false,0)))))
                .then(ClientCommands.literal("sell").then(ClientCommands.argument("product",StringArgumentType.word())
                    .then(ClientCommands.argument("price",LongArgumentType.longArg(1,1_000_000_000_000L))
                        .then(ClientCommands.argument("maximumFee",LongArgumentType.longArg(0,1_000_000_000_000L))
                            .executes(c->queue(StringArgumentType.getString(c,"product"),LongArgumentType.getLong(c,"price"),true,LongArgumentType.getLong(c,"maximumFee")))))))));
    }
    private static int queue(String product,long price,boolean publish,long maximumFee) {
        if(!FeatureManager.INSTANCE.prepareCrafting()){new LiveActions().message("Stop trading and resolve config/order recovery before queueing an auction.");return 0;}
        return FeatureManager.INSTANCE.auction().queue(product.toUpperCase(Locale.ROOT),price,publish,maximumFee)?1:0;
    }
    /** Export only GUI facts, with no credentials, raw NBT, player identity or pet UUIDs. */
    private static int inspect() {
        var menu=new LiveWorld().menu();var actions=new LiveActions();
        if(menu==null || menu.title()==null){actions.message("Open the auction menu you want to inspect first.");return 0;}
        try {
            Path path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("goofyaddons-auction-menu.json");
            Files.writeString(path,describe(menu),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            actions.message("Saved GUI title, controls and descriptions to config/goofyaddons-auction-menu.json.");return 1;
        }catch(java.io.IOException failed){actions.message("Auction menu capture could not be saved.");return 0;}
    }
    static String describe(MenuSnapshot menu) {
        var root=new JsonObject();root.addProperty("title",menu.title());var slots=new JsonArray();
        for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty()) {
            var row=new JsonObject();row.addProperty("slot",slot.index());row.addProperty("name",slot.hoverName());
            row.addProperty("productId",slot.customId());row.addProperty("count",slot.count());row.add("lore",new Gson().toJsonTree(slot.loreLines()));slots.add(row);
        }
        root.add("slots",slots);return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
