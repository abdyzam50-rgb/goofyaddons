package com.goofy.goofyaddons.features.access;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.TradingSafety;
import java.util.Locale;

/** Physical-NPC route owns the GUI until the requested search/orders page is acknowledged. */
public final class BazaarNavigation {
    public enum State {IDLE,OPEN,SEARCH,SIGN,RESULT,ORDERS,DONE,FAILED}
    public interface Port {
        MenuSnapshot menu();boolean signOpen();boolean nearNpc();boolean walking();
        boolean walk();void stopWalking();void interact();void click(int slot);boolean writeSearch(String text);
    }
    private State state=State.IDLE;private String query="",failure;private boolean orders,walkRequested;
    private long began,next,clickedAt;private int attempts,searchContainer,clickedSlot,clickAttempts;
    private String clickedName;
    public State state(){return state;}public String failure(){return failure;}
    public boolean busy(){return state!=State.IDLE && state!=State.DONE && state!=State.FAILED;}
    public boolean request(String command,long now) {
        String clean=command.trim();String lower=clean.toLowerCase(Locale.ROOT);String wanted;
        boolean manage=lower.equals("managebazaarorders");
        if(manage)wanted="";
        else if(lower.equals("bz") || lower.equals("bazaar"))wanted="";
        else if(lower.startsWith("bz "))wanted=clean.substring(3).trim();
        else if(lower.startsWith("bazaar "))wanted=clean.substring(7).trim();
        else return false;
        if(wanted.length()>100 || wanted.contains("\n") || wanted.contains("\r"))throw new IllegalArgumentException("Invalid Bazaar search");
        if(busy()) {
            if(orders!=manage || !query.equals(wanted))throw new IllegalStateException("Another Bazaar navigation already owns the menu");
            return true;
        }
        query=wanted;orders=manage;state=State.OPEN;began=now;next=now;attempts=0;walkRequested=false;failure=null;return true;
    }
    public void cancel(Port port){port.stopWalking();state=State.IDLE;failure=null;}
    private void fail(String message,Port port){failure=message;state=State.FAILED;port.stopWalking();}
    public long elapsed(long now){return Math.max(0,now-began);}
    public void tick(Port port,long now,int timeoutSeconds) {
        if(!busy())return;
        MenuSnapshot menu=port.menu();
        if(menu!=null && !menu.cursorEmpty()){fail("Bazaar navigation stopped: cursor is occupied",port);return;}
        if(now-began>timeoutSeconds*1000L){fail("NPC Bazaar navigation timed out; position retained",port);return;}
        String title=menu==null?null:Chat.strip(menu.title());
        if(state==State.OPEN) {
            if(orders && title!=null && TradingSafety.ordersTitle(title)){port.stopWalking();state=State.DONE;return;}
            if(title!=null && title.contains("Bazaar")) {port.stopWalking();state=orders?State.ORDERS:query.isBlank()?State.DONE:State.SEARCH;return;}
            if(title!=null){fail("Close the unrelated menu before NPC Bazaar navigation",port);return;}
            if(!port.nearNpc()) {
                if(!walkRequested){walkRequested=true;if(!port.walk()){fail("Stand next to the Bazaar NPC; automatic walking needs the custom pathfinder",port);}}
                else if(!port.walking())fail("Pathfinder stopped before reaching the Bazaar NPC",port);
                return;
            }
            port.stopWalking();
            if(now>=next){if(attempts>=3){fail("Bazaar NPC interaction was not acknowledged",port);return;}attempts++;next=now+2000;port.interact();}
            return;
        }
        if(state==State.SEARCH || state==State.ORDERS) {
            if(menu==null || title==null || !title.contains("Bazaar"))return;
            var slots=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory()
                    && Chat.strip(s.hoverName()).matches(state==State.SEARCH?"Search(?:\\.\\.\\.)?":"Manage (?:Bazaar )?Orders")).toList();
            if(slots.size()!=1)return;
            searchContainer=menu.containerId();clickedSlot=slots.getFirst().index();clickedName=Chat.strip(slots.getFirst().hoverName());
            port.click(clickedSlot);clickedAt=now;clickAttempts=1;state=state==State.SEARCH?State.SIGN:State.RESULT;next=now+8000;return;
        }
        if(state==State.SIGN) {
            if(port.signOpen()){if(!port.writeSearch(query)){fail("Bazaar search text could not be entered",port);return;}state=State.RESULT;return;}
            if(now>next)fail("Bazaar search sign was not acknowledged",port);
            else retryControl(port,menu,title,now);return;
        }
        if(state==State.RESULT && menu!=null && title!=null && menu.containerId()!=searchContainer
                && (orders?TradingSafety.ordersTitle(title):title.contains("Bazaar"))) {state=State.DONE;return;}
        if(state==State.RESULT && orders) {
            if(now>next)fail("Bazaar orders control was not acknowledged",port);
            else retryControl(port,menu,title,now);
        }
    }
    private void retryControl(Port port,MenuSnapshot menu,String title,long now) {
        if(clickAttempts>=3 || now-clickedAt<2000 || menu==null || title==null || !title.contains("Bazaar")
                || menu.containerId()!=searchContainer)return;
        var control=menu.slot(clickedSlot);
        if(control==null || control.empty() || control.inPlayerInventory() || !clickedName.equals(Chat.strip(control.hoverName())))return;
        port.click(clickedSlot);clickAttempts++;clickedAt=now;
    }
}
