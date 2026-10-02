package com.goofy.goofyaddons.config;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.TradingMode;
import com.goofy.goofyaddons.features.generalflipper.GeneralSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public class GoofyConfig {
    public List<Book> books = new ArrayList<>();


    public GoofyConfig() {
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise", 0, 0));
        books.add(new Book("ENCHANTMENT_ULTIMATE_WISE", 2, 5, "Ultimate Wise", 0, 0));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 1, 5, "Wisdom"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_WISDOM", 2, 5, "Wisdom"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 1, 5, "Last Stand"));
        // books.add(new Book("ENCHANTMENT_ULTIMATE_LAST_STAND", 2, 5, "Last Stand"));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("goofyaddons.json");
    }

    public static GoofyConfig INSTANCE;


    public TradingMode tradingMode = TradingMode.BOOKS;
    public int modeKey = GLFW.GLFW_KEY_M;
    public double maxTradingCapital = 300_000_000;
    public double purseReserve = 50_000_000;
    public GeneralSettings general = new GeneralSettings();
    public int startKey = GLFW.GLFW_KEY_J;
    public int stopKey = GLFW.GLFW_KEY_K;
    public boolean speedMode = false;
    public int speedModeDelay = 100;
    public int minActionDelay = 100;
    public int maxActionDelay = 500;
    public double bazaarTaxPercentage = 1.25;
    public double minNetProfit = 0;
    public int maxBookHoldingSeconds = 21600;
    public boolean profitHudEnabled = true;
    public String profitHudSide = "RIGHT";
    public double profitHudScale = 1.25;
    public double maxBookDrawdownPercentage = 15;
    public String firstPage = "ec";
    public String secondPage = "ec 2";


    public static void load() {
        load(configPath());
    }

    static void load(Path path) {
        try {
            if (!Files.exists(path)) {
                INSTANCE = new GoofyConfig();
                save(path);
                return;
            }
            String json = Files.readString(path);
            GoofyConfig parsed = GSON.fromJson(json, GoofyConfig.class);
            if (parsed == null) throw new IllegalArgumentException("Config must be a JSON object");
            parsed.validate();
            INSTANCE = parsed;
        } catch (Exception e) {
            // Preserve both the file and the last working in-memory config.
            System.err.println("GoofyAddons config rejected: " + e.getMessage());
            if (INSTANCE == null) INSTANCE = new GoofyConfig();
        }
    }

    public void validate() {
        if (tradingMode == null || general == null || !Double.isFinite(maxTradingCapital)
                || maxTradingCapital <= 0 || !Double.isFinite(purseReserve) || purseReserve < 0
                || modeKey < 32 || modeKey > GLFW.GLFW_KEY_LAST || modeKey == startKey || modeKey == stopKey) {
            throw new IllegalArgumentException("Invalid mode or shared capital settings");
        }
        general.validate();
        if (!Double.isFinite(profitHudScale) || profitHudScale<0.75 || profitHudScale>3.0) throw new IllegalArgumentException("HUD scale must be between 0.75 and 3.0");
        if (!"LEFT".equals(profitHudSide) && !"RIGHT".equals(profitHudSide)) throw new IllegalArgumentException("HUD side must be LEFT or RIGHT");
        if (minActionDelay < 51 || maxActionDelay <= minActionDelay || maxActionDelay > 60000) {
            throw new IllegalArgumentException("Require 51 <= minActionDelay < maxActionDelay <= 60000");
        }
        if (!Double.isFinite(bazaarTaxPercentage) || bazaarTaxPercentage < 0 || bazaarTaxPercentage >= 100
                || !Double.isFinite(minNetProfit) || minNetProfit < 0 || maxBookHoldingSeconds < 60
                || !Double.isFinite(maxBookDrawdownPercentage) || maxBookDrawdownPercentage <= 0 || maxBookDrawdownPercentage > 100) {
            throw new IllegalArgumentException("Invalid sale tax or minimum net profit");
        }
        if (startKey < 32 || startKey > GLFW.GLFW_KEY_LAST || stopKey < 32
                || stopKey > GLFW.GLFW_KEY_LAST || startKey == stopKey) {
            throw new IllegalArgumentException("Start and stop keys must be distinct valid keys");
        }
        if (firstPage == null || firstPage.isBlank() || secondPage == null || secondPage.isBlank()
                || firstPage.startsWith("/") || secondPage.startsWith("/")) {
            throw new IllegalArgumentException("Storage commands must be nonempty and omit the leading slash");
        }
        if (books == null) throw new IllegalArgumentException("books must be an array");
        Set<String> routes = new HashSet<>();
        for (Book book : books) {
            if (book == null || book.id() == null || !book.id().matches("ENCHANTMENT_[A-Z0-9_]+")
                    || book.name() == null || book.name().isBlank()
                    || book.level() < 1 || book.sellLevel() <= book.level() || book.sellLevel() > 10
                    || !validPercentage(book.instaBuyPercentage()) || !validPercentage(book.instaSellPercentage())) {
                throw new IllegalArgumentException("Invalid book route: " + book);
            }
            if (!routes.add(book.getLevel(book.level()) + ":" + book.sellLevel())) {
                throw new IllegalArgumentException("Duplicate book route: " + book);
            }
        }
    }

    private static boolean validPercentage(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 100;
    }

    public static void save() {
        save(configPath());
    }

    static void save(Path path) {
        Path temporary = null;
        try {
            INSTANCE.validate();
            Files.createDirectories(path.toAbsolutePath().getParent());
            temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "goofyaddons-", ".tmp");
            Files.writeString(temporary, GSON.toJson(INSTANCE));
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("GoofyAddons config save failed: " + e.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            }
        }
    }
}
