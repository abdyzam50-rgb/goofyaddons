// Local, pseudonymous confirmed outcomes. Never part of public market artifacts.
import { dataFile } from './data-paths.mjs';
import { readFileSync, mkdirSync, writeFileSync, renameSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const DAY=86400000;
export class ExecutionHistory {
  constructor({file=dataFile('execution-history.json'),now=Date.now}={}) {
    this.file=file instanceof URL?fileURLToPath(file):file;this.now=now;this.rows=new Map();this.error=null;
    try { if(readFileSync(this.file).length>2*1024*1024)throw new Error('History too large');this.ingest(JSON.parse(readFileSync(this.file,'utf8')),false); }
    catch(e){if(e.code!=='ENOENT')this.error=`Execution history unreadable; preserved: ${e.message}`;}
  }
  ingest(samples,save=true) {
    if(!Array.isArray(samples)||samples.length>2000)throw new Error('Invalid execution samples');
    const rows=samples.map(s=>{
      if(!s||typeof s.eventId!=='string'||!s.eventId.length||s.eventId.length>200||!['books','general'].includes(s.engine)
        ||![s.inputId,s.outputId].every(x=>typeof x==='string'&&/^[A-Z0-9_]{1,160}$/.test(x))
        ||![s.inputUnits,s.batch].every(x=>Number.isInteger(x)&&x>=1&&x<=4096)
        ||!Number.isFinite(s.completedAt)||s.completedAt>this.now()+5000||s.completedAt<=0
        ||!Number.isFinite(s.observedMillis)||s.observedMillis<0||typeof s.eligible!=='boolean'
        ||s.proceeds!=null&&(!Number.isFinite(s.proceeds)||s.proceeds<0)||s.profit!=null&&!Number.isFinite(s.profit))throw new Error('Invalid execution sample');
      return {eventId:s.eventId,engine:s.engine,inputId:s.inputId,outputId:s.outputId,inputUnits:s.inputUnits,batch:s.batch,
        completedAt:s.completedAt,observedMillis:s.observedMillis,proceeds:s.proceeds??null,profit:s.profit??null,eligible:s.eligible};
    });
    if(this.error)return;
    let changed=false;
    for(const s of rows)if(s.completedAt>=this.now()-7*DAY&&!this.rows.has(s.eventId)){this.rows.set(s.eventId,s);changed=true;}
    for(const [id,s]of this.rows)if(s.completedAt<this.now()-7*DAY){this.rows.delete(id);changed=true;}
    this.rows=new Map([...this.rows.entries()].sort((a,b)=>a[1].completedAt-b[1].completedAt).slice(-2000));
    if(save&&changed)try{mkdirSync(dirname(this.file),{recursive:true});writeFileSync(`${this.file}.tmp`,JSON.stringify([...this.rows.values()]));renameSync(`${this.file}.tmp`,this.file);}
    catch(e){this.error=`Execution history save failed: ${e.message}`;}
  }
  calibrate(row) {
    if(this.error)return;
    const recent=[...this.rows.values()].filter(s=>s.eligible&&s.proceeds!=null&&s.profit!=null&&s.observedMillis>0&&s.observedMillis<=DAY
      &&s.completedAt>=this.now()-DAY&&s.engine===(row.kind==='BOOK'?'books':'general')&&s.inputId===row.inputId&&s.outputId===row.outputId&&s.inputUnits===row.inputUnits&&s.batch===row.batch);
    if(recent.length<10)return;
    const times=recent.map(s=>s.observedMillis/1000).sort((a,b)=>a-b);
    const observed=times[Math.ceil(times.length*0.75)-1];
    // Current spreads remain authoritative; repeated personal timings adjust throughput either way.
    row.marketCycleSeconds=row.cycleSeconds;
    const strength=Math.min(1,times.length/30);
    const rawFactor=Math.max(0.5,Math.min(1.5,row.marketCycleSeconds/observed));
    const factor=1+(rawFactor-1)*strength;
    row.cycleSeconds=row.marketCycleSeconds/factor;
    row.outputsPerHour=row.batch/row.cycleSeconds*3600;
    row.coinsPerHour=row.outputsPerHour*row.profitPerOutput;
    row.executionEvidence={samples:times.length,p75ObservedSeconds:observed,latestAt:Math.max(...recent.map(s=>s.completedAt)),
      throughputFactor:factor,marketCycleSeconds:row.marketCycleSeconds,
      marketCoinsPerHour:row.batch/row.marketCycleSeconds*3600*row.profitPerOutput,
      observedCoinsPerHour:recent.reduce((sum,s)=>sum+s.profit,0)/recent.reduce((sum,s)=>sum+s.observedMillis,0)*3600000};
    row.assumptions.push('recent personal whole-cycle timings adjust throughput; bounded and weighted by sample count');
  }
  status(){return {samples:this.rows.size,eligible:[...this.rows.values()].filter(s=>s.eligible).length,error:this.error};}
}
