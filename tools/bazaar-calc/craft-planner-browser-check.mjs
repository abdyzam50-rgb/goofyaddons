// Real Chromium verification of the bundled calculator, using synthetic public market/profile fixtures only.
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdir,mkdtemp,readFile,readdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import assert from 'node:assert/strict';
import {createCompanion} from './server.mjs';
let phase=0;
const product=(id,ask,bid)=>({product_id:id,buy_summary:[{pricePerUnit:ask,amount:100000,orders:10}],sell_summary:[{pricePerUnit:bid,amount:100000,orders:10}],quick_status:{buyMovingWeek:700000,sellMovingWeek:700000,buyVolume:100000,sellVolume:100000,buyOrders:10,sellOrders:10}});
const products={BLAZE_ROD:product('BLAZE_ROD',10,9),ENCHANTED_ENDER_PEARL:product('ENCHANTED_ENDER_PEARL',100,90),ENCHANTED_EYE_OF_ENDER:product('ENCHANTED_EYE_OF_ENDER',100000,90000),ENCHANTED_COBBLESTONE:product('ENCHANTED_COBBLESTONE',100,90),ENCHANTED_REDSTONE:product('ENCHANTED_REDSTONE',100,90),ENCHANTED_IRON:product('ENCHANTED_IRON',100,90)};
const account={protocol:'goofy-profile/1',username:'Tester',profiles:[{id:'fixture',name:'Apple',selected:true,gameMode:'normal',purse:2500000,fetchedAt:Date.now(),unknown:[],stats:{hotmTier:10,quickForgeLevel:20,enchantingLevel:60,collections:{'Ender Pearl':6},skills:{},slayers:{Wolf:3},reputation:{},xpLevels:0,ignoreRequirements:false}}]};
const collector={market:()=>({success:true,lastUpdated:Date.now(),products:{...products,ENCHANTED_EYE_OF_ENDER:product('ENCHANTED_EYE_OF_ENDER',100000,phase?70000:90000)}}),history:()=>({stats:{},hold:{},names:{},asOf:Date.now()}),status:()=>({enabled:true})};
const server=createCompanion({collector,publishingFetcher:async url=>Response.json(url.endsWith('/history')?{protocol:'goofy-ah-history/1',generatedAt:Date.now(),rows:[{item:'ASPECT_OF_THE_END',price:5000000,lastAt:Date.now()-86400000,volume:42,demandAt:Date.now()-86400000}]}:{protocol:'goofy-ah-status/1',collection:{},publishing:{},items:0,observations:0}),profileFetcher:async()=>Response.json(account),resourcesFetcher:async()=>Response.json({success:true,items:[]}),auctionToken:()=>null,auctionFetcher:async url=>url.includes('ASPECT_OF_THE_END')?new Response('',{status:404}):url.endsWith('/profit')?Response.json([{itemId:'AATROX_BATPHONE',type:'crafting',sellPrice:100000,craftCost:900,median:100000,volume:100,lastUpdated:new Date().toISOString()}]):Response.json({lowest:100000,secondLowest:110000,uuid:'never-display-this'})});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const dir=process.argv[2]??'/tmp/goofy-public-craft-preview';await mkdir(dir,{recursive:true});
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
 for(const width of [1440,390]) {
  phase=0;
  await send('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:width<600});
  await send('Page.navigate',{url:base+'/calculator/flips/craft'});await new Promise(r=>setTimeout(r,1800));
  assert.ok(await evaluate(`document.body.innerText.includes('Live craft plans')`));
  const initial=await evaluate(`document.querySelector('section[aria-label="Live craft production plan"]').innerText`);
  if(width===1440)assert.ok(initial.includes('Confirm requirement'),initial);
  await evaluate(`const input=document.querySelector('input[aria-label="Minecraft username"]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'Tester');input.dispatchEvent(new Event('input',{bubbles:true}));`);
  await new Promise(r=>setTimeout(r,50));await evaluate(`document.querySelector('section[aria-label="Account calculator"] form').requestSubmit()`);await new Promise(r=>setTimeout(r,1200));
  assert.ok(await evaluate(`document.querySelector('[role="status"]').innerText.includes('2,500,000')`));
  const body=await evaluate(`document.querySelector('section[aria-label="Live craft production plan"]').innerText`);
  assert.ok(body.includes('Feasible under your budget and unlocks'),body);
  assert.ok(body.includes('AATROX_BATPHONE'),body);assert.ok(!body.includes('never-display-this'));
  await evaluate(`document.querySelectorAll('details').forEach(d=>d.open=true)`);
  assert.ok(await evaluate(`document.body.innerText.includes('Blaze Powder')&&document.body.innerText.includes('Blaze Rod')`));
  const before=await evaluate(`document.querySelector('tbody').innerText`);phase=1;
  await evaluate(`Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Refresh craft prices').click()`);await new Promise(r=>setTimeout(r,1000));
  assert.notEqual(await evaluate(`document.querySelector('tbody').innerText`),before,'Craft prices did not refresh');
  await evaluate(`const s=document.querySelector('select[aria-label="Craft sale market"]');s.value='AH';s.dispatchEvent(new Event('change',{bubbles:true}))`);await new Promise(r=>setTimeout(r,100));
  assert.ok(!(await evaluate(`document.querySelector('tbody').innerText`)).includes('ENCHANTED_EYE_OF_ENDER'));
  await evaluate(`document.querySelectorAll('details').forEach(d=>d.open=true)`);
  assert.ok(await evaluate(`document.body.innerText.includes('confirmed sale and claim')`));
  await evaluate(`{const input=document.querySelector('input[aria-label="Search crafts"]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'ASPECT_OF_THE_END');input.dispatchEvent(new Event('input',{bubbles:true}));}`);await new Promise(r=>setTimeout(r,800));
  assert.ok(await evaluate(`document.querySelector('tbody').innerText.includes('historical estimate')`));
  await evaluate(`{const input=document.querySelector('#fq');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'Hephaestus Relic');input.dispatchEvent(new Event('input',{bubbles:true}));}`);await new Promise(r=>setTimeout(r,1000));
  assert.equal(await evaluate(`document.querySelector('input[aria-label="Search crafts"]').value`),'Hephaestus Relic');
  assert.ok(await evaluate(`document.querySelector('section[aria-label="Live craft production plan"] tbody').innerText.includes('HEPHAESTUS_RELIC')`));
  await evaluate(`document.querySelector('section[aria-label="Live craft production plan"] tbody details').open=true`);
  assert.ok(await evaluate(`document.querySelector('section[aria-label="Live craft production plan"]').innerText.includes('Minos Relic')`));
  const layout=await evaluate(`({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth})`);assert.ok(layout.scroll<=layout.width+1,`Overflow at ${width}`);
  const shot=await send('Page.captureScreenshot',{format:'png'});await writeFile(join(dir,`craft-${width}.png`),Buffer.from(shot.result.data,'base64'));
  await evaluate(`Date.now=()=>${Date.now()+65000}`);await new Promise(r=>setTimeout(r,1300));
  assert.equal(await evaluate(`document.querySelector('tbody').children.length`),0,'Expired prices still recommend crafts');
 }
 for (const width of [1440,390]) {
  await send('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:width<600});
  for (const [route,expected] of [['timing','Refresh and retention'],['api-docs','Hosted endpoints'],['contribute','From the mod'],['status','Persistent Auction House market history'],['about','Auction House prices']]) {
   await send('Page.navigate',{url:base+'/calculator/'+route});await new Promise(r=>setTimeout(r,1200));
   const body=await evaluate('document.body.innerText');assert.ok(body.includes(expected),route+': '+body);
   assert.ok(!body.includes('This site has no server')&&!body.includes('No auction or NPC flips')&&!body.includes('Sign in with Discord'),route+' contains obsolete instructions');
   const layout=await evaluate('({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth})');assert.ok(layout.scroll<=layout.width+1,route+' overflow at '+width);
   if(route==='api-docs')for(const path of ['/v1/market','/v1/crafts/history','/calculator/data/production-recipes.json']){assert.equal((await fetch(base+path)).status,200,path);}
   if(route==='about')assert.equal((await fetch(base+'/calculator/licenses/NEU-CATALOG-LICENSE.txt')).status,200);
  }
 }
 assert.deepEqual(errors,[]);console.log('Public craft planner: desktop/mobile, profile unlocks, base preparations, BZ/AH filters, price refresh, stale-price removal and all five reference pages passed.');
}finally{ws?.close();chrome.kill();await once(chrome,'exit');await new Promise(r=>{server.close(r);server.closeAllConnections();});await rm(profile,{recursive:true,force:true});}
