// Fixed keyless Hypixel resources, cached centrally; expired quotes cannot authorize calculations.
export async function publicMarket(request,{fetchImpl=fetch,cache=globalThis.caches?.default,now=Date.now()}={}) {
 const url=new URL(request.url),market=url.pathname==='/v1/market';
 url.search='';const key=new Request(url.toString());
 const ttl=market?20:3600,limit=market?6*1024*1024:12*1024*1024;
 const valid=data=>data.success===true && (market?data.products&&Number.isFinite(data.lastUpdated)&&data.lastUpdated>=now-60000&&data.lastUpdated<=now+5000:Array.isArray(data.items));
 const cached=await cache?.match(key);
 if(cached){try{if(valid(await cached.clone().json()))return cached;}catch{}}
 try {
  const upstream=await fetchImpl(`https://api.hypixel.net/v2/${market?'skyblock/bazaar':'resources/skyblock/items'}`,{signal:AbortSignal.timeout(10000)});
  if(!upstream.ok)throw new Error(`Hypixel HTTP ${upstream.status}`);
  const reader=upstream.body.getReader();let size=0,parts=[];
  while(true){const r=await reader.read();if(r.done)break;size+=r.value.length;if(size>limit){await reader.cancel();throw new Error('Resource too large');}parts.push(r.value);}
  const bytes=new Uint8Array(size);let offset=0;for(const p of parts){bytes.set(p,offset);offset+=p.length;}
  const data=JSON.parse(new TextDecoder().decode(bytes));if(!valid(data))throw new Error('Fresh market data unavailable');
  const response=new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json','Cache-Control':`public, max-age=${ttl}`,'X-Content-Type-Options':'nosniff'}});
  await cache?.put(key,response.clone());return response;
 }catch{return new Response(JSON.stringify({error:market?'Fresh Bazaar prices unavailable; calculations are waiting':'Item metadata unavailable'}),{status:503,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});}
}
