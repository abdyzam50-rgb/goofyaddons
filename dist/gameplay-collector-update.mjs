// Goofy gameplay collector: approvals, private profile lookup, public market and publishing status.
/*
MIT License

Copyright (c) 2020 Moulberry

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
// Public gameplay evidence contains ratios and durations, never balances or receipts.
const COMMUNITY_PROTOCOL='goofy-community/1';
const DAY=86400000;
const MAX_UPLOAD_SAMPLES=20; // Two SQL statements per sample stay below Free D1's 50-query limit.
const DEFAULT_REPOSITORY='abdyzam50-rgb/goofyaddons';
const DATA_BRANCH='gameplay-data';
const product=/^[A-Z0-9_]{1,160}$/;
const digest=/^[a-f0-9]{64}$/;
const repositoryValid=v=>typeof v==='string'&&/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(v);
async function hash(text) {
 const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text));
 return [...new Uint8Array(bytes)].map(b=>b.toString(16).padStart(2,'0')).join('');
}
function validateSample(s,now=Date.now()) {
 const keys=['id','engine','inputId','outputId','inputUnits','batch','completedAt','observedMillis','profitRatio','forecast'];
 if(!s||typeof s!=='object'||Array.isArray(s)||Object.keys(s).some(k=>!keys.includes(k))
  ||typeof s.id!=='string'||!digest.test(s.id)||!['books','general'].includes(s.engine)||![s.inputId,s.outputId].every(v=>typeof v==='string'&&product.test(v))
  ||![s.inputUnits,s.batch].every(v=>Number.isInteger(v)&&v>=1&&v<=4096)
  ||!Number.isInteger(s.completedAt)||s.completedAt%3600000!==0||s.completedAt<=0
  ||s.completedAt>now||s.completedAt<now-7*DAY
  ||!Number.isInteger(s.observedMillis)||s.observedMillis<1000||s.observedMillis>DAY||s.observedMillis%1000!==0
  ||s.profitRatio!==null&&(!Number.isFinite(s.profitRatio)||s.profitRatio< -10||s.profitRatio>10))throw new Error('Invalid shared gameplay sample');
 const f=s.forecast;
 if(!f||Object.keys(f).sort().join(',')!=='cycleSeconds,inputPerDay,outputPerDay'
  ||![f.cycleSeconds,f.inputPerDay,f.outputPerDay].every(v=>Number.isFinite(v)&&v>0)
  ||f.cycleSeconds>30*86400||f.inputPerDay>1e15||f.outputPerDay>1e15)throw new Error('Invalid shared forecast');
 if(s.engine==='general') {
  if(s.inputId!==s.outputId||s.inputUnits!==s.batch)throw new Error('Unsupported shared general recipe');
 } else {
  const a=/^(ENCHANTMENT_.+)_(\d+)$/.exec(s.inputId),b=/^(ENCHANTMENT_.+)_(\d+)$/.exec(s.outputId);
  if(!a||!b||a[1]!==b[1]||+a[2]<1||+b[2]>10||+b[2]<=+a[2]||s.batch!==1||s.inputUnits!==2**(+b[2]-+a[2]))throw new Error('Unsupported shared book recipe');
 }
 return {...s,forecast:{...f}};
}
function validateSubmission(body,now=Date.now()) {
 if(!body||body.protocol!==COMMUNITY_PROTOCOL||Object.keys(body).sort().join(',')!=='protocol,samples'
  ||!Array.isArray(body.samples)||body.samples.length<1||body.samples.length>MAX_UPLOAD_SAMPLES)throw new Error('Invalid gameplay submission');
 const samples=body.samples.map(s=>validateSample(s,now));
 if(new Set(samples.map(s=>s.id)).size!==samples.length)throw new Error('Duplicate gameplay sample');
 return samples;
}
function validateDataset(body,now=Date.now()) {
 if(!body||body.protocol!==COMMUNITY_PROTOCOL||Object.keys(body).sort().join(',')!=='generatedAt,protocol,samples'
  ||!Number.isFinite(body.generatedAt)||body.generatedAt<now-7*DAY||body.generatedAt>now+5000
  ||!Array.isArray(body.samples)||body.samples.length>2000)throw new Error('Invalid or stale community dataset');
 const ids=new Set();
 const samples=body.samples.map(row=>{
  if(!row||typeof row.contributor!=='string'||!digest.test(row.contributor))throw new Error('Invalid contributor');
  const {contributor,...s}=row;const sample=validateSample(s,now),key=`${contributor}:${s.id}`;
  if(ids.has(key))throw new Error('Duplicate public sample');ids.add(key);
  return {contributor,...sample};
 });
 return {protocol:COMMUNITY_PROTOCOL,generatedAt:body.generatedAt,samples};
}
async function exportSamples(rows,salt,now=Date.now()) {
 if(typeof salt!=='string'||salt.length<32)throw new Error('Missing private sharing identity');
 const samples=[];
 for(const row of rows) {
  if(!row.eligible||row.censored||row.profit==null||row.proceeds==null||!row.forecast||!row.eventId)continue;
  const ratio=row.expectedProfit>0?Math.max(-10,Math.min(10,Math.round(row.profit/row.expectedProfit*1000)/1000)):null;
  const sample={id:await hash(`${salt}:${row.eventId}`),engine:row.engine,inputId:row.inputId,outputId:row.outputId,
   inputUnits:row.inputUnits,batch:row.batch,completedAt:Math.floor(row.completedAt/3600000)*3600000,
   observedMillis:Math.max(1000,Math.round(row.observedMillis/1000)*1000),profitRatio:ratio,
   forecast:{cycleSeconds:row.forecast.cycleSeconds,inputPerDay:row.forecast.inputPerDay,outputPerDay:row.forecast.outputPerDay}};
  try{samples.push(validateSample(sample,now));}catch{} // Old, interrupted and incomplete observations stay private.
 }
 return samples;
}

// MIT NotEnoughUpdates-REPO constants/leveling.json, commit 777a3aae04ff462ea20ea9b346e9208d7ce9adc5.
const LEVELS={"leveling_xp":[50,125,200,300,500,750,1000,1500,2000,3500,5000,7500,10000,15000,20000,30000,50000,75000,100000,200000,300000,400000,500000,600000,700000,800000,900000,1000000,1100000,1200000,1300000,1400000,1500000,1600000,1700000,1800000,1900000,2000000,2100000,2200000,2300000,2400000,2500000,2600000,2750000,2900000,3100000,3400000,3700000,4000000,4300000,4600000,4900000,5200000,5500000,5800000,6100000,6400000,6700000,7000000],"leveling_caps":{"taming":50,"mining":60,"foraging":50,"enchanting":60,"carpentry":50,"farming":50,"combat":60,"fishing":50,"alchemy":50,"runecrafting":25,"catacombs":50,"HOTM":10,"social":25,"hunting":50,"HOTF":8},"slayer_xp":{"zombie":[5,15,200,1000,5000,20000,100000,400000,1000000],"spider":[5,15,200,1000,5000,20000,100000,400000,1000000],"wolf":[10,30,250,1500,5000,20000,100000,400000,1000000],"enderman":[10,30,250,1500,5000,20000,100000,400000,1000000],"blaze":[10,30,250,1500,5000,20000,100000,400000,1000000],"vampire":[20,75,240,840,2400]},"HOTM":[0,3000,9000,25000,60000,100000,150000,210000,290000,400000]};

// Read-only account lookup. Hypixel profile fields: PublicAPI v2 SkyBlock profiles;
// progression thresholds: MIT NotEnoughUpdates-REPO constants/leveling.json.
// Nothing returned here is written to the shared gameplay dataset or GitHub.
const profileJson=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','Access-Control-Allow-Origin':'*'}});
const present=n=>typeof n==='number'&&Number.isFinite(n)&&n>=0;
const clean=s=>typeof s==='string'?s.replace(/§./g,'').slice(0,80):'';
function xpLevel(xp,steps,cap=steps.length) {
 if(!present(xp))return null;
 let level=0,left=xp;
 for(const cost of steps.slice(0,cap)){if(left<cost)break;left-=cost;level++;}
 return level;
}
function summarizeProfiles(data,uuid,resources,now=Date.now()) {
 if(data?.success!==true||!Array.isArray(data.profiles))throw new Error('Profile data unavailable');
 const collections=new Map();
 for(const category of Object.values(resources?.collections??{}))for(const [id,item] of Object.entries(category.items??{}))collections.set(id,clean(item.name));
 return data.profiles.slice(0,10).flatMap(p=>{
  const member=p.members?.[uuid];if(!member)return [];
  const unknown=[],skills={},slayers={},tiers={},reputation={};
  const experience=member.player_data?.experience;
  for(const name of ['enchanting','taming','foraging']) {
   const xp=experience?.[`SKILL_${name.toUpperCase()}`]??member[`experience_skill_${name}`];
   const level=xpLevel(xp,LEVELS.leveling_xp,LEVELS.leveling_caps[name]);
   if(level===null)unknown.push(name);else skills[name[0].toUpperCase()+name.slice(1)]=level;
  }
  const hotm=xpLevel(member.mining_core?.experience,LEVELS.HOTM);
  if(hotm===null)unknown.push('Heart of the Mountain');
  const quick=member.mining_core?.nodes?.forge_time;
  if(!present(quick))unknown.push('Quick Forge');
  const unlocked=member.player_data?.unlocked_coll_tiers??member.unlocked_coll_tiers;
  if(Array.isArray(unlocked)&&collections.size) {
   for(const value of unlocked) {
    const m=typeof value==='string'&&/^(.+)_(\d+)$/.exec(value),name=m&&collections.get(m[1]);
    if(name)tiers[name]=Math.max(tiers[name]??0,Number(m[2]));
   }
  }else unknown.push('collections');
  if(member.slayer_bosses)for(const [id,boss] of Object.entries(member.slayer_bosses)) {
   const table=LEVELS.slayer_xp[id];
   if(table&&present(boss.xp))slayers[({enderman:'Enderman',vampire:'Vampire'})[id]??id[0].toUpperCase()+id.slice(1)]=table.filter(n=>boss.xp>=n).length;
  }else unknown.push('slayers');
  for(const [field,label] of [['barbarians_reputation','Barbarian'],['mages_reputation','Mage']]) {
   const n=member.nether_island_player_data?.[field];if(present(n))reputation[label]=n;
  }
  if(!Object.keys(reputation).length)unknown.push('faction reputation');
  // SkyBlock profiles do not reliably publish the current vanilla XP level.
  unknown.push('current XP levels');
  const purse=member.currencies?.coin_purse??member.coin_purse;
  return [{id:clean(p.profile_id),name:clean(p.cute_name)||'Profile',selected:p.selected===true,gameMode:clean(p.game_mode)||'normal',
   purse:present(purse)&&purse<=1e13?purse:null,fetchedAt:now,unknown,
   stats:{hotmTier:hotm??0,quickForgeLevel:present(quick)?Math.min(20,quick):0,enchantingLevel:skills.Enchanting??0,skills,collections:tiers,slayers,reputation,
    xpLevels:0,ignoreRequirements:false,coleMoltenForge:false,quadTaxes:false,npcShoppingSpree:false}}];
 });
}
class AccountLookupError extends Error {
 constructor(message,status){super(message);this.status=status;}
}
async function lookupProfiles(username,key,fetcher=fetch,now=Date.now()) {
 const get=async(url,headers={})=>{
  const service=url.startsWith('https://api.hypixel.net/')?'Hypixel account service':url.startsWith('https://playerdb.co/')?'PlayerDB username service':'Minecraft username service';
  let r;
  try {r=await fetcher(url,{headers,signal:AbortSignal.timeout(10000)});}
  catch {throw new AccountLookupError(`${service} could not be reached; try again shortly`);}
  if(!r.ok) {
   if(r.status===404&&service!=='Hypixel account service')throw new AccountLookupError('Username not found');
   if(r.status===429)throw new AccountLookupError(`${service} is rate limited; try again shortly`);
   if(service==='Hypixel account service'&&[401,403].includes(r.status))throw new AccountLookupError(`Hypixel rejected profile access (HTTP ${r.status}). Check the Worker HYPIXEL_API_KEY and its Hypixel application permissions`);
   throw new AccountLookupError(`${service} returned HTTP ${r.status}; try again shortly`,r.status);
  }
  try{return await r.json();}
  catch {throw new AccountLookupError(`${service} returned an invalid response; try again shortly`);}
 };
 let player;
 try {player=await get(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(username)}`);}
 catch(error) {
  // Both endpoints are public, official name-to-UUID lookups. Do not retry
  // unknown names or rate limits, and never send the Hypixel key to either.
  if(!(error instanceof AccountLookupError)||!(error.status===403||error.status>=500))throw error;
  try {player=await get(`https://api.minecraftservices.com/minecraft/profile/lookup/name/${encodeURIComponent(username)}`);}
  catch(fallbackError) {
   if(!(fallbackError instanceof AccountLookupError)||!(fallbackError.status===403||fallbackError.status>=500))throw fallbackError;
   // Public identity lookup only; no account stats, API key or credentials.
   const result=await get(`https://playerdb.co/api/player/minecraft/${encodeURIComponent(username)}`);
   const identity=result?.data?.player;
   if(result?.success!==true||result?.code!=='player.found'||!identity||typeof identity.username!=='string'||identity.username.toLowerCase()!==username.toLowerCase())throw new AccountLookupError('PlayerDB could not confirm that Minecraft username');
   player={id:identity.raw_id,name:identity.username};
  }
 }
 if(!/^[a-f0-9]{32}$/i.test(player?.id))throw new AccountLookupError('Username not found');
 const [data,resources]=await Promise.all([
  get(`https://api.hypixel.net/v2/skyblock/profiles?uuid=${player.id}`,{'API-Key':key}),
  get('https://api.hypixel.net/v2/resources/skyblock/collections').catch(()=>null)
 ]);
 return {protocol:'goofy-profile/1',username:clean(player.name),fetchedAt:now,profiles:summarizeProfiles(data,player.id,resources,now)};
}
async function handleProfileLookup(request,env,now=Date.now(),fetcher=fetch) {
 const username=new URL(request.url).searchParams.get('username')??'';
 if(!/^[A-Za-z0-9_]{1,16}$/.test(username))return profileJson(400,{error:'Enter a Minecraft username (letters, numbers, underscore)'});
 if(!env.HYPIXEL_API_KEY||!env.PROFILE_RATE_LIMITER)return profileJson(503,{error:'Profile lookup is not configured; use a manual budget and unlocks'});
 // Cloudflare supplies this header; a missing limiter fails closed. No public key registration.
 const ip=request.headers.get('CF-Connecting-IP');
 if(!ip)return profileJson(400,{error:'Client address unavailable'});
 const limit=await env.PROFILE_RATE_LIMITER.limit({key:ip});
 if(!limit.success)return profileJson(429,{error:'Too many lookups; try again in a minute'});
 try{
  const cacheKey=username.toLowerCase(),cached=profileCache.get(cacheKey);
  if(cached&&now-cached.at<300000)return profileJson(200,cached.value);
  const value=await lookupProfiles(username,env.HYPIXEL_API_KEY,fetcher,now);
  if(profileCache.size>=256)profileCache.delete(profileCache.keys().next().value);
  profileCache.set(cacheKey,{at:now,value});
  return profileJson(200,value);
 }
 catch(error){return profileJson(502,{error:error instanceof AccountLookupError||error.message==='Profile data unavailable'?error.message:'Account service unavailable'});}
}
// Short-lived isolate memory only: no D1, persistent account history or shared publication.
const profileCache=new Map();

// Public aggregate state only: no contributor IDs, tokens, samples or player details.
const INTERVAL=15*60*1000;
class PublishingStatus {
 constructor(db){this.db=db;}
 async init(){await this.db.prepare('CREATE TABLE IF NOT EXISTS publisher_status (id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL)').bind().run();}
 async read(){await this.init();const rows=await this.db.prepare('SELECT state FROM publisher_status WHERE id=1').all();return rows.results?.[0]?JSON.parse(rows.results[0].state):{};}
 async write(state){await this.init();await this.db.prepare('INSERT INTO publisher_status(id,state) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET state=excluded.state').bind(JSON.stringify(state)).run();}
 async start(now){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,result:'RUNNING',error:null});}
 async finish(now,result){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,lastSuccessAt:now,result:result.changed?'COMMITTED':'UNCHANGED',error:null,publishedSamples:result.samples,...(result.changed?{lastCommitAt:now,commitSha:result.commitSha??null,commitUrl:result.commitUrl??null}:{})});}
 async fail(now,error){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,result:'FAILED',error:/HTTP \d{3}/.exec(error.message)?.[0]??'Publisher unavailable; check Worker logs and GitHub configuration'});}
 async public(now){const state=await this.read();const rows=await this.db.prepare('SELECT count(*) AS storedSamples, max(received_at) AS lastUploadAt, sum(CASE WHEN received_at>? THEN 1 ELSE 0 END) AS pendingSamples FROM samples WHERE completed_at>=?').bind(state.lastSuccessAt??0,now-7*86400000).all();return {...state,...rows.results?.[0],pendingSamples:rows.results?.[0]?.pendingSamples??0,checkedAt:now,intervalSeconds:INTERVAL/1000,nextScheduledAt:(Math.floor(now/INTERVAL)+1)*INTERVAL};}
}

// Fixed keyless Hypixel resources, cached centrally; expired quotes cannot authorize calculations.
async function publicMarket(request,{fetchImpl=fetch,cache=globalThis.caches?.default,now=Date.now()}={}) {
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

const json=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
// Independent lists: a malformed tester list must not disable the original owner list.
function approvedHashes(value) {
 if(value===undefined || value===null)return [];
 try {
  if(typeof value!=='string' || value.length>70000)return null;
  const hashes=JSON.parse(value);
  if(!Array.isArray(hashes)||hashes.length>1000||hashes.some(h=>typeof h!=='string'||!/^[a-f0-9]{64}$/.test(h)))return null;
  return hashes;
 }catch{return null;}
}
export class Store {
 constructor(db){this.db=db;}
 async accept(contributor,samples,now) {
  const day=Math.floor(now/DAY)*DAY;
  await this.db.batch(samples.map(s=>this.db.prepare(`INSERT OR IGNORE INTO samples(contributor,id,completed_at,received_at,sample)
   SELECT ?,?,?,?,? WHERE (SELECT count(*) FROM samples WHERE contributor=? AND received_at>=?)<500`)
   .bind(contributor,s.id,s.completedAt,now,JSON.stringify(s),contributor,day)));
  const rows=await this.db.batch(samples.map(s=>this.db.prepare('SELECT id FROM samples WHERE contributor=? AND id=?').bind(contributor,s.id)));
  return rows.flatMap(r=>r.results??[]).map(r=>r.id);
 }
 async dataset(now) {
  await this.db.prepare('DELETE FROM samples WHERE completed_at<?').bind(now-7*DAY).run();
  const result=await this.db.prepare('SELECT contributor,sample FROM samples WHERE received_at<=? ORDER BY completed_at DESC,contributor,id LIMIT 10000').bind(now).all();
  const counts=new Map(),samples=[];
  for(const r of result.results??[]) {
   const s=JSON.parse(r.sample),key=`${r.contributor}:${s.engine}:${s.inputId}:${s.outputId}`;
   if((counts.get(key)??0)>=10)continue;
   counts.set(key,(counts.get(key)??0)+1);samples.push({contributor:r.contributor,...s});
   if(samples.length===2000)break;
  }
  return validateDataset({protocol:COMMUNITY_PROTOCOL,generatedAt:now,samples},now);
 }
}
export async function handleRequest(request,env,now=Date.now()) {
 const path=new URL(request.url).pathname;
 if(request.method==='GET'&&['/v1/market','/v1/items'].includes(path))return publicMarket(request,{now});
 if(request.method==='GET'&&path==='/v1/publishing-status') {
  if(!env.DB)return json(503,{error:'Publishing status storage unavailable'});
  try{return json(200,{protocol:'goofy-publishing-status/1',repository:env.GITHUB_REPOSITORY,branch:DATA_BRANCH,configured:Boolean(env.GITHUB_TOKEN&&repositoryValid(env.GITHUB_REPOSITORY)),...await new PublishingStatus(env.DB).public(now)});}
  catch{return json(503,{error:'Publishing status unavailable; check the Worker database binding'});}
 }
 if(request.method==='GET'&&path==='/v1/profiles')return handleProfileLookup(request,env,now);
 if(['GET','HEAD'].includes(request.method)&&path==='/')return Response.redirect(new URL('/calculator/',request.url),302);
 if(['GET','HEAD'].includes(request.method)&&path==='/calculator')return Response.redirect(new URL('/calculator/',request.url),302);
 if(['GET','HEAD'].includes(request.method)&&path.startsWith('/calculator/')) {
  if(!env.ASSETS)return json(503,{error:'Public calculator assets have not been deployed'});
  const url=new URL(request.url),relative=path.slice('/calculator'.length);
  // Serve the SPA shell for page routes; assets/data keep exact paths and 404s.
  // Cloudflare canonicalizes /index.html to /. Fetch the asset root directly
  // so that redirect cannot escape to the Worker's own /calculator/ redirect.
  url.pathname=relative.split('/').at(-1).includes('.')?relative:'/';
  const response=await env.ASSETS.fetch(new Request(url,request));
  const headers=new Headers(response.headers);
  headers.set('X-Content-Type-Options','nosniff');headers.set('Referrer-Policy','no-referrer');
  headers.set('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' https://api.hypixel.net https://goofy-gameplay-collector.abdyzam50.workers.dev; worker-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
  if(headers.get('Content-Type')?.includes('text/html'))headers.set('Cache-Control','no-cache');
  return new Response(response.body,{status:response.status,headers});
 }
 if(request.method==='GET'&&path==='/health')return json(200,{protocol:COMMUNITY_PROTOCOL,ready:Boolean(env.DB&&env.GITHUB_TOKEN&&[env.CONTRIBUTOR_HASHES,env.CONTRIBUTOR_HASHES_EXTRA].some(value=>value&&approvedHashes(value)!==null)),additionalContributorKeysSupported:true,repository:env.GITHUB_REPOSITORY,branch:DATA_BRANCH});
 if(request.method!=='POST'||path!=='/v1/gameplay')return json(404,{error:'Unknown endpoint'});
 const token=request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{32,128})$/)?.[1];
 if(!token)return json(401,{error:'Contributor token required'});
 const registries=[approvedHashes(env.CONTRIBUTOR_HASHES),approvedHashes(env.CONTRIBUTOR_HASHES_EXTRA)];
 const contributor=await hash(token);
 if(!registries.some(list=>list?.includes(contributor)))return registries.some(list=>list===null)?json(503,{error:'Contributor registry unavailable'}):json(403,{error:'Contributor is not enrolled'});
 if(request.headers.get('Content-Type')!=='application/json')return json(400,{error:'JSON required'});
 try {
  const reader=request.body?.getReader();if(!reader)throw new Error('Empty body');let length=0,parts=[];
  while(true){const r=await reader.read();if(r.done)break;length+=r.value.length;if(length>100000){await reader.cancel();return json(413,{error:'Submission too large'});}parts.push(r.value);}
  const bytes=new Uint8Array(length);let at=0;for(const p of parts){bytes.set(p,at);at+=p.length;}
  const samples=validateSubmission(JSON.parse(new TextDecoder().decode(bytes)),now);
  const acknowledged=await new Store(env.DB).accept(contributor,samples,now);
  return json(200,{protocol:COMMUNITY_PROTOCOL,acknowledged,limited:acknowledged.length<samples.length});
 }catch(error){return json(400,{error:error.message==='Invalid shared gameplay sample'?error.message:'Submission could not be validated or stored'});}
}
function base64(text){const bytes=new TextEncoder().encode(text);let raw='';for(let i=0;i<bytes.length;i+=32768)raw+=String.fromCharCode(...bytes.subarray(i,i+32768));return btoa(raw);}
async function publishDataset(env,{now=Date.now(),fetchImpl=fetch}={}) {
 if(!env.GITHUB_TOKEN||!repositoryValid(env.GITHUB_REPOSITORY))throw new Error('GitHub publisher is not configured');
 const dataset=await new Store(env.DB).dataset(now),root=`https://api.github.com/repos/${env.GITHUB_REPOSITORY}`;
 const headers={'Authorization':`Bearer ${env.GITHUB_TOKEN}`,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'GoofyAddons-gameplay-collector','Content-Type':'application/json'};
 const api=async(path,method='GET',body)=>{
  const r=await fetchImpl(root+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  if(!r.ok&&r.status!==404)throw new Error(`GitHub publisher returned HTTP ${r.status}`);
  return {status:r.status,data:r.status===404?null:await r.json()};
 };
 const ref=await api(`/git/ref/heads/${DATA_BRANCH}`);
 if(ref.status===404) {
  const repo=await api('');const base=await api(`/git/ref/heads/${encodeURIComponent(repo.data.default_branch)}`);
  await api('/git/refs','POST',{ref:`refs/heads/${DATA_BRANCH}`,sha:base.data.object.sha});
 }
 const path='/contents/community-history.json';const previous=await api(`${path}?ref=${DATA_BRANCH}`);
 if(previous.status===200) {
  const decoded=JSON.parse(atob(previous.data.content.replace(/\s/g,'')));
  if(decoded.generatedAt>=now-6*DAY&&JSON.stringify(decoded.samples)===JSON.stringify(dataset.samples))return {changed:false,samples:dataset.samples.length};
 }
 const result=await api(path,'PUT',{message:'Update shared gameplay evidence',branch:DATA_BRANCH,content:base64(JSON.stringify(dataset)),...(previous.data?.sha?{sha:previous.data.sha}:{})});
 if(result.status===404)throw new Error('GitHub publisher returned HTTP 404');
 return {changed:true,samples:dataset.samples.length,commitSha:result.data?.commit?.sha??null,commitUrl:result.data?.commit?.html_url??null};
}
export async function publish(env,options={}) {
 const now=options.now??Date.now(),status=new PublishingStatus(env.DB);
 await status.start(now);
 try{const result=await publishDataset(env,{...options,now});await status.finish(now,result);return result;}
 catch(error){await status.fail(now,error);throw error;}
}
export default {
 fetch(request,env){return handleRequest(request,env);},
 async scheduled(_event,env,context){context.waitUntil(publish(env).catch(error=>{console.error(error.message);throw error;}));}
};
