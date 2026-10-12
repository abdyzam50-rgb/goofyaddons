package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Ender chest pages and backpacks a craft run may park bought inputs in, and how many units
 * of each product every run left on each page.
 *
 * <p>The ledger is the only proof that stored units belong to a run, so it is saved after
 * every verified move. A page is used for a product only while its contents match the
 * ledger exactly: a page that also holds the player's own units of that product is skipped,
 * and a page whose owned count changed stops the run for review.
 */
public final class ProductionStorage {
    /** The setting's default: ender chest pages 1–3, then backpacks 1–4. */
    public static final String DEFAULT_PAGES="ec 1,ec 2,ec 3,backpack 1,backpack 2,backpack 3,backpack 4";
    private static final Pattern EC_COMMAND=Pattern.compile("(?i)(?:ec|enderchest)\\s+([1-9])");
    private static final Pattern BP_COMMAND=Pattern.compile("(?i)(?:backpack|bp)\\s+([1-9][0-9]?)");
    private static final Pattern EC_TITLE=Pattern.compile("Ender Chest \\((\\d+)/(\\d+)\\)");
    private static final Pattern BP_TITLE=Pattern.compile(".*Backpack \\(Slot #(\\d+)\\)");

    public record Page(String command,boolean enderChest,int number) {
        public boolean open(MenuSnapshot menu) {
            if(menu==null || menu.title()==null)return false;
            var title=(enderChest?EC_TITLE:BP_TITLE).matcher(Chat.strip(menu.title()).strip());
            try{return title.matches() && Integer.parseInt(title.group(1))==number;}
            catch(NumberFormatException unreadable){return false;}
        }
    }

    /** Pages named by the setting, in order; unknown entries and repeats are ignored. */
    public static List<Page> pages(String setting) {
        var pages=new ArrayList<Page>();var seen=new HashSet<String>();
        for(String raw:(setting==null?DEFAULT_PAGES:setting).split(",")) {
            String text=raw.strip().replaceFirst("^/","");Page page=null;
            var ec=EC_COMMAND.matcher(text);var bp=BP_COMMAND.matcher(text);
            if(ec.matches())page=new Page("ec "+ec.group(1),true,Integer.parseInt(ec.group(1)));
            else if(bp.matches())page=new Page("backpack "+bp.group(1),false,Integer.parseInt(bp.group(1)));
            if(page!=null && seen.add(page.command()))pages.add(page);
        }
        return List.copyOf(pages);
    }

    private record Entry(String parent,String page,String product,int units) {}
    private record Observation(int emptySlots,long at) {}
    private final Path path;
    private final List<Entry> entries=new ArrayList<>();
    private final Map<String,Observation> observed=new HashMap<>();

    public ProductionStorage(Path path)throws java.io.IOException {
        this.path=path;
        if(path!=null && Files.exists(path)) {
            try {
                var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for(var value:root.getAsJsonArray("entries")) {
                    var row=value.getAsJsonObject();
                    var entry=new Entry(row.get("parent").getAsString(),row.get("page").getAsString(),row.get("product").getAsString(),row.get("units").getAsInt());
                    if(entry.units()<=0 || !ProductionRecipe.validId(entry.product()))throw new IllegalStateException("Invalid storage entry");
                    entries.add(entry);
                }
                if(root.has("observed"))for(var page:root.getAsJsonObject("observed").entrySet()) {
                    var row=page.getValue().getAsJsonObject();
                    observed.put(page.getKey(),new Observation(row.get("emptySlots").getAsInt(),row.get("at").getAsLong()));
                }
            }catch(RuntimeException malformed){throw new java.io.IOException("Production storage ledger is unreadable; nothing will be moved",malformed);}
        }
    }

