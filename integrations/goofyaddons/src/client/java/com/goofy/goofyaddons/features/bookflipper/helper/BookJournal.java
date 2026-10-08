package com.goofy.goofyaddons.features.bookflipper.helper;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Saved ownership and trade identity; live verification reconstructs supported cycles before resumption. */
public final class BookJournal implements BookOrderRepository {
    private static final Gson GSON = new Gson();
    private final Path path;
    private String previous;
    private List<BookPosition> previousTracked;
    public BookJournal(Path path) { this.path = path; }
    public List<BookPosition> read() throws Exception {
        if (!Files.exists(path)) return List.of();
        BookPosition[] positions = GSON.fromJson(Files.readString(path), BookPosition[].class);
        if (positions == null) throw new IllegalStateException("Missing book journal");
        return BookPosition.validated(java.util.Arrays.asList(positions),System.currentTimeMillis());
    }
    public void backupVerified(List<BookPosition> expected) throws Exception {
        if(!read().equals(expected))throw new IllegalStateException("Book journal changed during recovery check");
        if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".resumed-"+java.util.UUID.randomUUID()+".bak"));
    }
    /** Only a completed live check may retire old records; preserve the original as evidence. */
    public void reconcileVerified(List<BookPosition> expected, java.util.Set<String> stillPresent) throws Exception {
        if(!read().equals(expected))throw new IllegalStateException("Book journal changed during recovery check");
        List<BookPosition> retained=expected.stream().filter(p->stillPresent.contains(p.book().id())).toList();
        if(retained.equals(expected))return;
        if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".verified-"+java.util.UUID.randomUUID()+".bak"));
        write(retained);
    }
    /** Persist only plans with observed ownership or a submission that may reach the server. */
    public void writeTracked(List<BookPosition> plans, java.util.Set<String> exposed) throws Exception {
        List<BookPosition> tracked = plans.stream().filter(position -> exposed.contains(position.book().id())).toList();
        // checkpoint() runs at least twice per tick. Value equality on records, rather
        // than a hash, so an unchanged journal never costs a serialisation and a changed
        // one can never be mistaken for an unchanged one.
        if (tracked.equals(previousTracked)) return;
        write(tracked);
        previousTracked = tracked;
    }
    public void write(List<BookPosition> positions) throws Exception {
        // A direct write invalidates the writeTracked cache, so a later tracked write
        // can never skip on the strength of a snapshot this call replaced.
        previousTracked = null;
        String json = GSON.toJson(positions);
        if (json.equals(previous)) return;
        com.goofy.goofyaddons.storage.AtomicFiles.replace(path, json, "book-orders-");
        previous = json;
    }
}
