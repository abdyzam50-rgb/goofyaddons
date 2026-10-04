// Advisory portfolio forecasts. Never reserves money or changes the mod's execution plan.
import { recommend } from './adapter.mjs';
import { actionSeconds, DEFAULT_SETTINGS } from './engine.mjs';
const finite=v=>Number.isFinite(v);
const family=r=>r.kind==='BOOK'?r.inputId.replace(/_\d+$/,''):r.inputId;
const key=r=>`${r.kind}:${r.routeKey}`;
const unique=xs=>new Set(xs).size;
const unavailable=reason=>({coinsPerHour:null,rows:[],reason});
function guiSeconds(row,c) {
 const settings={...DEFAULT_SETTINGS,clickDelayMs:c.clickDelayMs};
 return actionSeconds('create_buy_order',settings)+actionSeconds('create_sell_offer',settings)
   +2*actionSeconds('claim_order',settings)+Math.max(0,row.inputUnits-row.batch)*actionSeconds('anvil_combine',settings);
}
function combine(rows,c) {
 const work=rows.reduce((sum,r)=>sum+3600/r.cycleSeconds*guiSeconds(r,c),0);
 const guiFactor=Math.min(1,3600/Math.max(1,work));
 return {coinsPerHour:rows.reduce((sum,r)=>sum+r.coinsPerHour,0)*guiFactor,guiFactor,
   rows:rows.map(r=>({...r,coinsPerHour:r.coinsPerHour*guiFactor})),reason:null};
}
export class DashboardForecast {
 constructor({provenance,now=Date.now}={}) {this.provenance=provenance;this.now=now;this.request=null;this.cache=null;}
 acceptRequest(body) {this.request=structuredClone(body);this.cache=null;}
 view(view,history,executions) {
  const now=this.now(),a=view.account,b=this.request;
  if(!view.fresh||!a?.account.connected)return {portfolio:unavailable('Waiting for fresh account data'),live:unavailable('Waiting for fresh account data')};
  if(!view.predictions||!b||!finite(b.market?.lastUpdated)||now-b.market.lastUpdated>60000||b.market.lastUpdated>now+5000)
   return {portfolio:unavailable('Waiting for fresh calculator inputs'),live:unavailable('Waiting for fresh calculator inputs')};
  const limits=a.analysis.pipeline?.account,comparison=a.analysis.comparison??{};
  const fingerprint=JSON.stringify([b.requestId,[a.status.state,a.status.purse,a.status.reserve,a.status.pending,a.status.committed,a.status.funded,a.status.capitalLimit],
    [comparison.rankingCapital,comparison.rankingInventoryCapacity,comparison.bookSlots,comparison.generalSlots],
    [limits?.bookSlots,limits?.generalSlots,limits?.mode],a.books.tasks,a.general.positions,
    executions?.status?.(),Math.floor(now/10000)]);
  if(this.cache?.key===fingerprint)return this.cache.value;
  let value;
  try {value={portfolio:this.portfolio(a,b,history,executions,now),live:this.live(a,b,history,executions,now)};}
  catch(error) {value={portfolio:unavailable(`Forecast unavailable: ${error.message}`),live:unavailable(`Forecast unavailable: ${error.message}`)};}
  this.cache={key:fingerprint,value};return value;
 }
 calculate(b,c,history,executions,now,allowed=null) {
  return recommend({protocol:b.protocol,requestId:b.requestId,market:b.market,constraints:c},history,this.provenance,now,executions,allowed);
 }
 portfolio(a,b,history,executions,now) {
  const observation=a.analysis.pipeline?.account,comparison=a.analysis.comparison??{};
  const counts={books:unique(a.books.tasks.filter(p=>!p.retiring).map(p=>p.item)),general:unique(a.general.positions.map(p=>p.item))};
  const freeBooks=observation?.bookSlots??comparison.bookSlots,freeGeneral=observation?.generalSlots??comparison.generalSlots;
  if(!finite(freeBooks)||!finite(freeGeneral))return unavailable('Waiting for position limits from the mod');
  const base=b.rankingConstraints??b.constraints,c=structuredClone(base);
  c.mode=b.constraints.mode;
  c.bookSlots=Math.min(10,freeBooks+counts.books);c.generalSlots=Math.min(10,freeGeneral+counts.general);
  if(![a.status.purse,a.status.reserve,a.status.funded,a.status.capitalLimit].every(finite)||a.status.purse<0)
   return unavailable('Waiting for verified funded positions; update the mod and recheck saved orders if needed');
  // Pending purchases still belong to the purse. Future book reservations are not
  // assets, so neither reserved commitments nor pending debits enter this sum.
  const budget=Math.max(0,Math.min(a.status.capitalLimit,a.status.funded+Math.max(0,a.status.purse-a.status.reserve)));
  c.availableCapital=budget;c.inventoryCapacity=comparison.rankingInventoryCapacity??base.inventoryCapacity;
  if(!budget)return {...combine([],c),budget:0,allocated:0,remaining:0,inventoryUsed:0,reason:'No trading capital is available'};
  const capacity=c.inventoryCapacity,variants=new Map();
  // Evaluate batch alternatives against real order curves instead of scaling one route's rate.
  const sizes=[...new Set([1,4,16,c.general.maxItemsPerOrder,capacity].map(n=>Math.min(n,c.general.maxItemsPerOrder,capacity)).filter(n=>n>=1))];
  for(const size of sizes) {
   const variant={...c,general:{...c.general,maxItemsPerOrder:size}};
   for(const r of this.calculate(b,variant,history,executions,now).rows.filter(r=>r.configured))variants.set(`${key(r)}:${r.batch}`,r);
  }
  const groups=new Map();for(const r of variants.values()) {const f=family(r);if(!groups.has(f))groups.set(f,[]);groups.get(f).push(r);}
  const ordered=[...groups.values()].sort((a,b)=>Math.max(...b.map(r=>r.coinsPerHour))-Math.max(...a.map(r=>r.coinsPerHour)));
  const root={rows:[],capital:0,units:0,books:0,general:0,rate:0,work:0};let beam=[root];
  const score=s=>s.rate*Math.min(1,3600/Math.max(1,s.work));
  // Bounded search compares whole feasible allocations. It is an advisory best-found
  // portfolio, not a promise of a mathematically optimal allocation or guaranteed fills.
  for(const options of ordered) {
   const next=[...beam];
   for(const state of beam)for(const r of options) {
    const book=r.kind==='BOOK';
    if(state.capital+r.capitalUsed>budget+1e-6||state.units+r.inputUnits>capacity
      ||(book?state.books>=c.bookSlots:state.general>=c.generalSlots))continue;
    next.push({rows:[...state.rows,r],capital:state.capital+r.capitalUsed,units:state.units+r.inputUnits,
      books:state.books+(book?1:0),general:state.general+(book?0:1),rate:state.rate+r.coinsPerHour,
      work:state.work+3600/r.cycleSeconds*guiSeconds(r,c)});
   }
   next.sort((a,b)=>score(b)-score(a)||b.capital-a.capital);beam=[...next.filter(state=>state!==root).slice(0,63),root];
  }
  beam.sort((a,b)=>score(b)-score(a)||b.capital-a.capital);const best=beam[0];
  return {...combine(best.rows,c),budget,allocated:best.capital,remaining:Math.max(0,budget-best.capital),inventoryUsed:best.units,
   reason:best.rows.length?null:'No supported portfolio fits your budget and limits',scope:'Best-found allocation of purse plus funded position costs after positions close; future reservations and unrealized profit excluded'};

 }
 live(a,b,history,executions,now) {
  if(a.status.state!=='RUNNING')return {coinsPerHour:0,rows:[],known:0,unknown:0,reason:'Trading is stopped or paused'};
  const mode=a.analysis.pipeline?.account?.mode??b.constraints.mode;
  const generalEnabled=mode!=='BOOKS'&&a.general.paused!==true,booksEnabled=mode!=='GENERAL'&&a.books.paused!==true;
  const positions=[...a.general.positions.map(p=>({kind:'GENERAL',routeKey:p.item,inputId:p.item,outputId:p.item,batch:p.units,inputUnits:p.units,
    cost:p.purchasePriceKnown?p.cost:null,trade:p.trade,stage:p.stage,offer:p.stage==='SELL_ORDER'?p.sellPrice:null,
    usable:generalEnabled&&!p.settlementPending&&['BUY_ORDER','SELL_ORDER','INVENTORY'].includes(p.stage)})),
   ...a.books.tasks.map(p=>({kind:'BOOK',routeKey:`${p.item}:${p.inputLevel}:${p.outputLevel}`,inputId:`${p.item}_${p.inputLevel}`,outputId:`${p.item}_${p.outputLevel}`,
     batch:1,inputUnits:2**(p.outputLevel-p.inputLevel),cost:p.plannedCost>0?p.plannedCost:null,trade:p.trade,stage:p.state,
     usable:booksEnabled&&!p.retiring&&['IN_BUY_ORDER','OUTBID','STORE','ANVIL','COMBINE','SELL','SELL_ORDER','REPLACE_SELL'].includes(p.state)}))];
  if(!positions.length)return {coinsPerHour:0,rows:[],known:0,unknown:0,reason:'No active positions'};
  const c=structuredClone(b.rankingConstraints??b.constraints);c.mode='BOTH';c.bookSlots=1;c.generalSlots=1;
  c.availableCapital=1e13;c.inventoryCapacity=4096;c.maxRecommendations=50;c.bookMinProfit=0;
  c.general={...c.general,maxCoinsPerItem:1e13,maxItemsPerOrder:4096,minProfitPerBatch:0,minMarginPercentage:0,minWeeklyVolume:0};
  const allowed=new Set(positions.filter(p=>p.usable).map(key));
  const predictions=this.calculate(b,c,history,executions,now,allowed).rows;
  const rows=[];let unknown=0;
  for(const p of positions) {
   const model=predictions.find(r=>key(r)===key(p)),product=b.market.products[p.outputId];
   const offer=finite(p.offer)&&p.offer>0?p.offer:(product?.buy_summary?.[0]?.pricePerUnit??0)-0.1;
   if(!p.usable||!model||!finite(p.cost)||p.cost<0||!Number.isInteger(p.batch)||p.batch<1||offer<=0){unknown++;continue;}
   const active=executions?.active?.find(s=>s.tradeId===p.trade&&s.observedAt>=now-15000);
   const elapsed=active?(now-active.startedAt)/1000:0;
   const work=guiSeconds(p,c),cycleSeconds=Math.max(model.cycleSeconds*p.batch/model.batch,work,elapsed+work);
   // Known input cost and the actual listed sell offer take precedence over a new-entry margin.
   // A realization discount can lower positive projected profit; never soften a projected loss.
   const rawProfit=offer*p.batch*(1-c.taxPercentage/100)-p.cost;
   const profitPerBatch=rawProfit>0?rawProfit*(model.executionEvidence?.profitRealizationFactor??1):rawProfit;
   rows.push({...p,cycleSeconds,profitPerBatch,coinsPerHour:profitPerBatch/cycleSeconds*3600,elapsedSeconds:elapsed});
  }
  const combined=combine(rows,c);
  return {...combined,known:rows.length,unknown,coinsPerHour:rows.length?combined.coinsPerHour:null,
    reason:rows.length?null:'Position prices, costs or fill forecasts are unavailable',scope:'Current positions at their observed quantity and price; full-cycle equivalent rate, not an exact completion ETA'};
 }
}
