package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Existing array journal format; path resolution and I/O remain lazy. */
final class JsonGeneralOrderRepository implements GeneralOrderRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Supplier<Path> path;
    private String persisted;
    JsonGeneralOrderRepository(Supplier<Path> path) { this.path = java.util.Objects.requireNonNull(path); }

    @Override public List<GeneralPosition> load() throws Exception {
        Path source = path.get();
        if (!Files.exists(source)) return List.of();
        GeneralPosition[] saved = GSON.fromJson(Files.readString(source), GeneralPosition[].class);
        if (saved == null) throw new IllegalArgumentException("Missing order state");
        return GeneralPosition.validated(java.util.Arrays.asList(saved));
    }

    @Override public void save(List<GeneralPosition> positions) throws Exception {
        var tree=GSON.toJsonTree(positions).getAsJsonArray();
        // Additive strategy fields do not rewrite legacy journals with meaningless defaults.
        for(var value:tree){var row=value.getAsJsonObject();
            for(String field:List.of("instantBuy","instantSell","productionBuy","completed"))if(row.has(field)&&!row.get(field).getAsBoolean())row.remove(field);
            for(String field:List.of("maximumBuyPrice","minimumSellPrice"))if(row.has(field)&&row.get(field).getAsDouble()==0)row.remove(field);
        }
        String json = GSON.toJson(tree);
        if (json.equals(persisted)) return;
        Path destination = path.get();
        com.goofy.goofyaddons.storage.AtomicFiles.replace(destination, json, "general-orders-");
        persisted = json; // Failed writes must never satisfy a later persist-before-click check.
    }
}
