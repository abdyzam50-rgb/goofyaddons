package com.goofy.goofyaddons.features.account;

import com.goofy.goofyaddons.storage.AccountScope;
import com.goofy.goofyaddons.storage.ScopedStorage;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which profile's files trading reads and writes.
 *
 * <p>The player is observed from the game and the profile from the server's join message.
 * Trading may start only once both are known. The first start pins that profile for the
 * rest of the game session: every repository then resolves its file under the pinned profile,
 * so positions loaded for one profile can never be saved into another's folder. Starting on a
 * different profile afterwards is refused until the game restarts.
 */
public final class AccountStorage {
    public static final String BOOK_ORDERS = "goofyaddons-book-orders.json";
    public static final String GENERAL_ORDERS = "goofyaddons-general-orders.json";
    public static final String PROFIT = "goofyaddons-profit.json";
    public static final String EXECUTION = "goofyaddons-execution.json";
    public static final String TRANSACTIONS = "goofyaddons-transactions.jsonl";
    public static final String PRODUCTION_JOBS = "goofyaddons-production-jobs.json";
    public static final List<String> FILES = List.of(BOOK_ORDERS, GENERAL_ORDERS, PROFIT, EXECUTION, TRANSACTIONS, PRODUCTION_JOBS);

    public static final AccountStorage INSTANCE = new AccountStorage(
            () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());

    private static final Pattern PROFILE = Pattern.compile("^(?:You are (?:now )?playing on profile: |You switched to profile:? |Your profile was changed to: )([A-Za-z]+)");
    /** The "Profile: Mango" line of the SkyBlock tab list, for sessions that started after the join message. */
    private static final Pattern TAB_PROFILE = Pattern.compile("^\\s*Profile: ([A-Za-z]+)");
    private static final Pattern PROFILE_ID = Pattern.compile("^Profile ID: ([0-9a-fA-F-]{32,36})");

    private final Supplier<Path> configDir;
    private ScopedStorage storage;
    private String player, profile, profileId;
    private AccountScope pinned;
    private String event;

    public AccountStorage(Supplier<Path> configDir) { this.configDir = configDir; }

    private ScopedStorage storage() {
        if (storage == null) storage = new ScopedStorage(configDir.get(), FILES);
        return storage;
    }

    /** Reads a server chat line; true when it announced a different profile than before. */
    public synchronized boolean chat(String text) {
        Matcher id = PROFILE_ID.matcher(text);
        if (id.find()) { profileId = id.group(1); return false; }
        Matcher name = PROFILE.matcher(text);
        if (!name.find()) return false;
        boolean changed = profile != null && !profile.equals(name.group(1));
        if (changed) profileId = null;
        profile = name.group(1);
        return changed;
    }

    /**
     * Reads the tab list while no join message has named the profile, so a game that joined
     * SkyBlock before the announcement was seen (or missed it) still identifies the profile.
     * The join and switch messages stay the authority once seen.
     */
    public synchronized void tabList(List<String> lines) {
        if (profile != null || player == null || lines == null) return;
        for (String line : lines) {
            Matcher name = line == null ? null : TAB_PROFILE.matcher(line);
            if (name != null && name.find()) { profile = name.group(1); return; }
        }
    }

    /** The player's UUID when the game provides it, otherwise their name. */
    public synchronized void player(String uuid, String username) {
        String next = uuid != null && !uuid.isBlank() ? uuid : username != null && !username.isBlank() ? "name:" + username : null;
        if (next != null && !next.equals(player)) { player = next; profile = null; profileId = null; }
    }

    public synchronized AccountScope current() {
        return player == null || profile == null ? null : new AccountScope(player, profile);
    }

    public synchronized AccountScope pinned() { return pinned; }

