package com.goofy.goofyaddons.features.marketanalysis;

import java.net.URI;

/** Shadow analysis only. These settings cannot enable trade execution or replace ownership records. */
public final class MarketAnalysisSettings {
    public boolean enabled = false;
    /** Opt-in local account dashboard; independent of recommendation requests. */
    public boolean dashboardEnabled = false;
    public String endpoint = "http://127.0.0.1:8789/v1/recommendations";
    public int refreshSeconds = 20;
    public int maxHistoryAgeHours = 48;
    public int maxRecommendations = 10;

    public void validate() {
        if (endpoint == null) throw new IllegalArgumentException("Calculator endpoint is missing");
        URI uri = URI.create(endpoint);
        if (!"http".equals(uri.getScheme()) || !java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !"/v1/recommendations".equals(uri.getPath()) || uri.getPort() < 1024 || uri.getPort() > 65535
                || refreshSeconds < 20 || refreshSeconds > 600 || maxHistoryAgeHours < 1 || maxHistoryAgeHours > 168
                || maxRecommendations < 1 || maxRecommendations > 50)
            throw new IllegalArgumentException("Invalid local shadow-analysis settings");
    }
}
