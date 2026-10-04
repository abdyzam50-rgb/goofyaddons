import test from 'node:test';
import assert from 'node:assert/strict';
import { positionEstimates,predictionReason } from './dashboard-profit.mjs';
const account=()=>({status:{taxPercentage:1.25},books:{tasks:[{item:'ENCHANTMENT_OVERLOAD',outputLevel:5,plannedCost:10000}]},general:{positions:[{item:'COAL',units:16,purchasePriceKnown:true,cost:1000}]}});
const collector={quote:id=>({ask:id==='COAL'?100:20000,sourceAt:Date.now()})};
test('active-cycle estimates use fresh current offers, exact tax and declared cost bases',()=>{
  const r=positionEstimates(account(),collector);
  assert.equal(r.rows[0].profit,99.9*16*0.9875-1000);assert.equal(r.rows[1].profit,19999.9*0.9875-10000);
  assert.equal(r.known,2);assert.equal(r.unknown,0);assert.equal(r.total,r.rows[0].profit+r.rows[1].profit);
  assert.equal(r.rows[1].costBasis,'planned full-cycle input cost');
});
test('unknown costs or stale quotes remain unknown and losses are displayed',()=>{
  const a=account();a.general.positions[0].purchasePriceKnown=false;a.books.tasks[0].plannedCost=30000;
  const r=positionEstimates(a,collector);assert.equal(r.unknown,1);assert.ok(r.total<0);
  const missing=positionEstimates(a,{quote:()=>null});assert.equal(missing.total,null);assert.equal(missing.unknown,2);
  a.status.taxPercentage=undefined;assert.equal(positionEstimates(a,collector).total,null);
});
test('forecast status distinguishes disabled, connection failures, occupied capital and filtered routes',()=>{
  const view={fresh:true,account:{account:{connected:true},analysis:{enabled:false,status:'DISABLED'}}};
  assert.match(predictionReason(view),/disabled/);view.account.analysis={enabled:true,status:'UNAVAILABLE',error:'HTTP 503'};
  assert.match(predictionReason(view),/503/);view.account.analysis={enabled:true,status:'READY',comparison:{availableCapital:0}};
  view.predictions={rows:[],counts:{filtered:2}};assert.match(predictionReason(view),/No capital/);
  view.account.analysis.comparison.availableCapital=100;assert.match(predictionReason(view),/2 filtered/);
  view.predictions.rows=[{}];assert.equal(predictionReason(view),null);
});

test('full position limits explain an unevaluated report even when millions are spendable',()=>{
 const view={fresh:true,account:{account:{connected:true},status:{mode:'BOTH'},books:{tasks:[{},{}]},general:{positions:[{},{},{}]},analysis:{enabled:true,status:'READY',comparison:{availableCapital:11304129},pipeline:{account:{ready:true,mode:'BOTH',bookSlots:0,generalSlots:0,available:11304129,inventoryCapacity:24}}}},predictions:{rows:[],counts:{evaluated:0,filtered:0,warnings:0,malformedProducts:10}}};
 assert.match(predictionReason(view),/Active-position limits reached \(2 book, 3 general/);
 view.account.analysis.pipeline.account.ready=false;view.account.analysis.pipeline.account.reason='Purse is unreadable';assert.equal(predictionReason(view),'Purse is unreadable');
 view.account.analysis.pipeline.account.ready=true;view.account.analysis.pipeline.account.generalSlots=1;view.predictions.counts.evaluated=1025;view.predictions.counts.filtered=978;assert.match(predictionReason(view),/978 filtered/);
 view.account.analysis.pipeline.account.mode='BOOKS';assert.match(predictionReason(view),/Active-position limits/);
 view.account.analysis.pipeline.account.mode='GENERAL';assert.match(predictionReason(view),/978 filtered/);
 view.account.analysis.pipeline.account.inventoryCapacity=0;assert.match(predictionReason(view),/inventory headroom/);
});

test('measured profit/hour uses confirmed active-time accounting and never guesses incomplete profit',async()=>{
 const {measuredProfitRate}=await import('./dashboard-profit.mjs');
 assert.equal(measuredProfitRate({profit:1000000,activeMillis:600000,settlements:3,incomplete:0}),6000000);
 assert.equal(measuredProfitRate({profit:1000000,activeMillis:600000,settlements:3,incomplete:1}),null);
 assert.equal(measuredProfitRate({profit:1000000,activeMillis:10000,settlements:3,incomplete:0}),null);
 assert.equal(measuredProfitRate(undefined),null);
});
