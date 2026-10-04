// Automatic opt-in uploads and public downloads. GitHub credentials never reach players.
import {readFileSync,writeFileSync,mkdirSync,renameSync,existsSync} from 'node:fs';
import {join} from 'node:path';
import {randomBytes,createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {dataDirectory} from './data-paths.mjs';
import {COMMUNITY_PROTOCOL,DEFAULT_REPOSITORY,DATA_BRANCH,DAY,MAX_UPLOAD_SAMPLES,exportSamples,validateDataset,repositoryValid} from './community-protocol.mjs';
function endpointValid(endpoint) {
 try{const u=new URL(endpoint);return !u.username&&!u.password&&!u.search&&!u.hash&&u.pathname==='/'
  &&(u.protocol==='https:'||u.protocol==='http:'&&['localhost','127.0.0.1'].includes(u.hostname));}catch{return false;}
}
function write(file,value) {writeFileSync(`${file}.tmp`,JSON.stringify(value),{mode:0o600});renameSync(`${file}.tmp`,file);}
export function configure(directory,{endpoint,token,repository=DEFAULT_REPOSITORY,sharingEnabled=true}) {
 if(!endpointValid(endpoint)||!/^[A-Za-z0-9_-]{32,128}$/.test(token)||!repositoryValid(repository))throw new Error('Valid collector URL, contributor token and repository required');
 mkdirSync(directory,{recursive:true});const path=join(directory,'community-settings.json');
 write(path,{endpoint:new URL(endpoint).origin,token,repository,sharingEnabled});return path;
}
export class CommunitySync {
 constructor({executions,directory=dataDirectory(),fetchImpl=fetch,now=Date.now,downloads=true}={}) {
  this.executions=executions;this.directory=directory;this.fetch=fetchImpl;this.now=now;this.downloads=downloads;
  mkdirSync(directory,{recursive:true});this.settings={repository:DEFAULT_REPOSITORY,sharingEnabled:false};this.error=null;
  this.sent=new Map();this.imported=0;this.lastUpload=0;this.lastDownload=0;this.nextUpload=0;this.nextDownload=0;this.pending=0;this.running=null;this.timer=null;
  try{const raw=JSON.parse(readFileSync(join(directory,'community-settings.json'),'utf8'));
   if(!repositoryValid(raw.repository)||!endpointValid(`${raw.endpoint}/`)||!/^[A-Za-z0-9_-]{32,128}$/.test(raw.token)||typeof raw.sharingEnabled!=='boolean')throw new Error('Invalid private community settings');
   this.settings=raw;
  }catch(e){if(e.code!=='ENOENT')this.error='Community settings unreadable; sharing disabled';}
  const identity=join(directory,'community-identity.json');
  if(!existsSync(identity))write(identity,{salt:randomBytes(32).toString('hex')});
  this.salt=JSON.parse(readFileSync(identity,'utf8')).salt;
  if(typeof this.salt!=='string'||this.salt.length<32)throw new Error('Sharing identity unreadable; original preserved');
  try{const state=JSON.parse(readFileSync(join(directory,'community-status.json'),'utf8'));
   if(!Array.isArray(state.sent)||state.sent.length>10000||!state.sent.every(r=>Array.isArray(r)&&/^[a-f0-9]{64}$/.test(r[0])&&Number.isFinite(r[1])))throw new Error('Invalid sharing receipt cache');
   this.sent=new Map(state.sent);
  }catch(e){if(e.code!=='ENOENT')this.error='Sharing receipt cache unreadable; duplicate uploads are deduplicated by the collector';}
  this.restoreCache();
 }
 restoreCache() {
  try{this.apply(JSON.parse(readFileSync(join(this.directory,'community-history.json'),'utf8')));}
  catch(e){if(e.code!=='ENOENT')this.error='Community cache stale or unreadable; using personal evidence';}
 }
 apply(body) {
  const data=validateDataset(body,this.now());const own=this.settings.token?createHash('sha256').update(this.settings.token).digest('hex'):null;
  const samples=data.samples.filter(s=>s.contributor!==own);this.executions.setCommunity(samples);this.imported=samples.length;
 }
 status(){return {sharingEnabled:this.settings.sharingEnabled,downloadsEnabled:this.downloads,imported:this.imported,pending:this.pending,
  acknowledged:this.sent.size,lastUpload:this.lastUpload,lastDownload:this.lastDownload,repository:this.settings.repository,branch:DATA_BRANCH,error:this.error};}
 start(){if(this.timer)return;this.tick();this.timer=setInterval(()=>this.tick(),30000);this.timer.unref?.();}
 async stop(){if(this.timer)clearInterval(this.timer);this.timer=null;await this.running;}
 tick() {
  if(this.running)return this.running;
  this.running=this.run().catch(()=>{this.error='Community sync unavailable; local trading and private history continue';}).finally(()=>{this.running=null;});
  return this.running;
 }
 async run() {
  const now=this.now();
  for(const [id,at]of this.sent)if(at<now-7*DAY)this.sent.delete(id);
  if(this.downloads&&now>=this.nextDownload) {
   this.nextDownload=now+15*60000;
   try {
    const url=`https://raw.githubusercontent.com/${this.settings.repository}/${DATA_BRANCH}/community-history.json`;
    const response=await this.fetch(url,{signal:AbortSignal.timeout(10000)});
    if(!response.ok)throw new Error('Shared dataset unavailable');
    const body=await boundedJSON(response,1024*1024);this.apply(body);
    write(join(this.directory,'community-history.json'),body);this.lastDownload=now;this.error=null;
   }catch{this.error='Shared data unavailable; cached and personal evidence remain in use';}
  }
  if(!this.settings.sharingEnabled||now<this.nextUpload)return;
  this.nextUpload=now+5*60000;
  const samples=(await exportSamples([...this.executions.rows.values()],this.salt,now)).filter(s=>!this.sent.has(s.id));
  this.pending=samples.length;if(!samples.length)return;
  const batch=samples.slice(0,MAX_UPLOAD_SAMPLES);
  try {
   const response=await this.fetch(`${this.settings.endpoint}/v1/gameplay`,{method:'POST',headers:{'Content-Type':'application/json','Authorization':`Bearer ${this.settings.token}`},
    body:JSON.stringify({protocol:COMMUNITY_PROTOCOL,samples:batch}),signal:AbortSignal.timeout(10000)});
   if(!response.ok)throw new Error('Upload rejected');
   const receipt=await boundedJSON(response,20000),ids=new Set(batch.map(s=>s.id));
   if(receipt.protocol!==COMMUNITY_PROTOCOL||!Array.isArray(receipt.acknowledged)||receipt.acknowledged.some(id=>!ids.has(id))
    ||new Set(receipt.acknowledged).size!==receipt.acknowledged.length)throw new Error('Invalid sharing acknowledgement');
   for(const id of receipt.acknowledged)this.sent.set(id,now);
   write(join(this.directory,'community-status.json'),{sent:[...this.sent]});
   this.pending=samples.length-receipt.acknowledged.length;this.lastUpload=now;
   this.error=receipt.limited?'Contributor daily limit reached; unsent samples remain private for retry':null;
  }catch{this.error='Gameplay upload unavailable; pending evidence stays local and retries automatically';}
 }
}
async function boundedJSON(response,max) {
 const reader=response.body?.getReader();if(!reader)throw new Error('Empty response');let size=0,parts=[];
 while(true){const r=await reader.read();if(r.done)break;size+=r.value.length;if(size>max){await reader.cancel();throw new Error('Response too large');}parts.push(r.value);}
 const bytes=new Uint8Array(size);let at=0;for(const p of parts){bytes.set(p,at);at+=p.length;}
 return JSON.parse(new TextDecoder().decode(bytes));
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
 const [operation,endpoint]=process.argv.slice(2);
 if(operation==='configure') {
  if(!process.env.GOOFY_CONTRIBUTOR_TOKEN)throw new Error('Set GOOFY_CONTRIBUTOR_TOKEN to your collector key first; no GitHub token is needed');
  console.log(`Private sharing settings saved: ${configure(dataDirectory(),{endpoint,token:process.env.GOOFY_CONTRIBUTOR_TOKEN})}`);
 } else if(operation==='disable') {
  const path=join(dataDirectory(),'community-settings.json'),settings=JSON.parse(readFileSync(path,'utf8'));settings.sharingEnabled=false;write(path,settings);
  console.log('Automatic gameplay uploads disabled. Restart the companion.');
 } else throw new Error('Usage: node community.mjs configure https://YOUR-COLLECTOR OR node community.mjs disable');
}
