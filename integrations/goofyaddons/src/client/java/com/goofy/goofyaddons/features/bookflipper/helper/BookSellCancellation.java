package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.goofy.goofyaddons.menu.SlotView;
import com.goofy.goofyaddons.features.TradingSafety;
import java.util.List;

/** Keeps a refunded output attached to its trade while cancellation packets settle. */
public final class BookSellCancellation {
    public enum Result { WAITING, RETURNED, BLOCKED }
    private Task task;
    private int origin, observed = -1;
    private long started, stableAt;
    private List<SlotView> contents = List.of();
    public boolean pending() { return task != null; }
    public Task task() { return task; }
    public void reset() { task=null; observed=-1; contents=List.of(); }
    public void start(Task owner, int container, long now) {
        if (pending()) throw new IllegalStateException("Sell cancellation already pending");
        task=owner; origin=container; started=now; observed=-1; contents=List.of();
    }
    public Result observe(MenuSnapshot menu, long now) {
        if (!pending()) throw new IllegalStateException("No sell cancellation pending");
        if (now-started >= 30_000) return Result.BLOCKED;
        if (menu==null || menu.containerId()==origin || !TradingSafety.ordersTitle(menu.title())
                || !menu.loaded(35) || !menu.cursorEmpty()) return Result.WAITING;
        if (observed!=menu.containerId() || !contents.equals(menu.slots())) {
            observed=menu.containerId(); contents=List.copyOf(menu.slots()); stableAt=now;
            return Result.WAITING;
        }
        if (now-stableAt<1500) return Result.WAITING;
        if (!menu.namedInContainer("SELL "+task.getBook().getRomanLevel(task.getBook().sellLevel())).isEmpty())
            return Result.WAITING;
        var slots=menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()>=0
                && s.containerSlot()<36).toList();
        if (slots.size()!=36) return Result.WAITING;
        var returned=slots.stream().filter(s->s.enchantedBook() && s.count()==1 && s.enchantments()!=null
                && s.enchantments().size()==1 && Integer.valueOf(task.getBook().sellLevel())
                .equals(s.enchantments().get(MenuSnapshot.enchantmentKey(task.getBook().id())))).toList();
        if (returned.isEmpty()) return Result.WAITING;
        var owned=task.bookList.stream().filter(b->b.level==task.getBook().sellLevel()).toList();
        if (returned.size()!=1 || owned.size()!=1) return Result.BLOCKED;
        var holding=owned.getFirst(); holding.location=0; holding.slot=returned.getFirst().containerSlot();
        task.setBookState(Task.BookState.SELL);
        return Result.RETURNED;
    }
}
