import {useQuery} from '@tanstack/react-query';
import {useEffect,useMemo,useState} from 'react';
import {parseCraftText,tradeRequirements,unmet} from '@bc/shared';
import {useApp} from '../state';
import {coins,num} from '../lib';
import {fresh,planCrafts,type Catalog} from './craft-plan.mjs';
const get=async(path:string)=>{const r=await fetch(path,{cache:'no-store',signal:AbortSignal.timeout(20000)});const body=await r.json();if(!r.ok)throw new Error(body.error??'Service unavailable');return body;};
export function CraftPlanner({search:sharedSearch,onSearchChange}:{search?:string;onSearchChange?:(value:string)=>void}={}){
 const {settings,profile}=useApp(),[now,setNow]=useState(Date.now()),[venue,setVenue]=useState('ALL'),[localSearch,setLocalSearch]=useState(''),[onlyEligible,setOnlyEligible]=useState(false),[minProfit,setMinProfit]=useState(10000),[maxBatches,setMaxBatches]=useState(16),[shown,setShown]=useState(100);
 const search=sharedSearch??localSearch,setSearch=onSearchChange??setLocalSearch;
 useEffect(()=>{const id=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(id);},[]);
 const catalog=useQuery<Catalog>({queryKey:['production-catalog'],queryFn:()=>get('/calculator/data/production-recipes.json'),staleTime:Infinity});
 const market=useQuery({queryKey:['craft-live-bazaar'],queryFn:()=>get('/v1/market'),refetchInterval:20000});
 const focus=useMemo(()=>search.trim().length>=3?catalog.data?.recipes.find(r=>r.kind==='CRAFT'&&`${catalog.data?.names[r.outputId]??''} ${r.outputId}`.toLowerCase().includes(search.trim().toLowerCase()))?.outputId??'':'',[search,catalog.data]);
 const ah=useQuery({queryKey:['craft-live-ah',focus],queryFn:async()=>{const b=await get(`/v1/crafts/market${focus?`?item=${encodeURIComponent(focus)}`:''}`);if(b.protocol!=='goofy-craft-market/1'||!Array.isArray(b.rows))throw new Error('Unsupported craft discovery response');return b;},refetchInterval:20000});
 const history=useQuery({queryKey:['craft-ah-history'],queryFn:()=>get('/v1/crafts/history'),refetchInterval:60000,retry:1});
 const requirements=useMemo(()=>Object.fromEntries((catalog.data?.recipes??[]).map(r=>{
  const reqs=[...parseCraftText(r.requirement),...[r.outputId,...Object.keys(r.ingredients)].flatMap(tradeRequirements)];
  const blocked=profile.ignoreRequirements?reqs.map(x=>`Confirm requirement: ${x.text}`):unmet(reqs,profile).map(x=>`Required or unobserved: ${x.text}`);
  return [r.key,blocked];
 })),[catalog.data,profile]);
 const rows=useMemo(()=>catalog.data?planCrafts({catalog:catalog.data,market:market.data,ah:ah.data,history:history.data,now,budget:Math.max(0,settings.coins),minProfit,maxBatches,tax:(settings.bazaarFlipperLevel===2?1:settings.bazaarFlipperLevel===1?1.125:1.25)*(profile.quadTaxes?4:1),requirements}):[],[catalog.data,market.data,ah.data,history.data,now,settings,profile.quadTaxes,minProfit,maxBatches,requirements]);
 const visible=rows.filter(r=>(venue==='ALL'||r.venue===venue)&&(!onlyEligible||r.eligible)&&`${r.name} ${r.output}`.toLowerCase().includes(search.toLowerCase()));
 const names=catalog.data?.names??{},at=market.data?.lastUpdated;
 return <section className="card pad stack" aria-label="Live craft production plan">
  <div className="spread" style={{flexWrap:'wrap',gap:8}}><div><span className="eyebrow">Craft production</span><h2>Live craft plans</h2></div><button onClick={()=>{void market.refetch();void ah.refetch();}}>Refresh craft prices</button></div>
  <p>Buy base inputs → prepare ingredients → craft whole batches → sell on Bazaar or list a BIN. Uses the mod’s verified recipe catalog, your calculator budget, and imported or manually confirmed unlocks.</p>
  <p role="status">Spendable budget {coins(settings.coins)} · {rows.filter(r=>r.eligible).length} feasible routes · Bazaar {fresh(at,now)?`updated ${Math.max(0,Math.floor((now-at)/1000))}s ago`:'waiting for fresh prices'}. Refreshes every 20 seconds while visible.</p>
  <p className="small muted">AH history: {history.data?.rows?.length??0} observed items · scheduled collection every 15 minutes · 30-day retention · aggregate updates about hourly. Historical estimates stay research-only until prices are verified live.</p>
  {history.error&&<p role="alert">AH history: {history.error.message}. Live quotes continue independently.</p>}
  {ah.data?.storageError&&<p role="alert">{ah.data.storageError}</p>}
  {catalog.data?.coverage&&<p className="small muted">{rows.length} distinct craft outputs shown · Catalog: {num(catalog.data.recipes.filter(r=>r.kind==='CRAFT').length)} crafting routes · {num(catalog.data.coverage.sourceCraftRows)} source grids checked · {num(catalog.data.coverage.unsupportedCraftRows)} require additional identity support.</p>}
  {!!catalog.data?.unsupportedCrafts?.length&&<details style={{overflowWrap:'anywhere'}}><summary>Recipes awaiting identity support</summary><ul>{catalog.data.unsupportedCrafts.filter(r=>`${r.name} ${r.outputId}`.toLowerCase().includes(search.toLowerCase())).map(r=><li key={r.key}>{r.name} ({r.outputId}): {r.reason}</li>)}</ul></details>}
  {(market.error||catalog.error)&&<p role="alert">{(market.error??catalog.error)?.message}</p>}
  {(ah.error||ah.data?.error)&&<p role="alert">AH discovery: {ah.error?.message??ah.data.error}. Bazaar plans continue independently.</p>}
  <div className="row" style={{flexWrap:'wrap',gap:12}}>
   <label className="field grow"><span>Search crafts</span><input aria-label="Search crafts" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Name or product ID" /></label>
   <label className="field"><span>Sale market</span><select aria-label="Craft sale market" value={venue} onChange={e=>setVenue(e.target.value)}><option value="ALL">Bazaar + AH</option><option value="BAZAAR">Bazaar</option><option value="AH">Auction House</option></select></label>
   <label className="field"><span>Minimum net profit</span><input aria-label="Craft minimum profit" type="number" min={0} value={minProfit} onChange={e=>setMinProfit(Math.max(0,Number(e.target.value)||0))} /></label>
   <label className="field"><span>Maximum batches</span><input aria-label="Craft maximum batches" type="number" min={1} max={16} value={maxBatches} onChange={e=>setMaxBatches(Math.max(1,Math.min(16,Math.floor(Number(e.target.value)||1))))} /></label>
   <label className="check"><input aria-label="Feasible crafts only" type="checkbox" checked={onlyEligible} onChange={e=>setOnlyEligible(e.target.checked)} /> Feasible only</label>
  </div>
  <p className="small muted">Ranked by conservative net profit, processing effort and liquidity; the ranking score is not coins/hour. Each route uses the budget independently. AH component costs use current lowest BIN per unit; buying several identical units can cost more. Catalog items with no tradable output or missing prices remain visible with a blocking reason. Fees and price movement allowances are included. Held items, inventory space and existing positions are not observed by this public page; check them before trading. AH volume has no confirmed daily window; BIN estimates do not guarantee a buyer. The mod currently executes Bazaar crafts; AH listings need sale/expiry/claim tracking before automatic selection.</p>
  {!visible.length&&<p>No matching catalog routes. Check budget, unlocks, filters and live market availability.</p>}
  <div style={{maxWidth:'100%',overflowX:'auto'}}><table><thead><tr><th>Craft / market</th><th>Whole batch</th><th>Budget incl. fees</th><th>Estimated net</th><th>Checks / pipeline</th></tr></thead><tbody>{visible.slice(0,shown).map(r=><tr key={r.key}>
   <td><strong>{r.name}</strong><div className="small muted">{r.venue} · {r.output}{r.pricing==='HISTORY'?' · historical estimate':''}</div></td><td>{num(r.batches)} crafts → {num(r.units)} units</td><td>{coins(r.capital)}</td><td>{coins(r.profit)}</td>
   <td><div>{r.eligible?'Feasible under your budget and unlocks':r.reason}</div>{r.requirement&&<div className="small muted">{r.requirement}</div>}<details><summary>Show craft pipeline</summary><ol>
    <li>Instant buy: {Object.entries(r.purchases).map(([id,qty])=>`${num(qty)}× ${names[id]??id}`).join(', ')}.</li>
    {r.steps.map((step,i)=><li key={i}>Prepare {num(step.units)}× {names[step.output]??step.output} ({num(step.batches)} crafts).</li>)}
    <li>Craft {num(r.units)}× {r.name} ({num(r.batches)} whole batches).</li><li>{r.venue==='AH'?`List one BIN at ${coins(r.binPrice)}; proceeds arrive only after a confirmed sale and claim.`:'Instant sell into observed Bazaar bid depth.'}</li>
   </ol></details></td></tr>)}</tbody></table></div>{visible.length>shown&&<button onClick={()=>setShown(n=>n+100)}>Show 100 more crafts</button>}<p className="small muted">{Math.min(visible.length,shown)} shown / {visible.length} matching. Prices are snapshots; verify the final in-game confirmation. The research table below also explores buy orders and sell offers with different timing assumptions.</p>
 </section>;
}
