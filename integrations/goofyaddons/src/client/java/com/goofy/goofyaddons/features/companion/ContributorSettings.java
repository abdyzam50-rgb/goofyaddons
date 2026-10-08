package com.goofy.goofyaddons.features.companion;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Private companion settings. The public view never returns an upload credential. */
public final class ContributorSettings {
    public static final String DEFAULT_ENDPOINT="https://goofy-gameplay-collector.abdyzam50.workers.dev";
    public static final String DEFAULT_REPOSITORY="abdyzam50-rgb/goofyaddons";
    public record View(String endpoint,String repository,boolean enabled,boolean hasKey) {}
    public static View defaults(){return new View(DEFAULT_ENDPOINT,DEFAULT_REPOSITORY,false,false);}
    private static JsonObject read(Path file) throws IOException {
        if(!Files.exists(file))return new JsonObject();
        try(var input=Files.newInputStream(file)) {
            byte[] bytes=input.readNBytes(16385);if(bytes.length>16384)throw new IOException();
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception invalid){throw new IOException("Private sharing settings are invalid; original preserved");}
    }
    private static String string(JsonObject object,String name,String fallback) throws IOException {
        if(!object.has(name))return fallback;
        var value=object.get(name);
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IOException("Private sharing settings are invalid; original preserved");
        return value.getAsString();
    }
    public static View load(Path directory) throws IOException {
        JsonObject settings=read(directory.resolve("community-settings.json"));
        boolean enabled=false;
        if(settings.has("sharingEnabled")) {
            var value=settings.get("sharingEnabled");
            if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IOException("Private sharing settings are invalid; original preserved");
            enabled=value.getAsBoolean();
        }
        return new View(string(settings,"endpoint",DEFAULT_ENDPOINT),string(settings,"repository",DEFAULT_REPOSITORY),enabled,!string(settings,"token","").isEmpty());
    }
    static String endpoint(String value) {
        try {
            var uri=URI.create(value.trim());
            boolean local=java.util.Set.of("127.0.0.1","localhost").contains(uri.getHost());
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))
                    || uri.getPort()>65535 || uri.getPort()==0)throw new IllegalArgumentException();
            return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),null,null,null).toString();
        } catch(Exception invalid){throw new IllegalArgumentException("Enter the collector's HTTPS address without a path, query or credentials");}
    }
    public static View save(Path directory,String endpoint,String repository,String replacementKey,boolean enabled,boolean forgetKey) throws IOException {
        String address=endpoint(endpoint);
        if(repository==null || !repository.trim().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Enter the public dataset repository as owner/repo");
        // Parse and preserve the existing file before touching any settings.
        JsonObject settings=read(directory.resolve("community-settings.json"));
        load(directory);
        String token=forgetKey?"":replacementKey.trim();
        if(!forgetKey && token.isEmpty())token=string(settings,"token","");
        if(!token.isEmpty() && !token.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalArgumentException("Contributor keys must contain 32–128 letters, numbers, hyphens or underscores");
        if(enabled && token.isEmpty())throw new IllegalArgumentException("Add a contributor key before enabling uploads");
        settings.addProperty("endpoint",address);settings.addProperty("repository",repository.trim());
        settings.addProperty("token",token);settings.addProperty("sharingEnabled",enabled);
        Files.createDirectories(directory);
        Path temp=Files.createTempFile(directory,".community-settings-",".tmp");
        try {
            if(Files.getFileStore(temp).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(settings)+"\n");
            Files.move(temp,directory.resolve("community-settings.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
        return load(directory);
    }
}
