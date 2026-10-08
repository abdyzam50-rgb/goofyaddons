package com.goofy.goofyaddons.features.access;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;

/** Catalog unlocks are checked before navigation; server action controls remain authoritative. */
public final class RouteRequirements {
    private static final JsonObject BOOKS=load();
    private static JsonObject load() {
        try(var in=RouteRequirements.class.getResourceAsStream("/goofyaddons/book-requirements.json")) {
            if(in==null)throw new IllegalStateException("Book requirements missing");
            return JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("books");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private RouteRequirements() {}
    public static String book(String id,Map<String,Integer> skills) {
        var rule=BOOKS.getAsJsonObject(id);
        if(rule==null || rule.get("minimumEnchanting").isJsonNull())return "Enchanting requirement is unverified for "+id;
        int minimum=rule.get("minimumEnchanting").getAsInt();
        if(minimum==0)return null;
        return threshold("Enchanting",minimum,skills.get("enchanting"));
    }
    public static String threshold(String name,int required,Integer current) {
        if(current==null)return "Requires "+name+" "+required+"; account requirement is unobserved";
        return current>=required?null:"Requires "+name+" "+required+"; observed "+current;
    }
    public static String normalize(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private static final Pattern REQUIREMENT=Pattern.compile("^(.+?)\\s+([0-9]{1,6}|[IVXLCDM]+)$",Pattern.CASE_INSENSITIVE);
    private static final Set<String> SKILLS=Set.of("farming","mining","combat","foraging","fishing","enchanting","alchemy","carpentry","taming","runecrafting","social");
    public static String craft(String text,Map<String,Integer> skills,Map<String,Integer> unlocks) {
        if(text==null || text.isBlank())return null;
        String clean=com.goofy.goofyaddons.utils.Chat.strip(text).replaceFirst("(?i)^requires?:\\s*","").trim();
        for(String part:clean.split("\\s*(?:&|,|(?i: and ))\\s*")) {
            var m=REQUIREMENT.matcher(part.trim());
            if(!m.matches())return "Unverified recipe requirement: "+part;
            int required=m.group(2).matches("[0-9]+")?Integer.parseInt(m.group(2)):ActionRequirements.level(m.group(2));
            if(required<0)return "Unverified recipe requirement: "+part;
            String name=m.group(1),key=normalize(name);
            Integer current=SKILLS.contains(key)?skills.get(key):unlocks.get(key);
            String reason=threshold(name,required,current);if(reason!=null)return reason;
        }
        return null;
    }
}
