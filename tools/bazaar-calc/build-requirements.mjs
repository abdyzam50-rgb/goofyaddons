// Rebuild trader gates from the bundled wiki-derived enchant rules and NEU recipe/lore data.
import {readFileSync,writeFileSync} from 'node:fs';
const resource=new URL('../../integrations/goofyaddons/src/main/resources/goofyaddons/',import.meta.url);
const rules=JSON.parse(readFileSync(new URL('./licenses/enchants.json',import.meta.url))).rules;
const books=Object.fromEntries(Object.entries(rules).map(([id,r])=>[id,{name:r.name,minimumEnchanting:r.enchanting_req,source:r.url}]));
writeFileSync(new URL('book-requirements.json',resource),JSON.stringify({schema:1,source:'Bundled bazaar-calc wiki-derived enchant rules; see tools/bazaar-calc/licenses',books},null,2)+'\n');
const file=new URL('production-recipes.json',resource),catalog=JSON.parse(readFileSync(file));
const rows=JSON.parse(readFileSync(new URL('./calculator/data/recipes.json',import.meta.url)));
const extra=new Map();
for(const r of rows.filter(r=>r.kind==='crafting'))for(const q of r.requirements) {
 const text=q.type==='collection'?`${q.name} ${q.tier}`:q.type==='slayer'?`${q.name} Slayer ${q.level}`:q.type==='skill'?`${q.name} ${q.level}`:q.type==='reputation'?`${q.faction} Reputation ${q.amount}`:q.text??'Unverified unlock';
 if(!extra.has(r.output_id))extra.set(r.output_id,new Set());extra.get(r.output_id).add(text);
}
const key=text=>text.replace(/\b[IVXLCDM]+$/i,value=>{
 const digits={I:1,V:5,X:10,L:50,C:100,D:500,M:1000};let n=0,previous=0;
 for(const ch of [...value.toUpperCase()].reverse()){const v=digits[ch];n+=v<previous?-v:v;previous=Math.max(previous,v);}return n;
}).toLowerCase().replace(/[^a-z0-9]/g,'');
for(const r of catalog.recipes.filter(r=>r.kind==='CRAFT')) {
 const text=r.requirement.replace(/^Requires: /,'');const parts=text?text.split(' & '):[];const seen=new Set(parts.map(key));
 for(const added of extra.get(r.outputId)??[])if(!seen.has(key(added))){parts.push(added);seen.add(key(added));}
 if(parts.length)r.requirement='Requires: '+parts.join(' & ');
}
catalog.requirementSource='NEU crafttext plus bundled bazaar-calc parsed item-lore requirements; unresolved requirements block execution.';
writeFileSync(file,JSON.stringify(catalog)+'\n');
