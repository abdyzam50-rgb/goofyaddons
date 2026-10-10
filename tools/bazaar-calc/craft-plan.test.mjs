import test from 'node:test';
import assert from 'node:assert/strict';
import {planCrafts,prepare,depth} from './website-src/craft-plan.mjs';
const now=2000000000000;
const recipe={key:'craft:RESULT',kind:'CRAFT',outputId:'RESULT',outputCount:1,ingredients:{INPUT:2},requirement:'Requires: Gold IV'};
const catalog={names:{RESULT:'Result'},recipes:[recipe]};
const product=(ask,bid,amount=10000)=>({buy_summary:[{pricePerUnit:ask,amount}],sell_summary:[{pricePerUnit:bid,amount}],quick_status:{buyMovingWeek:700000,sellMovingWeek:700000}});
const market={success:true,lastUpdated:now,products:{INPUT:product(100,90),RESULT:product(1100,1000)}};
const options={catalog,market,now,budget:10000,maxBatches:1,minProfit:0,requirements:{},tax:1.25};
test('craft plan uses whole batches, ask/bid depth, purchase cushion and sell tax',()=>{
 const [row]=planCrafts(options);assert.equal(row.eligible,true);assert.equal(row.capital,2*100*1.04*1.03);assert.equal(row.profit,1000*.97*.9875-row.capital);assert.equal(row.batches,1);
 assert.equal(depth([{pricePerUnit:1,amount:1},{pricePerUnit:2,amount:2}],3,true),5);assert.equal(depth([{pricePerUnit:1,amount:1}],2,true),null);
});
test('craft plans enforce purse, requirements, volume, depth and expired prices',()=>{
 assert.match(planCrafts({...options,budget:1})[0].reason,/budget/);
 assert.match(planCrafts({...options,requirements:{[recipe.key]:['Gold IV unobserved']}})[0].reason,/unobserved/);
 assert.match(planCrafts({...options,market:{...market,products:{...market.products,RESULT:{...market.products.RESULT,quick_status:{buyMovingWeek:1,sellMovingWeek:1}}}}})[0].reason,/volume/);
 assert.equal(planCrafts({...options,market:{...market,lastUpdated:now-60001}}).length,0);
 assert.match(planCrafts({...options,market:{...market,products:{RESULT:market.products.RESULT}}})[0].reason,/input/);
});
test('basic intermediates expand to rods, include full preparation costs and surplus',()=>{
 const powder={key:'powder',kind:'CRAFT',outputId:'BLAZE_POWDER',outputCount:2,ingredients:{BLAZE_ROD:1},requirement:''};
 const final={...recipe,ingredients:{BLAZE_POWDER:3}};const c={names:{},recipes:[powder,final]};
 const prep=prepare(final,1,c.recipes);assert.deepEqual(prep.purchases,{BLAZE_ROD:2});assert.deepEqual(prep.steps,[{output:'BLAZE_POWDER',batches:2,units:4}]);
 const [row]=planCrafts({...options,catalog:c,market:{...market,products:{BLAZE_ROD:product(100,90),RESULT:product(1100,1000)}}});assert.ok(row.eligible);assert.equal(row.capital,2*100*1.04*1.03);
});
test('AH prices require fresh discovery and item-specific BIN, and reserve fees',()=>{
 const ah={generatedAt:now,rows:[{item:'RESULT',sourceAt:now,volume:100,quote:{item:'RESULT',lowest:1000,fetchedAt:now}}]};
 const [row]=planCrafts({...options,market:{...market,products:{INPUT:market.products.INPUT}},ah});assert.equal(row.venue,'AH');assert.equal(row.binPrice,999);assert.ok(row.capital>2000);assert.equal(row.eligible,false);assert.match(row.reason,/profit/);
 const stale=structuredClone(ah);stale.rows[0].quote.fetchedAt=now-60001;
 assert.match(planCrafts({...options,market:{...market,products:{INPUT:market.products.INPUT}},ah:stale})[0].reason,/fresh/);
 assert.match(planCrafts({...options,market:{...market,products:{INPUT:market.products.INPUT}},ah:{...ah,generatedAt:now-60001}})[0].reason,/fresh/);
});
test('best whole batch fits budget while all ranked rows remain visible',()=>{
 const [row]=planCrafts({...options,budget:500,maxBatches:16});assert.equal(row.batches,2);assert.ok(row.capital<=500);
 const blocked=planCrafts({...options,budget:0});assert.equal(blocked.length,1);assert.equal(blocked[0].eligible,false);
});

