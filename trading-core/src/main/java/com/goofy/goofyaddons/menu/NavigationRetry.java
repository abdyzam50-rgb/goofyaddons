package com.goofy.goofyaddons.menu;

import java.util.Objects;

/** Acknowledges reversible navigation; never use for claims, cancellations or submissions. */
public final class NavigationRetry {
    public enum Result { READY, WAITING, RETRIED, EXHAUSTED }
    private MenuSnapshot source;
    private int slot,retries;
    private long sentAt,started,stableAt,blockedUntil;
    private SlotView stable;
    private boolean sameTarget(SlotView a,SlotView b) {
        if(a==null || b==null)return false;
        if(a.customId()==null || a.customId().isBlank())return a.equals(b);
        // Product prices update in place. Keep identity checks, while allowing
        // live lore changes for a reversible product-navigation click only.
        return !a.empty()&&!b.empty()&&!a.inPlayerInventory()&&!b.inPlayerInventory()
                &&a.index()==b.index()&&a.containerSlot()==b.containerSlot()
                &&Objects.equals(a.customId(),b.customId())&&Objects.equals(a.customName(),b.customName())
                &&Objects.equals(a.hoverName(),b.hoverName())&&Objects.equals(a.enchantments(),b.enchantments())
                &&a.count()==b.count()&&a.maxStackSize()==b.maxStackSize();
    }
    public boolean pending(){return source!=null;}
    public void reset(){source=null;stable=null;retries=0;blockedUntil=0;}
    public void sent(MenuSnapshot menu,int clicked,long now){
        if(pending())throw new IllegalStateException("Navigation already pending");
        source=menu;slot=clicked;started=sentAt=now;stable=null;retries=0;
    }
    public void slowdown(long now){blockedUntil=Math.max(blockedUntil,now+1500);stable=null;}
    public Result observe(MenuSnapshot menu,boolean signOpen,GameActions actions,long now){
        if(!pending())return Result.READY;
        if(signOpen || menu!=null && menu.title()!=null
                && (menu.containerId()!=source.containerId() || !Objects.equals(menu.title(),source.title()))){
            reset();return Result.READY;
        }
        if(now-started>=15_000)return Result.EXHAUSTED;
        if(menu==null || menu.title()==null || !menu.cursorEmpty() || !menu.loaded(slot)
                || !sameTarget(menu.slot(slot),source.slot(slot))){stable=null;return Result.WAITING;}
        if(!sameTarget(menu.slot(slot),stable)){stable=menu.slot(slot);stableAt=now;return Result.WAITING;}
        if(now-sentAt<3000 || now-stableAt<750 || now<blockedUntil || retries>=3)return Result.WAITING;
        actions.click(slot,false);retries++;sentAt=now;stable=null;return Result.RETRIED;
    }
}