    /** Units of this product the run left anywhere in storage. */
    public synchronized int stored(String parent,String product) {
        return entries.stream().filter(e->e.parent().equals(parent) && e.product().equals(product)).mapToInt(Entry::units).sum();
    }
    /** Every product the run left in storage, with its units. */
    public synchronized Map<String,Integer> stored(String parent) {
        var units=new TreeMap<String,Integer>();
        for(var e:entries)if(e.parent().equals(parent))units.merge(e.product(),e.units(),Integer::sum);
        return units;
    }
    /** Units of a product that any run owns on this page. */
    public synchronized int owned(String page,String product) {
        return entries.stream().filter(e->e.page().equals(page) && e.product().equals(product)).mapToInt(Entry::units).sum();
    }
    synchronized int owned(String parent,String page,String product) {
        return entries.stream().filter(e->e.parent().equals(parent) && e.page().equals(page) && e.product().equals(product)).mapToInt(Entry::units).sum();
    }
    /** Products owned on this page by any run. */
    synchronized Set<String> productsOn(String page) {
        var ids=new TreeSet<String>();for(var e:entries)if(e.page().equals(page))ids.add(e.product());return ids;
    }

    /** Records a verified move; positive into storage, negative out of it. */
    public synchronized void move(String parent,String page,String product,int delta)throws java.io.IOException {
        if(delta==0)return;
        var previous=List.copyOf(entries);
        int current=owned(parent,page,product);
        if(current+delta<0)throw new IllegalStateException("Storage ledger would go negative");
        entries.removeIf(e->e.parent().equals(parent) && e.page().equals(page) && e.product().equals(product));
        if(current+delta>0)entries.add(new Entry(parent,page,product,current+delta));
        try{save();}catch(java.io.IOException failed){entries.clear();entries.addAll(previous);throw failed;}
    }

    /** Forgets what a finished or reviewed run left; the units stay where they are. */
    public synchronized List<String> release(String parent)throws java.io.IOException {
        var released=entries.stream().filter(e->e.parent().equals(parent)).map(e->e.units()+"x "+e.product()+" in "+e.page()).toList();
        if(released.isEmpty())return released;
        var previous=List.copyOf(entries);
        entries.removeIf(e->e.parent().equals(parent));
        try{save();}catch(java.io.IOException failed){entries.clear();entries.addAll(previous);throw failed;}
        return released;
    }

    /** Remembers how many empty slots a page showed; planning uses the latest reading. */
    public synchronized void observe(String page,int emptySlots,long now) {
        observed.put(page,new Observation(Math.max(0,emptySlots),now));
        try{save();}catch(java.io.IOException ignored){/* The reading is advisory; the next visit refreshes it. */}
    }
    /** Empty slots across these pages at their latest readings, or -1 while any page is unread. */
    public synchronized int emptySlots(List<Page> pages) {
        int total=0;
        for(var page:pages){var reading=observed.get(page.command());if(reading==null)return -1;total+=reading.emptySlots();}
        return total;
    }

    public synchronized Map<String,Object> diagnosticState() {
        var state=new LinkedHashMap<String,Object>();
        state.put("entries",entries.stream().map(e->Map.of("parent",e.parent(),"page",e.page(),"product",e.product(),"units",e.units())).toList());
        var pages=new TreeMap<String,Object>();observed.forEach((page,o)->pages.put(page,Map.of("emptySlots",o.emptySlots(),"at",o.at())));
        state.put("observed",pages);return state;
    }

