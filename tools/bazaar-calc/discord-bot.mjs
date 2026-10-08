import { DiscordControl } from './discord-control.mjs';
const API='https://discord.com/api/v10';
// Commands deliberately mention the bot: Discord makes mentioned messages available without
// reading every channel's message content or requiring the privileged Message Content intent.
export class DiscordBot {
  constructor(settings,{request=fetch,now=Date.now}={}) {
    this.settings=settings;this.request=request;this.now=now;this.after=null;this.botId=null;this.timer=null;this.busy=false;this.nextPoll=0;this.lastStatus=0;this.notices=[];this.lastError=null;this.ready=false;
    for(const name of ['channelId','ownerId'])if(typeof settings[name]!=='string' || !/^\d{17,22}$/.test(settings[name]))throw new Error(`Invalid Discord ${name}`);
    if(typeof settings.botToken!=='string' || !settings.botToken)throw new Error('Discord bot token missing');
    if(settings.webhookUrl) {
      const u=new URL(settings.webhookUrl);
      if(u.protocol!=='https:' || u.hostname!=='discord.com' || !/^\/api\/webhooks\/\d+\/[A-Za-z0-9_-]+$/.test(u.pathname) || u.search || u.hash)throw new Error('Invalid Discord webhook URL');
    }
    if(settings.statusSeconds!=null && (!Number.isInteger(settings.statusSeconds) || settings.statusSeconds<60 || settings.statusSeconds>3600))throw new Error('Status interval must be 60–3600 seconds');
    this.control=new DiscordControl({key:settings.bridgeKey,now,notify:n=>{if(this.notices.length<64)this.notices.push(n);}});
    this.control.deliveryStatus=()=>({enabled:true,ready:this.ready,error:this.lastError});
  }
  async api(path,options={}) {
    const response=await this.request(API+path,{...options,signal:AbortSignal.timeout(8000),headers:{Authorization:`Bot ${this.settings.botToken}`,'Content-Type':'application/json',...options.headers}});
    if(response.status===429){const rate=await response.json();this.nextPoll=this.now()+Math.max(3000,Number(rate.retry_after)*1000||5000);throw new Error('Discord rate limited');}
    if(!response.ok){this.lastError=`Discord HTTP ${response.status}`;throw new Error(this.lastError);}
    return response.status===204?null:response.json();
  }
  async post(text,ping=false,webhook=false) {
    const content=(ping?`<@${this.settings.ownerId}> `:'')+text;
    const payload={content:content.slice(0,1900),allowed_mentions:{parse:[],users:ping?[this.settings.ownerId]:[]}};
    if(webhook && this.settings.webhookUrl) {
      const response=await this.request(this.settings.webhookUrl,{method:'POST',signal:AbortSignal.timeout(8000),headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
      if(response.status===429){const rate=await response.json();this.nextPoll=this.now()+Math.max(3000,Number(rate.retry_after)*1000||5000);throw new Error('Discord webhook rate limited');}
      if(!response.ok)throw new Error(`Discord webhook HTTP ${response.status}`);
    }else await this.api(`/channels/${this.settings.channelId}/messages`,{method:'POST',body:JSON.stringify(payload)});
  }
  async handle(message) {
    if(message.author?.id!==this.settings.ownerId || message.author?.bot || String(message.channel_id)!==this.settings.channelId)return;
    const match=new RegExp(`^<@!?${this.botId}>\\s+(status|start|stop|logout|login|chat)(?:\\s+([\\s\\S]*))?$`,'i').exec(message.content??'');
    if(!match)return;
    const action=match[1].toLowerCase(),text=match[2]??'';
    if(action!=='status' && (!/^\d+$/.test(message.id??'') || this.now()-(Number(BigInt(message.id)>>22n)+1420070400000)>30000))return;
    try {
      if(action==='status'){await this.post(this.control.status());return;}
      if(action!=='chat' && text.trim())throw new Error('This action takes no arguments');
      const id=this.control.enqueue(action,text);await this.post(`${action} queued (${id.slice(0,8)}). Waiting for Minecraft acknowledgment.`);
    }catch(error){await this.post(`Not queued: ${error.message}`);}
  }
  async tick() {
    if(this.busy || this.now()<this.nextPoll)return;this.busy=true;
    try {
      if(!this.botId){this.botId=(await this.api('/users/@me')).id;const recent=await this.api(`/channels/${this.settings.channelId}/messages?limit=1`);this.after=recent[0]?.id??'0';}
      // Start from the most recent message. Old commands are never replayed after restart.
      const rows=await this.api(`/channels/${this.settings.channelId}/messages?limit=100&after=${this.after}`);
      for(const message of [...rows].sort((a,b)=>BigInt(a.id)<BigInt(b.id)?-1:1)) {
        this.after=message.id;await this.handle(message);
      }
      if(this.notices.length){await this.post(this.notices[0].text,this.notices[0].ping,true);this.notices.shift();}
      if(this.now()-this.lastStatus>=Math.max(60,this.settings.statusSeconds??300)*1000) {
        await this.post(this.control.status(),false,true);this.lastStatus=this.now();
      }
    this.ready=true;this.lastError=null;
    }catch{this.ready=false;this.lastError??="Discord connection unavailable"; /* Do not log credentials or received chat. */ }
    finally{this.busy=false;}
  }
  start(){if(!this.timer){void this.tick();this.timer=setInterval(()=>void this.tick(),3000);this.timer.unref();}}
  stop(){clearInterval(this.timer);this.timer=null;}
}