test('all catalog outputs remain visible when AH discovery omits them',()=>{
 const rows=planCrafts({...options,market:{...market,products:{INPUT:market.products.INPUT}}});
 assert.equal(rows.length,1);assert.equal(rows[0].profit,null);assert.equal(rows[0].eligible,false);assert.match(rows[0].reason,/fresh/);
});
test('AH components are item-specific and stale quotes cannot fund a craft',()=>{
 const ah={generatedAt:now,rows:[{item:'INPUT',sourceAt:now,quote:{item:'INPUT',lowest:100,fetchedAt:now}}]};
 const m={...market,products:{RESULT:product(11000,10000)}};
 assert.equal(planCrafts({...options,market:m,ah})[0].capital,200*1.04*1.03);
 ah.rows[0].quote.item='WRONG';assert.equal(planCrafts({...options,market:m,ah})[0].capital,null);
 ah.rows[0].quote.item='INPUT';ah.rows[0].quote.fetchedAt=now-60001;assert.equal(planCrafts({...options,market:m,ah})[0].capital,null);
});
test('missing intermediates expand with yields, leftovers, coins and their unlocks',()=>{
 const intermediate={key:'mid',kind:'CRAFT',outputId:'MID',outputCount:4,ingredients:{INPUT:3},coins:10,requirement:'Requires: Gold IV'};
 const final={...recipe,ingredients:{MID:6},coins:50};const c={names:{},recipes:[intermediate,final]};
 const rows=planCrafts({...options,catalog:c,requirements:{mid:['Gold IV unobserved']}});
 const row=rows.find(r=>r.output==='RESULT');assert.deepEqual(row.purchases,{INPUT:6});assert.equal(row.steps[0].units,8);assert.equal(row.capital,(600*1.04+70)*1.03);assert.match(row.reason,/Gold/);assert.equal(row.eligible,false);
});
test('cycles terminate with an unavailable input rather than crashing all craft results',()=>{
 const loop={...recipe,key:'loop',outputId:'LOOP',ingredients:{RESULT:1}};
 const row=planCrafts({...options,market:{...market,products:{}},catalog:{names:{},recipes:[loop,{...recipe,ingredients:{LOOP:1}}]}}).find(r=>r.output==='RESULT');
 assert.equal(row.eligible,false);assert.equal(row.profit,null);
});
test('multi-unit AH crafts reserve a listing fee for every output and unknown demand blocks recommendation',()=>{
 const ah={generatedAt:now,rows:[{item:'RESULT',sourceAt:now,volume:100,quote:{item:'RESULT',lowest:10000,fetchedAt:now}}]};
 const o={...options,catalog:{names:{},recipes:[{...recipe,outputCount:2}]},market:{...market,products:{INPUT:market.products.INPUT}},ah,budget:100000};
 const [r]=planCrafts(o);assert.equal(r.units,2);assert.equal(r.binPrice,9999);assert.equal(r.capital,200*1.04*1.03+19998*.035+4000);
 ah.rows[0].volume=null;const [unknown]=planCrafts(o);assert.ok(unknown.profit>0);assert.equal(unknown.eligible,false);assert.match(unknown.reason,/demand/);
});
test('the shipped catalog evaluates every distinct craft output, including unpriced routes',async()=>{
 const {readFile}=await import('node:fs/promises');
 const shipped=JSON.parse(await readFile(new URL('./calculator/data/production-recipes.json',import.meta.url),'utf8'));
 const rows=planCrafts({...options,catalog:shipped,market:{success:true,lastUpdated:now,products:{}}});
 assert.equal(rows.length,new Set(shipped.recipes.filter(r=>r.kind==='CRAFT').map(r=>r.outputId)).size);
 assert.ok(rows.every(r=>!r.eligible&&r.profit===null));
});
