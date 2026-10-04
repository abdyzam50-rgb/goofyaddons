package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.goofy.goofyaddons.utils.Chat;
import java.nio.file.*;
import java.util.*;

/** Instance-local account exclusions; public market quotes cannot prove unlocks. */
public final class BazaarAccess {
    public static final Set<String> MUTATIONS = mutationProducts();
    private static Set<String> mutationProducts() {
        try(var stream=BazaarAccess.class.getResourceAsStream("/goofyaddons/mutation-products.json")) {
            if(stream==null)throw new IllegalStateException("Mutation category catalog is missing");
            var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
            Set<String> ids=new TreeSet<>();
            for(var id:root.getAsJsonObject().getAsJsonArray("products"))ids.add(id.getAsString());
            return Set.copyOf(ids);
        } catch(java.io.IOException failure) { throw new IllegalStateException("Cannot read mutation category catalog",failure); }
    }
    private static final class Live {
        static final BazaarAccess INSTANCE = new BazaarAccess(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("goofyaddons-bazaar-access.json"));
    }
    public static BazaarAccess instance() { return Live.INSTANCE; }
    private final Path path;
    private final Map<String,String> denied = new TreeMap<>();
    public BazaarAccess(Path path) {
        this.path=path;
        if(Files.exists(path))try {
            Map<String,String> saved=new Gson().fromJson(Files.readString(path),new TypeToken<Map<String,String>>(){}.getType());
            if(saved==null || saved.size()>4096 || saved.entrySet().stream().anyMatch(e->e.getKey()==null
                    || !e.getKey().matches("[A-Z0-9_]+") || e.getValue()==null || e.getValue().length()>500))
                throw new IllegalArgumentException("Invalid Bazaar access exclusions");
            denied.clear();denied.putAll(saved);
        } catch(Exception failure) {
            org.slf4j.LoggerFactory.getLogger(BazaarAccess.class).warn("Cannot read Bazaar access exclusions; keeping default exclusions",failure);
        }
    }
    public Set<String> excluded() {
        var all=new TreeSet<>(MUTATIONS);all.addAll(denied.keySet());return Set.copyOf(all);
    }
    public void deny(String id,String reason) {
        if(!id.matches("[A-Z0-9_]+"))throw new IllegalArgumentException("Invalid product ID");
        denied.put(id,reason.substring(0,Math.min(500,reason.length())));
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary,new Gson().toJson(denied));
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception failure) {
            org.slf4j.LoggerFactory.getLogger(BazaarAccess.class).warn("Cannot save Bazaar access exclusions; item remains excluded for this run",failure);
        }
    }
    /** Explicit unmet requirements only; ordinary descriptive requirement lore is insufficient. */
    public static String unmet(String text) {
        String clean=Chat.strip(text).replace('\u2019','\'');
        for(String line:clean.split("\\R")) {
            String lower=line.trim().toLowerCase(Locale.ROOT);
            if(lower.matches("(?:common|uncommon|rare|epic|legendary|mythic|divine|special|very special) mutation"))
                return "Farming > Garden > Mutations is excluded";
            if(lower.matches(".*\\b(?:not unlocked|not discovered|not inspected|haven't unlocked|have not unlocked|haven't discovered|have not discovered|haven't inspected|have not inspected)\\b.*")
                    || lower.matches(".*\\b(?:you must|you need to|required to|requires you to) (?:first )?(?:inspect|discover|unlock)\\b.*")
                    || lower.matches(".*\\byou (?:do not|don't) meet (?:the |this )?requirements?\\b.*")
                    || lower.matches(".*\\byou (?:need|require) .+ to (?:buy|purchase|trade|use the bazaar)\\b.*"))
                return line.trim();
        }
        return null;
    }
}
