package com.goofy.goofyaddons.features.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ContributorSettingsTest {
    @TempDir Path folder;
    static final String KEY="synthetic-only-"+"a".repeat(40);
    @Test void savesOnlyPrivateFileAndPublicViewNeverExposesKey() throws Exception {
        var view=ContributorSettings.save(folder,"https://collector.test/","owner/repo",KEY,true,false);
        assertTrue(view.enabled());assertTrue(view.hasKey());assertEquals("https://collector.test",view.endpoint());
        assertFalse(view.toString().contains(KEY));
        assertTrue(Files.readString(folder.resolve("community-settings.json")).contains(KEY));
        if(Files.getFileStore(folder).supportsFileAttributeView("posix"))assertEquals(Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),Files.getPosixFilePermissions(folder.resolve("community-settings.json")));
        try(var files=Files.list(folder)){assertEquals(1,files.count());}
    }
    @Test void blankKeepsExistingKeyDisableRetainsItAndForgetRemovesIt() throws Exception {
        ContributorSettings.save(folder,"https://collector.test","owner/repo",KEY,true,false);
        assertTrue(ContributorSettings.save(folder,"https://collector.test","owner/repo","",false,false).hasKey());
        assertTrue(Files.readString(folder.resolve("community-settings.json")).contains(KEY));
        assertTrue(ContributorSettings.save(folder,"https://collector.test","owner/repo","",true,false).enabled());
        var removed=ContributorSettings.save(folder,"https://collector.test","owner/repo","",false,true);
        assertFalse(removed.enabled());assertFalse(removed.hasKey());assertFalse(Files.readString(folder.resolve("community-settings.json")).contains(KEY));
    }
    @Test void badInputLeavesWorkingSettingsUnchangedAndDoesNotEchoCredentials() throws Exception {
        ContributorSettings.save(folder,"https://collector.test","owner/repo",KEY,true,false);
        String before=Files.readString(folder.resolve("community-settings.json"));
        for(String address:new String[]{"http://public.test","https://"+KEY+"@collector.test","https://collector.test/?token="+KEY,"https://collector.test/v1/gameplay"}) {
            var error=assertThrows(IllegalArgumentException.class,()->ContributorSettings.save(folder,address,"owner/repo",KEY,true,false));
            assertFalse(error.getMessage().contains(KEY));assertEquals(before,Files.readString(folder.resolve("community-settings.json")));
        }
        assertThrows(IllegalArgumentException.class,()->ContributorSettings.save(folder,"https://collector.test","owner/repo","short",true,false));
        assertEquals(before,Files.readString(folder.resolve("community-settings.json")));
    }
    @Test void malformedFileIsPreservedWithoutLeakingItsContentsAndMissingKeyCannotEnableUploads() throws Exception {
        assertThrows(IllegalArgumentException.class,()->ContributorSettings.save(folder,"https://collector.test","owner/repo","",true,false));
        Path file=folder.resolve("community-settings.json");Files.writeString(file,"broken private "+KEY);
        var error=assertThrows(java.io.IOException.class,()->ContributorSettings.save(folder,"https://collector.test","owner/repo",KEY,true,false));
        assertFalse(error.getMessage().contains(KEY));assertEquals("broken private "+KEY,Files.readString(file));
    }
}
