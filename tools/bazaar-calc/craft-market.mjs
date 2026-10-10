// Coflnet discovers AH craft candidates; recipes remain the bundled, verified catalog.
const ID=/^[A-Z0-9_]{1,64}$/;
export function craftCandidates(body,now=Date.now()) {
 if(!Array.isArray(body)||body.length>10000)throw new Error('Invalid Coflnet craft response');
 const rows=new Map();
 for(const r of body) {
  const at=Date.parse(r?.lastUpdated),id=r?.itemId;
  if(!ID.test(id??'')||(r.type!=null&&r.type!=='crafting')||!Number.isFinite(at)||at>now+5000||now-at>300000
    ||![r.sellPrice,r.craftCost,r.volume,r.median].every(x=>Number.isFinite(x)&&x>0))continue;
  rows.set(id,{item:id,volume:r.volume,median:r.median,sourceAt:at,discoveryProfit:r.sellPrice-r.craftCost});
 }
 return [...rows.values()].sort((a,b)=>b.discoveryProfit-a.discoveryProfit||a.item.localeCompare(b.item));
}
// Public price summary reports historical sales activity, independently of profitable-craft discovery.
export async function craftDemand(item,{fetcher=fetch,token=null,now=Date.now}={}) {
 const r=await fetcher(`https://sky.coflnet.com/api/item/price/${encodeURIComponent(item)}`,{signal:AbortSignal.timeout(10000),headers:token?{Authorization:`Bearer ${token}`}:{}});
 if(!r.ok)throw new Error(`Coflnet sales summary HTTP ${r.status}`);
 const text=await r.text();if(text.length>65536)throw new Error('Coflnet sales summary too large');
 const body=JSON.parse(text);
 if(!Number.isFinite(body.volume)||body.volume<0||!Number.isFinite(body.median)||body.median<0)throw new Error('Invalid Coflnet sales summary');
 return {item,volume:body.volume,median:body.median,fetchedAt:now()};
}
export class CraftMarket {
 constructor({fetcher=fetch,token=()=>null,price,demand=null,catalog={recipes:[]},now=Date.now}={}) {
  this.demand=demand;this.demands=new Map();this.demandErrors=new Map();this.catalog=catalog;this.fetcher=(...args)=>fetcher(...args);this.token=token;this.price=price;this.now=now;
  this.rows=[];this.at=0;this.attempt=0;this.offset=0;this.flight=null;this.error=null;this.quotes=new Map();this.quoteErrors=new Map();this.quoteFlights=new Map();this.quoteAttempts=new Map();
 }
 async refresh(products={},focus='') {
  const selected=this.catalog.recipes?.find(r=>r.kind==='CRAFT'&&r.outputId===focus);
  if(selected) {
   const general=this.refresh(products);
   const candidates=new Map(this.candidates(products).map(r=>[r.item,r]));
   const targets=[focus,...Object.keys(selected.ingredients??{})].map(id=>candidates.get(id)).filter(Boolean).slice(0,8);
   await Promise.all([general,...targets.map(r=>this.quoteRow(r))]);
   return this.view(products);
  }
  if(this.flight)return this.flight;
  if(this.now()-this.attempt<20000)return this.view(products);
  this.attempt=this.now();
  this.flight=(async()=>{
   try {
    if(!this.at||this.now()-this.at>=60000) {
     try {const token=this.token(),r=await this.fetcher('https://sky.coflnet.com/api/craft/profit',{
      signal:AbortSignal.timeout(10000),headers:token?{Authorization:`Bearer ${token}`}:{}});
     if(!r.ok)throw new Error(`Coflnet craft discovery HTTP ${r.status}`);
     const text=await r.text();if(text.length>8*1024*1024)throw new Error('Coflnet craft response too large');
     this.rows=craftCandidates(JSON.parse(text),this.now());this.at=this.now();this.error=null;
     }catch(e){this.error=e.message;}
    }
    const ah=this.candidates(products);
    // Bounded rotation gives new candidates price coverage without flooding Coflnet.
    const targets=[...new Set([...ah.slice(0,4),...Array.from({length:4},(_,i)=>ah[(this.offset+i)%Math.max(1,ah.length)])].filter(Boolean))];
    this.offset=(this.offset+4)%Math.max(1,ah.length);
    await Promise.all(targets.map(row=>this.quoteRow(row)));
   }catch(e){this.error=e.message;}
   return this.view(products);
  })().finally(()=>{this.flight=null;});
  return this.flight;
 }
 async quoteRow(row) {
  if(this.quoteFlights.has(row.item))return this.quoteFlights.get(row.item);
  if(this.now()-(this.quoteAttempts.get(row.item)??-Infinity)<20000)return;
  this.quoteAttempts.set(row.item,this.now());
  const task=(async()=>{
     const summaryTask=(async()=>{
     if(this.demand&&(!this.demands.has(row.item)||this.now()-this.demands.get(row.item).fetchedAt>=300000)) {
      try {const summary=await this.demand(row.item);if(summary.item===row.item&&Number.isFinite(summary.volume)&&summary.volume>=0&&Number.isFinite(summary.fetchedAt)){this.demands.set(row.item,summary);this.demandErrors.delete(row.item);}}catch(e){this.demandErrors.set(row.item,{message:e.message,at:this.now()});}
     }
     })();
     try {this.quotes.set(row.item,await this.price(row.item));this.quoteErrors.delete(row.item);}catch(e){this.quotes.delete(row.item);this.quoteErrors.set(row.item,{message:e.message,at:this.now()});}
     await summaryTask;
  })().finally(()=>this.quoteFlights.delete(row.item));
  this.quoteFlights.set(row.item,task);
  return task;
 }
 candidates(products={}) {
  const rows=new Map(this.rows.filter(r=>this.now()-r.sourceAt<=300000).map(r=>[r.item,r]));
  for(const recipe of this.catalog.recipes??[])if(recipe.kind==='CRAFT')for(const id of [recipe.outputId,...Object.keys(recipe.ingredients??{})]) {
   if(ID.test(id)&&!rows.has(id))rows.set(id,{item:id,volume:null,median:null,sourceAt:this.now(),discoveryProfit:0});
  }
  return [...rows.values()].filter(r=>!Object.hasOwn(products,r.item));
 }
 view(products={}) {
  const now=this.now();
  return {protocol:'goofy-craft-market/1',generatedAt:now,discoveryAt:this.at,error:this.error,
   source:'coflnet',rows:this.candidates(products).map(row=>{
    const quote=this.quotes.get(row.item);
    const failure=this.quoteErrors.get(row.item),demand=this.demands.get(row.item);
    const demandFailure=this.demandErrors.get(row.item);
    const validDemand=demand&&demand.fetchedAt<=now+5000&&now-demand.fetchedAt<=300000;
    return {...row,demandError:!validDemand&&demandFailure&&now-demandFailure.at<=300000?demandFailure.message:null,...(validDemand?{volume:demand.volume,median:demand.median,demandAt:demand.fetchedAt,demandSource:'coflnet-sales-summary'}:{}),quoteError:failure&&now-failure.at<=60000?failure.message:null,quote:quote&&now-quote.fetchedAt<=60000&&quote.fetchedAt<=now+5000?quote:null};
   }),coverage:'Catalog outputs and components plus Coflnet craft candidates; fresh BIN quotes are collected in bounded rotations. Market volume is provider-reported; its time window is not assumed.'};
 }
}