    private void save()throws java.io.IOException {
        if(path==null)return;
        if(path.getParent()!=null)Files.createDirectories(path.getParent());
        var root=new JsonObject();root.addProperty("schema",1);
        var rows=new JsonArray();
        for(var e:entries){var row=new JsonObject();row.addProperty("parent",e.parent());row.addProperty("page",e.page());row.addProperty("product",e.product());row.addProperty("units",e.units());rows.add(row);}
        root.add("entries",rows);
        var pages=new JsonObject();
        observed.forEach((page,o)->{var row=new JsonObject();row.addProperty("emptySlots",o.emptySlots());row.addProperty("at",o.at());pages.add(page,row);});
        root.add("observed",pages);
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(root));
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }

    // ---- Moves ---------------------------------------------------------------------------

    /** Shared by deposits, withdrawals and scans: the page-opening and per-click checks. */
    public static final class Move {
        public enum Kind { DEPOSIT, WITHDRAW, SCAN }
        public enum Result { WAITING, DONE, NO_SPACE, BLOCKED }
        private static final long OPEN_TIMEOUT_MS=12_000, COMMAND_RETRY_MS=5_000, SETTLE_MS=400, CLICK_RETRY_MS=1_500, CLICK_TIMEOUT_MS=10_000;
        private final Kind kind;
        private final String parent;
        private final ProductionStorage ledger;
        private final List<Page> pages;
        private final Set<String> products;
        private final Map<String,Integer> remaining;
        private final Set<String> finishedPages=new HashSet<>();
        private final Set<String> skippedPages=new TreeSet<>();
        /** Pages that never opened (locked or missing) during this move. */
        public Set<String> skippedPages(){return Set.copyOf(skippedPages);}
        private Page page;
        private int container=Integer.MIN_VALUE;
        private long openSince,commandAt,closeAt,seenSince;
        private boolean verified;
        private String pendingProduct;
        private int beforeInventory,beforeStorage,pendingSlot,clickRetries;
        private long clickedAt;
        private String failure;
        private int moved;
        private int freeSlotsTarget;
        private final Map<String,Integer> movedByProduct=new HashMap<>();

        private Move(Kind kind,String parent,ProductionStorage ledger,List<Page> pages,Set<String> products,Map<String,Integer> remaining) {
            this.kind=kind;this.parent=parent;this.ledger=ledger;this.pages=List.copyOf(pages);this.products=Set.copyOf(products);this.remaining=new HashMap<>(remaining);
        }
        /** Moves every inventory unit of these products into storage, page by page. */
        public static Move deposit(String parent,ProductionStorage ledger,List<Page> pages,Set<String> products) {
            return new Move(Kind.DEPOSIT,parent,ledger,pages,products,Map.of());
        }
        /** Park only enough stacks to make the requested inventory room. */
        public static Move makeRoom(String parent,ProductionStorage ledger,List<Page> pages,Set<String> products,int emptySlots) {
            var move=deposit(parent,ledger,pages,products);move.freeSlotsTarget=Math.max(1,Math.min(36,emptySlots));return move;
        }
        /** Brings at least these units back into the inventory, from pages the run owns them on. */
        public static Move withdraw(String parent,ProductionStorage ledger,List<Page> pages,Map<String,Integer> units) {
            var wanted=new HashMap<String,Integer>();units.forEach((id,n)->{if(n>0)wanted.put(id,n);});
            return new Move(Kind.WITHDRAW,parent,ledger,pages,wanted.keySet(),wanted);
        }
        /** Opens each page once to read its empty slots and check the ledger against it. */
        public static Move scan(ProductionStorage ledger,List<Page> pages) {
            return new Move(Kind.SCAN,null,ledger,pages,Set.of(),Map.of());
        }
        public Kind kind(){return kind;}
        public String failure(){return failure;}
        /** Units this move has verified so far. */
        public int moved(){return moved;}
        /** Units of one product this move has verified so far. */
        public int moved(String product){return movedByProduct.getOrDefault(product,0);}
        public String describe(){return (kind==Kind.DEPOSIT?"Storing":kind==Kind.WITHDRAW?"Taking from storage":"Checking storage")+(page==null?"":" · "+page.command());}

        public Result tick(MenuSnapshot menu,GameActions actions,long now) {
            if(failure!=null)return Result.BLOCKED;
            if(pendingProduct!=null)return settle(menu,actions,now);
            if(page==null) {
                page=nextPage(menu);
                if(page==null)return finish(menu,actions);
                openSince=0;verified=false;seenSince=0;container=Integer.MIN_VALUE;
            }
            if(!page.open(menu)) {
                if(openSince==0)openSince=now;
                if(now-openSince>OPEN_TIMEOUT_MS) {
                    // A locked page or an empty backpack slot never opens; skip it rather than stop.
                    // Record it as full so the planner stops rescanning every page before each pick.
                    ledger.observe(page.command(),0,now);skippedPages.add(page.command());
                    finishedPages.add(page.command());page=null;return Result.WAITING;
                }
                if(menu!=null && menu.title()!=null) {
                    if(!menu.cursorEmpty())return now-openSince>OPEN_TIMEOUT_MS/2?block("Storage needs an empty cursor"):Result.WAITING;
                    if(now>=closeAt){closeAt=now+COMMAND_RETRY_MS;actions.closeMenu();}
                    return Result.WAITING;
                }
                if(now>=commandAt){commandAt=now+COMMAND_RETRY_MS;actions.command(page.command());}
                return Result.WAITING;
            }
            if(container==Integer.MIN_VALUE || container!=menu.containerId()){container=menu.containerId();seenSince=now;}
            if(now-seenSince<SETTLE_MS || !menu.cursorEmpty())return Result.WAITING;
            if(!verified) {
                for(String product:ledger.productsOn(page.command())) {
                    int owned=ledger.owned(page.command(),product),found=storageCount(menu,product);
                    if(found!=owned)return block("Storage page "+page.command()+" should hold "+owned+" "+product+" but holds "+found+"; nothing moved");
                }
                ledger.observe(page.command(),menu.emptyContainerSlots(),now);
                verified=true;
            }
            return switch(kind) {
                case SCAN -> nextOnPage(menu,actions);
                case DEPOSIT -> depositStep(menu,actions,now);
                case WITHDRAW -> withdrawStep(menu,actions,now);
            };
        }

        private Page nextPage(MenuSnapshot menu) {
            for(var candidate:pages) {
                if(finishedPages.contains(candidate.command()))continue;
                if(kind==Kind.WITHDRAW && remaining.keySet().stream().noneMatch(id->remaining.get(id)>0 && ledger.owned(parent,candidate.command(),id)>0))continue;
                // Prefer the page already open, so a deposit does not reopen storage for nothing.
                return candidate;
            }
            return null;
        }

        private Result depositStep(MenuSnapshot menu,GameActions actions,long now) {
            if(freeSlotsTarget>0&&menu.emptyInventorySlots()>=freeSlotsTarget)return finish(menu,actions);
            if(products.stream().allMatch(id->inventoryCount(menu,id)==0))return finish(menu,actions);
            for(var slot:menu.slots()) {
                if(!inventorySlot(slot))continue;
                String id=ProductionMenus.productId(slot);
                if(id==null || !products.contains(id))continue;
                // Never mix with units the ledger does not own: they would become indistinguishable.
                if(storageCount(menu,id)!=ledger.owned(page.command(),id))continue;
                boolean room=menu.emptyContainerSlots()>0 || storageRoom(menu,id)>0;
                if(!room)break;
                return click(menu,actions,slot,id,now);
            }
            return nextOnPage(menu,actions);
        }

        private Result withdrawStep(MenuSnapshot menu,GameActions actions,long now) {
            SlotView best=null;String bestId=null;
            for(var slot:menu.slots()) {
                if(slot.inPlayerInventory() || slot.empty())continue;
                String id=ProductionMenus.productId(slot);
                if(id==null || remaining.getOrDefault(id,0)<=0 || ledger.owned(parent,page.command(),id)<slot.count())continue;
                // Prefer the largest stack the need covers, else the smallest one, to keep the
                // inventory free for crafting.
                boolean fits=slot.count()<=remaining.get(id);
                boolean bestFits=best!=null && best.count()<=remaining.get(bestId);
                if(best==null || fits && !bestFits || fits==bestFits && (fits?slot.count()>best.count():slot.count()<best.count())){best=slot;bestId=id;}
            }
            if(best==null)return nextOnPage(menu,actions);
            if(menu.emptyInventorySlots()==0 && menu.partialStackSpace(bestId)<best.count())return Result.NO_SPACE;
            return click(menu,actions,best,bestId,now);
        }

        private Result click(MenuSnapshot menu,GameActions actions,SlotView slot,String id,long now) {
            pendingProduct=id;pendingSlot=slot.index();clickRetries=0;clickedAt=now;
            beforeInventory=inventoryCount(menu,id);beforeStorage=storageCount(menu,id);
            actions.click(slot.index(),true);
            return Result.WAITING;
        }

        private Result settle(MenuSnapshot menu,GameActions actions,long now) {
            if(!page.open(menu) || menu.containerId()!=container)return block("Storage page closed during a move; the ledger was not changed");
            if(!menu.cursorEmpty())return now-clickedAt>CLICK_TIMEOUT_MS?block("Storage move left an item on the cursor"):Result.WAITING;
            int inventory=inventoryCount(menu,pendingProduct),storage=storageCount(menu,pendingProduct);
            int out=kind==Kind.DEPOSIT?beforeInventory-inventory:beforeStorage-storage;
            int in=kind==Kind.DEPOSIT?storage-beforeStorage:inventory-beforeInventory;
            if(out>0 && out==in) {
                try{ledger.move(parent,page.command(),pendingProduct,kind==Kind.DEPOSIT?out:-out);}
                catch(Exception failed){return block("Storage ledger could not be saved after a verified move; stop and check storage");}
                ledger.observe(page.command(),menu.emptyContainerSlots(),now);
                moved+=out;movedByProduct.merge(pendingProduct,out,Integer::sum);
                if(kind==Kind.WITHDRAW)remaining.merge(pendingProduct,-out,Integer::sum);
                pendingProduct=null;seenSince=now;return Result.WAITING;
            }
            if(out<0 || in<0 || out!=in && (out>0 || in>0)) {
                return now-clickedAt>CLICK_RETRY_MS?block("Storage move counts disagree for "+pendingProduct+"; the ledger was not changed"):Result.WAITING;
            }
            if(now-clickedAt>CLICK_TIMEOUT_MS)return block("Storage move of "+pendingProduct+" was not acknowledged");
            if(now-clickedAt>CLICK_RETRY_MS*(clickRetries+1) && clickRetries<2) {
                var slot=menu.slot(pendingSlot);
                if(slot!=null && !slot.empty() && pendingProduct.equals(ProductionMenus.productId(slot))){clickRetries++;actions.click(pendingSlot,true);}
            }
            return Result.WAITING;
        }

        private Result nextOnPage(MenuSnapshot menu,GameActions actions) {
            finishedPages.add(page.command());page=null;
            if(menu!=null && menu.cursorEmpty())actions.closeMenu();
            return Result.WAITING;
        }

        private Result finish(MenuSnapshot menu,GameActions actions) {
            if(menu!=null && menu.title()!=null && menu.cursorEmpty() && pages.stream().anyMatch(p->p.open(menu)))actions.closeMenu();
            if(kind==Kind.DEPOSIT && menu!=null && (freeSlotsTarget>0?menu.emptyInventorySlots()<freeSlotsTarget:products.stream().anyMatch(id->inventoryCount(menu,id)>0)))return Result.NO_SPACE;
            if(kind==Kind.WITHDRAW && remaining.entrySet().stream().anyMatch(e->e.getValue()>0 && ledger.stored(parent,e.getKey())>0))
                return block("Stored units could not be found on any storage page");
            return Result.DONE;
        }

        private Result block(String reason){failure=reason;return Result.BLOCKED;}

        static boolean inventorySlot(SlotView slot){return slot.inPlayerInventory() && slot.containerSlot()>=0 && slot.containerSlot()<36 && !slot.empty();}
        static int inventoryCount(MenuSnapshot menu,String id) {
            int total=0;if(menu==null)return 0;
            for(var slot:menu.slots())if(inventorySlot(slot) && id.equals(ProductionMenus.productId(slot)))total+=slot.count();
            return total;
        }
        static int storageCount(MenuSnapshot menu,String id) {
            int total=0;
            for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty() && id.equals(ProductionMenus.productId(slot)))total+=slot.count();
            return total;
        }
        private static int storageRoom(MenuSnapshot menu,String id) {
            if(RecipeCatalog.instance().unstackable(id))return 0;
            int room=0;
            for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty() && id.equals(ProductionMenus.productId(slot))
                    && (slot.metadata().uuid()==null||slot.metadata().uuid().isBlank())
                    && menu.slots().stream().anyMatch(source->inventorySlot(source)&&id.equals(ProductionMenus.productId(source))&&source.canStackWith(slot)))
                room+=Math.max(0,slot.maxStackSize()-slot.count());
            return room;
        }
    }
}
