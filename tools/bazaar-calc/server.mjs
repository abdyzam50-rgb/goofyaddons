// Loopback calculator, account dashboard, market collector and optional paired Discord controls.
import { DiscordBot } from './discord-bot.mjs';
import { discordSettings } from './discord-config.mjs';
import { dataDirectory } from './data-paths.mjs';
import { createServer } from 'node:http';
import { readFileSync, readdirSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { DashboardForecast } from './dashboard-forecast.mjs';
import { DashboardState } from './dashboard-state.mjs';
import { MarketCollector } from './collector.mjs';
import { ExecutionHistory } from './execution-history.mjs';
import { CommunitySync } from './community.mjs';
import { recommend, PROTOCOL, FORECAST_CONTRACT } from './adapter.mjs';
const history = JSON.parse(gunzipSync(readFileSync(new URL('./history.json.gz', import.meta.url))));
const provenance = JSON.parse(readFileSync(new URL('./provenance.json', import.meta.url), 'utf8'));
const assets = new Map([
  ['/', ['dashboard/index.html','text/html; charset=utf-8']],
  ['/dashboard/routes.mjs',['dashboard/routes.mjs','text/javascript; charset=utf-8']],
  ['/dashboard/app.mjs',['dashboard/app.mjs','text/javascript; charset=utf-8']],
  ['/dashboard/upstream.css',['dashboard/upstream.css','text/css; charset=utf-8']],
  ['/dashboard/style.css',['dashboard/style.css','text/css; charset=utf-8']]
].map(([route,[file,type]])=>[route,{data:readFileSync(new URL(file,import.meta.url)),type}]));
// Serve only bundled files; user-controlled paths never reach the filesystem.
function calculatorAssets(dir='calculator',prefix='/calculator/') {
 for(const entry of readdirSync(new URL(`${dir}/`,import.meta.url),{withFileTypes:true})) {
  if(entry.isDirectory())calculatorAssets(`${dir}/${entry.name}`,`${prefix}${entry.name}/`);
  else {
   const type=entry.name.endsWith('.html')?'text/html; charset=utf-8':entry.name.endsWith('.js')?'text/javascript; charset=utf-8':entry.name.endsWith('.css')?'text/css; charset=utf-8':entry.name.endsWith('.json')?'application/json':entry.name.endsWith('.svg')?'image/svg+xml':'application/octet-stream';
   assets.set(`${prefix}${entry.name}`,{data:readFileSync(new URL(`${dir}/${entry.name}`,import.meta.url)),type});
  }
 }
}
calculatorAssets();
export function createCompanion({ collector = null, dashboard = new DashboardState(), executions = new ExecutionHistory(), community = null, control = null, profileFetcher=fetch, resourcesFetcher=fetch, publishingFetcher=fetch, auctionFetcher=fetch, bundle=null } = {}) {
  const forecasts=new DashboardForecast({provenance});
  let itemMetadata=null,itemMetadataAt=0,itemMetadataFlight=null;
  const auctionPrices=new Map();
  const server = createServer(async (req, res) => {
    const host = req.headers.host?.split(':')[0];
    if (!['127.0.0.1','localhost'].includes(host)) { res.writeHead(403);res.end();return; }
    res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer');
    const calculatorPage=req.url?.startsWith('/calculator/');
    res.setHeader('Content-Security-Policy',`default-src 'self'; script-src 'self'; style-src 'self'${calculatorPage?" 'unsafe-inline'":''}; img-src 'self' data:; connect-src 'self'; worker-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`);
    const send = (status, value) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
    if(req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) {send(403,{error:'Local origin required'});return;}
    const url=new URL(req.url,'http://localhost');
    if(req.method==='GET'&&url.pathname==='/v1/publishing-status') {
      try {
        const r=await publishingFetcher('https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/publishing-status',{signal:AbortSignal.timeout(15000)});
        const body=await r.json();if(!r.ok || body.protocol!=='goofy-publishing-status/1')throw new Error();send(200,body);
      }catch{send(503,{error:'Shared publisher status is unavailable; update the collector deployment'});}return;
    }
    if(req.method==='GET'&&url.pathname==='/v1/items') {
      try {
        if(!itemMetadata || Date.now()-itemMetadataAt>86400000) {
          itemMetadataFlight??=(async()=>{
            const r=await resourcesFetcher('https://api.hypixel.net/v2/resources/skyblock/items',{signal:AbortSignal.timeout(10000)}),body=await r.json();
            if(!r.ok||body.success!==true||!Array.isArray(body.items)||body.items.length>50000)throw new Error();
            itemMetadata=body;itemMetadataAt=Date.now();
          })().finally(()=>{itemMetadataFlight=null;});
          await itemMetadataFlight;
        }
        send(200,itemMetadata);
      }catch{send(502,{error:'Item metadata temporarily unavailable'});}
      return;
    }
    if(req.method==='GET'&&url.pathname==='/v1/market') {
      const market=collector?.market?.();
      if(!market){send(503,{error:'Waiting for a fresh Bazaar snapshot; the market collector must be running'});return;}
      send(200,market);return;
    }
    if(req.method==='GET'&&url.pathname==='/v1/ah/price') {
      // Lowest BIN for one item from Coflnet, so listings are priced from the live market, not typed in.
      const item=url.searchParams.get('item')??'';
      if(!/^[A-Z0-9_:;\-]{1,64}$/.test(item)){send(400,{error:'Give a SkyBlock item ID'});return;}
      const cached=auctionPrices.get(item);
      if(cached&&Date.now()-cached.fetchedAt<60000){send(200,cached);return;}
      try {
        const r=await auctionFetcher(`https://sky.coflnet.com/api/item/price/${encodeURIComponent(item)}/bin`,{signal:AbortSignal.timeout(10000)});
        if(r.status===404||r.status===204){send(404,{error:'No BIN auctions found for that item'});return;}
        const body=await r.json();
        const lowest=Number(body?.lowest),second=body?.secondLowest==null?null:Number(body.secondLowest);
        if(!r.ok||!Number.isFinite(lowest)||lowest<1||lowest>1e13||(second!=null&&(!Number.isFinite(second)||second<lowest)))throw new Error();
        const price={protocol:'goofy-ah-price/1',item,lowest,secondLowest:second,source:'coflnet',fetchedAt:Date.now()};
        if(auctionPrices.size>500)auctionPrices.clear();
        auctionPrices.set(item,price);send(200,price);
      }catch{send(502,{error:'Auction price service unavailable; give a price'});}
      return;
    }
    if(req.method==='GET'&&url.pathname==='/v1/profiles') {
      const username=url.searchParams.get('username')??'';
      if(!/^[A-Za-z0-9_]{1,16}$/.test(username)){send(400,{error:'Enter a Minecraft username'});return;}
      try {
        const r=await profileFetcher(`https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles?username=${encodeURIComponent(username)}`,{signal:AbortSignal.timeout(15000)});
        const body=await r.json();
        if(r.ok && (body.protocol!=='goofy-profile/1'||!Array.isArray(body.profiles)||body.profiles.length>10))throw new Error();
        send(r.ok?200:[400,429,503].includes(r.status)?r.status:502,r.ok?body:{error:'Profile lookup unavailable; check the shared Worker setup or use a manual budget'});
      }catch{send(502,{error:'Profile service could not be reached; use a manual budget'});}
      return;
    }
    if(req.method==='GET'&&calculatorPage&&!assets.has(req.url)&&!url.pathname.split('/').at(-1).includes('.')) {
      const a=assets.get('/calculator/index.html');res.writeHead(200,{'Content-Type':a.type,'Cache-Control':'no-store'});res.end(a.data);return;
    }
    if(req.url==='/v1/control/exchange' && req.method==='POST') {
      if(!control || !control.authorized(req.headers['x-goofy-control'])) {send(403,{error:'Control pairing required'});return;}
      if(req.headers['content-type']!=='application/json'){send(400,{error:'JSON required'});return;}
      try {
        let size=0;const chunks=[];
        for await(const chunk of req){size+=chunk.length;if(size>32768){send(413,{error:'Control snapshot too large'});return;}chunks.push(chunk);}
        send(200,control.exchange(JSON.parse(Buffer.concat(chunks).toString('utf8'))));
      }catch {send(400,{error:'Invalid or conflicting control session'});}
      return;
    }
    if(req.method==='GET' && assets.has(req.url)) {const asset=assets.get(req.url);res.writeHead(200,{'Content-Type':asset.type,'Cache-Control':'no-store'});res.end(asset.data);return;}
    if(req.method==='GET' && req.url==='/v1/dashboard') {const view=dashboard.view(collector);send(200,{...view,...forecasts.view(view,collector?.history()??history,executions),execution:executions.status(),community:community?.status()??{sharingEnabled:false,downloadsEnabled:false}});return;}
    if(req.method==='POST' && req.url==='/v1/executions') {
      if(req.headers['x-goofy-dashboard']!=='local-v1' || req.headers['content-type']!=='application/json') {send(400,{error:'Invalid execution headers'});return;}
      try {
        let length=0;const chunks=[];
        for await(const chunk of req) {length+=chunk.length;if(length>1024*1024){send(413,{error:'Execution snapshot too large'});return;}chunks.push(chunk);}
        const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if(body.protocol!=='goofy-executions/1' || Object.keys(body).sort().join(',')!=='executions,protocol,sentAt'
          ||!Number.isFinite(body.sentAt)||Math.abs(Date.now()-body.sentAt)>30000)throw new Error();
        executions.ingest(body.executions);community?.tick();
        send(200,{ok:true,execution:executions.status()});
      } catch {send(400,{error:'Invalid execution snapshot'});}
      return;
    }
    if(req.method==='POST' && req.url==='/v1/account') {
      if(req.headers['x-goofy-dashboard']!=='local-v1' || req.headers['content-type']!=='application/json') {send(400,{error:'Invalid dashboard headers'});return;}
      try {
        let length=0;const chunks=[];
        for await(const chunk of req) {length+=chunk.length;if(length>1024*1024){send(413,{error:'Account snapshot too large'});return;}chunks.push(chunk);}
        const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if(dashboard.accept(body)) {
          if(body.executions)executions.ingest(body.executions);
          executions.ingestActive(body.account.connected && body.status.state==='RUNNING' ? body.activeExecutions??[] : []);
          community?.tick();
        }
        send(200,{ok:true,execution:executions.status()});
      } catch(error) {send(400,{error:error.message});}
      return;
    }
    if (req.method === 'GET' && req.url === '/health') {
      send(200, { protocol: PROTOCOL, forecastContract: FORECAST_CONTRACT, bundle: bundle ?? undefined, readOnly: !control, discord:control?.deliveryStatus?.()??{enabled:false}, upstreamCommit: provenance.commit, historyAsOf: (collector?.history() ?? history).asOf, collector: collector?.status() ?? { enabled: false }, execution:executions.status(),community:community?.status()??{sharingEnabled:false,downloadsEnabled:false}, dataDirectory:dataDirectory() }); return;
    }
    if (req.method !== 'POST' || req.url !== '/v1/recommendations') { send(404, { error: 'Unknown endpoint' }); return; }
    if (req.headers['x-goofy-analysis'] !== 'shadow-v1' || req.headers['content-type'] !== 'application/json') { send(400, { error: 'Invalid request headers' }); return; }
    try {
      let length = 0; const chunks = [];
      for await (const chunk of req) {
        length += chunk.length;
        if (length > 10 * 1024 * 1024) { send(413, { error: 'Market snapshot too large' }); return; }
        chunks.push(chunk);
      }
      const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const report=recommend(body,collector?.history()??history,provenance,Date.now(),executions);
      forecasts.acceptRequest(body);send(200,report);
    } catch (error) { send(400, { error: error instanceof Error ? error.message : 'Invalid request' }); }
  });
  server.requestTimeout = 15000; server.headersTimeout = 10000;
  return server;
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.argv.slice(2).find(arg=>!arg.startsWith('--')) ?? 8789);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Port must be 1024–65535');
  const collector = process.argv.includes('--no-collect') ? null : new MarketCollector({ bootstrap: history });
  const executions=new ExecutionHistory();let community=null;
  if(!process.argv.includes('--no-community'))try{community=new CommunitySync({executions});}catch{console.error('Community settings unavailable; private collection and trading remain enabled.');}
  let discord=null;
  try {const settings=process.argv.includes('--no-discord')?null:discordSettings();if(settings)discord=new DiscordBot(settings);}
  catch {console.error('Discord configuration invalid; Discord controls disabled.');}
  // The mod passes its bundle digest so it can tell its own calculator from an older one on the port.
  const bundle=process.argv.find(arg=>arg.startsWith('--bundle='))?.slice('--bundle='.length).replace(/[^A-Za-z0-9]/g,'').slice(0,64)||null;
  const server = createCompanion({ collector,executions,community,control:discord?.control,bundle });
  server.listen(port, '127.0.0.1', () => {
    console.log(`Bazaar Calc companion: http://127.0.0.1:${port}; continuous collection ${collector ? 'enabled (20s)' : 'disabled'}`);
    console.log(`Persistent data: ${dataDirectory()}`);
    collector?.start();discord?.start();
    community?.start();
  });
  let stopping=false;
  const shutdown=async()=>{
    if(stopping)return;stopping=true;
    server.close();server.closeAllConnections();discord?.stop();
    await collector?.stop();await community?.stop();
  };
  for (const signal of ['SIGINT','SIGTERM']) process.once(signal, shutdown);
  if(process.argv.includes('--managed')) {
    // The mod owns this pipe. EOF closes the companion even after a Minecraft crash.
    process.stdin.on('end',shutdown);process.stdin.on('error',shutdown);process.stdin.resume();
  }
}
