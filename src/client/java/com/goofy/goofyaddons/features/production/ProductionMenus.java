package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;
import java.util.regex.*;

/** Observed GUI facts for jobs and BIN-only transaction gates. No click is authorized by a title alone. */
public final class ProductionMenus {
    private ProductionMenus() {}
    public record ForgeSlot(int slot,String productId,String name,long remainingSeconds,boolean complete) {}
    private static final Pattern TIME=Pattern.compile("^Time Remaining: (Completed!|(?:(\\d+)d ?)?(?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?)$");
    private static final Pattern COINS=Pattern.compile("(?i)^(?:Item price|Price|BIN Price|Buy it now|Cost|Total cost):\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins[!.]?$",Pattern.MULTILINE);
    public static String productId(SlotView slot){return slot==null?null:"PET".equals(slot.customId())?slot.metadata().petVariant():slot.customId();}
    public static List<ForgeSlot> forgeSlots(MenuSnapshot menu) {
        if(menu==null || !"The Forge".equals(Chat.strip(menu.title())))return List.of();
        var result=new ArrayList<ForgeSlot>();
        for(var slot:menu.slots()) {
            if(slot.inPlayerInventory() || slot.index()<10 || slot.index()>16 || slot.empty())continue;
            for(String raw:slot.lore().split("\\R")) {
                var m=TIME.matcher(Chat.strip(raw).trim());if(!m.matches())continue;
                boolean complete=m.group(1).equals("Completed!");long seconds=0;
                long[] multipliers={86400,3600,60,1};
                try {for(int i=0;i<4;i++)if(m.group(i+2)!=null)seconds=Math.addExact(seconds,Math.multiplyExact(Long.parseLong(m.group(i+2)),multipliers[i]));}
                catch(ArithmeticException invalid){continue;}
                if(!complete && (seconds<=0 || seconds>31536000))continue;
                result.add(new ForgeSlot(slot.index()-10,productId(slot),Chat.strip(slot.hoverName()),seconds,complete));break;
            }
        }
        return List.copyOf(result);
    }
    public static ForgeSlot katTimer(SlotView control) {
        if(control==null)return null;
        // Reuse the strict server timer grammar without treating a clock estimate as claim proof.
        var fake=new MenuSnapshot(1,"The Forge",true,List.of(new SlotView(10,false,10,false,
            control.customName(),control.hoverName(),control.loreLines(),control.customId(),null,1,1)));
        return forgeSlots(fake).stream().findFirst().orElse(null);
    }
    public static Double exactCoins(String text) {
        var m=COINS.matcher(Chat.strip(text));Double found=null;
        while(m.find()) {
            try {double value=Double.parseDouble(m.group(1).replace(",",""));
                if(!Double.isFinite(value) || value<=0 || value>1e13 || found!=null && Double.compare(found,value)!=0)return null;
                found=value;
            }catch(NumberFormatException invalid){return null;}
        }
        return found;
    }
    public static boolean forgeConfirmation(MenuSnapshot menu,ProductionRecipe recipe) {
        if(menu==null || !menu.cursorEmpty() || !"Confirm Process".equals(Chat.strip(menu.title())) || recipe.kind()!=ProductionRecipe.Kind.FORGE)return false;
        var inputs=new HashMap<String,Integer>();int outputs=0;
        for(var slot:menu.slots()) {
            if(slot.inPlayerInventory() || slot.empty() || productId(slot)==null)continue;
            int column=slot.index()%9;
            if(column<4)inputs.merge(productId(slot),slot.count(),Integer::sum);
            else if(recipe.outputId().equals(productId(slot)) && slot.count()==recipe.outputCount())outputs++;
            else if(column>=4 && slot.customId()!=null && !slot.customId().isBlank())return false;
        }
        return outputs==1 && inputs.equals(recipe.ingredients());
    }
    public record BinListing(String auctionUuid,String productId,ItemMetadata identity,int count,double price,long observedAt,long endsAt) {
        public BinListing {
            if(auctionUuid==null || !auctionUuid.matches("[a-fA-F0-9]{32}|[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") || !ProductionRecipe.validId(productId)
                    || identity==null || count<1 || count>64 || !Double.isFinite(price) || price<=0 || price>1e13 || observedAt<=0 || endsAt<=observedAt)
                throw new IllegalArgumentException("Invalid BIN listing");
            if(productId.contains(";") && (!productId.equals(identity.petVariant()) || identity.uuid()==null))throw new IllegalArgumentException("BIN pet identity missing");
        }
    }
    public static boolean binPurchase(MenuSnapshot menu,BinListing listing,long now,double maximum,double spendable) {
        if(menu==null || !menu.cursorEmpty() || !Set.of("BIN Auction View","Confirm Purchase").contains(Chat.strip(menu.title()))
                || now-listing.observedAt()>30000 || listing.observedAt()>now+5000 || listing.endsAt()<=now
                || listing.price()>maximum || listing.price()>spendable)return false;
        var items=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty() && matchesListing(s,listing)).toList();
        if(items.size()!=1)return false;
        var prices=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty())
                .map(s->exactCoins(s.hoverName()+"\n"+s.lore())).filter(Objects::nonNull).distinct().toList();
        return prices.size()==1 && Math.abs(prices.getFirst()-listing.price())<0.01;
    }
    private static boolean matchesListing(SlotView item,BinListing listing) {
        if(item.count()!=listing.count())return false;
        if(listing.productId().contains(";"))return "PET".equals(item.customId()) && listing.identity().samePet(item.metadata());
        // Unique AH equipment must carry a known UUID. Unidentified lookalikes are not purchasable.
        return listing.productId().equals(item.customId()) && listing.identity().uuid()!=null && listing.identity().uuid().equals(item.metadata().uuid())
                && Objects.equals(listing.identity(),item.metadata());
    }
    public static boolean binCreation(MenuSnapshot menu,SlotView expected,double price) {
        if(menu==null || !menu.cursorEmpty() || !"Create BIN Auction".equals(Chat.strip(menu.title())) || expected==null || expected.empty()
                || !Double.isFinite(price) || price<=0)return false;
        var actual=menu.slot(13);var control=menu.slot(31);
        if(actual==null || control==null || actual.empty() || actual.inPlayerInventory() || control.inPlayerInventory())return false;
        Double observed=exactCoins(control.hoverName());
        return observed!=null && Math.abs(observed-price)<0.01 && actual.count()==expected.count()
                && Objects.equals(actual.customId(),expected.customId()) && Objects.equals(actual.metadata(),expected.metadata())
                && Objects.equals(actual.enchantments(),expected.enchantments());
    }

    /** Exact quote labels only: an unrelated price must not be mistaken for a listing fee. */
    public static Double listingFee(String text) {
        var pattern=Pattern.compile("(?im)^(?:Creation fee|Listing fee|Auction creation fee|Cost):\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins[!.]?$" );
        var matches=pattern.matcher(Chat.strip(text));Double found=null;
        while(matches.find()) {
            double value;
            try{value=Double.parseDouble(matches.group(1).replace(",",""));}catch(NumberFormatException invalid){return null;}
            if(!Double.isFinite(value) || value<0 || value>1e12 || found!=null && Math.abs(found-value)>0.01)return null;
            found=value;
        }
        return found;
    }
    /** The final BIN screen is separate from the editable form and from bidding confirmations. */
    public static boolean binPublication(MenuSnapshot menu,SlotView expected,long price) {
        if(menu==null || !menu.cursorEmpty() || !"Confirm BIN Auction".equals(Chat.strip(menu.title())))return false;
        var items=menu.slots().stream().filter(s->!s.inPlayerInventory() && BinListingExecutor.sameIdentity(expected,s) && s.count()==expected.count()).toList();
        if(items.size()!=1)return false;
        // Fee controls may also expose a Cost label. Read sale price from the item or the confirmation control,
        // requiring an explicit sale-price label rather than a standalone fee.
        var sale=Pattern.compile("(?im)^(?:Item price|Price|BIN Price|Buy it now):\\s*([0-9]+(?:,[0-9]{3})*) coins[!.]?$" );
        Set<Long> prices=new HashSet<>();
        for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty()) {
            var m=sale.matcher(Chat.strip(slot.hoverName()+"\n"+slot.lore()));
            while(m.find())try{prices.add(Long.parseLong(m.group(1).replace(",","")));}catch(NumberFormatException invalid){return false;}
        }
        return prices.size()==1 && prices.contains(price);
    }
    public static Long auctionDuration(String text) {
        var matcher=Pattern.compile("(?im)^Duration: ([0-9]+) (hours?|days?)[!.]?$").matcher(Chat.strip(text));Long found=null;
        while(matcher.find()) {
            try {long seconds=Math.multiplyExact(Long.parseLong(matcher.group(1)),matcher.group(2).toLowerCase(Locale.ROOT).startsWith("day")?86400:3600);
                if(seconds<3600 || seconds>336*3600L || found!=null && found!=seconds)return null;found=seconds;
            }catch(ArithmeticException | NumberFormatException invalid){return null;}
        }
        return found;
    }
}
