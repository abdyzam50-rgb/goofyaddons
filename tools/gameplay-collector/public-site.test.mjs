import test from 'node:test';
import assert from 'node:assert/strict';
import {handleRequest} from './worker.mjs';
test('public calculator redirects at the Worker root and serves all research page routes without login or contributor keys',async()=>{
 const seen=[],env={ASSETS:{fetch:async req=>{seen.push(new URL(req.url).pathname);return new Response('<html>Calculator</html>',{headers:{'Content-Type':'text/html'}});}}};
 const root=await handleRequest(new Request('https://example.workers.dev/'),env);assert.equal(root.status,302);assert.equal(root.headers.get('Location'),'https://example.workers.dev/calculator/');
 for(const path of ['/calculator/','/calculator/flips/book','/calculator/item/ENCHANTED_DIAMOND']) {
  const r=await handleRequest(new Request('https://example.workers.dev'+path),env);assert.equal(r.status,200);
  assert.match(r.headers.get('Content-Security-Policy'),/https:\/\/api.hypixel.net/);assert.equal(r.headers.get('Cache-Control'),'no-cache');
 }
 assert.deepEqual(seen,['/','/','/']);
});
test('Cloudflare index.html canonicalization cannot create a public-root redirect loop',async()=>{
 const env={ASSETS:{fetch:async req=>new URL(req.url).pathname==='/index.html'
  ?Response.redirect('https://site/',307):new Response('<html>Calculator</html>',{headers:{'Content-Type':'text/html'}})}};
 let url='https://site/';
 for(let i=0;i<3;i++) {
  const r=await handleRequest(new Request(url),env);
  if(r.status===200){assert.match(await r.text(),/Calculator/);assert.equal(i,1);return;}
  assert.ok(r.status>=300&&r.status<400);url=new URL(r.headers.get('Location'),url).href;
 }
 assert.fail('Public calculator remained in a redirect loop');
});
test('public site keeps static paths and missing assets distinct from page navigation',async()=>{
 const seen=[],env={ASSETS:{fetch:async req=>{seen.push(new URL(req.url).pathname);return new Response('Not found',{status:404});}}};
 assert.equal((await handleRequest(new Request('https://site/calculator/assets/missing.js'),env)).status,404);
 assert.equal((await handleRequest(new Request('https://site/calculator/data/items.json'),env)).status,404);
 assert.deepEqual(seen,['/assets/missing.js','/data/items.json']);
 assert.equal((await handleRequest(new Request('https://site/calculator/'),{})).status,503);
 assert.equal((await handleRequest(new Request('https://site/v1/profiles?username=Tester'),{})).status,503);
 // Asset hosting cannot allow anonymous telemetry uploads.
 assert.equal((await handleRequest(new Request('https://site/v1/gameplay',{method:'POST'}),env)).status,401);
});
