// Price-based active-cycle estimates, separate from receipt-confirmed realized profit and fill-rate forecasts.
export function positionEstimates(account,collector) {
  const tax=account?.status?.taxPercentage;
  if(!Number.isFinite(tax) || tax<0 || tax>=100)return {rows:[],total:null,known:0,unknown:(account?.books.tasks.length ?? 0)+(account?.general.positions.length ?? 0),reason:'Waiting for tax and position metadata from mod 1.3.27+'};
  const rows=[];
  const estimate=(engine,item,output,units,cost,basis)=>{
    const quote=collector?.quote?.(output),ask=quote?.ask;
    const profit=Number.isFinite(ask) && ask>0.1 && Number.isFinite(cost) && cost>=0 && Number.isInteger(units) && units>0 ? (ask-0.1)*units*(1-tax/100)-cost : null;
    rows.push({engine,item,output,units,cost,costBasis:basis,offer:Number.isFinite(ask)?ask-0.1:null,profit,sourceAt:quote?.sourceAt ?? null});
  };
  for(const p of account.general.positions)estimate('general',p.item,p.item,p.units,p.purchasePriceKnown?p.cost:null,'recorded input cost');
  for(const p of account.books.tasks)estimate('books',p.item,`${p.item}_${p.outputLevel}`,1,Number.isFinite(p.plannedCost)&&p.plannedCost>0?p.plannedCost:null,'planned full-cycle input cost');
  const known=rows.filter(r=>Number.isFinite(r.profit));
  return {rows,total:known.length?known.reduce((sum,r)=>sum+r.profit,0):null,known:known.length,unknown:rows.length-known.length,
    reason:!rows.length?'No tracked positions':!known.length?'Current market quote or input cost unavailable':null};
}
export function predictionReason(view) {
  if(!view.account)return 'Waiting for Minecraft account data';
  if(!view.fresh || !view.account.account.connected)return 'Account disconnected or stale';
  const analysis=view.account.analysis;
  if(analysis.enabled===false || analysis.status==='DISABLED')return 'Market analysis is disabled in your config';
  if(view.predictions) {
    if(view.predictions.rows.length)return null;
    const c=analysis.comparison ?? {},counts=view.predictions.counts ?? {};
    if((c.rankingCapital??c.availableCapital)===0)return 'No capital available for a new position';
    if(counts.evaluated===0 && counts.malformedProducts>0 && counts.filtered===0 && counts.warnings===0)return 'No routes evaluated; check market coverage';
    return `No eligible new routes under your limits (${counts.filtered ?? 0} filtered, ${counts.warnings ?? 0} market warnings, ${counts.unsupported ?? 0} unsupported)`;
  }
  const messages={WAITING_ACCOUNT:analysis.pipeline?.account?.reason ?? 'Waiting for usable account observations',STOPPED:'Waiting for the next market ranking update',WAITING_QUOTES:'Waiting for fresh Bazaar quotes',WAITING_INVENTORY:'Waiting for an empty cursor and readable inventory',REQUESTING:'Calculator request in progress',UNAVAILABLE:`Calculator unavailable: ${analysis.error ?? 'check the companion connection'}`,STALE:'Previous calculator forecast expired',DISCARDED:'Configuration changed; waiting for the next request'};
  return messages[analysis.status] ?? 'Waiting for a fresh calculator forecast';
}

export function entryBlockReason(account) {
  const planning=account.analysis?.pipeline?.account;
  if(planning?.ready===false)return planning.reason ?? 'Waiting for usable account observations';
  const mode=planning?.mode ?? account.analysis?.comparison?.mode ?? account.status?.mode;
  const bookSlots=planning?.bookSlots ?? account.analysis?.comparison?.bookSlots;
  const generalSlots=planning?.generalSlots ?? account.analysis?.comparison?.generalSlots;
  const noSlots=mode==='BOOKS'?bookSlots===0:mode==='GENERAL'?generalSlots===0:mode==='BOTH'&&bookSlots===0&&generalSlots===0;
  if(noSlots) {
    const books=account.books?.tasks?.length ?? 0,general=account.general?.positions?.length ?? 0;
    return `Active-position limits reached (${books} book, ${general} general positions). Waiting for a position to finish; unused coins do not free an order slot.`;
  }
  if(planning?.inventoryCapacity===0)return 'No inventory headroom for a new position';
  if(planning?.available===0)return 'No capital available for a new position';
  return null;
}

export function measuredProfitRate(profit) {
  return profit && Number.isFinite(profit.profit) && Number.isFinite(profit.activeMillis) && profit.activeMillis>=60000
    &&profit.settlements>0 &&profit.incomplete===0?profit.profit*3600000/profit.activeMillis:null;
}
