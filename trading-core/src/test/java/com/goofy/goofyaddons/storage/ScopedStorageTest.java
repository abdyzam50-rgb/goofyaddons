package com.goofy.goofyaddons.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScopedStorageTest {
    @TempDir Path config;
    private static final AccountScope APPLE = new AccountScope("0f1e2d3c-0000-4000-8000-000000000001", "Apple");
    private static final AccountScope BANANA = new AccountScope("0f1e2d3c-0000-4000-8000-000000000001", "Banana");
    private static final AccountScope OTHER_ACCOUNT = new AccountScope("0f1e2d3c-0000-4000-8000-000000000002", "Apple");

    private ScopedStorage storage() { return new ScopedStorage(config, List.of("orders.json", "profit.json")); }

    @Test void profilesAndAccountsNeverShareAFile() {
        var storage = storage();
        assertNotEquals(storage.path(APPLE, "orders.json"), storage.path(BANANA, "orders.json"));
        assertNotEquals(storage.path(APPLE, "orders.json"), storage.path(OTHER_ACCOUNT, "orders.json"));
        assertThrows(IllegalArgumentException.class, () -> storage.path(APPLE, "../goofyaddons.json"));
    }

    @Test void legacyFilesAreAdoptedByExactlyOneProfileAndNeverChanged() throws Exception {
        var storage = storage();
        Files.writeString(config.resolve("orders.json"), "[{\"book\":\"x\"}]");
        assertEquals(ScopedStorage.Legacy.PENDING, storage.legacy(APPLE));

        assertEquals(List.of("orders.json"), storage.adopt(APPLE));
        assertEquals("[{\"book\":\"x\"}]", Files.readString(storage.path(APPLE, "orders.json")));
        assertEquals("[{\"book\":\"x\"}]", Files.readString(config.resolve("orders.json")), "the original is untouched");
        assertEquals(ScopedStorage.Legacy.ADOPTED_HERE, storage.legacy(APPLE));
        assertEquals(ScopedStorage.Legacy.CLAIMED_ELSEWHERE, storage.legacy(BANANA));
        assertEquals(List.of(), storage.adopt(BANANA));
        assertFalse(Files.exists(storage.path(BANANA, "orders.json")));
    }

    @Test void adoptionNeverOverwritesAProfilesOwnFile() throws Exception {
        var storage = storage();
        Files.writeString(config.resolve("orders.json"), "[1]");
        Files.createDirectories(storage.directory(APPLE));
        Files.writeString(storage.path(APPLE, "orders.json"), "[2]");
        assertThrows(java.io.IOException.class, () -> storage.adopt(APPLE));
        assertEquals("[2]", Files.readString(storage.path(APPLE, "orders.json")));
        assertEquals(ScopedStorage.Legacy.PENDING, storage.legacy(APPLE), "nothing was claimed");
    }

    @Test void settingAsideClaimsWithoutCopying() throws Exception {
        var storage = storage();
        Files.writeString(config.resolve("profit.json"), "{}");
        storage.setAside();
        assertEquals(ScopedStorage.Legacy.CLAIMED_ELSEWHERE, storage.legacy(APPLE));
        assertTrue(Files.exists(config.resolve("profit.json")));
        assertFalse(Files.exists(storage.path(APPLE, "profit.json")));
    }

    @Test void manifestRecordsTheLayoutAndRefusesANewerOne() throws Exception {
        var storage = storage();
        Path dir = storage.prepare(APPLE, "a1b2c3d4-0000-4000-8000-00000000abcd");
        String manifest = Files.readString(dir.resolve(ScopedStorage.MANIFEST));
        assertEquals(2, ScopedStorage.manifestVersion(manifest));
        assertTrue(manifest.contains("\"profile\":\"Apple\""));
        Files.writeString(dir.resolve(ScopedStorage.MANIFEST), "{\"version\":3}");
        assertThrows(java.io.IOException.class, () -> storage.prepare(APPLE, null));
    }

    @Test void folderNamesAreSafe() {
        var scope = new AccountScope("name:Some Player", "../../Kiwi");
        assertEquals("name-some-player", scope.playerFolder());
        assertEquals("kiwi", scope.profileFolder());
    }
}
