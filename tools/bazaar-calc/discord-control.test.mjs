import test from 'node:test';
import assert from 'node:assert/strict';
import { DiscordControl } from './discord-control.mjs';
import { DiscordBot } from './discord-bot.mjs';
import { createCompanion } from './server.mjs';
const key='1'.repeat(64),session='12345678-1234-1234-1234-123456789abc';
const state=(now,extra={})=>({protocol:'goofy-discord/1',sessionId:session,sentAt:now,connected:true,state:'RUNNING',mode:'BOTH',profit:100,perHour:50,purse:1000,events:[],acks:[],...extra});
test('paired controls require a fresh client and never replay into a new session',()=>{
 let now=100000;const notices=[];const control=new DiscordControl({key,now:()=>now,notify:n=>notices.push(n)});
 assert.equal(control.authorized(key),true);assert.equal(control.authorized('0'.repeat(64)),false);assert.equal(control.authorized('é'.repeat(64)),false);
 assert.throws(()=>control.enqueue('logout'),/offline/);
 control.exchange(state(now));const id=control.enqueue('logout');
 const first=control.exchange(state(now));assert.equal(first.commands[0].id,id);
 assert.equal(control.exchange(state(now)).commands[0].id,id);
 control.exchange(state(now,{acks:[{id,result:'Waiting for a verified boundary'}]}));
 assert.equal(control.exchange(state(now)).commands.length,0);assert.equal(notices.length,1);
 const other='22345678-1234-1234-1234-123456789abc';
 assert.throws(()=>control.exchange(state(now,{sessionId:other})),/already paired/);
 control.enqueue('chat','Hello');now+=11000;
 assert.equal(control.exchange(state(now,{sessionId:other})).commands.length,0);
});
test('stale actions, duplicate alerts and arbitrary chat commands are rejected',()=>{
 let now=100000;const notices=[];const control=new DiscordControl({key,now:()=>now,notify:n=>notices.push(n)});
 const event={id:'alert',kind:'staff',text:'[GM] Example: Hello'};
 control.exchange(state(now,{events:[event]}));control.exchange(state(now,{events:[event]}));assert.equal(notices.length,1);
 for(const text of ['/is','.a* stop','hello\n/is',' '.repeat(2),'a'.repeat(257)])assert.throws(()=>control.enqueue('chat',text));
 assert.throws(()=>control.enqueue('delete'));
 control.enqueue('start');now+=31000;assert.equal(control.exchange(state(now)).commands.length,0);
 assert.match(notices.at(-1).text,/expired/);
 assert.throws(()=>control.exchange(state(now-31000)),/Invalid/);
});
test('only the configured Discord user and channel can queue commands',async()=>{
 const now=Date.now(),posts=[];const owner='123456789012345678',channel='223456789012345678';
 const bot=new DiscordBot({ownerId:owner,channelId:channel,botToken:'synthetic',bridgeKey:key},{now:()=>now,request:async(url,opts)=>{posts.push(JSON.parse(opts.body));return {ok:true,status:200,json:async()=>({})};}});
 bot.botId='323456789012345678';bot.control.exchange(state(now));
 const id=((BigInt(now-1420070400000)<<22n)+1n).toString();
 const message={id,channel_id:channel,author:{id:owner},content:`<@${bot.botId}> chat hello`};
 await bot.handle({...message,author:{id:'423456789012345678'}});await bot.handle({...message,channel_id:'523456789012345678'});
 assert.equal(bot.control.queue.length,0);
 await bot.handle(message);assert.equal(bot.control.queue.length,1);assert.equal(bot.control.queue[0].text,'hello');
 await bot.handle({...message,id:((BigInt(now-1420070400000-31000)<<22n)+1n).toString()});assert.equal(bot.control.queue.length,1);
 assert.deepEqual(posts[0].allowed_mentions,{parse:[],users:[]});
});
test('webhook alerts allow only the owner ping and never send bot authorization',async()=>{
 const calls=[];const owner='123456789012345678';
 const bot=new DiscordBot({ownerId:owner,channelId:'223456789012345678',botToken:'synthetic',bridgeKey:key,
 webhookUrl:'https://discord.com/api/webhooks/123456789012345678/synthetic'},{request:async(url,options)=>{calls.push({url,options});return {ok:true,status:204};}});
 await bot.post('@everyone please review',true,true);
 const payload=JSON.parse(calls[0].options.body);assert.deepEqual(payload.allowed_mentions,{parse:[],users:[owner]});
 assert.equal(calls[0].options.headers.Authorization,undefined);
});
test('HTTP player controls reject unpaired and cross-origin requests',async()=>{
 const control=new DiscordControl({key});const server=createCompanion({control});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {
  const url=`http://127.0.0.1:${server.address().port}/v1/control/exchange`;
  const options={method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(state(Date.now()))};
  assert.equal((await fetch(url,options)).status,403);
  assert.equal((await fetch(url,{...options,headers:{...options.headers,'X-Goofy-Control':key,Origin:'https://example.org'}})).status,403);
  const good=await fetch(url,{...options,headers:{...options.headers,'X-Goofy-Control':key}});assert.equal(good.status,200);assert.deepEqual((await good.json()).commands,[]);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
test('bot startup skips old messages and serializes player actions until acknowledged',async()=>{
 const now=Date.now(),owner='123456789012345678',channel='223456789012345678',botId='323456789012345678';
 const pastId=((BigInt(now-1420070400000-60000)<<22n)+1n).toString();
 const bot=new DiscordBot({ownerId:owner,channelId:channel,botToken:'synthetic',bridgeKey:key},{now:()=>now,request:async(url,options={})=>({ok:true,status:200,json:async()=>
  url.endsWith('/users/@me')?{id:botId}:url.endsWith('messages?limit=1')?[{id:pastId,author:{id:owner},channel_id:channel,content:`<@${botId}> start`}]:[]})});
 bot.control.exchange(state(now));await bot.tick();assert.equal(bot.control.queue.length,0);assert.equal(bot.after,pastId);
 bot.control.enqueue('start');assert.throws(()=>bot.control.enqueue('logout'),/previous player command/);
});
test('Discord rate limits are honored before any subsequent poll',async()=>{
 let now=100000,calls=0;
 const bot=new DiscordBot({ownerId:'123456789012345678',channelId:'223456789012345678',botToken:'synthetic',bridgeKey:key},{now:()=>now,
  request:async()=>{calls++;return {ok:false,status:429,json:async()=>({retry_after:90})};}});
 await bot.tick();assert.equal(bot.nextPoll,190000);now+=3000;await bot.tick();assert.equal(calls,1);
});
