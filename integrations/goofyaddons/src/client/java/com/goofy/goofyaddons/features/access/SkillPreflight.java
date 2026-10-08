package com.goofy.goofyaddons.features.access;

import com.goofy.goofyaddons.menu.*;
import java.util.Map;

/** One skills GUI visit per fresh start; missing observations stay unknown. */
public final class SkillPreflight {
    private Map<String,Integer> skills=Map.of();
    private boolean pending;private long started,next;private int attempts;
    public Map<String,Integer> skills(){return skills;}
    public boolean pending(){return pending;}
    public boolean observe(MenuSnapshot menu){var value=ActionRequirements.skills(menu);if(value.isEmpty() || value.equals(skills))return false;skills=value;return true;}
    public void begin(){skills=Map.of();pending=true;started=0;next=0;attempts=0;}
    public void cancel(){pending=false;}
    public void clear(){cancel();skills=Map.of();}
    public void tick(MenuSnapshot menu,GameActions actions,long now) {
        if(!pending)return;
        if(started==0)started=now;
        if(menu!=null && !menu.cursorEmpty())throw new IllegalStateException("Cannot check account requirements with an occupied cursor");
        var observed=ActionRequirements.skills(menu);
        if(!observed.isEmpty())skills=observed;
        if(observed.containsKey("enchanting")) {
            skills=observed;pending=false;actions.closeMenu();return;
        }
        if(now-started>=25000){pending=false;
            if(menu!=null && java.util.Set.of("Your Skills","Skills").contains(com.goofy.goofyaddons.utils.Chat.strip(menu.title())))actions.closeMenu();actions.message("Skill check unavailable; routes requiring unobserved levels will be skipped. Open Your Skills or restart to recheck.");return;}
        if(menu!=null && menu.title()!=null && !java.util.Set.of("Your Skills","Skills").contains(com.goofy.goofyaddons.utils.Chat.strip(menu.title()))) {
            // Own no unrelated GUI; wait for the player to close it instead of clicking through it.
            return;
        }
        if(attempts<3 && now>=next && (menu==null || menu.title()==null)) {
            attempts++;next=now+8000;actions.command("skills");
        }
    }
}
