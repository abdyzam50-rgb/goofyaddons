package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.utils.Chat;
import java.util.Set;

/** Item-first navigation and once-only cancellation of the selected buy order. */
public final class BookOutbidFlow {
    public record Navigation(String command, int slot, boolean fallback) {}
    private int clickedContainer = -1, cancellationContainer = -1;
    private boolean useOrdersFallback;
    private long cancellationAt;

    public void reset() {
        clickedContainer = -1; cancellationContainer = -1;
        useOrdersFallback = false; cancellationAt = 0;
    }

    public Navigation navigate(Book book, MenuSnapshot menu) {
        // LiveMenu still observes the player's inventory after a GUI closes.
        // Its null title, rather than a null snapshot, indicates no open screen.
        if (menu == null || menu.title() == null) return new Navigation(useOrdersFallback ? "managebazaarorders"
                : "bz " + book.name().replace("Ultimate", "").strip(), -1, false);
        String title = Chat.strip(menu.title());
        if (TradingSafety.ordersTitle(title) || title.contains("Order") && !title.contains("➜")) return null;
        if (menu.containerId() == clickedContainer) return null;
        String item = book.getRomanLevel(book.level());
        if (title.startsWith("Bazaar") && menu.loaded(53)) {
            var levels = menu.namedInContainer(item);
            if (levels.size() == 1) return click(menu, levels.getFirst());
        }
        if ((title.endsWith("➜ " + item) || title.endsWith("→ " + item)) && menu.loaded(35)) {
            // Use only explicit management controls. A Create Buy Order button must never
            // authorize a replacement while the old order is still live.
            var controls = menu.slots().stream().filter(slot -> !slot.inPlayerInventory()
                    && slot.index() < menu.containerEnd() && !slot.empty()
                    && Set.of("Manage Orders", "View Orders", "Manage Buy Orders", "Your Buy Orders")
                    .contains(Chat.strip(slot.customName()))).toList();
            if (controls.size() == 1) return click(menu, controls.getFirst().index());
            // Uncaptured menu variants retain the working order-list path, with evidence
            // recorded by the engine instead of guessing a transactional slot.
            useOrdersFallback = true;
            clickedContainer = menu.containerId();
            return new Navigation("managebazaarorders", -1, true);
        }
        return null;
    }

    private Navigation click(MenuSnapshot menu, int slot) {
        clickedContainer = menu.containerId();
        return new Navigation(null, slot, false);
    }

    public boolean cancellationSent() { return cancellationContainer != -1; }
    public void sentCancellation(int container, long now) {
        cancellationContainer = container; cancellationAt = now;
    }
    /** The caller must also verify a loaded, settled order list and no matching BUY entry. */
    public boolean freshAfterCancellation(int container) {
        return cancellationSent() && container != cancellationContainer;
    }
    public boolean cancellationTimedOut(long now) {
        return cancellationSent() && now - cancellationAt >= 30_000;
    }
}
