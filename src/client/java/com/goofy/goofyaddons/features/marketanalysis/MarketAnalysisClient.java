package com.goofy.goofyaddons.features.marketanalysis;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** One bounded asynchronous request to the local calculator. No trading API or game actions. */
public final class MarketAnalysisClient {
    @FunctionalInterface public interface Transport { CompletableFuture<JsonObject> request(String endpoint, JsonObject body); }
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public CompletableFuture<JsonObject> request(String endpoint, JsonObject body) {
        var settings = new MarketAnalysisSettings(); settings.endpoint=endpoint; settings.validate();
        String payload=body.toString();
        if(payload.length()>10*1024*1024) return CompletableFuture.failedFuture(new IllegalArgumentException("Market snapshot exceeds bridge limit"));
        HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").header("X-Goofy-Analysis","shadow-v1")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        return client.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),256*1024))
                .thenApply(response->{
                    if(response.statusCode()!=200) throw new IllegalStateException("Calculator HTTP " + response.statusCode());
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }
    public CompletableFuture<JsonObject> publishDashboard(String endpoint, JsonObject body) {
        var settings=new MarketAnalysisSettings();settings.endpoint=endpoint;settings.validate();
        String payload=body.toString();
        if(payload.length()>1024*1024)return CompletableFuture.failedFuture(new IllegalArgumentException("Dashboard snapshot too large"));
        var request=HttpRequest.newBuilder(URI.create(endpoint).resolve("/v1/account"))
                .timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").header("X-Goofy-Dashboard","local-v1")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        return client.sendAsync(request,HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),4096))
                .thenApply(response->{
                    if(response.statusCode()!=200)throw new IllegalStateException("Dashboard HTTP "+response.statusCode());
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }

}
