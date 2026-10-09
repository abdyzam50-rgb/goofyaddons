import {CraftMarket} from '../bazaar-calc/craft-market.mjs';
import {publicMarket} from './public-market.mjs';

// One discovery/quote rotation per isolate, shared by visitors. No profile or contributor key required.
export function createPublicCrafts({fetchImpl=fetch,now=Date.now,cache=globalThis.caches?.default}={}) {
 let market;
 return async function publicCrafts(request,env={}) {
  const url=new URL(request.url);url.search='';const key=new Request(url);
  const saved=await cache?.match(key);
  if(saved)try{const data=await saved.clone().json();if(data.protocol==='goofy-craft-market/1'&&data.generatedAt<=now()+5000&&now()-data.generatedAt<20000)return saved;}catch{}
  market??=new CraftMarket({fetcher:fetchImpl,now,token:()=>env.COFLNET_TOKEN??null,price:async item=>{
   const response=await fetchImpl(`https://sky.coflnet.com/api/item/price/${encodeURIComponent(item)}/bin`,{signal:AbortSignal.timeout(10000),headers:env.COFLNET_TOKEN?{Authorization:`Bearer ${env.COFLNET_TOKEN}`}:{}});
   if(!response.ok)throw new Error('BIN price unavailable');
   const text=await response.text();if(text.length>65536)throw new Error('BIN price response too large');
   const body=JSON.parse(text),lowest=body.lowest,secondLowest=body.secondLowest??null;
   if(!Number.isFinite(lowest)||lowest<=0||lowest>1e13||(secondLowest!==null&&secondLowest!==0&&(!Number.isFinite(secondLowest)||secondLowest<lowest)))throw new Error('Invalid BIN quote');
   return {protocol:'goofy-ah-price/1',item,lowest,secondLowest,source:'coflnet',fetchedAt:now()};
  }});
  // A failed Bazaar request must not misclassify Bazaar products as AH outputs.
  const bazaar=await publicMarket(new Request(new URL('/v1/market',url)),{fetchImpl,now:now(),cache});
  if(!bazaar.ok)return Response.json({error:'Fresh Bazaar prices unavailable; craft discovery is waiting'},{status:503,headers:{'Cache-Control':'no-store'}});
  const data=await market.refresh((await bazaar.json()).products);
  const response=Response.json(data,{headers:{'Cache-Control':data.error?'no-store':'public, max-age=20','X-Content-Type-Options':'nosniff'}});
  if(!data.error)await cache?.put(key,response.clone());
  return response;
 };
}
export const publicCrafts=createPublicCrafts();
