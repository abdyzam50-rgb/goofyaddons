package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
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
import java.util.List;

public class InventoryScanner {
    private Minecraft minecraft = Minecraft.getInstance();

    public List<Integer> findInv(String name) {
        List<Integer> slots = new ArrayList<>();
        Inventory playerInv = minecraft.player.getInventory();
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv || slot.getContainerSlot()<0 || slot.getContainerSlot()>=36) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().replaceAll("§.","").equals(name)) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    public List<Integer> getSellOrder() {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().contains("SELL")) continue;
            slots.add(i);
        }
        return slots;
    }

    public String getName(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        ItemStack itemStack = menu.slots.get(slot).getItem();
        return itemStack.getHoverName().getString().replaceAll("§.","");
    }

    public List<Integer> findContainer(String name) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            if (item.getCustomName() == null) continue;
            if (!item.getCustomName().getString().replaceAll("§.","").equals(name)) continue;
            slots.add(i);
        }
        return slots;
    }

    public List<Integer> findLoreInv(String string) {
        List<Integer> slots = new ArrayList<>();
        Inventory playerInv = minecraft.player.getInventory();
        AbstractContainerMenu menu = minecraft.player.containerMenu;

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv || slot.getContainerSlot()<0 || slot.getContainerSlot()>=36) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (!isEnchantedBook(item) || lore == null || !lore.lines().stream().anyMatch(l -> l.getString().replaceAll("§.","").equals(string))) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    public List<Integer> findLoreContainer(String string) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (!isEnchantedBook(item) || lore == null || !lore.lines().stream().anyMatch(l -> l.getString().replaceAll("§.","").equals(string))) continue;
            slots.add(i);
        }
        return slots;
    }

    public int checkOrder(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore == null) return 0;
        return com.goofy.goofyaddons.features.generalflipper.OrderLore.claimable(
                String.join("\n",lore.lines().stream().map(Component::getString).toList()),true);
    }

    public double getUnitPrice(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        ItemLore itemLore = itemStack.get(DataComponents.LORE);
        if (itemLore == null) return 0;
        Double price=com.goofy.goofyaddons.features.profit.TradeReceipts.unitPrice(
                String.join("\n",itemLore.lines().stream().map(Component::getString).toList()));
        return price==null ? 0 : price;
    }

    public int getEmptyInventorySlots() {
        int amount = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory playerInv = minecraft.player.getInventory();

        for (Slot slot : menu.slots) {
            if (slot.container != playerInv || slot.getContainerSlot()<0 || slot.getContainerSlot()>=36) continue;

            if (slot.hasItem()) continue;
            amount++;
        }

        return amount;
    }

    public int getEmptyContainerSlots() {
        int amount = 0;
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory playerInv = minecraft.player.getInventory();

        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) continue;

            if (slot.hasItem()) continue;
            amount++;
        }

        return amount;
    }

    public boolean findMisMatch(String string) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu.slots.size() <= 33) return false;
        if (!menu.slots.get(29).hasItem() || !menu.slots.get(33).hasItem()) return false;
        ItemStack item = menu.slots.get(29).getItem();
        ItemStack item2 = menu.slots.get(33).getItem();
        ItemLore lore = item.get(DataComponents.LORE);
        ItemLore lore2 = item2.get(DataComponents.LORE);
        if (lore == null || lore2 == null) return false;
        if (lore.lines().stream().anyMatch(l -> l.getString().replaceAll("§.","").equals(string)) && lore2.lines().stream().anyMatch(l -> l.getString().replaceAll("§.","").equals(string)))
            return false;
        return true;
    }

    public List<Integer> matchingBookInContainer(Book book) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        int end = menu.slots.size() - 36;
        for (int i = 0; i < end; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (!matchesBook(item,book)) continue;
            slots.add(i);
        }
        return slots;
    }

    public List<Integer> matchingBookInInventory(Book book) {
        List<Integer> slots = new ArrayList<>();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        Inventory inventory = minecraft.player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container != inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            ItemLore lore = item.get(DataComponents.LORE);
            if (!matchesBook(item,book)) continue;
            slots.add(slot.index);
        }
        return slots;
    }

    private static boolean isEnchantedBook(ItemStack item) {
        CustomData data=item.get(DataComponents.CUSTOM_DATA);
        return data!=null && "ENCHANTED_BOOK".equals(data.copyTag().getStringOr("id",""));
    }
    private static boolean matchesBook(ItemStack item,Book book) {
        CustomData data=item.get(DataComponents.CUSTOM_DATA);
        if(data==null || !"ENCHANTED_BOOK".equals(data.copyTag().getStringOr("id",""))) return false;
        CompoundTag enchants=data.copyTag().getCompound("enchantments").orElse(null);
        if(enchants==null || enchants.keySet().size()!=1) return false;
        return enchants.getIntOr(book.id().substring("ENCHANTMENT_".length()).toLowerCase(java.util.Locale.ROOT),-1)>0;
    }
    public int getLevel(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        ItemStack itemStack = menu.slots.get(slot).getItem();
        if (itemStack.isEmpty()) return -1;
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return -1;
        CompoundTag tag = customData.copyTag().getCompound("enchantments").orElse(null);
        if (tag == null) return -1;
        if (tag.keySet().isEmpty()) return -1;
        String id = tag.keySet().iterator().next();

        return tag.getIntOr(id, -1);
    }

    public boolean isMenuLoaded(int slot) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        return slot >= 0 && slot < menu.slots.size() && menu.slots.get(slot).hasItem();
    }
}