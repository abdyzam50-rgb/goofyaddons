package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;

/** BIN-only GUI browsing; a delayed confirmation never authorizes another purchase. */
public final class BinPurchaseExecutor {
    public enum Result {WAITING,PURCHASED,BLOCKED}
    private enum Step {OPEN,VIEW,CONFIRM,VERIFY,DONE}
    private final ProductionMenus.BinListing listing;
    private final ProductionJobs journal;
    private final String jobId;
    private final AuctionBrowserNavigation browser;
    private final AuctionPricing.Quote quote;
    private Step step=Step.OPEN;
    private long started,clickedAt;
    private Double purseBefore;
    private String failure;
    public BinPurchaseExecutor(ProductionMenus.BinListing listing,ProductionJobs journal,String jobId){this(listing,journal,jobId,null);}
    public BinPurchaseExecutor(ProductionMenus.BinListing listing,ProductionJobs journal,String jobId,AuctionPricing.Quote quote){
        this.listing=listing;this.journal=journal;this.jobId=jobId;this.quote=quote;
        String name=RecipeCatalog.instance().name(listing.productId());
        if(listing.productId().contains(";"))name=listing.identity().petType().replace('_',' ');
        browser=new AuctionBrowserNavigation(name,s->listing.productId().equals(ProductionMenus.productId(s))
            && s.count()==listing.count() && listing.identity().uuid()!=null && listing.identity().uuid().equals(s.metadata().uuid()));
    }
    public String failure(){return failure;}
    private Result block(String reason){failure=reason;return Result.BLOCKED;}
    public Result tick(MenuSnapshot menu,boolean signOpen,GameActions actions,String account,double purse,double maximum,double spendable,long now) {
        try {
            var job=journal.find(jobId).orElseThrow();
            if(!job.account().equals(account) || !listing.auctionUuid().equals(job.auctionUuid()))return block("BIN job account/auction differs");
            if(step==Step.DONE)return Result.PURCHASED;
            if(step==Step.OPEN && job.state()!=ProductionJobs.State.PLANNED)return block("Saved BIN purchase requires reconciliation; no replay");
            if(started==0)started=now;
            if(now-started>120000)return review(job,"BIN navigation/purchase timed out; verify live inventory and auction");
            AuctionPricing.validateObserved(quote,listing.productId(),listing.price(),now);
            if(menu==null || !menu.cursorEmpty() || signOpen && step!=Step.OPEN)return block("BIN purchase requires a readable menu and empty cursor");
            if(step==Step.CONFIRM || step==Step.VERIFY) {
                var acquired=menu.slots().stream().filter(s->s.inPlayerInventory() && !s.empty() && s.count()==listing.count()
                        && listing.identity().uuid()!=null && listing.identity().uuid().equals(s.metadata().uuid())
                        && (listing.productId().contains(";")?listing.identity().samePet(s.metadata()):listing.productId().equals(s.customId()))).toList();
                if(acquired.size()==1 && purseBefore!=null && Double.isFinite(purse) && Math.abs(purseBefore-purse-listing.price())<=0.51) {
                    var recorded=new ProductionJobs.Job(job.id(),job.recipeKey(),job.account(),ProductionJobs.State.OUTPUT_READY,1,-1,
                            clickedAt,0,listing.price(),listing.productId().contains(";")?listing.identity().uuid():null,listing.auctionUuid(),
                            "BIN item and exact purse debit verified; no sale/profit assumed",1);
                    journal.put(recorded);step=Step.DONE;return Result.PURCHASED;
                }
            }
            String title=Chat.strip(menu.title());
            switch(step) {
                case OPEN -> {
                    var result=browser.tick(menu,signOpen,actions,now);
                    if(result==AuctionBrowserNavigation.Result.BLOCKED)return block(browser.failure());
                    if(result!=AuctionBrowserNavigation.Result.READY)return Result.WAITING;
                    if(Double.compare(browser.price(),listing.price())!=0)return block("Observed BIN price changed; replan before buying");
                    actions.click(browser.selected().index(),false);step=Step.VIEW;clickedAt=now;return Result.WAITING;
                }
                case VIEW -> {
                    if(!"BIN Auction View".equals(title)){if(now-clickedAt>10000)return block("Selected BIN did not open");return Result.WAITING;}
                    if(!ProductionMenus.binPurchase(menu,listing,now,maximum,spendable))return block("BIN item, price, expiry or spending limit is not verified");
                    if(menu.slots().stream().anyMatch(s->s.inPlayerInventory() && !s.empty() && listing.identity().uuid()!=null && listing.identity().uuid().equals(s.metadata().uuid())))return block("BIN target UUID already exists in inventory");
                    var buy=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory() && Set.of("Buy Item","Buy it now").contains(Chat.strip(s.hoverName()))).toList();
                    if(buy.size()!=1)return block("BIN purchase control is ambiguous");
                    if(!Double.isFinite(purse) || purse<listing.price())return block("Purse cannot fund this BIN");
                    purseBefore=purse;clickedAt=now;
                    journal.put(job.withState(ProductionJobs.State.BUYING,"BIN purchase intent saved before purchase control"));
                    actions.click(buy.getFirst().index(),false);step=Step.CONFIRM;return Result.WAITING;
                }
                case CONFIRM -> {
                    if(title.equals("Confirm Purchase")) {
                        if(!ProductionMenus.binPurchase(menu,listing,now,maximum,spendable))return review(job,"BIN confirmation changed; no confirmation clicked");
                        var confirm=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory() && Set.of("Confirm","Confirm Purchase").contains(Chat.strip(s.hoverName()))).toList();
                        if(confirm.size()!=1)return review(job,"BIN confirm control is ambiguous");
                        journal.put(job.withState(ProductionJobs.State.BUYING,"Final BIN confirmation intent saved"));
                        actions.click(confirm.getFirst().index(),false);clickedAt=now;step=Step.VERIFY;return Result.WAITING;
                    }
                    if(now-clickedAt>10000)return review(job,"BIN purchase/confirmation not acknowledged; no second purchase");
                }
                case VERIFY -> {if(now-clickedAt>15000)return review(job,"BIN item arrival or exact debit not acknowledged");}
                default -> {}
            }
            return Result.WAITING;
        }catch(Exception failed){return block("BIN journal/evidence failed; purchase not replayed");}
    }
    private Result review(ProductionJobs.Job job,String reason)throws java.io.IOException {journal.put(job.withState(ProductionJobs.State.REVIEW,reason));return block(reason);}
}
