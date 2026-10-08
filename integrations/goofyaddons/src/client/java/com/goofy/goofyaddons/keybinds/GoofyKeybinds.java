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

    public static KeyMapping toggleKey;
    public static KeyMapping modeKey;
    public static KeyMapping reloadKey;

    public static void register() {
        toggleKey=KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.toggle",InputConstants.Type.KEYBOARD,GoofyConfig.INSTANCE.toggleKey,CATEGORY));
        modeKey=KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.mode",InputConstants.Type.KEYBOARD,GoofyConfig.INSTANCE.modeKey,CATEGORY));
        reloadKey=KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.goofyaddons.reload",InputConstants.Type.KEYBOARD,GoofyConfig.INSTANCE.reloadKey,CATEGORY));
    }
}
