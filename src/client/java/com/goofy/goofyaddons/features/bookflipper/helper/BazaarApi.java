package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.goofy.goofyaddons.features.TradingSafety;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class BazaarApi {
    private static final BazaarQuoteCache CACHE=new BazaarQuoteCache();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private BazaarApi() {}

    public static CompletableFuture<JsonObject> fetch() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .timeout(Duration.ofSeconds(15)).GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("Bazaar HTTP " + response.statusCode());
                    }
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!root.has("success") || !root.get("success").getAsBoolean()
                            || !root.has("products") || !root.get("products").isJsonObject()) {
                        throw new IllegalStateException("Invalid Bazaar response");
                    }
                    return CACHE.publish(root,System.currentTimeMillis());
                }).whenComplete((root,failure)->{
                    if(failure!=null) Diagnostics.failure("api.fetch_failed",failure);
                    else Diagnostics.event("INFO","api.fetch_succeeded",java.util.Map.of("products",root.getAsJsonObject("products").size(),"sourceTime",root.get("lastUpdated").getAsLong()));
                });
    }

    public static JsonObject latestFresh() {
        return CACHE.fresh(System.currentTimeMillis());
    }
}
