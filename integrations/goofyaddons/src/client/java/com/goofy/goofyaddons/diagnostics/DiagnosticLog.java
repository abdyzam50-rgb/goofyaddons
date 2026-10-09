package com.goofy.goofyaddons.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Disk operations are called only by the diagnostic worker. */
public final class DiagnosticLog {
    private final Path directory;
    private final long maxBytes;
    private final int archives;
    private final Gson gson=new Gson();
    private final String session=UUID.randomUUID().toString();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    public record Captured(long sequence, String time, long monotonicNanos, String level, String type, com.google.gson.JsonElement data) {}
    public Captured capture(String level, String type, Map<String,?> data) {
        return new Captured(sequence.incrementAndGet(), Instant.now().toString(), System.nanoTime(), level, type, gson.toJsonTree(data));
    }
    public DiagnosticLog(Path directory,long maxBytes,int archives) {
        if (maxBytes<1 || archives<1) throw new IllegalArgumentException("Invalid retention");
        this.directory=directory; this.maxBytes=maxBytes; this.archives=archives;
    }
    // Compiled once: redact() runs for every logged string, so per-call compilation showed up.
    private static final java.util.regex.Pattern PLAYER_LINE=java.util.regex.Pattern.compile("(?im)^(?:By|Created by|Order by|Seller|Buyer|Owner|Placed by|Co-op member):.*$");
    private static final java.util.regex.Pattern VENDOR_LINE=java.util.regex.Pattern.compile("(?m)^-\\s*[\\d,]+x\\s+.*$");
    private static final java.util.regex.Pattern CREDENTIAL=java.util.regex.Pattern.compile("(?i)(authorization|access[_-]?token|api[_-]?key|password|sessionid)([\\s=:]+)[^\\s,;]+");
    private static final java.util.regex.Pattern BEARER=java.util.regex.Pattern.compile("(?i)bearer\\s+[^\\s,;]+");
    private static final java.util.regex.Pattern EMAIL=java.util.regex.Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

    public static String redact(String text) {
        if (text==null) return "";
        String home=System.getProperty("user.home","");
        if (!home.isBlank()) text=text.replace(home,"<home>");
        text=com.goofy.goofyaddons.utils.Chat.strip(text);
        text=PLAYER_LINE.matcher(text).replaceAll("<player redacted>");
        text=VENDOR_LINE.matcher(text).replaceAll("<vendor redacted>");
        text=CREDENTIAL.matcher(text).replaceAll("$1$2<redacted>");
        text=BEARER.matcher(text).replaceAll("Bearer <redacted>");
        return EMAIL.matcher(text).replaceAll("<email>");
    }
    private String safeJson(Object value) {
        var tree=gson.toJsonTree(value);
        scrub(tree);
        return gson.toJson(tree);
    }
    private void scrub(com.google.gson.JsonElement tree) {
        if(tree.isJsonObject()) {
            for(var entry:tree.getAsJsonObject().entrySet()) {
                var value=entry.getValue();
                if(entry.getKey().matches("(?i).*(token|password|authorization|api.?key|sessionid).*")) entry.setValue(new com.google.gson.JsonPrimitive("<redacted>"));
                else if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) entry.setValue(new com.google.gson.JsonPrimitive(redact(value.getAsString())));
                else scrub(value);
            }
        } else if(tree.isJsonArray()) {
            var array=tree.getAsJsonArray();
            for(int i=0;i<array.size();i++) {
                var value=array.get(i);
                if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) array.set(i,new com.google.gson.JsonPrimitive(redact(value.getAsString())));
                else scrub(value);
            }
        }
    }
    public synchronized void append(String level,String type,Map<String,?> data) throws IOException {
        append(capture(level,type,data));
    }
    public synchronized void append(Captured captured) throws IOException {
        Files.createDirectories(directory);
        Path current=directory.resolve("events.jsonl");
        var event=new LinkedHashMap<String,Object>();
        event.put("schema",2);event.put("session",session);event.put("sequence",captured.sequence());
        event.put("time",captured.time());event.put("monotonicNanos",captured.monotonicNanos());event.put("writeTime",Instant.now().toString());event.put("level",captured.level());event.put("event",captured.type());event.put("data",captured.data());
        byte[] bytes=(safeJson(event)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (Files.exists(current) && Files.size(current)+bytes.length>maxBytes) {
            Files.deleteIfExists(directory.resolve("events."+archives+".jsonl"));
            for(int i=archives-1;i>=1;i--) {
                Path old=directory.resolve("events."+i+".jsonl");
                if(Files.exists(old)) Files.move(old,directory.resolve("events."+(i+1)+".jsonl"),StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(current,directory.resolve("events.1.jsonl"),StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(current,bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    public synchronized Path export(Map<String,?> snapshot) throws IOException {
        Files.createDirectories(directory);
        Path bundles=directory.resolve("bundles");Files.createDirectories(bundles);
        try(var files=Files.list(bundles)) {
            var old=files.filter(p->p.getFileName().toString().endsWith(".zip")).sorted().toList();
            for(int i=0;i<old.size()-4;i++) Files.deleteIfExists(old.get(i));
        }
        Path target=bundles.resolve("diagnostics-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry("snapshot.json"));
            zip.write(safeJson(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            if(snapshot.get("currentMenu") instanceof Map<?,?> menu) {
                zip.putNextEntry(new ZipEntry("current-menu.json"));
                zip.write(safeJson(menu).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            for(int i=0;i<=archives;i++) {
                Path file=directory.resolve(i==0?"events.jsonl":"events."+i+".jsonl");
                if(!Files.exists(file)) continue;
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));Files.copy(file,zip);zip.closeEntry();
            }
        } catch(IOException failed) { Files.deleteIfExists(target);throw failed; }
        return target;
    }
}
