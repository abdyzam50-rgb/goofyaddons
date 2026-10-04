import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync,existsSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {resolveDataDirectory,prepareDataDirectory} from './data-paths.mjs';
test('platform defaults and explicit override are outside the installation',()=>{
 assert.equal(resolveDataDirectory({platform:'win32',env:{LOCALAPPDATA:'/user/local'},home:'/user'}),'/user/local/GoofyAddons/bazaar-calc');
 assert.equal(resolveDataDirectory({platform:'darwin',env:{},home:'/user'}),'/user/Library/Application Support/GoofyAddons/bazaar-calc');
 assert.equal(resolveDataDirectory({platform:'linux',env:{XDG_DATA_HOME:'/persistent'},home:'/user'}),'/persistent/GoofyAddons/bazaar-calc');
 assert.equal(resolveDataDirectory({platform:'linux',env:{XDG_DATA_HOME:'relative'},home:'/user'}),'/user/.local/share/GoofyAddons/bazaar-calc');
 assert.equal(resolveDataDirectory({env:{GOOFY_BAZAAR_DATA_DIR:'/custom'}}),'/custom');
 assert.throws(()=>resolveDataDirectory({env:{GOOFY_BAZAAR_DATA_DIR:'relative'}}),/absolute/);
});
test('migration preserves originals, never overwrites established history and survives replacing installation',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-migration-'));
 try {
  const legacy=join(dir,'installation','data'),target=join(dir,'persistent');mkdirSync(legacy,{recursive:true});mkdirSync(target);
  writeFileSync(join(legacy,'live-history.json.gz'),'old-market');writeFileSync(join(legacy,'execution-history.json'),'gameplay');writeFileSync(join(legacy,'companion.log'),'log');writeFileSync(join(legacy,'private-other.json'),'unrelated');
  writeFileSync(join(target,'live-history.json.gz'),'new-market');
  const warnings=[];prepareDataDirectory(target,{legacy,warn:s=>warnings.push(s)});
  assert.equal(readFileSync(join(target,'live-history.json.gz'),'utf8'),'new-market');assert.equal(readFileSync(join(target,'execution-history.json'),'utf8'),'gameplay');
  assert.equal(readFileSync(join(legacy,'execution-history.json'),'utf8'),'gameplay');assert.ok(!existsSync(join(target,'private-other.json')));
  prepareDataDirectory(target,{legacy,warn:s=>warnings.push(s)});assert.equal(warnings.length,2);
  rmSync(join(dir,'installation'),{recursive:true});prepareDataDirectory(target,{legacy,warn:()=>{}});assert.equal(readFileSync(join(target,'execution-history.json'),'utf8'),'gameplay');
 }finally {rmSync(dir,{recursive:true,force:true});}
});
test('portable override equal to legacy directory avoids self-copy and failed migration preserves source',()=>{
 const dir=mkdtempSync(join(tmpdir(),'goofy-migration-'));
 try{const legacy=join(dir,'old'),target=join(dir,'new');mkdirSync(legacy);writeFileSync(join(legacy,'execution-history.json'),'history');
 prepareDataDirectory(legacy,{legacy});assert.equal(readFileSync(join(legacy,'execution-history.json'),'utf8'),'history');
 mkdirSync(join(legacy,'companion.log'));assert.throws(()=>prepareDataDirectory(target,{legacy,warn:()=>{}}),/original preserved/);
 assert.equal(readFileSync(join(legacy,'execution-history.json'),'utf8'),'history');
 }finally{rmSync(dir,{recursive:true,force:true});}
});
