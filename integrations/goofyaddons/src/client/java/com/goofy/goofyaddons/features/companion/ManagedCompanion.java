package com.goofy.goofyaddons.features.companion;

import com.google.gson.JsonParser;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * All unpacking, downloads, probes and process waits happen off the Minecraft thread.
 *
 * <p>The supervisor reports an explicit {@link State} beside its status text, and records
 * which configuration revision and bundle each launch belongs to, the last exit code and
 * the tail of the calculator's error log, so a failed start can be diagnosed from one
 * diagnostics export instead of a guess.
 */
public final class ManagedCompanion implements AutoCloseable {
    public enum State { STOPPED, PREPARING, STARTING, READY, EXTERNAL, CONFLICT, FAILED }
    @FunctionalInterface public interface RuntimeResolver {String resolve(Path cache,Consumer<String> status) throws Exception;}
    private final Path data,config;
    private final byte[] bundle;
    private final RuntimeResolver runtime;
    private final Map<String,String> childEnvironment;
    private final java.util.List<String> serverOptions;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Goofy calculator");t.setDaemon(true);return t;});
    private final HttpClient http=LocalCalculatorHttp.create(Duration.ofSeconds(1));
    private volatile boolean enabled,closed,restart;
    private volatile int port=8789;
    private volatile String status="Not started";
    private volatile String sharingStatus="Waiting for calculator";
    private volatile Process owned;
    private FileChannel lockChannel;
    private FileLock lock;
    private int failures;
    private long retryAt;
    private volatile State state=State.STOPPED;
    /** Increments whenever the requested port or auto-start changes. */
    private volatile int revision;
    private volatile int runningRevision=-1,runningPort=-1;
    private volatile Integer lastExitCode;
    private volatile String lastFailure;
    private volatile java.util.List<String> errorTail=java.util.List.of();
    private volatile String externalBundle,runningCommit;
    private volatile Integer runningContract;
    private String bundleId;
    public ManagedCompanion(byte[] bundle,Path data,Path config,RuntimeResolver runtime,Map<String,String> childEnvironment) {
        this(bundle,data,config,runtime,childEnvironment,java.util.List.of());
    }
    ManagedCompanion(byte[] bundle,Path data,Path config,RuntimeResolver runtime,Map<String,String> childEnvironment,java.util.List<String> serverOptions) {
        this.bundle=bundle;this.data=data;this.config=config;this.runtime=runtime;this.childEnvironment=Map.copyOf(childEnvironment);this.serverOptions=java.util.List.copyOf(serverOptions);
    }
    public void startWorker(){worker.scheduleWithFixedDelay(this::reconcile,0,3,TimeUnit.SECONDS);}
    public void configure(boolean enabled,int port) {
        if(this.port!=port){this.port=port;restart=true;revision++;}
        if(this.enabled!=enabled)revision++;
        this.enabled=enabled;
    }
    public State state(){return state;}
    /** The identity of the bundled calculator: the digest its payload folder is named by. */
    public String bundleId(){if(bundleId==null)bundleId=CompanionFiles.digest(bundle).substring(0,16);return bundleId;}
    /** Supervisor facts for a diagnostics export. Never includes keys or settings contents. */
    public Map<String,Object> diagnosticState() {
        var state=new java.util.LinkedHashMap<String,Object>();
        state.put("state",this.state.name());state.put("status",status);
        state.put("enabled",enabled);state.put("desiredPort",port);state.put("runningPort",runningPort);
        state.put("revision",revision);state.put("runningRevision",runningRevision);
        state.put("bundle",bundleId());state.put("externalBundle",externalBundle==null?"unknown":externalBundle);
        state.put("runningCommit",runningCommit==null?"unknown":runningCommit);state.put("runningForecastContract",runningContract==null?"unknown":runningContract);
        state.put("failures",failures);state.put("lastExitCode",lastExitCode==null?"none":lastExitCode);
        state.put("lastFailure",lastFailure==null?"none":lastFailure);state.put("errorLogTail",errorTail);
        return state;
    }
    public void retry(){restart=true;}
    public String status(){return status;}
    public String sharingStatus(){return sharingStatus;}
    public Path dataDirectory(){return data;}
    public Path discordSettings(){return data.resolve("discord-settings.json");}
    public String dashboard(){return "http://127.0.0.1:"+port+"/";}
    private boolean healthy(int port) {
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health")).timeout(Duration.ofSeconds(2)).GET().build();
            var future=http.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),32768));
            try {
                var response=future.get(3,TimeUnit.SECONDS);
                if(response.statusCode()!=200)return false;
                var health=JsonParser.parseString(response.body()).getAsJsonObject();
                if(!health.has("protocol") || !"goofy-bazaar-shadow/1".equals(health.get("protocol").getAsString()))return false;
                externalBundle=health.has("bundle") && health.get("bundle").isJsonPrimitive()?health.get("bundle").getAsString():null;
                runningCommit=health.has("upstreamCommit") && health.get("upstreamCommit").isJsonPrimitive()?health.get("upstreamCommit").getAsString():null;
                runningContract=health.has("forecastContract") && health.get("forecastContract").isJsonPrimitive()?health.get("forecastContract").getAsInt():null;
                if(health.has("community")) {
                    var community=health.getAsJsonObject("community");
                    boolean sharing=community.has("sharingEnabled") && community.get("sharingEnabled").getAsBoolean();
                    int imported=community.has("imported")?community.get("imported").getAsInt():0;
                    int acknowledged=community.has("acknowledged")?community.get("acknowledged").getAsInt():0;
                    // Do not echo arbitrary remote error text into the settings UI.
                    boolean error=community.has("error") && !community.get("error").isJsonNull();
                    sharingStatus=(sharing?"Uploads on · "+acknowledged+" acknowledged":"Uploads off")+" · "+imported+" shared samples"+(error?" · Sync unavailable; retrying":"");
                }
                return true;
            } finally {future.cancel(true);}
        } catch(Exception unavailable){return false;}
    }
    // Package-visible for deterministic tests; production uses the dedicated worker only.
    synchronized void reconcile() {
        if(closed)return;
        try {
            if(restart){restart=false;stopOwned();failures=0;retryAt=0;}
            if(!enabled){stopOwned();failures=0;retryAt=0;status="Auto-start off";state=State.STOPPED;return;}
            if(owned!=null) {
                if(owned.isAlive()) {
                    if(healthy(port)){status="Running · live market collection";state=State.READY;return;}
                    stopOwned();throw new IOException("Calculator stopped responding");
                }
                lastExitCode=owned.exitValue();
                stopOwned();throw new IOException("Calculator exited with code "+lastExitCode+"; check companion-error.log");
            }
            if(healthy(port)) {
                // Another process answers the protocol. Use it, but say when it is not this bundle.
                state=State.EXTERNAL;runningPort=port;
                status=externalBundle==null || externalBundle.equals(bundleId())?"Using existing calculator"
                        :"Using existing calculator · different version ("+externalBundle+"); stop it to use the bundled one";
                return;
            }
            // A non-calculator listener cannot be reused or terminated. Surface
            // the conflict before launching Node and repeatedly hitting EADDRINUSE.
            // Connect rather than bind: recently stopped servers can leave
            // TIME_WAIT sockets even though Node may safely listen again.
            try(var probe=new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1",port),500);
                status="Port "+port+" is occupied by another service. Change Calculator port, then Retry / restart.";
                state=State.CONFLICT;
                return;
            } catch(IOException noListener) { /* Normal when the local port is free. */ }
            if(failures>=3 || System.currentTimeMillis()<retryAt)return;
            Files.createDirectories(data);
            lockChannel=FileChannel.open(data.resolve(".managed-calculator.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            try{lock=lockChannel.tryLock();}catch(OverlappingFileLockException busy){lock=null;}
            if(lock==null){releaseLock();status="Calculator owned by another Minecraft instance";return;}
            status="Preparing bundled calculator";state=State.PREPARING;
            Path payload=CompanionFiles.unpack(bundle,data.resolve("managed/payloads"));
            String node=runtime.resolve(data.resolve("managed/runtime"),value->status=value);
            if(closed || !enabled){releaseLock();return;}
            // Creates missing local pairing files only. Existing keys/settings/history are retained.
            Process pair=process(node,payload.resolve("discord-pair.mjs").toString(),config.toString()).start();
            if(!pair.waitFor(10,TimeUnit.SECONDS)){pair.destroyForcibly();throw new IOException("Discord pairing helper timed out");}
            int launchPort=port,launchRevision=revision;
            var arguments=new java.util.ArrayList<>(java.util.List.of(node,payload.resolve("server.mjs").toString(),Integer.toString(launchPort),"--managed","--bundle="+bundleId()));
            arguments.addAll(serverOptions);
            Process candidate=process(arguments.toArray(String[]::new)).start();
            owned=candidate;state=State.STARTING;lastExitCode=null;
            // Stop on EOF if Minecraft crashes, and gracefully close on normal Minecraft exit.
            for(int attempt=0;attempt<30 && !closed && enabled && candidate.isAlive();attempt++) {
                if(healthy(launchPort)) {
                    if(externalBundle!=null && !externalBundle.equals(bundleId()))throw new IOException("Port "+launchPort+" answered with a different calculator version");
                    status="Running · live market collection";failures=0;state=State.READY;
                    runningPort=launchPort;runningRevision=launchRevision;lastFailure=null;return;
                }
                Thread.sleep(200);
            }
            if(!candidate.isAlive())lastExitCode=candidate.exitValue();
            stopOwned();throw new IOException("Calculator did not start"+(lastExitCode==null?"":" (exit code "+lastExitCode+")")+"; check companion-error.log");
        } catch(Exception failure) {
            stopOwned();failures++;retryAt=System.currentTimeMillis()+30_000;
            state=State.FAILED;runningPort=-1;runningRevision=-1;
            lastFailure=failure.getMessage();errorTail=errorLogTail(data.resolve("companion-error.log"));
            String cause=errorTail.isEmpty()?"":" · "+errorTail.getLast();
            status=(failures>=3?"Stopped after 3 failures · Retry: ":"Retrying in 30s: ")+failure.getMessage()+cause;
        }
    }

    /** What the calculator answering on the port reports about itself; null values are unknown. */
    public String runningBundle(){return externalBundle;}
    public String runningCommit(){return runningCommit;}
    public Integer runningContract(){return runningContract;}
    /** Bounded, redacted tails of both calculator logs, for a diagnostics export. */
    public Map<String,java.util.List<String>> logTails() {
        var tails=new java.util.LinkedHashMap<String,java.util.List<String>>();
        tails.put("companion.log",logTail(data.resolve("companion.log"),40,8192));
        tails.put("companion-error.log",logTail(data.resolve("companion-error.log"),40,8192));
        return tails;
    }
    /** The last lines of the calculator's error log, bounded and stripped of anything key-like. */
    static java.util.List<String> errorLogTail(Path log){return logTail(log,12,4096);}
    static java.util.List<String> logTail(Path log,int maxLines,int maxBytes) {
        try {
            if(!Files.isRegularFile(log))return java.util.List.of();
            long size=Files.size(log);
            try(var channel=FileChannel.open(log,StandardOpenOption.READ)) {
                long start=Math.max(0,size-maxBytes);
                var buffer=java.nio.ByteBuffer.allocate((int)(size-start));
                channel.read(buffer,start);
                var lines=new String(buffer.array(),java.nio.charset.StandardCharsets.UTF_8).lines()
                        .map(String::strip).filter(line->!line.isEmpty()).map(ManagedCompanion::redact).toList();
                return java.util.List.copyOf(lines.subList(Math.max(0,lines.size()-maxLines),lines.size()));
            }
        } catch(IOException unreadable){return java.util.List.of();}
    }
    static String redact(String line) {
        String clean=line.replaceAll("(?i)(token|key|secret|authorization|password)([\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}]+","$1$2[redacted]")
                .replaceAll("[A-Za-z0-9_-]{32,}","[redacted]");
        return clean.length()>300?clean.substring(0,300)+"…":clean;
    }
    private ProcessBuilder process(String... command) throws IOException {
        rotate(data.resolve("companion.log"));rotate(data.resolve("companion-error.log"));
        ProcessBuilder builder=new ProcessBuilder(command);
        builder.environment().putAll(childEnvironment);
        builder.environment().put("GOOFY_BAZAAR_DATA_DIR",data.toString());
        // The runtime must not inherit options that change Node's entrypoint or preload code.
        builder.environment().remove("NODE_OPTIONS");
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(data.resolve("companion.log").toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(data.resolve("companion-error.log").toFile()));
        return builder;
    }
    private static void rotate(Path log) throws IOException {
        if(Files.isRegularFile(log) && Files.size(log)>2L*1024*1024)Files.move(log,log.resolveSibling(log.getFileName()+".previous"),StandardCopyOption.REPLACE_EXISTING);
    }
    private void stopOwned() {
        Process process=owned;owned=null;
        if(process!=null) {
            try {process.getOutputStream().close();if(!process.waitFor(25,TimeUnit.SECONDS)){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();}}
            catch(Exception stopped){process.destroyForcibly();}
        }
        releaseLock();
    }
    private void releaseLock() {
        try{if(lock!=null)lock.close();}catch(IOException ignored){}lock=null;
        try{if(lockChannel!=null)lockChannel.close();}catch(IOException ignored){}lockChannel=null;
    }
    @Override public void close() {
        closed=true;enabled=false;state=State.STOPPED;
        // Close the pipe before taking the worker lock; this also interrupts a pending startup.
        Process process=owned;if(process!=null)try{process.getOutputStream().close();}catch(IOException ignored){}
        worker.shutdownNow();
        synchronized(this){stopOwned();}
        status="Stopped";
    }
}
