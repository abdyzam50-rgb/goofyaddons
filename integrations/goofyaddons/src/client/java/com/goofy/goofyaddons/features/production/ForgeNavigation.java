package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.generalflipper.BazaarAccess;
import java.util.*;

/** Reversible GUI navigation only. Submission is a separate persisted, verified transaction. */
public final class ForgeNavigation {
    public enum State {SLOT,CATEGORY,ITEMS,CONFIRM,READY,FAILED}
    private State state=State.SLOT;
    private final ProductionRecipe recipe;
    private final String itemName;
    private final int workstationSlot;
    private long started,clickedAt;
    private int previousContainer=-1,pages;
    private final Set<String> visitedCategories=new HashSet<>();
    private boolean pending;
    private String failure;
    public ForgeNavigation(ProductionRecipe recipe,String itemName,int workstationSlot) {
        if(recipe.kind()!=ProductionRecipe.Kind.FORGE || workstationSlot<0 || workstationSlot>6 || itemName==null || itemName.isBlank())throw new IllegalArgumentException("Invalid forge navigation");
        this.recipe=recipe;this.itemName=itemName;this.workstationSlot=workstationSlot;
    }
    public State state(){return state;}public String failure(){return failure;}
    private void fail(String reason){failure=reason;state=State.FAILED;}
    public void tick(MenuSnapshot menu,GameActions actions,long now) {
        if(state==State.READY || state==State.FAILED)return;
        if(started==0)started=now;
        if(menu==null || !menu.cursorEmpty()){fail("Forge navigation requires an empty cursor and a visible menu");return;}
        if(now-started>45000){fail("Forge navigation timed out; no process submitted");return;}
        String title=Chat.strip(menu.title());
        if(pending) {
            if(menu.containerId()!=previousContainer){pending=false;}
            else {if(now-clickedAt>8000)fail("Forge navigation click was not acknowledged");return;}
        }
        switch(state) {
            case SLOT -> {
                if(!title.equals("The Forge")){fail("Open The Forge menu first");return;}
                var slot=menu.slot(10+workstationSlot);
                if(slot==null || slot.empty() || slot.inPlayerInventory() || !Chat.strip(slot.hoverName()).equals("Slot #"+(workstationSlot+1))
                        || slot.lore().toLowerCase(Locale.ROOT).contains("locked") || !slot.lore().toLowerCase(Locale.ROOT).contains("click")) {
                    fail("Selected Forge slot is not confirmed available");return;
                }
                click(menu,actions,slot.index(),now);state=State.CATEGORY;
            }
            case CATEGORY -> {
                if(!Set.of("Select Process","Forge Item","The Forge").contains(title)){fail("Unrecognized Forge process menu");return;}
                // Search each recognized category at most once; category changes are reversible.
                var category=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory()
                        && Set.of("Refine Items","Item Casting").contains(Chat.strip(s.hoverName()))
                        && !visitedCategories.contains(Chat.strip(s.hoverName()))).sorted(Comparator.comparing(s->Chat.strip(s.hoverName()))).toList();
                if(category.isEmpty()){fail("Forge recipe absent from available categories");return;}
                visitedCategories.add(Chat.strip(category.getFirst().hoverName()));pages=0;
                click(menu,actions,category.getFirst().index(),now);state=State.ITEMS;
            }
            case ITEMS -> {
                if(!(title.startsWith("Refine") || title.startsWith("Item Casting"))){fail("Unrecognized Forge recipe menu");return;}
                var items=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory()
                        && (recipe.outputId().equals(s.customId()) || (s.customId()==null || s.customId().isBlank()) && itemName.equals(Chat.strip(s.hoverName())))).toList();
                if(items.size()==1) {
                    String unmet=BazaarAccess.unmet(items.getFirst().lore());if(unmet!=null){fail(unmet);return;}
                    click(menu,actions,items.getFirst().index(),now);state=State.CONFIRM;return;
                }
                var next=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory() && "Next Page".equals(Chat.strip(s.hoverName()))).toList();
                if(items.size()>1 || next.size()>1 || ++pages>8){fail("Forge recipe was not uniquely found");return;}
                if(next.isEmpty()) {
                    var back=menu.slots().stream().filter(s->!s.empty() && !s.inPlayerInventory() && "Go Back".equals(Chat.strip(s.hoverName()))).toList();
                    if(back.size()!=1){fail("Forge recipe was not found; no confirmed category return control");return;}
                    click(menu,actions,back.getFirst().index(),now);state=State.CATEGORY;return;
                }
                click(menu,actions,next.getFirst().index(),now);
            }
            case CONFIRM -> {
                if(!ProductionMenus.forgeConfirmation(menu,recipe)){fail("Forge confirmation does not match recipe inputs/output");return;}
                state=State.READY;
            }
            default -> {}
        }
    }
    private void click(MenuSnapshot menu,GameActions actions,int slot,long now){
        previousContainer=menu.containerId();clickedAt=now;pending=true;actions.click(slot,false);
    }
}
