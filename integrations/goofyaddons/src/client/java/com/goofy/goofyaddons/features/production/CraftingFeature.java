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
    private long openedAt,nextCommand,localMismatchSince;
    public String name(){return "Crafting";}
    public boolean queued(){return recipe!=null;}
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
        var chosen=selectRecipe(RecipeCatalog.instance(),output,batches,counts,CapitalManager.INSTANCE.occupiedProducts());
        if(chosen.isEmpty()){new LiveActions().message("No supported recipe with enough unreserved inventory ingredients for "+output+".");return false;}
        String requirement=com.goofy.goofyaddons.features.access.RouteRequirements.craft(chosen.get().requirement(),FeatureManager.INSTANCE.observedSkills(),FeatureManager.INSTANCE.observedUnlocks());
        if(requirement!=null){new LiveActions().message("Cannot queue craft: "+requirement+". Check Your Skills and wait for the account lookup.");return false;}
        recipe=chosen.get();remaining=batches;jobId=UUID.randomUUID().toString();executor.reset();opening=false;localMismatchSince=0;
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
    public ProductionJobs productionJobs()throws java.io.IOException{return jobs();}
    public List<ProductionJobs.Job> journal()throws java.io.IOException{return jobs().all();}
    public void start(){running=true;paused=false;}
    public void pause(){paused=true;markInterrupted();}
    public void stop(){running=false;paused=false;markInterrupted();}
    private void markInterrupted(){
        if(recipe==null)return;
        try {var old=jobs().find(jobId).orElseThrow();jobs().put(old.withState(old.state()==ProductionJobs.State.PLANNED?ProductionJobs.State.CANCELLED:ProductionJobs.State.REVIEW,"Crafting interrupted; inspect inventory, grid and cursor before requeueing"));}
        catch(Exception failure){Diagnostics.failure("production.journal_failed",failure);}
        recipe=null;executor.reset();opening=false;localMismatchSince=0;
    }
    public void resume(){paused=false;}
    public boolean isRunning(){return running && !paused;}
    public boolean needsMenu(){return isRunning() && recipe!=null;}
    public boolean canYield(){return recipe==null;}
    public void onTick() {
        if(!needsMenu())return;
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
            var confirmed=com.goofy.goofyaddons.menu.ServerMenuMirror.read();
            if(confirmed==null){if(now-openedAt>25000)fail("Crafting server inventory snapshot did not arrive");return;}
            if(!CraftingExecutor.sameOwnedState(confirmed,menu)) {
                if(localMismatchSince==0)localMismatchSince=now;
                if(now-localMismatchSince>8000)fail("Local/server crafting inventory differs; awaiting authoritative updates");
                return;
            }
            localMismatchSince=0;
            var result=executor.tick(recipe,confirmed,actions,FeatureManager.INSTANCE.observedSkills(),FeatureManager.INSTANCE.observedUnlocks(),now);
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
