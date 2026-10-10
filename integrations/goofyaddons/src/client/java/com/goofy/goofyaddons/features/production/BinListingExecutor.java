package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;

/** Prepares one exact BIN listing. Publication requires a separately verified GUI contract. */
public final class BinListingExecutor {
    public enum Result {WAITING,PREPARED,LISTED,BLOCKED}
    private enum Step {OPEN,NAVIGATE,SWITCH,INSERT,PRICE,SIGN,VERIFY,CONFIRM,RECEIPT,DONE}
    private final SlotView expected;
    private final long price;
    private final ProductionJobs journal;
    private final String jobId;
    private final boolean publish;
    private final double maximumFee;
    private final NavigationRetry navigation=new NavigationRetry();
    private Step step=Step.OPEN;
    private long started,nextCommand,actionAt;
    private int opens;
    private boolean inserted,signWritten;
    private String failure;
    private Double purseBefore,quotedFee;
    private int oldListingCount=-1;
    private MenuSnapshot verifiedForm;
    public BinListingExecutor(SlotView expected,long price,ProductionJobs journal,String jobId) {
        this(expected,price,journal,jobId,false,0);
    }
    public BinListingExecutor(SlotView expected,long price,ProductionJobs journal,String jobId,boolean publish,double maximumFee) {
        if(expected==null || expected.empty() || expected.customId()==null || expected.customId().isBlank()
                || !expected.inPlayerInventory() || price<1 || price>1_000_000_000_000L
                || !Double.isFinite(maximumFee) || maximumFee<0 || maximumFee>1e12)
            throw new IllegalArgumentException("Readable inventory item and positive integral BIN price required");
        this.expected=expected;this.price=price;this.journal=journal;this.jobId=jobId;this.publish=publish;this.maximumFee=maximumFee;
    }
    public String failure(){return failure;}
    public Result tick(MenuSnapshot menu,boolean signOpen,GameActions actions,String account,long now) {
        return tick(menu,signOpen,actions,account,Double.NaN,0,now);
    }
    public Result tick(MenuSnapshot menu,boolean signOpen,GameActions actions,String account,double purse,double spendable,long now) {
        try {
            var job=journal.find(jobId).orElseThrow();
            if(!job.account().equals(account))return block("Auction account changed");
            if(step==Step.DONE)return publish?Result.LISTED:Result.PREPARED;
            if(step==Step.OPEN && job.state()!=ProductionJobs.State.OUTPUT_READY)
                return block("Saved auction operation requires reconciliation; no replay");
            if(started==0)started=now;
            if(now-started>=90000)return review(job,"BIN preparation timed out; inspect the item and sell slot");
            if(menu==null)return Result.WAITING;
            // Expected sign submission moves no items. Hypixel can leave a transient
            // carried control while its sign editor is open; verify cursor/item on return.
            if(step==Step.SIGN&&signOpen) {
                if(signWritten)return Result.WAITING;
                navigation.reset();
                if(!actions.writeSign(Long.toString(price)))return review(job,"BIN price sign could not be written");
                signWritten=true;actionAt=now;return Result.WAITING;
            }
            if(!menu.cursorEmpty())return review(job,"Auction cursor is occupied; item movement stopped");
            if(navigation.pending()) {
                var result=navigation.observe(menu,signOpen,actions,now);
                if(result==NavigationRetry.Result.EXHAUSTED)return review(job,"Auction navigation was not acknowledged");
                if(result!=NavigationRetry.Result.READY)return Result.WAITING;
            }
            String title=Chat.strip(menu.title());
            if(step==Step.SIGN) {
                if(signWritten && "Create BIN Auction".equals(title)){step=Step.VERIFY;return Result.WAITING;}
                if(now-actionAt>=10000)return review(job,"BIN price sign/return was not acknowledged");
                return Result.WAITING;
            }
            if(signOpen)return review(job,"Unexpected sign during auction navigation");
            switch(step) {
                case OPEN -> {
                    if(menu.title()==null) {
                        if(now>=nextCommand){if(opens++>=3)return block("Auction House did not open");nextCommand=now+8000;actions.command("ah");}
                        return Result.WAITING;
                    }
                    if(!ProductionMenus.auctionHouse(title)&&!Set.of("Manage Auctions","Create Auction","Create BIN Auction").contains(title))
                        return block("Close the unrelated menu before auction preparation");
                    step=Step.NAVIGATE;return Result.WAITING;
                }
                case NAVIGATE -> {
                    if(ProductionMenus.auctionHouse(title))return navigate(menu,actions,now,Set.of("Manage Auctions","Create Auction","Create BIN Auction"));
                    if("Manage Auctions".equals(title)) {
                        if(oldListingCount<0)oldListingCount=listingCount(menu);
                        return navigate(menu,actions,now,Set.of("Create Auction","Create BIN Auction"));
                    }
                    if("Create Auction".equals(title)){step=Step.SWITCH;return Result.WAITING;}
                    if("Create BIN Auction".equals(title)){if(oldListingCount<0)oldListingCount=0;step=Step.INSERT;return Result.WAITING;}
                    return review(job,"Unexpected auction navigation screen: "+title);
                }
                case SWITCH -> {
                    if("Create BIN Auction".equals(title)){if(oldListingCount<0)oldListingCount=0;step=Step.INSERT;return Result.WAITING;}
                    if(!"Create Auction".equals(title))return review(job,"Auction mode switch changed screens unexpectedly");
                    return navigate(menu,actions,now,Set.of("Switch to BIN"));
                }
                case INSERT -> {
                    if(!"Create BIN Auction".equals(title))return review(job,"BIN creation menu was replaced");
                    var sell=menu.slot(13);
                    if(inserted) {
                        if(matches(sell) && ownedCount(menu)==0){step=Step.PRICE;return Result.WAITING;}
                        if(now-actionAt>=10000)return review(job,"Exact item transfer to the BIN sell slot was not acknowledged; no duplicate transfer");
                        return Result.WAITING;
                    }
                    // Some GUIs use a button as the empty-slot placeholder. Never adopt a real item already there.
                    if(sell==null || sell.inPlayerInventory() || !sell.empty() && sell.customId()!=null && !sell.customId().isBlank())
                        return review(job,"BIN sell slot already contains an item; no ownership assumed");
                    var sources=menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && matches(s)).toList();
                    if(sources.size()!=1 || ownedCount(menu)!=expected.count())return review(job,"Auction source item moved or is ambiguous");
                    journal.put(job.withState(ProductionJobs.State.LISTING,"BIN preparation intent; publication has not been performed"));
                    inserted=true;actionAt=now;actions.click(sources.getFirst().index(),true);return Result.WAITING;
                }
                case PRICE -> {
                    if(!"Create BIN Auction".equals(title) || !matches(menu.slot(13)) || ownedCount(menu)!=0)
                        return review(job,"BIN sell item no longer matches the owned item");
                    if(ProductionMenus.binCreation(menu,expected,price)){step=Step.VERIFY;return Result.WAITING;}
                    var control=menu.slot(31);
                    if(control==null || control.empty() || control.inPlayerInventory()
                            || !Chat.strip(control.hoverName()).startsWith("Item price:"))return review(job,"BIN price control is unverified");
                    actionAt=now;step=Step.SIGN;
                    // This is a reversible navigation click; retry only while the same price control remains present.
                    actions.click(control.index(),false);navigation.sent(menu,control.index(),now);return Result.WAITING;
                }
                case VERIFY -> {
                    if(!"Create BIN Auction".equals(title) || !matches(menu.slot(13)) || ownedCount(menu)!=0)
                        return review(job,"BIN item changed while verifying the entered price");
                    if(!ProductionMenus.binCreation(menu,expected,price)) {
                        if(now-actionAt>=10000)return review(job,"Server did not confirm the exact BIN price; no listing submitted");
                        return Result.WAITING;
                    }
                    if(!publish) {
                        journal.put(job.withState(ProductionJobs.State.REVIEW,
                            "BIN item and price prepared; verify duration, fee and publication manually. No auction submitted"));
                        step=Step.DONE;return Result.PREPARED;
                    }
                    var create=menu.slot(29);
                    var duration=menu.slot(33);
                    if(create==null || create.inPlayerInventory() || create.empty()
                            || !Set.of("Create BIN Auction","Create Auction").contains(Chat.strip(create.hoverName())))
                        return review(job,"BIN creation control is not verified");
                    if(duration==null || duration.inPlayerInventory() || ProductionMenus.auctionDuration(duration.hoverName()+"\n"+duration.lore())==null)
                        return review(job,"Auction duration is unverified; no create action performed");
                    journal.put(job.withState(ProductionJobs.State.LISTING,"Opening final BIN confirmation; never repeat the create action"));
                    verifiedForm=menu;
                    actionAt=now;step=Step.CONFIRM;actions.click(create.index(),false);return Result.WAITING;
                }
                case CONFIRM -> {
                    if("Create BIN Auction".equals(title)) {
                        if(now-actionAt>=10000)return review(job,"BIN creation was not acknowledged; no duplicate create action");
                        return Result.WAITING;
                    }
                    if(!ProductionMenus.binPublication(menu,expected,price)&&!ProductionMenus.compactBinPublication(menu,expected,price,verifiedForm))
                        return review(job,"Final BIN confirmation item/price/title is unverified: "+title);
                    var controls=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty()
                        && Set.of("Confirm","Confirm BIN Auction").contains(Chat.strip(s.hoverName()))).toList();
                    if(controls.size()!=1)return review(job,"BIN confirmation control is ambiguous");
                    Double controlFee=ProductionMenus.listingFee(controls.getFirst().hoverName()+"\n"+controls.getFirst().lore());
                    if(controlFee==null)return review(job,"Confirmation button has no exact, unambiguous listing fee");
                    var fees=new HashSet<Double>();
                    for(var slot:menu.slots())if(!slot.inPlayerInventory() && !slot.empty()) {
                        Double fee=ProductionMenus.listingFee(slot.hoverName()+"\n"+slot.lore());if(fee!=null)fees.add(fee);
                    }
                    if(fees.size()!=1 || !fees.contains(controlFee))return review(job,"Listing fee is not an exact, unambiguous quote");
                    quotedFee=fees.iterator().next();
                    if(!Double.isFinite(purse) || purse<quotedFee || !Double.isFinite(spendable) || quotedFee>spendable || quotedFee>maximumFee)
                        return review(job,"BIN listing fee exceeds the spending limit or purse");
                    journal.put(job.withState(ProductionJobs.State.LISTING,"Final BIN publication intent; quoted fee "+quotedFee+" coins"));
                    purseBefore=purse;actionAt=now;step=Step.RECEIPT;actions.click(controls.getFirst().index(),false);return Result.WAITING;
                }
                case RECEIPT -> {
                    // A button acknowledgement is insufficient. Require a new matching seller listing,
                    // the item absent from inventory, and the exact fee debit. Never publish twice.
                    if(("Manage Auctions".equals(title) && listingCount(menu)==oldListingCount+1 || ownListingView(menu)) && ownedCount(menu)==0
                            && purseBefore!=null && Double.isFinite(purse) && Math.abs(purseBefore-purse-quotedFee)<=0.51) {
                        journal.put(new ProductionJobs.Job(job.id(),job.recipeKey(),job.account(),ProductionJobs.State.SELLING,
                            job.batches(),job.workstationSlot(),now,job.readyAt(),job.costBasis()==null?null:job.costBasis()+quotedFee,
                            job.petUuid(),job.auctionUuid(),"Exact new BIN seller listing and fee debit verified; sale proceeds/profit not assumed",job.completedBatches()));
                        step=Step.DONE;return Result.LISTED;
                    }
                    if(now-actionAt>=15000)return review(job,"BIN publication receipt/fee debit is unverified; inspect Manage Auctions before restarting");
                }
                default -> {}
            }
            return Result.WAITING;
        } catch(Exception failed){return block("Auction journal/evidence failed; operation not replayed");}
    }
    private Result navigate(MenuSnapshot menu,GameActions actions,long now,Set<String> names) {
        var targets=menu.slots().stream().filter(s->!s.inPlayerInventory() && !s.empty() && names.contains(Chat.strip(s.hoverName()))).toList();
        if(targets.size()!=1)return block("Auction navigation control is missing or ambiguous");
        actions.click(targets.getFirst().index(),false);navigation.sent(menu,targets.getFirst().index(),now);return Result.WAITING;
    }
    private boolean matches(SlotView item){return sameIdentity(expected,item) && item.count()==expected.count();}
    static boolean sameIdentity(SlotView expected,SlotView actual) {
        return actual!=null && !actual.empty() && Objects.equals(expected.customId(),actual.customId())
            && Objects.equals(expected.metadata(),actual.metadata()) && Objects.equals(expected.enchantments(),actual.enchantments());
    }
    private int ownedCount(MenuSnapshot menu){return menu.slots().stream().filter(s->s.inPlayerInventory() && s.containerSlot()<36 && sameIdentity(expected,s)).mapToInt(SlotView::count).sum();}
    private boolean ownListingView(MenuSnapshot menu) {
        var item=menu.slot(13);
        return "BIN Auction View".equals(Chat.strip(menu.title()))&&matches(item)&&item.hasLoreLine("This is your own auction!")
            &&Objects.equals(AuctionBrowserNavigation.binPrice(item),(double)price);
    }
    private int listingCount(MenuSnapshot menu){return (int)menu.slots().stream().filter(s->!s.inPlayerInventory() && matches(s)
        && Objects.equals(ProductionMenus.exactCoins(s.lore()),(double)price)).count();}
    private Result block(String reason){failure=reason;return Result.BLOCKED;}
    private Result review(ProductionJobs.Job job,String reason)throws java.io.IOException {journal.put(job.withState(ProductionJobs.State.REVIEW,reason));return block(reason);}
}
