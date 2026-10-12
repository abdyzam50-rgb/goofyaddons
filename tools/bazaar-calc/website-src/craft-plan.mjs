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
export function prepare(recipe,batches,recipes,{canBuy=()=>true,requirements={},byOutput}={}){
 const purchases={},steps=[],available={},blocked=[];let coins=0;
 const require=(id,units,path)=>{
  const use=Math.min(units,available[id]??0);available[id]=(available[id]??0)-use;units-=use;if(!units)return;
  const choices=(byOutput?.get(id)??recipes.filter(r=>r.kind==='CRAFT'&&r.outputId===id)).filter(r=>!path.has(id));
  const basic=BASIC.has(id)?choices.find(r=>!r.requirement):!canBuy(id)?choices.find(r=>!(requirements[r.key]?.length))??choices[0]:null;
  if(!basic){purchases[id]=(purchases[id]??0)+units;return;}
  blocked.push(...(requirements[basic.key]??[]));
  const count=Math.ceil(units/basic.outputCount);coins+=Math.max(0,basic.coins??0)*count;path.add(id);
  for(const [input,qty] of Object.entries(basic.ingredients))require(input,qty*count,path);
  path.delete(id);steps.push({output:id,batches:count,units:count*basic.outputCount});available[id]=(available[id]??0)+count*basic.outputCount-units;
 };
 for(const [id,qty] of Object.entries(recipe.ingredients))require(id,qty*batches,new Set());
 return {purchases,steps,...(coins?{coins}:{}),...(blocked.length?{blocked:[...new Set(blocked)]}:{})};
}
function researchCrafts({catalog,market,ah,history,now=Date.now(),budget=0,minProfit=10000,maxBatches=16,tax=1.25,requirements={}}){
 if(!market?.success||!fresh(market.lastUpdated,now))return [];
 const products=market.products??{},best=new Map(),recipes=catalog.recipes.filter(r=>r.kind==='CRAFT');
 const byOutput=new Map();for(const r of recipes){const list=byOutput.get(r.outputId)??[];list.push(r);byOutput.set(r.outputId,list);}
 const ahRows=new Map((fresh(ah?.generatedAt,now)?ah.rows??[]:[]).filter(r=>fresh(r.sourceAt,now,300000)).map(r=>[r.item,r]));
 const historical=new Map((history?.protocol==='goofy-ah-history/1'&&fresh(history.generatedAt,now,5400000)?history.rows??[]:[]).filter(r=>fresh(r.lastAt,now,7*86400000)&&Number.isFinite(r.price)&&r.price>0&&r.price<=1e13).map(r=>[r.item,r]));
 for(const recipe of recipes){
  const product=products[recipe.outputId],source=ahRows.get(recipe.outputId),venue=product?'BAZAAR':'AH';

  for(let batches=1;batches<=Math.min(16,Math.max(1,Math.floor(maxBatches)));batches++){
   if(!product&&batches>1)break;
   const quoteFor=id=>{const q=ahRows.get(id)?.quote;return q?.item===id&&fresh(q.fetchedAt,now)&&Number.isFinite(q.lowest)&&q.lowest>0&&q.lowest<=1e13?q:null;};
   const prep=prepare(recipe,batches,recipes,{canBuy:id=>!!products[id]||!!quoteFor(id)||historical.has(id),requirements,byOutput}),units=recipe.outputCount*batches;
   let historicalPricing=false;
   let cost=Math.max(0,recipe.coins??0)*batches+(prep.coins??0),reason=[...(requirements[recipe.key]??[]),...(prep.blocked??[])].join('; '),gross=null,fee=0,liquidity=0;
   for(const [id,qty] of Object.entries(prep.purchases)){
    const value=products[id]?depth(products[id].buy_summary,qty,true):quoteFor(id)?quoteFor(id).lowest*qty:historical.has(id)?historical.get(id).price*qty:null;
    if(!products[id]&&!quoteFor(id)&&historical.has(id)){historicalPricing=true;reason||='Historical AH component prices; verify fresh BINs';}
    if(value===null){reason||=`Missing input price or insufficient Bazaar ask depth: ${catalog.names[id]??id}`;cost=null;break;}
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
    const quote=quoteFor(recipe.outputId),reference=historical.get(recipe.outputId);
    if(!quote){
     if(reference){gross=Math.max(1,Math.floor(reference.price)-1)*units;historicalPricing=true;reason||='Historical AH price estimate; verify a fresh BIN quote';}
     else reason||=source?.quoteError?`Coflnet BIN unavailable: ${source.quoteError}`:'Waiting for a fresh Coflnet BIN quote';
    }else gross=Math.max(1,Math.floor(quote.lowest)-1)*units;
    if(gross!==null)fee=gross*.035+2000*units;
    const volume=source?.volume??(fresh(reference?.demandAt,now,7*86400000)?reference?.volume:null);
    liquidity=Math.min(1,Math.log1p(volume??0)/Math.log(101));
    if(gross!==null&&!(volume>0))reason||=source?.demandError?`AH demand unavailable: ${source.demandError}`:volume===0?'No reported AH sales; profit estimate only':'AH demand unavailable; profit estimate only';
   }
   const total=capital===null?null:capital+fee;
   const net=gross===null?null:product?gross*.97*(1-Math.max(0,tax)/100):gross*.90;
   const profit=total===null||net===null?null:net-total;
   if(total!==null&&total>budget)reason||='Whole batch exceeds your spendable budget';
   if(profit!==null&&profit<minProfit)reason||='Below your minimum net profit';
   const score=profit===null?0:profit/(60+Object.keys(prep.purchases).length*20+prep.steps.length*15+batches*3)*liquidity;
   const row={pricing:historicalPricing?'HISTORY':gross===null?'UNPRICED':'LIVE',key:recipe.key,output:recipe.outputId,name:catalog.names[recipe.outputId]??recipe.outputId,batches,units,venue,capital:total,profit,score,eligible:!reason,reason,requirement:recipe.requirement,purchases:prep.purchases,steps:prep.steps,binPrice:product?null:gross===null?null:gross/units,sourceAt:product?market.lastUpdated:source?.quote?.fetchedAt??null};
   const prev=best.get(row.output);
   if(!prev||Number(row.eligible)>Number(prev.eligible)||(row.eligible===prev.eligible&&row.score>prev.score))best.set(row.output,row);
  }
 }
 return [...best.values()].sort((a,b)=>Number(b.profit!==null)-Number(a.profit!==null)||b.score-a.score||a.key.localeCompare(b.key));
}

