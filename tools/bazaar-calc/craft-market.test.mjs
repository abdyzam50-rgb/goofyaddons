import test from 'node:test';
import assert from 'node:assert/strict';
import {CraftMarket,craftCandidates} from './craft-market.mjs';
const NOW=1700000000000;
const item=(id,overrides={})=>({itemId:id,type:'crafting',sellPrice:20000,craftCost:10000,volume:50,median:19000,lastUpdated:new Date(NOW).toISOString(),...overrides});
test('craft discovery accepts only fresh crafting rows with usable volume and prices',()=>{
 assert.deepEqual(craftCandidates([item('GOOD'),item('FORGE',{type:'forge'}),item('STALE',{lastUpdated:new Date(NOW-300001).toISOString()}),item('BAD',{volume:-1}),item('???')],NOW).map(r=>r.item),['GOOD']);
 assert.throws(()=>craftCandidates({items:[]},NOW));
});
test('AH discovery excludes Bazaar outputs, uses price-only quotes, and expires them',async()=>{
 let now=NOW,calls=0;
 const market=new CraftMarket({now:()=>now,token:()=> 'private-token',fetcher:async(url,options)=>{
  calls++;assert.equal(url,'https://sky.coflnet.com/api/crafts/profit');assert.equal(options.headers.Authorization,'Bearer private-token');
  return Response.json([item('AH_ITEM'),item('BAZAAR_ITEM')]);
 },price:async id=>({item:id,lowest:20000,secondLowest:21000,fetchedAt:now,source:'coflnet'})});
 const result=await market.refresh({BAZAAR_ITEM:{}});assert.equal(calls,1);assert.equal(result.rows.length,1);
 assert.equal(result.rows[0].quote.item,'AH_ITEM');assert.ok(!JSON.stringify(result).includes('private-token'));
 await market.refresh({BAZAAR_ITEM:{}});assert.equal(calls,1);
 now+=60001;assert.equal(market.view({BAZAAR_ITEM:{}}).rows[0].quote,null);
});
test('failed discovery reports its status without supplying invented candidates',async()=>{
 const market=new CraftMarket({now:()=>NOW,fetcher:async()=>new Response(null,{status:403}),price:async()=>{throw new Error();}});
 const view=await market.refresh();assert.equal(view.rows.length,0);assert.match(view.error,/HTTP 403/);
});

test('catalog outputs and components are quoted even when craft discovery fails, rotation reaches later items',async()=>{
 let now=NOW;const quoted=[];
 const catalog={recipes:Array.from({length:12},(_,i)=>({kind:'CRAFT',outputId:`OUT_${i}`,ingredients:{BASE:1}}))};
 const market=new CraftMarket({catalog,now:()=>now,fetcher:async()=>new Response(null,{status:403}),price:async item=>{quoted.push(item);return {item,lowest:100,fetchedAt:now};}});
 const first=await market.refresh({BASE:{}});assert.equal(first.rows.length,12);assert.match(first.error,/403/);assert.ok(first.rows.some(r=>r.quote));
 now+=20001;await market.refresh({BASE:{}});now+=20001;await market.refresh({BASE:{}});assert.ok(quoted.includes('OUT_11'));
});

test('search focus quotes a catalog craft and its AH components without arbitrary URL requests',async()=>{
 const catalog={recipes:[...Array.from({length:20},(_,i)=>({kind:'CRAFT',outputId:`OTHER_${i}`,ingredients:{}})),{kind:'CRAFT',outputId:'FOCUS',ingredients:{PART:1}}]};
 const quoted=[];const market=new CraftMarket({catalog,now:()=>NOW,fetcher:async()=>Response.json([]),price:async item=>{quoted.push(item);return {item,lowest:1,fetchedAt:NOW};}});
 await market.refresh({},'FOCUS');assert.ok(quoted.includes('FOCUS'));assert.ok(quoted.includes('PART'));assert.ok(quoted.length<=16);
});
