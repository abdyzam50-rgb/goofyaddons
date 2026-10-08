package com.goofy.goofyaddons.features.companion;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/** Official Node 24.14.1, SHA-256 pins from nodejs.org/dist/v24.14.1/SHASUMS256.txt.
 * Uses an existing Node >=22 when available; otherwise installs a private runtime only.
 * No shell, npm, system installer, administrator access or PATH changes. */
public final class NodeRuntime {
    public static final String VERSION="24.14.1";
    record Artifact(String name,String sha256,String member) {}
    static Artifact artifact(String os,String arch) {
        String lower=os.toLowerCase(Locale.ROOT);
        String cpu=switch(arch.toLowerCase(Locale.ROOT)){case "amd64","x86_64","x64"->"x64";case "aarch64","arm64"->"arm64";default->throw new IllegalArgumentException("Unsupported calculator architecture: "+arch);};
        // Darwin contains 'win'; test macOS first.
        String platform=lower.contains("mac") || lower.contains("darwin")?"darwin":lower.contains("win")?"win":lower.contains("linux")?"linux":null;
        if(platform==null)throw new IllegalArgumentException("Unsupported calculator operating system: "+os);
        String hash=switch(platform+"-"+cpu) {
            case "win-x64"->"58e74bf02fc5bbacc41dcb8bef089961cd5bddd37830b87784e4fc624d145d1f";
            case "win-arm64"->"557ba2ad04fd08464edc2ee3e399b58ff11eaba35a00bb05671661557dc6f79e";
            case "linux-x64"->"ace9fa104992ed0829642629c46ca7bd7fd6e76278cb96c958c4b387d29658ea";
            case "linux-arm64"->"734ff04fa7f8ed2e8a78d40cacf5ac3fc4515dac2858757cbab313eb483ba8a2";
            case "darwin-x64"->"2526230ad7d922be82d4fdb1e7ee1e84303e133e3b4b0ec4c2897ab31de0253d";
            case "darwin-arm64"->"25495ff85bd89e2d8a24d88566d7e2f827c6b0d3d872b2cebf75371f93fcb1fe";
            default->throw new IllegalStateException();
        };
        String folder="node-v"+VERSION+"-"+platform+"-"+cpu;
        return platform.equals("win")?new Artifact(platform+"-"+cpu+"/node.exe",hash,null):new Artifact(folder+".tar.gz",hash,folder+"/bin/node");
    }
    public static String resolve(Path directory,Consumer<String> status) throws Exception {
        String os=System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String executable=os.contains("win") && !os.contains("darwin")?"node.exe":"node";
        if(usable(executable))return executable;
        Artifact artifact=artifact(System.getProperty("os.name"),System.getProperty("os.arch"));
        Files.createDirectories(directory);
        Path target=directory.resolve("node-"+VERSION+"-"+System.getProperty("os.arch")+(artifact.member()==null?".exe":""));
        if(Files.isRegularFile(target) && usable(target.toString()))return target.toString();
        status.accept("Downloading calculator runtime (first launch)");
        Path temporary=Files.createTempFile(directory,"download-",".tmp");
        Path binary=Files.createTempFile(directory,"runtime-",".tmp");
        try {
            var request=HttpRequest.newBuilder(URI.create("https://nodejs.org/dist/v"+VERSION+"/"+artifact.name())).timeout(Duration.ofSeconds(180)).GET().build();
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            var future=client.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofFile(temporary),128L*1024*1024));
            try {
                var response=future.get(185,TimeUnit.SECONDS);
                if(response.statusCode()!=200)throw new IOException("Node download HTTP "+response.statusCode());
            } finally {future.cancel(true);}
            verify(Files.readAllBytes(temporary),artifact.sha256());
            if(artifact.member()==null)Files.copy(temporary,binary,StandardCopyOption.REPLACE_EXISTING);
            else try(var archive=new GZIPInputStream(Files.newInputStream(temporary));var output=Files.newOutputStream(binary)){extractTar(archive,artifact.member(),output);}
            if(!binary.toFile().setExecutable(true,true))throw new IOException("Cannot make calculator runtime executable");
            if(!usable(binary.toString()))throw new IOException("Downloaded Node runtime cannot run on this system");
            Files.move(binary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            return target.toString();
        } finally {Files.deleteIfExists(temporary);Files.deleteIfExists(binary);}
    }
    static void verify(byte[] archive,String hash) throws IOException {
        if(!CompanionFiles.digest(archive).equals(hash))throw new IOException("Node runtime checksum mismatch; download rejected");
    }
    static boolean usable(String executable) {
        Process process=null;
        try {
            process=new ProcessBuilder(executable,"--version").redirectErrorStream(true).start();
            if(!process.waitFor(5,TimeUnit.SECONDS))return false;
            String version=new String(process.getInputStream().readNBytes(128),StandardCharsets.UTF_8).trim();
            return process.exitValue()==0 && version.matches("v\\d+\\.\\d+\\.\\d+") && Integer.parseInt(version.substring(1,version.indexOf('.')))>=22;
        } catch(Exception unavailable){return false;}
        finally {if(process!=null && process.isAlive())process.destroyForcibly();}
    }
    // Read only the exact regular-file node binary; never extract archive paths/symlinks.
    static void extractTar(InputStream tar,String member,OutputStream output) throws IOException {
        byte[] header=new byte[512];long total=0;
        while(true) {
            if(tar.readNBytes(header,0,512)!=512)throw new IOException("Node binary missing from archive");
            String name=new String(header,0,100,StandardCharsets.US_ASCII).split("\0",2)[0];
            if(name.isEmpty())throw new IOException("Node binary missing from archive");
            long size;
            try{size=Long.parseLong(new String(header,124,12,StandardCharsets.US_ASCII).replace("\0","").trim(),8);}
            catch(NumberFormatException invalid){throw new IOException("Invalid Node archive",invalid);}
            total+=size;if(size<0 || total>512L*1024*1024)throw new IOException("Node archive too large");
            if(name.equals(member)) {
                if(header[156]!='0' && header[156]!=0)throw new IOException("Node binary is not a regular file");
                if(size>128L*1024*1024)throw new IOException("Node binary too large");
                byte[] buffer=new byte[16384];long remaining=size;
                while(remaining>0){int count=tar.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw new EOFException();output.write(buffer,0,count);remaining-=count;}
                return;
            }
            tar.skipNBytes(size+(512-size%512)%512);
        }
    }
}
