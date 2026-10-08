package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        String json = GSON.toJson(positions);
        if (json.equals(persisted)) return;
        Path destination = path.get();
        Path temp = null;
        try {
            Files.createDirectories(destination.getParent());
            temp = Files.createTempFile(destination.getParent(), "general-orders-", ".tmp");
            Files.writeString(temp, json);
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            persisted = json; // Failed writes must never satisfy a later persist-before-click check.
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }
}
