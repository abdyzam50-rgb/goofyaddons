import {routeId,routeName,visibleRoutes,routeDisposition,workStages,storageCells} from './routes.mjs';
// Render only observed data using text nodes; item names never become HTML.
const $ = id => document.getElementById(id);
const number = new Intl.NumberFormat(undefined,{maximumFractionDigits:1});
const coins = v => Number.isFinite(v) ? number.format(v) : '—';
const batchProfit = p => p.profitPerBatch*(p.executionEvidence?.profitRealizationFactor??1);
const age = ts => ts ? `${Math.max(0,Math.floor((Date.now()-ts)/1000))}s ago` : 'not observed';
const put = (id,value) => { $(id).textContent=value; };
let state=null,selectedRoute=null,favorites=[],preferences={};
try {preferences=JSON.parse(localStorage.getItem('goofy-desk-preferences')||'{}')||{};}catch{}
if(Array.isArray(preferences.favorites))favorites=preferences.favorites.filter(x=>typeof x==='string').slice(0,250);
if(['dark','light'].includes(preferences.theme))document.documentElement.dataset.theme=preferences.theme;
for(const id of ['route-search','route-engine','route-scope','route-sort']) {
 const saved=preferences[id];if(typeof saved==='string' && (id==='route-search'||Array.from($(id).options).some(o=>o.value===saved)))$(id).value=saved;
}
function savePreferences(){
 const value={favorites,theme:document.documentElement.dataset.theme};for(const id of ['route-search','route-engine','route-scope','route-sort'])value[id]=$(id).value;
 try{localStorage.setItem('goofy-desk-preferences',JSON.stringify(value));}catch{}
}
function inspect(p){selectedRoute=routeId(p);put('copy-status','');render();$('route-detail').scrollIntoView({behavior:'smooth',block:'start'});}
function button(text,action){const b=document.createElement('button');b.type='button';b.className='route-link';b.textContent=text;b.addEventListener('click',action);return b;}
function renderRoutes(predictions) {
 const automatic=state.account?.analysis?.automaticSelection===true;
 const shown=visibleRoutes(predictions,{search:$('route-search').value,engine:$('route-engine').value,scope:$('route-scope').value,sort:$('route-sort').value,favorites});
 const cards=$('best-flips');cards.replaceChildren();
 for(const [i,p]of shown.slice(0,3).entries()){
  const card=document.createElement('article');card.className='card pad flip-card';
  const rank=document.createElement('span');rank.className='eyebrow';rank.textContent=`#${i+1} · ${p.kind==='BOOK'?'Book combine':'General flip'}`;
  const title=document.createElement('h3');title.append(button(`${favorites.includes(routeId(p))?'★ ':''}${routeName(p)}`,()=>inspect(p)));
  const rate=document.createElement('p');rate.className='value coin';rate.textContent=coins(p.coinsPerHour);
  const units=document.createElement('p');units.className='muted';units.textContent='Estimated coins/hour';
  const cost=document.createElement('p');cost.textContent=`Capital ${coins(p.capitalUsed)} · profit/batch ${coins(batchProfit(p))}`;
  const status=document.createElement('p');status.className='muted';status.textContent=routeDisposition(p,state.pipeline,automatic);
  card.append(rank,title,rate,units,cost,status);cards.append(card);
 }
 put('route-count',`${shown.length} shown / ${predictions.length} reported${state.predictions?.total>predictions.length?` · ${state.predictions.total} eligible before report limit`:''}`);
 const routes=$('prediction-rows');routes.replaceChildren();
 for(const p of shown){
  const tr=document.createElement('tr'),name=document.createElement('td');name.append(button(`${favorites.includes(routeId(p))?'★ ':''}${routeName(p)}`,()=>inspect(p)));tr.append(name);
  for(const value of [p.kind??'—',`${p.batch} / ${p.inputUnits??'—'}`,coins(p.capitalUsed),coins(batchProfit(p)),p.capitalUsed>0?`${coins(batchProfit(p)/p.capitalUsed*100)}%`:'—',coins(p.coinsPerHour),`${coins(p.cycleSeconds/60)} min`,p.confidence,routeDisposition(p,state.pipeline,automatic)]){const td=document.createElement('td');td.textContent=value;tr.append(td);}routes.append(tr);
 }
 if(!shown.length)empty(routes,10,predictions.length?'No matches. Clear filters to see all reported routes.':state.predictionReason||'Waiting for a fresh calculator forecast.');
 const selected=predictions.find(p=>routeId(p)===selectedRoute),details=$('detail-values');details.replaceChildren();
 put('detail-name',selected?routeName(selected):'Inspect a flip');put('detail-status',selected?routeDisposition(selected,state.pipeline,automatic):selectedRoute?'Selected route is absent from the fresh report.':'Select a route to see its inputs, limits and evidence.');
 $('favorite-route').disabled=!selected;$('copy-route').disabled=!selected?.inputId;
 put('favorite-route',selected&&favorites.includes(routeId(selected))?'Remove favorite':'Favorite');
 if(selected)for(const [label,value]of [
  ['Input product',selected.inputId??'Not supplied'],['Output product',selected.outputId??'Not supplied'],['Batch / input units',`${selected.batch} / ${selected.inputUnits??'—'}`],
  ['Capital required',coins(selected.capitalUsed)],['Market batch profit',coins(selected.profitPerBatch)],['Gameplay-adjusted batch profit',coins(selected.profitPerBatch*(selected.executionEvidence?.profitRealizationFactor??1))],['Estimated cycle',`${coins(selected.cycleSeconds/60)} min`],
  ['Book combine operations',selected.kind==='BOOK'?Math.max(0,selected.inputUnits-selected.batch):'None'],['Limited by',selected.limitedBy??'Not supplied'],['Price basis',selected.priceBasis??'Current market forecast'],
  ['Market evidence',selected.confidence==='MEASURED'?'Observed market samples · personal fills may differ':'Estimated fill model'],['Quote age',age(state.predictions?.marketAt)],['Execution scope',selected.configured?(automatic?'Automatic selection':'Configured'):'Research only'],
  ['Market-only coins/hour',coins(selected.executionEvidence?.marketCoinsPerHour??selected.coinsPerHour)],
  ['Gameplay adjustment',Number.isFinite(selected.executionEvidence?.throughputFactor)?`${selected.executionEvidence.samples} own completed · ${selected.executionEvidence.sharedSamples??0} similar-volume trades · ${selected.executionEvidence.pendingSamples??0} open · ${selected.executionEvidence.censoredSamples??0} retired · ${coins(selected.executionEvidence.throughputFactor)}× throughput · ${coins(selected.executionEvidence.profitRealizationFactor??1)}× profit realization`:'Market estimate; waiting for similar-volume or own gameplay evidence'],
  ['Input daily volume · weekly average',selected.volumeEvidence?coins(selected.volumeEvidence.inputWeeklyAveragePerDay):'—'],
  ['Output daily volume · weekly average',selected.volumeEvidence?coins(selected.volumeEvidence.outputWeeklyAveragePerDay):'—'],
  ['Input recent daily rate',selected.volumeEvidence?.inputObservationHours?`${coins(selected.volumeEvidence.inputRecentPerDay)} · ${coins(selected.volumeEvidence.inputObservationHours)} h observed`:'Not enough recent counter observations'],
  ['Output recent daily rate',selected.volumeEvidence?.outputObservationHours?`${coins(selected.volumeEvidence.outputRecentPerDay)} · ${coins(selected.volumeEvidence.outputObservationHours)} h observed`:'Not enough recent counter observations'],
  ['Observed gameplay coins/hour',selected.executionEvidence?.samples?coins(selected.executionEvidence.observedCoinsPerHour):'—']]){
   const group=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=value;group.append(dt,dd);details.append(group);
 }
}
function empty(tbody,columns,message) { const row=document.createElement('tr'),cell=document.createElement('td');cell.colSpan=columns;cell.className='empty';cell.textContent=message;row.append(cell);tbody.append(row); }
function row(tbody,values) { const tr=document.createElement('tr');for(const value of values){const td=document.createElement('td');td.textContent=value ?? '—';tr.append(td);}tbody.append(tr); }
function render() {
  if(!state)return;
  const a=state.account,live=state.fresh && a?.account.connected,market=state.collector;
  put('account-name',a?.account.name || 'Waiting for Minecraft');put('trader-status',a ? `${a.status.state} · ${a.status.mode}${live?'':' · last observed'}` : 'No account connected');
  put('market-status',market.fresh?'Market feed live':market.enabled?'Market feed awaiting fresh data':'Market collection disabled');
  put('updated',state.receivedAt?`Account updated ${age(state.receivedAt)}`:'Waiting for account');
  put('notice',!a?'Enable marketAnalysis.dashboardEnabled in goofyaddons.json and press \\ while stopped.':!live?'Account data is offline or stale. Values below are the last observed snapshot.':market.error?`Account connected. Market collector: ${market.error}`:market.storageError?`Account connected. ${market.storageError}`:'Account connected. Refreshes every 2 seconds; market collection every 20 seconds.');
  $('notice').classList.toggle('good',Boolean(live && !market.error && !market.storageError));
  put('activity',a?.status.action || 'A live view of your inventory, positions and market opportunities.');
  put('spendable',live?coins(a?.status.available??state.pipeline?.account?.available):'—');
  put('pending-capital',live?`Pending purchases ${coins(a.status.pending??state.pipeline?.account?.pending)} · reserve ${coins(a.status.reserve)}`:'Waiting for a fresh account snapshot');
  put('purse',coins(a?.status.purse));put('committed',coins(a?.status.funded));
  put('budget',a?`Reserved trading budget ${coins(a.status.committed)}${Number.isFinite(a.status.funded)?` · future inputs ${coins(Math.max(0,a.status.committed-a.status.funded))}`:' · funding unverified'}. Reservations include unbought inputs; refunds are not profit.`:'Waiting for account limits');
  put('confirmed',a?.profitError?'—':coins(a?.profit.profit));$('confirmed').classList.toggle('negative',a?.profit.profit<0);
  put('settlements',a?.profitError?`Profit ledger unavailable: ${a.profitError}`:a?a.profit.settlements===0?'No sale or loss settlements recorded yet':`${a.profit.settlements} settled · ${a.profit.incomplete} with unknown profit`:'From settled trade receipts');
  const predictions=state.predictions?.rows ?? [];
  put('predicted',coins(state.portfolio?.coinsPerHour));
  put('live-predicted',coins(state.live?.coinsPerHour));$('live-predicted').classList.toggle('negative',state.live?.coinsPerHour<0);
  put('live-forecast-note',state.live?.known?`${state.live.known} predicted · ${state.live.unknown} unknown${state.live.unknown?' · partial estimate':''}. Recorded/planned costs, listed offers and overdue cycles; full-cycle run rate.`:state.live?.reason || 'Waiting for active trade forecasts');
  const portfolioRows=$('portfolio-rows');portfolioRows.replaceChildren();
  for(const p of state.portfolio?.rows??[])row(portfolioRows,[`${p.inputId} → ${p.outputId}`,p.inputUnits,coins(p.capitalUsed),coins(p.coinsPerHour)]);
  if(!portfolioRows.children.length)empty(portfolioRows,4,state.portfolio?.reason||'Waiting for the full-budget forecast');
  put('portfolio-budget',state.portfolio?.budget!==undefined?`Budget ${coins(state.portfolio.budget)} · allocated ${coins(state.portfolio.allocated)} · unallocated ${coins(state.portfolio.remaining)} · shared GUI factor ${coins(state.portfolio.guiFactor)}×. ${state.portfolio.scope}.`:'Waiting for budget and position limits');
  put('measured-rate',live?coins(state.measuredProfitPerHour):'—');$('measured-rate').classList.toggle('negative',state.measuredProfitPerHour<0);
  put('measured-rate-note',live&&Number.isFinite(state.measuredProfitPerHour)?'Receipt-confirmed profit / tracked active time; paused time excluded':a?.profit?.incomplete?'Unavailable: some settlements have unknown profit':'Needs known-profit settlements and at least 60 seconds of active time');
  put('execution-note',state.execution?.error || a?.executionError || `${state.execution?.samples ?? 0} recorded outcomes · ${state.execution?.eligible ?? 0} completed timing samples · ${state.execution?.censored ?? 0} retired bounds · ${state.execution?.pending ?? 0} open. Downside learns after 3 completed cycles or an overdue trade; upside needs 10. Rankings share corrections across similar-volume routes, then refine per item using realized profit and timings.`);
  put('forecast-status',state.portfolio?.rows?.length?`${state.portfolio.rows.length} routes · ${coins(state.portfolio.allocated)} allocated · ${coins(state.portfolio.remaining)} unallocated under your limits`:state.portfolio?.reason || state.predictionReason || 'Waiting for a portfolio forecast');
  const positionProfit=state.positionProfit;
  put('position-profit',coins(positionProfit?.total));$('position-profit').classList.toggle('negative',positionProfit?.total<0);
  put('position-profit-note',positionProfit?.known?`${positionProfit.known} priced · ${positionProfit.unknown} unknown. Book costs are planned full-cycle estimates; excludes listing fees, future price changes and fill timing.`:positionProfit?.reason || 'No fresh position estimate');
  const filter=$('inventory-search').value.toLowerCase();
  const matches=r=>!filter || `${r.name ?? ''} ${r.id ?? ''}`.toLowerCase().includes(filter);
  const inv=a?.inventory ?? [],grid=$('inventory-grid');grid.replaceChildren();
  for(const slot of [...Array.from({length:27},(_,i)=>i+9),...Array.from({length:9},(_,i)=>i)]) {
    const item=inv.find(r=>r.slot===slot),cell=document.createElement('div');cell.className=`inventory-slot${item?' filled':''}${slot<9?' hotbar':''}${item&&!matches(item)?' faded':''}`;
    cell.title=item?`Slot ${slot}: ${item.name} ×${item.count}${item.id?` (${item.id})`:''}`:`Slot ${slot}${a?' · empty':' · not observed'}`;
    if(item){const name=document.createElement('span');name.className='slot-name';name.textContent=item.name;const qty=document.createElement('span');qty.className='qty';qty.textContent=item.count;cell.append(name,qty);}grid.append(cell);
  }
  put('inventory-count',a?`${inv.filter(r=>r.slot<36).length}/36 occupied slots`:'Not observed');
  put('equipment',inv.filter(r=>r.slot>=36).map(r=>`${r.name} ×${r.count}`).join(' · '));
  const storage=$('storage');storage.replaceChildren();
  const regions=a?.books.slotMemory?.regions?.filter(r=>r.region>0) ?? [];
  if(!regions.length)storage.textContent='Storage pages have not been observed in this session.';
  for(const region of regions) {
    const block=document.createElement('div');block.className='storage-region';const title=document.createElement('h4');title.textContent=`Ender Chest page ${region.region} · last inspected ${age(region.observedAt)}`;block.append(title);
    const view=$('storage-view').value,cells=storageCells(region,view),grid=document.createElement('div');grid.className='inventory-grid storage-grid';
    grid.setAttribute('aria-label',`Ender Chest page ${region.region} · ${view} observed slots`);
    for(const {slot,item,observed}of cells){
      const cell=document.createElement('div');cell.className=`inventory-slot storage-slot${item?' filled':''}${item&&!matches(item)?' faded':''}${!observed?' unobserved':''}`;
      cell.title=item?`Slot ${slot}: ${item.name} ×${item.count}${item.id?` (${item.id})`:''}`:`Slot ${slot} · ${observed?'empty in this snapshot':'not supplied in this snapshot'}`;
      if(item){const name=document.createElement('span'),qty=document.createElement('span');name.className='slot-name';qty.className='qty';name.textContent=item.name;qty.textContent=item.count;cell.append(name,qty);}
      grid.append(cell);
    }
    const caption=document.createElement('p');caption.className='muted';caption.textContent=cells.some(c=>c.observed)?`${view==='previous'?'Previous':'Current'} observed snapshot · ${cells.filter(c=>c.item).length} occupied menu slots${filter?` · ${cells.filter(c=>c.item&&matches(c.item)).length} matching`:''}. Includes any observed menu controls.${cells.some(c=>!c.observed)?' Dashed slots were not supplied in this snapshot.':''}`:'No slot observation available for this snapshot.';
    block.append(grid,caption);storage.append(block);
  }
  const orders=$('order-rows');orders.replaceChildren();const selected=$('order-filter').value;
  if(selected!=='books')for(const p of a?.general.positions ?? [])row(orders,[p.item,'General',p.stage,p.units,p.purchasePriceKnown?coins(p.cost):'Unknown',p.sellPrice>0?coins(p.sellPrice):'—']);
  if(selected!=='general')for(const p of a?.books.tasks ?? [])row(orders,[p.item,'Books',p.state,`${p.remaining} inputs pending · ${p.holdings.length} books held`,'Unknown','—']);
  if(!orders.children.length)empty(orders,6,a?'No tracked positions for this filter.':'Waiting for Minecraft account data.');
  const stages=workStages(live?a:null),work=$('work-stages');work.replaceChildren();
  for(const [label,key]of [['Buy / claim','buy'],['Store / combine','combine'],['Sell / settle','sell'],['Verify / recover','review']]){
   const tile=document.createElement('div');tile.className='card pad';const name=document.createElement('div'),count=document.createElement('div');name.className='label';name.textContent=label;count.className='stage-count';count.textContent=live?stages[key]:'—';tile.append(name,count);work.append(tile);
  }
  put('work-note',live?'Observed positions grouped by their current stage; counts are not fill progress.':'Waiting for fresh account data.');
  const plan=state.pipeline,queue=$('pipeline-rows');queue.replaceChildren();
  for(const proposal of plan?.next ?? []){const p=proposal.route;row(queue,[proposal.priority,`${p.inputId} → ${p.outputId}`,p.kind,p.inputUnits,coins(p.capitalUsed),coins(batchProfit(p)),`${coins(p.cycleSeconds/60)} min`,p.confidence]);}
  if(!queue.children.length)empty(queue,8,plan?.reason || state.predictionReason || 'Waiting for account and forecast data');
  put('pipeline-budget',plan?.account?`Spendable ${coins(plan.account.available)} · pending purse deduction ${coins(plan.account.pending)} · preview allocation ${coins(plan.plannedCapital)} · left ${coins(plan.capitalLeft)} · headroom ${plan.account.bookSlots} book / ${plan.account.generalSlots} general positions · ${plan.account.inventoryCapacity} input slots${Number.isFinite(plan.inventoryLeft)?` · ${plan.inventoryLeft} slots left in preview`:''}`:'Waiting for a shared account snapshot from mod 1.3.31+');
  put('pipeline-note',plan?`${plan.reason}. Preview only; no real capital is reserved. Conservative inventory capacity; reported candidates only. Rates are ranked individually and are not added together.`:state.predictionReason || 'Waiting for planner data');
  put('plan-status',plan?.status??'Waiting');
  const allocated=plan?.account?.available>0?Math.max(0,Math.min(100,plan.plannedCapital/plan.account.available*100)):0;
  $('allocation-fill').style.width=`${allocated}%`;$('allocation-bar').setAttribute('aria-valuenow',String(Math.round(allocated)));
  put('deferred-summary',`Deferred candidates (${plan?.deferred?.length??0})`);
  const deferred=$('pipeline-deferred');deferred.replaceChildren();
  for(const item of plan?.deferred ?? []){const li=document.createElement('li');li.textContent=`${item.routeKey}: ${item.reason}`;deferred.append(li);}
  if(!deferred.children.length){const li=document.createElement('li');li.textContent='No deferred candidates reported';deferred.append(li);}
  renderRoutes(predictions);
  put('prediction-note',predictions.length?`Quote ${age(state.predictions.marketAt)} · individually ranked routes, not a combined portfolio. ${Number.isFinite(a?.analysis?.comparison?.rankingCapital)?`Ranking budget ${coins(a.analysis.comparison.rankingCapital)} · standard input capacity ${a.analysis.comparison.rankingInventoryCapacity}${a.analysis.comparison.rankingFundingUnknown?` · lower bound; ${a.analysis.comparison.rankingFundingUnknown} positions have unverified funding`:""}. `:''}${state.predictions.historyUsed?'Historical observations used.':'Fill rates are estimated.'}`:state.predictionReason || 'Waiting for a fresh calculator forecast.');
}
for(const id of ['inventory-search','storage-view','order-filter'])$(id).addEventListener('input',render);
for(const id of ['route-search','route-engine','route-scope','route-sort'])$(id).addEventListener('input',()=>{savePreferences();render();});
$('clear-filters').addEventListener('click',()=>{$('route-search').value='';$('route-engine').value='all';$('route-scope').value='all';$('route-sort').value='rate';savePreferences();render();});
$('favorite-route').addEventListener('click',()=>{if(!selectedRoute)return;favorites=favorites.includes(selectedRoute)?favorites.filter(x=>x!==selectedRoute):[...favorites,selectedRoute].slice(-250);savePreferences();render();});
$('copy-route').addEventListener('click',async()=>{const p=state?.predictions?.rows?.find(p=>routeId(p)===selectedRoute);if(!p?.inputId)return;try{await navigator.clipboard.writeText(p.inputId);put('copy-status','Product ID copied');}catch{put('copy-status','Clipboard unavailable; select the product ID above to copy it.');}});
$('theme').addEventListener('click',()=>{document.documentElement.dataset.theme=document.documentElement.dataset.theme==='dark'?'light':'dark';savePreferences();});
let pollTimer,inFlight=false;
$('refresh').addEventListener('click',()=>{clearTimeout(pollTimer);void poll();});
async function poll() {
  if(inFlight)return;inFlight=true;$('refresh').disabled=true;
  try {const response=await fetch('/v1/dashboard',{cache:'no-store',signal:AbortSignal.timeout(5000)});if(!response.ok)throw new Error(`HTTP ${response.status}`);state=await response.json();render();}
  catch {if(state){state.fresh=false;state.predictions=null;state.positionProfit=null;state.measuredProfitPerHour=null;state.pipeline=null;state.portfolio=null;state.live=null;state.predictionReason='Companion unavailable';render();}put('notice','Companion unavailable. Keep its terminal running; displayed values are last observed.');}
  finally {inFlight=false;$('refresh').disabled=false;pollTimer=setTimeout(poll,2000);}
}
void poll();
