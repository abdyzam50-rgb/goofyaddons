package com.goofy.goofyaddons.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that turns the live game's open menu into a {@link MenuSnapshot}.
 *
 * <p>This is the only Minecraft-touching half of the observation seam. Everything that
 * decides anything reads the snapshot instead, so those decisions can be exercised by
 * tests that describe a menu directly.
 */
public final class LiveMenu {
    private LiveMenu() {}

    /** Null when there is no player, so callers fail closed rather than see an empty menu. */
    public static MenuSnapshot read() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory inventory = minecraft.player.getInventory();
        List<SlotView> views = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) views.add(view(slot, slot.container == inventory));
        return new MenuSnapshot(menu.containerId,
                minecraft.screen == null ? null : minecraft.screen.getTitle().getString(),
                menu.getCarried().isEmpty(), views);
    }

    private static SlotView view(Slot slot, boolean inPlayerInventory) {
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return SlotView.empty(slot.index, inPlayerInventory, slot.getContainerSlot());
        ItemLore lore = item.get(DataComponents.LORE);
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? null : data.copyTag();
        return new SlotView(slot.index, inPlayerInventory, slot.getContainerSlot(), false,
                item.getCustomName() == null ? null : item.getCustomName().getString(),
                item.getHoverName().getString(),
                lore == null ? null : lore.lines().stream().map(Component::getString).toList(),
                tag == null ? null : tag.getStringOr("id", ""),
                enchantments(tag),
                item.getCount(), item.getMaxStackSize());
    }

    /**
     * Resolves each level the way the game would, so an unreadable value becomes -1
     * here rather than disappearing and changing a count. Insertion order is kept
     * because {@link MenuSnapshot#levelAt} reads whichever the game lists first.
     */
    private static Map<String, Integer> enchantments(CompoundTag tag) {
        if (tag == null) return null;
        CompoundTag enchants = tag.getCompound("enchantments").orElse(null);
        if (enchants == null) return null;
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String key : enchants.keySet()) levels.put(key, enchants.getIntOr(key, -1));
        return levels;
    }
}
