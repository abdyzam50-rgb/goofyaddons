package com.goofy.goofyaddons.features.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class CompanionFilesTest {
    @TempDir Path folder;
    @Test void dataPathsMatchExistingNodeCompanionIncludingOverridesAndDarwin() {
        Path home=folder.resolve("home");
        assertEquals(home.resolve("AppData/Local/GoofyAddons/bazaar-calc"),CompanionFiles.dataDirectory("Windows 11",Map.of(),home));
        assertEquals(folder.resolve("local/GoofyAddons/bazaar-calc"),CompanionFiles.dataDirectory("Windows 11",Map.of("LOCALAPPDATA",folder.resolve("local").toString()),home));
        for(String os:List.of("Mac OS X","Darwin"))assertEquals(home.resolve("Library/Application Support/GoofyAddons/bazaar-calc"),CompanionFiles.dataDirectory(os,Map.of(),home));
        assertEquals(home.resolve(".local/share/GoofyAddons/bazaar-calc"),CompanionFiles.dataDirectory("Linux",Map.of("XDG_DATA_HOME","relative"),home));
        assertEquals(folder,CompanionFiles.dataDirectory("Linux",Map.of("GOOFY_BAZAAR_DATA_DIR",folder.toString()),home));
        assertThrows(IllegalArgumentException.class,()->CompanionFiles.dataDirectory("Linux",Map.of("GOOFY_BAZAAR_DATA_DIR","relative"),home));
    }
    private byte[] zip(String entry,String text) throws Exception {
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry(entry));zip.write(text.getBytes());zip.closeEntry();}return bytes.toByteArray();
    }
    @Test void payloadUpgradeKeepsOldPayloadAndDoesNotTouchUserData() throws Exception {
        Files.writeString(folder.resolve("execution-history.json"),"existing receipts");
        Path first=CompanionFiles.unpack(zip("server.mjs","version one"),folder.resolve("managed"));
        Path again=CompanionFiles.unpack(zip("server.mjs","version one"),folder.resolve("managed"));
        Path newer=CompanionFiles.unpack(zip("server.mjs","version two"),folder.resolve("managed"));
        assertEquals(first,again);assertNotEquals(first,newer);
        assertEquals("version one",Files.readString(first.resolve("server.mjs")));
        assertEquals("existing receipts",Files.readString(folder.resolve("execution-history.json")));
    }
    @Test void archiveTraversalIsRejectedWithoutPublishingPartialPayload() throws Exception {
        assertThrows(IOException.class,()->CompanionFiles.unpack(zip("../outside.mjs","bad"),folder.resolve("managed")));
        assertFalse(Files.exists(folder.resolve("outside.mjs")));
        try(var entries=Files.list(folder.resolve("managed"))){assertEquals(0,entries.count());}
    }
}