// Craft plans for the public website. rankLikeMod is a line-for-line port of the mod's
// CraftFlipPlanner.rank: same strategies, batch limits, preparation, fees, depth, volume checks,
// scoring and exclusion reasons. craft-parity.json is asserted by both this module's tests and
// the mod's CraftPlannerParityTest, so a change to either side that breaks parity fails CI.
// Things only the game can observe (held items, other positions, compactors) are not modelled;
// free inventory slots are a user setting that defaults to an empty inventory.
export const STRATEGIES=['ORDER_OFFER','INSTANT_OFFER','ORDER_INSTANT','INSTANT_INSTANT'];
const STRATEGY={ORDER_OFFER:[false,false],INSTANT_OFFER:[true,false],ORDER_INSTANT:[false,true],INSTANT_INSTANT:[true,true]};
export const strategyLabel=s=>`${STRATEGY[s][0]?'instant':'order'} → ${STRATEGY[s][1]?'instant':'offer'}`;
const INSTANT_BUY_FEE=1.04,SALE_FLOOR=0.97;
const INTERMEDIATES=BASIC;
const BOOK=/^ENCHANTMENT_[A-Z][A-Z0-9_]*_(?:[1-9][0-9]?|100)$/;
/** Books and catalogued unstackable items take one slot per unit; everything else stacks to 64. */
export function inventorySlotsForProduct(id,units,unstackable=new Set()){
 if(units<0)throw new Error('Negative inventory quantity');
 return id!=null&&(unstackable.has(id)||BOOK.test(id))?units:Math.floor((units+63)/64);
}
const sortedKeys=o=>Object.keys(o).sort((a,b)=>a<b?-1:a>b?1:0);
function intermediate(byOutput,id){
 if(!INTERMEDIATES.has(id))return null;
 const recipe=(byOutput.get(id)??[]).find(r=>r.kind==='CRAFT'&&!(r.requirement??'').trim());
 if(!recipe)throw new Error(`Missing basic intermediate recipe: ${id}`);
 return recipe;
}
/** IngredientPreparation.plan with nothing held. */
export function modPreparation(byOutput,deficits){
 const available={},purchases={},crafts=[],products=new Set();
 const require=(id,units,path)=>{
  products.add(id);const taken=Math.min(units,available[id]??0);available[id]=(available[id]??0)-taken;units-=taken;
  if(units===0)return;
  const recipe=intermediate(byOutput,id);
  if(!recipe){purchases[id]=(purchases[id]??0)+units;return;}
  if(path.has(id))throw new Error(`Cyclic intermediate recipe: ${id}`);path.add(id);
  const batches=Math.floor((units+recipe.outputCount-1)/recipe.outputCount);
  for(const input of sortedKeys(recipe.ingredients))require(input,recipe.ingredients[input]*batches,path);
  path.delete(id);crafts.push({output:id,batches});available[id]=(available[id]??0)+batches*recipe.outputCount-units;
 };
 for(const id of sortedKeys(deficits))require(id,deficits[id],new Set());
 const sorted={};for(const id of sortedKeys(purchases))sorted[id]=purchases[id];
 return {purchases:sorted,crafts,products};
}
/** IngredientPreparation.dependencies: inputs plus everything basic intermediates expand into. */
export function dependencies(byOutput,inputs){
 const result=new Set(inputs),queue=[...inputs];
 while(queue.length){const recipe=intermediate(byOutput,queue.shift());if(recipe)for(const id of Object.keys(recipe.ingredients))if(!result.has(id)){result.add(id);queue.push(id);}}
 return result;
}
export function indexRecipes(recipes){const byOutput=new Map();for(const r of recipes){const list=byOutput.get(r.outputId)??[];list.push(r);byOutput.set(r.outputId,list);}return byOutput;}
// ProductionPlanner.depth / instantSellValue: stable sort, then any invalid level reached voids the quote.
function ladder(product,side,descending){
 const levels=product?.[side];if(!Array.isArray(levels))return null;
 const rows=levels.map(l=>{if(l===null||typeof l!=='object'||typeof l.pricePerUnit!=='number'||typeof l.amount!=='number')throw new Error('Malformed order book');return l;});
 return rows.map((l,i)=>[l,i]).sort((a,b)=>(descending?b[0].pricePerUnit-a[0].pricePerUnit:a[0].pricePerUnit-b[0].pricePerUnit)||a[1]-b[1]).map(x=>x[0]);
}
function walk(levels,units){
 if(!levels)return null;let total=0,remaining=units;
 for(const {pricePerUnit:price,amount} of levels){
  if(!Number.isFinite(price)||price<=0||!Number.isFinite(amount)||amount<0)return null;
  const take=Math.min(remaining,Math.floor(amount));total+=take*price;remaining-=take;if(remaining===0)return Number.isFinite(total)?total:null;
 }
 return null;
}
function topPrice(product,side){
 const summary=product[side];if(!Array.isArray(summary)||!summary.length)return -1;
 const value=summary[0]?.pricePerUnit;if(typeof value!=='number')throw new Error('Malformed order book');
 return Number.isFinite(value)&&value>0?value:-1;
}
const DEAD_BID_RATIO=0.5;
function books(products){
 const asks=new Map(),bids=new Map();
 const cached=(cache,id,side,descending)=>{if(!cache.has(id)){let value;try{value=ladder(products[id],side,descending);}catch{value=null;}cache.set(id,value);}return cache.get(id);};
 return {
  // BazaarQuotes.orderableBid: a top bid under half the lowest offer never fills.
  orderable(id){const product=products[id];const bid=topPrice(product,'sell_summary'),ask=topPrice(product,'buy_summary');return bid<=0||ask<=0||bid>=ask*DEAD_BID_RATIO;},
  buy(strategy,id,units){const product=products[id];if(!product||units<1)return null;
   // BazaarStrategy.ingredientBuy: inputs without a fillable bid are priced at the instant buy.
   if(!STRATEGY[strategy][0]&&this.orderable(id)){const price=topPrice(product,'sell_summary');return price>0?price*units:null;}
   const book=walk(cached(asks,id,'buy_summary',false),units);return book===null?null:book*INSTANT_BUY_FEE;},
  sell(strategy,id,units){const product=products[id];if(!product||units<1)return null;
   if(!STRATEGY[strategy][1]){const price=topPrice(product,'buy_summary');return price>0?price*units:null;}
   return walk(cached(bids,id,'sell_summary',true),units);},
 };
}
// BazaarQuotes.weeklyVolume / weeklyFlow: missing or invalid volumes count as zero.
function weeklyVolume(quick,key){if(!quick||!(key in quick))return 0;const v=Number(quick[key]);return Number.isFinite(v)?Math.max(0,v):0;}
function weeklyFlow(product){const quick=product?.quick_status;return Math.min(weeklyVolume(quick,'sellMovingWeek'),weeklyVolume(quick,'buyMovingWeek'));}
function hourlyActivity(product){
 const quick=product?.quick_status;
 if(!quick||!('buyMovingWeek' in quick)||!('sellMovingWeek' in quick))return 0;
 const week=Math.min(Number(quick.buyMovingWeek),Number(quick.sellMovingWeek));
 return Number.isFinite(week)&&week>0?week/168:0;
}
/** AuctionPricing.parse + listingPrice. Throws with a readable reason when no price is safe. */
export function listingPrice(quote,item,now){
 if(!quote||quote.protocol!=='goofy-ah-price/1'||quote.item!==item)throw new Error(`Auction price reply is not for ${item}`);
 const lowest=quote.lowest,second=quote.secondLowest??null,at=quote.fetchedAt;
 if(typeof lowest!=='number'||!Number.isFinite(lowest)||lowest<1||lowest>1e12||second!==null&&(typeof second!=='number'||!Number.isFinite(second)||second<lowest||second>1e12))throw new Error(`Auction price for ${item} is invalid`);
 if(!Number.isFinite(at)||at>now+5000||now-at>300000)throw new Error(`Auction price for ${item} is stale`);
 const low=Math.floor(lowest),next=second===null?null:Math.floor(second);
 if(next!==null&&low*2<next)throw new Error('AH lowest BIN is under half the next one; outlier price');
 return Math.max(1,low-1);
}
/** ProductionPlanner.listingFeeLimit. */
export function listingFeeLimit(price){
 if(price<1)throw new Error('BIN price must be positive');
 const rate=price<10_000_000?0.01:price<100_000_000?0.02:0.025;
 return Math.ceil(price*rate)+1200;
}
export const AH_RECONCILIATION='AH sale/expiry/claim reconciliation is not implemented; use production test for one verified listing';
/**
 * CraftFlipPlanner.rank. `requirementFor(recipe,dependencies)` replaces the mod's account checks
 * and returns a blocking reason or null. Returns {routes, excluded, excludedByOutput}.
 */