    /**
     * Prepares the current profile for trading and pins it. Returns null when trading may
     * start, otherwise the reason it may not, in words a player can act on.
     */
    public synchronized String prepare() {
        AccountScope scope = current();
        if (scope == null) return "SkyBlock profile not identified yet; be on SkyBlock with the tab list showing your profile, or rejoin SkyBlock";
        if (pinned != null && !pinned.equals(scope))
            return "Trading data is open for profile " + pinned.profile() + "; restart the game to trade on " + scope.profile();
        try {
            var legacy = storage().legacy(scope);
            if (legacy == ScopedStorage.Legacy.PENDING) {
                int open = legacyPositions();
                if (open > 0) return open + " position(s) saved before profiles were separated were found. On the profile that owns them run "
                        + "\".a* goofyaddon profiles adopt\"; otherwise \".a* goofyaddon profiles setaside\". Nothing was moved.";
                var adopted = storage().adopt(scope);
                event = "Adopted " + adopted.size() + " file(s) with no open positions into " + scope.label();
            }
            storage().prepare(scope, profileId);
        } catch (IOException | RuntimeException failure) {
            return "Profile storage unavailable: " + failure.getMessage();
        }
        pinned = scope;
        return null;
    }

    /** One message about an automatic adoption, consumed once for diagnostics. */
    public synchronized String takeEvent() { String text = event; event = null; return text; }

    /** The file for the pinned profile. Repositories call this only after a start pinned one. */
    public synchronized Path path(String file) {
        if (pinned == null) throw new IllegalStateException("No SkyBlock profile selected for trading files");
        return storage().path(pinned, file);
    }

    /** The file to show reports from: the pinned profile, else the current one, else none. */
    public synchronized Path displayPath(String file) {
        AccountScope scope = pinned != null ? pinned : current();
        return scope == null ? null : storage().path(scope, file);
    }

    public synchronized String adopt() throws IOException {
        AccountScope scope = current();
        if (scope == null) throw new IOException("SkyBlock profile not identified yet");
        if (pinned != null && !pinned.equals(scope)) throw new IOException("Restart the game first; profile " + pinned.profile() + " is open");
        var files = storage().adopt(scope);
        return files.isEmpty() ? "Nothing to adopt." : "Adopted " + files.size() + " file(s) into " + scope.label()
                + ". Start trading to verify them against inventory, storage and Bazaar orders.";
    }

    public synchronized String setAside() throws IOException {
        var files = storage().setAside();
        return files.isEmpty() ? "Nothing to set aside." : "Set aside " + files.size() + " legacy file(s); the originals stay in the config folder.";
    }

    public synchronized String status() {
        AccountScope scope = current();
        try {
            return "Profile: " + (scope == null ? "not identified" : scope.label())
                    + (pinned == null ? "" : " | trading files: " + pinned.profile())
                    + (scope == null ? "" : " | legacy files: " + storage().legacy(scope).name().toLowerCase(java.util.Locale.ROOT));
        } catch (IOException failure) {
            return "Profile storage unavailable: " + failure.getMessage();
        }
    }

    /** Positions held in legacy files no profile has claimed yet. */
    int legacyPositions() throws IOException {
        int open = 0;
        for (String file : storage().unclaimed()) {
            if (!file.equals(BOOK_ORDERS) && !file.equals(GENERAL_ORDERS) && !file.equals(PRODUCTION_JOBS)) continue;
            Path path = storage().legacyPath(file);
            String text = Files.readString(path).strip();
            if (text.isEmpty()) continue;
            var root = JsonParser.parseString(text);
            if (root.isJsonArray()) open += ((JsonArray) root).size();
            else if (root.isJsonObject() && root.getAsJsonObject().has("jobs") && root.getAsJsonObject().get("jobs").isJsonArray()) {
                for (var job : root.getAsJsonObject().getAsJsonArray("jobs")) {
                    var state = job.isJsonObject() && job.getAsJsonObject().has("state") ? job.getAsJsonObject().get("state").getAsString() : "";
                    if (!state.equals("DONE") && !state.equals("CANCELLED")) open++;
                }
            }
            else open++; // An unrecognized shape is treated as holding something; a person decides.
        }
        return open;
    }
}
