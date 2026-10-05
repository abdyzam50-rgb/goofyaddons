package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.access.ActionRequirements;
import java.util.*;

/** Forge/Kat submission and claim proof. Journal intent precedes every irreversible click. */
public final class WorkstationExecutor {
    public enum Result {WAITING,SUBMITTED,CLAIMED,BLOCKED}
    private final ProductionRecipe recipe;
    private final String jobId;
    private final ProductionJobs journal;
    private final ItemMetadata pet;
    private Map<String,Integer> beforeInputs;
    private long actionAt;
    private int beforeOutput;
    private Double purseBefore;
    private double expectedCharge;
    private String failure;
    public String failure(){return failure;}
    public WorkstationExecutor(ProductionRecipe recipe,String jobId,ProductionJobs journal,ItemMetadata pet) {
        if(recipe.kind()==ProductionRecipe.Kind.CRAFT)throw new IllegalArgumentException("Crafting is not a timed workstation");
        this.recipe=recipe;this.jobId=jobId;this.journal=journal;this.pet=pet;
        if(recipe.kind()==ProductionRecipe.Kind.KAT && (pet==null || pet.uuid()==null || !recipe.inputPet().equals(pet.petVariant())))
            throw new IllegalArgumentException("Kat needs the exact input pet UUID and variant");
    }
    public Result tick(MenuSnapshot menu,GameActions actions,Map<String,Integer> skills,String account,double purse,double maximumCoins,long now) {
        try {
            var job=journal.find(jobId).orElseThrow();
            if(job.batches()!=1 || !job.account().equals(account) || !job.recipeKey().equals(recipe.key()))return block("Workstation job account/recipe changed");
            if(menu==null || !menu.cursorEmpty())return block("Workstation transaction requires a readable menu and empty cursor");
            if(job.state()==ProductionJobs.State.PLANNED) {
                if(!Double.isFinite(purse) || purse<0 || !Double.isFinite(maximumCoins) || maximumCoins<0)return block("Workstation spending budget is unavailable");
                int control=submitControl(menu);
                if(control<0)return block("Workstation confirmation does not prove the selected recipe/pet");
                String reason=ActionRequirements.blocked(menu.slot(control).lore(),skills,
                        recipe.kind()==ProductionRecipe.Kind.FORGE?ActionRequirements.Action.FORGE:ActionRequirements.Action.KAT);
                if(reason!=null)return block(reason);
                Double charge=ProductionMenus.exactCoins(menu.slot(control).lore());
                if(recipe.coins()>0 && (charge==null || charge>maximumCoins || charge>purse) || recipe.coins()==0 && charge!=null)return block("Workstation coin charge unreadable or outside budget");
                beforeInputs=inventory(menu);purseBefore=purse;expectedCharge=charge==null?0:charge;
                for(var need:recipe.ingredients().entrySet())if(!need.getKey().equals(recipe.inputPet()) && beforeInputs.getOrDefault(need.getKey(),0)<need.getValue())return block("Missing workstation ingredient: "+need.getKey());
                journal.put(job.withState(ProductionJobs.State.SUBMITTING,"Submission intent saved; awaiting server proof"));
                actionAt=now;actions.click(control,false);return Result.WAITING;
            }
            if(job.state()==ProductionJobs.State.SUBMITTING) {
                // A restored uncertain intent has no baseline: never replay it.
                if(beforeInputs==null || actionAt==0)return block("Interrupted submission requires live reconciliation");
                var timer=timer(menu,job.workstationSlot());
                var current=inventory(menu);boolean consumed=true;
                for(var need:recipe.ingredients().entrySet())if(!need.getKey().equals(recipe.inputPet()))
                    consumed &= current.getOrDefault(need.getKey(),0)==beforeInputs.getOrDefault(need.getKey(),0)-need.getValue();
                boolean debit=recipe.coins()==0 || Double.isFinite(purse) && purseBefore!=null && Math.abs(purseBefore-purse-expectedCharge)<=0.51;
                if(timer!=null && consumed && debit) {
                    journal.put(job.submitted(job.workstationSlot(),now,Math.addExact(now,Math.multiplyExact(timer.remainingSeconds(),1000)),job.costBasis()==null?null:job.costBasis()+expectedCharge));
                    return Result.SUBMITTED;
                }
                if(now-actionAt>20000)return review(job,"Submission not acknowledged; no automatic second submission");
                return Result.WAITING;
            }
            if(job.state()==ProductionJobs.State.WAITING) {
                var timer=timer(menu,job.workstationSlot());if(timer==null || !timer.complete())return Result.WAITING;
                int claim=claimControl(menu,job.workstationSlot());if(claim<0)return block("Completed workstation item identity is not verified");
                beforeOutput=inventory(menu).getOrDefault(recipe.outputId(),0);
                journal.put(job.withState(ProductionJobs.State.CLAIMING,"Claim intent saved; awaiting inventory proof"));
                actionAt=now;actions.click(claim,false);return Result.WAITING;
            }
            if(job.state()==ProductionJobs.State.CLAIMING) {
                if(actionAt==0)return block("Interrupted claim requires live reconciliation");
                boolean arrived=inventory(menu).getOrDefault(recipe.outputId(),0)==beforeOutput+recipe.outputCount();
                if(recipe.kind()==ProductionRecipe.Kind.KAT)arrived &= menu.slots().stream().anyMatch(s->s.inPlayerInventory() && !s.empty()
                        && pet.uuid().equals(s.metadata().uuid()) && recipe.outputId().equals(s.metadata().petVariant()));
                if(arrived && timer(menu,job.workstationSlot())==null) {
                    journal.put(job.completedBatch());return Result.CLAIMED;
                }
                if(now-actionAt>20000)return review(job,"Claim not acknowledged; verify NPC and inventory before retrying");
                return Result.WAITING;
            }
            return block("Workstation job is not in an executable state: "+job.state());
        }catch(Exception failed){failure="Workstation journal/evidence failed; no replay authorized";return Result.BLOCKED;}
    }
    private Result review(ProductionJobs.Job job,String reason)throws java.io.IOException {journal.put(job.withState(ProductionJobs.State.REVIEW,reason));return block(reason);}
    private Result block(String reason){failure=reason;return Result.BLOCKED;}
    private int submitControl(MenuSnapshot menu) {
        if(recipe.kind()==ProductionRecipe.Kind.FORGE) {
            if(!ProductionMenus.forgeConfirmation(menu,recipe))return -1;
            var matches=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory()
                    && Set.of("Confirm","Confirm Process").contains(Chat.strip(s.hoverName())) && s.hasLoreLine("Click to confirm!")).toList();
            return matches.size()==1?matches.getFirst().index():-1;
        }
        if(!"Pet Sitter".equals(Chat.strip(menu.title())))return -1;
        var input=menu.slot(13);var button=menu.slot(22);
        if(input==null || button==null || !pet.samePet(input.metadata()) || !button.hasLoreLine("Click to upgrade!"))return -1;
        String output=recipe.outputId();
        if(!output.startsWith(pet.petType()+";") || Integer.parseInt(output.substring(output.lastIndexOf(';')+1))!=Integer.parseInt(recipe.inputPet().substring(recipe.inputPet().lastIndexOf(';')+1))+1)return -1;
        return 22;
    }
    private ProductionMenus.ForgeSlot timer(MenuSnapshot menu,int slot) {
        if(recipe.kind()==ProductionRecipe.Kind.FORGE)return ProductionMenus.forgeSlots(menu).stream()
                .filter(s->s.slot()==slot && recipe.outputId().equals(s.productId())).findFirst().orElse(null);
        if(!"Pet Sitter".equals(Chat.strip(menu.title())))return null;
        var input=menu.slot(13);var control=menu.slot(22);
        if(input==null || control==null || !pet.uuid().equals(input.metadata().uuid()) || !pet.petType().equals(input.metadata().petType()))return null;
        return ProductionMenus.katTimer(control);
    }
    private int claimControl(MenuSnapshot menu,int slot) {
        if(recipe.kind()==ProductionRecipe.Kind.FORGE){var item=menu.slot(slot+10);return item!=null && recipe.outputId().equals(ProductionMenus.productId(item))?slot+10:-1;}
        var item=menu.slot(13);var control=menu.slot(22);
        return item!=null && control!=null && pet.uuid().equals(item.metadata().uuid()) && recipe.outputId().equals(item.metadata().petVariant())
                && control.hasLoreLine("Click to collect!")?22:-1;
    }
    private static Map<String,Integer> inventory(MenuSnapshot menu){
        var counts=new HashMap<String,Integer>();
        for(var slot:menu.slots())if(slot.inPlayerInventory() && slot.containerSlot()<36 && !slot.empty()) {
            String id="PET".equals(slot.customId())?slot.metadata().petVariant():slot.customId();
            if(id!=null)counts.merge(id,slot.count(),Integer::sum);
        }
        return counts;
    }
}
