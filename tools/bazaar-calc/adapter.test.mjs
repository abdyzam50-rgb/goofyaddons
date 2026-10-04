// Calculator/adapter tests use the bundled upstream engine, not a substitute implementation.
import test from 'node:test';
import assert from 'node:assert/strict';
import { recommend, PROTOCOL } from './adapter.mjs';
import { createCompanion } from './server.mjs';
const now = Date.now(), commit = '6dd0ae9565fd555dec9dbe5eca3a2f48ca3218cc';
export const product = (bid, ask, week = 100000) => ({ sell_summary: [{ pricePerUnit: bid, amount: 1000, orders: 10 }],
  buy_summary: [{ pricePerUnit: ask, amount: 1000, orders: 10 }], quick_status: { buyMovingWeek: week, sellMovingWeek: week,
    buyVolume: 1000, sellVolume: 1000, buyOrders: 10, sellOrders: 10 } });
export const packet = () => ({ protocol: PROTOCOL, requestId: 'test-1', market: { success: true, lastUpdated: now,
  products: { ENCHANTED_COAL: product(100, 130), ENCHANTMENT_OVERLOAD_4: product(100, 110), ENCHANTMENT_OVERLOAD_5: product(250, 280) } },
  constraints: { mode: 'BOTH', availableCapital: 10000, inventoryCapacity: 32, maxRecommendations: 10, maxHistoryAgeHours: 48,
    taxPercentage: 1.25, bookMinProfit: 0, checkSeconds: 20, bookCheckSeconds: 180, clickDelayMs: 350, bookSlots: 2, generalSlots: 3,
    general: { maxCoinsPerItem: 10000, maxItemsPerOrder: 16, minProfitPerBatch: 0, minMarginPercentage: 0, minWeeklyVolume: 0 },
    excludedProducts: [], configuredBookRoutes: ['ENCHANTMENT_OVERLOAD:4:5'], configuredGeneralItems: ['ENCHANTED_COAL'] } });
const history = { asOf: now, stats: {}, hold: {}, names: {} };
const run = (body = packet(), h = history) => recommend(body, h, { commit }, now);

test('sequential trader commits only input capital and admits profitable batches without a second escrow',()=>{
 const b=packet();b.constraints.mode='GENERAL';b.constraints.availableCapital=500;b.constraints.general.maxCoinsPerItem=500;
 b.constraints.general.minProfitPerBatch=75;
 const r=run(b);assert.equal(r.rows.length,1);
 const x=r.rows[0];assert.ok(x.batch>=3&&x.batch<=4);assert.ok(x.profitPerBatch>=75);
 assert.equal(x.capitalUsed,x.costPerOutput*x.batch);assert.ok(x.capitalUsed<=500);
 for(const row of run().rows)assert.equal(row.capitalUsed,row.costPerOutput*row.batch);
 b.constraints.availableCapital=200;assert.equal(run(b).rows.length,0);
});

test('automatic selection makes supported routes executable with empty manual lists and keeps exclusions',()=>{
 const b=packet();b.constraints.automaticSelection=true;b.constraints.configuredGeneralItems=[];b.constraints.configuredBookRoutes=[];
 b.market.products.REFINED_MINERAL=product(100,140);b.market.products.CHORUS_FRUIT=product(100,150);b.market.products.SYNTHETIC_UNKNOWN=product(100,200);
 const r=run(b);assert.ok(r.rows.some(x=>x.inputId==='REFINED_MINERAL'));
 assert.ok(r.rows.some(x=>x.kind==='BOOK'));assert.ok(r.rows.every(x=>x.configured));
 assert.ok(r.rows.every(x=>!['CHORUS_FRUIT','SYNTHETIC_UNKNOWN'].includes(x.inputId)));
 b.constraints.excludedProducts=['REFINED_MINERAL'];assert.ok(run(b).rows.every(x=>x.inputId!=='REFINED_MINERAL'));
 b.constraints.automaticSelection='true';assert.throws(()=>run(b),/automatic selection/);
});

test('excludes every Garden mutation even when explicitly configured, retaining other Garden routes', async () => {
  const { default: catalog } = await import('./mutation-products.json', { with: { type: 'json' } });
  const b=packet();b.constraints.mode='GENERAL';b.market.products={};
  for(const id of [...catalog.products,'FINE_FLOUR','DESIGNER_COFFEE_BEANS'])b.market.products[id]=product(100,130);
  b.constraints.configuredGeneralItems=Object.keys(b.market.products);
  const r=run(b);
  assert.equal(catalog.products.length,40);
  assert.deepEqual(r.rows.map(x=>x.inputId).sort(),['DESIGNER_COFFEE_BEANS','FINE_FLOUR']);
});

