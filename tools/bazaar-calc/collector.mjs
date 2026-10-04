// Public market observations only. No account credentials or Minecraft actions.
import { TopTracker, summarizeTop, bookFlow, toLevels, validateBazaar, degradedBazaar, competition, delists } from './engine.mjs';
import { readFileSync } from 'node:fs';
import { mkdir, writeFile, rename } from 'node:fs/promises';
import { dirname } from 'node:path';
import { gzip, gunzipSync } from 'node:zlib';
import { promisify } from 'node:util';
const compress = promisify(gzip), HOUR = 3600000, DAY = 24 * HOUR;
const median = xs => { const s = xs.toSorted((a,b) => a-b), m = s.length >> 1; return s.length ? s.length % 2 ? s[m] : (s[m-1]+s[m])/2 : null; };
const validProduct = p => p && p.quick_status && ['buyMovingWeek','sellMovingWeek','buyVolume','sellVolume','buyOrders','sellOrders'].every(k => Number.isFinite(p.quick_status[k]) && p.quick_status[k] >= 0)
  && [p.sell_summary,p.buy_summary].every(a => Array.isArray(a) && a.every(x => Number.isFinite(x.pricePerUnit) && x.pricePerUnit > 0 && Number.isFinite(x.amount) && x.amount >= 0 && Number.isFinite(x.orders) && x.orders >= 0));