export function rankLikeMod({catalog,market,ah,now,budget,minimumProfit,bazaarTax,maxBatches,venue='BOTH',strategy:strategySetting='AUTO',freeSlots=36,requirementFor=()=>null}){
 const excluded={},excludedByOutput={},result={routes:[],excluded,excludedByOutput};
 const exclude=(reason,output)=>{excluded[reason]=(excluded[reason]??0)+1;if(output){const m=excludedByOutput[output]??={};m[reason]=(m[reason]??0)+1;}};
 if(!(Number.isFinite(budget)&&budget>0)){exclude('No observed spendable budget');return result;}
 if(!market||!('lastUpdated' in market)||!fresh(market.lastUpdated,now)
  ||!Number.isFinite(minimumProfit)||minimumProfit<0||!Number.isFinite(bazaarTax)||bazaarTax<0||bazaarTax>=100
  ||!Number.isInteger(maxBatches)||maxBatches<1||maxBatches>1024||!['BOTH','BAZAAR','AH'].includes(venue)
  ||!(strategySetting==='AUTO'||STRATEGIES.includes(strategySetting))){exclude('Fresh Bazaar prices or valid craft settings unavailable');return result;}
 const products=market.products;if(!products||typeof products!=='object'){exclude('Bazaar products unavailable');return result;}
 const auctions=new Map();for(const row of Array.isArray(ah?.rows)?ah.rows:[])if(row&&typeof row.item==='string')auctions.set(row.item,row);
 const recipes=catalog.recipes,byOutput=indexRecipes(recipes),book=books(products),activity=new Map(),best=new Map();
 const single=new Set(catalog.unstackable??[]);
 for(const recipe of recipes){
  if(recipe.kind!=='CRAFT')continue;
  const output=recipe.outputId,bz=Object.hasOwn(products,output),sale=bz?'BAZAAR':'AH';
  if(venue!=='BOTH'&&venue!==sale)continue;
  if(!bz&&!auctions.has(output)){exclude('AH quote unavailable',output);continue;}
  let requirement;
  try{requirement=requirementFor(recipe,dependencies(byOutput,Object.keys(recipe.ingredients)))??null;}catch(e){exclude('Malformed quote or unsupported recipe',output);continue;}
  const limit=bz?Math.min(1024,maxBatches):1,preparations=new Map();
  for(const strategy of bz?STRATEGIES:['INSTANT_INSTANT'])for(let batches=1;batches<=limit;batches++)try{
   if(bz&&strategySetting!=='AUTO'&&strategy!==strategySetting)continue;
   const [instantBuy,instantSell]=STRATEGY[strategy];
   if(!preparations.has(batches)){const quantities={};for(const [id,n] of Object.entries(recipe.ingredients))quantities[id]=n*batches;preparations.set(batches,modPreparation(byOutput,quantities));}
   const preparation=preparations.get(batches);
   let reason=requirement;
   const outputSlots=inventorySlotsForProduct(output,recipe.outputCount*batches,single);
   let slots=outputSlots,cost=0,orderWait=0,priced=true;
   for(const [id,units] of Object.entries(preparation.purchases)){
    const product=products[id];
    const amount=product&&typeof product==='object'&&!Array.isArray(product)?book.buy(strategy,id,units):null;
    if(amount===null){priced=false;break;}
    cost+=amount;slots+=inventorySlotsForProduct(id,units,single);
    if(!instantBuy&&book.orderable(id)){
     if(!activity.has(id))activity.set(id,hourlyActivity(product));
     const rate=activity.get(id)*0.05;
     if(rate<=0)reason='Input order activity unavailable';
     else orderWait=Math.max(orderWait,units/rate*3600);
    }
   }
   if(!priced||cost<=0){exclude('Missing input prices or insufficient buy depth',output);continue;}
   for(const craft of preparation.crafts){
    const mid=(byOutput.get(craft.output)??[]).find(r=>r.kind==='CRAFT');if(!mid)throw new Error('Missing intermediate');
    slots=Math.max(slots,outputSlots+inventorySlotsForProduct(mid.outputId,mid.outputCount*craft.batches,single)+Object.entries(mid.ingredients).reduce((n,[id,q])=>n+inventorySlotsForProduct(id,q*craft.batches,single),0));
   }
   if(slots>Math.max(0,freeSlots))reason='Insufficient empty inventory slots for inputs, intermediate crafts and output';
   let fee=0,net,liquidity,price=null;const units=recipe.outputCount*batches;
   if(bz){
    const product=products[output],gross=book.sell(strategy,output,units);
    if(gross===null){exclude('Insufficient Bazaar sell depth',output);continue;}
    net=gross*SALE_FLOOR*(1-bazaarTax/100);
    const week=weeklyFlow(product);
    if(!Number.isFinite(week)||week<=0){exclude('Daily volume unavailable',output);continue;}
    if(units>week/7*0.05)reason='Batch exceeds 5% of observed daily market volume';
    liquidity=week/168;
    if(!instantSell)orderWait+=units/(liquidity*0.05)*3600;
   }else{
    if(recipe.outputCount!==1){exclude('AH output stacks are not listed automatically',output);continue;}
    const row=auctions.get(output),quote=row.quote;
    if(!quote||typeof quote.fetchedAt!=='number'||now-quote.fetchedAt>60000||quote.fetchedAt>now+5000){exclude('AH quote stale',output);continue;}
    try{price=listingPrice(quote,output,now);}catch(e){exclude(e.message.includes('outlier')?'AH lowest BIN looks like an outlier':'AH quote invalid',output);continue;}
    fee=listingFeeLimit(price);
    net=price*0.90*0.965;
    if(reason===null)reason=AH_RECONCILIATION;
    const volume=row.volume;
    if(typeof volume!=='number'||!Number.isFinite(volume)||volume<=2){exclude('AH demand unavailable or too low',output);continue;}
    liquidity=Math.min(1,Math.log1p(volume)/Math.log(101));
   }
   const capital=cost*1.03+fee,profit=net-capital;
   if(!Number.isFinite(profit)||profit<minimumProfit||profit<=0){exclude('Below minimum net profit after fees and price allowance',output);continue;}
   if(capital>budget)reason='Whole batch exceeds spendable budget';
   let seconds=60+Object.keys(preparation.purchases).length*20+preparation.crafts.length*15+batches*3+orderWait;
   if(bz)seconds=Math.max(seconds,units/liquidity*3600);
   const score=profit/seconds*(bz?1:liquidity);
   const route={recipe,batches,venue:sale,capital,profit,binPrice:price,fee,score,reason,eligible:reason===null,strategy,inventorySlots:slots,preparation};
   const old=best.get(output);
   if(!old||route.eligible&&!old.eligible||route.eligible===old.eligible&&route.score>old.score)best.set(output,route);
  }catch(e){exclude('Malformed quote or unsupported recipe',output);}
 }
 result.routes=[...best.values()].sort((a,b)=>b.score-a.score||(a.recipe.key<b.recipe.key?-1:a.recipe.key>b.recipe.key?1:0));
 return result;
}
const topReason=reasons=>Object.entries(reasons??{}).sort((a,b)=>b[1]-a[1])[0]?.[0]??null;
/**
 * Rows for the website: every route the mod would rank, with the mod's numbers, plus a research
 * row for each other catalog output. Research rows are never feasible; they say why the mod
 * skips the craft and keep the older AH-component and history estimates for exploration.
 */
