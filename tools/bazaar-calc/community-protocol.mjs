// Public gameplay evidence contains ratios and durations, never balances or receipts.
export const COMMUNITY_PROTOCOL='goofy-community/1';
export const DAY=86400000;
export const MAX_UPLOAD_SAMPLES=20; // Two SQL statements per sample stay below Free D1's 50-query limit.
export const DEFAULT_REPOSITORY='abdyzam50-rgb/goofyaddons';
export const DATA_BRANCH='gameplay-data';
const product=/^[A-Z0-9_]{1,160}$/;
const digest=/^[a-f0-9]{64}$/;
export const repositoryValid=v=>typeof v==='string'&&/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(v);
export async function hash(text) {
 const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text));
 return [...new Uint8Array(bytes)].map(b=>b.toString(16).padStart(2,'0')).join('');
}
export function validateSample(s,now=Date.now()) {
 const keys=['id','engine','inputId','outputId','inputUnits','batch','completedAt','observedMillis','profitRatio','forecast'];
 if(!s||typeof s!=='object'||Array.isArray(s)||Object.keys(s).some(k=>!keys.includes(k))
  ||typeof s.id!=='string'||!digest.test(s.id)||!['books','general'].includes(s.engine)||![s.inputId,s.outputId].every(v=>typeof v==='string'&&product.test(v))
  ||![s.inputUnits,s.batch].every(v=>Number.isInteger(v)&&v>=1&&v<=4096)
  ||!Number.isInteger(s.completedAt)||s.completedAt%3600000!==0||s.completedAt<=0
  ||s.completedAt>now||s.completedAt<now-7*DAY
  ||!Number.isInteger(s.observedMillis)||s.observedMillis<1000||s.observedMillis>DAY||s.observedMillis%1000!==0
  ||s.profitRatio!==null&&(!Number.isFinite(s.profitRatio)||s.profitRatio< -10||s.profitRatio>10))throw new Error('Invalid shared gameplay sample');
 const f=s.forecast;
 if(!f||Object.keys(f).sort().join(',')!=='cycleSeconds,inputPerDay,outputPerDay'
  ||![f.cycleSeconds,f.inputPerDay,f.outputPerDay].every(v=>Number.isFinite(v)&&v>0)
  ||f.cycleSeconds>30*86400||f.inputPerDay>1e15||f.outputPerDay>1e15)throw new Error('Invalid shared forecast');
 if(s.engine==='general') {
  if(s.inputId!==s.outputId||s.inputUnits!==s.batch)throw new Error('Unsupported shared general recipe');
 } else {
  const a=/^(ENCHANTMENT_.+)_(\d+)$/.exec(s.inputId),b=/^(ENCHANTMENT_.+)_(\d+)$/.exec(s.outputId);
  if(!a||!b||a[1]!==b[1]||+a[2]<1||+b[2]>10||+b[2]<=+a[2]||s.batch!==1||s.inputUnits!==2**(+b[2]-+a[2]))throw new Error('Unsupported shared book recipe');
 }
 return {...s,forecast:{...f}};
}
export function validateSubmission(body,now=Date.now()) {
 if(!body||body.protocol!==COMMUNITY_PROTOCOL||Object.keys(body).sort().join(',')!=='protocol,samples'
  ||!Array.isArray(body.samples)||body.samples.length<1||body.samples.length>MAX_UPLOAD_SAMPLES)throw new Error('Invalid gameplay submission');
 const samples=body.samples.map(s=>validateSample(s,now));
 if(new Set(samples.map(s=>s.id)).size!==samples.length)throw new Error('Duplicate gameplay sample');
 return samples;
}
export function validateDataset(body,now=Date.now()) {
 if(!body||body.protocol!==COMMUNITY_PROTOCOL||Object.keys(body).sort().join(',')!=='generatedAt,protocol,samples'
  ||!Number.isFinite(body.generatedAt)||body.generatedAt<now-7*DAY||body.generatedAt>now+5000
  ||!Array.isArray(body.samples)||body.samples.length>2000)throw new Error('Invalid or stale community dataset');
 const ids=new Set();
 const samples=body.samples.map(row=>{
  if(!row||typeof row.contributor!=='string'||!digest.test(row.contributor))throw new Error('Invalid contributor');
  const {contributor,...s}=row;const sample=validateSample(s,now),key=`${contributor}:${s.id}`;
  if(ids.has(key))throw new Error('Duplicate public sample');ids.add(key);
  return {contributor,...sample};
 });
 return {protocol:COMMUNITY_PROTOCOL,generatedAt:body.generatedAt,samples};
}
export async function exportSamples(rows,salt,now=Date.now()) {
 if(typeof salt!=='string'||salt.length<32)throw new Error('Missing private sharing identity');
 const samples=[];
 for(const row of rows) {
  if(!row.eligible||row.censored||row.profit==null||row.proceeds==null||!row.forecast||!row.eventId)continue;
  const ratio=row.expectedProfit>0?Math.max(-10,Math.min(10,Math.round(row.profit/row.expectedProfit*1000)/1000)):null;
  const sample={id:await hash(`${salt}:${row.eventId}`),engine:row.engine,inputId:row.inputId,outputId:row.outputId,
   inputUnits:row.inputUnits,batch:row.batch,completedAt:Math.floor(row.completedAt/3600000)*3600000,
   observedMillis:Math.max(1000,Math.round(row.observedMillis/1000)*1000),profitRatio:ratio,
   forecast:{cycleSeconds:row.forecast.cycleSeconds,inputPerDay:row.forecast.inputPerDay,outputPerDay:row.forecast.outputPerDay}};
  try{samples.push(validateSample(sample,now));}catch{} // Old, interrupted and incomplete observations stay private.
 }
 return samples;
}
