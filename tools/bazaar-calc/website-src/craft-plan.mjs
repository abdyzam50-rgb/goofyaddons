// Advisory equivalent of the mod's whole-batch craft evaluation; all inputs are priced from scratch.
const BASIC=new Set(['BLAZE_POWDER','STICK','WOOD','PAPER','SUGAR','BOOK','BOWL','CHEST','GOLD_NUGGET','REDSTONE_TORCH_ON','EYE_OF_ENDER','GLASS_BOTTLE','WORKBENCH','BUCKET','WOOD_PICKAXE','WOOD_AXE','WOOD_HOE','WOOD_SPADE','WOOD_SWORD']);
export function fresh(at,now,ttl=60000){return Number.isFinite(at)&&at>0&&at<=now+5000&&now-at<=ttl;}
export function depth(levels,units,ascending){
 if(!Array.isArray(levels)||!Number.isSafeInteger(units)||units<=0)return null;
 let cost=0,remaining=units;
 for(const level of [...levels].sort((a,b)=>ascending?a.pricePerUnit-b.pricePerUnit:b.pricePerUnit-a.pricePerUnit)){
  if(!Number.isFinite(level.pricePerUnit)||level.pricePerUnit<=0||!Number.isFinite(level.amount)||level.amount<=0)continue;
  const take=Math.min(remaining,Math.floor(level.amount));cost+=take*level.pricePerUnit;remaining-=take;if(!remaining)return Number.isFinite(cost)?cost:null;
 }
 return null;
}
export function prepare(recipe,batches,recipes){
 const purchases={},steps=[],available={};
 const require=(id,units,path)=>{
  const use=Math.min(units,available[id]??0);available[id]=(available[id]??0)-use;units-=use;if(!units)return;
  const basic=BASIC.has(id)?recipes.find(r=>r.kind==='CRAFT'&&r.outputId===id&&!r.requirement):null;
  if(!basic){purchases[id]=(purchases[id]??0)+units;return;}
  if(path.has(id))throw new Error('Cyclic intermediate recipe');
  const count=Math.ceil(units/basic.outputCount);path.add(id);
  for(const [input,qty] of Object.entries(basic.ingredients))require(input,qty*count,path);
  path.delete(id);steps.push({output:id,batches:count,units:count*basic.outputCount});available[id]=(available[id]??0)+count*basic.outputCount-units;
 };
 for(const [id,qty] of Object.entries(recipe.ingredients))require(id,qty*batches,new Set());
 return {purchases,steps};
}
export function planCrafts({catalog,market,ah,now=Date.now(),budget=0,minProfit=10000,maxBatches=16,tax=1.25,requirements={}}){
 if(!market?.success||!fresh(market.lastUpdated,now))return [];
 const products=market.products??{},best=new Map(),recipes=catalog.recipes.filter(r=>r.kind==='CRAFT');
 const ahRows=new Map((fresh(ah?.generatedAt,now)?ah.rows??[]:[]).filter(r=>fresh(r.sourceAt,now,300000)).map(r=>[r.item,r]));
 for(const recipe of recipes){
  const product=products[recipe.outputId],source=ahRows.get(recipe.outputId),venue=product?'BAZAAR':'AH';
  if(!product&&!source)continue;
  for(let batches=1;batches<=Math.min(16,Math.max(1,Math.floor(maxBatches)));batches++){
   if(!product&&(batches>1||recipe.outputCount!==1))break;
   const prep=prepare(recipe,batches,recipes),units=recipe.outputCount*batches;
   let cost=0,reason=(requirements[recipe.key]??[]).join('; '),gross=null,fee=0,liquidity=0;
   for(const [id,qty] of Object.entries(prep.purchases)){
    const value=depth(products[id]?.buy_summary,qty,true);
    if(value===null){reason||=`Missing Bazaar input or insufficient ask depth: ${catalog.names[id]??id}`;cost=null;break;}
    cost+=value*1.04;
   }
   const capital=cost===null?null:cost*1.03;
   if(product){
    gross=depth(product.sell_summary,units,false);
    const weekly=Math.min(product.quick_status?.buyMovingWeek??0,product.quick_status?.sellMovingWeek??0),daily=weekly/7;
    liquidity=Math.min(1,weekly/168/units);
    if(!Number.isFinite(daily)||units>daily*.05)reason||='Batch exceeds 5% of estimated daily volume';
    if(gross===null)reason||='Insufficient sell-side bid depth';
   }else{
    const quote=source.quote;
    if(quote?.item!==recipe.outputId||!fresh(quote?.fetchedAt,now)||!Number.isFinite(quote?.lowest)||quote.lowest<=0)reason||='Waiting for a fresh Coflnet BIN quote';
    else{gross=Math.max(1,Math.floor(quote.lowest)-1);fee=gross*.035+2000;}
    liquidity=Math.min(1,Math.log1p(source.volume??0)/Math.log(101));
   }
   const total=capital===null?null:capital+fee;
   const net=gross===null?null:product?gross*.97*(1-Math.max(0,tax)/100):gross*.90;
   const profit=total===null||net===null?null:net-total;
   if(total!==null&&total>budget)reason||='Whole batch exceeds your spendable budget';
   if(profit!==null&&profit<minProfit)reason||='Below your minimum net profit';
   const score=profit===null?0:profit/(60+Object.keys(prep.purchases).length*20+prep.steps.length*15+batches*3)*liquidity;
   const row={key:recipe.key,output:recipe.outputId,name:catalog.names[recipe.outputId]??recipe.outputId,batches,units,venue,capital:total,profit,score,eligible:!reason,reason,requirement:recipe.requirement,purchases:prep.purchases,steps:prep.steps,binPrice:product?null:gross,sourceAt:product?market.lastUpdated:source.quote?.fetchedAt??null};
   const prev=best.get(row.output);
   if(!prev||Number(row.eligible)>Number(prev.eligible)||(row.eligible===prev.eligible&&row.score>prev.score))best.set(row.output,row);
  }
 }
 return [...best.values()].sort((a,b)=>b.score-a.score||a.key.localeCompare(b.key));
}
