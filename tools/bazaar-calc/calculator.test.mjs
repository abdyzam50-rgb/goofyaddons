import test from 'node:test';
import assert from 'node:assert/strict';
import {createCompanion} from './server.mjs';
import {MarketCollector} from './collector.mjs';
test('manual calculator and fresh market work without an account snapshot or active macro',async t=>{
 let now=Date.now();const collector=new MarketCollector({file:'/tmp/goofy-absent-manual-fixture',now:()=>now});
 const snapshot={success:true,lastUpdated:now,products:Object.fromEntries(Array.from({length:500},(_,i)=>[`TEST_${i}`,{
  product_id:`TEST_${i}`,sell_summary:[{pricePerUnit:100,amount:1000,orders:10}],buy_summary:[{pricePerUnit:130,amount:1000,orders:10}],
  quick_status:{buyMovingWeek:100000,sellMovingWeek:100000,buyVolume:1000,sellVolume:1000,buyOrders:10,sellOrders:10}}]))};
 collector.accept(snapshot);collector.accept({...snapshot,lastUpdated:now-1000});assert.equal(collector.market().lastUpdated,now);
 const server=createCompanion({collector,profileFetcher:async()=>Response.json({protocol:'goofy-profile/1',profiles:[],username:'Test'}),resourcesFetcher:async()=>Response.json({success:true,items:[]}),
  auctionFetcher:async url=>url.includes('/MISSING/')?new Response(null,{status:404}):url.includes('/BROKEN/')?Response.json({lowest:-1}):Response.json({lowest:5000,secondLowest:5200})});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>{server.close(r);server.closeAllConnections();}));
 const base=`http://127.0.0.1:${server.address().port}`;
 for(const path of ['/calculator/','/calculator/flips/craft','/calculator/flips/kat','/calculator/flips/fusion']) {
  const r=await fetch(base+path);assert.equal(r.status,200);assert.match(await r.text(),/calculator\/assets/);
 }
 assert.equal((await fetch(base+'/calculator/../../server.mjs')).status,404);
 assert.equal((await fetch(base+'/v1/market')).status,200);now+=60001;assert.equal((await fetch(base+'/v1/market')).status,503);
 assert.equal((await fetch(base+'/v1/profiles?username=Test')).status,200);
 assert.equal((await fetch(base+'/v1/profiles?username=bad%20name')).status,400);
 assert.equal((await fetch(base+'/v1/profiles?username=Test',{headers:{Origin:'https://foreign.test'}})).status,403);
 assert.equal((await fetch(base+'/v1/items')).status,200);
 const ah=await (await fetch(base+'/v1/ah/price?item=GOLDEN_TOOTH')).json();
 assert.equal(ah.protocol,'goofy-ah-price/1');assert.equal(ah.lowest,5000);assert.equal(ah.secondLowest,5200);assert.equal(ah.source,'coflnet');
 assert.equal((await fetch(base+'/v1/ah/price?item=MISSING')).status,404);
 assert.equal((await fetch(base+'/v1/ah/price?item=BROKEN')).status,502);
 assert.equal((await fetch(base+'/v1/ah/price?item=bad%20id')).status,400);
});
