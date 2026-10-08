package com.goofy.goofyaddons.diagnostics;

import com.goofy.goofyaddons.features.release.ReleaseManifest;
import net.fabricmc.loader.api.FabricLoader;
import java.util.List;

/** Gathers this install's component versions and what the running calculator reports. */
public final class ReleaseInfo {
    private static volatile String bundledCommit;
    private ReleaseInfo() {}

    public static ReleaseManifest manifest() {
        var builder = ReleaseManifest.builder();
        for (String id : List.of("goofyaddons", "minecraft", "fabricloader", "fabric-api"))
            builder.component(id, FabricLoader.getInstance().getModContainer(id).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null));
        builder.component("java", System.getProperty("java.version"));
        builder.component("calculator.protocol", com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisProtocol.VERSION);
        builder.component("storage.layout", com.goofy.goofyaddons.storage.ScopedStorage.LAYOUT_VERSION);
        builder.component("production.journal", 1);
        var config = com.goofy.goofyaddons.config.GoofyConfig.INSTANCE;
        builder.component("config.keyCodeSchema", config == null ? null : config.keyCodeSchema);
        var companion = com.goofy.goofyaddons.features.companion.BundledCalculator.companion();
        String bundle = companion == null ? null : companion.bundleId();
        builder.component("calculator.bundle", bundle);
        builder.expect("calculator.bundle", bundle, companion == null ? null : companion.runningBundle());
        builder.expect("calculator.upstreamCommit", bundledCommit(), companion == null ? null : companion.runningCommit());
        builder.expect("calculator.forecastContract", ReleaseManifest.FORECAST_CONTRACT, companion == null ? null : companion.runningContract());
        return builder.build();
    }

    /** The upstream calculator commit packaged with this mod, from its provenance file. */
    static String bundledCommit() {
        if (bundledCommit != null) return bundledCommit;
        try (var in = ReleaseInfo.class.getResourceAsStream("/goofyaddons/provenance.json")) {
            if (in == null) return null;
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            bundledCommit = root.has("commit") ? root.get("commit").getAsString() : null;
            return bundledCommit;
        } catch (Exception unreadable) { return null; }
    }
}