export function planCrafts({catalog,market,ah,history,now=Date.now(),budget=0,minProfit=10000,maxBatches=64,tax=1.25,requirements={},venue='BOTH',strategy='AUTO',freeSlots=36}){
 if(!market?.success||!fresh(market.lastUpdated,now))return [];
 const ranked=rankLikeMod({catalog,market,ah,now,budget,minimumProfit:minProfit,bazaarTax:tax,maxBatches:Math.max(1,Math.min(1024,Math.floor(maxBatches)||1)),venue,strategy,freeSlots,
  requirementFor:recipe=>(requirements[recipe.key]??[]).join('; ')||null});
 const names=catalog.names??{},rows=ranked.routes.map(r=>({pricing:'LIVE',key:r.recipe.key,output:r.recipe.outputId,name:names[r.recipe.outputId]??r.recipe.outputId,batches:r.batches,units:r.recipe.outputCount*r.batches,venue:r.venue,
  strategy:r.strategy,buySell:r.venue==='BAZAAR'?strategyLabel(r.strategy):null,capital:r.capital,profit:r.profit,score:r.score,coinsPerHour:r.score*3600,eligible:r.eligible,reason:r.reason??'',requirement:r.recipe.requirement,
  inventorySlots:r.inventorySlots,purchases:r.preparation.purchases,steps:r.preparation.crafts.map(c=>({output:c.output,batches:c.batches,units:c.batches*((catalog.recipes.find(x=>x.kind==='CRAFT'&&x.outputId===c.output)?.outputCount)??1)})),
  binPrice:r.binPrice,sourceAt:r.venue==='BAZAAR'?market.lastUpdated:ah?.rows?.find?.(x=>x.item===r.recipe.outputId)?.quote?.fetchedAt??null}));
 const rankedOutputs=new Set(rows.map(r=>r.output));
 const research=researchCrafts({catalog,market,ah,history,now,budget,minProfit,maxBatches:Math.min(16,maxBatches),tax,requirements})
  .filter(r=>!rankedOutputs.has(r.output)&&(venue==='BOTH'||r.venue===venue))
  .map(r=>{const skip=topReason(ranked.excludedByOutput[r.output]);return {...r,pricing:r.pricing==='LIVE'?'RESEARCH':r.pricing,strategy:null,buySell:null,coinsPerHour:null,eligible:false,
   reason:[skip?`Mod skips this craft: ${skip}`:'Mod skips this craft',r.reason].filter(Boolean).join('. ')};});
 return [...rows,...research];
}
