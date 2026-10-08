package com.goofy.goofyaddons.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where each profile's trading files live, and how files from before profiles were
 * separated are handed to exactly one profile.
 *
 * <p>Layout version 1 kept every file directly in the config folder. Version 2 keeps them in
 * {@code goofyaddons/accounts/<player>/<profile>/}, beside a {@code storage.json} manifest that
 * records the layout version and the owner. File contents keep their own formats.
 *
 * <p>Legacy files are never moved, edited or deleted. Adopting them copies them into one
 * profile's folder and writes a {@code .claimed} marker beside each original naming that
 * profile, so no other profile can adopt the same positions. Setting them aside writes the
 * same marker without copying. Adopted positions are then verified against the live game
 * by each engine's normal recovery before anything trades.
 */
public final class ScopedStorage {
    public static final int LAYOUT_VERSION = 2;
    public static final String MANIFEST = "storage.json";
    public static final String CLAIM_SUFFIX = ".claimed";
    private static final String SET_ASIDE = "set-aside";

    public enum Legacy { NONE, PENDING, ADOPTED_HERE, CLAIMED_ELSEWHERE }

    private final Path configDir;
    private final List<String> files;

    /** @param files every per-profile file name this installation keeps */
    public ScopedStorage(Path configDir, List<String> files) {
        this.configDir = configDir;
        this.files = List.copyOf(files);
    }

    public List<String> files() { return files; }

    public Path directory(AccountScope scope) {
        return configDir.resolve("goofyaddons").resolve("accounts").resolve(scope.playerFolder()).resolve(scope.profileFolder());
    }

    public Path path(AccountScope scope, String file) {
        if (!files.contains(file)) throw new IllegalArgumentException("Not a per-profile file: " + file);
        return directory(scope).resolve(file);
    }

    public Path legacyPath(String file) { return configDir.resolve(file); }

    /** Creates the profile folder and its manifest, refusing a folder written by a newer layout. */
    public Path prepare(AccountScope scope, String profileId) throws IOException {
        Path dir = directory(scope);
        Path manifest = dir.resolve(MANIFEST);
        if (Files.exists(manifest)) {
            int version = manifestVersion(Files.readString(manifest, StandardCharsets.UTF_8));
            if (version > LAYOUT_VERSION)
                throw new IOException("Profile storage was written by a newer GoofyAddons (layout " + version + "); files preserved");
            if (version < 1) throw new IOException("Profile storage manifest is unreadable; files preserved");
            if (profileId == null || Files.readString(manifest, StandardCharsets.UTF_8).contains(quote(profileId))) return dir;
        }
        AtomicFiles.replace(manifest, manifest(scope, profileId), "storage-");
        return dir;
    }

    /** The state of the version 1 files for this profile. */
    public Legacy legacy(AccountScope scope) throws IOException {
        boolean any = false, here = false, elsewhere = false;
        for (String file : files) {
            if (!Files.exists(legacyPath(file))) continue;
            any = true;
            Optional<String> claimant = claimant(file);
            if (claimant.isEmpty()) return Legacy.PENDING;
            if (claimant.get().equals(key(scope))) here = true; else elsewhere = true;
        }
        if (!any) return Legacy.NONE;
        return here ? Legacy.ADOPTED_HERE : elsewhere ? Legacy.CLAIMED_ELSEWHERE : Legacy.NONE;
    }

    /** Legacy files that exist and no profile has claimed yet. */
    public List<String> unclaimed() throws IOException {
        var result = new java.util.ArrayList<String>();
        for (String file : files) if (Files.exists(legacyPath(file)) && claimant(file).isEmpty()) result.add(file);
        return List.copyOf(result);
    }

    /**
     * Copies every unclaimed legacy file into this profile's folder and claims it. A file the
     * profile already has is never overwritten; the call then fails before claiming anything.
     */
    public List<String> adopt(AccountScope scope) throws IOException {
        var pending = unclaimed();
        for (String file : pending)
            if (Files.exists(path(scope, file))) throw new IOException(file + " already exists for " + scope.label() + "; nothing adopted");
        Files.createDirectories(directory(scope));
        for (String file : pending) {
            Files.copy(legacyPath(file), path(scope, file));
            AtomicFiles.replace(claimPath(file), key(scope), "claim-");
        }
        return pending;
    }

    /** Claims every unclaimed legacy file for nobody, leaving the originals untouched. */
    public List<String> setAside() throws IOException {
        var pending = unclaimed();
        for (String file : pending) AtomicFiles.replace(claimPath(file), SET_ASIDE, "claim-");
        return pending;
    }

    public Optional<String> claimant(String file) throws IOException {
        Path claim = claimPath(file);
        if (!Files.exists(claim)) return Optional.empty();
        return Optional.of(Files.readString(claim, StandardCharsets.UTF_8).strip());
    }

    public static String key(AccountScope scope) { return scope.player() + "/" + scope.profile(); }

    private Path claimPath(String file) { return configDir.resolve(file + CLAIM_SUFFIX); }

    private static String manifest(AccountScope scope, String profileId) {
        return "{\"version\":" + LAYOUT_VERSION + ",\"player\":" + quote(scope.player()) + ",\"profile\":" + quote(scope.profile())
                + (profileId == null ? "" : ",\"profileId\":" + quote(profileId)) + "}";
    }

    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");

    static int manifestVersion(String text) {
        Matcher matcher = VERSION.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
