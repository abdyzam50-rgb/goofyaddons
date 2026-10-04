package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Saved ownership and trade identity; live verification reconstructs supported cycles before resumption. */
public final class BookJournal {
    public record Position(Book book, double cost, String tradeId, boolean retiring, long progressAt, boolean orphanCleanup) {
        public Position(Book book,double cost,String tradeId,boolean retiring,long progressAt){this(book,cost,tradeId,retiring,progressAt,false);}
        public Position(Book book,double cost,String tradeId){this(book,cost,tradeId,false,0,false);}
        public Position(Book book, double cost) {this(book,cost,null,false,0,false);}
    }
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
                    || position.tradeId()!=null && !position.tradeId().matches("[A-Za-z0-9_-]{1,100}")
                    || position.progressAt()<0 || position.progressAt()>System.currentTimeMillis()+5000
                    || !ids.add(position.book().id())) throw new IllegalStateException("Invalid book journal");
        }
        return List.of(positions);
    }
    public void backupVerified(List<Position> expected) throws Exception {
        if(!read().equals(expected))throw new IllegalStateException("Book journal changed during recovery check");
        if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".resumed-"+java.util.UUID.randomUUID()+".bak"));
    }
    /** Only a completed live check may retire old records; preserve the original as evidence. */
    public void reconcileVerified(List<Position> expected, java.util.Set<String> stillPresent) throws Exception {
        if(!read().equals(expected))throw new IllegalStateException("Book journal changed during recovery check");
        List<Position> retained=expected.stream().filter(p->stillPresent.contains(p.book().id())).toList();
        if(retained.equals(expected))return;
        if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".verified-"+java.util.UUID.randomUUID()+".bak"));
        write(retained);
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
