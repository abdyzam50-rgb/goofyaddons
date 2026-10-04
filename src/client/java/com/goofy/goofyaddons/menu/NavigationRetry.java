package com.goofy.goofyaddons.menu;

import java.util.List;
import java.util.Objects;

/** Acknowledges reversible navigation; never use for claims, cancellations or submissions. */
public final class NavigationRetry {
    public enum Result { READY, WAITING, RETRIED, EXHAUSTED }
    private MenuSnapshot source;
    private int slot,retries;
    private long sentAt,started,stableAt,blockedUntil;
    private List<SlotView> stable;
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
                || !Objects.equals(menu.slot(slot),source.slot(slot))){stable=null;return Result.WAITING;}
        if(!menu.slots().equals(stable)){stable=List.copyOf(menu.slots());stableAt=now;return Result.WAITING;}
        if(now-sentAt<3000 || now-stableAt<750 || now<blockedUntil || retries>=3)return Result.WAITING;
        actions.click(slot,false);retries++;sentAt=now;stable=null;return Result.RETRIED;
    }
}
