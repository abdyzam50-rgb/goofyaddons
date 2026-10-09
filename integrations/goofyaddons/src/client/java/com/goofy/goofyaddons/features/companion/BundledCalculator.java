package com.goofy.goofyaddons.features.companion;

import com.goofy.goofyaddons.config.GoofyConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.nio.file.Path;

/** Minecraft adapter: starting the calculator never starts trading or publishes the account. */
public final class BundledCalculator {
    private static ManagedCompanion companion;
    private static String unavailable="Not initialized";
    private static volatile ContributorSettings.View contributor=ContributorSettings.defaults();
    private static volatile String contributorNote="Loading private sharing settings";
    private static volatile boolean saving;

    private BundledCalculator() {}
    public static void register() {
        try(var resource=BundledCalculator.class.getResourceAsStream("/goofyaddons/calculator.zip")) {
            if(resource==null)throw new IllegalStateException("Bundled calculator resource missing");
            Path data=CompanionFiles.dataDirectory(System.getProperty("os.name"),System.getenv(),Path.of(System.getProperty("user.home")));
            companion=new ManagedCompanion(resource.readAllBytes(),data,FabricLoader.getInstance().getConfigDir(),NodeRuntime::resolve,java.util.Map.of());
            // The bounded private settings file is loaded before any settings screen can
            // build drafts, so an old enabled/custom endpoint cannot become a default draft.
            try{contributor=ContributorSettings.load(data);contributorNote="Blank key keeps your saved key. Keys are never shown or exported.";}
            catch(Exception failure){contributorNote=failure.getMessage();}
            configure();companion.startWorker();
            ClientTickEvents.END_CLIENT_TICK.register(client->configure());
            ClientLifecycleEvents.CLIENT_STOPPING.register(client->companion.close());
            Runtime.getRuntime().addShutdownHook(new Thread(()->companion.close(),"Goofy calculator shutdown"));
        } catch(Exception failure){unavailable="Calculator unavailable: "+failure.getMessage();}
    }
    private static void configure() {
        var settings=GoofyConfig.INSTANCE.marketAnalysis;
        companion.configure(settings.autoStartCompanion,URI.create(settings.endpoint).getPort());
    }
    public static ContributorSettings.View contributor(){return contributor;}
    public static String contributorNote(){return contributorNote;}
    public static boolean savingContributor(){return saving;}
    public static String sharingStatus(){return companion==null?unavailable:companion.sharingStatus();}
    public static void saveContributor(String endpoint,String repository,String key,boolean enabled,boolean forgetKey) {
        if(companion==null || saving)return;
        saving=true;contributorNote="Saving private sharing settings";
        java.util.concurrent.CompletableFuture.runAsync(()->{
            try {
                contributor=ContributorSettings.save(companion.dataDirectory(),endpoint,repository,key,enabled,forgetKey);
                boolean external=!GoofyConfig.INSTANCE.marketAnalysis.autoStartCompanion || companion.state()==ManagedCompanion.State.EXTERNAL;
                companion.retry();
                contributorNote=external?"Saved. Restart your separately launched calculator to apply changes.":"Saved. The bundled calculator will restart to apply changes.";
            }catch(Exception failure){contributorNote=failure.getMessage();}
            finally{saving=false;}
        });
    }
    public static String status(){return companion==null?unavailable:companion.status();}
    public static java.util.Map<String,java.util.List<String>> logTails(){return companion==null?java.util.Map.of():companion.logTails();}
    public static ManagedCompanion companion(){return companion;}
    public static java.util.Map<String,Object> diagnosticState(){return companion==null?java.util.Map.of("state","UNAVAILABLE","status",unavailable):companion.diagnosticState();}
    public static void retry(){if(companion!=null)companion.retry();}
    public static void openDashboard(){if(companion!=null)open(companion.dashboard());}
    public static boolean discordSettingsReady(){return companion!=null && java.nio.file.Files.isRegularFile(companion.discordSettings());}
    public static void openDiscordSettings(){if(discordSettingsReady())open(companion.discordSettings().toUri().toString());}
    private static void open(String uri){com.mojang.blaze3d.Blaze3D.openUri(URI.create(uri));}
}
