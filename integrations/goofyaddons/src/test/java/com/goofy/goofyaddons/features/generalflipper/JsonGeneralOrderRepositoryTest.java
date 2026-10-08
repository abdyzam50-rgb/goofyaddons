package com.goofy.goofyaddons.features.generalflipper;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JsonGeneralOrderRepositoryTest {
    @TempDir Path dir;
    private static final String LEGACY="""
        [{"item":{"id":"ENCHANTED_COAL","name":"Enchanted Coal"},"quantity":16,"unitCost":100,
        "sellPrice":130,"stage":"BUY_ORDER","submitted":true,"cancelRequested":false,"reprices":0,
        "placedAt":100,"heldSince":0,"checkedAt":0,"tradeId":"trade-1","saleEvent":"sale-1",
        "purchasePriceKnown":true,"settlementPending":false}]
        """;

    @Test void legacyPositionsRoundTripWithoutChangingTheJournalContract() throws Exception {
        var path=dir.resolve("orders.json");Files.writeString(path,LEGACY);
        var repository=new JsonGeneralOrderRepository(()->path);
        var positions=repository.load();repository.save(positions);
        assertEquals(JsonParser.parseString(LEGACY),JsonParser.parseString(Files.readString(path)));
        var restored=new JsonGeneralOrderRepository(()->path).load().getFirst();
        assertEquals("trade-1",restored.tradeId);assertEquals(16,restored.quantity);
        assertEquals(GeneralPosition.Stage.BUY_ORDER,restored.stage);
    }

    @Test void corruptOrDuplicatePositionsAreRejectedWithoutOverwritingEvidence() throws Exception {
        var path=dir.resolve("orders.json");var repository=new JsonGeneralOrderRepository(()->path);
        for(var text:List.of("{broken",LEGACY.strip().replace("]",","+LEGACY.strip().substring(1)))) {
            Files.writeString(path,text);
            assertThrows(Exception.class,repository::load);
            assertEquals(text,Files.readString(path));
        }
    }

    @Test void failedWriteDoesNotLetTheSameStateSkipItsNextPersistenceAttempt() throws Exception {
        var source=dir.resolve("source.json");Files.writeString(source,LEGACY);
        var positions=new JsonGeneralOrderRepository(()->source).load();
        var blockedParent=dir.resolve("not-a-directory");Files.writeString(blockedParent,"preserve");
        var destination=new AtomicReference<>(blockedParent.resolve("orders.json"));
        var repository=new JsonGeneralOrderRepository(destination::get);
        assertThrows(Exception.class,()->repository.save(positions));
        destination.set(dir.resolve("recovered.json"));repository.save(positions);
        assertEquals(JsonParser.parseString(LEGACY),JsonParser.parseString(Files.readString(destination.get())));
        assertEquals("preserve",Files.readString(blockedParent));
    }
}
