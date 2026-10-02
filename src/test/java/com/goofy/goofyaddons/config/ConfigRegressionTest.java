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
        Files.writeString(file, "{broken");
        GoofyConfig.load(file);
        assertNull(GoofyConfig.loadError());
    }
}
