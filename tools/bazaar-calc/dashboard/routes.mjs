// Read-only display helpers. Filters never change the mod's allocation or execution.
export const routeId=p=>`${p.kind ?? 'GENERAL'}:${p.routeKey ?? `${p.inputId ?? p.inputName}:${p.outputId ?? p.outputName}`}`;
export const productName=id=>typeof id==='string'?id.replace(/^ENCHANTMENT_/,'').split('_').map(s=>s.charAt(0)+s.slice(1).toLowerCase()).join(' '):'Unknown product';
export const routeName=p=>`${p.inputName ?? productName(p.inputId)} → ${p.outputName ?? productName(p.outputId)}`;
export function visibleRoutes(routes,{search='',engine='all',scope='all',sort='rate',favorites=[]}={}) {
 const saved=new Set(favorites),query=search.trim().toLowerCase();
 const filtered=routes.filter(p=>(engine==='all'||p.kind===engine)&&(scope!=='configured'||p.configured)&&(scope!=='favorites'||saved.has(routeId(p)))
   &&`${routeName(p)} ${p.inputId??''} ${p.outputId??''}`.toLowerCase().includes(query));
 const score=p=>sort==='profit'?p.profitPerBatch*(p.executionEvidence?.profitRealizationFactor??1):sort==='capital'?-p.capitalUsed:sort==='cycle'?-p.cycleSeconds:p.coinsPerHour;
 return filtered.toSorted((a,b)=>(Number.isFinite(score(b))?score(b):-Infinity)-(Number.isFinite(score(a))?score(a):-Infinity)||routeId(a).localeCompare(routeId(b)));
}
export function routeDisposition(p,plan,automatic=false) {
 if(plan?.next?.some(x=>routeId(x.route)===routeId(p)))return 'In allocation preview';
 const deferred=plan?.deferred?.find(x=>x.routeKey===p.routeKey);
 if(deferred)return deferred.reason;
 const a=plan?.account;
 if(p.configured && a) {
  if(a.excludedProducts?.some(id=>id===p.inputId||id===p.outputId||(p.kind==='BOOK'&&id===p.inputId?.replace(/_\d+$/,''))))return 'Product already held, reserved or temporarily excluded';
  if((p.kind==='BOOK'&&a.mode==='GENERAL')||(p.kind==='GENERAL'&&a.mode==='BOOKS'))return 'Engine disabled by trading mode';
  if((p.kind==='BOOK'?a.bookSlots:a.generalSlots)===0)return 'Active position limit';
  if(Number.isFinite(a.available)&&p.capitalUsed>a.available)return 'Ranking batch exceeds currently spendable capital';
  if(Number.isFinite(a.inventoryCapacity)&&p.inputUnits>a.inventoryCapacity)return 'Ranking batch exceeds current inventory capacity';
 }
 if(p.configured && plan?.status==='WAITING')return `Supported route · ${plan.reason ?? 'waiting for execution capacity'}`;
 return p.configured?(automatic?'Automatically eligible':'Configured route'):'Research only · not configured for execution';
}
export function workStages(account) {
 const counts={buy:0,combine:0,sell:0,review:0};
 for(const p of account?.general?.positions??[])counts[p.stage==='BUY_ORDER'||p.stage==='PLANNED'?'buy':p.stage==='SELL_ORDER'||p.stage==='INVENTORY'?'sell':'review']++;
 for(const p of account?.books?.tasks??[])counts[['SELECTED','IN_BUY_ORDER','OUTBID'].includes(p.state)?'buy':['STORE','ANVIL','COMBINE'].includes(p.state)?'combine':['SELL','SELL_ORDER','REPLACE_SELL'].includes(p.state)?'sell':'review']++;
 return counts;
}

export function storageCells(region,view='current') {
 const entries=region[view]??[],observed=region[`${view}Slots`],known=new Set(Array.isArray(observed)?observed.filter(i=>Number.isInteger(i)&&i>=0&&i<100):[]);
 const items=new Map(entries.filter(e=>Number.isInteger(e.slot)&&e.slot>=0&&e.slot<100).map(e=>[e.slot,e.item]));
 const max=Math.max(-1,...known,...items.keys());
 const size=known.size?max+1:Math.max(54,max+1);
 return Array.from({length:size},(_,slot)=>({slot,item:items.get(slot),observed:known.has(slot)||items.has(slot)}));
}
