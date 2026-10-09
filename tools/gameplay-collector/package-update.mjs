// Self-contained Worker for Cloudflare's browser editor; no keys/configuration bundled.
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
export function standaloneSource() {
 const protocol=readFileSync(new URL('../bazaar-calc/community-protocol.mjs',import.meta.url),'utf8').replace(/^export /gm,'');
 const levels=readFileSync(new URL('./profile-levels.mjs',import.meta.url),'utf8').replace(/^export /gm,'');
 const profiles=readFileSync(new URL('./profile-lookup.mjs',import.meta.url),'utf8').replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,'');
 const worker=readFileSync(new URL('./worker.mjs',import.meta.url),'utf8').replace(/^import[^\n]+\n/gm,'');
 const publishing=readFileSync(new URL('./publishing-status.mjs',import.meta.url),'utf8').replace(/^export /gm,'');
 const publicMarket=readFileSync(new URL('./public-market.mjs',import.meta.url),'utf8').replace(/^export /gm,'');
 const craftMarket=readFileSync(new URL('../bazaar-calc/craft-market.mjs',import.meta.url),'utf8').replace(/^export /gm,'');
 const publicCrafts=readFileSync(new URL('./public-crafts.mjs',import.meta.url),'utf8').replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,'');
 const license=readFileSync(new URL('./NEU-LICENSE',import.meta.url),'utf8');
 return '// Goofy gameplay collector: approvals, private profile lookup, public market and publishing status.\n/*\n'+license+'\n*/\n'+protocol+'\n'+levels+'\n'+profiles+'\n'+publishing+'\n'+publicMarket+'\n'+craftMarket+'\n'+publicCrafts+'\n'+worker;
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
 const path=resolve(process.argv[2]??fileURLToPath(new URL('../../dist/gameplay-collector-update.mjs',import.meta.url)));
 mkdirSync(dirname(path),{recursive:true});writeFileSync(path,standaloneSource());
 console.log(`Standalone collector update: ${path}`);
}
