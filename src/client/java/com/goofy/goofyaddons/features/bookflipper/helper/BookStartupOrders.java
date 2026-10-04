package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.menu.*;
import java.util.List;

/** One stable order-list observation accounts for every startup route's missing BUY. */
public final class BookStartupOrders {
    private int container=-1;
    private long since;
    private List<SlotView> contents;
    public void reset() { container=-1;contents=null; }

    /** Null means not yet a complete, stable and unambiguous observation. Never clicks. */
    public List<Task> missingBuys(List<Task> tasks,MenuSnapshot menu,long now) {
        if (menu==null || !TradingSafety.ordersTitle(menu.title()) || !menu.loaded(35) || !menu.cursorEmpty()) {
            reset();return null;
        }
        if (container!=menu.containerId() || !menu.slots().equals(contents)) {
            container=menu.containerId();contents=menu.slots();since=now;return null;
        }
        if (now-since<750) return null;
        var names=menu.slots().stream().filter(slot->!slot.inPlayerInventory() && !slot.empty())
                .map(slot->com.goofy.goofyaddons.utils.Chat.strip(slot.hoverName())).toList();
        for (var task:tasks) {
            if (TradingSafety.ambiguousOrders(names,task.getBook().getRomanLevel(task.getBook().level()))
                    || TradingSafety.ambiguousOrders(names,task.getBook().getRomanLevel(task.getBook().sellLevel()))) return null;
        }
        return tasks.stream().filter(task->task.getBookState()==Task.BookState.BAZAAR_ORDER_CHECK)
                .filter(task->menu.namedInContainer("BUY "+task.getBook().getRomanLevel(task.getBook().level())).isEmpty()).toList();
    }

    public static void scheduleMissingBuy(Task task) {
        if (task.getBookState()!=Task.BookState.BAZAAR_ORDER_CHECK)
            throw new IllegalStateException("Only startup/reconciliation tasks may adopt missing buy orders");
        task.actionSchedule=Task.ActionSchedule.NONE;
        if (task.getAmountToOrder()==0) task.setBookState(Task.BookState.ANVIL);
        else {
            if (task.isCombinable()) task.actionSchedule=Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER;
            else if (task.bookList.stream().anyMatch(book->book.location==0))
                task.actionSchedule=Task.ActionSchedule.SELECTED_STORE_BUYORDER;
            task.setBookState(Task.BookState.SELECTED);
        }
    }
}
