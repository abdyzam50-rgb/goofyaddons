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
    private String tradeId;
    private int cancellationReopens;
    private boolean awaitingFreshOrder;
    private String orderName;
    private Integer orderTotal;
    private Double orderPrice;

    public void reset() {
        clickedContainer = -1; cancellationContainer = -1;
        useOrdersFallback = false; cancellationAt = 0; tradeId = null;
        cancellationReopens=0;awaitingFreshOrder=false;orderName=null;orderTotal=null;orderPrice=null;
    }

    /** The engine can handle several OUTBID tasks without changing its outer state.
     * Keep duplicate-click protection for the same trade, never inherit it for the next one. */
    public void selectTrade(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing outbid trade identity");
        if (id.equals(tradeId)) return;
        reset();
        tradeId = id;
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
        if (title.startsWith("Bazaar")) {
            int target=com.goofy.goofyaddons.features.transaction.ProductIdentity.searchResult(menu,book.getLevel(book.level()),item);
            if(target>=0)return click(menu,target);
        }
        boolean exactProduct=menu.containerEnd()==36
                && com.goofy.goofyaddons.features.transaction.ProductIdentity.truncatedProductPage(menu,item,15);
        if ((title.endsWith("➜ " + item) || title.endsWith("→ " + item) || exactProduct) && menu.loaded(35)) {
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

    public void rememberOrder(MenuSnapshot menu,int slot) {
        if(orderName!=null)return;
        var item=menu.slot(slot);orderName=item.hoverName();
        orderTotal=com.goofy.goofyaddons.features.generalflipper.OrderLore.total(item.lore());
        orderPrice=com.goofy.goofyaddons.features.profit.TradeReceipts.unitPrice(item.lore());
    }
    public boolean cancellationReopenDue(long now) {
        return cancellationSent() && !awaitingFreshOrder && now-cancellationAt>=5000 && cancellationReopens<2;
    }
    public void reopenCancellation(long now) {
        if(!cancellationReopenDue(now))throw new IllegalStateException("Cancellation reopen is not due");
        cancellationReopens++;awaitingFreshOrder=true;useOrdersFallback=true;clickedContainer=-1;cancellationAt=now;
    }
    public boolean awaitingFreshOrder(){return awaitingFreshOrder;}
    /** The caller also checks uniqueness and the route's allowable quantity before resuming. */
    public boolean resumeFreshOrder(MenuSnapshot menu,int slot,String username) {
        if(!awaitingFreshOrder || menu.containerId()==cancellationContainer || orderTotal==null || orderPrice==null)return false;
        var item=menu.slot(slot);if(item==null || item.empty() || item.inPlayerInventory())return false;
        var price=com.goofy.goofyaddons.features.profit.TradeReceipts.unitPrice(item.lore());
        if(!java.util.Objects.equals(orderName,item.hoverName()) || !orderTotal.equals(com.goofy.goofyaddons.features.generalflipper.OrderLore.total(item.lore()))
                || price==null || Math.abs(price-orderPrice)>0.051
                || !com.goofy.goofyaddons.features.generalflipper.OrderLore.ownOrder(item.lore(),username))return false;
        cancellationContainer=-1;awaitingFreshOrder=false;return true;
    }
    public int cancellationReopens(){return cancellationReopens;}
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
