// Local, paired control channel. Discord credentials never enter Minecraft or account telemetry.
import { randomUUID, timingSafeEqual } from 'node:crypto';
const ACTIONS=new Set(['start','stop','logout','login','chat']);
export class DiscordControl {
  constructor({key,now=Date.now,notify=()=>{}}={}) {
    if(!/^[a-f0-9]{64}$/.test(key??''))throw new Error('Discord bridge needs a 64-character pairing key');
    this.key=key;this.now=now;this.notify=notify;this.session=null;this.seenAt=0;this.snapshot=null;this.queue=[];this.events=new Set();
  }
  authorized(value) {
    return typeof value==='string' && /^[a-f0-9]{64}$/.test(value) && timingSafeEqual(Buffer.from(value),Buffer.from(this.key));
  }
  enqueue(action,text='') {
    if(!ACTIONS.has(action))throw new Error('Unsupported player action');
    if(!this.session || this.now()-this.seenAt>10000)throw new Error('Minecraft bridge is offline');
    if(this.queue.length)throw new Error('Wait for the previous player command acknowledgment');
    if(action==='chat' && (!text.trim() || text.length>256 || /[\r\n\x00-\x1f]/.test(text) || /^[/.]/.test(text.trim())))throw new Error('Chat must be a single plain message, up to 256 characters');
    const entry={id:randomUUID(),sessionId:this.session,action,text,issuedAt:this.now(),expiresAt:this.now()+30000};this.queue.push(entry);return entry.id;
  }
  exchange(body) {
    if(body?.protocol!=='goofy-discord/1' || typeof body.sessionId!=='string' || !/^[a-f0-9-]{36}$/.test(body.sessionId)
      || !Number.isFinite(body.sentAt) || Math.abs(body.sentAt-this.now())>30000 || !Array.isArray(body.events) || body.events.length>32
      || !Array.isArray(body.acks) || body.acks.length>32)throw new Error('Invalid control snapshot');
    if(this.session && this.session!==body.sessionId && this.now()-this.seenAt<10000)throw new Error('Another Minecraft instance is already paired');
    if(this.session!==body.sessionId){this.queue=[];this.events.clear();}
    this.session=body.sessionId;this.seenAt=this.now();
    this.snapshot={connected:body.connected===true,state:String(body.state??'UNKNOWN').slice(0,64),mode:String(body.mode??'').slice(0,16),
      purse:Number.isFinite(body.purse)&&body.purse>=0?body.purse:null,profit:Number.isFinite(body.profit)?body.profit:null,perHour:Number.isFinite(body.perHour)?body.perHour:null};
    for(const ack of body.acks) {
      const queued=this.queue.find(q=>q.id===ack?.id);
      if(!queued)continue;this.queue=this.queue.filter(q=>q!==queued);
      this.notify({kind:'result',text:`${queued.action}: ${String(ack.result??'processed').slice(0,300)}`,ping:false});
    }
    for(const event of body.events) {
      if(!event || typeof event.id!=='string' || typeof event.text!=='string' || !['mention','staff','transfer','connection'].includes(event.kind) || this.events.has(event.id))continue;
      this.events.add(event.id);if(this.events.size>256)this.events.delete(this.events.values().next().value);
      this.notify({kind:event.kind,text:event.text.slice(0,1200),ping:['mention','staff'].includes(event.kind)});
    }
    const expired=this.queue.filter(q=>q.expiresAt<this.now());
    this.queue=this.queue.filter(q=>q.expiresAt>=this.now());
    for(const q of expired)this.notify({kind:'result',text:`${q.action}: expired without acknowledgment; it will not be retried in a new session`,ping:false});
    return {protocol:'goofy-discord/1',commands:this.queue.slice(0,1),delivery:this.deliveryStatus?.()??null};
  }
  status() {
    if(!this.snapshot || this.now()-this.seenAt>10000)return 'Minecraft bridge offline (or not enabled).';
    const s=this.snapshot,coins=n=>n==null?'unknown':Math.round(n).toLocaleString('en-US');
    return `${s.connected?'Connected':'Logged off'} · ${s.state} · ${s.mode}\nPurse: ${coins(s.purse)} · confirmed session profit: ${coins(s.profit)} · actual coins/h: ${coins(s.perHour)}`;
  }
}
