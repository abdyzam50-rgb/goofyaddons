// Read-only account lookup. Hypixel profile fields: PublicAPI v2 SkyBlock profiles;
// progression thresholds: MIT NotEnoughUpdates-REPO constants/leveling.json.
// Nothing returned here is written to the shared gameplay dataset or GitHub.
import { LEVELS } from './profile-levels.mjs';
const profileJson=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','Access-Control-Allow-Origin':'*'}});
const present=n=>typeof n==='number'&&Number.isFinite(n)&&n>=0;
const clean=s=>typeof s==='string'?s.replace(/§./g,'').slice(0,80):'';
function xpLevel(xp,steps,cap=steps.length) {
 if(!present(xp))return null;
 let level=0,left=xp;
 for(const cost of steps.slice(0,cap)){if(left<cost)break;left-=cost;level++;}
 return level;
}
export function summarizeProfiles(data,uuid,resources,now=Date.now()) {
 if(data?.success!==true||!Array.isArray(data.profiles))throw new Error('Profile data unavailable');
 const collections=new Map();
 for(const category of Object.values(resources?.collections??{}))for(const [id,item] of Object.entries(category.items??{}))collections.set(id,clean(item.name));
 return data.profiles.slice(0,10).flatMap(p=>{
  const member=p.members?.[uuid];if(!member)return [];
  const unknown=[],skills={},slayers={},tiers={},collectionIds={},reputation={};
  const experience=member.player_data?.experience;
  for(const name of ['farming','mining','combat','foraging','fishing','enchanting','alchemy','carpentry','taming']) {
   const xp=experience?.[`SKILL_${name.toUpperCase()}`]??member[`experience_skill_${name}`];
   const level=xpLevel(xp,LEVELS.leveling_xp,LEVELS.leveling_caps[name]);
   if(level===null)unknown.push(name);else skills[name[0].toUpperCase()+name.slice(1)]=level;
  }
  const hotm=xpLevel(member.mining_core?.experience,LEVELS.HOTM);
  if(hotm===null)unknown.push('Heart of the Mountain');
  const quick=member.mining_core?.nodes?.forge_time;
  if(!present(quick))unknown.push('Quick Forge');
  const unlocked=member.player_data?.unlocked_coll_tiers??member.unlocked_coll_tiers;
  if(Array.isArray(unlocked)) {
   for(const value of unlocked) {
    const m=typeof value==='string'&&/^(.+)_(\d+)$/.exec(value),name=m&&collections.get(m[1]);
    if(m)collectionIds[m[1]]=Math.max(collectionIds[m[1]]??0,Number(m[2]));
    if(name)tiers[name]=Math.max(tiers[name]??0,Number(m[2]));
   }
  }else unknown.push('collections');
  if(member.slayer_bosses)for(const [id,boss] of Object.entries(member.slayer_bosses)) {
   const table=LEVELS.slayer_xp[id];
   if(!table)continue;
   const name=id[0].toUpperCase()+id.slice(1);
   if(!present(boss?.xp)){unknown.push(`${name} Slayer`);continue;}
   let level=table.filter(n=>boss.xp>=n).length;
   if(boss.claimed_levels&&typeof boss.claimed_levels==='object') {
    const claimed=Object.entries(boss.claimed_levels).filter(([key,value])=>/^level_[1-9]$/.test(key)&&value===true).map(([key])=>Number(key.slice(6)));
    level=Math.min(level,Math.max(0,...claimed));
   }
   slayers[name]=level;
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
   stats:{hotmTier:hotm??0,quickForgeLevel:present(quick)?Math.min(20,quick):0,enchantingLevel:skills.Enchanting??0,skills,collections:tiers,collectionIds,slayers,reputation,
    xpLevels:0,ignoreRequirements:false,coleMoltenForge:false,quadTaxes:false,npcShoppingSpree:false}}];
 });
}
class AccountLookupError extends Error {
 constructor(message,status){super(message);this.status=status;}
}
export async function lookupProfiles(username,key,fetcher=fetch,now=Date.now()) {
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
export async function handleProfileLookup(request,env,now=Date.now(),fetcher=fetch) {
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
