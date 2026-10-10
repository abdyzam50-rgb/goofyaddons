import test from 'node:test';
import assert from 'node:assert/strict';
import {parseCraftText,isMet,unmet,tradeRequirements,DEFAULT_PROFILE} from './website-src/requirements.ts';
import {readFileSync} from 'node:fs';
test('website Dungeon floor and mutation requirements preserve unknown facts',()=>{
 const p={...DEFAULT_PROFILE,ignoreRequirements:false,catacombsLevel:20,dungeonCompletions:{'Catacombs Floor 7':1,'Master Catacombs Floor 7':0}};
 const reqs=parseCraftText('Requires: Catacombs XX & The Catacombs Floor VII Completion & Master Mode The Catacombs Floor VII Completion & Mutation CHORUS_FRUIT inspected & Crop Analyzer Milestone V');
 assert.deepEqual(reqs.map(r=>r.type),['catacombs','dungeon_floor','dungeon_floor','mutation','analyzer']);
 assert.deepEqual(reqs.map(r=>isMet(r,p)),[true,true,false,null,null]);assert.equal(unmet(reqs,p).length,3);
 assert.equal(unmet(tradeRequirements('ESSENCE_DRAGON'),{...p,catacombsLevel:19}).length,1);
 assert.equal(unmet(tradeRequirements('ESSENCE_DRAGON'),p).length,0);
 assert.equal(unmet(tradeRequirements('CHORUS_FRUIT'),p).length,1);
});
test('all forty mutation gates and eight essence gates match mod and adapter resources',()=>{
 const gates=JSON.parse(readFileSync(new URL('./product-requirements.json',import.meta.url))).products;
 const mod=JSON.parse(readFileSync(new URL('../../integrations/goofyaddons/src/main/resources/goofyaddons/product-requirements.json',import.meta.url))).products;
 assert.deepEqual(gates,mod);assert.equal(Object.keys(gates).length,48);
 for(const id of Object.keys(gates))assert.equal(tradeRequirements(id).length,1,id);
 assert.deepEqual(tradeRequirements('BLAZE_POWDER'),[]);
});