test('discovers supported ordinary and free-combine routes outside manual lists', () => {
  const body = packet(); body.constraints.configuredBookRoutes = []; body.constraints.configuredGeneralItems = [];
  const r = run(body);
  assert.ok(r.rows.some(x => x.kind === 'BOOK')); assert.ok(r.rows.some(x => x.kind === 'GENERAL'));
  assert.ok(r.rows.every(x => !x.configured && x.confidence === 'ESTIMATED'));
  for (const x of r.rows) {
    assert.equal(x.profitPerOutput, x.sellPrice * 0.9875 - x.costPerOutput);
    assert.ok(x.inputUnits <= 32 && x.capitalUsed <= 10000);
  }
});
test('sorts by sequential expected profit per hour with consistent cycle estimates', () => {
  const r = run();
  for (let i = 0; i < r.rows.length; i++) {
    const x = r.rows[i]; assert.equal(x.coinsPerHour, x.outputsPerHour * x.profitPerOutput);
    assert.equal(x.cycleSeconds, x.batch / x.outputsPerHour * 3600);
    if (i) assert.ok(r.rows[i - 1].coinsPerHour >= x.coinsPerHour);
  }
});
test('respect mode, occupied products, slots, inventory, budget, tax and entry profit', () => {
  let b = packet(); b.constraints.mode = 'GENERAL'; assert.ok(run(b).rows.every(x => x.kind === 'GENERAL'));
  b = packet(); b.constraints.excludedProducts = ['ENCHANTED_COAL', 'ENCHANTMENT_OVERLOAD']; assert.equal(run(b).rows.length, 0);
  b = packet(); b.constraints.inventoryCapacity = 1; assert.ok(run(b).rows.every(x => x.kind !== 'BOOK' && x.inputUnits === 1));
  b = packet(); b.constraints.availableCapital = 0; assert.equal(run(b).rows.length, 0);
  b = packet(); b.constraints.bookSlots = 0; b.constraints.generalSlots = 0; assert.equal(run(b).rows.length, 0);
  b = packet(); b.constraints.taxPercentage = 99; assert.equal(run(b).rows.length, 0);
  b = packet(); b.constraints.bookMinProfit = 1000000; b.constraints.general.minProfitPerBatch = 1000000; assert.equal(run(b).rows.length, 0);
});
test('stale history is discarded and explicitly labelled estimated', () => {
  const r = run(packet(), { ...history, asOf: now - 49 * 3600000 });
  assert.equal(r.historyUsed, false); assert.equal(r.historyStatus, 'STALE');
  assert.ok(r.rows.every(x => x.confidence === 'ESTIMATED'));
});
test('fresh samples produce measured recommendations; outdated samples cannot', () => {
  const hold = { n: 12, samples: Array.from({ length: 12 }, () => [60, 100, 0]), hours: 1, meanS: 60 };
  const fresh = { ...history, hold: { ENCHANTED_COAL: { bid: hold, ask: hold } } };
  assert.equal(run(packet(), fresh).rows.find(x => x.kind === 'GENERAL').confidence, 'MEASURED');
  assert.equal(run(packet(), { ...fresh, asOf: now - 49 * 3600000 }).rows.find(x => x.kind === 'GENERAL').confidence, 'ESTIMATED');
});
test('reject stale quotes, malformed constraints and isolate malformed products', () => {
  let b = packet(); b.market.lastUpdated -= 61000; assert.throws(() => run(b), /timestamp/);
  b = packet(); b.constraints.availableCapital = '10000'; assert.throws(() => run(b), /capital/);
  b = packet(); b.market.products.BAD = {}; const r = run(b); assert.equal(r.counts.malformedProducts, 1); assert.ok(r.rows.length > 0);
});
test('cannot suggest non-combinable enchantments, crafting or instant execution', () => {
  const b = packet(); b.market.products.ENCHANTMENT_COMPACT_1 = product(10, 11); b.market.products.ENCHANTMENT_COMPACT_2 = product(1000, 1100);
  const r = run(b); assert.ok(r.rows.every(x => !x.inputId.startsWith('ENCHANTMENT_COMPACT')));
  assert.ok(r.rows.every(x => ['BOOK','GENERAL'].includes(x.kind)));
});
test('real local HTTP companion accepts the mod protocol and returns recommendations', async () => {
  const server = createCompanion(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const url = `http://127.0.0.1:${server.address().port}`;
    const health = await (await fetch(url + '/health')).json(); assert.equal(health.readOnly, true);
    const body = packet(); body.market.lastUpdated = Date.now();
    body.market.products = { SYNTHETIC_ORDINARY: product(100, 130) }; body.constraints.configuredGeneralItems = [];
    const response = await fetch(url + '/v1/recommendations', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Goofy-Analysis': 'shadow-v1' }, body: JSON.stringify(body) });
    assert.equal(response.status, 200); const r = await response.json(); assert.equal(r.requestId, 'test-1'); assert.ok(r.rows.length > 0);
    const bad = await fetch(url + '/v1/recommendations', { method: 'POST', body: '{}' }); assert.equal(bad.status, 400);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('personal calibration is applied before ranking and preserves forecast arithmetic and current prices',()=>{
  const ordinary=run();let calls=0;
  const evidence={calibrate(row){calls++;row.cycleSeconds*=2;row.outputsPerHour=row.batch/row.cycleSeconds*3600;row.coinsPerHour=row.outputsPerHour*row.profitPerOutput;}};
  const r=recommend(packet(),history,{commit},now,evidence);
  assert.equal(calls,r.total);
  for(const row of r.rows){const base=ordinary.rows.find(x=>x.routeKey===row.routeKey);assert.equal(row.buyPrice,base.buyPrice);assert.equal(row.profitPerOutput,base.profitPerOutput);assert.ok(Math.abs(row.coinsPerHour-base.coinsPerHour/2)<1e-6);}
});
