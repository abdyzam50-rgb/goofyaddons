package com.goofy.goofyaddons.config;

import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.TradingMode;
import com.goofy.goofyaddons.features.generalflipper.GeneralSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;

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
    /** Set while trading would run on built-in defaults because the user's file was rejected. */
    private static String loadError;
    private static String lastLoadProblem;


    public com.goofy.goofyaddons.features.sessions.RestScheduleSettings restSchedule = new com.goofy.goofyaddons.features.sessions.RestScheduleSettings();
    public com.goofy.goofyaddons.features.access.AccessSettings access = new com.goofy.goofyaddons.features.access.AccessSettings();
    public com.goofy.goofyaddons.features.discord.DiscordSettings discord = new com.goofy.goofyaddons.features.discord.DiscordSettings();
    public TradingMode tradingMode = TradingMode.BOOKS;
    public int keyCodeSchema = 2;
    public int modeKey = InputConstants.KEY_F7;
    public double maxTradingCapital = 300_000_000;
    public double purseReserve = 50_000_000;
    public GeneralSettings general = new GeneralSettings();
    public com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings marketAnalysis = new com.goofy.goofyaddons.features.marketanalysis.MarketAnalysisSettings();
    public int toggleKey = InputConstants.KEY_F6;
    public int reloadKey = InputConstants.KEY_F8;
    public int minActionDelay = 100;
    public int maxActionDelay = 500;
    public double bazaarTaxPercentage = 1.25;
    public double minNetProfit = 0;
    public int maxBookHoldingSeconds = 21600;
    /** How long a placed book order may sit untouched before it is re-read. */
    public int bookOrderRecheckSeconds = 180;
    /**
     * Book-engine counterparts of general.maxActiveItems, general.repriceCooldownSeconds and
     * general.maxReprices. Without them the book engine opened one order per eligible route at
     * once (committing far more than the purse could cover) and re-placed an outbid order
     * immediately and forever.
     */
    public int maxActiveBooks = 2;
    public boolean liquidateStaleBooks = true;
    public int bookStaleSeconds = 900;
    public int bookRepriceCooldownSeconds = 120;
    public int maxBookReprices = 3;
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
                // A fresh file is written with built-in defaults, including a 300m capital
                // limit and a 50m purse reserve nobody chose. A field run started on exactly
                // this path - a new config directory, a silently created default file - and
                // traded under limits that did not match the ones the player had set
                // elsewhere. Defaults are a starting point to review, not a mandate, so
                // trading stays blocked until the file is read and reloaded.
                INSTANCE = new GoofyConfig();
                save(path);
                loadError = "Wrote a new goofyaddons.json with built-in defaults (capital "
                        + (long) INSTANCE.maxTradingCapital + ", reserve " + (long) INSTANCE.purseReserve
                        + "). Review it at " + path + " and use Reload in A* Macros settings before trading.";
                lastLoadProblem=loadError;
                Diagnostics.event("WARN","config.defaults_written",java.util.Map.of("path",path.toString(),
                        "capital",INSTANCE.maxTradingCapital,"reserve",INSTANCE.purseReserve));
                return;
            }
            String json = Files.readString(path);
            GoofyConfig parsed = GSON.fromJson(json, GoofyConfig.class);
            if (parsed == null) throw new IllegalArgumentException("Config must be a JSON object");
            var root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            boolean legacyCodes=!root.has("keyCodeSchema") || root.get("keyCodeSchema").getAsInt()==1;
            if(legacyCodes) {
                if(root.has("toggleKey"))parsed.toggleKey=LegacyKeys.fromGlfw(parsed.toggleKey);
                if(root.has("reloadKey"))parsed.reloadKey=LegacyKeys.fromGlfw(parsed.reloadKey);
                if(root.has("modeKey"))parsed.modeKey=LegacyKeys.fromGlfw(parsed.modeKey);
                parsed.keyCodeSchema=2;
            }
            if(!root.has("toggleKey")) {
                // Replace old defaults; retain a deliberately customized start key as the toggle.
                if(root.has("startKey")) {
                    int oldStart=root.get("startKey").getAsInt();
                    if(legacyCodes)oldStart=LegacyKeys.fromGlfw(oldStart);
                    if(oldStart!=InputConstants.KEY_J)parsed.toggleKey=oldStart;
                }
                if(parsed.modeKey==InputConstants.KEY_M)parsed.modeKey=InputConstants.KEY_F7;
            }
            parsed.validate();
            INSTANCE = parsed;
            loadError = null;
            lastLoadProblem = null;
            Diagnostics.event("INFO","config.loaded",java.util.Map.of("mode",parsed.tradingMode.name(),"capital",parsed.maxTradingCapital,"reserve",parsed.purseReserve));
        } catch (Exception e) {
            lastLoadProblem="Config rejected: "+e.getMessage();
            Diagnostics.failure("config.load_failed",e);
            // Preserve both the file and the last working in-memory config.
            System.err.println("GoofyAddons config rejected: " + e.getMessage());
            if (INSTANCE == null) {
                // Never trade with default capital limits the user did not choose.
                INSTANCE = new GoofyConfig();
                loadError = "Config file rejected (" + e.getMessage() + "); fix goofyaddons.json and reload.";
            }
        }
    }

    /** Non-null when the config file was rejected and no valid config has been loaded yet. */
    /** Latest read outcome, including failed reloads that retained a working config. */
    public static String lastLoadProblem() { return lastLoadProblem; }
    public static String location() { return configPath().toAbsolutePath().toString(); }

    public static String loadError() {
        return loadError;
    }

    public void validate() {
        if (keyCodeSchema!=2 || tradingMode == null || general == null || !Double.isFinite(maxTradingCapital)
                || maxTradingCapital <= 0 || !Double.isFinite(purseReserve) || purseReserve < 0
                || modeKey < 4 || modeKey > 511 || modeKey == toggleKey || modeKey == reloadKey) {
            throw new IllegalArgumentException("Invalid mode or shared capital settings");
        }
        if(discord==null)throw new IllegalArgumentException("discord must be an object");
        general.validate();
        if(restSchedule==null)throw new IllegalArgumentException("restSchedule must be an object");
        restSchedule.validate();
        if(access==null)throw new IllegalArgumentException("access must be an object");
        access.validate();
        if (marketAnalysis == null) throw new IllegalArgumentException("marketAnalysis must be an object");
        marketAnalysis.validate();
        if (!Double.isFinite(profitHudScale) || profitHudScale<0.75 || profitHudScale>3.0) throw new IllegalArgumentException("HUD scale must be between 0.75 and 3.0");
        if (!"LEFT".equals(profitHudSide) && !"RIGHT".equals(profitHudSide)) throw new IllegalArgumentException("HUD side must be LEFT or RIGHT");
        if (minActionDelay < 51 || maxActionDelay <= minActionDelay || maxActionDelay > 60000) {
            throw new IllegalArgumentException("Require 51 <= minActionDelay < maxActionDelay <= 60000");
        }
        if (!Double.isFinite(bazaarTaxPercentage) || bazaarTaxPercentage < 0 || bazaarTaxPercentage >= 100
                || !Double.isFinite(minNetProfit) || minNetProfit < 0 || maxBookHoldingSeconds < 60
                || bookOrderRecheckSeconds < 30
                || bookStaleSeconds<60 || bookStaleSeconds>86400
                || maxActiveBooks < 1 || maxActiveBooks > 10
                || bookRepriceCooldownSeconds < 30 || maxBookReprices < 0 || maxBookReprices > 10
                || !Double.isFinite(maxBookDrawdownPercentage) || maxBookDrawdownPercentage <= 0 || maxBookDrawdownPercentage > 100) {
            throw new IllegalArgumentException("Invalid sale tax or minimum net profit");
        }
        if (toggleKey < 4 || toggleKey > 511 || reloadKey < 4
                || reloadKey > 511 || toggleKey == reloadKey) {
            throw new IllegalArgumentException("Toggle, mode and reload keys must be distinct valid keys");
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

    /** GUI changes commit atomically before replacing the last working in-memory settings. */
    public static void commitSettings(GoofyConfig candidate)throws java.io.IOException {
        if(!com.goofy.goofyaddons.features.FeatureManager.INSTANCE.canReloadConfig())throw new IllegalStateException("Stop trading before editing settings");
        commitSettings(candidate,configPath());
    }
    static void commitSettings(GoofyConfig candidate,Path path)throws java.io.IOException {
        candidate.validate();
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary=Files.createTempFile(path.toAbsolutePath().getParent(),"goofyaddons-",".tmp");
        try {
            Files.writeString(temporary,GSON.toJson(candidate));
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            INSTANCE=candidate;loadError=null;lastLoadProblem=null;
        }finally{Files.deleteIfExists(temporary);}
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
            Diagnostics.failure("config.save_failed",e);
            System.err.println("GoofyAddons config save failed: " + e.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            }
        }
    }
}
