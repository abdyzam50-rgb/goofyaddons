import {COMMUNITY_PROTOCOL,DAY,DATA_BRANCH,hash,repositoryValid,validateSubmission,validateDataset} from '../bazaar-calc/community-protocol.mjs';
const json=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
export class Store {
 constructor(db){this.db=db;}
 async accept(contributor,samples,now) {
  const day=Math.floor(now/DAY)*DAY;
  await this.db.batch(samples.map(s=>this.db.prepare(`INSERT OR IGNORE INTO samples(contributor,id,completed_at,received_at,sample)
   SELECT ?,?,?,?,? WHERE (SELECT count(*) FROM samples WHERE contributor=? AND received_at>=?)<500`)
   .bind(contributor,s.id,s.completedAt,now,JSON.stringify(s),contributor,day)));
  const rows=await this.db.batch(samples.map(s=>this.db.prepare('SELECT id FROM samples WHERE contributor=? AND id=?').bind(contributor,s.id)));
  return rows.flatMap(r=>r.results??[]).map(r=>r.id);
 }
 async dataset(now) {
  await this.db.prepare('DELETE FROM samples WHERE completed_at<?').bind(now-7*DAY).run();
  const result=await this.db.prepare('SELECT contributor,sample FROM samples ORDER BY completed_at DESC,contributor,id LIMIT 10000').all();
  const counts=new Map(),samples=[];
  for(const r of result.results??[]) {
   const s=JSON.parse(r.sample),key=`${r.contributor}:${s.engine}:${s.inputId}:${s.outputId}`;
   if((counts.get(key)??0)>=10)continue;
   counts.set(key,(counts.get(key)??0)+1);samples.push({contributor:r.contributor,...s});
   if(samples.length===2000)break;
  }
  return validateDataset({protocol:COMMUNITY_PROTOCOL,generatedAt:now,samples},now);
 }
}
export async function handleRequest(request,env,now=Date.now()) {
 const path=new URL(request.url).pathname;
 if(request.method==='GET'&&path==='/health')return json(200,{protocol:COMMUNITY_PROTOCOL,ready:Boolean(env.DB&&env.CONTRIBUTOR_HASHES&&env.GITHUB_TOKEN),repository:env.GITHUB_REPOSITORY,branch:DATA_BRANCH});
 if(request.method!=='POST'||path!=='/v1/gameplay')return json(404,{error:'Unknown endpoint'});
 const token=request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{32,128})$/)?.[1];
 if(!token)return json(401,{error:'Contributor token required'});
 let allowed;try{allowed=JSON.parse(env.CONTRIBUTOR_HASHES??'[]');}catch{return json(503,{error:'Contributor registry unavailable'});}
 const contributor=await hash(token);
 if(!Array.isArray(allowed)||!allowed.includes(contributor))return json(403,{error:'Contributor is not enrolled'});
 if(request.headers.get('Content-Type')!=='application/json')return json(400,{error:'JSON required'});
 try {
  const reader=request.body?.getReader();if(!reader)throw new Error('Empty body');let length=0,parts=[];
  while(true){const r=await reader.read();if(r.done)break;length+=r.value.length;if(length>100000){await reader.cancel();return json(413,{error:'Submission too large'});}parts.push(r.value);}
  const bytes=new Uint8Array(length);let at=0;for(const p of parts){bytes.set(p,at);at+=p.length;}
  const samples=validateSubmission(JSON.parse(new TextDecoder().decode(bytes)),now);
  const acknowledged=await new Store(env.DB).accept(contributor,samples,now);
  return json(200,{protocol:COMMUNITY_PROTOCOL,acknowledged,limited:acknowledged.length<samples.length});
 }catch(error){return json(400,{error:error.message==='Invalid shared gameplay sample'?error.message:'Submission could not be validated or stored'});}
}
function base64(text){const bytes=new TextEncoder().encode(text);let raw='';for(let i=0;i<bytes.length;i+=32768)raw+=String.fromCharCode(...bytes.subarray(i,i+32768));return btoa(raw);}
export async function publish(env,{now=Date.now(),fetchImpl=fetch}={}) {
 if(!env.GITHUB_TOKEN||!repositoryValid(env.GITHUB_REPOSITORY))throw new Error('GitHub publisher is not configured');
 const dataset=await new Store(env.DB).dataset(now),root=`https://api.github.com/repos/${env.GITHUB_REPOSITORY}`;
 const headers={'Authorization':`Bearer ${env.GITHUB_TOKEN}`,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'GoofyAddons-gameplay-collector','Content-Type':'application/json'};
 const api=async(path,method='GET',body)=>{
  const r=await fetchImpl(root+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  if(!r.ok&&r.status!==404)throw new Error(`GitHub publisher returned HTTP ${r.status}`);
  return {status:r.status,data:r.status===404?null:await r.json()};
 };
 const ref=await api(`/git/ref/heads/${DATA_BRANCH}`);
 if(ref.status===404) {
  const repo=await api('');const base=await api(`/git/ref/heads/${encodeURIComponent(repo.data.default_branch)}`);
  await api('/git/refs','POST',{ref:`refs/heads/${DATA_BRANCH}`,sha:base.data.object.sha});
 }
 const path='/contents/community-history.json';const previous=await api(`${path}?ref=${DATA_BRANCH}`);
 if(previous.status===200) {
  const decoded=JSON.parse(atob(previous.data.content.replace(/\s/g,'')));
  if(decoded.generatedAt>=now-6*DAY&&JSON.stringify(decoded.samples)===JSON.stringify(dataset.samples))return {changed:false,samples:dataset.samples.length};
 }
 await api(path,'PUT',{message:'Update shared gameplay evidence',branch:DATA_BRANCH,content:base64(JSON.stringify(dataset)),...(previous.data?.sha?{sha:previous.data.sha}:{})});
 return {changed:true,samples:dataset.samples.length};
}
export default {
 fetch(request,env){return handleRequest(request,env);},
 async scheduled(_event,env,context){context.waitUntil(publish(env).catch(error=>{console.error(error.message);throw error;}));}
};
