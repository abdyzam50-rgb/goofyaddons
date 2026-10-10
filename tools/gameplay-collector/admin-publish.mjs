// Owner-only publication trigger. This credential is separate from contributor and GitHub tokens.
import {hash} from '../bazaar-calc/community-protocol.mjs';
const json=(status,body)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
const safeError=error=>/HTTP \d{3}/.exec(error?.message??'')?.[0]??'Publication failed; check Worker configuration and logs';
export async function runPublishers(env,{now=Date.now(),gameplay,ah}={}) {
 // A database lease also coordinates manual requests with the scheduled publisher.
 await env.DB.prepare('CREATE TABLE IF NOT EXISTS publication_lock (id INTEGER PRIMARY KEY CHECK(id=1), owner TEXT, expires_at INTEGER NOT NULL)').bind().run();
 await env.DB.prepare('INSERT OR IGNORE INTO publication_lock(id,owner,expires_at) VALUES(1,NULL,0)').bind().run();
 const owner=crypto.randomUUID();
 const lock=await env.DB.prepare('UPDATE publication_lock SET owner=?,expires_at=? WHERE id=1 AND expires_at<=?').bind(owner,now+10*60000,now).run();
 if(!lock.meta?.changes)return {busy:true};
 try {
  const results={};
  // GitHub writes must remain serial even if one dataset fails.
  for(const [name,publish] of [['gameplay',gameplay],['ah',ah]]) {
   try {const result=await publish();results[name]={ok:true,result:result.result??(result.changed?'COMMITTED':'UNCHANGED'),commitSha:result.commitSha??null};}
   catch(error){results[name]={ok:false,error:safeError(error)};}
  }
  return {busy:false,ok:Object.values(results).every(r=>r.ok),checkedAt:now,...results};
 }finally{await env.DB.prepare('UPDATE publication_lock SET owner=NULL,expires_at=0 WHERE id=1 AND owner=?').bind(owner).run();}
}
export async function handleAdminPublish(request,env,options={}) {
 if(request.method!=='POST')return json(405,{error:'Use POST for the owner publication check'});
 if(typeof env.ADMIN_TOKEN!=='string'||env.ADMIN_TOKEN.length<32)return json(503,{error:'Owner publication checks require the ADMIN_TOKEN Worker secret'});
 const supplied=request.headers.get('Authorization')?.match(/^Bearer ([A-Za-z0-9_-]{32,128})$/)?.[1];
 if(!supplied||await hash(supplied)!==await hash(env.ADMIN_TOKEN))return json(401,{error:'Owner authorization required'});
 if(!env.DB)return json(503,{error:'Publication database unavailable'});
 try {
  const result=await runPublishers(env,options);
  return json(result.busy?409:result.ok?200:502,{protocol:'goofy-publication-check/1',...result,...(result.busy?{error:'A publication is already running; retry after it finishes'}:{})});
 }catch{return json(503,{error:'Publication check unavailable; inspect Worker database configuration'});}
}
