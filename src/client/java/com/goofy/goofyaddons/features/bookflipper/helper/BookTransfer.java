package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.List;
import java.util.regex.Pattern;

/** A storage click is acknowledged only by a matching decrease and increase on the same page. */
public final class BookTransfer {
    public enum Result { WAITING, MOVED, NO_SPACE, BLOCKED }
    private BookList pending;
    private int destination, container, beforeSource, beforeDestination;
    private String title;
    private long started;
    private String failure;
    private java.util.Set<Integer> destinationSlots = java.util.Set.of();
    private final BookActionRetry retry = new BookActionRetry();
    public String failure() { return failure; }
    public boolean pending() { return pending != null; }
    public int actionRetries() { return retry.retries(); }
    public void slowdown(long now) { if (pending()) retry.slowdown(now); }
    public void reset() { pending=null;failure=null;retry.reset(); }

    /** Production path: bind the click and its acknowledgment to confirmed slot layouts. */
    public Result tick(BookList book,int target,String pageCommand,MenuSnapshot menu,GameActions actions,long now,InventoryMemory memory) {
        int page = target == 0 ? book.location : target;
        if (failure != null) return Result.BLOCKED;
        if (menu == null || !pageMatches(menu.title(),pageCommand))
            return block("Storage page identity differs or is unsupported; no book location changed.");
        if (pending != null && now-started >= 30_000 && (!memory.fresh(0) || !memory.fresh(page)))
            return block("Book transfer timed out without matched arrival; location retained.");
        if (!memory.fresh(0) || !memory.fresh(page)) { retry.clearObservation();return Result.WAITING; }
        boolean wasPending = pending != null;
        if (!wasPending) {
            if (target==book.location || (target!=0 && book.location!=0)) return block("Invalid book transfer direction.");
            var source = memory.matching(book,book.location);
            if (source.isEmpty()) return block("Tracked book is absent from the confirmed source layout; reconcile locations first.");
            if ((book.location==0?menu.emptyContainerSlots():menu.emptyInventorySlots())==0) return Result.NO_SPACE;
            var bound = source.stream().filter(a -> a.slot() == book.slot).findFirst().orElse(source.getFirst());
            book.slot = bound.slot();
            destinationSlots = memory.matching(book,target).stream().map(InventoryMemory.Address::slot)
                    .collect(java.util.stream.Collectors.toSet());
            memory.beginMove(bound,target,now);
        }
        var result = tick(book,target,pageCommand,menu,actions,now);
        if (result == Result.MOVED) {
            var arrived = memory.matching(book,target).stream().filter(a -> !destinationSlots.contains(a.slot())).toList();
            // Counts proved arrival, but indistinguishable copies may also have rearranged slots.
            book.slot = arrived.size() == 1 ? arrived.getFirst().slot() : -1;
            memory.finishMove();
        }
        return result;
    }

    public static boolean pageMatches(String title,String command) {
        var cmd=Pattern.compile("(?i)ec(?:\\s+(\\d+))?").matcher(command.strip());
        var page=Pattern.compile("Ender Chest \\((\\d+)/(\\d+)\\)").matcher(Chat.strip(title));
        if (!cmd.matches() || !page.matches()) return false;
        try {
            int expected=cmd.group(1)==null?1:Integer.parseInt(cmd.group(1));
            int actual=Integer.parseInt(page.group(1)),total=Integer.parseInt(page.group(2));
            return expected>0 && actual==expected && actual<=total;
        } catch (NumberFormatException bad) { return false; }
    }

    public Result tick(BookList book,int target,String pageCommand,MenuSnapshot menu,GameActions actions,long now) {
        if (failure!=null) return Result.BLOCKED;
        if (menu==null || !pageMatches(menu.title(),pageCommand)) return block("Storage page identity differs or is unsupported; no book location changed.");
        if (pending!=null && (pending!=book || destination!=target || container!=menu.containerId() || !title.equals(menu.title())))
            return block("Storage context changed during a transfer; ownership retained.");
        if (!menu.cursorEmpty()) {
            retry.clearObservation();
            if (pending!=null && now-started>=30_000) return block("Book transfer timed out without matched arrival; location retained.");
            return Result.WAITING;
        }
        boolean storing=book.location==0;
        var source=matching(menu,book,storing);
        var dest=matching(menu,book,!storing);
        if (pending==null) {
            if (target==book.location || (target!=0 && book.location!=0)) return block("Invalid book transfer direction.");
            if (source.isEmpty()) return block("Tracked book is missing from the transfer source; location retained.");
            if ((storing?menu.emptyContainerSlots():menu.emptyInventorySlots())==0) return Result.NO_SPACE;
            pending=book;destination=target;container=menu.containerId();title=menu.title();started=now;
            beforeSource=source.size();beforeDestination=dest.size();
            int click = source.stream().filter(index -> (storing ? menu.slot(index).containerSlot() : index) == book.slot)
                    .findFirst().orElse(source.getFirst());
            retry.sent(click,true,now);
            actions.click(click,true);
            return Result.WAITING;
        }
        if (source.size()==beforeSource-1 && dest.size()==beforeDestination+1) {
            book.location=target;
            reset();
            return Result.MOVED;
        }
        if (source.size()<beforeSource-1 || dest.size()>beforeDestination+1)
            return block("Unexpected book quantities during storage transfer; location retained.");
        if (now-started>=30_000) return block("Book transfer timed out without matched arrival; location retained.");
        retry.retry(menu,source.size()==beforeSource && dest.size()==beforeDestination
                && source.contains(retry.slot())
                && (storing?menu.emptyContainerSlots():menu.emptyInventorySlots())>0,actions,now);
        return Result.WAITING;
    }
    private Result block(String reason) { failure=reason;return Result.BLOCKED; }
    private static List<Integer> matching(MenuSnapshot menu,BookList book,boolean inventory) {
        String key=MenuSnapshot.enchantmentKey(book.book.id());
        return menu.slots().stream().filter(slot -> slot.inPlayerInventory()==inventory && !slot.empty()
                && (!inventory || slot.containerSlot()>=0 && slot.containerSlot()<36)
                && slot.enchantedBook() && slot.count()==1 && slot.enchantments()!=null && slot.enchantments().size()==1
                && Integer.valueOf(book.level).equals(slot.enchantments().get(key))).map(SlotView::index).toList();
    }
}
