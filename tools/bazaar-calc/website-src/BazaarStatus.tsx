import {useQuery} from '@tanstack/react-query';
import {useEffect,useState} from 'react';
import type {Manifest} from '../static/backend';
import {ago,num,utc} from '../lib';
export function BazaarStatus({manifest}:{manifest?:Manifest}){
 const [now,setNow]=useState(Date.now());
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
 const query=useQuery({queryKey:['craft-live-bazaar'],queryFn:async()=>{
  const r=await fetch('/v1/market',{cache:'no-store',signal:AbortSignal.timeout(15000)});
  const data=await r.json();if(!r.ok)throw new Error(data.error??'Live Bazaar feed unavailable');
  if(data.success!==true||!Number.isFinite(data.lastUpdated)||!data.products||typeof data.products!=='object'||Array.isArray(data.products))throw new Error('Invalid Bazaar snapshot');return data;
 },refetchInterval:20000,retry:1});
 const data=query.data,age=data?now-data.lastUpdated:null;
 const fresh=age!==null&&age>=-5000&&age<=60000;
 const usable=!!data&&fresh&&!query.error;
 const status=query.error?'UNAVAILABLE':!data?'CHECKING':fresh?'LIVE':'STALE';
 return <section aria-label="Bazaar market data">
  <h2>Bazaar market data</h2>
  <p>Live Hypixel Bazaar snapshots include current buy/sell order depth and rolling weekly activity. The Worker caches quotes for 20 seconds; visible pages refresh about every 20 seconds. Quotes older than 60 seconds cannot generate recommendations. Scheduled AH scans also read Bazaar prices every 15 minutes without visitors, but do not save a public Bazaar price history.</p>
  <div className="grid cols-3">
   <div className="card tile"><div className="label">Live Bazaar feed</div><div className="value">{status}</div><div className="sub">{data?`${utc(data.lastUpdated)} · ${Math.max(0,Math.floor((age??0)/1000))}s old`:'Waiting for a snapshot'}<br/>Source: Hypixel Public API → Worker → browser</div>{query.error&&<p role="alert">{query.error.message}. Recommendations wait for fresh prices.</p>}</div>
   <div className="card tile"><div className="label">Live Bazaar coverage</div><div className="value">{usable?`${num(Object.keys(data.products).length)} products`:'—'}</div><div className="sub">{usable?'Current order books and weekly volume counters; weekly activity is not confirmed profit.':'Coverage appears when fresh prices are available.'}<br/><a href="/v1/market">View current Bazaar JSON</a></div></div>
   <div className="card tile"><div className="label">Public Bazaar history storage</div><div className="value">Bundled snapshot</div><div className="sub">{manifest?<>History through {utc(manifest.asOf)} ({ago(manifest.asOf)}).<br/>Snapshot built {utc(manifest.builtAt)}.<br/>Recipes: {manifest.recipesVersion??'Not recorded'}.</>:'Loading bundled history metadata…'}<br/>No continuous public Bazaar history export to D1 or GitHub is currently enabled.</div></div>
  </div>
  <p className="small muted">The charts and coverage below describe bundled history, not the current quote feed. Rebuilding and deploying the website updates that snapshot. Shared gameplay commits calibrate the mods’ predictions; neither gameplay nor AH commits rebuild Bazaar charts. This page does not measure actual player profits. Background tabs pause refreshes; returning resumes updates.</p>
 </section>;
}
