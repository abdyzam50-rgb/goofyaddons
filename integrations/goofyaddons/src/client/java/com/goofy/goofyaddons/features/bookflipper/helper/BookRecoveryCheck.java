package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.menu.*;
import java.util.*;

/** Read-only evidence before clearing coarse legacy ownership records. Never claims or moves items. */
public final class BookRecoveryCheck {
    public enum Result { WAITING, VERIFIED, BLOCKED }
    private final List<BookPosition> positions;
    private final Set<String> present=new HashSet<>();
    private final Map<Integer,MenuSnapshot> snapshots=new HashMap<>();
    private int page=0,container=-1;
    private long requestedAt=-1,stableSince,startedAt=-1;
    private List<SlotView> contents;
    private String reason="";
    private boolean finished;
    public BookRecoveryCheck(List<BookPosition> positions) {this.positions=List.copyOf(positions);}
    public String reason(){return reason;}
    public String progress(){return page==0?"Checking inventory":page<3?"Checking storage page "+page:"Checking Bazaar orders";}
    public Set<String> present(){return Set.copyOf(present);}
    public Map<Integer,MenuSnapshot> snapshots(){if(!finished)throw new IllegalStateException("Recovery scan incomplete");return Map.copyOf(snapshots);}
    public Result tick(MenuSnapshot menu,GameActions actions,String firstPage,String secondPage,String username,long now) {
        if(finished)return Result.VERIFIED;
        if(!reason.isBlank())return Result.BLOCKED;
        if(startedAt<0)startedAt=now;
        if(now-startedAt>45000)return block("Saved-book check timed out; ownership file kept. Use the trading toggle to retry.");
        if(menu==null || !menu.cursorEmpty())return Result.WAITING;
        String command=page==1?firstPage:page==2?secondPage:"managebazaarorders";
        boolean expected=page==0?menu.title()==null:page<3?BookTransfer.pageMatches(menu.title(),command):TradingSafety.ordersTitle(menu.title());
        if(!expected) {
            container=-1;contents=null;
            if(menu.title()!=null) {actions.closeMenu();return Result.WAITING;}
            if(requestedAt<0 || now-requestedAt>10000) {actions.command(command);requestedAt=now;}
            return Result.WAITING;
        }
        if(page>0 && !menu.loaded(page<3?8:35))return Result.WAITING;
        if(container!=menu.containerId() || !menu.slots().equals(contents)) {
            container=menu.containerId();contents=menu.slots();stableSince=now;return Result.WAITING;
        }
        // Lagging placeholder menus must not count as an absence observation.
        if(now-stableSince<1500)return Result.WAITING;
        if(menu.slots().stream().filter(SlotView::inPlayerInventory).filter(s->s.containerSlot()>=0 && s.containerSlot()<36).count()!=36)
            return Result.WAITING;
        for(var slot:menu.slots()) {
            if(slot.empty() || page==0 && !slot.inPlayerInventory())continue;
            if(slot.enchantedBook() && (slot.inPlayerInventory() || page<3)) {
                if(slot.enchantments()==null || slot.enchantments().isEmpty() || slot.enchantments().values().stream().anyMatch(level->level==null || level<1))
                    return block("Unreadable book identity during recovery check; ownership file kept.");
                for(var position:positions) {
                    var book=position.book();Integer level=slot.enchantments().get(MenuSnapshot.enchantmentKey(book.id()));
                    if(level!=null && level>=book.level() && level<=book.sellLevel())present.add(book.id());
                }
            }
            if(page==3 && !slot.inPlayerInventory()) {
                String name=com.goofy.goofyaddons.utils.Chat.strip(slot.hoverName());
                if(name.contains("Next Page") || name.contains("Previous Page"))return block("Bazaar orders are paginated; absence cannot be verified. Ownership file kept.");
                for(var position:positions)for(int level=position.book().level();level<=position.book().sellLevel();level++) {
                    String item=position.book().getRomanLevel(level);
                    if(name.equals("BUY "+item) || name.equals("SELL "+item)) {
                        if(menu.title().contains("Co-op")) {
                            var creator=com.goofy.goofyaddons.features.generalflipper.OrderLore.creator(slot.lore(),username);
                            if(creator==com.goofy.goofyaddons.features.generalflipper.OrderLore.Creator.UNREADABLE)
                                return block("Matching co-op order creator is unreadable; ownership file kept.");
                            if(creator!=com.goofy.goofyaddons.features.generalflipper.OrderLore.Creator.OWN)continue;
                        }
                        present.add(position.book().id());
                    }
                }
            }
        }
        snapshots.put(page,menu);
        actions.closeMenu();requestedAt=-1;container=-1;contents=null;
        if(page++==3){finished=true;return Result.VERIFIED;}
        return Result.WAITING;
    }
    private Result block(String message){reason=message;return Result.BLOCKED;}
}
