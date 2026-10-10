import test from 'node:test';
import assert from 'node:assert/strict';
import {summarizeProfiles,lookupProfiles,handleProfileLookup} from './profile-lookup.mjs';
const uuid='a'.repeat(32),profileId='b'.repeat(32);
const resources={collections:{MINING:{items:{DIAMOND:{name:'Diamond'}}}}};
const member={currencies:{coin_purse:3210000},player_data:{experience:{SKILL_ENCHANTING:50,SKILL_TAMING:175,SKILL_FORAGING:0}},
 mining_core:{experience:3000,nodes:{forge_time:3}},unlocked_coll_tiers:['DIAMOND_4','DIAMOND_3'],slayer_bosses:{zombie:{xp:200}},nether_island_player_data:{barbarians_reputation:500}};
const data=m=>({success:true,profiles:[{profile_id:profileId,cute_name:'Apple',selected:true,members:{[uuid]:m}}]});
test('profile import uses spendable purse and published progression, excludes unrelated account data',()=>{
 const [p]=summarizeProfiles(data({...member,inventory:{secret:'private'},banking:{balance:9999999}}),uuid,resources,1234);
 assert.equal(p.purse,3210000);assert.equal(p.fetchedAt,1234);assert.equal(p.stats.enchantingLevel,1);assert.equal(p.stats.skills.Taming,2);
 assert.equal(p.stats.hotmTier,2);assert.equal(p.stats.collections.Diamond,4);assert.equal(p.stats.slayers.Zombie,3);
 assert.equal(p.stats.ignoreRequirements,false);assert.ok(p.unknown.includes('current XP levels'));
 assert.doesNotMatch(JSON.stringify(p),/private|inventory|banking|9999999/);
});
test('unpublished purse and skills remain unknown; legacy profiles are supported',()=>{
 const [p]=summarizeProfiles(data({}),uuid,null);assert.equal(p.purse,null);assert.ok(p.unknown.includes('enchanting'));assert.ok(p.unknown.includes('collections'));
 const [legacy]=summarizeProfiles(data({coin_purse:42,experience_skill_enchanting:175}),uuid,null);assert.equal(legacy.purse,42);assert.equal(legacy.stats.enchantingLevel,2);
 const [nested]=summarizeProfiles(data({player_data:{unlocked_coll_tiers:['DIAMOND_5']}}),uuid,resources);assert.equal(nested.stats.collections.Diamond,5);
 assert.deepEqual(summarizeProfiles(data(member),'c'.repeat(32),resources),[]);
});
test('craft prerequisites include published farming, mining, combat, fishing, alchemy and carpentry levels',()=>{
 const experience=Object.fromEntries(['FARMING','MINING','COMBAT','FISHING','ALCHEMY','CARPENTRY'].map(name=>[`SKILL_${name}`,175]));
 const [p]=summarizeProfiles(data({...member,player_data:{experience}}),uuid,resources,1234);
 for(const name of ['Farming','Mining','Combat','Fishing','Alchemy','Carpentry'])assert.equal(p.stats.skills[name],2);
 assert.ok(p.unknown.includes('enchanting'));
 assert.equal(p.stats.skills.Enchanting,undefined);
 const [legacy]=summarizeProfiles(data({experience_skill_combat:175}),uuid,null,1234);
 assert.equal(legacy.stats.skills.Combat,2);
});
test('published collection IDs survive unavailable resource names and never infer private tiers',()=>{
 const [p]=summarizeProfiles(data({...member,unlocked_coll_tiers:['GOLD_INGOT_4','GOLD_INGOT_3']}),uuid,null,1234);
 assert.equal(p.stats.collectionIds.GOLD_INGOT,4);assert.deepEqual(p.stats.collections,{});assert.ok(!p.unknown.includes('collections'));
 const [unpublished]=summarizeProfiles(data({}),uuid,null,1234);
 assert.deepEqual(unpublished.stats.collectionIds,{});assert.ok(unpublished.unknown.includes('collections'));
});
test('private API key goes only to Hypixel profiles and is absent from the response',async()=>{
 const calls=[];const fetcher=async(url,options)=>{calls.push({url,options});return Response.json(url.includes('mojang')?{id:uuid,name:'Tester'}:url.includes('collections')?resources:data(member));};
 const body=await lookupProfiles('Tester','private-api-secret',fetcher,1234);
 assert.equal(calls.filter(c=>c.options.headers['API-Key']).length,1);
 assert.ok(calls.find(c=>c.options.headers['API-Key']).url.startsWith('https://api.hypixel.net/v2/skyblock/profiles?uuid='));
 assert.doesNotMatch(JSON.stringify(body),/private-api-secret/);
});
test('lookup requires private provisioning and rate limits before any API request',async()=>{
 const req=new Request('https://worker/v1/profiles?username=Tester',{headers:{'CF-Connecting-IP':'192.0.2.1'}});
 assert.equal((await handleProfileLookup(req,{})).status,503);
 let fetched=false;const env={HYPIXEL_API_KEY:'private',PROFILE_RATE_LIMITER:{limit:async()=>({success:false})}};
 assert.equal((await handleProfileLookup(req,env,Date.now(),async()=>{fetched=true;})).status,429);assert.equal(fetched,false);
 assert.equal((await handleProfileLookup(new Request('https://worker/v1/profiles?username=../secret'),env)).status,400);
});
test('upstream errors identify the failing service without exposing keys or response bodies',async()=>{
 const req=new Request('https://worker/v1/profiles?username=ErrorTester',{headers:{'CF-Connecting-IP':'192.0.2.1'}});
 const env={HYPIXEL_API_KEY:'private-api-secret',PROFILE_RATE_LIMITER:{limit:async()=>({success:true})}};
 for(const service of ['mojang','hypixel']) {
  const response=await handleProfileLookup(req,env,1234,async url=>{
   if(url.includes(service)||(service==='mojang'&&(url.includes('minecraftservices')||url.includes('playerdb'))))return new Response('private-api-secret sensitive upstream body',{status:403});
   return Response.json({id:uuid,name:'ErrorTester'});
  });
  assert.equal(response.status,502);
  const text=await response.text();assert.match(text,/HTTP 403/);
  assert.match(text,service==='mojang'?/PlayerDB username service/:/Hypixel rejected profile access/);
  assert.doesNotMatch(text,/private-api-secret|sensitive upstream body/);
 }
 const unreachable=await handleProfileLookup(req,env,1234,async()=>{throw new Error('private-api-secret');});
 assert.match((await unreachable.json()).error,/Minecraft username service could not be reached/);
});
test('PlayerDB fallback validates public identity when both official services reject cloud requests',async()=>{
 const calls=[];
 const fetcher=async(url,options)=>{
  calls.push({url,options});
  if(url.includes('mojang')||url.includes('minecraftservices'))return new Response('',{status:403});
  if(url.includes('playerdb'))return Response.json({success:true,code:'player.found',data:{player:{raw_id:uuid,username:'Tester'}}});
  return Response.json(url.includes('collections')?resources:data(member));
 };
 const result=await lookupProfiles('Tester','private-api-secret',fetcher);
 assert.equal(result.profiles[0].purse,3210000);
 assert.deepEqual(calls.find(c=>c.url.includes('playerdb')).options.headers,{});
 await assert.rejects(lookupProfiles('SomeoneElse','private-api-secret',fetcher),/could not confirm/);
 assert.equal(calls.filter(c=>c.options.headers['API-Key']).length,1,'An unconfirmed identity must not reach Hypixel');
});
test('official Minecraft Services fallback resolves Mojang 403 without leaking the Hypixel key',async()=>{
 const calls=[];
 const result=await lookupProfiles('Tester','private-api-secret',async(url,options)=>{
  calls.push({url,options});
  if(url.includes('api.mojang.com'))return new Response('Forbidden',{status:403});
  if(url.includes('api.minecraftservices.com'))return Response.json({id:uuid,name:'Tester'});
  return Response.json(url.includes('collections')?resources:data(member));
 });
 assert.equal(result.profiles[0].purse,3210000);
 assert.equal(calls.filter(c=>c.url.includes('minecraftservices')).length,1);
 for(const call of calls.filter(c=>!c.url.includes('hypixel')))assert.deepEqual(call.options.headers,{});
});
test('unknown names and username rate limits do not trigger fallback or Hypixel requests',async()=>{
 for(const status of [404,429]) {
  const calls=[];
  await assert.rejects(lookupProfiles('Tester','private',async url=>{calls.push(url);return new Response('',{status});}),status===404?/Username not found/:/rate limited/);
  assert.equal(calls.length,1);
 }
});

