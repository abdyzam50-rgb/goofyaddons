// Render only observed data using text nodes; item names never become HTML.
const $ = id => document.getElementById(id);
const number = new Intl.NumberFormat(undefined,{maximumFractionDigits:1});
const coins = v => Number.isFinite(v) ? number.format(v) : '—';
const age = ts => ts ? `${Math.max(0,Math.floor((Date.now()-ts)/1000))}s ago` : 'not observed';
const put = (id,value) => { $(id).textContent=value; };
let state=null;
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
  put('purse',coins(a?.status.purse));put('committed',coins(a?.status.committed));
  put('budget',a?`Limit ${coins(a.status.capitalLimit)} · reserve ${coins(a.status.reserve)}`:'Waiting for account limits');
  put('confirmed',a?.profitError?'—':coins(a?.profit.profit));$('confirmed').classList.toggle('negative',a?.profit.profit<0);
  put('settlements',a?.profitError?`Profit ledger unavailable: ${a.profitError}`:a?a.profit.settlements===0?'No sale or loss settlements recorded yet':`${a.profit.settlements} settled · ${a.profit.incomplete} with unknown profit`:'From settled trade receipts');
  const predictions=state.predictions?.rows ?? [];
  put('predicted',coins(predictions[0]?.coinsPerHour));
  put('execution-note',state.execution?.error || a?.executionError || `${state.execution?.samples ?? 0} recorded gameplay outcomes · ${state.execution?.eligible ?? 0} eligible. Timing adjusts after 10 uninterrupted, known-cost cycles of the same route and batch in 24 hours.`);
  put('forecast-status',state.predictionReason || 'One eligible route; not total earnings');
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
    const block=document.createElement('div');block.className='storage-region';const title=document.createElement('h4');title.textContent=`Storage page ${region.region} · ${age(region.observedAt)}`;block.append(title);
    const list=document.createElement('ul');list.className='storage-items';
    for(const entry of region[$('storage-view').value] ?? [])if(matches(entry.item)){
      const li=document.createElement('li'),name=document.createElement('span'),qty=document.createElement('span');name.textContent=`Slot ${entry.slot} · ${entry.item.name}`;qty.textContent=`×${entry.item.count}`;li.append(name,qty);list.append(li);
    }
    if(!list.children.length){const p=document.createElement('p');p.className='muted';p.textContent=filter?'No matching observed items.':'No occupied slots in this snapshot.';block.append(p);}block.append(list);storage.append(block);
  }
  const orders=$('order-rows');orders.replaceChildren();const selected=$('order-filter').value;
  if(selected!=='books')for(const p of a?.general.positions ?? [])row(orders,[p.item,'General',p.stage,p.units,p.purchasePriceKnown?coins(p.cost):'Unknown',p.sellPrice>0?coins(p.sellPrice):'—']);
  if(selected!=='general')for(const p of a?.books.tasks ?? [])row(orders,[p.item,'Books',p.state,`${p.remaining} inputs pending · ${p.holdings.length} books held`,'Unknown','—']);
  if(!orders.children.length)empty(orders,6,a?'No tracked positions for this filter.':'Waiting for Minecraft account data.');
  const plan=state.pipeline,queue=$('pipeline-rows');queue.replaceChildren();
  for(const proposal of plan?.next ?? []){const p=proposal.route;row(queue,[proposal.priority,`${p.inputId} → ${p.outputId}`,p.kind,p.inputUnits,coins(p.capitalUsed),coins(p.profitPerBatch),`${coins(p.cycleSeconds/60)} min`,p.confidence]);}
  if(!queue.children.length)empty(queue,8,plan?.reason || state.predictionReason || 'Waiting for account and forecast data');
  put('pipeline-budget',plan?.account?`Spendable ${coins(plan.account.available)} · pending purse deduction ${coins(plan.account.pending)} · preview allocation ${coins(plan.plannedCapital)} · left ${coins(plan.capitalLeft)} · headroom ${plan.account.bookSlots} book / ${plan.account.generalSlots} general positions · ${plan.account.inventoryCapacity} input slots`:'Waiting for a shared account snapshot from mod 1.3.31+');
  put('pipeline-note',plan?`${plan.reason}. Preview only; no real capital is reserved. Conservative inventory capacity; reported candidates only. Rates are ranked individually and are not added together.`:state.predictionReason || 'Waiting for planner data');
  const deferred=$('pipeline-deferred');deferred.replaceChildren();
  for(const item of plan?.deferred ?? []){const li=document.createElement('li');li.textContent=`${item.routeKey}: ${item.reason}`;deferred.append(li);}
  if(!deferred.children.length){const li=document.createElement('li');li.textContent='No deferred candidates reported';deferred.append(li);}
  const routes=$('prediction-rows');routes.replaceChildren();
  for(const p of predictions)row(routes,[`${p.inputName ?? p.inputId} → ${p.outputName ?? p.outputId}`,p.batch,coins(p.profitPerBatch),coins(p.coinsPerHour),`${coins(p.cycleSeconds/60)} min`,p.confidence,p.configured?'Yes':'No']);
  if(!routes.children.length)empty(routes,7,state.predictionReason || 'No fresh recommendations.');
  put('prediction-note',predictions.length?`Quote ${age(state.predictions.marketAt)} · individually ranked routes, not a combined portfolio. ${state.predictions.historyUsed?'Historical observations used.':'Fill rates are estimated.'}`:state.predictionReason || 'Waiting for a fresh calculator forecast.');
}
for(const id of ['inventory-search','storage-view','order-filter'])$(id).addEventListener('input',render);
$('theme').addEventListener('click',()=>{document.documentElement.dataset.theme=document.documentElement.dataset.theme==='dark'?'light':'dark';});
async function poll() {
  try {const response=await fetch('/v1/dashboard',{cache:'no-store',signal:AbortSignal.timeout(5000)});if(!response.ok)throw new Error(`HTTP ${response.status}`);state=await response.json();render();}
  catch {if(state){state.fresh=false;state.predictions=null;state.positionProfit=null;state.pipeline=null;state.predictionReason='Companion unavailable';render();}put('notice','Companion unavailable. Keep its terminal running; displayed values are last observed.');}
  finally {setTimeout(poll,2000);}
}
void poll();
