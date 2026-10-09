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
  account:{connected:true,name:'Local preview'},status:{state:'RUNNING',mode:'BOTH',action:'Monitoring orders and eligible flips',purse:12400000,committed:950000,funded:950000,capitalLimit:2000000,reserve:10000000,taxPercentage:1.25},
  inventory:[{slot:0,name:'Enchanted Coal',id:'ENCHANTED_COAL',count:16},{slot:9,name:'Overload IV',id:'ENCHANTMENT_OVERLOAD_4',count:1},{slot:10,name:'<img src=x onerror=alert(1)>',count:1}],
  books:{tasks:[{item:'ENCHANTMENT_OVERLOAD',state:'IN_BUY_ORDER',remaining:1,inputLevel:4,outputLevel:5,plannedCost:10000,holdings:[]}],slotMemory:{regions:[{region:1,observedAt:clock-20000,currentSlots:Array.from({length:54},(_,i)=>i),previousSlots:Array.from({length:54},(_,i)=>i),current:[{slot:3,item:{name:'Overload IV',count:1}}],previous:[{slot:4,item:{name:'Previous book',count:1}}]}]}},
  general:{positions:[{item:'ENCHANTED_COAL',stage:'SELL_ORDER',units:16,purchasePriceKnown:true,cost:32000,sellPrice:2500}]},
  profit:{profit:83500,settlements:6,incomplete:0,activeMillis:600000},analysis:{report:{marketAt:clock,historyUsed:true,rows:[
    {kind:'BOOK',routeKey:'ENCHANTMENT_OVERLOAD:4:5',inputId:'ENCHANTMENT_OVERLOAD_4',outputId:'ENCHANTMENT_OVERLOAD_5',inputUnits:2,capitalUsed:20000,limitedBy:'buy fill',priceBasis:'current offer',inputName:'Overload IV',outputName:'Overload V',batch:1,profitPerBatch:12000,coinsPerHour:48000,cycleSeconds:900,confidence:'MEASURED',configured:true},
    {kind:'GENERAL',routeKey:'ENCHANTED_COAL',inputId:'ENCHANTED_COAL',outputId:'ENCHANTED_COAL',inputUnits:16,capitalUsed:32000,limitedBy:'sell fill',priceBasis:'current offer',inputName:'Enchanted Coal',outputName:'Enchanted Coal',batch:16,profitPerBatch:7500,coinsPerHour:30000,cycleSeconds:900,confidence:'ESTIMATED',configured:true}]}}};
account.analysis.automaticSelection=true;
account.analysis.report.rows[0].executionEvidence={samples:30,pendingSamples:1,censoredSamples:2,profitRealizationFactor:0.8,throughputFactor:0.75,marketCoinsPerHour:64000,observedCoinsPerHour:47000};
account.analysis.report.rows[0].volumeEvidence={inputWeeklyAveragePerDay:14000,outputWeeklyAveragePerDay:7000,inputRecentPerDay:12000,outputRecentPerDay:6000,inputObservationHours:12,outputObservationHours:12};
account.analysis.pipeline={status:'READY',executionAuthority:false,expiresAt:clock+60000,reason:'Remaining coins do not fit another reported eligible batch',
  account:{available:1050000,pending:20000,inventoryCapacity:29,bookSlots:1,generalSlots:2},plannedCapital:400000,capitalLeft:650000,
  next:[{priority:1,route:{kind:'GENERAL',inputId:'ENCHANTED_QUARTZ',outputId:'ENCHANTED_QUARTZ',inputUnits:16,capitalUsed:400000,profitPerBatch:7500,cycleSeconds:900,confidence:'ESTIMATED'}}],
  deferred:[{routeKey:'EXPENSIVE',reason:'Insufficient spendable capital'}]};
