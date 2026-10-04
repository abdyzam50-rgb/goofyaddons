package com.goofy.goofyaddons.features.profit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionLedgerTest {
 @TempDir Path dir;
 @Test void completeConfirmedCycleSurvivesRestartAndDuplicateReceipt() throws Exception {
  var l=new ExecutionLedger();l.begin("t","books","ENCHANTMENT_OVERLOAD_4","ENCHANTMENT_OVERLOAD_5",2,1,1000);
  l.begin("t","books","ENCHANTMENT_OVERLOAD_4","ENCHANTMENT_OVERLOAD_5",2,1,2000);
  l.complete("t","e",2,300.0,100.0,61000,false);l.complete("t","e",2,300.0,100.0,62000,false);
  assertEquals(1,l.samples().size());assertTrue(l.samples().getFirst().eligible());assertEquals(60000,l.samples().getFirst().observedMillis());
  l.write(dir.resolve("execution.json"));assertEquals(l.samples(),ExecutionLedger.read(dir.resolve("execution.json")).samples());
 }
 @Test void pausedUnknownPartialLostAndRecoveredTradesDoNotBecomeTimingEvidence() {
  for(int i=0;i<4;i++) {
   var l=new ExecutionLedger();l.begin("t","general","COAL","COAL",16,16,1000);
   if(i==0)l.interrupt();
   l.complete("t","e",i==1?8:16,100.0,i==2?null:10.0,10000,i==3);
   assertFalse(l.samples().getFirst().eligible());
  }
  var l=new ExecutionLedger();l.complete("recovered","e",16,100.0,10.0,10000,false);assertTrue(l.samples().isEmpty());
 }
}
