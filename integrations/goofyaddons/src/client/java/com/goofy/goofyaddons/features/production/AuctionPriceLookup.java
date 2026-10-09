package com.goofy.goofyaddons.features.production;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Local companion price-only lookup; never retrieves an auction ID. */
final class AuctionPriceLookup {
    private static final HttpClient HTTP=com.goofy.goofyaddons.features.companion.LocalCalculatorHttp.create(Duration.ofSeconds(2));
    static CompletableFuture<AuctionPricing.Quote> fetch(String product) {
        var settings=new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings();
        settings.endpoint=com.goofy.goofyaddons.config.GoofyConfig.INSTANCE.marketAnalysis.endpoint;settings.validate();
        var uri=URI.create(settings.endpoint).resolve("/v1/ah/price?item="+java.net.URLEncoder.encode(product,java.nio.charset.StandardCharsets.UTF_8));
        return HTTP.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
            HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(),16384)).thenApply(response->{
                if(response.statusCode()!=200)throw new IllegalArgumentException("Auction price validation service returned HTTP "+response.statusCode());
                return AuctionPricing.parse(JsonParser.parseString(response.body()).getAsJsonObject(),product,System.currentTimeMillis());
            });
    }
}
