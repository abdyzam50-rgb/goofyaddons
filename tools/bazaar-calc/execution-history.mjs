// Local, pseudonymous confirmed outcomes. Never part of public market artifacts.
import { dataFile } from './data-paths.mjs';
import { readFileSync, mkdirSync, writeFileSync, renameSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const DAY=86400000;
export class ExecutionHistory {
  constructor({file=dataFile('execution-history.json'),now=Date.now}={}) {
    this.file=file instanceof URL?fileURLToPath(file):file;this.now=now;this.rows=new Map();this.active=[];this.error=null;
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
        ||s.censored!==undefined&&typeof s.censored!=='boolean'
        ||s.censored===true&&(s.eligible||s.proceeds!=null||s.profit!=null||s.observedMillis<180000||s.observedMillis>DAY)
        ||!Number.isFinite(s.observedMillis)||s.observedMillis<0||typeof s.eligible!=='boolean'
        ||s.expectedProfit!=null&&(!Number.isFinite(s.expectedProfit)||s.expectedProfit<=0)
        ||s.proceeds!=null&&(!Number.isFinite(s.proceeds)||s.proceeds<0)||s.profit!=null&&!Number.isFinite(s.profit))throw new Error('Invalid execution sample');
      return {eventId:s.eventId,engine:s.engine,inputId:s.inputId,outputId:s.outputId,inputUnits:s.inputUnits,batch:s.batch,
        completedAt:s.completedAt,observedMillis:s.observedMillis,proceeds:s.proceeds??null,profit:s.profit??null,eligible:s.eligible,censored:s.censored===true,expectedProfit:s.expectedProfit??null};
    });
    if(this.error)return;
    let changed=false;
    for(const s of rows)if(s.completedAt>=this.now()-7*DAY&&!this.rows.has(s.eventId)){this.rows.set(s.eventId,s);changed=true;}
    for(const [id,s]of this.rows)if(s.completedAt<this.now()-7*DAY){this.rows.delete(id);changed=true;}
    this.rows=new Map([...this.rows.entries()].sort((a,b)=>a[1].completedAt-b[1].completedAt).slice(-2000));
    if(save&&changed)try{mkdirSync(dirname(this.file),{recursive:true});writeFileSync(`${this.file}.tmp`,JSON.stringify([...this.rows.values()]));renameSync(`${this.file}.tmp`,this.file);}
    catch(e){this.error=`Execution history save failed: ${e.message}`;}
  }
  ingestActive(samples) {
    if(!Array.isArray(samples)||samples.length>100)throw new Error('Invalid active executions');
    const active=samples.map(s=>{
      if(!s||typeof s.tradeId!=='string'||!s.tradeId.length||s.tradeId.length>200||!['books','general'].includes(s.engine)
        ||![s.inputId,s.outputId].every(x=>typeof x==='string'&&/^[A-Z0-9_]{1,160}$/.test(x))
        ||![s.inputUnits,s.batch].every(x=>Number.isInteger(x)&&x>=1&&x<=4096)
        ||!Number.isFinite(s.startedAt)||s.startedAt<=0||!Number.isFinite(s.observedAt)
        ||s.observedAt>this.now()+5000||s.observedAt<this.now()-15000
        ||s.observedMillis!==s.observedAt-s.startedAt||s.observedMillis<0||s.observedMillis>DAY)
        throw new Error('Invalid active execution');
      return {...s};
    });
    if(new Set(active.map(s=>s.tradeId)).size!==active.length)throw new Error('Duplicate active execution');
    this.active=active; // Session-only lower bounds, never stored or counted as completed profit.
  }
  calibrate(row) {
    if(this.error)return;
    const matching=s=>s.engine===(row.kind==='BOOK'?'books':'general')&&s.inputId===row.inputId&&s.outputId===row.outputId
      &&s.inputUnits/s.batch===row.inputUnits/row.batch;
    const recent=[...this.rows.values()].filter(s=>s.eligible&&s.proceeds!=null&&s.profit!=null&&s.observedMillis>0&&s.observedMillis<=DAY
      &&s.completedAt>=this.now()-DAY&&matching(s));
    const censored=[...this.rows.values()].filter(s=>s.censored&&!s.eligible&&s.observedMillis>=180000&&s.observedMillis<=DAY
      &&s.completedAt>=this.now()-DAY&&matching(s));
    const pending=this.active.filter(s=>s.observedAt>=this.now()-15000&&s.observedMillis>=180000&&matching(s));
    const normalized=s=>s.observedMillis/1000/s.batch*row.batch;
    const lower=[...censored,...pending].map(normalized).filter(t=>t>row.cycleSeconds);
    if(recent.length<3&&!lower.length)return;
    const times=recent.map(normalized).sort((a,b)=>a-b);
    const observed=times.length?times[Math.ceil(times.length*0.75)-1]:row.cycleSeconds;
    row.marketCycleSeconds=row.cycleSeconds;
    const rawFactor=Math.max(0.1,Math.min(1.5,row.marketCycleSeconds/observed));
    // Downward evidence starts after three cycles; an improvement needs ten.
    const strength=rawFactor>1?(times.length>=10?Math.min(1,times.length/30):0):Math.min(1,times.length/10);
    let factor=1+(rawFactor-1)*strength;
    const overdue=lower.length?Math.max(...lower):0;
    if(overdue) {
      const lowerFactor=Math.max(0.1,row.marketCycleSeconds/overdue);
      factor=Math.min(factor,1+(lowerFactor-1)*Math.min(0.8,0.5+0.1*(lower.length-1)));
    }
    factor=Math.max(0.1,Math.min(1.5,factor));
    if(Number.isFinite(row.maxOutputsPerHour))factor=Math.min(factor,row.maxOutputsPerHour/(row.batch/row.marketCycleSeconds*3600));
    row.cycleSeconds=row.marketCycleSeconds/factor;
    row.outputsPerHour=row.batch/row.cycleSeconds*3600;
    const expected=recent.filter(s=>s.expectedProfit>0);
    const realizationRatio=expected.length>=3?Math.max(0.1,Math.min(1,expected.reduce((sum,s)=>sum+s.profit,0)/expected.reduce((sum,s)=>sum+s.expectedProfit,0))):1;
    const realizationFactor=Math.max(0.1,1+(realizationRatio-1)*Math.min(1,expected.length/10));
    row.coinsPerHour=row.outputsPerHour*row.profitPerOutput*realizationFactor;
    row.executionEvidence={samples:times.length,pendingSamples:pending.length,censoredSamples:censored.length,
      p75ObservedSeconds:times.length?observed:Math.max(row.marketCycleSeconds,overdue),latestAt:Math.max(...recent.map(s=>s.completedAt),...censored.map(s=>s.completedAt),...pending.map(s=>s.observedAt)),
      throughputFactor:factor,profitRealizationFactor:realizationFactor,expectedProfitSamples:expected.length,marketCycleSeconds:row.marketCycleSeconds,
      marketCoinsPerHour:row.batch/row.marketCycleSeconds*3600*row.profitPerOutput,
      observedCoinsPerHour:recent.length?recent.reduce((sum,s)=>sum+s.profit,0)/recent.reduce((sum,s)=>sum+s.observedMillis,0)*3600000:0};
    row.assumptions.push('recent personal timings normalized by output quantity; overdue open/retired routes provide duration lower bounds, never confirmed profit');
  }
  status(){return {censored:[...this.rows.values()].filter(s=>s.censored).length,pending:this.active.filter(s=>s.observedAt>=this.now()-15000).length,samples:this.rows.size,eligible:[...this.rows.values()].filter(s=>s.eligible).length,error:this.error};}
}
