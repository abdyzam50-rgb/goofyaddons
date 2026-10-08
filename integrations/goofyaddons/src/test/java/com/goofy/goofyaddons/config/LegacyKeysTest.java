package com.goofy.goofyaddons.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyKeysTest {
    @TempDir Path dir;
    @Test void existingTradingBindingsMigrateWithoutChangingMoneyOrOverwritingTheSource()throws Exception {
        var path=dir.resolve("goofyaddons.json");var gson=new Gson();
        var object=gson.toJsonTree(new GoofyConfig()).getAsJsonObject();object.remove("keyCodeSchema");object.remove("toggleKey");object.remove("reloadKey");
        object.addProperty("startKey",74);object.addProperty("stopKey",75);object.addProperty("modeKey",77);
        object.addProperty("maxTradingCapital",154000000);object.addProperty("purseReserve",0);
        String old=gson.toJson(object);Files.writeString(path,old);GoofyConfig.load(path);
        assertNull(GoofyConfig.loadError());assertEquals(63,GoofyConfig.INSTANCE.toggleKey);assertEquals(64,GoofyConfig.INSTANCE.modeKey);assertEquals(65,GoofyConfig.INSTANCE.reloadKey);
        assertEquals(154000000,GoofyConfig.INSTANCE.maxTradingCapital);assertEquals(0,GoofyConfig.INSTANCE.purseReserve);assertEquals(old,Files.readString(path));
        GoofyConfig.save(path);GoofyConfig.load(path);assertEquals(63,GoofyConfig.INSTANCE.toggleKey);assertEquals(2,GoofyConfig.INSTANCE.keyCodeSchema);
    }
    @Test void customizedLegacyStartBecomesToggleAndConflictingNewBindingsAreRejected()throws Exception {
        var gson=new Gson();var object=gson.toJsonTree(new GoofyConfig()).getAsJsonObject();
        object.remove("toggleKey");object.remove("keyCodeSchema");object.remove("reloadKey");
        object.addProperty("startKey",290);object.addProperty("stopKey",75);object.addProperty("modeKey",77);
        Path file=dir.resolve("custom.json");Files.writeString(file,gson.toJson(object));GoofyConfig.load(file);
        assertEquals(58,GoofyConfig.INSTANCE.toggleKey);assertEquals(64,GoofyConfig.INSTANCE.modeKey);
        var invalid=new GoofyConfig();invalid.reloadKey=invalid.toggleKey;
        assertThrows(IllegalArgumentException.class,invalid::validate);
    }
    @Test void unsupportedBindingsAndUnknownSchemasAreRejectedRatherThanGuessed()throws Exception {
        assertThrows(IllegalArgumentException.class,()->LegacyKeys.fromGlfw(161));
        assertEquals(49,LegacyKeys.fromGlfw(92));assertEquals(58,LegacyKeys.fromGlfw(290));assertEquals(224,LegacyKeys.fromGlfw(340));
        var config=new GoofyConfig();config.keyCodeSchema=3;assertThrows(IllegalArgumentException.class,config::validate);
    }
    @Test void rejectedOrFailedGuiWritesKeepTheLastWorkingSettingsAndFile()throws Exception {
        var path=dir.resolve("settings.json");var good=new GoofyConfig();good.purseReserve=0;
        GoofyConfig.commitSettings(good,path);String saved=Files.readString(path);
        var invalid=new GoofyConfig();invalid.maxTradingCapital=-1;
        assertThrows(IllegalArgumentException.class,()->GoofyConfig.commitSettings(invalid,path));
        assertSame(good,GoofyConfig.INSTANCE);assertEquals(saved,Files.readString(path));
        Path blocked=dir.resolve("directory");Files.createDirectory(blocked);Files.writeString(blocked.resolve("keep"),"retained");
        assertThrows(java.io.IOException.class,()->GoofyConfig.commitSettings(new GoofyConfig(),blocked));
        assertSame(good,GoofyConfig.INSTANCE);assertEquals(saved,Files.readString(path));assertEquals("retained",Files.readString(blocked.resolve("keep")));
    }
}
