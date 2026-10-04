package com.goofy.goofyaddons.keybinds;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class GoofyKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("goofyaddons", "category")
    );

    public static KeyMapping startKey;
    public static KeyMapping stopKey;
    public static KeyMapping modeKey;
    public static KeyMapping reloadKey;

    public static void register() {
        reloadKey=KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.reload",InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSLASH,CATEGORY));
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.mode", InputConstants.Type.KEYSYM,
                GoofyConfig.INSTANCE.modeKey, CATEGORY));
        startKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.start",
                InputConstants.Type.KEYSYM,
                GoofyConfig.INSTANCE.startKey,
                CATEGORY
        ));

        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.stop",
                InputConstants.Type.KEYSYM,
                GoofyConfig.INSTANCE.stopKey,
                CATEGORY
        ));
    }
}
