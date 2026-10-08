// Real Chromium verification of the bundled calculator, using synthetic public market/profile fixtures only.
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdir,mkdtemp,readFile,readdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import assert from 'node:assert/strict';
import {createCompanion} from './server.mjs';
const products=Object.fromEntries(Array.from({length:500},(_,i)=>[`TEST_${i}`,{
 product_id:`TEST_${i}`,sell_summary:[{pricePerUnit:100,amount:1000,orders:10}],buy_summary:[{pricePerUnit:150,amount:1000,orders:10}],
 quick_status:{buyMovingWeek:100000,sellMovingWeek:100000,buyVolume:1000,sellVolume:1000,buyOrders:10,sellOrders:10}}]));
for(let level=1;level<=5;level++) {
 const id=`ENCHANTMENT_WISDOM_${level}`,price=level===5?2500:100*2**(level-1);
 products[id]={...products.TEST_0,product_id:id,sell_summary:[{pricePerUnit:price,amount:100,orders:10}],buy_summary:[{pricePerUnit:price*1.2,amount:100,orders:10}]};
}
let phase=0,marketReads=0,statusReads=0;
const collector={market:()=>{marketReads++;return {success:true,lastUpdated:Date.now(),products:Object.fromEntries(Object.entries(products).map(([id,p])=>[id,{...p,buy_summary:[{...p.buy_summary[0],pricePerUnit:p.buy_summary[0].pricePerUnit*(phase?2:1)}]}]))};},history:()=>({stats:{},hold:{},names:{},asOf:Date.now()}),status:()=>({enabled:true})};
const publishing=()=>({protocol:'goofy-publishing-status/1',repository:'abdyzam50-rgb/goofyaddons',branch:'gameplay-data',configured:true,result:phase?'UNCHANGED':'COMMITTED',lastAttemptAt:Date.now(),lastSuccessAt:Date.now(),lastCommitAt:Date.now()-60000,commitSha:'a'.repeat(40),storedSamples:50,publishedSamples:40,pendingSamples:phase?2:0,nextScheduledAt:Date.now()+900000,checkedAt:Date.now()});
const server=createCompanion({collector,publishingFetcher:async()=>{statusReads++;return Response.json(publishing());},resourcesFetcher:async()=>Response.json({success:true,items:[]})});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const dir=process.argv[2]??'/tmp/goofy-refresh-preview';await mkdir(dir,{recursive:true});
const profile=await mkdtemp(join(tmpdir(),'goofy-calculator-chrome-'));
const chrome=spawn(process.env.CHROME??'chromium',['--headless=new','--no-sandbox','--disable-dev-shm-usage','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'});
let ws;
try {
 let port;for(let i=0;i<50;i++){try{port=Number((await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]);break;}catch{await new Promise(r=>setTimeout(r,100));}}
 const target=(await(await fetch(`http://127.0.0.1:${port}/json`)).json()).find(t=>t.type==='page');
 ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r));
 let id=0;const pending=new Map(),errors=[];
 ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.exception?.description??m.params.exceptionDetails.text);});
 const send=(method,params={})=>new Promise(r=>{const n=++id;pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}));});
 const evaluate=async(expression)=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.result.exceptionDetails)throw new Error(r.result.exceptionDetails.exception?.description);return r.result.result.value;};
 await send('Runtime.enable');await send('Page.enable');
 const assets=await readdir(new URL('./calculator/assets/',import.meta.url)),client=assets.find(f=>/^client-.*\.js$/.test(f));

 const text=()=>evaluate('document.body.innerText');
 await send('Page.navigate',{url:base+'/calculator/status'});await new Promise(r=>setTimeout(r,2500));
 assert.match(await text(),/Gameplay data → GitHub/);assert.match(await text(),/COMMITTED/);
 assert.ok(await evaluate(`!!document.querySelector('a[href="https://github.com/abdyzam50-rgb/goofyaddons/commit/${'a'.repeat(40)}"]')`));
 const before=marketReads,oldStatus=statusReads;
 const plan=()=>evaluate(`(async()=>{const c=await import('/calculator/assets/${client}');return c.staticApi('/api/v1/calc/plan',{settings:{coins:2500000},profile:{ignoreRequirements:true},options:{kinds:['bazaar','book'],requireMet:false}})})()`);
 const initial=await plan();assert.ok(initial.picks.length);
 phase=1;
 const deadline=Date.now()+38000;while((marketReads<=before || statusReads<=oldStatus)&&Date.now()<deadline)await new Promise(r=>setTimeout(r,500));
 assert.ok(marketReads>before,'Live worker did not fetch another quote');assert.ok(statusReads>oldStatus,'Status did not poll again');
 assert.match(await text(),/UNCHANGED/);const fresh=await plan();assert.ok(fresh.dataAt>initial.dataAt);assert.notEqual(fresh.totals.coinsH,initial.totals.coinsH,'Plan stayed frozen after quote update');
 for(const route of ['flips/bazaar','flips/craft','flips/book','flips/forge','flips/npc','flips/kat','flips/fusion','orders','alerts','record','outlook','dips','items','events','timing','api-docs','status','about','contribute']) {
  await send('Page.navigate',{url:base+'/calculator/'+route});await new Promise(r=>setTimeout(r,750));
  const body=await text();assert.ok(body.length>100,`Empty ${route}`);assert.doesNotMatch(body,/Page not found/);
 }
 await send('Page.navigate',{url:base+'/calculator/status'});await new Promise(r=>setTimeout(r,1200));
 await evaluate(`Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'hidden'});document.dispatchEvent(new Event('visibilitychange'));`);
 await new Promise(r=>setTimeout(r,200));assert.match(await text(),/paused/);
 const hiddenCount=marketReads;
 await evaluate(`Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'visible'});document.dispatchEvent(new Event('visibilitychange'));`);
 await new Promise(r=>setTimeout(r,1500));assert.ok(marketReads>hiddenCount,'Returning to a tab did not resume prices');
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:1100,deviceScaleFactor:1,mobile:true});
 const layout=await evaluate(`({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth})`);assert.ok(layout.scroll<=layout.width+1,'Mobile status overflow');
 const shot=await send('Page.captureScreenshot',{format:'png'});await writeFile(join(dir,'publishing-status-mobile.png'),Buffer.from(shot.result.data,'base64'));
 assert.deepEqual(errors,[]);console.log('Passed: live status polling, commit links, changed-price plan recalculation, 19 pages, background resume, mobile layout.');
}finally{ws?.close();chrome.kill();await once(chrome,'exit');await new Promise(r=>{server.close(r);server.closeAllConnections();});await rm(profile,{recursive:true,force:true});}
