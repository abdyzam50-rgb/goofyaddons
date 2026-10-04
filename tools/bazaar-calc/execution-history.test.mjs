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
  h.ingest(Array.from({length:9},(_,i)=>sample(i)));const a=row();h.calibrate(a);assert.equal(a.cycleSeconds,60);
  h.ingest([sample(0),sample(9)]);assert.equal(h.status().samples,10);
  const restored=new ExecutionHistory({file,now:()=>now}),b=row();restored.calibrate(b);assert.equal(b.cycleSeconds,72);assert.equal(b.coinsPerHour,16/72*3600*3);assert.equal(b.executionEvidence.samples,10);assert.equal(b.executionEvidence.marketCoinsPerHour,2880);
  const changed=row();changed.batch=8;restored.calibrate(changed);assert.equal(changed.cycleSeconds,60);
  const slow=row();slow.cycleSeconds=240;restored.calibrate(slow);assert.equal(slow.cycleSeconds,240/(1+0.5/3));
  restored.ingest(Array.from({length:20},(_,i)=>sample(i+10)));const strong=row();restored.calibrate(strong);assert.equal(strong.cycleSeconds,120);
  const fast=row();fast.cycleSeconds=240;restored.calibrate(fast);assert.equal(fast.cycleSeconds,160);
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
