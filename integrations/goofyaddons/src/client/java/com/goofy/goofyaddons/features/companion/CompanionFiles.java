package com.goofy.goofyaddons.features.companion;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** User observations and credentials never live in the replaceable mod/payload. */
public final class CompanionFiles {
    private CompanionFiles() {}
    public static Path dataDirectory(String os, Map<String,String> env, Path home) {
        String override=env.get("GOOFY_BAZAAR_DATA_DIR");
        if(override!=null && !override.isEmpty()) {
            Path path=Path.of(override);
            if(!path.isAbsolute())throw new IllegalArgumentException("GOOFY_BAZAAR_DATA_DIR must be absolute");
            return path.normalize();
        }
        Path base;
        if(os.toLowerCase(Locale.ROOT).contains("win") && !os.toLowerCase(Locale.ROOT).contains("darwin"))base=Path.of(env.getOrDefault("LOCALAPPDATA",home.resolve("AppData/Local").toString()));
        else if(os.toLowerCase(Locale.ROOT).contains("mac") || os.toLowerCase(Locale.ROOT).contains("darwin"))base=home.resolve("Library/Application Support");
        else {
            String xdg=env.get("XDG_DATA_HOME");
            base=xdg!=null && Path.of(xdg).isAbsolute()?Path.of(xdg):home.resolve(".local/share");
        }
        return base.resolve("GoofyAddons/bazaar-calc");
    }
    static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static Path unpack(byte[] zip, Path cache) throws IOException {
        Files.createDirectories(cache);
        Path target=cache.resolve(digest(zip));
        if(Files.isRegularFile(target.resolve("server.mjs")))return target;
        Path staging=Files.createTempDirectory(cache,"extract-");
        try {
            extractZip(zip,staging);
            if(!Files.isRegularFile(staging.resolve("server.mjs")))throw new IOException("Calculator bundle has no server");
            Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {if(Files.exists(staging))try(var files=Files.walk(staging)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    static void extractZip(byte[] bytes,Path directory) throws IOException {
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;long total=0;
            while((entry=zip.getNextEntry())!=null) {
                Path path=directory.resolve(entry.getName()).normalize();
                if(!path.startsWith(directory) || entry.getName().contains("\\"))throw new IOException("Unsafe calculator resource path");
                if(entry.isDirectory()){Files.createDirectories(path);continue;}
                Files.createDirectories(path.getParent());
                try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[16384];int count;
                    while((count=zip.read(buffer))!=-1){total+=count;if(total>64L*1024*1024)throw new IOException("Calculator bundle too large");out.write(buffer,0,count);}
                }
            }
        }
    }
}
