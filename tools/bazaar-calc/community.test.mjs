import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,rmSync,statSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {DatabaseSync} from 'node:sqlite';
import {COMMUNITY_PROTOCOL,DAY,hash,exportSamples,validateSubmission,validateDataset} from './community-protocol.mjs';
import {CommunitySync,configure} from './community.mjs';
import {ExecutionHistory} from './execution-history.mjs';
import {enroll} from '../gameplay-collector/enroll.mjs';
import worker,{Store,handleRequest,publish} from '../gameplay-collector/worker.mjs';
const now=2000000000000,token='synthetic-test-token-'.padEnd(64,'a');
const source=(id='private-receipt-1')=>({eventId:id,engine:'general',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,
 completedAt:now-1000,observedMillis:120000,eligible:true,proceeds:200,profit:50,expectedProfit:100,
 forecast:{cycleSeconds:60,inputPerDay:2400,outputPerDay:2400},accountName:'Private name',chat:'private chat',purse:12345});
class D1 {
 constructor(){this.sql=new DatabaseSync(':memory:');this.sql.exec(readFileSync(new URL('../gameplay-collector/schema.sql',import.meta.url),'utf8'));}
 prepare(query){const db=this;return {bind(...args){return {async run(){return {meta:db.sql.prepare(query).run(...args)};},async all(){return {results:db.sql.prepare(query).all(...args)};},query,args};},async all(){return {results:db.sql.prepare(query).all()};}};}
 async batch(queries){assert.ok(queries.length<=50,'Free D1 query budget');this.sql.exec('BEGIN');try{const rows=queries.map(q=>q.query.trim().startsWith('SELECT')?{results:this.sql.prepare(q.query).all(...q.args)}:{meta:this.sql.prepare(q.query).run(...q.args)});this.sql.exec('COMMIT');return rows;}catch(e){this.sql.exec('ROLLBACK');throw e;}}
 close(){this.sql.close();}
}
const environment=async db=>({DB:db,CONTRIBUTOR_HASHES:JSON.stringify([await hash(token)]),GITHUB_TOKEN:'synthetic-server-only-credential',GITHUB_REPOSITORY:'abdyzam50-rgb/goofyaddons'});
const request=(body,key=token)=>new Request('https://collector.test/v1/gameplay',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${key}`},body:JSON.stringify(body)});
test('public export strips account, receipts, exact money and timestamps; excludes unknown and interrupted cycles',async()=>{
 const rows=[source(),{...source('unknown'),profit:null},{...source('paused'),eligible:false},{...source('no-forecast'),forecast:null}];
 const samples=await exportSamples(rows,'x'.repeat(64),now);assert.equal(samples.length,1);
 const s=samples[0];assert.equal(s.profitRatio,0.5);assert.equal(s.completedAt%3600000,0);assert.equal(s.observedMillis,120000);
 const text=JSON.stringify(samples);for(const secret of ['private-receipt','Private name','private chat','purse','proceeds','expectedProfit'])assert.ok(!text.includes(secret));
 assert.notEqual(s.id,(await exportSamples([source()],'y'.repeat(64),now))[0].id);
 assert.throws(()=>validateSubmission({protocol:COMMUNITY_PROTOCOL,samples:[{...s,accountName:'injected'}]},now));
 assert.throws(()=>validateSubmission({protocol:COMMUNITY_PROTOCOL,samples:[s,s]},now));
 assert.throws(()=>validateSubmission({protocol:COMMUNITY_PROTOCOL,samples:Array.from({length:21},(_,i)=>({...s,id:i.toString(16).padStart(64,'0')}))},now));
 assert.throws(()=>validateSubmission({protocol:COMMUNITY_PROTOCOL,samples:[{...s,inputId:['COAL']}]},now));
 assert.throws(()=>validateSubmission({protocol:COMMUNITY_PROTOCOL,samples:[{...s,forecast:{...s.forecast,inputPerDay:Infinity}}]},now));
});
test('enrolled automatic uploads are authenticated, deduplicated and capped in real SQLite; invalid packets are atomic',async()=>{
 const db=new D1(),env=await environment(db);try {
  const [s]=await exportSamples([source()],'x'.repeat(64),now),body={protocol:COMMUNITY_PROTOCOL,samples:[s]};
  assert.equal((await handleRequest(request(body,'b'.repeat(64)),env,now)).status,403);
  for(let i=0;i<2;i++){const result=await handleRequest(request(body),env,now);assert.equal(result.status,200);assert.deepEqual((await result.json()).acknowledged,[s.id]);}
  assert.equal(db.sql.prepare('SELECT count(*) AS n FROM samples').get().n,1);
  assert.equal((await handleRequest(request({protocol:COMMUNITY_PROTOCOL,samples:[s,{...s,id:'c'.repeat(64),batch:17}]}),env,now)).status,400);
  assert.equal(db.sql.prepare('SELECT count(*) AS n FROM samples').get().n,1);
  const store=new Store(db),contributor=await hash(token);
  for(let i=0;i<30;i++)await store.accept(contributor,Array.from({length:20},(_,j)=>({...s,id:(i*20+j+1).toString(16).padStart(64,'0')})),now);
  assert.equal(db.sql.prepare('SELECT count(*) AS n FROM samples').get().n,500);
  const dataset=await store.dataset(now);assert.equal(dataset.samples.length,10);validateDataset(dataset,now);
  assert.equal((await worker.fetch(new Request('https://collector.test/health'),env,{})).status,200);
 }finally{db.close();}
});
test('collector publishes only the data branch, preserves code branches and skips unchanged uploads',async()=>{
 const db=new D1(),env=await environment(db);try {
  const samples=await exportSamples([source()],'x'.repeat(64),now);await new Store(db).accept(await hash(token),samples,now);
  const calls=[];let saved=null,branch=false;
  const fake=async(url,opts)=>{
   const path=new URL(url).pathname.split('/goofyaddons')[1];calls.push({path,method:opts.method});let value={};
   if(path==='/git/ref/heads/gameplay-data'){if(!branch)return new Response('{}',{status:404});value={object:{sha:'branch'}};}
   else if(path==='')value={default_branch:'master'};
   else if(path==='/git/ref/heads/master')value={object:{sha:'base'}};
   else if(path==='/git/refs'){assert.deepEqual(JSON.parse(opts.body),{ref:'refs/heads/gameplay-data',sha:'base'});branch=true;}
   else if(path==='/contents/community-history.json'&&opts.method==='GET'){if(!saved)return new Response('{}',{status:404});value={sha:'file-sha',content:btoa(saved)};}
   else if(path==='/contents/community-history.json'&&opts.method==='PUT'){const body=JSON.parse(opts.body);assert.equal(body.branch,'gameplay-data');saved=atob(body.content);validateDataset(JSON.parse(saved),now);value={commit:{sha:'a'.repeat(40),html_url:'https://github.com/abdyzam50-rgb/goofyaddons/commit/'+ 'a'.repeat(40)}};}
   else throw new Error('Unexpected GitHub endpoint');
   return Response.json(value);
  };
  assert.equal((await publish(env,{now,fetchImpl:fake})).changed,true);
  assert.equal((await publish(env,{now:now+1000,fetchImpl:fake})).changed,false);
  const status=await(await handleRequest(new Request('https://collector.test/v1/publishing-status'),env,now+2000)).json();
  assert.equal(status.result,'UNCHANGED');assert.equal(status.lastCommitAt,now);assert.equal(status.lastSuccessAt,now+1000);
  assert.equal(status.commitSha,'a'.repeat(40));assert.equal(status.pendingSamples,0);assert.equal(status.publishedSamples,1);
  assert.ok(status.nextScheduledAt>now+2000);assert.ok(!JSON.stringify(status).includes(token));assert.ok(!JSON.stringify(status).includes(await hash(token)));
  assert.equal(calls.filter(c=>c.method==='PUT').length,1);assert.ok(!saved.includes(token));assert.ok(!saved.includes('private-receipt'));
 }finally{db.close();}
});
test('failed publisher attempts are visible without leaking credentials and newer uploads remain pending',async()=>{
 const db=new D1(),env=await environment(db);try {
  await new Store(db).accept(await hash(token),await exportSamples([source()],'x'.repeat(64),now),now);
  await assert.rejects(publish(env,{now,fetchImpl:async()=>new Response('secret must not be reported',{status:403})}),/HTTP 403/);
  const status=await(await handleRequest(new Request('https://collector.test/v1/publishing-status'),env,now+1000)).json();
  assert.equal(status.result,'FAILED');assert.equal(status.error,'HTTP 403');assert.equal(status.pendingSamples,1);assert.equal(status.lastSuccessAt,undefined);
  const old=await new Store(db).dataset(now-1);assert.equal(old.samples.length,0,'Uploads after the snapshot start cannot be marked published');
 }finally{db.close();}
});
test('companion automatically uploads, retries failures, persists acknowledgements and imports fleet corrections without private profit',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-community-'));const db=new D1();let clock=now,uploads=0,fail=true;
 try {
  const env=await environment(db),h=new ExecutionHistory({file:join(dir,'execution-history.json'),now:()=>clock});h.ingest([source()]);
  configure(dir,{endpoint:'https://collector.test',token});const fetchImpl=async(url,opts)=>{
   if(url.startsWith('https://raw.githubusercontent.com/'))return Response.json(await new Store(db).dataset(clock));
   uploads++;if(fail)throw new Error('Synthetic outage');return handleRequest(new Request(url,opts),env,clock);
  };
  const sync=new CommunitySync({executions:h,directory:dir,now:()=>clock,fetchImpl});await sync.tick();
  assert.equal(sync.status().pending,1);assert.equal(sync.sent.size,0);assert.ok(sync.status().error);
  fail=false;clock+=5*60000;await sync.tick();assert.equal(sync.sent.size,1);assert.equal(sync.status().pending,0);
  await new Store(db).accept('b'.repeat(64),await exportSamples([source('foreign')],'z'.repeat(64),clock),clock);
  const restarted=new CommunitySync({executions:h,directory:dir,now:()=>clock,fetchImpl});await restarted.tick();assert.equal(uploads,2,'acknowledged receipts survive replacement and restart');
  assert.equal(h.community.length,1,'only the other contributor is imported');assert.equal(h.community[0].contributor,'b'.repeat(64));assert.equal(h.rows.size,1);assert.equal(h.rows.values().next().value.profit,50,'shared ratios never replace local accounting');
  assert.ok(!JSON.stringify(restarted.status()).includes(token));
 }finally{db.close();rmSync(dir,{recursive:true,force:true});}
});
test('same-route fleet data supplies a bounded prior; ten personal outcomes override it and unrelated volumes do not transfer',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-fleet-prior-'));try {
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});
  const samples=await exportSamples(Array.from({length:10},(_,i)=>source(`private-${i}`)),'x'.repeat(64),now);
  h.setCommunity(samples.map(s=>({contributor:'a'.repeat(64),...s})));
  const row=()=>({kind:'GENERAL',inputId:'COAL',outputId:'COAL',inputUnits:16,batch:16,cycleSeconds:60,profitPerOutput:3,
   volumeEvidence:{inputEffectivePerDay:2400,outputEffectivePerDay:2400},assumptions:[]});
  const r=row();h.calibrate(r);assert.equal(r.executionEvidence.samples,0);assert.equal(r.executionEvidence.sharedSamples,3);assert.ok(r.coinsPerHour<2880);
  const varied=await exportSamples(Array.from({length:24},(_,i)=>({...source(`varied-${i}`),inputId:`COAL_${i%8}`,outputId:`COAL_${i%8}`})),'v'.repeat(64),now);
  h.setCommunity(varied.map(s=>({contributor:'a'.repeat(64),...s})));const bounded=row();h.calibrate(bounded);assert.equal(bounded.executionEvidence.sharedSamples,10,'one contributor is capped across all comparable routes');
  h.setCommunity(samples.map(s=>({contributor:'a'.repeat(64),...s})));
  h.ingest(Array.from({length:10},(_,i)=>({...source(`own-${i}`),observedMillis:60000,profit:100})));
  const own=row();h.calibrate(own);assert.equal(own.executionEvidence.throughputFactor,1);assert.equal(own.executionEvidence.profitRealizationFactor,1);
  const far={...row(),inputId:'DIAMOND',outputId:'DIAMOND',volumeEvidence:{inputEffectivePerDay:24000,outputEffectivePerDay:24000}};
  h.calibrate(far);assert.equal(far.executionEvidence,undefined);
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('owner enrollment writes private keys outside the installation, prevents overwrite and supports revocation',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-enrollment-'));try {
  const first=enroll(dir,'tester-01'),key=readFileSync(first.keyFile,'utf8').trim();
  assert.match(key,/^[A-Za-z0-9_-]{43}$/);assert.equal(statSync(first.keyFile).mode&0o777,0o600);
  const hashes=readFileSync(first.hashesFile,'utf8');assert.ok(!hashes.includes(key));assert.equal(JSON.parse(hashes).length,1);
  assert.throws(()=>enroll(dir,'tester-01'));assert.equal(readFileSync(first.keyFile,'utf8').trim(),key);
  enroll(dir,'tester-02');enroll(dir,'tester-01',{revoke:true});assert.equal(JSON.parse(readFileSync(first.hashesFile,'utf8')).length,1);
  assert.throws(()=>enroll(dir,'../outside'));
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('owner can import externally generated keys, rejects duplicate or invalid keys and revokes imported access',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-import-'));const db=new D1();try {
  const imported='0123456789abcdef'.repeat(4),entry=enroll(dir,'external-01',{token:imported});
  assert.equal(readFileSync(entry.keyFile,'utf8').trim(),imported);
  const approved=JSON.parse(readFileSync(entry.hashesFile,'utf8'));assert.deepEqual(approved,[await hash(imported)]);
  assert.throws(()=>enroll(dir,'duplicate',{token:imported}));assert.throws(()=>enroll(dir,'invalid',{token:'too-short'}));
  assert.deepEqual(JSON.parse(readFileSync(entry.hashesFile,'utf8')),approved);
  const env=await environment(db);env.CONTRIBUTOR_HASHES=JSON.stringify(approved);
  const body={protocol:COMMUNITY_PROTOCOL,samples:await exportSamples([source()],'x'.repeat(64),now)};
  assert.equal((await handleRequest(request(body,imported),env,now)).status,200);
  enroll(dir,'external-01',{revoke:true});env.CONTRIBUTOR_HASHES=readFileSync(entry.hashesFile,'utf8');
  assert.equal((await handleRequest(request(body,imported),env,now)).status,403);
 }finally{db.close();rmSync(dir,{recursive:true,force:true});}
});

test('keyless settings retain the chosen public repository and download learning without uploading',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-keyless-'));try {
  configure(dir,{endpoint:'https://collector.test',repository:'owner/shared-repo',sharingEnabled:false});
  const h=new ExecutionHistory({file:join(dir,'history.json'),now:()=>now});h.ingest([source()]);const calls=[];
  const sync=new CommunitySync({executions:h,directory:dir,now:()=>now,fetchImpl:async url=>{
   calls.push(url);return Response.json({protocol:COMMUNITY_PROTOCOL,generatedAt:now,samples:[]});
  }});
  await sync.tick();assert.deepEqual(calls,['https://raw.githubusercontent.com/owner/shared-repo/gameplay-data/community-history.json']);
  assert.equal(sync.status().sharingEnabled,false);assert.equal(sync.status().error,null);
  assert.ok(!JSON.stringify(sync.status()).includes(token));
  assert.throws(()=>configure(dir,{endpoint:'https://collector.test',sharingEnabled:true}));
 }finally{rmSync(dir,{recursive:true,force:true});}
});

test('trade-only HTTP feed works without an account dashboard and rejects stale or cross-origin packets',async()=>{
 const {createCompanion}=await import('./server.mjs');
 const dir=mkdtempSync(join(tmpdir(),'goofy-feed-'));let ticks=0;
 const executions=new ExecutionHistory({file:join(dir,'history.json')});
 const server=createCompanion({executions,community:{tick(){ticks++;}}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {
  const current=Date.now(),sample={...source(),completedAt:current-1000};
  const body={protocol:'goofy-executions/1',sentAt:current,executions:[sample]};
  const url=`http://127.0.0.1:${server.address().port}/v1/executions`;
  const post=(packet,extra={})=>fetch(url,{method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Dashboard':'local-v1',...extra},body:JSON.stringify(packet)});
  assert.equal((await post(body,{Origin:'https://other.test'})).status,403);assert.equal(executions.rows.size,0);
  assert.equal((await post({...body,sentAt:current-60000})).status,400);assert.equal(executions.rows.size,0);
  assert.equal((await post({...body,token})).status,400);assert.equal(executions.rows.size,0);
  assert.equal((await post(body)).status,200);assert.equal(executions.rows.size,1);assert.equal(ticks,1);
  assert.ok(!readFileSync(join(dir,'history.json'),'utf8').includes('Private name'));
  assert.equal((await post(body)).status,200);assert.equal(executions.rows.size,1);
 }finally{await new Promise(resolve=>server.close(resolve));rmSync(dir,{recursive:true,force:true});}
});