export class MarketCollector {
  constructor({ file = new URL('./data/live-history.json.gz', import.meta.url), fetcher = fetch, now = Date.now, intervalMs = 20000, bootstrap = {} } = {}) {
    this.file = file; this.fetcher = fetcher; this.now = now; this.intervalMs = intervalMs; this.bootstrap = bootstrap;
    this.state = { version: 1, asOf: 0, products: 0, items: {} };
    this.tracker = new TopTracker(); this.previous = new Map(); this.timer = null; this.running = false; this.inFlight = null;
    this.error = null; this.storageError = null; this.lastAttempt = 0; this.failures = 0; this.lastSaved = 0; this.historyCache = null;
    try {
      const state = JSON.parse(gunzipSync(readFileSync(file), { maxOutputLength: 256 * 1024 * 1024 }));
      if (state.version !== 1 || !Number.isFinite(state.asOf) || state.asOf > now() + 5000 || !state.items || Array.isArray(state.items)) throw new Error('Invalid saved history');
      for (const item of Object.values(state.items)) {
        if (!Array.isArray(item.closes) || !Array.isArray(item.flows) || !Array.isArray(item.episodes)) throw new Error('Invalid saved item');
      }
      this.state = state; this.prune();
    } catch (e) { if (e.code !== 'ENOENT') this.storageError = `Saved history could not be loaded: ${e.message}`; }
  }
  prune() {
    const now = this.now();
    for (const [id,item] of Object.entries(this.state.items)) {
      item.closes = item.closes.filter(r => r[0] >= now - 7*DAY);
      item.flows = item.flows.filter(r => r[0] >= Math.floor((now-DAY)/HOUR)*HOUR);
      item.episodes = item.episodes.filter(e => e.endTs >= now-DAY);
      if (!item.closes.length) { delete this.state.items[id]; this.previous.delete(id); }
    }
  }
  accept(payload) {
    const now = this.now();
    const bad = validateBazaar(payload,now);
    if (bad) throw new Error(bad);
    if (payload.lastUpdated < now-60000 || payload.lastUpdated > now+5000) throw new Error('Stale or future market snapshot');
    if (!Object.values(payload.products).every(validProduct)) throw new Error('Malformed market product');
    const degraded = degradedBazaar(payload,this.state.products);
    if (degraded) throw new Error(degraded);
    if (payload.lastUpdated <= this.state.asOf) return false;
    const ts = payload.lastUpdated, hour = Math.floor(ts/HOUR)*HOUR;
    for (const [id,p] of Object.entries(payload.products)) {
      if (!/^[A-Z0-9_]+$/.test(id)) continue;
      const item = this.state.items[id] ??= { closes: [], flows: [], episodes: [] };
      const bids = toLevels(p.sell_summary), asks = toLevels(p.buy_summary), q = p.quick_status;
      if (bids.length && asks.length) {
        const row = [ts,bids[0].price,asks[0].price,q.sellVolume,q.buyVolume,q.buyMovingWeek,q.sellMovingWeek];
        if (Math.floor((item.closes.at(-1)?.[0] ?? 0)/HOUR)*HOUR === hour) item.closes[item.closes.length-1] = row;
        else item.closes.push(row);
      }
      const prev = this.previous.get(id);
      if (prev && ts-prev.ts <= 150000) {
        const change = bookFlow(prev.bids,prev.asks,p.sell_summary,p.buy_summary);
        let flow = item.flows.at(-1);
        if (!flow || flow[0] !== hour) item.flows.push(flow = [hour,0,0,0,0,0,0]);
        flow[1]++; flow[2] += (ts-prev.ts)/1000; flow[3] += Number(change.outbid); flow[4] += Number(change.undercut);
        flow[5] += change.bidRemoved; flow[6] += change.askRemoved;
      }
      item.episodes.push(...this.tracker.step(id,ts,bids,asks));
      // Keep the most recent 128 observations on each side, rather than unbounded bursts.
      item.episodes = ['bid','ask'].flatMap(side => item.episodes.filter(e => e.side === side).slice(-128));
      this.previous.set(id,{ ts,bids,asks });
    }
    this.state.asOf = ts; this.state.products = Object.keys(payload.products).length;
    this.prune(); this.historyCache = null;
    return true;
  }
  quote(id) {
    const book=this.previous.get(id);
    if(!book || this.now()-book.ts>60000 || book.ts>this.now()+5000 || !book.asks[0])return null;
    return {sourceAt:book.ts,ask:book.asks[0].price,bid:book.bids[0]?.price ?? null};
  }
  history() {
    if (!this.state.asOf) return this.bootstrap;
    // Expiry is checked against the wall clock, even if requests to Hypixel have stopped succeeding.
    if (this.historyCache?.expires > this.now()) return this.historyCache.value;
    this.prune();
    const now = this.now(), stats = {}, hold = {};
    for (const [id,item] of Object.entries(this.state.items)) {
      // Do not stamp an absent product's old measurements with another product's new timestamp.
      const latest = item.closes.at(-1);
      if (!latest || latest[0] < now-60000) continue;
      const pts = item.closes, w24 = pts.filter(r => r[0] >= now-DAY);
      const aggregate = item.flows.reduce((a,r) => ({ n:a.n+r[1],secs:a.secs+r[2],ob:a.ob+r[3],uc:a.uc+r[4],br:a.br+r[5],ar:a.ar+r[6] }),{n:0,secs:0,ob:0,uc:0,br:0,ar:0});
      const first = w24[0], last = w24.at(-1);
      const counters = first && last ? {span:(last[0]-first[0])/HOUR,b1:first[5],b2:last[5],s1:first[6],s2:last[6]} : undefined;
      const ago = pts.findLast(r => r[0] <= now-HOUR && r[0] >= now-3*HOUR);
      stats[id] = { askMed:median(pts.map(r=>r[2])),bidMed:median(pts.map(r=>r[1])),spreadMed:median(pts.map(r=>(r[2]-r[1])/r[1])),days:7,
        ...competition(aggregate),liveHours:aggregate.secs/3600,delists:delists(aggregate,counters),
        hourAgo:ago ? {bid:ago[1],ask:ago[2]} : null,
        ask24:median(w24.map(r=>r[2])),bid24:median(w24.map(r=>r[1])),n24:w24.length,
        ask7:median(pts.map(r=>r[2])),bid7:median(pts.map(r=>r[1])),n7:pts.length,
        askVol24:median(w24.map(r=>r[4])),bidVol24:median(w24.map(r=>r[3])) };
      for (const side of ['bid','ask']) {
        const episodes = item.episodes.filter(e => e.side === side).sort((a,b) => a.startTs-b.startTs);
        const summary = summarizeTop(episodes,aggregate.secs/3600);
        if (summary) (hold[id] ??= {})[side] = summary;
      }
    }
    // Bootstrap statistics are replaced, never re-dated or mixed into newly measured samples.
    const value = { asOf:this.state.asOf,stats,hold,names:this.bootstrap.names ?? {} };
    this.historyCache = { value,expires:now+1000 };
    return value;
  }
  async save() {
    const snapshot = JSON.stringify(this.state);
    const bytes = await compress(snapshot);
    const path = this.file instanceof URL ? (await import('node:url')).fileURLToPath(this.file) : this.file;
    await mkdir(dirname(path),{ recursive:true });
    await writeFile(`${path}.tmp`,bytes); await rename(`${path}.tmp`,path);
    this.lastSaved = this.now(); this.storageError = null;
  }
  poll() {
    if (this.inFlight) return this.inFlight;
    this.inFlight = this.runPoll().finally(() => { this.inFlight = null; });
    return this.inFlight;
  }
  async runPoll() {
    this.lastAttempt = this.now();
    try {
      const response = await this.fetcher('https://api.hypixel.net/v2/skyblock/bazaar',{ signal:AbortSignal.timeout(10000),headers:{Accept:'application/json'} });
      if (!response.ok) throw new Error(`Hypixel HTTP ${response.status}`);
      // Stream with a size limit; no unbounded response.json() allocation.
      let length = 0; const chunks = [];
      for await (const chunk of response.body) {
        length += chunk.length;
        if (length > 10*1024*1024) throw new Error('Market response exceeds 10 MiB');
        chunks.push(chunk);
      }
      const changed = this.accept(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      this.error = null; this.failures = 0;
      if ((changed || this.storageError) && this.now()-this.lastSaved >= 60000) {
        try { await this.save(); } catch (e) { this.storageError = `History save failed: ${e.message}`; }
      }
    } catch (e) { this.error = e.cause?.code ? `${e.message} (${e.cause.code})` : e.message; this.failures++; }
  }
  status() {
    const ageMs = this.state.asOf ? Math.max(0,this.now()-this.state.asOf) : null;
    return { enabled:this.running,intervalSeconds:this.intervalMs/1000,lastAttempt:this.lastAttempt,lastUpdated:this.state.asOf,
      ageMs,fresh:ageMs !== null && ageMs <= 60000,products:this.state.products,failures:this.failures,error:this.error,
      lastSaved:this.lastSaved,storageError:this.storageError,historySource:this.state.asOf ? 'continuous live collection' : 'bootstrap' };
  }
  start() {
    if (this.running) return;
    this.running = true;
    const tick = async () => {
      const started = this.now();
      await this.poll();
      const interval = Math.min(120000,this.intervalMs*Math.max(1,2**Math.min(3,this.failures)));
      if (this.running) this.timer = setTimeout(tick,Math.max(1,interval-(this.now()-started)));
    };
    void tick();
  }
  async stop() {
    this.running = false; clearTimeout(this.timer);
    if (this.inFlight) await this.inFlight;
    for (const { item, e } of this.tracker.flushItems()) {
      const recorded = this.state.items[item];
      if (recorded) { recorded.episodes.push(e); recorded.episodes = ['bid','ask'].flatMap(side => recorded.episodes.filter(x => x.side === side).slice(-128)); }
    }
    this.historyCache = null; this.prune();
    if (this.state.asOf) { try { await this.save(); } catch(e) { this.storageError = `History save failed: ${e.message}`; } }
  }
}
