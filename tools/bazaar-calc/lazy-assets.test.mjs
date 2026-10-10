import test from 'node:test';
import {execFileSync} from 'node:child_process';

test('calculator opens its port without reading website assets and loads requested files once',()=>{
 const source=`
 import fs from 'node:fs';import {syncBuiltinESMExports} from 'node:module';import assert from 'node:assert/strict';
 const original=fs.readFileSync;let reads=0;
 fs.readFileSync=(path,...args)=>{if(String(path).includes('/calculator/'))reads++;return original(path,...args);};syncBuiltinESMExports();
 const {createCompanion}=await import(${JSON.stringify(new URL('./server.mjs',import.meta.url).href)});
 assert.equal(reads,0,'Startup must not eagerly read thousands of website files');
 const server=createCompanion();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+server.address().port;
 try {
  assert.equal((await fetch(base+'/health')).status,200);assert.equal(reads,0);
  for(let i=0;i<2;i++){
   const r=await fetch(base+'/calculator/data/production-recipes.json');assert.equal(r.status,200);assert.ok((await r.json()).recipes.length>1000);
  }
  assert.equal(reads,1,'Requested static asset should be cached after its first read');
  assert.equal((await fetch(base+'/calculator/data/not-present.json')).status,404);
 }finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
 `;
 execFileSync(process.execPath,['--input-type=module','-e',source],{timeout:20000,stdio:'pipe'});
});
