// Rebuild the full, MIT upstream calculator with a small GoofyAddons profile-import overlay.
// node build-website.mjs UPSTREAM_CHECKOUT SITE_DATA_DIRECTORY NEU_DIRECTORY
// Pass - for NEU_DIRECTORY to retain the supplied published recipe/reference snapshot.
// The checkout must have its pinned dependencies installed and shared package built first.
import {cpSync,readFileSync,writeFileSync,mkdirSync,readdirSync,rmSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {pathToFileURL,fileURLToPath} from 'node:url';
const COMMIT='85cc23d621cd7194b163abf3d55ea76b682fcc2a';
const [checkout,data,neu]=process.argv.slice(2).map(p=>p==='-'?null:resolve(p));
if(!checkout||!data||process.argv.length<5)throw new Error('Provide upstream checkout, published site data, and NEU directory');
if(execFileSync('git',['rev-parse','HEAD'],{cwd:checkout,encoding:'utf8'}).trim()!==COMMIT)throw new Error('Wrong upstream revision');
const root=fileURLToPath(new URL('.',import.meta.url)),web=join(checkout,'packages/web');
function patch(file,old,next){const p=join(checkout,file),text=readFileSync(p,'utf8');if(text.includes(next))return;if(!text.includes(old))throw new Error(`Patch mismatch: ${file}`);writeFileSync(p,text.replace(old,next));}
for(const file of ['CraftPlanner.tsx','craft-plan.mjs','craft-plan.d.mts'])cpSync(join(root,'website-src',file),join(web,'src/components',file));
patch('packages/web/src/pages/Flips.tsx','// Flip tables',"import { CraftPlanner } from '../components/CraftPlanner';\n// Flip tables");
patch('packages/web/src/pages/Flips.tsx','  return (\n    <>\n      <div className="pagehead">','  return (\n    <>\n      {kind === "craft" && <CraftPlanner />}\n      <div className="pagehead">');
cpSync(join(root,'website-src/ProfileLookup.tsx'),join(web,'src/components/ProfileLookup.tsx'));
cpSync(join(root,'website-src/CommunityStatus.tsx'),join(web,'src/components/CommunityStatus.tsx'));
patch('packages/web/src/pages/Static.tsx','import { useQuery }',"import { CommunityStatus } from '../components/CommunityStatus';\nimport { useQuery }");
patch('packages/web/src/pages/Static.tsx','Live prices come from Hypixel in your browser. History comes from contributed data, rebuilt each time a contribution is approved.','Live Bazaar prices refresh from Hypixel through the collector. Historical charts use the bundled reference snapshot. Gameplay publishing status is tracked separately below.');
patch('packages/web/src/pages/Static.tsx','      {m && q.data && <>','      <CommunityStatus />\n      {m && q.data && <>');
patch('packages/web/src/pages/Static.tsx','straight from Hypixel; updates every minute while a page is open','live Hypixel prices; refresh about every 20 seconds while visible');
patch('packages/web/src/pages/Static.tsx','rebuilt on every approved contribution and every scanner push · recipes: NEU','bundled history snapshot; changes when the website is rebuilt and deployed · recipes: NEU');
patch('packages/web/src/live.ts','"orders-book", "alerts", "paper"','"orders-book", "alerts", "paper", "outlook", "events", "events-now", "ah", "rules-bazaar"');
patch('packages/web/src/components/Live.tsx','const label = l.mode === "live"','const label = l.error ? "UNAVAILABLE" : age != null && age > 60_000 ? "STALE" : l.mode === "live"');
patch('packages/web/src/components/Live.tsx','age < 90_000','age < 60_000');
patch('packages/web/src/components/Layout.tsx','import { SettingsDrawer }',"import { ProfileLookup } from './ProfileLookup';\nimport { SettingsDrawer }");
patch('packages/web/src/components/Layout.tsx','        <Outlet />','        <ProfileLookup />\n        <Outlet />');
const backendFile=join(web,'src/static/backend.ts');
writeFileSync(backendFile,readFileSync(backendFile,'utf8').replace("fetch(['localhost','127.0.0.1'].includes(location.hostname)?'/v1/market':`${HYPIXEL}/skyblock/bazaar`,","fetch('/v1/market',"));
patch('packages/web/src/static/backend.ts','fetch(`${HYPIXEL}/skyblock/bazaar`,',"fetch('/v1/market',");
// An old quote can still be displayed as history, but must not produce recommendations.
patch('packages/web/src/static/backend.ts','      return live!.data;','      if (Date.now()-live!.data.lastUpdated>60000 || live!.data.lastUpdated>Date.now()+5000) throw new HttpError(503, "Waiting for fresh market prices");\n      return live!.data;');
patch('packages/web/src/static/backend.ts','      if (live) return live.data;', '      if (live && Date.now()-live.data.lastUpdated<=60000 && live.data.lastUpdated<=Date.now()+5000) return live.data;');
patch('packages/web/src/static/backend.ts','  if (live && (mode !== "paused" || Date.now() - live.at < 60_000)) return live.data;','  if (live && Date.now()-live.data.lastUpdated<=60000 && live.data.lastUpdated<=Date.now()+5000) return live.data;');
// A status-page-only visitor must start the live worker too: no calculator action required.
patch('packages/web/src/static/backend.ts','  const was = mode;','  start();\n  const was = mode;');
patch('packages/web/src/static/backend.ts','a.ts > b.asOf - 2 * 3600_000','a.ts > now - 2 * 3600_000');
// Refresh Hypixel item metadata for NPC prices, rather than guessing from recipe costs.
patch('packages/web/src/static/backend.ts','    const recipes = new Map<string, Recipe[]>();',`    try {
      const url='/v1/items';
      const liveItems=await getJson<{success:boolean;items:{id:string;name?:string;category?:string;tier?:string;npc_sell_price?:number;unstackable?:boolean}[]}>(url,'item metadata');
      if(liveItems.success&&Array.isArray(liveItems.items)) {
        const known=new Map(items.map(i=>[i.id,i]));
        for(const i of liveItems.items)if(typeof i.id==='string') {
          const row:ItemRow={id:i.id,name:i.name??i.id,category:i.category??null,tier:i.tier??null,on_bazaar:known.get(i.id)?.on_bazaar??false,npc_sell_price:i.npc_sell_price??null,unstackable:i.unstackable};
          known.set(i.id,row);market.names[i.id]=row.name;
        }
        items.splice(0,items.length,...known.values());
      }
    }catch { /* Metadata unavailable: keep published values; unpriceable NPC routes say why. */ }
    const recipes = new Map<string, Recipe[]>();`);
patch('packages/web/src/pages/Flips.tsx','import { Fragment, useState }','import { Fragment, useEffect, useState }');
patch('packages/web/src/pages/Flips.tsx','  const [f, setF] = useState<Filters>(DEFAULT);','  const [f, setF] = useState<Filters>(DEFAULT);\n  useEffect(()=>setF(v=>({...v,requirementsMet:!profile.ignoreRequirements})),[profile.ignoreRequirements]);');
// Re-running after an earlier overlay also restores the visible filter's own toggle.
const flipFile=join(web,'src/pages/Flips.tsx');writeFileSync(flipFile,readFileSync(flipFile,'utf8').replace('requirementsMet: !profile.ignoreRequirements || f.requirementsMet || undefined','requirementsMet: f.requirementsMet || undefined'));
patch('packages/web/src/pages/Flips.tsx','noFlags: true } })','noFlags: true, requirementsMet: !profile.ignoreRequirements } })');
patch('packages/web/src/pages/Planner.tsx','import { Fragment, useState }','import { Fragment, useEffect, useState }');
patch('packages/web/src/pages/Planner.tsx','  const [requireMet, setRequireMet] = useState(!profile.ignoreRequirements);','  const [requireMet, setRequireMet] = useState(!profile.ignoreRequirements);\n  useEffect(()=>setRequireMet(!profile.ignoreRequirements),[profile.ignoreRequirements]);');
const plannerFile=join(web,'src/pages/Planner.tsx');writeFileSync(plannerFile,readFileSync(plannerFile,'utf8').replace('options: { kinds, requireMet: !profile.ignoreRequirements || requireMet }','options: { kinds, requireMet }'));
cpSync(join(root,'website-src/requirements.ts'),join(checkout,'packages/shared/src/rules/requirements.ts'));
patch('packages/shared/src/calc/engine.ts','import { BAZAAR,','import { tradeRequirements, BAZAAR,');
patch('packages/shared/src/calc/engine.ts','const reqs = dedupeRequirements(route.requirements);','const reqs = dedupeRequirements([...route.requirements,...[route.outputId,...route.buys.map(b=>b.item)].flatMap(tradeRequirements)]);');
execFileSync('pnpm',['--filter','@bc/shared','build'],{cwd:checkout,stdio:'inherit'});
execFileSync('pnpm',['--filter','@bc/web','build'],{cwd:checkout,stdio:'inherit',env:{...process.env,VITE_STATIC:'1',VITE_REPO:'abdyzam50-rgb/goofyaddons'}});
execFileSync('pnpm',['exec','vite','build','--base','/calculator/','--outDir',join(root,'calculator'),'--emptyOutDir'],{cwd:web,stdio:'inherit',env:{...process.env,VITE_STATIC:'1',VITE_REPO:'abdyzam50-rgb/goofyaddons'}});
const out=join(root,'calculator');
const indexPath=join(out,'index.html');writeFileSync(indexPath,readFileSync(indexPath,'utf8').replace(/^.*<link[^\n]*fonts\.(?:googleapis|gstatic)\.com[^\n]*\n/gm,''));
cpSync(indexPath,join(out,'404.html'));
for(const route of ['flips/bazaar','flips/craft','flips/book','flips/forge','flips/npc','flips/kat','flips/fusion','orders','alerts','record','outlook','dips','items','events','timing','api-docs','status','about','contribute']){
 mkdirSync(join(out,route),{recursive:true});cpSync(indexPath,join(out,route,'index.html'));
}
// Ship full history/reference data, including item pages, rather than requiring a second install.
cpSync(data,join(out,'data'),{recursive:true});
cpSync(join(root,'../../integrations/goofyaddons/src/main/resources/goofyaddons/production-recipes.json'),join(out,'data/production-recipes.json'));
mkdirSync(join(out,'licenses'),{recursive:true});
cpSync(join(root,'../../integrations/goofyaddons/src/main/resources/goofyaddons/NEU-CATALOG-LICENSE.txt'),join(out,'licenses/NEU-CATALOG-LICENSE.txt'));
if(neu){
const {parseNeuItem,neuToHypixelId,petAuctionKey}=await import(pathToFileURL(join(checkout,'packages/shared/dist/index.js')));
const recipes=[],items=JSON.parse(readFileSync(join(out,'data/items.json'),'utf8')),known=new Set(items.map(i=>i.id)),seen=new Set();
for(const file of readdirSync(join(neu,'items')).filter(f=>f.endsWith('.json'))) {
 const item=JSON.parse(readFileSync(join(neu,'items',file),'utf8'));
 for(const r of parseNeuItem(item)) {
  const key=JSON.stringify([r.outputId,r.kind,r.inputs]);if(seen.has(key))continue;seen.add(key);
  recipes.push({output_id:r.outputId,kind:r.kind,inputs:r.inputs,output_count:r.outputCount,duration_s:r.durationS??null,requirements:r.requirements,requirement_text:r.kind==='npc'?r.source??null:item.crafttext??null});
 }
 const id=petAuctionKey(item.internalname)??neuToHypixelId(item.internalname);
 if(!known.has(id)){known.add(id);items.push({id,name:(item.displayname??id).replace(/§./g,''),category:null,tier:null,on_bazaar:false,npc_sell_price:null});}
}
writeFileSync(join(out,'data/recipes.json'),JSON.stringify(recipes));writeFileSync(join(out,'data/items.json'),JSON.stringify(items));
const manifest=JSON.parse(readFileSync(join(out,'data/manifest.json'),'utf8'));
manifest.recipesVersion='NotEnoughUpdates-REPO '+execFileSync('git',['rev-parse','HEAD'],{cwd:neu,encoding:'utf8'}).trim();
writeFileSync(join(out,'data/manifest.json'),JSON.stringify(manifest));
}
// Source maps are build diagnostics, not a runtime dependency.
for(const f of readdirSync(join(out,'assets')))if(f.endsWith('.map'))rmSync(join(out,'assets',f));
mkdirSync(join(out,'licenses'),{recursive:true});cpSync(join(checkout,'LICENSE'),join(out,'licenses/BAZAAR-CALC-LICENSE'));
if(neu)cpSync(join(neu,'LICENSE'),join(out,'licenses/NEU-LICENSE'));
else cpSync(join(out,'licenses/NEU-CATALOG-LICENSE.txt'),join(out,'licenses/NEU-LICENSE'));
const manifest=JSON.parse(readFileSync(join(out,'data/manifest.json'),'utf8'));
writeFileSync(join(out,'provenance.json'),JSON.stringify({repository:'https://github.com/Goofythesecond/bazaar-calc',commit:COMMIT,neu:manifest.recipesVersion,overlay:'build-website.mjs + website-src (profile, status, live craft planner)',researchOnly:true,productionRecipes:JSON.parse(readFileSync(join(out,'data/production-recipes.json'),'utf8')).source},null,2));
console.log('Embedded full calculator with live verified craft plans');
