package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.*;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import java.util.*;

/** Inventory-backed crafting participates in the same foreground menu scheduler as trading. */
public final class CraftingFeature implements Feature {
    private final CraftingExecutor executor=new CraftingExecutor();
    private ProductionRecipe recipe;
    private int remaining;
    private String jobId;
    private ProductionJobs jobs;
    private boolean running,paused,opening;
    private long openedAt,nextCommand;
    public String name(){return "Crafting";}
    public boolean queued(){return recipe!=null;}
    /** The journal id of the most recently queued job, for callers that follow it. */
    public String jobId(){return jobId;}
    public String activity(){return recipe==null?"No craft queued":"Crafting "+recipe.outputId()+" · "+remaining+" batches remaining";}
    public Set<String> lockedProducts(){if(recipe==null)return Set.of();var ids=new HashSet<>(recipe.ingredients().keySet());ids.add(recipe.outputId());return Set.copyOf(ids);}
    private ProductionJobs jobs()throws java.io.IOException {
        if(jobs==null) {
            var storage=com.goofy.goofyaddons.features.account.AccountStorage.INSTANCE;
            if(storage.pinned()==null) {String reason=storage.prepare();if(reason!=null)throw new java.io.IOException(reason);}
            jobs=new ProductionJobs(storage.path(com.goofy.goofyaddons.features.account.AccountStorage.PRODUCTION_JOBS));
            String account=new LiveWorld().username();if(account!=null)jobs.recoverUncertain(account);
        }
        return jobs;
    }
    public boolean queue(String output,int batches) {
        if(recipe!=null || FeatureManager.INSTANCE.auction().queued() || batches<1 || batches>16){new LiveActions().message("Finish the queued craft first; batch count must be 1–16.");return false;}
        var menu=new LiveWorld().menu();
        if(menu==null || !menu.cursorEmpty()){new LiveActions().message("Clear the cursor before queueing crafting.");return false;}
        var counts=new HashMap<String,Integer>();
        for(var slot:menu.slots())if(slot.inPlayerInventory() && slot.containerSlot()<36 && !slot.empty() && slot.customId()!=null)
            counts.merge(slot.customId(),slot.count(),Integer::sum);
        var manager=FeatureManager.INSTANCE;
        var chosen=selectRecipe(RecipeCatalog.instance(),output,batches,counts,CapitalManager.INSTANCE.occupiedProducts(),manager.observedSkills(),manager.observedUnlocks());
        if(chosen.isEmpty()) {
            var held=selectRecipe(RecipeCatalog.instance(),output,batches,counts,CapitalManager.INSTANCE.occupiedProducts());
            String reason=held.map(r->com.goofy.goofyaddons.features.access.RouteRequirements.craft(r.requirement(),manager.observedSkills(),manager.observedUnlocks())).orElse(null);
            new LiveActions().message(reason==null?"No supported recipe with enough unreserved inventory ingredients for "+output+".":
                "Cannot queue craft: "+reason+(manager.accountRequirementsPending()?". Account lookup is in progress; retry once it finishes.":". Check the account lookup or open Your Skills."));return false;
        }
        String requirement=com.goofy.goofyaddons.features.access.RouteRequirements.craft(chosen.get().requirement(),FeatureManager.INSTANCE.observedSkills(),FeatureManager.INSTANCE.observedUnlocks());
        if(requirement!=null){new LiveActions().message("Cannot queue craft: "+requirement+". Check Your Skills and wait for the account lookup.");return false;}
        recipe=chosen.get();remaining=batches;jobId=UUID.randomUUID().toString();executor.reset();opening=false;
        try {
            jobs().put(new ProductionJobs.Job(jobId,recipe.key(),new LiveWorld().username(),ProductionJobs.State.PLANNED,batches,-1,0,0,null,null,null,null));
        }catch(Exception failure){recipe=null;Diagnostics.failure("production.journal_failed",failure);new LiveActions().message("Crafting journal could not be saved; no action performed.");return false;}
        FeatureManager.INSTANCE.invalidateMarketReport();
        new LiveActions().message("Queued "+batches+" craft batches of "+RecipeCatalog.instance().name(output)+". Use the trading toggle to run; existing ingredients only.");return true;
    }
    static Optional<ProductionRecipe> selectRecipe(RecipeCatalog catalog,String output,int batches,Map<String,Integer> inventory,Set<String> occupied){
        if(batches<1 || batches>16)return Optional.empty();
        return catalog.forOutput(output).stream().filter(r->r.kind()==ProductionRecipe.Kind.CRAFT)
            .filter(r->!occupied.contains(r.outputId()) && r.ingredients().keySet().stream().noneMatch(occupied::contains))
            .filter(r->r.ingredients().entrySet().stream().allMatch(e->inventory.getOrDefault(e.getKey(),0)>=(long)e.getValue()*batches)).findFirst();
    }
    static Optional<ProductionRecipe> selectRecipe(RecipeCatalog catalog,String output,int batches,Map<String,Integer> inventory,Set<String> occupied,Map<String,Integer> skills,Map<String,Integer> unlocks){
        var eligible=new RecipeCatalog(catalog.forOutput(output).stream()
            .filter(r->com.goofy.goofyaddons.features.access.RouteRequirements.craft(r.requirement(),skills,unlocks)==null).toList(),Map.of());
        return selectRecipe(eligible,output,batches,inventory,occupied);
    }
    public ProductionJobs productionJobs()throws java.io.IOException{return jobs();}
    public List<ProductionJobs.Job> journal()throws java.io.IOException{return jobs().all();}
    public void start(){running=true;paused=false;}
    public void pause(){paused=true;markInterrupted();}
    public void stop(){running=false;paused=false;markInterrupted();}
    private void markInterrupted(){
        if(recipe==null)return;
        try {var old=jobs().find(jobId).orElseThrow();jobs().put(old.withState(old.state()==ProductionJobs.State.PLANNED?ProductionJobs.State.CANCELLED:ProductionJobs.State.REVIEW,"Crafting interrupted; inspect inventory, grid and cursor before requeueing"));}
        catch(Exception failure){Diagnostics.failure("production.journal_failed",failure);}
        recipe=null;executor.reset();opening=false;
    }
    public void resume(){paused=false;}
    public boolean isRunning(){return running && !paused;}
    public boolean needsMenu(){return isRunning() && recipe!=null;}
    public boolean canYield(){return recipe==null;}
    public void slowdownNotice(String message) {
        if(isRunning() && executor.busy() && com.goofy.goofyaddons.features.transaction.ActionRetry.slowdownMessage(message)) {
            executor.slowdown(System.currentTimeMillis());Diagnostics.event("WARN","production.craft_slowdown",Map.of("job",jobId));
        }
    }
    public void onTick() {
        if(!needsMenu())return;
        // The executor paces observed grid mutations; do not add the trader's delay on top.
        var world=new LiveWorld();var actions=new LiveActions();var menu=world.menu();long now=world.now();
        try {
            if(!opening) {
                String requirement=com.goofy.goofyaddons.features.access.RouteRequirements.craft(recipe.requirement(),FeatureManager.INSTANCE.observedSkills(),FeatureManager.INSTANCE.observedUnlocks());
                if(requirement!=null && FeatureManager.INSTANCE.accountRequirementsPending())return;
                if(requirement!=null){fail("Crafting requirement: "+requirement);return;}
                if(menu==null || !menu.cursorEmpty()){fail("Crafting cursor is occupied");return;}
                if(menu.title()!=null && !"Craft Item".equals(Chat.strip(menu.title()))){fail("Close the unrelated menu before starting crafting");return;}
                jobs().put(jobs().find(jobId).orElseThrow().withState(ProductionJobs.State.PROCESSING,null));
                opening=true;openedAt=now;nextCommand=0;
            }
            if(menu==null || menu.title()==null) {
                if(now-openedAt>25000){fail("Craft Item menu did not open");return;}
                if(now>=nextCommand){nextCommand=now+8000;actions.command("craft");}return;
            }
            if(!"Craft Item".equals(Chat.strip(menu.title()))){fail("Crafting menu was replaced; inventory retained");return;}
            // Hypixel answers a click only when it disagrees with the client's prediction, so the
            // client's menu is the state to act on; a rejected click reverts it, and the executor
            // re-reads it after a settle delay before every next click.
            var result=executor.tick(recipe,menu,actions,FeatureManager.INSTANCE.observedSkills(),FeatureManager.INSTANCE.observedUnlocks(),now);
            if(result==CraftingExecutor.Result.BLOCKED){fail(executor.failure());return;}
            if(result==CraftingExecutor.Result.CRAFTED) {
                jobs().put(jobs().find(jobId).orElseThrow().completedBatch());
                remaining--;
                Diagnostics.event("INFO","production.craft_verified",Map.of("job",jobId,"recipe",recipe.key(),"output",recipe.outputId(),"units",recipe.outputCount(),"remaining",remaining));
                if(remaining==0) {
                    FeatureManager.INSTANCE.invalidateMarketReport();
                    actions.closeMenu();actions.message("Crafted "+RecipeCatalog.instance().name(recipe.outputId())+"; output verified in inventory.");recipe=null;opening=false;
                }
            }
        } catch(Exception failure){Diagnostics.failure("production.craft_failed",failure);fail("Crafting execution/journal failed; reconcile inventory before restarting");}
    }
    private void fail(String reason){FeatureManager.INSTANCE.safetyPause(reason);}
}
