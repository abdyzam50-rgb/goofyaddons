package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.generalflipper.OrderLore;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;

/** Rebuild one supported cycle per saved route from complete live observations. No actions. */
public record BookRecoveryPlan(List<Recovered> routes, List<BookList> extras) {
    public record Recovered(Task task, int acquiredUnits, Double orderPrice, boolean selling) {}
    private record Order(boolean selling, int total, int filled, double price) {}

    public static BookRecoveryPlan build(List<BookJournal.Position> saved,
                                          Map<Integer,MenuSnapshot> pages, String username) {
        if(!pages.keySet().containsAll(Set.of(0,1,2,3)))throw new IllegalStateException("Recovery scan incomplete");
        if(Objects.equals(pages.get(1).title(),pages.get(2).title()))
            throw new IllegalStateException("Recovery storage commands point to the same page; configure two distinct pages.");
        var inventory=inventory(pages.get(0));
        for(int page=1;page<=3;page++)if(!inventory.equals(inventory(pages.get(page))))
            throw new IllegalStateException("Inventory changed during recovery; press J to scan again.");
        var routes=new ArrayList<Recovered>();var extras=new ArrayList<BookList>();
        for(var position:saved) {
            Book book=position.book();var holdings=new ArrayList<BookList>();
            for(int page=0;page<3;page++)for(var slot:pages.get(page).slots()) {
                if(slot.empty() || !slot.enchantedBook() || (page==0)!=slot.inPlayerInventory())continue;
                Integer level=slot.enchantments()==null?null:slot.enchantments().get(MenuSnapshot.enchantmentKey(book.id()));
                if(level==null || level<book.level() || level>book.sellLevel())continue;
                if(slot.count()!=1 || slot.enchantments().size()!=1 || page==0 && (slot.containerSlot()<0 || slot.containerSlot()>=36))
                    throw new IllegalStateException("Move the matching "+book.name()+" book into a normal inventory slot; stacked or mixed books cannot be resumed.");
                BookList entry=new BookList(book,level,page);entry.slot=page==0?slot.containerSlot():slot.index();holdings.add(entry);
            }
            // Ignore player inventory embedded in later menus; it was counted exactly once.
            var orders=new ArrayList<Order>();MenuSnapshot menu=pages.get(3);
            for(var slot:menu.slots()) {
                if(slot.empty() || slot.inPlayerInventory())continue;
                String name=Chat.strip(slot.hoverName());
                if(name.contains("Next Page") || name.contains("Previous Page"))throw new IllegalStateException("Paginated orders cannot be resumed automatically.");
                for(int level=book.level();level<=book.sellLevel();level++) {
                    boolean buy=name.equals("BUY "+book.getRomanLevel(level)),sell=name.equals("SELL "+book.getRomanLevel(level));
                    if(!buy && !sell)continue;
                    if(menu.title().contains("Co-op")) {
                        var creator=OrderLore.creator(slot.lore(),username);
                        if(creator==OrderLore.Creator.OTHER)continue;
                        if(creator!=OrderLore.Creator.OWN)throw new IllegalStateException("Matching order creator unreadable for "+book.name());
                    }
                    if(buy && level!=book.level() || sell && level!=book.sellLevel())
                        throw new IllegalStateException("Order level differs from the saved route for "+book.name());
                    Integer total=OrderLore.total(slot.lore());var fill=OrderLore.fill(slot.lore());Double price=TradeReceipts.unitPrice(slot.lore());
                    if(total==null || fill==null || price==null || !Double.isFinite(price) || price<=0
                            || total>(sell?1:book.getQtyAmount(book.level())))
                        throw new IllegalStateException("Order amount, progress or price unreadable/unsupported for "+book.name());
                    orders.add(new Order(sell,total,fill.filled(),price));
                }
            }
            if(orders.size()>1)throw new IllegalStateException("Multiple matching orders for "+book.name()+"; resolve duplicates before retrying.");
            if(holdings.isEmpty() && orders.isEmpty())continue;
            Task task=new Task(book,false,false,position.tradeId()==null?UUID.randomUUID().toString():position.tradeId());
            task.markRecovered();
            task.setReservedUnitCost(position.cost()/book.getQtyAmount(book.level()));
            Order order=orders.isEmpty()?null:orders.getFirst();int acquired=0;
            if(order!=null && order.selling()) {
                // The output is in the offer, not an inventory slot. Its cost may be unknown.
                acquired=book.getQtyAmount(book.level());extras.addAll(holdings);
                task.setBookState(Task.BookState.SELL_ORDER);
                task.setBookState(order.filled()>0?Task.BookState.REPLACE_SELL:Task.BookState.VERIFY_ORDER);
            } else {
                // Prefer the most advanced books; remaining physical copies become tracked extras.
                holdings.sort(Comparator.comparingInt((BookList b)->b.level).reversed());
                for(var entry:holdings) {
                    if(task.assignBook(book,entry.level,entry.location,1)==0) {
                        task.bookList.stream().filter(b->b.slot<0 && b.level==entry.level && b.location==entry.location).findFirst().orElseThrow().slot=entry.slot;
                        acquired+=book.baseUnits(entry.level);
                    } else extras.add(entry);
                }
                if(order!=null) {
                    // Claim/cancel through the existing verified flow before any replacement.
                    task.setBookState(Task.BookState.OUTBID);
                } else if(task.getAmountToOrder()==0)task.setBookState(Task.BookState.ANVIL);
                else {
                    task.setBookState(Task.BookState.SELECTED);
                    task.actionSchedule=task.isCombinable()?Task.ActionSchedule.SELECTED_COMBINE_STORE_BUYORDER:Task.ActionSchedule.SELECTED_STORE_BUYORDER;
                }
            }
            routes.add(new Recovered(task,acquired,order==null?null:order.price(),order!=null && order.selling()));
        }
        return new BookRecoveryPlan(List.copyOf(routes),List.copyOf(extras));
    }
    private static Map<Integer,InventoryMemory.Item> inventory(MenuSnapshot menu) {
        var result=new HashMap<Integer,InventoryMemory.Item>();
        for(var slot:menu.slots())if(slot.inPlayerInventory() && slot.containerSlot()>=0 && slot.containerSlot()<36)
            result.put(slot.containerSlot(),slot.empty()?new InventoryMemory.Item("","",Map.of(),0):
                    new InventoryMemory.Item(slot.customId(),slot.hoverName(),slot.enchantments(),slot.count()));
        if(result.size()!=36)throw new IllegalStateException("Incomplete inventory during recovery");
        return result;
    }

    /** Finish an additional fully held cycle without buying anything or requiring entry profit. */
    public static Task completedExtraCycle(Book book,List<BookList> extras,double unitCost) {
        Task task=new Task(book,false,false);task.markRecovered();task.setReservedUnitCost(unitCost);
        var matching=extras.stream().filter(b->b.book.equals(book))
                .sorted(Comparator.comparingInt((BookList b)->b.level).reversed()).toList();
        for(var entry:matching)if(task.assignBook(book,entry.level,entry.location,1)==0) {
            task.bookList.stream().filter(b->b.slot<0 && b.level==entry.level && b.location==entry.location)
                    .findFirst().orElseThrow().slot=entry.slot;
        }
        if(task.getAmountToOrder()!=0)return null;
        task.setBookState(Task.BookState.ANVIL);return task;
    }
}
