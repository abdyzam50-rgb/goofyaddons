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
    private long sequence;
    public DiagnosticLog(Path directory,long maxBytes,int archives) {
        if (maxBytes<1 || archives<1) throw new IllegalArgumentException("Invalid retention");
        this.directory=directory; this.maxBytes=maxBytes; this.archives=archives;
    }
    public static String redact(String text) {
        if (text==null) return "";
        String home=System.getProperty("user.home","");
        if (!home.isBlank()) text=text.replace(home,"<home>");
        text=text.replaceAll("§.","")
                .replaceAll("(?im)^(?:By|Created by|Order by|Seller|Buyer|Owner|Placed by|Co-op member):.*$","<player redacted>")
                .replaceAll("(?m)^-\\s*[\\d,]+x\\s+.*$","<vendor redacted>");
        return text.replaceAll("(?i)(authorization|access[_-]?token|api[_-]?key|password|sessionid)([\\s=:]+)[^\\s,;]+","$1$2<redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+","Bearer <redacted>")
                .replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}","<email>");
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
        Files.createDirectories(directory);
        Path current=directory.resolve("events.jsonl");
        var event=new LinkedHashMap<String,Object>();
        event.put("schema",1);event.put("session",session);event.put("sequence",++sequence);
        event.put("time",Instant.now().toString());event.put("level",level);event.put("event",type);event.put("data",data);
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
            for(int i=0;i<=archives;i++) {
                Path file=directory.resolve(i==0?"events.jsonl":"events."+i+".jsonl");
                if(!Files.exists(file)) continue;
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));Files.copy(file,zip);zip.closeEntry();
            }
        } catch(IOException failed) { Files.deleteIfExists(target);throw failed; }
        return target;
    }
}