account.production={generatedAt:clock,error:null,rankingNote:'Score is not realized coins/hour',rows:[{name:'Enchanted Gold',venue:'BAZAAR',batches:2,outputUnits:2,capital:50000,profit:10000,eligible:true},{name:'Grappling Hook',venue:'AH',batches:1,outputUnits:1,capital:100000,profit:20000,eligible:false,reason:'AH sale/expiry/claim reconciliation is not implemented'}]};
dashboard.accept(account);
const community={status:()=>({sharingEnabled:true,downloadsEnabled:true,imported:12,pending:2,lastUpload:clock,error:null})};
const server=createCompanion({dashboard,community,collector:{status:()=>({enabled:true,fresh:true,intervalSeconds:20}),history:()=>({asOf:clock}),quote:()=>({ask:20000,sourceAt:clock})}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
const product=(bid,ask)=>({sell_summary:[{pricePerUnit:bid,amount:1000,orders:10}],buy_summary:[{pricePerUnit:ask,amount:1000,orders:10}],
 quick_status:{buyMovingWeek:100000,sellMovingWeek:100000,buyVolume:1000,sellVolume:1000,buyOrders:10,sellOrders:10}});
const forecastRequest={protocol:'goofy-bazaar-shadow/1',requestId:'browser-portfolio',market:{success:true,lastUpdated:clock,
 products:{ENCHANTED_COAL:product(1000,1300),ENCHANTMENT_OVERLOAD_4:product(10000,11000),ENCHANTMENT_OVERLOAD_5:product(25000,28000)}},
 constraints:{mode:'BOTH',automaticSelection:true,skills:{enchanting:60},availableCapital:1050000,inventoryCapacity:29,maxRecommendations:50,maxHistoryAgeHours:48,
 taxPercentage:1.25,bookMinProfit:0,checkSeconds:20,bookCheckSeconds:180,clickDelayMs:350,bookSlots:1,generalSlots:2,
 general:{maxCoinsPerItem:2000000,maxItemsPerOrder:32,minProfitPerBatch:0,minMarginPercentage:0,minWeeklyVolume:0},excludedProducts:[],configuredBookRoutes:[],configuredGeneralItems:[]}};
forecastRequest.rankingConstraints={...forecastRequest.constraints,availableCapital:2000000,inventoryCapacity:32};
account.analysis.comparison={rankingCapital:2000000,rankingInventoryCapacity:32};
assert.equal((await fetch(`${base}/v1/recommendations`,{method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Analysis':'shadow-v1'},body:JSON.stringify(forecastRequest)})).status,200);
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
    const result=await evaluate(`({overflow:document.documentElement.scrollWidth-document.documentElement.clientWidth,slots:document.querySelectorAll('#inventory-grid .inventory-slot').length,images:document.querySelectorAll('img').length,name:document.getElementById('account-name').textContent,rows:document.getElementById('prediction-rows').children.length,profit:document.getElementById('confirmed').textContent})`);
    assert.match(await evaluate(`document.getElementById('community-note').textContent`),/12 imported outcomes.*uploads enabled.*2 pending/);
    assert.equal(result.overflow,0);assert.equal(result.slots,36);assert.equal(result.images,0);assert.equal(result.name,'Local preview');assert.equal(result.rows,2);assert.match(result.profit,/83/);assert.match(await evaluate(`document.getElementById('pipeline-rows').textContent`),/ENCHANTED_QUARTZ/);assert.match(await evaluate(`document.getElementById('pipeline-deferred').textContent`),/Insufficient spendable capital/);assert.notEqual(await evaluate(`document.getElementById('position-profit').textContent`),'—');assert.match(await evaluate(`document.getElementById('position-profit-note').textContent`),/2 priced/);
    await evaluate(`document.getElementById('storage-view').value='previous';document.getElementById('storage-view').dispatchEvent(new Event('input'));`);
    assert.match(await evaluate(`document.getElementById('storage').textContent`),/Previous book/);
    assert.equal(await evaluate(`document.querySelectorAll('.storage-slot').length`),54);
    assert.match(await evaluate(`document.querySelectorAll('.storage-slot')[4].textContent`),/Previous book/);
    assert.match(await evaluate(`document.querySelectorAll('.storage-slot')[3].title`),/empty in this snapshot/);
    await evaluate(`document.getElementById('storage-view').value='current';document.getElementById('storage-view').dispatchEvent(new Event('input'));document.getElementById('order-filter').value='books';document.getElementById('order-filter').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.getElementById('order-rows').children.length`),1);
    await evaluate(`document.getElementById('order-filter').value='all';document.getElementById('order-filter').dispatchEvent(new Event('input'));document.getElementById('inventory-search').value='Coal';document.getElementById('inventory-search').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.querySelectorAll('#inventory-grid .inventory-slot.faded').length`),2);
    assert.equal(await evaluate(`document.querySelectorAll('.storage-slot.faded').length`),1);
    await evaluate(`document.getElementById('inventory-search').value='';document.getElementById('inventory-search').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.getElementById('craft-rows').children.length`),2);
    assert.match(await evaluate(`document.getElementById('craft-rows').textContent`),/Eligible for CRAFT mode/);
    assert.match(await evaluate(`document.getElementById('craft-rows').textContent`),/AH sale\/expiry\/claim reconciliation/);
    assert.equal(await evaluate(`document.querySelectorAll('.flip-card').length`),2);
    assert.notEqual(await evaluate(`document.getElementById('predicted').textContent`),'—');
    assert.equal(await evaluate(`document.getElementById('committed').textContent`),'950,000');
    assert.match(await evaluate(`document.getElementById('budget').textContent`),/future inputs 0/);
    assert.notEqual(await evaluate(`document.getElementById('live-predicted').textContent`),'—');
    assert.notEqual(await evaluate(`document.getElementById('measured-rate').textContent`),'—');
    assert.ok(await evaluate(`document.getElementById('portfolio-rows').children.length>=1`));
    await evaluate(`document.getElementById('route-engine').value='BOOK';document.getElementById('route-engine').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.getElementById('prediction-rows').children.length`),1);
    await evaluate(`document.querySelector('#prediction-rows button').click();`);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/ENCHANTMENT_OVERLOAD_4/);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/Book combine operations1/);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/Automatic selection/);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/30 own completed · 0 similar-volume trades · 1 open · 2 retired/);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/Input recent daily rate12,000 · 12 h observed/);
    assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/Observed gameplay coins\/hour/);
    await evaluate(`document.getElementById('favorite-route').click();document.getElementById('route-engine').value='all';document.getElementById('route-scope').value='favorites';document.getElementById('route-scope').dispatchEvent(new Event('input'));`);
    assert.equal(await evaluate(`document.getElementById('prediction-rows').children.length`),1);
    assert.match(await evaluate(`document.getElementById('favorite-route').textContent`),/Remove favorite/);
    await evaluate(`document.getElementById('theme').click();`);
    const selectedTheme=await evaluate(`document.documentElement.dataset.theme`);
    await send('Page.reload');await new Promise(r=>setTimeout(r,700));
    assert.equal(await evaluate(`document.documentElement.dataset.theme`),selectedTheme);
    assert.equal(await evaluate(`document.getElementById('route-scope').value`),'favorites');
    assert.equal(await evaluate(`document.getElementById('prediction-rows').children.length`),1);
    await evaluate(`document.querySelector('#prediction-rows button').click();document.getElementById('favorite-route').click();document.getElementById('clear-filters').click();document.getElementById('route-sort').value='capital';document.getElementById('route-sort').dispatchEvent(new Event('input'));`);
    assert.match(await evaluate(`document.getElementById('prediction-rows').firstChild.textContent`),/Overload/);
    await evaluate(`document.getElementById('route-search').value='no-such-item';document.getElementById('route-search').dispatchEvent(new Event('input'));`);
    assert.match(await evaluate(`document.getElementById('prediction-rows').textContent`),/No matches/);
    assert.match(await evaluate(`document.getElementById('pipeline-rows').textContent`),/ENCHANTED_QUARTZ/);
    await evaluate(`document.getElementById('clear-filters').click();document.getElementById('refresh').click();`);await new Promise(r=>setTimeout(r,100));
    assert.equal(await evaluate(`document.getElementById('allocation-bar').getAttribute('aria-valuenow')`),'38');
    const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});await writeFile(join(output,`dashboard-${width}.png`),Buffer.from(shot.result.data,'base64'));
    console.log(`PASS ${width}px: best-flip filters, inspection, favorites/theme persistence, allocation, refresh and no overflow/XSS`);
  }
  clock+=1;account.sentAt=clock;account.analysis.pipeline={status:'WAITING',reason:'Active-position limits reached; waiting for a position to finish',next:[],deferred:[],
    account:{mode:'BOTH',ready:true,bookSlots:0,generalSlots:0,available:0,inventoryCapacity:0}};
  account.analysis.report.rows[0].executionEvidence.sharedSamples=12;dashboard.accept(account);
  await evaluate(`document.getElementById('refresh').click();`);await new Promise(r=>setTimeout(r,100));
  assert.equal(await evaluate(`document.querySelectorAll('.flip-card').length`),2);
  assert.match(await evaluate(`document.getElementById('pipeline-rows').textContent`),/Active-position limits/);
  await evaluate(`document.getElementById('route-engine').value='BOOK';document.getElementById('route-engine').dispatchEvent(new Event('input'));document.querySelector('#prediction-rows button').click();`);
  assert.match(await evaluate(`document.getElementById('detail-values').textContent`),/12 similar-volume trades/);
  console.log('PASS full capacity preserves live rankings and peer evidence while pipeline waits');
  account.status.mode='CRAFT';account.sentAt=++clock;dashboard.accept(account);await new Promise(r=>setTimeout(r,2200));
  assert.equal(await evaluate(`document.getElementById('predicted').textContent`),'—');
  assert.match(await evaluate(`document.getElementById('craft-rows').textContent`),/Enchanted Gold/);
  console.log('PASS CRAFT mode renders live craft plans and suppresses book/general portfolio forecasts');
  clock+=11000;await new Promise(r=>setTimeout(r,2200));
  assert.match(await evaluate(`document.getElementById('notice').textContent`),/offline or stale/);
  assert.equal(await evaluate(`document.getElementById('predicted').textContent`),'—');assert.equal(await evaluate(`document.getElementById('live-predicted').textContent`),'—');assert.doesNotMatch(await evaluate(`document.getElementById('pipeline-rows').textContent`),/ENCHANTED_QUARTZ/);
  assert.equal(await evaluate(`document.getElementById('position-profit').textContent`),'—');assert.doesNotMatch(await evaluate(`document.getElementById('craft-rows').textContent`),/Enchanted Gold/);assert.deepEqual(errors,[]);assert.equal(await evaluate(`document.querySelectorAll('.flip-card').length`),0);assert.equal(await evaluate(`document.getElementById('favorite-route').disabled`),true);console.log('PASS stale account hides cards, details, forecasts and plan; no browser exceptions');
} finally {
  ws?.close();const exited=once(chrome,'exit');chrome.kill();await exited;
  await new Promise(r=>{server.close(r);server.closeAllConnections();});await rm(profile,{recursive:true,force:true});
}
