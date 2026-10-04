// Loopback calculator, opt-in local account dashboard and public-market collector. No trade execution.
import { dataDirectory } from './data-paths.mjs';
import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { DashboardState } from './dashboard-state.mjs';
import { MarketCollector } from './collector.mjs';
import { ExecutionHistory } from './execution-history.mjs';
import { recommend, PROTOCOL } from './adapter.mjs';
const history = JSON.parse(gunzipSync(readFileSync(new URL('./history.json.gz', import.meta.url))));
const provenance = JSON.parse(readFileSync(new URL('./provenance.json', import.meta.url), 'utf8'));
const assets = new Map([
  ['/', ['dashboard/index.html','text/html; charset=utf-8']],
  ['/dashboard/routes.mjs',['dashboard/routes.mjs','text/javascript; charset=utf-8']],
  ['/dashboard/app.mjs',['dashboard/app.mjs','text/javascript; charset=utf-8']],
  ['/dashboard/upstream.css',['dashboard/upstream.css','text/css; charset=utf-8']],
  ['/dashboard/style.css',['dashboard/style.css','text/css; charset=utf-8']]
].map(([route,[file,type]])=>[route,{data:readFileSync(new URL(file,import.meta.url)),type}]));
export function createCompanion({ collector = null, dashboard = new DashboardState(), executions = new ExecutionHistory() } = {}) {
  const server = createServer(async (req, res) => {
    const host = req.headers.host?.split(':')[0];
    if (!['127.0.0.1','localhost'].includes(host)) { res.writeHead(403);res.end();return; }
    res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    const send = (status, value) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
    if(req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) {send(403,{error:'Local origin required'});return;}
    if(req.method==='GET' && assets.has(req.url)) {const asset=assets.get(req.url);res.writeHead(200,{'Content-Type':asset.type,'Cache-Control':'no-store'});res.end(asset.data);return;}
    if(req.method==='GET' && req.url==='/v1/dashboard') {send(200,{...dashboard.view(collector),execution:executions.status()});return;}
    if(req.method==='POST' && req.url==='/v1/account') {
      if(req.headers['x-goofy-dashboard']!=='local-v1' || req.headers['content-type']!=='application/json') {send(400,{error:'Invalid dashboard headers'});return;}
      try {
        let length=0;const chunks=[];
        for await(const chunk of req) {length+=chunk.length;if(length>1024*1024){send(413,{error:'Account snapshot too large'});return;}chunks.push(chunk);}
        const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if(dashboard.accept(body) && body.executions)executions.ingest(body.executions);
        send(200,{ok:true,execution:executions.status()});
      } catch(error) {send(400,{error:error.message});}
      return;
    }
    if (req.method === 'GET' && req.url === '/health') {
      send(200, { protocol: PROTOCOL, readOnly: true, upstreamCommit: provenance.commit, historyAsOf: (collector?.history() ?? history).asOf, collector: collector?.status() ?? { enabled: false }, execution:executions.status(), dataDirectory:dataDirectory() }); return;
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
      send(200, recommend(JSON.parse(Buffer.concat(chunks).toString('utf8')), collector?.history() ?? history, provenance, Date.now(), executions));
    } catch (error) { send(400, { error: error instanceof Error ? error.message : 'Invalid request' }); }
  });
  server.requestTimeout = 15000; server.headersTimeout = 10000;
  return server;
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.argv[2] ?? 8789);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Port must be 1024–65535');
  const collector = process.argv.includes('--no-collect') ? null : new MarketCollector({ bootstrap: history });
  const server = createCompanion({ collector });
  server.listen(port, '127.0.0.1', () => {
    console.log(`Read-only Bazaar Calc companion: http://127.0.0.1:${port}; continuous collection ${collector ? 'enabled (20s)' : 'disabled'}`);
    console.log(`Persistent data: ${dataDirectory()}`);
    collector?.start();
  });
  for (const signal of ['SIGINT','SIGTERM']) process.once(signal, async () => {
    server.close(); await collector?.stop();
  });
}
