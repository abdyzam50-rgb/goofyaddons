package com.goofy.goofyaddons.menu;

import java.util.Objects;

/** Acknowledges reversible navigation; never use for claims, cancellations or submissions. */
public final class NavigationRetry {
    public enum Result { READY, WAITING, RETRIED, REOPEN, EXHAUSTED }
    private MenuSnapshot source;
    private int slot,retries,reopens;
    private String searchKey;
    private boolean searchClick;
    private long sentAt,started,stableAt,blockedUntil;
    private SlotView stable;
    private boolean sameTarget(SlotView a,SlotView b) {
        if(a==null || b==null)return false;
        if(!searchClick && (a.customId()==null || a.customId().isBlank())) {
            // These reversible controls update market lore while retaining their action.
            // Other anonymous buttons still require the complete original observation.
            String action=com.goofy.goofyaddons.utils.Chat.strip(a.hoverName());
            if(!"Create Buy Order".equals(action)&&!"Create Sell Offer".equals(action))return a.equals(b);
        }
        // Product prices update in place. Keep identity checks, while allowing
        // live lore changes for a reversible product-navigation click only.
        return !a.empty()&&!b.empty()&&!a.inPlayerInventory()&&!b.inPlayerInventory()
                &&a.index()==b.index()&&a.containerSlot()==b.containerSlot()
                &&Objects.equals(a.customId(),b.customId())&&Objects.equals(a.customName(),b.customName())
                &&Objects.equals(a.hoverName(),b.hoverName())&&Objects.equals(a.enchantments(),b.enchantments())
                &&a.count()==b.count()&&a.maxStackSize()==b.maxStackSize();
    }
    public boolean pending(){return source!=null;}
    public java.util.Map<String,Object> diagnosticState(MenuSnapshot current){
        var state=new java.util.LinkedHashMap<String,Object>();state.put("pending",pending());state.put("retries",retries);state.put("reopens",reopens);state.put("searchClick",searchClick);state.put("slot",slot);
        if(source!=null){state.put("sourceContainer",source.containerId());state.put("sourceTitle",source.title());state.put("sourceTarget",source.slot(slot));}
        if(current!=null){state.put("currentContainer",current.containerId());state.put("currentTitle",current.title());state.put("currentTarget",current.slot(slot));}
        return state;
    }
    public void reset(){source=null;stable=null;retries=0;blockedUntil=0;searchKey=null;reopens=0;searchClick=false;}
    private Result timeout(MenuSnapshot menu) {
        // Only exact product-result navigation can be restarted here. Never replay a purchase.
        if(searchClick && menu!=null && menu.cursorEmpty() && reopens<2) {
            reopens++;source=null;stable=null;return Result.REOPEN;
        }
        return Result.EXHAUSTED;
    }
    public void sent(MenuSnapshot menu,int clicked,long now){
        if(pending())throw new IllegalStateException("Navigation already pending");
        var target=menu.slot(clicked);
        boolean result=menu.containerEnd()==54 && menu.title()!=null
                && com.goofy.goofyaddons.utils.Chat.strip(menu.title()).startsWith("Bazaar")
                && target!=null && !target.empty() && !target.inPlayerInventory()
                && target.hoverName()!=null && !com.goofy.goofyaddons.utils.Chat.strip(target.hoverName()).isBlank()
                && (target.hasLoreLine("Click to view details!") || menu.loading(clicked));
        String key=result?menu.title()+":"+target.hoverName()+":"+target.customId()+":"+target.enchantments():null;
        if(!Objects.equals(key,searchKey)){reopens=0;searchKey=key;}
        searchClick=result;
        source=menu;slot=clicked;started=sentAt=now;stable=null;retries=0;
    }
    public void slowdown(long now){blockedUntil=Math.max(blockedUntil,now+1500);stable=null;}
    public Result observe(MenuSnapshot menu,boolean signOpen,GameActions actions,long now){
        if(!pending())return Result.READY;
        if(signOpen){reset();return Result.READY;}
        if(menu!=null && menu.title()!=null
                && (menu.containerId()!=source.containerId() || !Objects.equals(menu.title(),source.title()))){
            // Opening packets arrive before their contents. At short action delays,
            // acknowledging the header alone lets the next engine inspect an empty
            // or placeholder menu. Keep ownership until actual controls arrive.
            if(now-started>=15_000)return timeout(menu);
            boolean controlsLoaded=menu.slots().stream().anyMatch(s->
                    !s.empty() && !s.inPlayerInventory()
                    && s.hoverName()!=null && !com.goofy.goofyaddons.utils.Chat.strip(s.hoverName()).isBlank());
            if(!controlsLoaded)return Result.WAITING;
            reset();return Result.READY;
        }
        if(now-started>=15_000)return timeout(menu);
        if(menu==null || menu.title()==null || !menu.cursorEmpty() || !menu.loaded(slot)
                || !sameTarget(menu.slot(slot),source.slot(slot))){stable=null;return Result.WAITING;}
        if(!sameTarget(menu.slot(slot),stable)){stable=menu.slot(slot);stableAt=now;return Result.WAITING;}
        if(now-sentAt<3000 || now-stableAt<750 || now<blockedUntil || retries>=3)return Result.WAITING;
        actions.click(slot,false);retries++;sentAt=now;stable=null;return Result.RETRIED;
    }
}
