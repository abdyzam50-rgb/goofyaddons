package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.generalflipper.OrderLore;
import com.goofy.goofyaddons.features.profit.TradeReceipts;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;
import java.util.regex.Pattern;

/** Retire one owned route: cancel its order, retrieve holdings, sell one exact level at a time.
 * Instant-sale control/product slots and Sold receipts: SkyHanni BazaarApi.kt,
 * commit 0913ae5c96fc52d03aa41ad1a8369c8643e6dc63. Never click bulk Sell Inventory.
 * Missing acknowledgment blocks instead of replaying an uncertain sale. */
public final class BookRetirement {
    public enum Result { WAITING, COMPLETE, COLLECT_SALE, BLOCKED }
    public interface Receipts {
        void acquired(Task task,int count,double price,String event);
        void sold(Task task,int baseUnits,double proceeds,String event);
        boolean checkpoint();
    }
    private enum Phase { ORDERS, OPTIONS, CANCEL, HOLDINGS, SEARCH, SALE }
    private Phase phase=Phase.ORDERS;
    private String trade,reason="";
    private long started=-1,nextAction,stableAt;
    private int stableContainer=-1,cancelContainer=-1,clickedContainer=-1,level,quantity,beforeClaim;
    private List<SlotView> contents;
    private boolean claiming;
    private double claimPrice;
    private Double saleProceeds;
    private String saleEvent;
    private final BookTransfer transfer=new BookTransfer();
    private static final Pattern SOLD=Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");
    public String reason(){return reason;}
    public boolean pending(){return trade!=null;}
    public void reset(){trade=null;reason="";started=-1;phase=Phase.ORDERS;nextAction=0;stableContainer=-1;contents=null;claiming=false;cancelContainer=-1;clickedContainer=-1;saleProceeds=null;transfer.reset();}
    public void receipt(Task task,String raw) {
        if(phase!=Phase.SALE || !Objects.equals(trade,task.getProfitTradeId()))return;
        var m=SOLD.matcher(Chat.strip(raw));
        if(!m.matches() || !m.group(2).equals(task.getBook().getRomanLevel(level)))return;
        try {
            if(Integer.parseInt(m.group(1).replace(",",""))!=quantity)return;
            double coins=Double.parseDouble(m.group(3).replace(",",""));
            if(Double.isFinite(coins)&&coins>0)saleProceeds=coins;
        } catch(NumberFormatException ignored){}
    }
    private Result block(String why){reason=why;return Result.BLOCKED;}
    private boolean settled(MenuSnapshot m,long now) {
        if(m.containerId()!=stableContainer || !m.slots().equals(contents)) {stableContainer=m.containerId();contents=m.slots();stableAt=now;return false;}
        return now-stableAt>=1500;
    }
    private boolean matches(SlotView slot,Book book,int atLevel) {
        return !slot.empty()&&slot.enchantedBook()&&slot.count()==1&&slot.enchantments()!=null
                &&slot.enchantments().size()==1&&Objects.equals(slot.enchantments().get(MenuSnapshot.enchantmentKey(book.id())),atLevel);
    }
    private int inventory(MenuSnapshot menu,Book book,int atLevel) {
        return (int)menu.slots().stream().filter(s->s.inPlayerInventory()&&s.containerSlot()>=0&&s.containerSlot()<36&&matches(s,book,atLevel)).count();
    }
    public Result tick(Task task,MenuSnapshot menu,GameActions actions,InventoryMemory memory,
                       String firstPage,String secondPage,String username,long now,Receipts ledger) {
        if(trade==null){trade=task.getProfitTradeId();started=now;}
        if(!trade.equals(task.getProfitTradeId()))return block("Retirement task changed during an outstanding action.");
        if(!reason.isEmpty())return Result.BLOCKED;
        if(now-started>=180_000)return block("Book cleanup timed out; unverified ownership retained.");
        if(menu==null || !menu.cursorEmpty())return Result.WAITING;
        if(menu.slots().stream().filter(s->s.inPlayerInventory()&&s.containerSlot()>=0&&s.containerSlot()<36).count()!=36)return Result.WAITING;
        if(now<nextAction)return Result.WAITING;
        Book book=task.getBook();
        if(phase==Phase.ORDERS || phase==Phase.CANCEL) {
            if(!TradingSafety.ordersTitle(menu.title())) {
                if(menu.title()!=null)actions.closeMenu();else actions.command("managebazaarorders");
                nextAction=now+1000;return Result.WAITING;
            }
            if(!menu.loaded(35)||!settled(menu,now))return Result.WAITING;
            var orders=new ArrayList<SlotView>();
            for(var s:menu.slots())if(!s.inPlayerInventory()&&!s.empty()) {
                String name=Chat.strip(s.hoverName());
                if(name.contains("Next Page")||name.contains("Previous Page"))return block("Paginated orders cannot prove cleanup ownership.");
                for(int l=book.level();l<=book.sellLevel();l++)if(name.equals("BUY "+book.getRomanLevel(l))||name.equals("SELL "+book.getRomanLevel(l))) {
                    if(menu.title().contains("Co-op")) {
                        var owner=OrderLore.creator(s.lore(),username);
                        if(owner==OrderLore.Creator.OTHER)continue;
                        if(owner!=OrderLore.Creator.OWN)return block("Cleanup order owner is unreadable.");
                    }
                    if(name.startsWith("BUY ")&&l!=book.level() || name.startsWith("SELL ")&&l!=book.sellLevel())
                        return block("Cleanup order level differs from the tracked route.");
                    orders.add(s);
                }
            }
            if(!orders.isEmpty()&&task.orphanCleanup())return block("Unassigned leftovers have a live order without a matching tracked flip.");
            if(orders.size()>1)return block("Multiple live orders match the retiring book.");
            if(claiming) {
                int count=inventory(menu,book,book.level());
                if(count<beforeClaim+quantity)return Result.WAITING;
                if(count!=beforeClaim+quantity || task.assignBook(book,book.level(),0,quantity)!=0)
                    return block("Cleanup claim differs from the expected owned quantity.");
                ledger.acquired(task,quantity,claimPrice,UUID.randomUUID().toString());claiming=false;
                if(!ledger.checkpoint())return block("Cleanup claim could not be saved.");
            }
            if(phase==Phase.CANCEL) {
                if(menu.containerId()==cancelContainer||!orders.isEmpty())return Result.WAITING;
                phase=Phase.HOLDINGS;return Result.WAITING;
            }
            if(orders.isEmpty()){phase=Phase.HOLDINGS;return Result.WAITING;}
            var order=orders.getFirst();var fill=OrderLore.fill(order.lore());
            if(fill==null)return block("Cleanup order fill is unreadable.");
            boolean selling=Chat.strip(order.hoverName()).startsWith("SELL ");
            if(fill.total()>(selling?1:book.getQtyAmount(book.level())))return block("Cleanup order quantity exceeds the tracked route.");
            if(selling&&fill.filled()>0)return Result.COLLECT_SALE;
            if(!selling) {
                quantity=OrderLore.claimable(order.lore(),inventory(menu,book,book.level()));
                if(quantity>0) {
                    Double price=TradeReceipts.unitPrice(order.lore());
                    if(price==null||quantity>task.getAmountToOrder()||quantity>menu.emptyInventorySlots())
                        return block("Cleanup buy claim cannot fit or its cost is unreadable.");
                    beforeClaim=inventory(menu,book,book.level());claimPrice=price;claiming=true;
                }
            }
            if(!ledger.checkpoint())return block("Cleanup order intent could not be saved.");
            actions.click(order.index(),false);nextAction=now+1000;
            if(!claiming)phase=Phase.OPTIONS;
            return Result.WAITING;
        }
        if(phase==Phase.OPTIONS) {
            if(!"Order options".equals(Chat.strip(menu.title()))||!menu.loaded(35)||!settled(menu,now))return Result.WAITING;
            var cancel=menu.namedInContainer("Cancel Order");
            if(cancel.size()!=1)return block("Cleanup cancellation control is missing or ambiguous.");
            if(!ledger.checkpoint())return block("Cleanup cancellation intent could not be saved.");
            cancelContainer=menu.containerId();phase=Phase.CANCEL;
            actions.click(cancel.getFirst(),false);nextAction=now+1000;return Result.WAITING;
        }
        if(phase==Phase.HOLDINGS) {
            var stored=task.bookList.stream().filter(b->b.location!=0).findFirst().orElse(null);
            if(stored!=null) {
                String command=stored.location==1?firstPage:secondPage;
                if(!BookTransfer.pageMatches(menu.title(),command)) {
                    if(menu.title()!=null)actions.closeMenu();else actions.command(command);
                    nextAction=now+1000;return Result.WAITING;
                }
                var result=transfer.tick(stored,0,command,menu,actions,now,memory);
                if(result==BookTransfer.Result.BLOCKED)return block(transfer.failure());
                if(result==BookTransfer.Result.NO_SPACE)return block("No inventory space to retrieve cleanup books.");
                if(result==BookTransfer.Result.MOVED){transfer.reset();if(!ledger.checkpoint())return block("Retrieved cleanup book could not be saved.");}
                return Result.WAITING;
            }
            if(task.bookList.isEmpty())return Result.COMPLETE;
            level=task.bookList.getFirst().level;
            quantity=(int)task.bookList.stream().filter(b->b.level==level).count();
            // Instant Sell sells every book of this product in the inventory, so all must be owned.
            if(inventory(menu,book,level)!=quantity)return block("Cleanup inventory quantity differs from tracked holdings.");
            actions.closeMenu();phase=Phase.SEARCH;nextAction=now+1000;clickedContainer=-1;return Result.WAITING;
        }
        if(phase==Phase.SEARCH) {
            String item=book.getRomanLevel(level),title=Chat.strip(menu.title());
            if(menu.title()==null){actions.command("bz "+book.name().replace("Ultimate","").strip());nextAction=now+1000;return Result.WAITING;}
            if(title.endsWith("➜ "+item)||title.endsWith("→ "+item)) {
                if(!menu.loaded(35)||!settled(menu,now))return Result.WAITING;
                if(menu.slots().size()<=13||!Chat.strip(menu.slots().get(13).hoverName()).equals(item))return block("Instant-sale product identity could not be verified.");
                if(inventory(menu,book,level)!=quantity)return block("Cleanup books changed before instant sale.");
                var sell=menu.namedInContainer("Sell Instantly");
                if(sell.size()!=1||sell.getFirst()!=11)return block("Verified instant-sale control is unavailable.");
                if(!ledger.checkpoint())return block("Instant-sale intent could not be saved.");
                saleProceeds=null;saleEvent=UUID.randomUUID().toString();phase=Phase.SALE;
                actions.click(11,false);nextAction=now+1000;return Result.WAITING;
            }
            if(title.startsWith("Bazaar")&&menu.loaded(53)&&menu.containerId()!=clickedContainer) {
                var levels=menu.namedInContainer(item);
                if(levels.size()==1){clickedContainer=menu.containerId();actions.click(levels.getFirst(),false);nextAction=now+1000;}
            }
            return Result.WAITING;
        }
        if(phase==Phase.SALE) {
            // A sale receipt alone is insufficient; all sold books must also disappear.
            if(saleProceeds==null||inventory(menu,book,level)!=0||!settled(menu,now))return Result.WAITING;
            ledger.sold(task,quantity*book.baseUnits(level),saleProceeds,saleEvent);
            task.bookList.removeIf(b->b.level==level);
            if(!ledger.checkpoint())return block("Instant-sale completion could not be saved.");
            actions.closeMenu();phase=Phase.HOLDINGS;nextAction=now+1000;return Result.WAITING;
        }
        return Result.WAITING;
    }
}
