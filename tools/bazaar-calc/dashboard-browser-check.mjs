// Optional real Chromium check with synthetic account observations; no live Hypixel requests.
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdir, mkdtemp, rm, writeFile, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { createCompanion } from './server.mjs';
import { DashboardState } from './dashboard-state.mjs';
let clock=Date.now();const dashboard=new DashboardState(()=>clock);
const account={protocol:'goofy-dashboard/1',sessionId:'browser-check',sentAt:clock,
  account:{connected:true,name:'Local preview'},status:{state:'RUNNING',mode:'BOTH',action:'Monitoring orders and eligible flips',purse:12400000,committed:950000,capitalLimit:2000000,reserve:10000000,taxPercentage:1.25},
  inventory:[{slot:0,name:'Enchanted Coal',id:'ENCHANTED_COAL',count:16},{slot:9,name:'Overload IV',id:'ENCHANTMENT_OVERLOAD_4',count:1},{slot:10,name:'<img src=x onerror=alert(1)>',count:1}],
  books:{tasks:[{item:'ENCHANTMENT_OVERLOAD',state:'IN_BUY_ORDER',remaining:1,outputLevel:5,plannedCost:10000,holdings:[]}],slotMemory:{regions:[{region:1,observedAt:clock-20000,current:[{slot:3,item:{name:'Overload IV',count:1}}],previous:[{slot:4,item:{name:'Previous book',count:1}}]}]}},
  general:{positions:[{item:'ENCHANTED_COAL',stage:'SELL_ORDER',units:16,purchasePriceKnown:true,cost:32000,sellPrice:2500}]},
  profit:{profit:83500,settlements:6,incomplete:1},analysis:{report:{marketAt:clock,historyUsed:true,rows:[
    {inputName:'Overload IV',outputName:'Overload V',batch:1,profitPerBatch:12000,coinsPerHour:48000,cycleSeconds:900,confidence:'MEASURED',configured:true},
    {inputName:'Enchanted Coal',outputName:'Enchanted Coal',batch:16,profitPerBatch:7500,coinsPerHour:30000,cycleSeconds:900,confidence:'ESTIMATED',configured:true}]}}};
account.analysis.pipeline={status:'READY',executionAuthority:false,expiresAt:clock+60000,reason:'Remaining coins do not fit another reported eligible batch',
  account:{available:1050000,pending:20000,inventoryCapacity:29,bookSlots:1,generalSlots:2},plannedCapital:400000,capitalLeft:650000,
  next:[{priority:1,route:{kind:'GENERAL',inputId:'ENCHANTED_QUARTZ',outputId:'ENCHANTED_QUARTZ',inputUnits:16,capitalUsed:400000,profitPerBatch:7500,cycleSeconds:900,confidence:'ESTIMATED'}}],
  deferred:[{routeKey:'EXPENSIVE',reason:'Insufficient spendable capital'}]};
dashboard.accept(account);
const server=createCompanion({dashboard,collector:{status:()=>({enabled:true,fresh:true,intervalSeconds:20}),history:()=>({asOf:clock}),quote:()=>({ask:20000,sourceAt:clock})}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const output=process.argv[2] ?? '/tmp/goofy-dashboard-preview';await mkdir(output,{recursive:true});
const profile=await mkdtemp(join(tmpdir(),'goofy-dashboard-chrome-'));
const chrome=spawn(process.env.CHROME ?? 'chromium',['--headless=new','--no-sandbox','--disable-dev-shm-usage','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'});
let ws;
try {
  let port;
  for(let i=0;i<50;i++){try{port=Number((await readFile(join(profile,'DevToolsActivePort'),'utf8')).split('\n')[0]);break;}catch{await new Promise(r=>setTimeout(r,100));}}
  assert.ok(port,'Chromium did not start');
  const target=(await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find(t=>t.type==='page');
  ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r));
  let id=0;const pending=new Map(),errors=[];
  ws.addEventListener('message',event=>{const m=JSON.parse(event.data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.text);});
  const send=(method,params={})=>new Promise(r=>{const n=++id;pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}));});
  const evaluate=async expression=>(await send('Runtime.evaluate',{expression,returnByValue:true})).result.result.value;
  await send('Runtime.enable');await send('Page.enable');
  for(const width of [1440,390]){
    clock=Date.now();account.sentAt=clock;account.analysis.report.marketAt=clock;account.analysis.pipeline.expiresAt=clock+60000;dashboard.accept(account);
    await send('Emulation.setDeviceMetricsOverride',{width,height:1100,deviceScaleFactor:1,mobile:width<600});
    await send('Page.navigate',{url:base});await new Promise(r=>setTimeout(r,700));
    const result=await evaluate(`({overflow:document.documentElement.scrollWidth-document.documentElement.clientWidth,slots:document.querySelectorAll('.inventory-slot').length,images:document.querySelectorAll('img').length,name:document.getElementById('account-name').textContent,rows:document.getElementById('prediction-rows').children.length,profit:document.getElementById('confirmed').textContent})`);
    assert.equal(result.overflow,0);assert.equal(result.slots,36);assert.equal(result.images,0);assert.equal(result.name,'Local preview');assert.equal(result.rows,2);assert.match(result.profit,/83/);assert.match(await evaluate(`document.getElementById('pipeline-rows').textContent`),/ENCHANTED_QUARTZ/);assert.match(await evaluate(`document.getElementById('pipeline-deferred').textContent`),/Insufficient spendable capital/);assert.notEqual(await evaluate(`document.getElementById('position-profit').textContent`),'—');assert.match(await evaluate(`document.getElementById('position-profit-note').textContent`),/2 priced/);
    await evaluate(`document.getElementById('storage-view').value='previous';document.getElementById('storage-view').dispatchEvent(new Event('input'));`);
    assert.match(await evaluate(`document.getElementById('storage').textContent`),/Previous book/);
    await evaluate(`document.getElementById('storage-view').value='current';document.getElementById('storage-view').dispatchEvent(new Event('input'));document.getElementById('order-filter').value='books';document.getElementById('order-filter').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.getElementById('order-rows').children.length`),1);
    await evaluate(`document.getElementById('order-filter').value='all';document.getElementById('order-filter').dispatchEvent(new Event('input'));document.getElementById('inventory-search').value='Coal';document.getElementById('inventory-search').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.querySelectorAll('.inventory-slot.faded').length`),2);
    await evaluate(`document.getElementById('inventory-search').value='';document.getElementById('inventory-search').dispatchEvent(new Event('input'));`);
    const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});await writeFile(join(output,`dashboard-${width}.png`),Buffer.from(shot.result.data,'base64'));
    console.log(`PASS ${width}px: observed account, inventory, positions, predictions, filters and no overflow/XSS`);
  }
  clock+=11000;await new Promise(r=>setTimeout(r,2200));
  assert.match(await evaluate(`document.getElementById('notice').textContent`),/offline or stale/);
  assert.equal(await evaluate(`document.getElementById('predicted').textContent`),'—');assert.doesNotMatch(await evaluate(`document.getElementById('pipeline-rows').textContent`),/ENCHANTED_QUARTZ/);
  assert.equal(await evaluate(`document.getElementById('position-profit').textContent`),'—');assert.deepEqual(errors,[]);console.log('PASS stale account hides predicted profit; no browser exceptions');
} finally {
  ws?.close();const exited=once(chrome,'exit');chrome.kill();await exited;
  await new Promise(r=>{server.close(r);server.closeAllConnections();});await rm(profile,{recursive:true,force:true});
}
