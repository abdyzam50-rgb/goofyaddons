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
   if(i==1){assertTrue(l.samples().isEmpty());assertEquals(1,l.active(10000).size());}
   else assertFalse(l.samples().getFirst().eligible());
  }
  var l=new ExecutionLedger();l.complete("recovered","e",16,100.0,10.0,10000,false);assertTrue(l.samples().isEmpty());
 }
 @Test void partialSalesAggregateIntoOneWholeCycleAndKeepTheOriginalForecast() {
  var l=new ExecutionLedger();var f=new ExecutionLedger.Forecast(60,2400,2400);
  l.begin("t","general","COAL","COAL",16,16,1000,100.0,f);
  l.complete("t","first",4,100.0,10.0,61000,false);
  l.complete("t","first",4,100.0,10.0,62000,false);
  assertTrue(l.samples().isEmpty());assertEquals(1,l.active(121000).size());
  l.complete("t","last",12,300.0,30.0,121000,false);
  var s=l.samples().getFirst();assertTrue(s.eligible());assertEquals(120000,s.observedMillis());
  assertEquals(400.0,s.proceeds());assertEquals(40.0,s.profit());assertEquals(f,s.forecast());
  assertEquals(100.0,s.expectedProfit());assertTrue(l.active(122000).isEmpty());
 }
 @Test void partialUnknownCostAndInterruptedTimingNeverBecomeEligibleAfterAggregation() {
  for(boolean pause:new boolean[]{false,true}) {
   var l=new ExecutionLedger();l.begin("t","general","COAL","COAL",16,16,1000);
   l.complete("t","first",4,100.0,pause?10.0:null,61000,false);
   if(pause)l.interrupt();
   l.complete("t","last",12,300.0,30.0,121000,false);
   assertFalse(l.samples().getFirst().eligible());
  }
 }
 @Test void retirementExcludesOnlyItsOwnTimingEvidence() {
  var l=new ExecutionLedger();l.begin("retired","books","A_1","A_2",2,1,1000);l.begin("normal","general","COAL","COAL",2,2,1000);
  l.interrupt("retired");l.complete("retired","sale1",2,50.0,-20.0,61000,false);
  l.complete("normal","sale2",2,100.0,10.0,61000,false);
  assertFalse(l.samples().stream().filter(s->s.eventId().equals("sale1")).findFirst().orElseThrow().eligible());
  assertTrue(l.samples().stream().filter(s->s.eventId().equals("sale2")).findFirst().orElseThrow().eligible());
 }
 @Test void activeTimingIsSessionLocalAndInterruptionsCannotPenalizeMarkets() {
  var l=new ExecutionLedger();l.begin("t","general","COAL","COAL",16,16,1000);
  var a=l.active(201000).getFirst();assertEquals(200000,a.observedMillis());assertEquals("t",a.tradeId());
  l.interrupt();assertTrue(l.active(301000).isEmpty());l.retire("t",301000);assertTrue(l.samples().isEmpty());
 }
 @Test void retirementPersistsALowerBoundWithoutClaimingSuccessfulTimingOrProfit() throws Exception {
  var l=new ExecutionLedger();l.begin("t","books","A_1","A_2",2,1,1000);l.retire("t",601000);
  assertTrue(l.active(601000).isEmpty());assertEquals(1,l.samples().size());var s=l.samples().getFirst();
  assertTrue(s.censored());assertFalse(s.eligible());assertNull(s.profit());assertNull(s.proceeds());
  l.retire("t",602000);l.complete("t","sale",2,100.0,10.0,603000,false);assertEquals(1,l.samples().size());
  l.write(dir.resolve("execution.json"));assertEquals(l.samples(),ExecutionLedger.read(dir.resolve("execution.json")).samples());
 }
 @Test void verifiedPurchaseForecastSurvivesCompletionForRealizedProfitComparison() throws Exception {
  var l=new ExecutionLedger();l.begin("t","general","COAL","COAL",16,16,1000,100.0);
  l.begin("t","general","COAL","COAL",16,16,2000,999.0);
  l.complete("t","e",16,150.0,50.0,61000,false);
  assertEquals(100.0,l.samples().getFirst().expectedProfit());assertEquals(50.0,l.samples().getFirst().profit());
  l.write(dir.resolve("execution.json"));assertEquals(l.samples(),ExecutionLedger.read(dir.resolve("execution.json")).samples());
 }
 @Test void originalTimingAndVolumeForecastSurvivesRestartAndRepeatedBegin() throws Exception {
  var l=new ExecutionLedger();var forecast=new ExecutionLedger.Forecast(60,2400,1200);
  l.begin("t","books","A_1","A_2",2,1,1000,100.0,forecast);
  l.begin("t","books","A_1","A_2",2,1,2000,999.0,new ExecutionLedger.Forecast(600,10,10));
  l.complete("t","e",2,300.0,50.0,121000,false);
  assertEquals(forecast,l.samples().getFirst().forecast());
  l.write(dir.resolve("execution.json"));assertEquals(forecast,ExecutionLedger.read(dir.resolve("execution.json")).samples().getFirst().forecast());
  var invalid=new ExecutionLedger();invalid.begin("bad","general","COAL","COAL",1,1,1000,100.0,new ExecutionLedger.Forecast(60,Double.NaN,1));
  invalid.complete("bad","e",1,200.0,100.0,61000,false);assertNull(invalid.samples().getFirst().forecast());
 }

}
