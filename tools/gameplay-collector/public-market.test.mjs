import test from 'node:test';
import assert from 'node:assert/strict';
import {publicMarket} from './public-market.mjs';
const now=2000000000000;
test('public market proxy caches fixed keyless resources and refuses expired prices',async()=>{
 const entries=new Map(),cache={match:async key=>entries.get(key.url)?.clone(),put:async(key,value)=>entries.set(key.url,value)};
 let calls=0;const fetchImpl=async(url,options)=>{calls++;assert.equal(url,'https://api.hypixel.net/v2/skyblock/bazaar');assert.equal(options.headers,undefined);return Response.json({success:true,lastUpdated:now,products:{COAL:{}}});};
 const request=new Request('https://collector.test/v1/market?url=https://attacker.invalid');
 for(let i=0;i<2;i++)assert.equal((await publicMarket(request,{fetchImpl,cache,now})).status,200);
 assert.equal(calls,1);assert.ok(entries.has('https://collector.test/v1/market'));
 const stale=await publicMarket(request,{fetchImpl,cache,now:now+61000});assert.equal(stale.status,503);assert.equal(calls,2);
 assert.equal(stale.headers.get('Cache-Control'),'no-store');
});
test('upstream failures and unsuccessful JSON cannot appear as fresh market data',async()=>{
 const request=new Request('https://collector.test/v1/market');
 for(const fetchImpl of [async()=>new Response('blocked',{status:429}),async()=>Response.json({success:false,lastUpdated:now,products:{}}),async()=>{throw new Error('Offline');}])assert.equal((await publicMarket(request,{fetchImpl,now})).status,503);
 const items=await publicMarket(new Request('https://collector.test/v1/items'),{fetchImpl:async()=>Response.json({success:true,items:[]}),now});assert.equal(items.status,200);
});