test('additional contributor secret approves new keys while preserving original owner approvals',async()=>{
 const db=new D1();try {
  const env=await environment(db),extraKey='synthetic-extra-'.padEnd(64,'x');
  const ownerList=env.CONTRIBUTOR_HASHES;
  env.CONTRIBUTOR_HASHES_EXTRA=JSON.stringify([await hash(extraKey)]);
  const body={protocol:COMMUNITY_PROTOCOL,samples:await exportSamples([source()],'x'.repeat(64),now)};
  assert.equal((await handleRequest(request(body),env,now)).status,200);
  assert.equal((await handleRequest(request(body,extraKey),env,now)).status,200);
  assert.equal(env.CONTRIBUTOR_HASHES,ownerList);
  assert.equal(db.sql.prepare('SELECT count(DISTINCT contributor) AS n FROM samples').get().n,2);
  env.CONTRIBUTOR_HASHES_EXTRA='[]';
  assert.equal((await handleRequest(request(body,extraKey),env,now)).status,403);
  assert.equal((await handleRequest(request(body),env,now)).status,200);
  assert.equal(env.CONTRIBUTOR_HASHES,ownerList);
  const health=await (await handleRequest(new Request('https://collector.test/health'),env,now)).json();
  assert.equal(health.additionalContributorKeysSupported,true);assert.equal(health.ready,true);
  assert.ok(!JSON.stringify(health).includes(ownerList));assert.ok(!JSON.stringify(health).includes(token));
 }finally{db.close();}
});

