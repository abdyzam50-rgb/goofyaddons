import test from 'node:test';import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,writeFileSync,rmSync} from 'node:fs';import {tmpdir} from 'node:os';import {join} from 'node:path';
import {ExecutionHistory} from './execution-history.mjs';
import {collectWindow} from './collect-market.mjs';
const now=2000000000000;
const sample=i=>({eventId:`event-${i}`,engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,completedAt:now-1000,observedMillis:120000,proceeds:200,profit:50,eligible:true});
const row=()=>({kind:'GENERAL',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,cycleSeconds:60,profitPerOutput:3,assumptions:[]});
test('deduplicated local observations survive restart, and matching samples adjust timing in both directions with evidence weighting',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-execution-'));
 try {
  const file=join(dir,'history.json'),h=new ExecutionHistory({file,now:()=>now});
  h.ingest(Array.from({length:9},(_,i)=>sample(i)));const a=row();h.calibrate(a);assert.ok(Math.abs(a.cycleSeconds-102)<1e-9);
  h.ingest([sample(0),sample(9)]);assert.equal(h.status().samples,10);
  const restored=new ExecutionHistory({file,now:()=>now}),b=row();restored.calibrate(b);assert.ok(Math.abs(b.cycleSeconds-102)<1e-9);assert.ok(Math.abs(b.coinsPerHour-16/102*3600*3)<1e-9);assert.equal(b.executionEvidence.samples,10);assert.equal(b.executionEvidence.marketCoinsPerHour,2880);
  const changed=row();changed.batch=8;restored.calibrate(changed);assert.equal(changed.cycleSeconds,60);
  const slow=row();slow.cycleSeconds=240;restored.calibrate(slow);assert.ok(slow.cycleSeconds<240);assert.ok(slow.cycleSeconds>160);
  restored.ingest(Array.from({length:20},(_,i)=>sample(i+10)));const strong=row();restored.calibrate(strong);assert.ok(Math.abs(strong.cycleSeconds-102)<1e-9);
  const fast=row();fast.cycleSeconds=240;restored.calibrate(fast);assert.ok(Math.abs(fast.cycleSeconds-184)<1e-9);
  const stale=new ExecutionHistory({file,now:()=>now+86400001}),c=row();stale.calibrate(c);assert.equal(c.cycleSeconds,60);
  assert.ok(!readFileSync(file,'utf8').includes('account'));
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('unknown, interrupted and malformed evidence cannot calibrate; corrupt files are preserved',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-execution-'));
 try{const file=join(dir,'history.json'),h=new ExecutionHistory({file,now:()=>now});
  h.ingest(Array.from({length:12},(_,i)=>({...sample(i),eligible:false})));const r=row();h.calibrate(r);assert.equal(r.cycleSeconds,60);
  assert.throws(()=>h.ingest([{...sample(1),profit:Infinity}]));
  writeFileSync(file,'broken');const corrupt=new ExecutionHistory({file,now:()=>now});corrupt.ingest([sample(1)]);assert.equal(readFileSync(file,'utf8'),'broken');assert.ok(corrupt.status().error);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('bounded collector saves once finished and fails when no live observation was accepted',async()=>{
 let clock=0,stops=0;const collector={state:{asOf:0},intervalMs:20,failures:0,poll:async()=>{collector.state.asOf++;},stop:async()=>{stops++;},status:()=>({lastUpdated:collector.state.asOf})};
 const result=await collectWindow({collector,durationMs:50,now:()=>clock,wait:async ms=>{clock+=ms;}});assert.ok(result.lastUpdated>0);assert.equal(stops,1);
 collector.poll=async()=>{};clock=0;await assert.rejects(collectWindow({collector,durationMs:50,now:()=>clock,wait:async ms=>{clock+=ms;}}),/No new/);assert.equal(stops,2);
});

test('three slow cycles lower forecasts across matching batch sizes, but early fast results cannot boost them',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-feedback-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest(Array.from({length:3},(_,i)=>sample(i)));
  const slow=row();h.calibrate(slow);assert.ok(slow.coinsPerHour<2880);assert.ok(slow.coinsPerHour>1440);
  const other=row();other.batch=8;other.inputUnits=8;other.cycleSeconds=30;h.calibrate(other);
  assert.equal(other.executionEvidence.samples,3);assert.ok(other.coinsPerHour<2880);
  const fast=row();fast.cycleSeconds=240;h.calibrate(fast);assert.equal(fast.cycleSeconds,240);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('fresh overdue positions and retired routes only lower timing, never invent settled profit, and pending bounds expire',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-pending-'));let clock=now;
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>clock});
  const active={tradeId:'waiting',engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,startedAt:now-600000,observedAt:now,observedMillis:600000};
  h.ingestActive([active]);const a=row();h.calibrate(a);
  assert.equal(a.executionEvidence.samples,0);assert.equal(a.executionEvidence.pendingSamples,1);
  assert.equal(a.executionEvidence.observedCoinsPerHour,0);assert.ok(a.coinsPerHour<2880);
  assert.throws(()=>h.ingestActive([active,active]));assert.throws(()=>h.ingestActive([{...active,observedMillis:900000}]));
  clock+=16000;const expired=row();h.calibrate(expired);assert.equal(expired.cycleSeconds,60);
  h.ingestActive([]);h.ingest([{...sample(1),eligible:false,censored:true,observedMillis:600000,profit:null,proceeds:null}]);
  const retired=row();h.calibrate(retired);assert.equal(retired.executionEvidence.censoredSamples,1);assert.equal(retired.executionEvidence.samples,0);
  assert.ok(retired.coinsPerHour<2880);
  const restored=new ExecutionHistory({file:join(dir,'history.json'),now:()=>clock});const b=row();restored.calibrate(b);
  assert.equal(b.coinsPerHour,retired.coinsPerHour);assert.equal(restored.status().pending,0);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('strong downside evidence can cut estimates by more than half while volume caps an upside adjustment',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-realistic-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest(Array.from({length:30},(_,i)=>({...sample(i),observedMillis:600000})));
  const slow=row();h.calibrate(slow);assert.ok(slow.executionEvidence.throughputFactor<0.15);assert.ok(Math.abs(slow.cycleSeconds-438)<1e-9);
  const improved=row();improved.cycleSeconds=1200;improved.maxOutputsPerHour=55;h.calibrate(improved);
  assert.ok(improved.outputsPerHour<=55);assert.ok(improved.executionEvidence.throughputFactor<=1.5);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('realized profit is compared to each original purchase forecast, without changing current prices or measured cycle time',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-realization-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest(Array.from({length:10},(_,i)=>({...sample(i),expectedProfit:100})));
  const r=row();r.cycleSeconds=120;r.buyPrice=100;r.sellPrice=130;h.calibrate(r);
  assert.equal(r.cycleSeconds,120);assert.equal(r.executionEvidence.profitRealizationFactor,0.65);
  assert.equal(r.executionEvidence.expectedProfitSamples,10);assert.equal(r.coinsPerHour,936);
  assert.equal(r.buyPrice,100);assert.equal(r.sellPrice,130);assert.equal(r.profitPerOutput,3);
  const legacy=new ExecutionHistory({file:join(dir,'legacy.json'),now:()=>now});legacy.ingest(Array.from({length:10},(_,i)=>sample(i)));
  const old=row();old.cycleSeconds=120;legacy.calibrate(old);assert.equal(old.executionEvidence.profitRealizationFactor,1);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('untested routes learn bounded corrections from similar-volume peers and own outcomes replace the prior',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-similar-volume-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  const peer=i=>({...sample(i),forecast:{cycleSeconds:60,inputPerDay:2400,outputPerDay:2400},expectedProfit:100});
  h.ingest(Array.from({length:10},(_,i)=>peer(i)));
  const fresh=()=>({...row(),inputId:'QUARTZ',outputId:'QUARTZ',volumeEvidence:{inputEffectivePerDay:2400,outputEffectivePerDay:2400}});
  const a=fresh();h.calibrate(a);
  assert.equal(a.executionEvidence.samples,0);assert.equal(a.executionEvidence.sharedSamples,10);
  assert.ok(a.executionEvidence.throughputFactor<0.51);assert.ok(a.executionEvidence.profitRealizationFactor<0.51);
  assert.equal(a.executionEvidence.observedCoinsPerHour,0);assert.ok(a.coinsPerHour<2880/3);
  const near=fresh();near.volumeEvidence={inputEffectivePerDay:4800,outputEffectivePerDay:4800};h.calibrate(near);
  assert.ok(near.coinsPerHour>a.coinsPerHour);assert.equal(near.executionEvidence.sharedSamples,10);
  for(const field of ['inputEffectivePerDay','outputEffectivePerDay']) {
   const far=fresh();far.volumeEvidence[field]=20000;h.calibrate(far);assert.equal(far.cycleSeconds,60);assert.equal(far.executionEvidence,undefined);
  }
  const book=fresh();book.kind='BOOK';h.calibrate(book);assert.equal(book.executionEvidence,undefined);
  // Original forecast ratios survive today's changing prices and fill model.
  const slowerMarket=fresh();slowerMarket.cycleSeconds=240;h.calibrate(slowerMarket);
  assert.equal(slowerMarket.executionEvidence.throughputFactor,a.executionEvidence.throughputFactor);
  h.ingest(Array.from({length:10},(_,i)=>({...peer(i+100),inputId:'QUARTZ',outputId:'QUARTZ',observedMillis:60000,profit:100})));
  const learned=fresh();h.calibrate(learned);assert.equal(learned.executionEvidence.samples,10);
  assert.ok(learned.executionEvidence.throughputFactor>a.executionEvidence.throughputFactor);assert.ok(learned.executionEvidence.throughputFactor<1);assert.ok(learned.executionEvidence.profitRealizationFactor>a.executionEvidence.profitRealizationFactor);
  const restored=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});const again=fresh();restored.calibrate(again);assert.equal(again.coinsPerHour,learned.coinsPerHour);
  assert.throws(()=>h.ingest([{...peer(500),forecast:{cycleSeconds:NaN,inputPerDay:1,outputPerDay:1}}]),/Invalid execution/);
  assert.throws(()=>h.ingest([{...peer(500),forecast:{cycleSeconds:60,inputPerDay:0,outputPerDay:1}}]),/Invalid execution/);
  const stale=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now+86400001});const expired=fresh();stale.calibrate(expired);assert.equal(expired.executionEvidence,undefined);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('book peer similarity compares daily inputs per output and does not transfer interrupted or retired outcomes',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-book-peer-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest(Array.from({length:10},(_,i)=>({...sample(i),engine:'books',inputId:'ENCHANTMENT_A_1',outputId:'ENCHANTMENT_A_5',inputUnits:16,batch:1,
    forecast:{cycleSeconds:60,inputPerDay:16000,outputPerDay:1000}})));
  const r={...row(),kind:'BOOK',inputId:'ENCHANTMENT_B_1',outputId:'ENCHANTMENT_B_4',inputUnits:8,batch:1,
    volumeEvidence:{inputEffectivePerDay:8000,outputEffectivePerDay:1000}};
  h.calibrate(r);assert.equal(r.executionEvidence.sharedSamples,10);assert.ok(r.executionEvidence.throughputFactor<0.51);
  const invalid=new ExecutionHistory({file:join(dir,'interrupted.json'),now:()=>now});
  invalid.ingest([...h.rows.values()].map(s=>({...s,eligible:false})));const other={...r,cycleSeconds:60,executionEvidence:undefined};invalid.calibrate(other);assert.equal(other.executionEvidence,undefined);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('one overdue cycle supplies its observed duration directly instead of blending toward an optimistic model',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-overdue-bound-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingestActive([{tradeId:'slow',engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,startedAt:now-300000,observedAt:now,observedMillis:300000}]);
  const r=row();h.calibrate(r);assert.equal(r.cycleSeconds,300);assert.equal(r.executionEvidence.throughputFactor,0.2);
  assert.equal(r.executionEvidence.pendingSamples,1);assert.equal(r.executionEvidence.samples,0);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('same-day weights step through 30/50/70 percent and fade after two hours',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-day-weight-'));let clock=now;
 try {
  for(const [count,weight] of [[1,.3],[2,.3],[3,.5],[5,.5],[6,.7]]) {
   const h=new ExecutionHistory({file:join(dir,`${count}.json`),now:()=>clock});
   h.ingest(Array.from({length:count},(_,i)=>sample(i)));const r=row();h.calibrate(r);
   assert.equal(r.executionEvidence.sameDayWeight,weight);assert.ok(Math.abs(r.cycleSeconds-(60*(1-weight)+120*weight))<1e-9);
  }
  const h=new ExecutionHistory({file:join(dir,'fade.json'),now:()=>clock});h.ingest(Array.from({length:6},(_,i)=>sample(i)));
  clock+=13*3600000;const faded=row();h.calibrate(faded);assert.ok(faded.executionEvidence.sameDayWeight>.34&&faded.executionEvidence.sameDayWeight<.36);
  clock+=12*3600000;const expired=row();h.calibrate(expired);assert.equal(expired.executionEvidence,undefined);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('waiting penalties start before three minutes and remain specific to the exact strategy',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-live-method-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingestActive([{tradeId:'waiting',engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,startedAt:now-90000,observedAt:now,observedMillis:90000,buyMode:'order',sellMode:'offer'}]);
  const waiting=row();h.calibrate(waiting);assert.equal(waiting.cycleSeconds,90);assert.equal(waiting.executionEvidence.samples,0);
  const instant={...row(),buyMode:'instant'};h.calibrate(instant);assert.equal(instant.executionEvidence,undefined);
  h.ingest([{...sample(1),eligible:false,censored:true,observedMillis:90000,profit:null,proceeds:null}]);h.ingestActive([]);
  const abandoned=row();h.calibrate(abandoned);assert.equal(abandoned.cycleSeconds,90);assert.equal(abandoned.executionEvidence.censoredSamples,1);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('known losses reduce profit while GUI failures reduce pipeline reliability without changing market timing',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-failures-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest([{...sample(1),eligible:false,outcome:'LOSS',profit:-100,proceeds:0,expectedProfit:100}]);
  const loss=row();h.calibrate(loss);assert.ok(loss.executionEvidence.profitRealizationFactor<1);assert.equal(loss.executionEvidence.samples,0);assert.equal(loss.executionEvidence.lossSamples,1);
  const gui=new ExecutionHistory({file:join(dir,'gui.json'),now:()=>now});
  gui.ingest([{...sample(2),eligible:false,outcome:'PIPELINE_FAILURE',profit:null,proceeds:null}]);
  const failed={...row(),inputId:'IRON',outputId:'IRON'};gui.calibrate(failed);
  assert.equal(failed.executionEvidence.marketTimingFactor,1);assert.equal(failed.executionEvidence.pipelineFailureSamples,1);
  assert.ok(failed.coinsPerHour<2880);assert.equal(failed.executionEvidence.observedCoinsPerHour,0);
  const different={...row(),buyMode:'instant'};gui.calibrate(different);assert.equal(different.executionEvidence,undefined);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
test('three recent retired stalls set a bounded entry cooldown without inventing profit',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-stalls-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingest(Array.from({length:3},(_,i)=>({...sample(i),eligible:false,censored:true,observedMillis:600000,profit:null,proceeds:null})));
  const r=row();h.calibrate(r);assert.ok(r.executionBlockedUntil>now);assert.ok(r.executionBlockedUntil<=now+300000);
  assert.equal(r.executionEvidence.observedCoinsPerHour,0);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('authoritative profile snapshots remove stale personal evidence and history resets survive restart',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-authoritative-history-'));
 try {
  const file=join(dir,'history.json'),h=new ExecutionHistory({file,now:()=>now});
  h.ingest(Array.from({length:6},(_,i)=>({...sample(i),inputId:'FUMING_POTATO_BOOK',outputId:'FUMING_POTATO_BOOK'})));
  const previous={...row(),inputId:'FUMING_POTATO_BOOK',outputId:'FUMING_POTATO_BOOK'};h.calibrate(previous);assert.equal(previous.executionEvidence.samples,6);
  h.ingest([sample(50)],true,true);
  const current={...row(),inputId:'FUMING_POTATO_BOOK',outputId:'FUMING_POTATO_BOOK'};h.calibrate(current);assert.equal(current.executionEvidence,undefined);
  assert.equal(h.rows.size,1);assert.equal(new ExecutionHistory({file,now:()=>now}).rows.size,1);
  assert.throws(()=>h.ingest([{...sample(51),profit:Infinity}],true,true));assert.equal(h.rows.size,1,'invalid snapshots cannot erase valid evidence');
  h.ingest([],true,true);assert.equal(new ExecutionHistory({file,now:()=>now}).rows.size,0);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('multi-hour observed stalls cannot be hidden behind a ten-times slowdown floor',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-long-wait-'));
 try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  h.ingestActive([{tradeId:'ten-hours',engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,startedAt:now-36000000,observedAt:now,observedMillis:36000000}]);
  const r=row();h.calibrate(r);assert.equal(r.cycleSeconds,36000);assert.ok(r.executionEvidence.throughputFactor<0.1);
  assert.ok(Math.abs(r.coinsPerHour-4.8)<1e-9);assert.equal(r.executionEvidence.samples,0);
  const smaller={...row(),batch:8,inputUnits:8,cycleSeconds:30};h.calibrate(smaller);assert.equal(smaller.cycleSeconds,18000);assert.ok(Math.abs(smaller.coinsPerHour-4.8)<1e-9);
 }finally{rmSync(dir,{recursive:true,force:true});}
});
