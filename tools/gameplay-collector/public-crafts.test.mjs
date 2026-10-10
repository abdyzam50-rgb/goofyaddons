import test from 'node:test';
import assert from 'node:assert/strict';
import {createPublicCrafts} from './public-crafts.mjs';
const now=2000000000000,request=new Request('https://collector.test/v1/crafts/market?url=https://attacker.test');
const candidate=item=>({itemId:item,type:'crafting',sellPrice:10000,craftCost:100,volume:100,median:10000,lastUpdated:new Date(now).toISOString()});
test('public crafts are cached, fixed-destination, price-only and exclude Bazaar outputs',async()=>{
 let calls=0;const entries=new Map(),cache={match:async k=>entries.get(k.url)?.clone(),put:async(k,r)=>entries.set(k.url,r)};
 const service=createPublicCrafts({now:()=>now,cache,fetchImpl:async(url,options)=>{
  calls++;assert.ok(!url.includes('attacker'));if(url.includes('hypixel'))return Response.json({success:true,lastUpdated:now,products:{COAL:{}}});
  assert.equal(options.headers.Authorization,'Bearer secret-test');if(url.endsWith('profit'))return Response.json([candidate('COAL'),candidate('RESULT')]);
  return Response.json({lowest:10000,secondLowest:11000,uuid:'private-auction-id'});
 }});
 const response=await service(request,{COFLNET_TOKEN:'secret-test'}),body=await response.json();assert.equal(body.rows.length,1);assert.equal(body.rows[0].item,'RESULT');assert.equal(body.rows[0].quote.lowest,10000);assert.ok(!JSON.stringify(body).includes('private-auction-id'));assert.ok(!JSON.stringify(body).includes('secret-test'));
 await service(request,{COFLNET_TOKEN:'secret-test'});assert.equal(calls,3);assert.ok(entries.has('https://collector.test/v1/crafts/market'));
});
test('upstream errors cannot invent craft prices and unavailable Bazaar stops AH classification',async()=>{
 const unavailable=createPublicCrafts({now:()=>now,fetchImpl:async()=>new Response('',{status:403})});assert.equal((await unavailable(request)).status,503);
 const service=createPublicCrafts({now:()=>now,fetchImpl:async url=>url.includes('hypixel')?Response.json({success:true,lastUpdated:now,products:{}}):new Response('',{status:403})});
 const response=await service(request),body=await response.json();assert.deepEqual(body.rows,[]);assert.match(body.error,/403/);assert.equal(response.headers.get('Cache-Control'),'no-store');
});

test('Worker loads the catalog from its own assets and prices outputs outside discovery',async()=>{
 const catalog={recipes:[{kind:'CRAFT',outputId:'RESULT',ingredients:{COAL:1,COMPONENT:1}}]};
 const service=createPublicCrafts({now:()=>now,fetchImpl:async url=>url.includes('hypixel')?Response.json({success:true,lastUpdated:now,products:{COAL:{}}}):url.endsWith('profit')?Response.json([]):Response.json({lowest:10000})});
 const response=await service(request,{ASSETS:{fetch:async r=>{const path=new URL(r.url).pathname;return path==='/data/production-recipes.json'?Response.json(catalog):new Response('Missing asset',{status:404});}}});
 const b=await response.json();assert.deepEqual(b.rows.map(r=>r.item),['RESULT','COMPONENT']);assert.ok(b.rows.every(r=>r.quote));
});
