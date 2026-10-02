package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Recovery barrier, not a replay log: uncertain server ownership requires review. */
public final class BookJournal {
    public record Position(Book book, double cost) {}
    private static final Gson GSON = new Gson();
    private final Path path;
    private String previous;
    private List<Position> previousTracked;
    public BookJournal(Path path) { this.path = path; }
    public List<Position> read() throws Exception {
        if (!Files.exists(path)) return List.of();
        Position[] positions = GSON.fromJson(Files.readString(path), Position[].class);
        if (positions == null) throw new IllegalStateException("Missing book journal");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Position position : positions) {
            if (position == null || position.book() == null || position.book().id() == null
                    || !position.book().id().matches("ENCHANTMENT_[A-Z0-9_]+")
                    || position.book().level() < 1 || position.book().sellLevel() <= position.book().level()
                    || position.book().sellLevel() > 10 || position.book().name() == null
                    || position.book().name().isBlank() || !Double.isFinite(position.cost()) || position.cost() <= 0
                    || !ids.add(position.book().id())) throw new IllegalStateException("Invalid book journal");
        }
        return List.of(positions);
    }
    /** Persist only plans with observed ownership or a submission that may reach the server. */
    public void writeTracked(List<Position> plans, java.util.Set<String> exposed) throws Exception {
        List<Position> tracked = plans.stream().filter(position -> exposed.contains(position.book().id())).toList();
        // checkpoint() runs at least twice per tick. Value equality on records, rather
        // than a hash, so an unchanged journal never costs a serialisation and a changed
        // one can never be mistaken for an unchanged one.
        if (tracked.equals(previousTracked)) return;
        write(tracked);
        previousTracked = tracked;
    }
    public void write(List<Position> positions) throws Exception {
        // A direct write invalidates the writeTracked cache, so a later tracked write
        // can never skip on the strength of a snapshot this call replaced.
        previousTracked = null;
        String json = GSON.toJson(positions);
        if (json.equals(previous)) return;
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "book-orders-", ".tmp");
        try {
            Files.writeString(temp, json);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            previous = json;
        } finally { Files.deleteIfExists(temp); }
    }
}
