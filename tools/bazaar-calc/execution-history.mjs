// Local, pseudonymous confirmed outcomes. Never part of public market artifacts.
import { dataFile } from './data-paths.mjs';
import { readFileSync, mkdirSync, writeFileSync, renameSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {validateDataset,COMMUNITY_PROTOCOL} from './community-protocol.mjs';
const DAY=86400000;
const validForecast=f=>f && Number.isFinite(f.cycleSeconds)&&f.cycleSeconds>0&&f.cycleSeconds<=30*86400
  &&[f.inputPerDay,f.outputPerDay].every(v=>Number.isFinite(v)&&v>0&&v<=1e15);
const clamp=(v,lo=0.1,hi=1.5)=>Math.max(lo,Math.min(hi,v));
const timingStrength=(factor,n)=>factor>1?(n>=10?Math.min(1,n/30):0):Math.min(1,n/10);
const weightedQuantile=(rows,key,q)=>{
  const sorted=[...rows].sort((a,b)=>a[key]-b[key]),total=sorted.reduce((sum,s)=>sum+s.weight,0);
  let accumulated=0;for(const s of sorted){accumulated+=s.weight;if(accumulated>=total*q)return s[key];}
  return 1;
};
export class ExecutionHistory {
  constructor({file=dataFile('execution-history.json'),now=Date.now}={}) {
    this.file=file instanceof URL?fileURLToPath(file):file;this.now=now;this.rows=new Map();this.active=[];this.community=[];this.error=null;
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
        ||s.forecast!=null&&!validForecast(s.forecast)
        ||s.expectedProfit!=null&&(!Number.isFinite(s.expectedProfit)||s.expectedProfit<=0)
        ||s.proceeds!=null&&(!Number.isFinite(s.proceeds)||s.proceeds<0)||s.profit!=null&&!Number.isFinite(s.profit))throw new Error('Invalid execution sample');
      return {eventId:s.eventId,engine:s.engine,inputId:s.inputId,outputId:s.outputId,inputUnits:s.inputUnits,batch:s.batch,
        completedAt:s.completedAt,observedMillis:s.observedMillis,proceeds:s.proceeds??null,profit:s.profit??null,eligible:s.eligible,censored:s.censored===true,expectedProfit:s.expectedProfit??null,forecast:s.forecast?{cycleSeconds:s.forecast.cycleSeconds,inputPerDay:s.forecast.inputPerDay,outputPerDay:s.forecast.outputPerDay}:null};
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
  setCommunity(samples) {
    validateDataset({protocol:COMMUNITY_PROTOCOL,generatedAt:this.now(),samples},this.now());
    this.community=samples.map(s=>({eventId:s.id,contributor:s.contributor,community:true,engine:s.engine,inputId:s.inputId,outputId:s.outputId,
      inputUnits:s.inputUnits,batch:s.batch,completedAt:s.completedAt,observedMillis:s.observedMillis,forecast:s.forecast,
      eligible:true,proceeds:1,profit:s.profitRatio??0,expectedProfit:s.profitRatio===null?null:1}));
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
    // Transfer ratios, never raw durations, from the same engine and similar two-sided volumes.
    // Volume is expressed per output unit so 16-input book recipes can be compared fairly.
    const v=row.volumeEvidence,recipe=row.inputUnits/row.batch;
    const peers=v&&v.inputEffectivePerDay>0&&v.outputEffectivePerDay>0?[...this.rows.values(),...this.community].filter(s=>
      s.engine===(row.kind==='BOOK'?'books':'general')&&(!matching(s)||s.community)&&s.eligible&&s.profit!=null&&s.proceeds!=null
      &&s.observedMillis>0&&s.observedMillis<=DAY&&s.completedAt>=this.now()-DAY&&validForecast(s.forecast)).map(s=>{
        const distance=Math.max(Math.abs(Math.log2((s.forecast.inputPerDay/(s.inputUnits/s.batch))/(v.inputEffectivePerDay/recipe))),
          Math.abs(Math.log2(s.forecast.outputPerDay/v.outputEffectivePerDay)));
        return {...s,distance,weight:Math.max(0,1-distance/3)*2**(-(this.now()-s.completedAt)/(DAY/2)),
          ratio:clamp(s.forecast.cycleSeconds/(s.observedMillis/1000))};
      }).filter(s=>s.distance<=2):[];
    // Bound any one route's influence and require three independent completed trades.
    const families=new Map();for(const s of peers.sort((a,b)=>b.completedAt-a.completedAt)){
      const key=`${s.community?s.contributor:'personal'}:${s.inputId}:${s.outputId}:${s.inputUnits/s.batch}`,group=families.get(key)??[];
      if(group.length<(s.community?3:10))group.push(s);families.set(key,group);
    }
    const contributorCounts=new Map();
    const shared=[...families.values()].flat().filter(s=>{
      if(!s.community)return true;const n=contributorCounts.get(s.contributor)??0;
      if(n>=10)return false;contributorCounts.set(s.contributor,n+1);return true;
    }).slice(0,2000),sharedWeight=shared.reduce((sum,s)=>sum+s.weight,0);
    const sharedRaw=shared.length>=3?weightedQuantile(shared,'ratio',0.25):1;
    const prior=1+(sharedRaw-1)*timingStrength(sharedRaw,sharedWeight);
    const sharedExpected=shared.filter(s=>s.expectedProfit>0);
    const sharedRealization=sharedExpected.length>=3?clamp(sharedExpected.reduce((sum,s)=>sum+s.profit*s.weight,0)/sharedExpected.reduce((sum,s)=>sum+s.expectedProfit*s.weight,0),0.1,1):1;
    const profitPrior=1+(sharedRealization-1)*Math.min(1,sharedExpected.reduce((sum,s)=>sum+s.weight,0)/10);
    if(recent.length<3&&!lower.length&&shared.length<3)return;
    const observedTimes=recent.map(normalized).sort((a,b)=>a-b);
    const times=recent.map(s=>validForecast(s.forecast)?row.cycleSeconds*(s.observedMillis/1000)/s.forecast.cycleSeconds:normalized(s)).sort((a,b)=>a-b);
    const observed=times.length?times[Math.ceil(times.length*0.75)-1]:row.cycleSeconds;
    row.marketCycleSeconds=row.cycleSeconds;
    const rawFactor=Math.max(0.1,Math.min(1.5,row.marketCycleSeconds/observed));
    // Downward evidence starts after three cycles; an improvement needs ten.
    const strength=times.length>=3?timingStrength(rawFactor,times.length):0;
    let factor=prior+(rawFactor-prior)*strength;
    const overdue=lower.length?Math.max(...lower):0;
    if(overdue) {
      const lowerFactor=Math.max(0.1,row.marketCycleSeconds/overdue);
      // Unfinished, continuously observed cycles supply duration lower bounds.
      factor=Math.min(factor,lowerFactor);
    }
    factor=Math.max(0.1,Math.min(1.5,factor));
    if(Number.isFinite(row.maxOutputsPerHour))factor=Math.min(factor,row.maxOutputsPerHour/(row.batch/row.marketCycleSeconds*3600));
    row.cycleSeconds=row.marketCycleSeconds/factor;
    row.outputsPerHour=row.batch/row.cycleSeconds*3600;
    const expected=recent.filter(s=>s.expectedProfit>0);
    const realizationRatio=expected.length>=3?Math.max(0.1,Math.min(1,expected.reduce((sum,s)=>sum+s.profit,0)/expected.reduce((sum,s)=>sum+s.expectedProfit,0))):1;
    const realizationFactor=clamp(profitPrior+(realizationRatio-profitPrior)*(expected.length>=3?Math.min(1,expected.length/10):0),0.1,1);
    row.coinsPerHour=row.outputsPerHour*row.profitPerOutput*realizationFactor;
    row.executionEvidence={samples:times.length,pendingSamples:pending.length,censoredSamples:censored.length,
      p75ObservedSeconds:times.length?observedTimes[Math.ceil(observedTimes.length*0.75)-1]:Math.max(row.marketCycleSeconds,overdue),sharedSamples:shared.length>=3?shared.length:0,sharedProfitSamples:sharedExpected.length>=3?sharedExpected.length:0,
      sharedThroughputFactor:prior,sharedProfitRealizationFactor:profitPrior,
      latestAt:Math.max(...shared.map(s=>s.completedAt),...recent.map(s=>s.completedAt),...censored.map(s=>s.completedAt),...pending.map(s=>s.observedAt)),
      throughputFactor:factor,profitRealizationFactor:realizationFactor,expectedProfitSamples:expected.length,marketCycleSeconds:row.marketCycleSeconds,
      marketCoinsPerHour:row.batch/row.marketCycleSeconds*3600*row.profitPerOutput,
      observedCoinsPerHour:recent.length?recent.reduce((sum,s)=>sum+s.profit,0)/recent.reduce((sum,s)=>sum+s.observedMillis,0)*3600000:0};
    row.assumptions.push('shared forecast corrections from same-engine routes within 4× input and output daily volumes; per-route evidence gradually replaces the prior; overdue routes supply lower bounds only');
  }
  status(){return {forecastBacked:[...this.rows.values()].filter(s=>s.eligible&&validForecast(s.forecast)).length,censored:[...this.rows.values()].filter(s=>s.censored).length,pending:this.active.filter(s=>s.observedAt>=this.now()-15000).length,samples:this.rows.size,eligible:[...this.rows.values()].filter(s=>s.eligible).length,error:this.error};}
}
