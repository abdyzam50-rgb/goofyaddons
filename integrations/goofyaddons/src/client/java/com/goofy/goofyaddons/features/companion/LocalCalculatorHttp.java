package com.goofy.goofyaddons.features.companion;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/** For validated loopback calculator URLs only; public API traffic keeps its normal proxy policy. */
public final class LocalCalculatorHttp {
    private LocalCalculatorHttp() {}
    public static HttpClient create(Duration timeout) {
        return HttpClient.newBuilder().connectTimeout(timeout)
                .proxy(ProxySelector.of(null)).version(HttpClient.Version.HTTP_1_1).build();
    }
}
