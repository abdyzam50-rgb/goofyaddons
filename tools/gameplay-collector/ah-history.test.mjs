import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {AHHistory,AH_DAY,AH_INTERVAL,observations} from './ah-history.mjs';
import {collectAH,publishAH,runScheduledAH} from './scheduled-ah.mjs';
import worker,{handleRequest} from './worker.mjs';
const NOW=2000000000000;
class D1 {
 constructor(){this.sql=new DatabaseSync(':memory:');}
 prepare(query){const db=this;return {bind(...args){return {query,args,async run(){return {meta:db.sql.prepare(query).run(...args)};},async all(){return {results:db.sql.prepare(query).all(...args)};}};}};}
 async batch(rows){assert.ok(rows.length<=50);return rows.map(q=>({meta:this.sql.prepare(q.query).run(...q.args)}));}
 close(){this.sql.close();}
}
const view=(now=NOW,price=100)=>({rows:[{item:'RESULT',sourceAt:now,median:90,volume:42,demandSource:'coflnet-sales-summary',demandAt:now,quote:{item:'RESULT',lowest:price,fetchedAt:now},account:'never-store',auctionId:'never-store'}]});
test('daily AH history survives new store instances, deduplicates refreshes and expires old days',async t=>{
 const db=new D1();t.after(()=>db.close());const store=new AHHistory(db);
 await store.record(view(),NOW);await store.record(view(),NOW);
 let rows=(await store.buildDataset(NOW)).rows;assert.equal(rows.length,1);assert.equal(rows[0].observations,1);assert.equal(rows[0].volume,42);assert.equal(rows[0].priceSource,'BIN');
 await new AHHistory(db).record(view(NOW+AH_INTERVAL,200),NOW+AH_INTERVAL);
 rows=(await new AHHistory(db).buildDataset(NOW+AH_INTERVAL)).rows;assert.equal(rows[0].observations,2);assert.equal(rows[0].average,150);assert.equal(rows[0].price,200);
 assert.ok(!JSON.stringify(rows).includes('never-store'));
 await store.record({rows:[]},NOW+32*AH_DAY);assert.equal((await store.buildDataset(NOW+32*AH_DAY)).rows.length,0);
});
test('invalid IDs, expired or mismatched BIN prices cannot enter history',()=>{
 const bad=view();bad.rows[0].quote.item='WRONG';bad.rows[0].median=NaN;assert.equal(observations(bad,NOW).length,0);
 assert.equal(observations(view(NOW-300001),NOW).length,0);
 bad.rows[0].item='https://attacker.test';assert.equal(observations(bad,NOW).length,0);
});
const catalog={recipes:Array.from({length:30},(_,i)=>({kind:'CRAFT',outputId:`OUT_${i}`,ingredients:{}}))};
const assets={fetch:async req=>new URL(req.url).pathname==='/data/production-recipes.json'?Response.json(catalog):new Response('',{status:404})};
const provider=async url=>url.includes('hypixel')?Response.json({success:true,lastUpdated:NOW,products:{}}):url.endsWith('/profit')?Response.json([]):url.endsWith('/bin')?Response.json({lowest:1000}):Response.json({volume:42,median:900});
test('scheduled collector rotates persistently without visitors or a GitHub token',async t=>{
 const db=new D1();t.after(()=>db.close());const env={DB:db,ASSETS:assets};
 await collectAH(env,{now:NOW,fetchImpl:provider});let status=await new AHHistory(db).status(NOW);assert.equal(status.collection.result,'COLLECTED');assert.equal(status.collection.offset,12);assert.ok(status.items>=12);
 await collectAH(env,{now:NOW,fetchImpl:provider});status=await new AHHistory(db).status(NOW);assert.equal(status.collection.offset,24);
 assert.ok((await new AHHistory(db).buildDataset(NOW)).rows.some(r=>r.item==='OUT_23'));
});
test('AH publisher writes a separate bounded market dataset, skips unchanged data and records failures',async t=>{
 const db=new D1();t.after(()=>db.close());const store=new AHHistory(db);await store.record(view(),NOW);
 const env={DB:db,GITHUB_TOKEN:'server-only-secret',GITHUB_REPOSITORY:'owner/repo'};let content=null,puts=0;
 const fetchImpl=async(url,options={})=>{
  assert.ok(url.startsWith('https://api.github.com/repos/owner/repo'));assert.equal(options.headers.Authorization,'Bearer server-only-secret');
  if(url.includes('/git/ref/'))return Response.json({object:{sha:'a'.repeat(40)}});
  if(options.method==='PUT'){puts++;const body=JSON.parse(options.body);content=body.content;assert.equal(body.branch,'gameplay-data');assert.ok(url.endsWith('/contents/ah-market-history.json'));return Response.json({commit:{sha:'b'.repeat(40)}});}
  return content?Response.json({content,sha:'c'.repeat(40)}):new Response('',{status:404});
 };
 assert.equal((await publishAH(env,{now:NOW,fetchImpl})).result,'COMMITTED');
 assert.equal((await publishAH(env,{now:NOW+1000,fetchImpl})).result,'UNCHANGED');assert.equal(puts,1);
 const body=JSON.parse(atob(content));assert.equal(body.protocol,'goofy-ah-history/1');assert.ok(!atob(content).includes('server-only-secret'));
 await assert.rejects(publishAH(env,{now:NOW+2000,fetchImpl:async()=>new Response('',{status:403})}),/403/);
 assert.equal((await store.state('publisher')).error,'HTTP 403');
});
test('history endpoints are public, and publication failure does not stop collection',async t=>{
 const db=new D1();t.after(()=>db.close());const env={DB:db,ASSETS:assets};
 const result=await runScheduledAH(env,{now:NOW,fetchImpl:provider});assert.equal(result.collection,'fulfilled');assert.equal(result.publishing,'rejected');
 const response=await handleRequest(new Request('https://collector.test/v1/crafts/history'),env,NOW);assert.equal(response.status,200);assert.equal((await response.json()).protocol,'goofy-ah-history/1');
 const status=await handleRequest(new Request('https://collector.test/v1/crafts/status'),env,NOW);assert.equal((await status.json()).collection.result,'COLLECTED');
});
test('Worker cron registers market collection even if the gameplay publisher is unavailable',async t=>{
 const db=new D1();t.after(()=>db.close());let pending;
 await worker.scheduled({}, {DB:db,ASSETS:{fetch:async()=>new Response('',{status:404})}}, {waitUntil(task){pending=task;}});await pending;
 const state=await new AHHistory(db).state('collector');assert.equal(state.result,'FAILED');
});

test('zero provider sales persist as zero activity rather than disappearing or becoming fabricated prices',async t=>{
 const db=new D1();t.after(()=>db.close());const history=new AHHistory(db),data=view();data.rows[0].volume=0;data.rows[0].median=0;
 await history.record(data,NOW);const [r]=(await history.buildDataset(NOW)).rows;assert.equal(r.price,100);assert.equal(r.volume,0);
});
