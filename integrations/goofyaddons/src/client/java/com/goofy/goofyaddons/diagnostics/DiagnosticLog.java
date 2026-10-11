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
        return text.indexOf('@')<0?text:EMAIL.matcher(text).replaceAll("<email>");
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
        if(captured.type().equals("production.wait_observed") || captured.type().equals("production.run_review") || captured.type().equals("production.run_interrupted")
                || captured.type().equals("safety.pause") || captured.level().equals("ERROR"))retainIncident(captured,bytes);
    }
    /** Last sixteen incidents survive ordinary heartbeat/ranking rotation and client restarts. */
    private void retainIncident(Captured captured,byte[] bytes)throws IOException {
        Path incidents=directory.resolve("incidents");Files.createDirectories(incidents);
        if(bytes.length>2*1024*1024) {
            bytes=(safeJson(Map.of("event",captured.type(),"time",captured.time(),"evidenceTruncated",true,
                "originalBytes",bytes.length,"redactedPrefix",new String(bytes,java.nio.charset.StandardCharsets.UTF_8).substring(0,32768)))+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        Path file=incidents.resolve(String.format("%019d-%s-%019d.json",System.currentTimeMillis(),session,captured.sequence()));
        Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
        try(var paths=Files.list(incidents)) {
            var old=paths.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList();
            for(int i=0;i<old.size()-16;i++)Files.deleteIfExists(old.get(i));
        }
    }
    public synchronized Path export(Map<String,?> snapshot) throws IOException { return export(snapshot,Map.of()); }
    public synchronized Path export(Map<String,?> snapshot,Map<String,Path> accountFiles) throws IOException { return export(snapshot,accountFiles,Map.of()); }
    /** Events and incidents beyond this many bytes are left out, newest kept, so a bundle stays easy to send. */
    static final long EXPORT_LOG_BUDGET=16L*1024*1024;
    /**
     * Account journals (orders, production jobs) go in under account/, redacted like every other
     * entry. {@code extras} become their own JSON files (config, chat, market snapshot, rankings).
     */
    public synchronized Path export(Map<String,?> snapshot,Map<String,Path> accountFiles,Map<String,?> extras) throws IOException {
        Files.createDirectories(directory);
        Path bundles=directory.resolve("bundles");Files.createDirectories(bundles);
        try(var files=Files.list(bundles)) {
            var old=files.filter(p->p.getFileName().toString().endsWith(".zip")).sorted().toList();
            for(int i=0;i<old.size()-4;i++) Files.deleteIfExists(old.get(i));
        }
        Path target=bundles.resolve("diagnostics-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".zip");
        var manifest=new LinkedHashMap<String,Object>();var included=new ArrayList<String>();var omitted=new ArrayList<String>();
        try(var zip=new ZipOutputStream(Files.newOutputStream(target))) {
            // The menu and production state get their own files; they are not repeated in the snapshot.
            var trimmed=new LinkedHashMap<String,Object>(snapshot);
            Object menu=trimmed.remove("currentMenu"),production=trimmed.remove("productionDebug");
            write(zip,"snapshot.json",trimmed,included);
            if(menu instanceof Map<?,?>) write(zip,"current-menu.json",menu,included);
            if(production instanceof Map<?,?>) write(zip,"production-debug.json",production,included);
            for(var extra:new TreeMap<String,Object>(extras).entrySet()) if(extra.getValue()!=null) write(zip,extra.getKey()+".json",extra.getValue(),included);
            for(var journal:new TreeMap<>(accountFiles).entrySet()) {
                Path file=journal.getValue();
                if(file==null || !Files.isRegularFile(file)) continue;
                zip.putNextEntry(new ZipEntry("account/"+journal.getKey()));
                zip.write(accountJournal(file).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();included.add("account/"+journal.getKey());
            }
            // Newest first: current events, then incidents newest to oldest, then older event archives.
            var logs=new ArrayList<Map.Entry<String,Path>>();
            Path current=directory.resolve("events.jsonl");if(Files.exists(current))logs.add(Map.entry("events.jsonl",current));
            Path incidents=directory.resolve("incidents");
            if(Files.isDirectory(incidents))try(var paths=Files.list(incidents)) {
                for(var file:paths.filter(p->p.getFileName().toString().endsWith(".json")).sorted(Comparator.reverseOrder()).toList())
                    logs.add(Map.entry("incidents/"+file.getFileName(),file));
            }
            for(int i=1;i<=archives;i++) {Path file=directory.resolve("events."+i+".jsonl");if(Files.exists(file))logs.add(Map.entry(file.getFileName().toString(),file));}
            long used=0;
            for(var log:logs) {
                long size=Files.size(log.getValue());
                if(used+size>EXPORT_LOG_BUDGET && used>0){omitted.add(log.getKey()+" ("+size+" bytes)");continue;}
                zip.putNextEntry(new ZipEntry(log.getKey()));Files.copy(log.getValue(),zip);zip.closeEntry();used+=size;included.add(log.getKey());
            }
            manifest.put("included",included);manifest.put("omittedForSize",omitted);manifest.put("logBudgetBytes",EXPORT_LOG_BUDGET);manifest.put("logBytes",used);
            zip.putNextEntry(new ZipEntry("bundle.json"));zip.write(safeJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        } catch(IOException failed) { Files.deleteIfExists(target);throw failed; }
        return target;
    }
    private void write(ZipOutputStream zip,String name,Object value,List<String> included) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(safeJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();included.add(name);
    }
    private String accountJournal(Path file) throws IOException {
        long bytes=Files.size(file);
        if(bytes>8L*1024*1024) return safeJson(Map.of("omitted","larger than 8 MiB","bytes",bytes));
        try { return safeJson(com.google.gson.JsonParser.parseString(Files.readString(file))); }
        catch(RuntimeException unreadable) { return safeJson(Map.of("unreadable",unreadable.getClass().getSimpleName(),"bytes",bytes)); }
    }
}
