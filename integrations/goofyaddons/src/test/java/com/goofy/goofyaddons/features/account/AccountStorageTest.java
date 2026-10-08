package com.goofy.goofyaddons.features.account;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccountStorageTest {
    @TempDir Path config;

    private AccountStorage joined(String profile) {
        var storage = new AccountStorage(() -> config);
        storage.player("0f1e2d3c-0000-4000-8000-000000000001", "Steve");
        storage.chat("You are now playing on profile: " + profile);
        return storage;
    }

    @Test void tradingWaitsForTheProfileAnnouncement() {
        var storage = new AccountStorage(() -> config);
        storage.player("0f1e2d3c-0000-4000-8000-000000000001", "Steve");
        assertNotNull(storage.prepare());
        assertThrows(IllegalStateException.class, () -> storage.path(AccountStorage.BOOK_ORDERS));
        storage.chat("You are now playing on profile: Apple");
        assertNull(storage.prepare());
        assertTrue(storage.path(AccountStorage.BOOK_ORDERS).toString().contains("apple"));
    }

    @Test void thePinnedProfileKeepsItsFilesAfterASwitch() {
        var storage = joined("Apple");
        assertNull(storage.prepare());
        Path apple = storage.path(AccountStorage.BOOK_ORDERS);
        assertTrue(storage.chat("You switched to profile Banana"));
        assertEquals(apple, storage.path(AccountStorage.BOOK_ORDERS), "positions loaded for Apple are only ever saved for Apple");
        assertTrue(storage.prepare().contains("restart the game"));
    }

    @Test void legacyPositionsNeedAPersonToChooseTheirProfile() throws Exception {
        Files.writeString(config.resolve(AccountStorage.BOOK_ORDERS), "[{\"book\":\"x\"}]");
        var storage = joined("Apple");
        String reason = storage.prepare();
        assertNotNull(reason);
        assertTrue(reason.contains("profiles adopt"));
        assertFalse(Files.exists(config.resolve("goofyaddons")) && Files.exists(config.resolve("goofyaddons/accounts")
                .resolve("0f1e2d3c-0000-4000-8000-000000000001/apple/" + AccountStorage.BOOK_ORDERS)));
        storage.adopt();
        assertNull(storage.prepare());
        assertEquals("[{\"book\":\"x\"}]", Files.readString(storage.path(AccountStorage.BOOK_ORDERS)));
    }

    @Test void legacyHistoryWithoutOpenPositionsIsAdoptedAutomatically() throws Exception {
        Files.writeString(config.resolve(AccountStorage.BOOK_ORDERS), "[]");
        Files.writeString(config.resolve(AccountStorage.PRODUCTION_JOBS), "{\"schema\":1,\"jobs\":[{\"id\":\"a\",\"state\":\"DONE\"}]}");
        Files.writeString(config.resolve(AccountStorage.PROFIT), "{}");
        var storage = joined("Apple");
        assertNull(storage.prepare());
        assertNotNull(storage.takeEvent());
        assertTrue(Files.exists(storage.path(AccountStorage.PROFIT)));
        assertTrue(Files.exists(config.resolve(AccountStorage.PROFIT)), "the original stays in place");
    }

    @Test void anUnfinishedProductionJobCountsAsAnOpenPosition() throws Exception {
        Files.writeString(config.resolve(AccountStorage.PRODUCTION_JOBS), "{\"schema\":1,\"jobs\":[{\"id\":\"a\",\"state\":\"PROCESSING\"}]}");
        assertNotNull(joined("Apple").prepare());
    }

    @Test void currentJoinMessageAndTabListBothIdentifyTheProfile() {
        var storage = new AccountStorage(() -> config);
        storage.player("0f1e2d3c-0000-4000-8000-000000000001", "Steve");
        storage.chat("You are playing on profile: Mango");
        assertEquals("Mango", storage.current().profile());

        var late = new AccountStorage(() -> config);
        late.tabList(java.util.List.of(" Profile: Kiwi"));
        assertNull(late.current(), "the tab list waits for the player");
        late.player("0f1e2d3c-0000-4000-8000-000000000002", "Alex");
        late.tabList(java.util.List.of("Area: Hub", " Profile: Kiwi \u2672"));
        assertEquals("Kiwi", late.current().profile());
        late.tabList(java.util.List.of(" Profile: Lime"));
        assertEquals("Kiwi", late.current().profile(), "only the join or switch message changes a known profile");
        assertTrue(late.chat("You are playing on profile: Lime"));
    }
}
