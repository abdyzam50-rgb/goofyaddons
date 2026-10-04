package com.goofy.goofyaddons.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigRegressionTest {
    @TempDir Path directory;

    @Test
    void shippedBothModeExampleIsValid() {
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(Path.of("examples/goofyaddons-both.json"));
        assertEquals(com.goofy.goofyaddons.features.TradingMode.BOTH, GoofyConfig.INSTANCE.tradingMode);
        assertEquals(9, GoofyConfig.INSTANCE.books.size());
        assertEquals(6, GoofyConfig.INSTANCE.general.items.size());
        GoofyConfig.INSTANCE.validate();
    }

    @Test
    void defaultsAndLegacyJsonRemainValid() throws Exception {
        new GoofyConfig().validate();
        Path file = directory.resolve("goofyaddons.json");
        Files.writeString(file, "{\"minActionDelay\":100,\"maxActionDelay\":200}");
        GoofyConfig.load(file);
        assertEquals(1.25, GoofyConfig.INSTANCE.bazaarTaxPercentage);
        assertEquals(2, GoofyConfig.INSTANCE.books.size());
    }

    @Test
    void malformedFileIsPreservedAndLastGoodConfigSurvives() throws Exception {
        GoofyConfig previous = new GoofyConfig();
        GoofyConfig.INSTANCE = previous;
        Path file = directory.resolve("goofyaddons.json");
        Files.writeString(file, "{broken");
        GoofyConfig.load(file);
        assertSame(previous, GoofyConfig.INSTANCE);
        assertEquals("{broken", Files.readString(file));
    }

    @Test
    void invalidDelayBoundsAndNullBooksAreRejected() {
        GoofyConfig config = new GoofyConfig();
        config.maxActionDelay = config.minActionDelay;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.maxActionDelay = 200;
        config.books = null;
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void saveCreatesDirectoriesAndRoundTrips() {
        Path file = directory.resolve("nested/goofyaddons.json");
        GoofyConfig.INSTANCE = new GoofyConfig();
        GoofyConfig.INSTANCE.minNetProfit = 5000;
        GoofyConfig.save(file);
        assertTrue(Files.exists(file));
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        assertEquals(5000, GoofyConfig.INSTANCE.minNetProfit);
    }

    @Test
    void rejectedFirstLoadBlocksTradingUntilAValidConfigLoads() throws Exception {
        Path file = directory.resolve("goofyaddons.json");
        Files.writeString(file, "{\"maxTradingCapital\":-1}");
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        assertNotNull(GoofyConfig.INSTANCE);
        assertNotNull(GoofyConfig.loadError());
        Files.writeString(file, "{\"maxTradingCapital\":1000000}");
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError());
        assertEquals(1000000, GoofyConfig.INSTANCE.maxTradingCapital);
    }

    @Test
    void rejectedReloadKeepsTheLastGoodConfigUsable() throws Exception {
        Path file = directory.resolve("goofyaddons.json");
        Files.writeString(file, "{}");
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        var working=GoofyConfig.INSTANCE;
        assertNull(GoofyConfig.lastLoadProblem());
        Files.writeString(file, "{broken");
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError());
        assertSame(working,GoofyConfig.INSTANCE);
        assertNotNull(GoofyConfig.lastLoadProblem(),"a rejected reload must be reportable even with a working config");
        Files.writeString(file,"{\"marketAnalysis\":{\"enabled\":true,\"dashboardEnabled\":true}}");
        GoofyConfig.load(file);
        assertNull(GoofyConfig.lastLoadProblem());
        assertTrue(GoofyConfig.INSTANCE.marketAnalysis.enabled);
        assertTrue(GoofyConfig.INSTANCE.marketAnalysis.dashboardEnabled);
    }

    @Test
    void aFreshlyWrittenDefaultConfigBlocksTradingUntilItIsReviewed() throws Exception {
        // A field run started in a new config directory, where the mod silently wrote
        // defaults and traded under a 300m capital limit and a 50m reserve the player had
        // not chosen - their own file, with 65m and 15m, was somewhere else entirely.
        Path file = directory.resolve("goofyaddons.json");
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        assertTrue(Files.exists(file), "the defaults are still written out to edit");
        assertNotNull(GoofyConfig.INSTANCE);
        assertNotNull(GoofyConfig.loadError(), "defaults nobody chose must not trade");
        assertTrue(GoofyConfig.loadError().contains(file.toString()),
                "the message must say which file to look at");
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError(), "reloading the reviewed file clears the block");
    }

    @Test
    void theBookEngineLimitsAreValidatedLikeTheGeneralOnes() throws Exception {
        Path file = directory.resolve("goofyaddons.json");
        for (String bad : new String[]{"{\"maxActiveBooks\":0}", "{\"maxActiveBooks\":11}",
                "{\"bookRepriceCooldownSeconds\":29}", "{\"maxBookReprices\":-1}",
                "{\"maxBookReprices\":11}"}) {
            Files.writeString(file, bad);
            GoofyConfig.INSTANCE = null;
            GoofyConfig.load(file);
            assertNotNull(GoofyConfig.loadError(), bad + " should have been rejected");
        }
        Files.writeString(file, "{\"maxActiveBooks\":2,\"bookRepriceCooldownSeconds\":120,\"maxBookReprices\":3}");
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError());
        assertEquals(2, GoofyConfig.INSTANCE.maxActiveBooks);
    }

    @Test
    void theConfigShippedInTheGuidesLoadsAndValidates() throws Exception {
        // A config handed to a player has to survive the real validator, or they paste it in
        // and the mod refuses to trade with no obvious reason why.
        Path source = Path.of("docs", "guides", "goofyaddons.tuned.json");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        Path file = directory.resolve("goofyaddons.json");
        Files.writeString(file, Files.readString(source));
        GoofyConfig.INSTANCE = null;
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError());
        assertEquals(6, GoofyConfig.INSTANCE.books.size());
        assertEquals(2, GoofyConfig.INSTANCE.maxActiveBooks);
        assertEquals(35_000_000, GoofyConfig.INSTANCE.maxTradingCapital);
        assertTrue(GoofyConfig.INSTANCE.maxTradingCapital
                        <= 55_000_000 - GoofyConfig.INSTANCE.purseReserve,
                "the capital limit must bind before the purse does");
    }
    @Test void legacyConfigKeepsMarketAnalysisDisabledAndRoundTripsExplicitShadowSettings() {
        GoofyConfig.INSTANCE = null;
        Path file = directory.resolve("shadow.json");
        try { Files.writeString(file, "{}"); } catch (Exception e) { throw new RuntimeException(e); }
        GoofyConfig.load(file);
        assertFalse(GoofyConfig.INSTANCE.marketAnalysis.enabled);
        GoofyConfig.INSTANCE.marketAnalysis.enabled = true;
        GoofyConfig.INSTANCE.marketAnalysis.maxRecommendations = 5;
        GoofyConfig.save(file);
        GoofyConfig.INSTANCE = null; GoofyConfig.load(file);
        assertTrue(GoofyConfig.INSTANCE.marketAnalysis.enabled);
        assertEquals(5, GoofyConfig.INSTANCE.marketAnalysis.maxRecommendations);
    }

    @Test void nullOrRemoteAnalysisSettingsAreRejected() {
        var c = new GoofyConfig(); c.marketAnalysis = null;
        assertThrows(IllegalArgumentException.class, c::validate);
        c.marketAnalysis = new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings();
        c.marketAnalysis.endpoint = "http://example.com:8789/v1/recommendations";
        assertThrows(IllegalArgumentException.class, c::validate);
    }

}
