package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;
import java.util.function.Predicate;

/** Reversible /ah navigation. Only observed BIN listings select an item or price. */
public final class AuctionBrowserNavigation {
    public enum Result {WAITING,READY,BLOCKED}
    private final String query;
    private final Predicate<SlotView> matches;
    private final NavigationRetry retry=new NavigationRetry();
    private MenuSnapshot sent;
    private int clicked;
    private long started=-1,nextCommand,signAt;
    private boolean searchOpened,searchWritten,searchReturned;
    private SlotView selected;
    private double price;
    private String failure;
    public AuctionBrowserNavigation(String query,Predicate<SlotView> matches) {
        if(query==null || query.isBlank() || query.length()>80)throw new IllegalArgumentException("Invalid auction search");
        this.query=query;this.matches=matches;
    }
    public SlotView selected(){return selected;}
    public double price(){return price;}
    public String failure(){return failure;}
    private Result block(String text){failure=text;return Result.BLOCKED;}
    public Result tick(MenuSnapshot menu,boolean signOpen,GameActions actions,long now) {
        if(started<0)started=now;
        if(now-started>90000)return block("Auction browsing timed out; no purchase or listing submitted");
        if(menu==null || !menu.cursorEmpty())return block("Auction browsing needs a readable menu and empty cursor");
        if(searchOpened && signOpen) {
            retry.reset();
            if(!searchWritten){if(!actions.writeSign(query))return block("Auction search sign could not be written");searchWritten=true;signAt=now;}
            return Result.WAITING;
        }
        if(signOpen)return block("Unexpected sign during auction browsing");
        String title=Chat.strip(menu.title());
        if(retry.pending()) {
            // Filter/sort controls update their lore in the same container.
            if(sent!=null && !Objects.equals(sent.slot(clicked),menu.slot(clicked)))retry.reset();
            var state=retry.observe(menu,false,actions,now);
            if(state==NavigationRetry.Result.EXHAUSTED)return block("Auction navigation was not acknowledged");
            if(state!=NavigationRetry.Result.READY)return Result.WAITING;
        }
        if(menu.title()==null){if(now>=nextCommand){actions.command("ah");nextCommand=now+8000;}return Result.WAITING;}
        if(ProductionMenus.auctionHouse(title))return click(menu,actions,now,control(menu,"Auctions Browser","Auction Browser","Browse Auctions"));
        if(!Set.of("Auctions Browser","Auction Browser").contains(title))return block("Unexpected auction browser screen: "+title);
        var type=control(menu,"BIN Filter","Auction Type","Auction Type Filter");
        if(type==null)return block("Auction BIN filter control is unverified; capture the menu");
        if(!selected(type,"BIN Only","BIN only","BIN"))return click(menu,actions,now,type);
        var sort=control(menu,"Sort","Sort By","Sort Order");
        if(sort==null)return block("Auction sort control is unverified; capture the menu");
        if(!selected(sort,"Lowest Price"))return click(menu,actions,now,sort);
        if(!searchOpened) {
            var search=control(menu,"Search");if(search==null)return block("Auction search control is unverified");
            searchOpened=true;signAt=now;return click(menu,actions,now,search);
        }
        if(!searchWritten){if(now-signAt>10000)return block("Auction search sign did not open");return Result.WAITING;}
        if(!searchReturned){searchReturned=true;signAt=now;return Result.WAITING;}
        if(now-signAt<500)return Result.WAITING;
        var candidates=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty()
                && s.index()>=10 && s.index()<=43 && s.index()%9>=1 && s.index()%9<=7 && matches.test(s))
            .filter(s->binPrice(s)!=null).sorted(Comparator.comparingDouble(s->binPrice(s))).toList();
        if(candidates.isEmpty())return block("No readable matching BIN results; no item selected");
        selected=candidates.getFirst();price=binPrice(selected);return Result.READY;
    }
    private Result click(MenuSnapshot menu,GameActions actions,long now,SlotView slot) {
        if(slot==null)return block("Auction navigation control is missing or ambiguous");
        actions.click(slot.index(),false);retry.sent(menu,slot.index(),now);sent=menu;clicked=slot.index();return Result.WAITING;
    }
    private static SlotView control(MenuSnapshot menu,String... names) {
        var found=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty())
            .filter(s->{String n=Chat.strip(s.hoverName()).trim();return Arrays.stream(names).anyMatch(w->n.equals(w)||n.startsWith(w+":"));}).toList();
        return found.size()==1?found.getFirst():null;
    }
    static boolean selected(SlotView control,String... options) {
        for(String raw:(control.hoverName()+"\n"+control.lore()).split("\\R")) {
            String line=Chat.strip(raw).trim();
            for(String option:options)if(line.equals("▶ "+option)||line.equals("➜ "+option)||line.equals("Selected: "+option)
                ||line.equals("Current: "+option)||line.equals("Sort: "+option)||line.equals("BIN Filter: "+option))return true;
        }
        return false;
    }
    static Double binPrice(SlotView slot) {
        // A bid amount or ordinary auction starting price cannot authorize BIN selection.
        for(String raw:slot.lore().split("\\R")) {
            String line=Chat.strip(raw).trim();
            if(line.startsWith("Buy it now: ") || line.startsWith("BIN Price: "))return ProductionMenus.exactCoins(line);
        }
        return null;
    }
}