test('HotM and all Slayer XP boundaries are imported without turning missing data into zero',()=>{
 for(const [xp,expected] of [[0,1],[2999,1],[3000,2],[11999,2],[12000,3],[37000,4]]) {
  const [p]=summarizeProfiles(data({mining_core:{experience:xp}}),uuid,null,1234);
  assert.equal(p.stats.hotmTier,expected);
 }
 const [p]=summarizeProfiles(data({slayer_bosses:{zombie:{xp:200},spider:{xp:200},wolf:{xp:250},enderman:{xp:250},blaze:{xp:250},vampire:{xp:240}}}),uuid,null,1234);
 for(const name of ['Zombie','Spider','Wolf','Enderman','Blaze','Vampire'])assert.equal(p.stats.slayers[name],3);
 const [missing]=summarizeProfiles(data({slayer_bosses:{wolf:{}}}),uuid,null,1234);
 assert.equal(missing.stats.slayers.Wolf,undefined);assert.ok(missing.unknown.includes('Wolf Slayer'));
});
test('published Slayer reward claims limit usable recipe levels',()=>{
 const [p]=summarizeProfiles(data({slayer_bosses:{wolf:{xp:1000000,claimed_levels:{level_1:true,level_2:true,level_3:true,level_9:false}},zombie:{xp:200,claimed_levels:{}}}}),uuid,null,1234);
 assert.equal(p.stats.slayers.Wolf,3);assert.equal(p.stats.slayers.Zombie,0);
});
