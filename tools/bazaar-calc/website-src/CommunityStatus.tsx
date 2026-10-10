import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

type Status = { protocol:string;repository:string;branch:string;configured:boolean;result?:string;lastAttemptAt?:number;lastSuccessAt?:number;lastCommitAt?:number;commitSha?:string;commitUrl?:string;error?:string;publishedSamples?:number;storedSamples:number;pendingSamples:number;lastUploadAt?:number;nextScheduledAt:number;checkedAt:number };
const time=(value?:number)=>value?new Date(value).toLocaleString():'Not recorded';
export function CommunityStatus(){
 const [now,setNow]=useState(Date.now());
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
 const query=useQuery({queryKey:['community-publishing'],queryFn:async()=>{
  const r=await fetch('/v1/publishing-status',{cache:'no-store',signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error('Publishing status unavailable. The collector may need the latest deployment.');
  const data=await r.json() as Status;if(data.protocol!=='goofy-publishing-status/1')throw new Error('Publishing status is not supported by this deployment.');return data;
 },refetchInterval:30000,retry:1,refetchOnWindowFocus:true});
 const ah=useQuery({queryKey:['ah-collection-status'],queryFn:async()=>{const r=await fetch('/v1/crafts/status',{cache:'no-store'});if(!r.ok)throw new Error('AH collector status unavailable');return r.json();},refetchInterval:30000});
 const s=query.data;
 const repository=s&&/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(s.repository)?`https://github.com/${s.repository}`:null;
 const commit=repository&&s?.commitSha&&/^[a-f0-9]{40}$/.test(s.commitSha)?`${repository}/commit/${s.commitSha}`:null;
 const remaining=s?Math.max(0,Math.ceil((s.nextScheduledAt-now)/1000)):0;
 return <section aria-label="Community data publishing">
  <h2>Gameplay data → GitHub</h2>
  <p>Approved mods upload anonymized trade evidence to Cloudflare D1. Every 15 minutes, the collector publishes a bounded seven-day dataset to <code>gameplay-data/community-history.json</code>. Unchanged evidence does not create another commit. Account details, chat, keys and exact profit receipts stay private.</p>
  {query.error&&<div className="note" role="alert">{(query.error as Error).message}{s?' Last known status is shown below.':''}</div>}
  {!s&&!query.error&&<p>Checking the collector…</p>}
  {s&&<>
   <div className="grid cols-3">
    <div className="card tile"><div className="label">Last publish attempt</div><div className="value">{s.result??'No recorded attempt'}</div><div className="sub">{time(s.lastAttemptAt)} · {s.configured?'Publisher configured':'Publisher configuration missing'}</div>{s.error&&<p role="alert">{s.error}. Check the Worker logs, repository access and GitHub token permissions.</p>}</div>
    <div className="card tile"><div className="label">Last successful GitHub commit</div><div className="value">{s.lastCommitAt?time(s.lastCommitAt):'Not recorded'}</div><div className="sub">{commit?<a href={commit} target="_blank" rel="noreferrer">View commit {s.commitSha!.slice(0,7)}</a>:'Commit details appear after this publisher version completes a changed upload.'}<br/>Last successful check: {time(s.lastSuccessAt)}</div></div>
    <div className="card tile"><div className="label">Next scheduled check</div><div className="value">{Math.floor(remaining/60)}m {remaining%60}s</div><div className="sub">{time(s.nextScheduledAt)} · schedule estimate; execution can be delayed</div></div>
    <div className="card tile"><div className="label">New samples since successful publish</div><div className="value">{s.pendingSamples.toLocaleString()}</div><div className="sub">Last accepted upload: {time(s.lastUploadAt)}</div></div>
    <div className="card tile"><div className="label">Stored / last published samples</div><div className="value">{s.storedSamples.toLocaleString()} / {s.publishedSamples??'—'}</div><div className="sub">Seven-day retention; route and contributor caps apply to publication</div></div>
    <div className="card tile"><div className="label">Status checked</div><div className="value">{time(s.checkedAt)}</div><div className="sub">Refreshes every 30 seconds{repository&&<><br/><a href={`${repository}/blob/${s.branch}/community-history.json`} target="_blank" rel="noreferrer">Open shared dataset</a></>}</div></div>
   </div>
   {s.lastAttemptAt&&now-s.lastAttemptAt>30*60000&&<p className="note" role="alert">The scheduled publisher has not reported an attempt for over 30 minutes. Check its Cloudflare trigger and logs.</p>}
  </>}
  <h2>Persistent Auction House market history</h2>
  <p>Collection runs every 15 minutes without visitors. D1 retains 30 days of daily price/sales summaries; compact aggregates refresh about hourly and publish to gameplay-data/ah-market-history.json. BIN snapshots and provider sales activity are not confirmed player profits or guaranteed selling times.</p>
  {ah.error&&<p role="alert">{ah.error.message}</p>}
  {ah.data&&<div className="grid cols-3">
   <div className="card tile"><div className="label">Last AH collection</div><div className="value">{ah.data.collection.result??'Waiting for first scheduled run'}</div><div className="sub">{time(ah.data.collection.lastAttemptAt)} · {ah.data.collection.quotedItems??0} BIN quotes</div>{ah.data.collection.error&&<p role="alert">{ah.data.collection.error}</p>}</div>
   <div className="card tile"><div className="label">Retained AH evidence</div><div className="value">{ah.data.items} items</div><div className="sub">{ah.data.observations} price observations · 30-day window</div></div>
   <div className="card tile"><div className="label">AH GitHub publication</div><div className="value">{ah.data.publishing.result??'Waiting'}</div><div className="sub">Last commit: {time(ah.data.publishing.lastCommitAt)}</div>{ah.data.publishing.error&&<p role="alert">{ah.data.publishing.error}</p>}</div>
  </div>}
  <p className="small muted">GitHub gameplay commits feed the mods’ shared calibration. The historical market charts below use the bundled site snapshot; publishing gameplay evidence does not rebuild those files. Live Bazaar quotes and rankings refresh separately, about every 20 seconds while this page is visible. Return to a background tab to resume its live updates.</p>
 </section>;
}