test('malformed or raw-key extra secrets never authorize upload and do not disable a valid owner list',async()=>{
 const db=new D1();try {
  const env=await environment(db),unknown='unapproved-contributor-'.padEnd(64,'u');
  const body={protocol:COMMUNITY_PROTOCOL,samples:await exportSamples([source()],'x'.repeat(64),now)};
  for(const bad of ['malformed','{}',JSON.stringify([unknown]),JSON.stringify([await hash(unknown),null]),JSON.stringify(Array(1001).fill('a'.repeat(64)))]) {
   env.CONTRIBUTOR_HASHES_EXTRA=bad;
   assert.equal((await handleRequest(request(body),env,now)).status,200);
   assert.equal((await handleRequest(request(body,unknown),env,now)).status,503);
  }
  assert.equal(db.sql.prepare('SELECT count(*) AS n FROM samples').get().n,1);
  env.CONTRIBUTOR_HASHES_EXTRA=JSON.stringify([await hash(unknown)]);env.CONTRIBUTOR_HASHES='malformed';
  assert.equal((await handleRequest(request(body,unknown),env,now)).status,200);
  delete env.CONTRIBUTOR_HASHES;
  assert.equal((await handleRequest(request(body,unknown),env,now)).status,200);
  const health=await (await handleRequest(new Request('https://collector.test/health'),env,now)).json();assert.equal(health.ready,true);
 }finally{db.close();}
});

test('browser-editor collector bundle has no imports and preserves both approval lists',async()=>{
 const {standaloneSource}=await import('../gameplay-collector/package-update.mjs');
 const code=standaloneSource();assert.ok(!/^import /m.test(code));
 const bundled=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
 const db=new D1();try {
  const env=await environment(db),extra='browser-extra-'.padEnd(64,'z');env.CONTRIBUTOR_HASHES_EXTRA=JSON.stringify([await hash(extra)]);
  const body={protocol:COMMUNITY_PROTOCOL,samples:await exportSamples([source()],'x'.repeat(64),now)};
  assert.equal((await bundled.handleRequest(request(body),env,now)).status,200);
  assert.equal((await bundled.handleRequest(request(body,extra),env,now)).status,200);
  const health=await (await bundled.handleRequest(new Request('https://collector.test/health'),env,now)).json();
  assert.equal(health.additionalContributorKeysSupported,true);
 }finally{db.close();}
});
