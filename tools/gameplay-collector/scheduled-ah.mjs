import {AHHistory} from './ah-history.mjs';
import {createPublicCrafts} from './public-crafts.mjs';
import {DATA_BRANCH,repositoryValid} from '../bazaar-calc/community-protocol.mjs';
const message=e=>/HTTP \d{3}/.exec(e.message)?.[0]??'AH collector unavailable; check Worker logs';
export async function collectAH(env,{now=Date.now(),fetchImpl=fetch}={}) {
 const history=new AHHistory(env.DB),previous=await history.state('collector');
 await history.setState('collector',{...previous,result:'RUNNING',lastAttemptAt:now,error:null});
 try {
  const service=createPublicCrafts({fetchImpl,now:()=>now,cache:null,rotationSize:12,offset:previous.offset??0,persist:false});
  const response=await service(new Request('https://collector.internal/v1/crafts/market'),env);
  if(!response.ok)throw new Error(`AH collection HTTP ${response.status}`);
  const view=await response.json(),saved=await history.record(view,now);
  if(!saved)throw new Error(view.error??'No usable AH observations returned');
  const state={result:view.error?'PARTIAL':'COLLECTED',lastAttemptAt:now,lastSuccessAt:now,offset:(previous.offset??0)+12,savedObservations:saved,quotedItems:view.rows.filter(r=>r.quote).length,error:view.error?message(new Error(view.error)):null};
  await history.setState('collector',state);return state;
 }catch(e){await history.setState('collector',{...previous,result:'FAILED',lastAttemptAt:now,error:message(e)});throw e;}
}
export async function publishAH(env,{now=Date.now(),fetchImpl=fetch}={}) {
 const history=new AHHistory(env.DB),previous=await history.state('publisher');
 await history.setState('publisher',{...previous,result:'RUNNING',lastAttemptAt:now,error:null});
 try {
  if(!env.GITHUB_TOKEN||!repositoryValid(env.GITHUB_REPOSITORY))throw new Error('GitHub AH publisher is not configured');
  const dataset=await history.dataset(now),root=`https://api.github.com/repos/${env.GITHUB_REPOSITORY}`;
  const headers={Authorization:`Bearer ${env.GITHUB_TOKEN}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'GoofyAddons-AH-history','Content-Type':'application/json'};
  const api=async(path,method='GET',body)=>{
   const r=await fetchImpl(root+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
   if(!r.ok&&r.status!==404)throw new Error(`GitHub AH publisher HTTP ${r.status}`);
   return {status:r.status,data:r.status===404?null:await r.json()};
  };
  const ref=await api(`/git/ref/heads/${DATA_BRANCH}`);
  if(ref.status===404){const repo=await api(''),base=await api(`/git/ref/heads/${encodeURIComponent(repo.data.default_branch)}`);const created=await api('/git/refs','POST',{ref:`refs/heads/${DATA_BRANCH}`,sha:base.data.object.sha});if(created.status===404)throw new Error('GitHub AH publisher HTTP 404');}
  const path='/contents/ah-market-history.json',old=await api(`${path}?ref=${DATA_BRANCH}`);
  let changed=true;
  if(old.status===200&&old.data.content){const decoded=JSON.parse(atob(old.data.content.replace(/\s/g,'')));changed=JSON.stringify(decoded.rows)!==JSON.stringify(dataset.rows);}
  let commit=null;
  if(changed){const text=JSON.stringify(dataset);if(text.length>900000)throw new Error('AH export exceeded its bounded size');const result=await api(path,'PUT',{branch:DATA_BRANCH,message:'Update shared AH market history',content:btoa(text),...(old.data?.sha?{sha:old.data.sha}:{})});if(result.status===404)throw new Error('GitHub AH publisher HTTP 404');commit=result.data.commit;}
  const state={...previous,result:changed?'COMMITTED':'UNCHANGED',lastAttemptAt:now,lastSuccessAt:now,items:dataset.rows.length,error:null,...(changed?{lastCommitAt:now,commitSha:commit?.sha??null,commitUrl:commit?.html_url??null}:{})};
  await history.setState('publisher',state);return state;
 }catch(e){await history.setState('publisher',{...previous,result:'FAILED',lastAttemptAt:now,error:message(e)});throw e;}
}
export async function runScheduledAH(env,options={}) {
 // Collection and publication survive independent failures; existing retained history remains exportable.
 const collected=await Promise.allSettled([collectAH(env,options)]);
 const published=await Promise.allSettled([publishAH(env,options)]);
 return {collection:collected[0].status,publishing:published[0].status};
}
