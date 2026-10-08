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
const account={protocol:'goofy-profile/1',username:'Tester',profiles:[{id:'fixture',name:'Apple',selected:true,gameMode:'normal',purse:2500000,fetchedAt:Date.now(),unknown:['current XP levels'],stats:{hotmTier:10,quickForgeLevel:20,enchantingLevel:60,collections:{},skills:{Taming:50,Foraging:50},slayers:{},reputation:{},xpLevels:0,ignoreRequirements:false}}]};
const collector={market:()=>({success:true,lastUpdated:Date.now(),products}),history:()=>({stats:{},hold:{},names:{},asOf:Date.now()}),status:()=>({enabled:true})};
const server=createCompanion({collector,profileFetcher:async()=>Response.json(account),resourcesFetcher:async()=>Response.json({success:true,items:[]})});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const dir=process.argv[2]??'/tmp/goofy-full-calculator-preview';await mkdir(dir,{recursive:true});
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
 for(const width of [1440,390]) {
  await send('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:width<600});
  await send('Page.navigate',{url:base+'/calculator/'});await new Promise(r=>setTimeout(r,1800));
  assert.ok(await evaluate(`document.body.innerText.includes('no macro needed')`));
  await evaluate(`const input=document.querySelector('input[aria-label="Minecraft username"]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'Tester');input.dispatchEvent(new Event('input',{bubbles:true}));`);
  await new Promise(r=>setTimeout(r,50));await evaluate(`document.querySelector('section[aria-label="Account calculator"] form').requestSubmit()`);
  await new Promise(r=>setTimeout(r,1500));
  assert.ok(await evaluate(`document.querySelector('[role="status"]').innerText.includes('2,500,000')`));
  const saved=await evaluate(`JSON.parse(localStorage.getItem('bazaar-calc.v1'))`);assert.equal(saved.settings.coins,2500000);assert.equal(saved.profile.enchantingLevel,60);assert.equal(saved.profile.ignoreRequirements,false);
  const result=await evaluate(`(async()=>{const c=await import('/calculator/assets/${client}');const state=JSON.parse(localStorage.getItem('bazaar-calc.v1'));return c.staticApi('/api/v1/calc/plan',{...state,options:{kinds:['bazaar','book'],requireMet:true}})})()`);
  assert.ok(result.picks.length>0);assert.ok(result.picks.reduce((sum,p)=>sum+p.capitalUsed,0)<=2500000.01,'Portfolio exceeds purse');assert.ok(result.picks.every(p=>p.unmet.length===0),'Blocked requirements in plan');
  const resultLow=await evaluate(`(async()=>{const c=await import('/calculator/assets/${client}');const state=JSON.parse(localStorage.getItem('bazaar-calc.v1'));return c.staticApi('/api/v1/calc/plan',{...state,settings:{...state.settings,coins:10000},options:{kinds:['bazaar','book'],requireMet:true}})})()`);
  assert.ok(resultLow.picks.reduce((sum,p)=>sum+p.capitalUsed,0)<=10000.01);
  const layout=await evaluate(`({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth})`);assert.ok(layout.scroll<=layout.width+1,`Overflow at ${width}: ${JSON.stringify(layout)}`);
  const shot=await send('Page.captureScreenshot',{format:'png'});await writeFile(join(dir,`calculator-${width}.png`),Buffer.from(shot.result.data,'base64'));
  for(const kind of ['bazaar','craft','book','forge','npc','kat','fusion']){
   await send('Page.navigate',{url:base+`/calculator/flips/${kind}`});await new Promise(r=>setTimeout(r,600));
   assert.ok(!(await evaluate(`document.body.innerText`)).includes('Page not found'),`Missing category ${kind}`);
  }
 }
 assert.deepEqual(errors,[]);console.log('Full calculator: desktop/mobile, username/profile import, all seven categories, purse budgets and requirement filtering passed.');
}finally{ws?.close();chrome.kill();await once(chrome,'exit');await new Promise(r=>{server.close(r);server.closeAllConnections();});await rm(profile,{recursive:true,force:true});}
