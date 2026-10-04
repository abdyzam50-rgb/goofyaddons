package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.*;

/** Classifies physical loss and newly found route books after refreshing storage evidence. */
public final class BookPopulation {
    private final Map<String,Long> firstSeen = new HashMap<>();
    public void reset() { firstSeen.clear(); }
    public record Difference(int inspectPage, List<BookList> missing, List<BookList> found) {
        public Difference { missing = List.copyOf(missing); found = List.copyOf(found); }
        public boolean changed() { return !missing.isEmpty() || !found.isEmpty(); }
    }

    public Difference inspect(List<BookList> tracked, Collection<Book> routes, InventoryMemory memory) {
        if (!memory.fresh(0) || memory.move() != null) return new Difference(0,List.of(),List.of());
        var missing = new ArrayList<BookList>();
        var found = new ArrayList<BookList>();
        var unique = new LinkedHashSet<Book>(routes);
        for (var entry : tracked) unique.add(entry.book);
        var processed = new HashSet<String>();
        for (var route : unique) for (int level=route.level();level<=route.sellLevel();level++) {
            String key = route.id()+":"+level;
            if (!processed.add(key)) continue;
            final int atLevel = level;
            var sample = new BookList(route,level,0);
            var model = tracked.stream().filter(b -> b.book.id().equals(route.id()) && b.level == atLevel).toList();
            var observed = new ArrayList<InventoryMemory.Address>();
            for (int region=0;region<=2;region++) observed.addAll(memory.matching(sample,region));
            if (observed.size() == model.size()) { firstSeen.remove(key); continue; } // Location changes belong to BookLocations.
            long changed = firstSeen.computeIfAbsent(key,k -> memory.layout(0).observedAt());
            for (int region=0;region<=2;region++) {
                final int r = region;
                var layout = memory.layout(region);
                if (layout != null && memory.matching(sample,region).size() != model.stream().filter(b -> b.location == r).count())
                    changed = Math.max(changed,layout.changedAt());
            }
            for (int page=1;page<=2;page++) {
                var layout = memory.layout(page);
                if (layout == null || layout.observedAt() < changed)
                    return new Difference(page,List.of(),List.of());
            }
            var unmatched = new ArrayList<>(model);
            for (var entry : model) {
                if (observed.remove(new InventoryMemory.Address(entry.location,entry.slot))) unmatched.remove(entry);
            }
            // Preserve same-region copies next. Identical physical copies have no invented identity.
            for (var entry : List.copyOf(unmatched)) {
                var address = observed.stream().filter(a -> a.region() == entry.location).findFirst().orElse(null);
                if (address != null) { observed.remove(address); unmatched.remove(entry); }
            }
            // Remaining copies can explain a move; only an actual quantity deficit is a loss.
            while (!unmatched.isEmpty() && !observed.isEmpty()) { unmatched.removeFirst(); observed.removeFirst(); }
            missing.addAll(unmatched);
            for (var address : observed) {
                var entry = new BookList(route,level,address.region());
                entry.slot = address.slot(); entry.found = true; found.add(entry);
            }
        }
        return new Difference(0,missing,found);
    }
}
