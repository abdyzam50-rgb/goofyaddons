package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.access.ActionRequirements;
import java.util.*;

/**
 * A bounded stack of exact recipe batches. Every cursor/grid mutation waits until the menu shows it, then a settle
 * delay in which a server that rejects the click reverts it, before the next step is chosen.
 */
public final class CraftingExecutor {
    public enum Result {WAITING,CRAFTED,BLOCKED}
    private static final int[] GRID={10,11,12,19,20,21,28,29,30};
    private static final int RESULT=23;
    private static final long FAST_SETTLE_MS=50;
    private final java.util.function.IntSupplier actionDelay;
    public CraftingExecutor(){this(com.goofy.goofyaddons.utils.ActionDelay::next);}
    CraftingExecutor(java.util.function.IntSupplier actionDelay){this.actionDelay=Objects.requireNonNull(actionDelay);}
    private ProductionRecipe recipe,capacityWaitRecipe;
    private int capacityWaitContainer;
    private long capacityWaitSince;
    private int container,source=-1,batchCount=1,lastCompletedBatches;
    public int completedBatches(){return lastCompletedBatches;}
    private final Set<Integer> splitTargets=new HashSet<>();
    private final Map<String,Integer> logicalAttempts=new HashMap<>();
    private Map<String,Integer> before;
    private MenuSnapshot sent;
    private long sentAt,started,next,inconsistentSince,resultSince;
    private long settleMs=FAST_SETTLE_MS,cooldownUntil,actionDelayMs;
    private int retries;
    private boolean submitted,quick;
    private String failure;
    public String failure(){return failure;}
    public boolean busy(){return recipe!=null;}
    public boolean quickCrafting(){return recipe!=null && quick;}
    public boolean outputCapacityWaiting(){return capacityWaitRecipe!=null;}
    public void reset(){recipe=null;sent=null;before=null;submitted=false;quick=false;source=-1;splitTargets.clear();failure=null;inconsistentSince=0;resultSince=0;capacityWaitRecipe=null;capacityWaitSince=0;logicalAttempts.clear();}
    public void slowdown(long now) {
        if(recipe==null)return;
        settleMs=Math.min(500,settleMs+100);cooldownUntil=Math.max(cooldownUntil,now+1000);
    }
    private Result block(String reason){failure=reason;return Result.BLOCKED;}
    public Result tick(ProductionRecipe wanted,MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,long now) {
        return tick(wanted,menu,actions,skills,Map.of(),now);
    }
    public Result tick(ProductionRecipe wanted,MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,Map<String,Integer> unlocks,long now) {
        return tick(wanted,1,menu,actions,skills,unlocks,now);
    }
    public Result tick(ProductionRecipe wanted,int requestedBatches,MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,Map<String,Integer> unlocks,long now) {
        if(failure!=null)return Result.BLOCKED;
        if(requestedBatches<1)return block("No craft batches requested");
        if(recipe==null){String reason=com.goofy.goofyaddons.features.access.RouteRequirements.craft(wanted.requirement(),skills,unlocks);if(reason!=null)return block(reason);}
        var compactorConflict=PersonalCompactors.conflict(menu,wanted.ingredients().keySet(),RecipeCatalog.instance());
        if(compactorConflict!=null)return block(compactorConflict);
        if(menu==null || !"Craft Item".equals(Chat.strip(menu.title())))return block("Open the SkyBlock Craft Item menu before crafting");
        if(wanted.kind()!=ProductionRecipe.Kind.CRAFT)return block("Not a crafting recipe");
        if(recipe==null) {
            if(capacityWaitRecipe!=null && (!wanted.equals(capacityWaitRecipe) || menu.containerId()!=capacityWaitContainer))
                return block("Crafting owner/container changed while waiting for output capacity; no craft submitted");
            if(!menu.cursorEmpty())return block("Crafting cannot adopt an occupied cursor");
            for(int slot:GRID)if(!empty(menu.slot(slot)))return block("Crafting grid already contains items; ownership must be reconciled");
            before=inventory(menu);
            batchCount=Math.min(64,requestedBatches);
            var quickSlot=quickSlot(wanted,menu,before);
            for(var cell:wanted.grid())if(cell!=null && quickSlot==null) {
                int limit=menu.slots().stream().filter(v->v.inPlayerInventory() && !v.empty() && cell.id().equals(v.bazaarProductId()))
                    .mapToInt(v->v.maxStackSize()).min().orElse(1);
                batchCount=Math.min(batchCount,Math.min(64,limit)/cell.count());
            }
            for(var need:wanted.ingredients().entrySet())batchCount=Math.min(batchCount,before.getOrDefault(need.getKey(),0)/need.getValue());
            if(batchCount<1)return block("Insufficient ingredients or ingredient stack capacity for a whole craft");
            long free=menu.slots().stream().filter(v->v.inPlayerInventory() && v.containerSlot()<36 && v.empty()).count();
            if(free==0)return block("Keep one free inventory slot for crafting output");
            // Stack capacity is an item fact, independent of whether a Quick Craft control
            // proves the requested recipe and amount. Read only exact output identities.
            Integer observedStack=outputStack(wanted,menu);
            int fallback=Set.of("BLAZE_POWDER","STICK","WOOD","PAPER","SUGAR","BOOK","BOWL","CHEST","GOLD_NUGGET","REDSTONE_TORCH_ON","EYE_OF_ENDER","GLASS_BOTTLE","WORKBENCH").contains(wanted.outputId())?64:1;
            int outputStack=observedStack==null?fallback:observedStack;
            int outputBatches=(int)(free*outputStack/wanted.outputCount());
            if(outputBatches<1 && observedStack==null && fallback==1) {
                if(capacityWaitRecipe==null) {
                    capacityWaitRecipe=wanted;capacityWaitContainer=menu.containerId();capacityWaitSince=now;
                    com.goofy.goofyaddons.diagnostics.Diagnostics.event("WARN","production.craft_capacity_wait",Map.of(
                        "recipe",wanted.key(),"output",wanted.outputId(),"outputPerCraft",wanted.outputCount(),"freeSlots",free,"container",menu.containerId(),"quickSlot",describe(menu.slot(16))));
                }
                if(now-capacityWaitSince>=8000)return block("Craft output stack size is still unreadable for "+wanted.outputId()
                    +"; "+free+" free slots, "+wanted.outputCount()+" output per craft; no crafting click sent");
                return Result.WAITING;
            }
            capacityWaitRecipe=null;capacityWaitSince=0;
            if(outputBatches<1)return block("Insufficient crafting output space for "+wanted.outputId()+": "+free
                +" free slots, stack limit "+outputStack+", "+wanted.outputCount()+" output per craft");
            batchCount=Math.min(batchCount,outputBatches);
            if(quickSlot!=null && craftable(quickSlot)<=batchCount){quick=true;batchCount=craftable(quickSlot);}
            else {
                quickSlot=null;
                batchCount=gridBatchLimit(wanted,menu,batchCount);
            }
            if(batchCount<1)return block("Ingredient grid stack limits do not fit a whole craft of "+wanted.outputId());
            for(var need:wanted.ingredients().entrySet())if(before.getOrDefault(need.getKey(),0)<need.getValue())return block("Missing ingredient: "+need.getKey());
            if(menu.slots().stream().noneMatch(s->s.inPlayerInventory() && s.containerSlot()<36 && s.empty()))return block("Keep one free inventory slot for crafting output");
            recipe=wanted;container=menu.containerId();started=now;next=now;submitted=false;
            if(quickSlot!=null) {
                String reason=ActionRequirements.blocked(quickSlot.lore(),skills,unlocks,ActionRequirements.Action.CRAFT);
                if(reason!=null)return block("Crafting requirement: "+reason);
                com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.quick_craft",Map.of("recipe",wanted.key(),"output",wanted.outputId(),"batches",batchCount,"slot",quickSlot.index(),"container",container));
                submitted=true;return send(menu,actions,quickSlot.index(),true,false,now);
            }
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
        if(now<cooldownUntil)return Result.WAITING;
        int verifiedBatches=quick?convertedBatches(menu):batchCount;
        if(submitted && verifiedBatches>0 && completed(menu,verifiedBatches) && now-sentAt>=Math.max(settleMs,actionDelayMs)){lastCompletedBatches=verifiedBatches;reset();return Result.CRAFTED;}
        if(sent!=null) {
            if(!sameContents(sent,menu)) {sent=null;retries=0;next=sentAt+Math.max(settleMs,actionDelayMs);}
            else if(now-sentAt>=2000) {
                if(retries>=2)return block("Crafting input was not acknowledged; grid and cursor retained");
                // A duplicate is permitted only against the exact same cursor/grid/inventory.
                if(quick && (!Objects.equals(sent.slot(actionSlot),menu.slot(actionSlot))
                        || craftable(menu.slot(actionSlot))!=batchCount))return block("Quick craft control changed before acknowledgement; items retained");
                retries++;sentAt=now;sent=menu;return replay(actions);
            } else return Result.WAITING;
        }
        if(now<next)return Result.WAITING;
        if(submitted) {
            int made=convertedBatches(menu);
            if(made>0 && made<batchCount && remainingGrid(menu,batchCount-made))return takeOutput(menu,actions,skills,unlocks,now);
            return Result.WAITING;
        }
        int target=-1,donor=-1;
        for(int i=0;i<9;i++) {
            var expected=recipe.grid().get(i);var actual=menu.slot(GRID[i]);
            if(expected==null){if(!empty(actual))return block("Unexpected item in recipe grid");continue;}
            if(!empty(actual) && !expected.id().equals(actual.bazaarProductId()))return block("Recipe grid identity/count mismatch");
            if(count(actual)>expected.count()*batchCount) {
                if(!splitTargets.contains(i) || !canPark(count(actual),expected.count()*batchCount))return block("Crafting stack split changed unexpectedly; grid and cursor retained");
                if(donor<0)donor=i;
            } else {
                splitTargets.remove(i);
                if(count(actual)<expected.count()*batchCount && (target<0 || expected.id().equals(recipe.grid().get(target).id())
                        && expected.count()>recipe.grid().get(target).count()))target=i;
            }
        }
        if(menu.cursorEmpty() && donor>=0) {
            return send(menu,actions,GRID[donor],false,true,now);
        }
        if(!menu.cursorEmpty()) {
            var cursor=menu.carried();
            if(cursor==null || cursor.empty() || cursor.bazaarProductId()==null)return block("Unreadable crafting cursor");
            // Match the capacity plan's largest-cell-first allocation within this
            // ingredient, including its already parked halves and partial cells.
            int cursorTarget=-1;
            for(int i=0;i<9;i++) {
                var expected=recipe.grid().get(i);
                if(expected!=null && expected.id().equals(cursor.bazaarProductId()) && count(menu.slot(GRID[i]))<expected.count()*batchCount
                        && (empty(menu.slot(GRID[i])) || cursor.canStackWith(menu.slot(GRID[i])))
                        && cursor.count()+compatibleUnits(cursor,menu)>=expected.count()*batchCount-count(menu.slot(GRID[i]))
                        && (cursorTarget<0 || expected.count()>recipe.grid().get(cursorTarget).count()))cursorTarget=i;
            }
            target=cursorTarget;
            if(target>=0 && recipe.grid().get(target).id().equals(cursor.bazaarProductId())
                    && (empty(menu.slot(GRID[target])) || cursor.canStackWith(menu.slot(GRID[target])))) {
                int remaining=recipe.grid().get(target).count()*batchCount-count(menu.slot(GRID[target]));
                if(empty(menu.slot(GRID[target])) && cursor.count()>remaining && canPark(cursor.count(),remaining)) {
                    // Park the whole stack in the grid; reuse each half in another matching cell.
                    splitTargets.add(target);return send(menu,actions,GRID[target],false,false,now);
                }
                return send(menu,actions,GRID[target],false,cursor.count()>remaining,now);
            }
            var origin=menu.slot(source);
            if(origin==null || !origin.inPlayerInventory() || (!origin.empty() && !cursor.canStackWith(origin))
                    || count(origin)+cursor.count()>origin.maxStackSize())return block("Cursor return slot no longer matches owned ingredients");
            return send(menu,actions,source,false,false,now);
        }
        if(target>=0) {
            var need=recipe.grid().get(target);
            var current=menu.slot(GRID[target]);int remaining=need.count()*batchCount-count(current);
            var available=stock(menu,need.id()).stream().filter(v->empty(current)||v.canStackWith(current))
                .filter(v->compatibleUnits(v,menu)>=remaining).toList();
            if(available.isEmpty())return block("No compatible ingredient stack can fill the owned recipe cell for "+need.id()+"; grid and cursor retained");
            // Preserve component groups selected by the grid capacity plan, then take
            // the largest fragment within the smallest group that fills this cell.
            var stack=available.stream().min(Comparator.comparingInt((SlotView v)->compatibleUnits(v,menu))
                .thenComparing(Comparator.comparingInt(SlotView::count).reversed())).orElseThrow();
            source=stack.index();return send(menu,actions,source,false,false,now);
        }
        return takeOutput(menu,actions,skills,unlocks,now);
    }
    private Result takeOutput(MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,Map<String,Integer> unlocks,long now) {
        // Hypixel's Quick Crafting column (16, 25, 34) can show the same item before the grid is
        // full; only the result slot holds the grid's output.
        var outputs=menu.slots().stream().filter(s->s.index()==RESULT && !s.empty() && recipe.outputId().equals(s.bazaarProductId())).toList();
        if(outputs.size()!=1 || outputs.getFirst().count()!=recipe.outputCount()) {
            if(resultSince==0) {
                resultSince=now;
                com.goofy.goofyaddons.diagnostics.Diagnostics.event("WARN","production.craft_result_wait",Map.of(
                    "recipe",recipe.key(),"expected",recipe.outputCount()+"x "+recipe.outputId(),"observed",describe(menu.slot(RESULT)),"container",container));
            }
            if(now-resultSince>=8000)return block("Craft result was not verified: expected "+recipe.outputCount()+"x "+recipe.outputId()
                +", observed "+describe(menu.slot(RESULT))+"; ingredients retained in grid");
            return Result.WAITING;
        }
        resultSince=0;
        var output=outputs.getFirst();String reason=ActionRequirements.blocked(output.lore(),skills,unlocks,ActionRequirements.Action.CRAFT);
        if(reason!=null)return block("Crafting requirement: "+reason);
        com.goofy.goofyaddons.diagnostics.Diagnostics.event("INFO","production.craft_result_submit",Map.of(
            "recipe",recipe.key(),"output",recipe.outputId(),"batches",batchCount,"observed",describe(output),
            "rawId",Objects.toString(output.customId(),"unknown"),"enchantments",Objects.toString(output.enchantments(),"absent"),"container",container));
        submitted=true;return send(menu,actions,output.index(),true,false,now);
    }
    // Shift quick crafting may consume every available recipe. Never send it when that
    // would exceed this owner's requested batch, or when its ingredient recipe is unreadable.
    private static SlotView quickSlot(ProductionRecipe wanted,MenuSnapshot menu,Map<String,Integer> stock) {
        int available=wanted.ingredients().entrySet().stream().mapToInt(e->stock.getOrDefault(e.getKey(),0)/e.getValue()).min().orElse(0);
        var expected=new HashSet<String>();
        wanted.ingredients().forEach((id,qty)->expected.add(qty+"x "+RecipeCatalog.instance().name(id)));
        for(int index:new int[]{16,25,34}) {
            var slot=menu.slot(index);
            if(empty(slot)||!wanted.outputId().equals(slot.bazaarProductId())||slot.count()!=wanted.outputCount()
                    ||craftable(slot)!=available||available<1)continue;
            var lines=slot.loreLines()==null?List.<String>of():slot.loreLines().stream().map(Chat::strip).toList();
            int start=lines.indexOf("Ingredients:");
            if(start<0||!lines.contains("Click to craft!"))continue;
            var actual=new HashSet<String>();
            for(int i=start+1;i<lines.size()&&!lines.get(i).isBlank();i++)actual.add(lines.get(i));
            if(actual.equals(expected))return slot;
        }
        return null;
    }
    private static List<SlotView> stock(MenuSnapshot menu,String id){
        return menu.slots().stream().filter(v->v.inPlayerInventory() && v.containerSlot()<36 && !empty(v) && id.equals(v.bazaarProductId())).toList();
    }
    private static int compatibleUnits(SlotView item,MenuSnapshot menu){
        return stock(menu,item.bazaarProductId()).stream().filter(item::canStackWith).mapToInt(SlotView::count).sum();
    }
    private record IngredientGroup(SlotView item,int units,int limit) {}
    private static int gridBatchLimit(ProductionRecipe wanted,MenuSnapshot menu,int requested){
        var byProduct=new HashMap<String,List<IngredientGroup>>();
        for(String id:wanted.ingredients().keySet()){
            var groups=new ArrayList<IngredientGroup>();
            for(var item:stock(menu,id)){
                int index=-1;for(int i=0;i<groups.size();i++)if(item.canStackWith(groups.get(i).item())){index=i;break;}
                int limit=Math.max(1,Math.min(64,item.maxStackSize()));
                if(index<0)groups.add(new IngredientGroup(item,item.count(),limit));
                else{var old=groups.get(index);groups.set(index,new IngredientGroup(old.item(),old.units()+item.count(),Math.min(old.limit(),limit)));}
            }
            byProduct.put(id,groups);
        }
        for(int batches=requested;batches>0;batches--){
            boolean fits=true;
            for(var entry:byProduct.entrySet()){
                var groups=entry.getValue();int[] remaining=groups.stream().mapToInt(IngredientGroup::units).toArray();
                final int amount=batches;
                var cells=wanted.grid().stream().filter(v->v!=null && entry.getKey().equals(v.id()))
                    .map(v->v.count()*amount).sorted(Comparator.reverseOrder()).toList();
                for(int cell:cells){
                    int best=-1;for(int i=0;i<groups.size();i++)if(groups.get(i).limit()>=cell && remaining[i]>=cell
                        && (best<0 || remaining[i]<remaining[best]))best=i;
                    if(best<0){fits=false;break;}remaining[best]-=cell;
                }
                if(!fits)break;
            }
            if(fits)return batches;
        }
        return 0;
    }
    private static Integer outputStack(ProductionRecipe wanted,MenuSnapshot menu) {
        return menu.slots().stream().filter(s->!empty(s) && wanted.outputId().equals(s.bazaarProductId())
                && (s.inPlayerInventory() && s.containerSlot()<36 || !s.inPlayerInventory() && (s.index()==RESULT || s.index()==16 || s.index()==25 || s.index()==34)))
            .map(s->Math.max(1,Math.min(64,s.maxStackSize()))).min(Integer::compareTo).orElse(null);
    }
    private static int craftable(SlotView slot) {
        if(slot==null)return -1;
        var match=java.util.regex.Pattern.compile("(?m)^Craftable: ([0-9,]+)x$").matcher(Chat.strip(slot.lore()));
        if(!match.find())return -1;
        try{return Integer.parseInt(match.group(1).replace(",",""));}catch(NumberFormatException e){return -1;}
    }
    private int actionSlot;private boolean actionShift,actionRight;
    private Result send(MenuSnapshot menu,GameActions actions,int slot,boolean shift,boolean right,long now){
        sent=menu;sentAt=now;retries=0;actionSlot=slot;actionShift=shift;actionRight=right;return replay(actions);
    }
    private Result replay(GameActions actions){
        // Swapping two component variants changes metadata, but leaves the recipe's
        // product counts unchanged. Bound that cycle independently of packet retries.
        String state=logicalState(sent)+"|"+actionSlot+":"+actionShift+":"+actionRight;
        int attempts=logicalAttempts.merge(state,1,Integer::sum);
        if(attempts>3){
            com.goofy.goofyaddons.diagnostics.Diagnostics.event("ERROR","production.craft_stalled",Map.of(
                "recipe",recipe.key(),"slot",actionSlot,"attempts",attempts-1,"target",describe(sent.slot(actionSlot)),
                "cursor",describe(sent.carried()),"container",container));
            return block("Crafting made no progress after three attempts at slot "+actionSlot+" ("+describe(sent.slot(actionSlot))
                +"); grid and cursor retained");
        }
        actionDelayMs=Math.max(FAST_SETTLE_MS,actionDelay.getAsInt());
        if(actionRight)actions.rightClick(actionSlot);else actions.click(actionSlot,actionShift);
        return Result.WAITING;
    }
    private String logicalState(MenuSnapshot menu){
        var state=new StringBuilder();
        for(int index:GRID){var v=menu.slot(index);state.append(index).append(':').append(v==null?null:v.bazaarProductId()).append(':').append(count(v)).append(';');}
        var cursor=menu.carried();state.append("cursor:").append(cursor==null?null:cursor.bazaarProductId()).append(':').append(count(cursor));
        for(var v:menu.slots())if(v.inPlayerInventory() && v.containerSlot()<36 && !empty(v) && v.bazaarProductId()!=null
                && (recipe.ingredients().containsKey(v.bazaarProductId()) || recipe.outputId().equals(v.bazaarProductId())))
            state.append(';').append(v.index()).append(':').append(v.bazaarProductId()).append(':').append(count(v));
        return state.toString();
    }
    private static boolean canPark(int stack,int required) {
        // Also halve an almost-full target once, then top it up: 64 -> 32 + 15 for 47,
        // instead of right-clicking 47 individual items from the inventory stack.
        return canSplit(stack,required) || required>=(stack+1)/2;
    }
    private static boolean canSplit(int stack,int required) {
        if(required<1 || stack<=required || stack%required!=0)return false;
        int ratio=stack/required;return (ratio & (ratio-1))==0;
    }
    private boolean completed(MenuSnapshot menu,int batches){
        if(!menu.cursorEmpty())return false;
        for(int slot:GRID)if(!empty(menu.slot(slot)))return false;
        var counts=inventory(menu);
        if(counts.getOrDefault(recipe.outputId(),0)!=before.getOrDefault(recipe.outputId(),0)+recipe.outputCount()*batches)return false;
        for(var e:recipe.ingredients().entrySet())if(counts.getOrDefault(e.getKey(),0)!=before.getOrDefault(e.getKey(),0)-e.getValue()*batches)return false;
        return true;
    }
    private int convertedBatches(MenuSnapshot menu) {
        int delta=inventory(menu).getOrDefault(recipe.outputId(),0)-before.getOrDefault(recipe.outputId(),0);
        return delta>=0 && delta%recipe.outputCount()==0?delta/recipe.outputCount():-1;
    }
    private boolean remainingGrid(MenuSnapshot menu,int batches) {
        if(!menu.cursorEmpty())return false;
        for(int i=0;i<9;i++) {
            var need=recipe.grid().get(i);var actual=menu.slot(GRID[i]);
            if(need==null){if(!empty(actual))return false;}
            else if(empty(actual)||!need.id().equals(actual.bazaarProductId())||count(actual)!=need.count()*batches)return false;
        }
        return true;
    }
    private boolean conserved(MenuSnapshot menu,boolean allowOutput){
        var counts=inventory(menu);
        for(int slot:GRID)add(counts,menu.slot(slot));
        if(!menu.cursorEmpty())add(counts,menu.carried());
        int made=allowOutput?convertedBatches(menu):0;
        if(made<0||made>batchCount)return false;
        var ids=new HashSet<>(before.keySet());ids.addAll(counts.keySet());ids.addAll(recipe.ingredients().keySet());ids.add(recipe.outputId());
        for(String id:ids){int old=before.getOrDefault(id,0),current=counts.getOrDefault(id,0);
            if(current!=old-recipe.ingredients().getOrDefault(id,0)*made+(id.equals(recipe.outputId())?recipe.outputCount()*made:0))return false;
        }
        return true;
    }
    private static Map<String,Integer> inventory(MenuSnapshot menu){
        var result=new HashMap<String,Integer>();for(var slot:menu.slots())if(slot.inPlayerInventory() && slot.containerSlot()<36)add(result,slot);return result;
    }
    private static void add(Map<String,Integer> counts,SlotView slot){if(!empty(slot) && slot.bazaarProductId()!=null)counts.merge(slot.bazaarProductId(),slot.count(),Integer::sum);}
    private static int count(SlotView slot){return empty(slot)?0:slot.count();}
    private static boolean empty(SlotView slot){return slot==null || slot.empty();}
    public static boolean reusableMenu(MenuSnapshot menu) {
        if(menu==null || !"Craft Item".equals(Chat.strip(menu.title())) || !menu.cursorEmpty())return false;
        for(int slot:GRID)if(!empty(menu.slot(slot)))return false;
        return true;
    }
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
        return slot.count()+"x "+(slot.bazaarProductId()==null?Chat.strip(slot.hoverName()):slot.bazaarProductId())+("ENCHANTED_BOOK".equals(slot.bazaarProductId())?" enchantments="+slot.enchantments():"")+(slot.metadata()==null?"":" "+slot.metadata().vanillaId());
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
