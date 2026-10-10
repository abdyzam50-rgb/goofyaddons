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
export class CraftMarket {
 constructor({fetcher=fetch,token=()=>null,price,catalog={recipes:[]},now=Date.now}={}) {
  this.catalog=catalog;this.fetcher=fetcher;this.token=token;this.price=price;this.now=now;
  this.rows=[];this.at=0;this.attempt=0;this.offset=0;this.flight=null;this.error=null;this.quotes=new Map();
 }
 async refresh(products={},focus='') {
  if(this.flight)return this.flight;
  if(this.now()-this.attempt<20000)return this.view(products);
  this.attempt=this.now();
  this.flight=(async()=>{
   try {
    if(!this.at||this.now()-this.at>=60000) {
     try {const token=this.token(),r=await this.fetcher('https://sky.coflnet.com/api/crafts/profit',{
      signal:AbortSignal.timeout(10000),headers:token?{Authorization:`Bearer ${token}`}:{}});
     if(!r.ok)throw new Error(`Coflnet craft discovery HTTP ${r.status}`);
     const text=await r.text();if(text.length>8*1024*1024)throw new Error('Coflnet craft response too large');
     this.rows=craftCandidates(JSON.parse(text),this.now());this.at=this.now();this.error=null;
     }catch(e){this.error=e.message;}
    }
    const ah=this.candidates(products);
    // Bounded rotation gives new candidates price coverage without flooding Coflnet.
    const recipe=this.catalog.recipes?.find(r=>r.kind==='CRAFT'&&r.outputId===focus);
    const focused=new Set(recipe?[focus,...Object.keys(recipe.ingredients??{})]:[]);
    const targets=[...new Set([...ah.filter(r=>focused.has(r.item)).slice(0,8),...ah.slice(0,4),...Array.from({length:4},(_,i)=>ah[(this.offset+i)%Math.max(1,ah.length)])].filter(Boolean))];
    this.offset=(this.offset+4)%Math.max(1,ah.length);
    await Promise.all(targets.map(async row=>{
     try {this.quotes.set(row.item,await this.price(row.item));}catch {this.quotes.delete(row.item);}
    }));
   }catch(e){this.error=e.message;}
   return this.view(products);
  })().finally(()=>{this.flight=null;});
  return this.flight;
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
    return {...row,quote:quote&&now-quote.fetchedAt<=60000&&quote.fetchedAt<=now+5000?quote:null};
   }),coverage:'Catalog outputs and components plus Coflnet craft candidates; fresh BIN quotes are collected in bounded rotations. Market volume is provider-reported; its time window is not assumed.'};
 }
}
