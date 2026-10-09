package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.access.ActionRequirements;
import java.util.*;

/** One exact recipe batch. Every cursor/grid mutation waits for a server observation. */
public final class CraftingExecutor {
    public enum Result {WAITING,CRAFTED,BLOCKED}
    private static final int[] GRID={10,11,12,19,20,21,28,29,30};
    private static final int RESULT=23;
    private ProductionRecipe recipe;
    private int container,source=-1;
    private Map<String,Integer> before;
    private MenuSnapshot sent;
    private long sentAt,started,next,inconsistentSince;
    private int retries;
    private boolean submitted;
    private String failure;
    public String failure(){return failure;}
    public boolean busy(){return recipe!=null;}
    public void reset(){recipe=null;sent=null;before=null;submitted=false;source=-1;failure=null;inconsistentSince=0;}
    private Result block(String reason){failure=reason;return Result.BLOCKED;}
    public Result tick(ProductionRecipe wanted,MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,long now) {
        return tick(wanted,menu,actions,skills,Map.of(),now);
    }
    public Result tick(ProductionRecipe wanted,MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,Map<String,Integer> unlocks,long now) {
        if(recipe==null){String reason=com.goofy.goofyaddons.features.access.RouteRequirements.craft(wanted.requirement(),skills,unlocks);if(reason!=null)return block(reason);}
        if(menu==null || !"Craft Item".equals(Chat.strip(menu.title())))return block("Open the SkyBlock Craft Item menu before crafting");
        if(wanted.kind()!=ProductionRecipe.Kind.CRAFT)return block("Not a crafting recipe");
        if(recipe==null) {
            if(!menu.cursorEmpty())return block("Crafting cannot adopt an occupied cursor");
            for(int slot:GRID)if(!empty(menu.slot(slot)))return block("Crafting grid already contains items; ownership must be reconciled");
            before=inventory(menu);
            for(var need:wanted.ingredients().entrySet())if(before.getOrDefault(need.getKey(),0)<need.getValue())return block("Missing ingredient: "+need.getKey());
            if(menu.slots().stream().noneMatch(s->s.inPlayerInventory() && s.containerSlot()<36 && s.empty()))return block("Keep one free inventory slot for crafting output");
            recipe=wanted;container=menu.containerId();started=now;next=now;submitted=false;
        }
        if(!wanted.equals(recipe) || menu.containerId()!=container)return block("Crafting owner/container changed; items retained");
        if(now-started>180000)return block("Crafting timed out; reconcile grid and cursor before retrying");
        if(sent!=null && sent.serverObservation()>=0 && menu.serverObservation()==sent.serverObservation()) {
            if(now-sentAt>8000)return block("No server inventory acknowledgement; crafting cursor/grid retained");
            return Result.WAITING;
        }
        if(!conserved(menu,submitted)) {
            if(inconsistentSince==0)inconsistentSince=now;
            if(now-inconsistentSince>=1500)return block("Crafting inventory/grid changed unexpectedly; items retained");
            return Result.WAITING;
        }
        inconsistentSince=0;
        if(submitted && completed(menu)){reset();return Result.CRAFTED;}
        if(sent!=null) {
            if(!sameContents(sent,menu)) {sent=null;retries=0;next=now+100;}
            else if(now-sentAt>=2000) {
                if(retries>=2)return block("Crafting input was not acknowledged; grid and cursor retained");
                // A duplicate is permitted only against the exact same cursor/grid/inventory.
                retries++;sentAt=now;sent=menu;replay(actions);return Result.WAITING;
            } else return Result.WAITING;
        }
        if(now<next)return Result.WAITING;
        if(submitted)return Result.WAITING;
        int target=-1;
        for(int i=0;i<9;i++) {
            var expected=recipe.grid().get(i);var actual=menu.slot(GRID[i]);
            if(expected==null){if(!empty(actual))return block("Unexpected item in recipe grid");continue;}
            if(!empty(actual) && (!expected.id().equals(actual.customId()) || actual.count()>expected.count()))return block("Recipe grid identity/count mismatch");
            if(empty(actual) || actual.count()<expected.count()){target=i;break;}
        }
        if(!menu.cursorEmpty()) {
            var cursor=menu.carried();
            if(cursor==null || cursor.empty() || cursor.customId()==null)return block("Unreadable crafting cursor");
            if(target>=0 && recipe.grid().get(target).id().equals(cursor.customId())) {
                int remaining=recipe.grid().get(target).count()-count(menu.slot(GRID[target]));
                send(menu,actions,GRID[target],false,cursor.count()>remaining,now);return Result.WAITING;
            }
            var origin=menu.slot(source);
            if(origin==null || !origin.inPlayerInventory() || (!origin.empty() && !cursor.customId().equals(origin.customId()))
                    || count(origin)+cursor.count()>origin.maxStackSize())return block("Cursor return slot no longer matches owned ingredients");
            send(menu,actions,source,false,false,now);return Result.WAITING;
        }
        if(target>=0) {
            var need=recipe.grid().get(target);int remaining=need.count()-count(menu.slot(GRID[target]));
            var available=menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && !s.empty() && need.id().equals(s.customId())).toList();
            if(available.isEmpty())return block("Ingredient moved out of accessible inventory");
            var stack=available.stream().filter(s->s.count()==remaining || (s.count()+1)/2==remaining).findFirst().orElse(available.getFirst());
            source=stack.index();send(menu,actions,source,false,stack.count()!=remaining && (stack.count()+1)/2==remaining,now);return Result.WAITING;
        }
        // Hypixel's Quick Crafting column (16, 25, 34) can show the same item before the grid is
        // full; only the result slot holds the grid's output.
        var outputs=menu.slots().stream().filter(s->s.index()==RESULT && !s.empty() && recipe.outputId().equals(s.customId())).toList();
        if(outputs.size()!=1 || outputs.getFirst().count()!=recipe.outputCount())return Result.WAITING;
        var output=outputs.getFirst();String reason=ActionRequirements.blocked(output.lore(),skills,ActionRequirements.Action.CRAFT);
        if(reason!=null)return block("Crafting requirement: "+reason);
        submitted=true;send(menu,actions,output.index(),true,false,now);return Result.WAITING;
    }
    private int actionSlot;private boolean actionShift,actionRight;
    private void send(MenuSnapshot menu,GameActions actions,int slot,boolean shift,boolean right,long now){
        sent=menu;sentAt=now;retries=0;actionSlot=slot;actionShift=shift;actionRight=right;replay(actions);
    }
    private void replay(GameActions actions){if(actionRight)actions.rightClick(actionSlot);else actions.click(actionSlot,actionShift);}
    private boolean completed(MenuSnapshot menu){
        if(!menu.cursorEmpty())return false;
        for(int slot:GRID)if(!empty(menu.slot(slot)))return false;
        var counts=inventory(menu);
        if(counts.getOrDefault(recipe.outputId(),0)!=before.getOrDefault(recipe.outputId(),0)+recipe.outputCount())return false;
        for(var e:recipe.ingredients().entrySet())if(counts.getOrDefault(e.getKey(),0)!=before.getOrDefault(e.getKey(),0)-e.getValue())return false;
        return true;
    }
    private boolean conserved(MenuSnapshot menu,boolean allowOutput){
        var counts=inventory(menu);
        for(int slot:GRID)add(counts,menu.slot(slot));
        if(!menu.cursorEmpty())add(counts,menu.carried());
        boolean original=true,converted=allowOutput;
        var ids=new HashSet<>(before.keySet());ids.addAll(counts.keySet());ids.addAll(recipe.ingredients().keySet());ids.add(recipe.outputId());
        for(String id:ids){int old=before.getOrDefault(id,0),current=counts.getOrDefault(id,0);
            original &= old==current;
            converted &= current==old-recipe.ingredients().getOrDefault(id,0)+(id.equals(recipe.outputId())?recipe.outputCount():0);
        }
        return original || converted;
    }
    private static Map<String,Integer> inventory(MenuSnapshot menu){
        var result=new HashMap<String,Integer>();for(var slot:menu.slots())if(slot.inPlayerInventory() && slot.containerSlot()<36)add(result,slot);return result;
    }
    private static void add(Map<String,Integer> counts,SlotView slot){if(!empty(slot) && slot.customId()!=null)counts.merge(slot.customId(),slot.count(),Integer::sum);}
    private static int count(SlotView slot){return empty(slot)?0:slot.count();}
    private static boolean empty(SlotView slot){return slot==null || slot.empty();}
    public static boolean sameOwnedState(MenuSnapshot a,MenuSnapshot b){return a!=null && b!=null && sameContents(a,b);}
    /** The first owned slot or cursor where the client and server copies differ, for the player to read. */
    public static String difference(MenuSnapshot local,MenuSnapshot server){
        if(local==null || server==null)return "a copy is missing";
        if(local.containerId()!=server.containerId())return "menu id "+local.containerId()+" vs "+server.containerId();
        if(local.cursorEmpty()!=server.cursorEmpty() || !sameItem(local.carried(),server.carried()))return "cursor "+describe(local.carried())+" vs "+describe(server.carried());
        if(local.slots().size()!=server.slots().size())return "slot count "+local.slots().size()+" vs "+server.slots().size();
        for(int i=0;i<local.slots().size();i++){var slot=local.slots().get(i);
            if((slot.inPlayerInventory() || Arrays.stream(GRID).anyMatch(n->n==slot.index())) && !sameItem(slot,server.slots().get(i)))
                return "slot "+i+" "+describe(slot)+" vs "+describe(server.slots().get(i));
        }
        return null;
    }
    private static String describe(SlotView slot){
        if(empty(slot))return "empty";
        return slot.count()+"x "+(slot.customId()==null?Chat.strip(slot.hoverName()):slot.customId())+(slot.metadata()==null?"":" "+slot.metadata().vanillaId());
    }
    private static boolean sameContents(MenuSnapshot a,MenuSnapshot b){
        if(a.containerId()!=b.containerId() || a.cursorEmpty()!=b.cursorEmpty() || !sameItem(a.carried(),b.carried()) || a.slots().size()!=b.slots().size())return false;
        for(int i=0;i<a.slots().size();i++){var slot=a.slots().get(i);
            if((slot.inPlayerInventory() || Arrays.stream(GRID).anyMatch(n->n==slot.index())) && !sameItem(slot,b.slots().get(i)))return false;
        }
        return true;
    }
    private static boolean sameItem(SlotView a,SlotView b){
        if(empty(a) || empty(b))return empty(a)==empty(b);
        return a.index()==b.index() && a.count()==b.count() && Objects.equals(a.customId(),b.customId())
                && Objects.equals(a.metadata(),b.metadata()) && Objects.equals(a.enchantments(),b.enchantments());
    }
}
