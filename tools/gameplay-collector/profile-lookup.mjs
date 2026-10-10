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
  // Current skill-tree layout, with legacy fields for profiles not yet migrated.
  // Verified against SkyCrypt-Backend stats/mining.go and SkyCrypt-Types profile.go.
  const miningXp=member.skill_tree?.experience?.mining??member.mining_core?.experience;
  const hotm=xpLevel(miningXp,LEVELS.HOTM);
  if(hotm===null)unknown.push('Heart of the Mountain');
  const dungeon=member.dungeons?.dungeon_types?.catacombs;
  const catacombsLevel=xpLevel(dungeon?.experience,LEVELS.catacombs,50);
  if(catacombsLevel===null)unknown.push('Catacombs');
  const dungeonCompletions={};
  for(const [type,label] of [['catacombs','Catacombs'],['master_catacombs','Master Catacombs']]) {
   const completed=member.dungeons?.dungeon_types?.[type]?.tier_completions;
   if(!completed||typeof completed!=='object'||Array.isArray(completed)){unknown.push(`${label} floor completions`);continue;}
   for(let floor=0;floor<=7;floor++) {
    const count=completed[String(floor)];
    // Omitted counters in a published completion map mean no clears; XP never proves a clear.
    if(count===undefined)dungeonCompletions[`${label} Floor ${floor}`]=0;
    else if(Number.isSafeInteger(count)&&count>=0)dungeonCompletions[`${label} Floor ${floor}`]=count>0?1:0;
   }
  }
  const garden=member.garden_player_data;
  const analyzed=garden&&typeof garden==='object'&&!Array.isArray(garden)
   ?garden.analyzed_greenhouse_crops===undefined?[]:garden.analyzed_greenhouse_crops:undefined;
  const inspectedMutations=Array.isArray(analyzed)&&analyzed.length<=4096&&analyzed.every(id=>typeof id==='string'&&/^[a-z0-9_]{1,80}$/i.test(id))
   ?[...new Set(analyzed.map(id=>id.toUpperCase()))]:null;
  if(inspectedMutations===null)unknown.push('Garden mutation inspections');
  const tutorial=member.objectives?.tutorial;
  let cropAnalyzerMilestone=null;
  if(Array.isArray(tutorial)&&tutorial.length<=10000&&tutorial.every(id=>typeof id==='string')) {
   cropAnalyzerMilestone=Math.max(0,...tutorial.map(id=>/^dna_analysis_rewardskyblock_xp_([1-6])$/.exec(id)).filter(Boolean).map(match=>Number(match[1])));
  }else unknown.push('Crop Analyzer Milestone');
  const modernNodes=member.skill_tree?.nodes?.mining;
  const legacyNodes=member.mining_core?.nodes;
  const nodes=modernNodes??legacyNodes;
  const quick=modernNodes!=null?modernNodes.quick_forge:legacyNodes?.forge_time;
  // A published node map without Quick Forge means this perk has not been purchased.
  const quickLevel=quick===undefined&&nodes&&typeof nodes==='object'&&!Array.isArray(nodes)?0:quick;
  if(!Number.isInteger(quickLevel)||quickLevel<0||quickLevel>20)unknown.push('Quick Forge');
  const unlocked=member.player_data?.unlocked_coll_tiers??member.unlocked_coll_tiers;
  if(Array.isArray(unlocked)) {
   for(const value of unlocked) {
    const m=typeof value==='string'&&/^(.+)_(\d+)$/.exec(value),name=m&&collections.get(m[1]);
    if(m)collectionIds[m[1]]=Math.max(collectionIds[m[1]]??0,Number(m[2]));
    if(name)tiers[name]=Math.max(tiers[name]??0,Number(m[2]));
   }
  }else unknown.push('collections');
  const slayerBosses=member.slayer?.slayer_bosses??member.slayer_bosses;
  if(slayerBosses&&typeof slayerBosses==='object'&&!Array.isArray(slayerBosses))for(const [id,table] of Object.entries(LEVELS.slayer_xp)) {
   const raw=slayerBosses[id];
   const boss=raw===undefined?{}:raw;
   const name=id[0].toUpperCase()+id.slice(1);
   const xp=boss&&typeof boss==='object'&&!Array.isArray(boss)?boss.xp===undefined?0:boss.xp:undefined;
   if(!present(xp)){unknown.push(`${name} Slayer`);continue;}
   let level=table.filter(n=>xp>=n).length;
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
  // Vanilla XP is not a profile unlock and is intentionally omitted from the account warnings.
  const purse=member.currencies?.coin_purse??member.coin_purse;
  return [{id:clean(p.profile_id),name:clean(p.cute_name)||'Profile',selected:p.selected===true,gameMode:clean(p.game_mode)||'normal',
   purse:present(purse)&&purse<=1e13?purse:null,fetchedAt:now,unknown,
   stats:{catacombsLevel,dungeonCompletions,inspectedMutations,cropAnalyzerMilestone,hotmTier:hotm??0,quickForgeLevel:Number.isInteger(quickLevel)&&quickLevel>=0&&quickLevel<=20?quickLevel:0,enchantingLevel:skills.Enchanting??0,skills,collections:tiers,collectionIds,slayers,reputation,
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
 return {protocol:'goofy-profile/1',parserVersion:'2026-10-10-unlock-completeness',username:clean(player.name),fetchedAt:now,profiles:summarizeProfiles(data,player.id,resources,now)};
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
