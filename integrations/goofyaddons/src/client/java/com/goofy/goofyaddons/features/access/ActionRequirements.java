package com.goofy.goofyaddons.features.access;

import com.goofy.goofyaddons.utils.Chat;
import com.goofy.goofyaddons.features.generalflipper.BazaarAccess;
import java.util.*;
import java.util.regex.Pattern;

/** Requirements from an action control, never a generic equipment-use tooltip. */
public final class ActionRequirements {
    public enum Action {BUY,COMBINE,CRAFT,FORGE,KAT}
    private static final String SKILLS="Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Taming|Runecrafting|Social";
    private static final Pattern THRESHOLD=Pattern.compile("(?i)(?:requires?|you need|you must have)\\s+(?:an?\\s+)?("+SKILLS+")\\s+(?:skill\\s+)?(?:level\\s*)?[: ]*([0-9]{1,2}|[IVXLCDM]+)\\b");
    private static final Pattern UNLOCK=Pattern.compile("(?i)(?:requires?|you need|you must have)\\s+(?:an?\\s+)?(HotM|Heart of the Mountain|(?:Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer)\\s+(?:(?:tier|level)\\s*)?[: ]*([0-9]{1,2}|[IVXLCDM]+)\\b");
    private static final Pattern NAME=Pattern.compile("(?i)^("+SKILLS+")\\s+([0-9]{1,2}|[IVXLCDM]+)$");
    private static final Pattern PROGRESS=Pattern.compile("(?i)progress to level\\s+([0-9]{1,2}|[IVXLCDM]+)\\b");
    private ActionRequirements() {}
    public static int level(String s) {
        if(s.matches("[0-9]{1,2}"))return Integer.parseInt(s);
        for(int n=1;n<=60;n++)if(roman(n).equals(s.toUpperCase(Locale.ROOT)))return n;
        return -1;
    }
    private static String roman(int n) {
        StringBuilder s=new StringBuilder();int[] values={50,40,10,9,5,4,1};String[] words={"L","XL","X","IX","V","IV","I"};
        for(int i=0;i<values.length;i++)while(n>=values[i]){s.append(words[i]);n-=values[i];}return s.toString();
    }
    public static Map<String,Integer> skills(com.goofy.goofyaddons.menu.MenuSnapshot menu) {
        if(menu==null || !Set.of("Your Skills","Skills").contains(Chat.strip(menu.title())))return Map.of();
        Map<String,Integer> result=new TreeMap<>();Set<String> ambiguous=new HashSet<>();
        for(var slot:menu.slots()) {
            if(slot.inPlayerInventory() || slot.empty())continue;
            String title=Chat.strip(slot.hoverName()).trim();var match=NAME.matcher(title);String skill=null;int value=-1;
            if(match.matches()){skill=match.group(1).toLowerCase(Locale.ROOT);value=level(match.group(2));}
            else if(title.matches("(?i)^(?:"+SKILLS+")$")) {
                skill=title.toLowerCase(Locale.ROOT);var progress=PROGRESS.matcher(Chat.strip(slot.lore()));
                if(progress.find()){int next=level(progress.group(1));if(next>0)value=next-1;}
            }
            if(skill!=null && value>=0 && value<=60) {
                Integer previous=result.putIfAbsent(skill,value);if(previous!=null && previous!=value)ambiguous.add(skill);
            }
        }
        ambiguous.forEach(result::remove);return Map.copyOf(result);
    }
    public static String blocked(String control,Map<String,Integer> skills,Action action) {
        return blocked(control,skills,Map.of(),action);
    }
    public static String blocked(String control,Map<String,Integer> skills,Map<String,Integer> unlocks,Action action) {
        String explicit=BazaarAccess.unmet(control);if(explicit!=null)return explicit;
        var unlock=UNLOCK.matcher(Chat.strip(control));
        while(unlock.find()) {
            String reason=RouteRequirements.craft(unlock.group(1)+" "+unlock.group(2),skills,unlocks);
            if(reason!=null)return action+" "+reason;
        }
        var match=THRESHOLD.matcher(Chat.strip(control));
        while(match.find()) {
            int required=level(match.group(2));if(required<1 || required>60)continue;
            Integer current=skills.get(match.group(1).toLowerCase(Locale.ROOT));
            if(current==null)return action+" requires "+match.group(1)+" "+required+"; account skill level is unobserved";
            if(current<required)return action+" requires "+match.group(1)+" "+required+"; observed level "+current;
        }
        return null;
    }
}
