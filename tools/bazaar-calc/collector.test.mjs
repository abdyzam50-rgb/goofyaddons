import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { MarketCollector } from './collector.mjs';
import { createCompanion } from './server.mjs';
const HOUR = 3600000;
const market = (ts,bid = 100,ask = 130,amount = 1000) => ({success:true,lastUpdated:ts,
  products:Object.fromEntries(Array.from({length:500},(_,i) => [`TEST_${i}`,{
    product_id:`TEST_${i}`,sell_summary:[{pricePerUnit:bid,amount,orders:10}],buy_summary:[{pricePerUnit:ask,amount,orders:10}],
    quick_status:{buyMovingWeek:100000,sellMovingWeek:100000,buyVolume:amount,sellVolume:amount,buyOrders:10,sellOrders:10}
  }]))});
const fixture = async t => {
  const dir = await mkdtemp(join(tmpdir(),'goofy-collector-')); t.after(() => rm(dir,{recursive:true,force:true}));
  let clock = Math.floor(Date.now()/HOUR)*HOUR;
  const options = {file:join(dir,'live.json.gz'),now:()=>clock,bootstrap:{asOf:clock-100000,stats:{OLD:{n24:100}},hold:{OLD:{}},names:{TEST_0:'Test'}}};
  return {options,setTime:ts=>{clock=ts;},get now(){return clock;}};
};
test('fresh snapshots replace bootstrap; duplicates, stale, degraded and malformed snapshots do not mutate history',async t => {
  const f = await fixture(t), c = new MarketCollector(f.options), p = market(f.now);
  assert.equal(c.history().stats.OLD.n24,100); assert.equal(c.accept(p),true);
  assert.ok(c.quote('TEST_0').ask>0); assert.equal(c.quote('UNKNOWN'),null);
  assert.equal(c.accept(p),false); assert.equal(c.history().stats.OLD,undefined); assert.equal(c.history().stats.TEST_0.n24,1);
  const before = JSON.stringify(c.state);
  assert.throws(()=>c.accept(market(f.now-61000)),/Stale/);
  assert.throws(()=>c.accept(market(f.now+6000)),/future/);
  const degraded = market(f.now+1000); for (const p of Object.values(degraded.products)) p.quick_status.buyMovingWeek=p.quick_status.sellMovingWeek=0;
  assert.throws(()=>c.accept(degraded),/weekly volume/);
  const malformed = market(f.now+1000); malformed.products.TEST_499.buy_summary[0].amount=-1;
  assert.throws(()=>c.accept(malformed),/Malformed/); assert.equal(JSON.stringify(c.state),before);
});
test('upstream competition and time-on-top samples follow real consecutive changes without filling outage gaps',async t => {
  const f = await fixture(t), c = new MarketCollector(f.options), start = f.now;
  for (let i=0;i<=16;i++) { f.setTime(start+i*20000); c.accept(market(f.now,100+i,130-i,1000-i)); }
  const h = c.history(); assert.ok(h.stats.TEST_0.undercutBuyH>0); assert.equal(h.stats.TEST_0.liveHours,320/3600);
  assert.ok(h.hold.TEST_0.bid.n>=8); assert.ok(h.hold.TEST_0.ask.n>=8);
  f.setTime(f.now+200000); c.accept(market(f.now,120,110));
  assert.equal(c.history().stats.TEST_0.liveHours,320/3600);
  assert.ok(c.history().hold.TEST_0.bid.censored>0);
});
test('hourly prices and flow survive restart; startup does not invent book continuity',async t => {
  const f = await fixture(t), c = new MarketCollector(f.options), start = f.now;
  c.accept(market(f.now)); f.setTime(start+20000); c.accept(market(f.now,101,129));
  await c.save(); const recovered = new MarketCollector(f.options);
  assert.deepEqual(recovered.history(),c.history()); assert.equal(recovered.accept(market(f.now)),false);
  f.setTime(start+40000); recovered.accept(market(f.now,102,128));
  assert.equal(recovered.history().stats.TEST_0.liveHours,20/3600);
  f.setTime(start+HOUR); recovered.accept(market(f.now,103,127));
  assert.equal(recovered.history().stats.TEST_0.n24,2); assert.equal(recovered.history().stats.TEST_0.bid24,102.5);
});
test('wall-clock expiry removes measurements during outages and prunes seven-day prices',async t => {
  const f = await fixture(t), c = new MarketCollector(f.options), start = f.now;
  c.accept(market(start)); f.setTime(start+61000);
  assert.equal(c.quote('TEST_0'),null); assert.equal(c.status().fresh,false); assert.deepEqual(c.history().stats,{});
  f.setTime(start+8*24*HOUR); assert.deepEqual(c.history().stats,{}); assert.equal(Object.keys(c.state.items).length,0);
});
test('polling is single-flight, recovers from errors and saves accepted snapshots',async t => {
  const f = await fixture(t); let calls=0, release;
  const c = new MarketCollector({...f.options,fetcher:async () => { calls++; await new Promise(resolve=>{release=resolve;}); return new Response(JSON.stringify(market(f.now))); }});
  const first = c.poll(), second = c.poll(); assert.equal(first,second); assert.equal(calls,1); release(); await first;
  assert.equal(c.status().fresh,true); assert.equal(c.status().lastSaved,f.now);
  c.fetcher=async()=>new Response('',{status:503}); await c.poll(); assert.equal(c.status().failures,1); assert.match(c.status().error,/503/);
  f.setTime(f.now+20000); c.fetcher=async()=>new Response(JSON.stringify(market(f.now))); await c.poll();
  assert.equal(c.status().failures,0); assert.equal(c.status().error,null); assert.equal(c.status().fresh,true);
});
test('corrupt saved state and failed writes are visible without crashing recommendations',async t => {
  const f = await fixture(t); await writeFile(f.options.file,'broken');
  const c = new MarketCollector(f.options); assert.match(c.status().storageError,/could not be loaded/);
  c.file=join(f.options.file,'impossible'); c.fetcher=async()=>new Response(JSON.stringify(market(f.now))); await c.poll();
  assert.equal(c.status().fresh,true); assert.match(c.status().storageError,/save failed/); assert.ok(c.history().stats.TEST_0);
});
test('collector runs independently and stops its timer cleanly',async t => {
  const f = await fixture(t); let calls=0;
  const c = new MarketCollector({...f.options,intervalMs:5,fetcher:async()=> { calls++; return new Response(JSON.stringify(market(f.now))); }});
  c.start(); while (!c.timer) await new Promise(r=>setTimeout(r,5));
  assert.ok(c.status().enabled); assert.ok(calls>=1); await c.stop(); const stopped=calls;
  await new Promise(r=>setTimeout(r,20)); assert.equal(calls,stopped); assert.equal(c.status().enabled,false);
});
test('HTTP health exposes collector freshness and recommendations use collected history',async t => {
  const f = await fixture(t); f.setTime(Date.now());
  const c = new MarketCollector(f.options); c.accept(market(f.now));
  const server=createCompanion({collector:c}); await new Promise(r=>server.listen(0,'127.0.0.1',r));
  t.after(()=>new Promise(r=>{server.close(r); server.closeAllConnections();}));
  const health=await (await fetch(`http://127.0.0.1:${server.address().port}/health`)).json();
  const body = {protocol:'goofy-bazaar-shadow/1',requestId:'live-history-test',market:market(f.now),
    constraints:{mode:'GENERAL',availableCapital:10000,inventoryCapacity:32,maxRecommendations:10,maxHistoryAgeHours:48,
      taxPercentage:1.25,bookMinProfit:0,checkSeconds:20,bookCheckSeconds:180,clickDelayMs:350,bookSlots:0,generalSlots:3,
      general:{maxCoinsPerItem:10000,maxItemsPerOrder:16,minProfitPerBatch:0,minMarginPercentage:0,minWeeklyVolume:0},
      excludedProducts:[],configuredBookRoutes:[],configuredGeneralItems:[]}};
  const response = await fetch(`http://127.0.0.1:${server.address().port}/v1/recommendations`,{
    method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Analysis':'shadow-v1'},body:JSON.stringify(body)});
  assert.equal(response.status,200); const report = await response.json();
  assert.equal(report.dataAt,f.now); assert.ok(report.rows.length>0); assert.ok(report.rows.every(r=>r.confidence==='ESTIMATED'));
  assert.equal(health.collector.lastUpdated,f.now); assert.equal(health.collector.fresh,true); assert.equal(health.historyAsOf,f.now);
});

test('real trade counters override removals after an hour and survive legacy-row restart',async t => {
  const f=await fixture(t),c=new MarketCollector(f.options),start=f.now;
  for(let i=0;i<=90;i++) {
    f.setTime(start+i*120000);const p=market(f.now,100,130,1000-i);
    for(const product of Object.values(p.products)) {
      product.quick_status.sellMovingWeek=100000+2*i;
      product.quick_status.buyMovingWeek=100000+3*i;
    }
    c.accept(p);
    if(i===29)assert.equal(c.history().stats.TEST_0.flowBasis,'book');
    if(i===30) {
      const s=c.history().stats.TEST_0;
      assert.equal(s.flowBasis,'trades');assert.equal(s.observedBuyFlowH,60);assert.equal(s.observedSellFlowH,90);
    }
  }
  const s=c.history().stats.TEST_0;
  assert.equal(s.liveHours,3);assert.equal(s.delists.exact,true);
  assert.equal(s.delists.bidTrades,180);assert.equal(s.delists.askTrades,270);
  assert.equal(s.delists.bidRemoved,90);assert.equal(s.delists.askRemoved,90);
  await c.save();const recovered=new MarketCollector(f.options);assert.deepEqual(recovered.history(),c.history());
  // Version-1 installations had seven-field flow rows; they retain the book-based fallback.
  for(const item of Object.values(c.state.items))item.flows=item.flows.map(row=>row.slice(0,7));
  await c.save();const legacy=new MarketCollector(f.options);
  assert.equal(legacy.status().storageError,null);assert.equal(legacy.history().stats.TEST_0.flowBasis,'book');
  assert.equal(legacy.history().stats.TEST_0.observedBuyFlowH,30);
});

test('counter rollover, disconnected gaps and restart never invent trades',async t => {
  const f=await fixture(t),c=new MarketCollector(f.options),start=f.now;
  const accept=(ts,buy,sell)=>{
    f.setTime(ts);const p=market(ts);
    for(const product of Object.values(p.products))Object.assign(product.quick_status,{buyMovingWeek:buy,sellMovingWeek:sell});
    c.accept(p);
  };
  accept(start,100000,100000);accept(start+20000,100004,100006);
  let flow=c.state.items.TEST_0.flows[0];assert.deepEqual(flow.slice(7),[20,6,4]);
  accept(start+40000,99999,100010);assert.deepEqual(flow.slice(7),[20,6,4]);
  accept(start+60000,100002,100014);assert.deepEqual(flow.slice(7),[40,10,7]);
  accept(start+260000,100100,100100);assert.deepEqual(flow.slice(7),[40,10,7]);
  await c.save();const recovered=new MarketCollector(f.options);
  f.setTime(start+280000);recovered.accept(market(f.now));
  assert.deepEqual(recovered.state.items.TEST_0.flows[0].slice(7),[40,10,7]);
});
