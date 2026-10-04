import test from 'node:test';
import assert from 'node:assert/strict';
import { get } from 'node:http';
import { createCompanion } from './server.mjs';
import { DashboardState } from './dashboard-state.mjs';
export const snapshot=(now=Date.now())=>({protocol:'goofy-dashboard/1',sessionId:'local-test',sentAt:now,
  account:{connected:true,name:'TestPlayer'},status:{state:'STOPPED',mode:'BOTH',purse:100000,committed:5000,capitalLimit:20000,reserve:10000},
  inventory:[{slot:0,name:'<img src=x onerror=alert(1)>',count:2,id:'ENCHANTED_COAL'}],
  books:{tasks:[],slotMemory:{regions:[]}},general:{positions:[]},analysis:{status:'STOPPED'},profit:{profit:500,settlements:2,incomplete:1}});
test('account freshness expires independently; predictions expire and unknown profit stays unknown',()=>{
  let now=Date.now();const state=new DashboardState(()=>now),body=snapshot(now);
  body.analysis.report={marketAt:now-50000,rows:[{coinsPerHour:123}]};state.accept(body);
  assert.equal(state.view().fresh,true);assert.ok(state.view().predictions);assert.equal(state.view().account.profit.incomplete,1);
  now+=11000;assert.equal(state.view().fresh,false);assert.equal(state.view().predictions,null);
  body.sentAt=now;state.accept(body);assert.equal(state.view().fresh,true);assert.equal(state.view().predictions,null);
});
test('invalid and out-of-order snapshots cannot replace accepted observations',()=>{
  const now=Date.now(),state=new DashboardState(()=>now);state.accept(snapshot(now));
  assert.equal(state.accept(snapshot(now-1)),false);assert.throws(()=>state.accept({...snapshot(now),sentAt:now-16000}));
  assert.throws(()=>state.accept({...snapshot(now),inventory:[{slot:0,name:'Bad',count:-1}]}));
  assert.equal(state.view().account.account.name,'TestPlayer');
});
test('disconnect retains observed inventory with an offline label and clears predictions',()=>{
  let now=Date.now();const state=new DashboardState(()=>now);const live=snapshot(now);
  live.analysis.report={marketAt:now,rows:[]};state.accept(live);
  now+=2000;const disconnected=snapshot(now);disconnected.account={connected:false,name:''};disconnected.inventory=[];
  state.accept(disconnected);const view=state.view();
  assert.equal(view.account.account.connected,false);assert.equal(view.account.account.name,'TestPlayer');
  assert.equal(view.account.inventory[0].count,2);assert.equal(view.predictions,null);
  now+=2000;const next=snapshot(now);next.sessionId='new-session';next.account={connected:false,name:''};next.inventory=[];
  state.accept(next);assert.deepEqual(state.view().account.inventory,[]);
});
test('local HTTP serves dashboard assets, accepts bounded opt-in telemetry and rejects foreign origins and hosts',async t=>{
  const server=createCompanion();await new Promise(r=>server.listen(0,'127.0.0.1',r));
  t.after(()=>new Promise(r=>{server.close(r);server.closeAllConnections();}));const url=`http://127.0.0.1:${server.address().port}`;
  const page=await fetch(url);assert.equal(page.status,200);assert.match(await page.text(),/Inventory & storage/);
  assert.match(page.headers.get('content-security-policy'),/frame-ancestors 'none'/);
  for(const asset of ['app.mjs','style.css','upstream.css'])assert.equal((await fetch(`${url}/dashboard/${asset}`)).status,200);
  const options={method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Dashboard':'local-v1'},body:JSON.stringify(snapshot())};
  assert.equal((await fetch(`${url}/v1/account`,options)).status,200);
  const view=await (await fetch(`${url}/v1/dashboard`)).json();assert.equal(view.account.account.name,'TestPlayer');assert.equal(view.fresh,true);
  assert.equal((await fetch(`${url}/v1/account`,{...options,headers:{...options.headers,Origin:'https://example.com'}})).status,403);
  const foreignHost=await new Promise((resolve,reject)=>{get(`${url}/v1/dashboard`,{headers:{Host:'rebound.example'}},res=>{res.resume();resolve(res.statusCode);}).on('error',reject);});
  assert.equal(foreignHost,403);
  assert.equal((await fetch(`${url}/dashboard/../server.mjs`)).status,404);
  assert.equal((await fetch(`${url}/v1/account`,{...options,body:'x'.repeat(1024*1024+1)})).status,413);
  assert.equal((await fetch(`${url}/v1/account`,{...options,headers:{'Content-Type':'application/json'}})).status,400);
});

test('pipeline previews require a fresh account and forecast and expire independently',()=>{
  let now=Date.now();const state=new DashboardState(()=>now),body=snapshot(now);
  body.analysis.report={marketAt:now,rows:[]};body.analysis.pipeline={status:'READY',expiresAt:now+500,next:[{priority:1}]};
  state.accept(body);assert.equal(state.view().pipeline.next.length,1);
  now+=1000;body.sentAt=now;state.accept(body);assert.equal(state.view().pipeline,null);assert.ok(state.view().predictions);
  body.analysis.pipeline.expiresAt=now+1000;body.analysis.report=undefined;now++;body.sentAt=now;state.accept(body);
  assert.equal(state.view().pipeline,null);
});

test('accepted opt-in account snapshots forward gameplay outcomes once and health reports collection',async()=>{
 const ingested=[];const executions={ingest(rows){ingested.push(rows);},status:()=>({samples:ingested.length,error:null})};
 const server=createCompanion({executions});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url=`http://127.0.0.1:${server.address().port}`,body={...snapshot(),executions:[{eventId:'confirmed-receipt'}]};
 try{
  const options={method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Dashboard':'local-v1'},body:JSON.stringify(body)};
  assert.equal((await fetch(`${url}/v1/account`,options)).status,200);assert.equal((await fetch(`${url}/v1/account`,options)).status,200);
  assert.equal(ingested.length,1);assert.equal((await(await fetch(`${url}/health`)).json()).execution.samples,1);
 }finally{await new Promise(r=>server.close(r));}
});
