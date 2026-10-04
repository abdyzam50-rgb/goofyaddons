package com.goofy.goofyaddons.utils;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.generalflipper.OrderLore;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.menu.*;
import java.util.List;
import java.util.function.Supplier;

/** Queries over an observation; the book engine supplies its one snapshot per tick. */
public final class InventoryScanner {
    private static final MenuSnapshot UNAVAILABLE = new MenuSnapshot(-1,null,false,List.of());
    private final Supplier<MenuSnapshot> observation;

    public InventoryScanner() { this(LiveMenu::read); }
    public InventoryScanner(Supplier<MenuSnapshot> observation) {
        this.observation=java.util.Objects.requireNonNull(observation);
    }
    private MenuSnapshot menu() {
        var snapshot=observation.get();
        return snapshot==null?UNAVAILABLE:snapshot;
    }
    public List<Integer> findContainer(String name) { return menu().namedInContainer(name); }
    public List<Integer> findLoreInv(String lore) { return menu().bookLoreInInventory(lore); }
    public List<Integer> findLoreContainer(String lore) { return menu().bookLoreInContainer(lore); }
    public int checkOrder(int slot) {
        var item=menu().slot(slot);
        return item==null || item.loreLines()==null?0:OrderLore.claimable(item.lore(),true);
    }
    public double getUnitPrice(int slot) {
        var item=menu().slot(slot);
        Double price=item==null?null:TradeReceipts.unitPrice(item.lore());
        return price==null?0:price;
    }
    public int getEmptyInventorySlots() { return menu().emptyInventorySlots(); }
    public int getEmptyContainerSlots() { return menu().emptyContainerSlots(); }
    public boolean findMisMatch(String lore) { return menu().anvilInputsMismatch(lore); }
    public List<Integer> matchingBookInContainer(Book book) {
        return menu().matchingBookInContainer(MenuSnapshot.enchantmentKey(book.id()));
    }
    public List<Integer> matchingBookInInventory(Book book) {
        return menu().matchingBookInInventory(MenuSnapshot.enchantmentKey(book.id()));
    }
    public int getLevel(int slot) { return menu().levelAt(slot); }
    public boolean isMenuLoaded(int slot) { return menu().loaded(slot); }
}
