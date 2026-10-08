package com.goofy.goofyaddons.event;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatHook {
    private static List<HOOK> hookList = new ArrayList<>();

    public static void register() {
        ClientReceiveMessageEvents.GAME.register(ChatHook::onChatMessage);
        ClientReceiveMessageEvents.CHAT.register((message,signed,sender,type,time)->
            com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.chat(message.getString()));
    }

    public static void onMessage(String pattern, Consumer<String> string) {
        hookList.add(new HOOK(pattern, string));
    }


    private static void onChatMessage(Component message, boolean overlay) {
        if (overlay == true) return;
        String text = message.getString().replaceAll("§.", "");
        if (text.startsWith("[GoofyAddons]")) return;
        if(text.startsWith("[Bazaar]")) Diagnostics.event("INFO","bazaar.receipt",java.util.Map.of("message",text));
        if(text.startsWith("You are now playing on profile:") || text.startsWith("You switched to profile")) {
            com.goofy.goofyaddons.features.FeatureManager.INSTANCE.clearAccountRequirements();
            com.goofy.goofyaddons.features.FeatureManager.INSTANCE.safetyPause("Profile changed; restart trading to check account requirements");
        }
        com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.chat(text);
        for (HOOK hook : hookList) {
            if (!text.contains(hook.pattern)) continue;
            try { hook.string.accept(text); } catch(RuntimeException failure) { Diagnostics.failure("chat.handler_failed",failure);com.goofy.goofyaddons.features.FeatureManager.INSTANCE.safetyPause("Chat receipt processing failed."); }
        }
    }

    record HOOK(String pattern, Consumer<String> string) {
    }
}
