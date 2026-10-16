package com.goofy.goofyaddons.config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProfileConfigTest {
 @TempDir Path dir;
 Map<String,Object> old=new HashMap<>();
 @BeforeEach void reset()throws Exception {
  for(String name:List.of("INSTANCE","profileConfigPath","globalTemplate","loadError","lastLoadProblem")) {
   var field=GoofyConfig.class.getDeclaredField(name);field.setAccessible(true);old.put(name,field.get(null));field.set(null,null);
  }
  GoofyConfig.INSTANCE=new GoofyConfig();GoofyConfig.INSTANCE.purseReserve=12345;
 }
 @AfterEach void restore()throws Exception {
  for(var entry:old.entrySet()){var field=GoofyConfig.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(null,entry.getValue());}
 }
 @Test void profilesKeepIndependentSettingsAndReturningRestoresTheOriginal()throws Exception {
  var pear=dir.resolve("pear/goofyaddons.json");var papaya=dir.resolve("papaya/goofyaddons.json");
  GoofyConfig.selectProfile(pear);GoofyConfig.INSTANCE.purseReserve=777;GoofyConfig.save();
  GoofyConfig.selectProfile(papaya);assertEquals(12345,GoofyConfig.INSTANCE.purseReserve);
  GoofyConfig.INSTANCE.purseReserve=999;GoofyConfig.save();
  GoofyConfig.selectProfile(pear);assertEquals(777,GoofyConfig.INSTANCE.purseReserve);
  assertEquals(pear.toAbsolutePath().toString(),GoofyConfig.location());assertTrue(Files.exists(papaya));
 }
 @Test void invalidProfileSettingsCannotTradeWithAnotherProfilesConfig()throws Exception {
  GoofyConfig.selectProfile(dir.resolve("pear/goofyaddons.json"));GoofyConfig.INSTANCE.purseReserve=777;
  var bad=dir.resolve("papaya/goofyaddons.json");Files.createDirectories(bad.getParent());Files.writeString(bad,"{invalid}");
  GoofyConfig.selectProfile(bad);assertNotNull(GoofyConfig.loadError());assertNotEquals(777,GoofyConfig.INSTANCE.purseReserve);
  assertEquals("{invalid}",Files.readString(bad));
 }
}
