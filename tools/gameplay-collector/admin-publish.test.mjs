import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {handleAdminPublish,runPublishers} from './admin-publish.mjs';
class D1 {
 constructor(){this.sql=new DatabaseSync(':memory:');}
 prepare(query){const db=this.sql;return {bind(...args){return {async run(){return {meta:db.prepare(query).run(...args)};}};}};}
}
const key='a'.repeat(64),request=(token=key,method='POST')=>new Request('https://collector.test/v1/admin/publish',{method,headers:token?{Authorization:`Bearer ${token}`}:{}});
const fixture=t=>{const db=new D1();t.after(()=>db.sql.close());return {DB:db,ADMIN_TOKEN:key};};
test('manual publishing fails closed without owner credentials and never invokes publishers',async t=>{
 const env=fixture(t);let calls=0;const options={gameplay:()=>calls++,ah:()=>calls++};
 assert.equal((await handleAdminPublish(request(null),env,options)).status,401);
 assert.equal((await handleAdminPublish(request('b'.repeat(64)),env,options)).status,401);
 assert.equal((await handleAdminPublish(request(key,'GET'),env,options)).status,405);
 assert.equal((await handleAdminPublish(request(),{DB:env.DB},options)).status,503);
 assert.equal(calls,0);
});
test('owner sees immediate results for both serial publishers without secrets',async t=>{
 const env=fixture(t),events=[];
 const response=await handleAdminPublish(request(),env,{now:1000,gameplay:async()=>{events.push('gameplay');return {changed:false};},ah:async()=>{events.push('ah');return {result:'COMMITTED',commitSha:'f'.repeat(40)};}});
 assert.equal(response.status,200);assert.deepEqual(events,['gameplay','ah']);
 const body=await response.json();assert.equal(body.gameplay.result,'UNCHANGED');assert.equal(body.ah.result,'COMMITTED');assert.ok(!JSON.stringify(body).includes(key));
});
test('one GitHub failure remains visible while the other dataset is attempted; lock releases',async t=>{
 const env=fixture(t);let ah=0;
 const options={now:1000,gameplay:async()=>{throw new Error('GitHub HTTP 401 secret-sensitive-detail');},ah:async()=>{ah++;return {result:'UNCHANGED'};}};
 const response=await handleAdminPublish(request(),env,options);assert.equal(response.status,502);
 const body=await response.json();assert.equal(body.gameplay.error,'HTTP 401');assert.equal(ah,1);assert.ok(!JSON.stringify(body).includes('secret-sensitive-detail'));
 assert.equal((await handleAdminPublish(request(),env,options)).status,502);assert.equal(ah,2);
});
test('database lease blocks concurrent manual and cron runs, and expired leases recover',async t=>{
 const env=fixture(t);let release,started;const ready=new Promise(r=>started=r),pause=new Promise(r=>release=r);
 const first=runPublishers(env,{now:1000,gameplay:async()=>{started();await pause;return {changed:false};},ah:async()=>({result:'UNCHANGED'})});await ready;
 const busy=await handleAdminPublish(request(),env,{now:1001,gameplay:()=>assert.fail('overlapping publish'),ah:()=>assert.fail('overlapping publish')});assert.equal(busy.status,409);
 release();assert.equal((await first).ok,true);
 env.DB.sql.prepare('UPDATE publication_lock SET owner=?,expires_at=?').run('crashed',500);
 assert.equal((await runPublishers(env,{now:2000,gameplay:async()=>({changed:false}),ah:async()=>({result:'UNCHANGED'})})).ok,true);
});
