import test from 'node:test';
import assert from 'node:assert/strict';
import {profileFailure} from './profile-errors.mjs';
import {createCompanion} from './server.mjs';
test('profile failures preserve actionable categories without echoing raw bodies or secrets',()=>{
 const denied=profileFailure(502,{error:'Hypixel rejected profile access (HTTP 403). private-api-secret'});
 assert.equal(denied.failureCode,'HYPIXEL_FORBIDDEN');assert.match(denied.error,/HYPIXEL_API_KEY/);assert.doesNotMatch(JSON.stringify(denied),/private-api-secret/);
 assert.equal(profileFailure(503,{}).failureCode,'PROFILE_NOT_CONFIGURED');
 assert.equal(profileFailure(429,{}).failureCode,'PROFILE_RATE_LIMITED');
 assert.equal(profileFailure(502,{error:'Minecraft username service returned HTTP 403'}).failureCode,'USERNAME_SERVICE_UNAVAILABLE');
 assert.doesNotMatch(JSON.stringify(profileFailure(502,{error:'private-api-secret'})),/private-api-secret/);
});
test('companion forwards the Hypixel 403 cause to the mod without disclosing the upstream body',async t=>{
 const server=createCompanion({profileFetcher:async()=>Response.json({error:'Hypixel rejected profile access (HTTP 403). private-api-secret'},{status:502})});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>{server.close(r);server.closeAllConnections();}));
 const r=await fetch(`http://127.0.0.1:${server.address().port}/v1/profiles?username=Tester`);
 assert.equal(r.status,502);const body=await r.json();assert.equal(body.failureCode,'HYPIXEL_FORBIDDEN');assert.match(body.error,/HTTP 403/);assert.doesNotMatch(JSON.stringify(body),/private-api-secret/);
});
