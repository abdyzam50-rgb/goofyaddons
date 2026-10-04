package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.SlotView;
import java.util.*;

/** Slot history. Closed pages retain observations, never a claim to current visibility. */
public final class InventoryMemory {
    public record Address(int region, int slot) {}
    public record Item(String id, String name, Map<String,Integer> enchantments, int count) {
        public Item { enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments); }
        public boolean matches(BookList book) {
            return SlotView.ENCHANTED_BOOK.equals(id) && count == 1 && enchantments.size() == 1
                    && Integer.valueOf(book.level).equals(enchantments.get(MenuSnapshot.enchantmentKey(book.book.id())));
        }
    }
    public record Change(Address address, Item before, Item after) {}
    public record Move(Address source, int destinationRegion, Item item, long started) {}
    public record Layout(Map<Integer,Item> previous, Map<Integer,Item> current, long observedAt, long changedAt) {
        public Layout { previous = Collections.unmodifiableMap(new TreeMap<>(previous)); current = Collections.unmodifiableMap(new TreeMap<>(current)); }
    }
    private record Candidate(Map<Integer,Item> slots, int container, long since) {}
    private final Map<Integer,Layout> layouts = new TreeMap<>();
    private final Map<Integer,Candidate> candidates = new HashMap<>();
    private final Set<Integer> fresh = new HashSet<>();
    private Move move;
    private boolean cursorEmpty;

    public void reset() { layouts.clear(); candidates.clear(); fresh.clear(); move = null; cursorEmpty = false; }
    public Layout layout(int region) { return layouts.get(region); }
    public boolean fresh(int region) { return cursorEmpty && fresh.contains(region); }
    public Move move() { return move; }
    public void beginMove(Address source, int destination, long now) {
        var layout = layouts.get(source.region());
        if (move != null || !fresh(source.region()) || layout == null || layout.current().get(source.slot()) == null)
            throw new IllegalStateException("Transfer source has no confirmed slot observation");
        move = new Move(source, destination, layout.current().get(source.slot()), now);
    }
    public void finishMove() { move = null; }

    /** Accept a complete layout only after it remains unchanged for 150ms in this container. */
    public void observe(MenuSnapshot menu, int storageRegion, long now) {
        fresh.clear();
        if (menu == null) { candidates.clear(); cursorEmpty = false; return; }
        cursorEmpty = menu.cursorEmpty();
        var inventory = new TreeMap<Integer,Item>();
        var storage = new TreeMap<Integer,Item>();
        for (var slot : menu.slots()) {
            if (slot.inPlayerInventory() && slot.containerSlot() >= 0 && slot.containerSlot() < 36)
                inventory.put(slot.containerSlot(), item(slot));
            else if (!slot.inPlayerInventory() && storageRegion > 0) storage.put(slot.index(), item(slot));
        }
        Set<Integer> visible = new HashSet<>();
        if (menu.carried() != null) {
            var cursor = new TreeMap<Integer,Item>(); cursor.put(0,item(menu.carried()));
            visible.add(-1); accept(-1,cursor,menu.containerId(),now);
        }
        if ("Anvil".equals(com.goofy.goofyaddons.utils.Chat.strip(menu.title())) && menu.slot(33) != null) {
            var anvil = new TreeMap<Integer,Item>();
            anvil.put(29,item(menu.slot(29))); anvil.put(33,item(menu.slot(33)));
            visible.add(-2); accept(-2,anvil,menu.containerId(),now);
            // Slot 13 is the result preview; slot 22 is the combine/collection button.
            // Keep both observations separate from owned anvil inputs.
            var preview = new TreeMap<Integer,Item>();
            preview.put(13,item(menu.slot(13))); preview.put(22,item(menu.slot(22)));
            visible.add(-3); accept(-3,preview,menu.containerId(),now);
        }
        if (inventory.size() == 36) { visible.add(0); accept(0, inventory, menu.containerId(), now); }
        if (storageRegion > 0 && storage.size() == menu.containerEnd() && !storage.isEmpty()) {
            visible.add(storageRegion); accept(storageRegion, storage, menu.containerId(), now);
        }
        candidates.keySet().retainAll(visible);
    }
    private void accept(int region, Map<Integer,Item> slots, int container, long now) {
        var candidate = candidates.get(region);
        if (candidate == null || candidate.container() != container || !candidate.slots().equals(slots)) {
            candidates.put(region, new Candidate(Collections.unmodifiableMap(new TreeMap<>(slots)), container, now));
            return;
        }
        if (now - candidate.since() < 150) return;
        var last = layouts.get(region);
        boolean changed = last == null || !last.current().equals(slots);
        layouts.put(region, new Layout(last == null ? Map.of() : changed ? last.current() : last.previous(),
                slots, now, changed ? candidate.since() : last.changedAt()));
        fresh.add(region);
    }
    private static Item item(SlotView slot) {
        return slot.empty() ? null : new Item(slot.customId(), slot.hoverName(), slot.enchantments(), slot.count());
    }
    public List<Address> matching(BookList book, int region) {
        var layout = layouts.get(region);
        if (layout == null) return List.of();
        return layout.current().entrySet().stream().filter(e -> e.getValue() != null && e.getValue().matches(book))
                .map(e -> new Address(region, e.getKey())).toList();
    }
    public List<Change> changes(int region) {
        var layout = layouts.get(region);
        if (layout == null) return List.of();
        var slots = new TreeSet<>(layout.previous().keySet()); slots.addAll(layout.current().keySet());
        return slots.stream().filter(s -> !Objects.equals(layout.previous().get(s), layout.current().get(s)))
                .map(s -> new Change(new Address(region,s),layout.previous().get(s),layout.current().get(s))).toList();
    }
    public Map<String,Object> diagnosticState() {
        var regions = new ArrayList<Map<String,Object>>();
        for (var e : layouts.entrySet()) {
            var l = e.getValue();
            regions.add(Map.of("region",e.getKey(),"fresh",fresh(e.getKey()),"observedAt",l.observedAt(),
                    "changedAt",l.changedAt(),"previous",occupied(l.previous()),"current",occupied(l.current())));
        }
        return Map.of("regions",regions,"pendingMove",move == null ? "none" : Map.of(
                "fromRegion",move.source().region(),"fromSlot",move.source().slot(),"toRegion",move.destinationRegion(),
                "item",move.item(),"started",move.started()),"cursorEmpty",cursorEmpty);
    }
    private static List<Map<String,Object>> occupied(Map<Integer,Item> slots) {
        return slots.entrySet().stream().filter(e -> e.getValue() != null)
                .map(e -> Map.<String,Object>of("slot",e.getKey(),"item",e.getValue())).toList();
    }
}
