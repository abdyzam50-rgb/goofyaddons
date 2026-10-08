package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.*;

/** Reassign fungible books only when a fresh inventory/page pair conserves their quantity. */
public final class BookLocations {
    public record Result(int corrected, int inspectPage, boolean unresolved) {}

    public Result reconcile(List<BookList> books, InventoryMemory memory, int visiblePage) {
        if (!memory.fresh(0) || memory.move() != null) return new Result(0,0,false);
        int corrected = 0;
        var groups = new LinkedHashMap<String,List<BookList>>();
        for (var book : books) groups.computeIfAbsent(book.book.id()+":"+book.level,k -> new ArrayList<>()).add(book);
        for (var group : groups.values()) {
            var sample = group.getFirst();
            if (visiblePage > 0 && memory.fresh(visiblePage)) {
                var pair = group.stream().filter(b -> b.location == 0 || b.location == visiblePage).toList();
                var observed = new ArrayList<>(memory.matching(sample,0)); observed.addAll(memory.matching(sample,visiblePage));
                if (pair.size() == observed.size()) {
                    // Preserve exact bindings first, then same-region copies, before correcting cross-region moves.
                    var unbound = new ArrayList<>(pair);
                    for (var book : pair) {
                        var address = new InventoryMemory.Address(book.location,book.slot);
                        if (observed.remove(address)) unbound.remove(book);
                    }
                    for (var book : List.copyOf(unbound)) {
                        var address = observed.stream().filter(a -> a.region() == book.location).findFirst().orElse(null);
                        if (address != null) { book.slot = address.slot(); observed.remove(address); unbound.remove(book); }
                    }
                    for (var book : unbound) {
                        var address = observed.removeFirst();
                        if (book.location != address.region()) corrected++;
                        book.location = address.region(); book.slot = address.slot();
                    }
                }
            } else {
                // Exact inventory slots can be refreshed without adopting any closed-page inference.
                var held = group.stream().filter(b -> b.location == 0).toList();
                var observed = new ArrayList<>(memory.matching(sample,0));
                if (held.size() == observed.size()) {
                    for (var book : held) {
                        var address = new InventoryMemory.Address(0,book.slot);
                        if (observed.remove(address)) continue;
                        book.slot = -1;
                    }
                    for (var book : held) if (book.slot < 0) book.slot = observed.removeFirst().slot();
                }
            }
        }
        // A mismatch calls for observing storage, never inventing a destination from cached contents.
        for (var group : groups.values()) {
            var sample = group.getFirst();
            long changed = 0;
            boolean mismatch = false;
            for (int region = 0; region <= 2; region++) {
                final int r = region;
                var layout = memory.layout(region);
                long expected = group.stream().filter(b -> b.location == r).count();
                if (layout == null) {
                    if (expected > 0) return new Result(corrected,region,false);
                } else if (memory.matching(sample,region).size() != expected) {
                    mismatch = true; changed = Math.max(changed,layout.changedAt());
                }
            }
            if (!mismatch) continue;
            for (int page = 1; page <= 2; page++) {
                var layout = memory.layout(page);
                if (layout == null || layout.observedAt() < changed) return new Result(corrected,page,false);
            }
            var observed = new ArrayList<InventoryMemory.Address>();
            for (int region = 0; region <= 2; region++) observed.addAll(memory.matching(sample,region));
            if (observed.size() != group.size()) return new Result(corrected,0,true);
            // Both pages were inspected after the discrepancy. This also resolves page-to-page moves.
            var unbound = new ArrayList<>(group);
            for (var book : group) {
                if (observed.remove(new InventoryMemory.Address(book.location,book.slot))) unbound.remove(book);
            }
            for (var book : List.copyOf(unbound)) {
                var address = observed.stream().filter(a -> a.region() == book.location).findFirst().orElse(null);
                if (address != null) { book.slot = address.slot(); observed.remove(address); unbound.remove(book); }
            }
            for (var book : unbound) {
                var address = observed.removeFirst();
                if (book.location != address.region()) corrected++;
                book.location = address.region(); book.slot = address.slot();
            }
        }
        return new Result(corrected,0,false);
    }
}
