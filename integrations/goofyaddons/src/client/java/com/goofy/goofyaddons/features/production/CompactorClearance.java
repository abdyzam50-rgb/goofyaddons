package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;
import java.util.regex.Pattern;

/** Clears observed compactor filters before production. Never retries an unacknowledged removal. */
final class CompactorClearance {
    interface Ports {
        boolean open(SlotView device);
        void intent(String reason)throws Exception;
    }
    record Controls(int tier,boolean active,Map<Integer,Integer> slots,Map<Integer,String> recipes){}
    private static final Pattern TITLE=Pattern.compile("Personal Compactor (4000|5000|6000|7000)");
    private static final Pattern LABEL=Pattern.compile("Auto-Craft Slot #(\\d+)");
    private SlotView target;
    private Map<Integer,String> expected;
    private Map<String,Integer> inventory;
    private int container=-1;
    private long deadline,settledAt;
    private boolean opening,closing;
    private String failure;

    /** Every numbered control and its action lore must be present, including empty filters. */
    static Controls controls(MenuSnapshot menu) {
        if(menu==null||!menu.cursorEmpty()||menu.title()==null)return null;
        var title=TITLE.matcher(Chat.strip(menu.title()));if(!title.matches())return null;
        int tier=Integer.parseInt(title.group(1)),capacity=CompactorData.capacity(tier);
        var slots=new TreeMap<Integer,Integer>();var recipes=new TreeMap<Integer,String>();Boolean active=null;
        for(var s:menu.slots())if(!s.inPlayerInventory()&&!s.empty()) {
            String name=Chat.strip(s.hoverName());var label=LABEL.matcher(name==null?"":name);
            if(label.matches()) {
                int number;try{number=Integer.parseInt(label.group(1))-1;}catch(NumberFormatException bad){return null;}
                if(number<0||number>=capacity||slots.putIfAbsent(number,s.index())!=null)return null;
                String id=s.customId();
                if(s.hasLoreLine("Click to remove item!")) {
                    if(id==null||!ProductionRecipe.validId(id))return null;recipes.put(number,id);
                } else if(id!=null&&!id.isBlank()||!s.hasLoreLine("Click on an item in your inventory to")
                        ||!s.hasLoreLine("add it to this Personal Compactor")||!s.hasLoreLine("filter."))return null;
            } else if("Compactor Currently ON!".equals(name)||"Compactor Currently OFF!".equals(name)) {
                if(active!=null||!s.hasLoreLine("Click to toggle!"))return null;
                active="Compactor Currently ON!".equals(name);
            }
        }
        return slots.size()==capacity&&active!=null?new Controls(tier,active,Map.copyOf(slots),Map.copyOf(recipes)):null;
    }
    static String unreadable(MenuSnapshot menu) {
        for(var device:PersonalCompactors.detect(menu))if(device.metadata().compactor()==null||device.metadata().compactor().active()==null)
            return "Personal Compactor configuration unreadable; open its menu before production";
        return null;
    }
    boolean needsMenu(MenuSnapshot menu) {
        return target!=null||controls(menu)!=null||unreadable(menu)!=null||PersonalCompactors.detect(menu).stream()
            .anyMatch(s->!s.metadata().compactor().recipes().isEmpty());
    }
    ProductionLoop.Outcome tick(MenuSnapshot menu,GameActions actions,Ports ports,boolean ownsMenu,long now) {
        if(failure!=null)return ProductionLoop.Outcome.uncertain(failure);
        if(menu==null||!menu.cursorEmpty())return ProductionLoop.Outcome.blocked("Clear the cursor before removing compactor filters");
        if(target==null) {
            String unknown=unreadable(menu);if(unknown!=null)return ProductionLoop.Outcome.blocked(unknown);
            var devices=PersonalCompactors.detect(menu);
            target=devices.stream().filter(s->!s.metadata().compactor().recipes().isEmpty()).findFirst().orElse(null);
            if(target==null) {
                var open=controls(menu);
                if(open!=null&&open.recipes().isEmpty()) {
                    if(ownsMenu)actions.closeMenu();
                    return ProductionLoop.Outcome.pending("Leaving the cleared Personal Compactor menu");
                }
                return ProductionLoop.Outcome.DONE;
            }
            expected=target.metadata().compactor().recipes();inventory=counts(menu);container=-1;
        }
        if(!ownsMenu)return ProductionLoop.Outcome.pending("Waiting to clear Personal Compactor recipes");
        if(!inventory.equals(counts(menu)))return uncertain("Inventory changed while clearing compactor recipes; inspect it before continuing");
        if(closing) {
            if(menu.title()!=null)return now>deadline?uncertain("Compactor menu did not close; production stopped"):waiting();
            var current=device(menu);
            if(current==null)return uncertain("Compactor disappeared before its empty configuration could be verified");
            var data=current.metadata().compactor();
            if(data!=null&&data.recipes().isEmpty()&&Objects.equals(data.active(),target.metadata().compactor().active())) {
                if(now<settledAt)return waiting();
                target=null;closing=false;expected=null;return tick(menu,actions,ports,true,now);
            }
            return now>deadline?uncertain("Saved compactor filters are not empty; no production inputs were bought"):waiting();
        }
        var controls=controls(menu);
        if(controls==null) {
            if(opening)return now>deadline?uncertain("Compactor menu did not load; production stopped"):waiting();
            if(!ports.open(target)){target=null;return ProductionLoop.Outcome.blocked("Put the Personal Compactor in your hotbar or open its configuration menu before production");}
            opening=true;deadline=now+8000;return waiting();
        }
        if(controls.tier()!=target.metadata().compactor().tier()||controls.active()!=target.metadata().compactor().active())
            return uncertain("The open compactor does not match the device selected for clearing");
        if(container>=0&&container!=menu.containerId())return uncertain("Compactor container changed while clearing recipes");
        container=menu.containerId();opening=false;
        if(!controls.recipes().equals(expected))return now>deadline?uncertain("Compactor recipe removal was not verified; no click retried"):waiting();
        if(now<settledAt)return waiting();
        if(expected.isEmpty()) {
            actions.closeMenu();closing=true;deadline=now+8000;settledAt=now+500;return waiting();
        }
        int recipe=Collections.min(expected.keySet()),slot=controls.slots().get(recipe);
        try{ports.intent("Clearing Personal Compactor recipe #"+(recipe+1)+": "+expected.get(recipe));}
        catch(Exception failed){return ProductionLoop.Outcome.blocked("Compactor removal intent could not be saved; no click sent");}
        actions.click(slot,false);
        var next=new TreeMap<>(expected);next.remove(recipe);expected=Map.copyOf(next);deadline=now+8000;settledAt=now+500;
        return waiting();
    }
    private SlotView device(MenuSnapshot menu) {
        return PersonalCompactors.detect(menu).stream().filter(s->s.inPlayerInventory()==target.inPlayerInventory()
            &&s.containerSlot()==target.containerSlot()&&Objects.equals(s.customId(),target.customId())
            &&Objects.equals(s.metadata().uuid(),target.metadata().uuid())).findFirst().orElse(null);
    }
    private static Map<String,Integer> counts(MenuSnapshot menu) {
        var result=new TreeMap<String,Integer>();
        for(var s:menu.slots())if(s.inPlayerInventory()&&s.containerSlot()<36&&!s.empty()) {
            String id=ProductionMenus.productId(s);if(id==null)id="name:"+Chat.strip(s.hoverName());
            result.merge(id,s.count(),Integer::sum);
        }
        return result;
    }
    private ProductionLoop.Outcome waiting(){return ProductionLoop.Outcome.pending("Clearing and verifying Personal Compactor recipes before production");}
    private ProductionLoop.Outcome uncertain(String reason){failure=reason;return ProductionLoop.Outcome.uncertain(reason);}
}
