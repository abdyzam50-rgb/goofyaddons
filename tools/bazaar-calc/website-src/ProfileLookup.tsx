// Optional public-profile import. It changes calculator settings, never macro settings or orders.
import { useState } from 'react';
import { DEFAULT_PROFILE, type Profile } from '@bc/shared';
import { useApp } from '../state';
import { coins, num } from '../lib';
type AccountProfile={id:string;name:string;selected:boolean;gameMode:string;purse:number|null;fetchedAt:number;unknown:string[];stats:Profile};
export function ProfileLookup() {
 const {setSettings,setProfile}=useApp();
 const [username,setUsername]=useState(''),[profiles,setProfiles]=useState<AccountProfile[]>([]),[selection,setSelection]=useState('');
 const [message,setMessage]=useState('Enter a username to import a purse and published unlocks, or use Settings & unlocks to enter them yourself.');
 const [busy,setBusy]=useState(false),[applied,setApplied]=useState<AccountProfile|null>(null);
 const importProfile=(p:AccountProfile)=>{
  setProfile({...DEFAULT_PROFILE,...p.stats,ignoreRequirements:false});
  if(['ironman','stranded','bingo'].includes(p.gameMode.toLowerCase())){
   setSettings({coins:0});setApplied(p);setMessage(`${p.name} is a ${p.gameMode} profile. Bazaar/Auction House flips are unavailable; its trading budget is set to zero.`);return;
  }
  if(p.purse!==null)setSettings({coins:p.purse});
  setApplied(p);
  setMessage(p.purse===null?'Purse is unavailable from the API. Enter your spendable coins in Settings & unlocks.':`Using ${p.name}’s purse: ${num(p.purse)} coins. Bank balance and existing orders are excluded.`);
 };
 const lookup=async()=>{
  if(!/^[A-Za-z0-9_]{1,16}$/.test(username)){setMessage('Enter a valid Minecraft username.');return;}
  setBusy(true);setProfiles([]);setApplied(null);
  try {
   const local=['127.0.0.1','localhost'].includes(location.hostname);
   const endpoint=local?'/v1/profiles':'https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles';
   const r=await fetch(`${endpoint}?username=${encodeURIComponent(username)}`,{signal:AbortSignal.timeout(20000)});
   const body=await r.json();if(!r.ok)throw new Error(body.error||'Profile lookup unavailable');
   if(body.protocol!=='goofy-profile/1'||!Array.isArray(body.profiles))throw new Error('Unexpected profile response');
   const found=body.profiles as AccountProfile[];setProfiles(found);
   if(!found.length){setMessage('No accessible SkyBlock profiles found. Use a manual budget and unlocks.');return;}
   const active=found.find(p=>p.selected)??found[0]!;setSelection(active.id);importProfile(active);
  }catch(e){setMessage(`${(e as Error).message}. You can still use a manual budget and unlocks.`);}
  finally{setBusy(false);}
 };
 return <section className="card stack" aria-label="Account calculator" style={{marginBottom:20,padding:16}}>
  <div className="spread"><strong>Calculator for your account · no macro needed</strong>{['127.0.0.1','localhost'].includes(location.hostname)&&<a href="/">Live trading dashboard</a>}</div>
  <form onSubmit={e=>{e.preventDefault();void lookup();}} className="spread" style={{gap:12,flexWrap:'wrap'}}>
   <label className="field" style={{flex:'1 1 180px'}}><span>Minecraft username</span><input aria-label="Minecraft username" maxLength={16} autoComplete="off" value={username} onChange={e=>setUsername(e.target.value)} placeholder="Your username" /></label>
   <button disabled={busy} type="submit">{busy?'Looking up…':'Load / refresh profile'}</button>
   {!!profiles.length&&<label className="field" style={{flex:'1 1 180px'}}><span>SkyBlock profile</span><select aria-label="SkyBlock profile" value={selection} onChange={e=>{setSelection(e.target.value);const p=profiles.find(p=>p.id===e.target.value);if(p)importProfile(p);}}>{profiles.map(p=><option key={p.id} value={p.id}>{p.name} · {p.gameMode}{p.selected?' · selected':''}</option>)}</select></label>}
  </form>
  <p role="status" style={{margin:0}}>{message}</p>
  {applied&&<><p className="small muted" style={{margin:0}}>Purse {coins(applied.purse)} · Enchanting {applied.unknown.includes('enchanting')?'unknown':applied.stats.enchantingLevel} · Catacombs {applied.stats.catacombsLevel??'unknown'} · HotM {applied.unknown.includes('Heart of the Mountain')?'unknown':applied.stats.hotmTier} · fetched {new Date(applied.fetchedAt).toLocaleTimeString()}. Refresh after trading; this is an API snapshot.</p>
   <p className="small muted" style={{margin:0}}>Slayers: {applied.unknown.includes('slayers')?'unpublished':Object.entries(applied.stats.slayers).map(([name,level])=>`${name} ${level}`).join(' · ')||'unpublished'}. Published claimed rewards limit recipe unlocks when available.</p>
   <p className="small muted" style={{margin:0}}>Dungeon floor clears are checked separately from Catacombs XP. Analyzed mutations: {applied.stats.inspectedMutations?.length??'unknown'} · Crop Analyzer milestone: {applied.stats.cropAnalyzerMilestone??'unknown'}. Unpublished unlocks remain blocked.</p>
   {!!applied.unknown.length&&<p className="small" style={{margin:0}}>Unpublished or unavailable: {applied.unknown.join(', ')}. Required unlocks that cannot be confirmed are excluded conservatively. Confirm them in Settings & unlocks to include those routes.</p>}</>}
  <p className="small muted" style={{margin:0}}>Calculations use your current calculator budget and live prices. Estimates are not guaranteed profit. Imported profiles stay out of the shared gameplay dataset. This page never starts trading.</p>
 </section>;
}
