import test from 'node:test';import assert from 'node:assert/strict';
import {routeId,visibleRoutes,routeDisposition,workStages} from './dashboard/routes.mjs';
const book={kind:'BOOK',routeKey:'OVERLOAD:4:5',inputId:'ENCHANTMENT_OVERLOAD_4',outputId:'ENCHANTMENT_OVERLOAD_5',coinsPerHour:100,profitPerBatch:50,capitalUsed:200,cycleSeconds:100,configured:true};
const general={kind:'GENERAL',routeKey:'COAL',inputId:'COAL',outputId:'COAL',coinsPerHour:200,profitPerBatch:20,capitalUsed:50,cycleSeconds:20,configured:false};
test('search, engine, configured and favorite filters stay display-only; sorting follows the selected metric',()=>{
 const rows=[book,general];assert.equal(visibleRoutes(rows)[0],general);assert.equal(visibleRoutes(rows,{sort:'profit'})[0],book);
 assert.deepEqual(visibleRoutes(rows,{search:'overload',engine:'BOOK'}),[book]);assert.deepEqual(visibleRoutes(rows,{scope:'configured'}),[book]);assert.deepEqual(visibleRoutes(rows,{scope:'favorites',favorites:[routeId(general)]}),[general]);
 assert.equal(visibleRoutes(rows,{sort:'capital'})[0],general);assert.equal(visibleRoutes(rows,{sort:'cycle'})[0],general);assert.deepEqual(rows,[book,general]);
});
test('pipeline membership, deferred reasons and research scope remain distinct',()=>{
 assert.equal(routeDisposition(book,{next:[{route:book}]}),'In allocation preview');assert.equal(routeDisposition(book,{deferred:[{routeKey:book.routeKey,reason:'Inventory full'}]}),'Inventory full');assert.match(routeDisposition(general),/Research only/);assert.equal(routeDisposition(book,null,true),'Automatically eligible');
});
test('observed work stages count exact book and general states without implying fill percentages',()=>{
 const c=workStages({general:{positions:[{stage:'BUY_ORDER'},{stage:'INVENTORY'},{stage:'RECONCILE'}]},books:{tasks:[{state:'COMBINE'},{state:'STORE'},{state:'SELL_ORDER'},{state:'OUTBID'},{state:'VERIFY_ORDER'}]}});
 assert.deepEqual(c,{buy:2,combine:2,sell:2,review:2});assert.deepEqual(workStages(null),{buy:0,combine:0,sell:0,review:0});
});

test('storage grids retain exact slot positions and distinguish known empty slots from unavailable observations',async()=>{
 const {storageCells}=await import('./dashboard/routes.mjs');
 const region={currentSlots:Array.from({length:54},(_,i)=>i),previousSlots:[0,1],current:[{slot:4,item:{name:'Book',count:1}}],previous:[{slot:1,item:{name:'Old book',count:1}}]};
 const current=storageCells(region);assert.equal(current.length,54);assert.equal(current[4].item.name,'Book');assert.equal(current[3].observed,true);assert.equal(current[3].item,undefined);
 const previous=storageCells(region,'previous');assert.equal(previous.length,2);assert.equal(previous[1].item.name,'Old book');
 const legacy=storageCells({current:[{slot:4,item:{name:'Book',count:1}}]});assert.equal(legacy[4].observed,true);assert.equal(legacy[3].observed,false);
});


test('ranking routes show actual execution blockers and profit sorting uses the adjusted amount',()=>{
 const plan={status:'WAITING',reason:'Slots full',account:{mode:'BOTH',bookSlots:0,generalSlots:0,available:0,inventoryCapacity:0,excludedProducts:['ENCHANTMENT_OVERLOAD']}};
 assert.match(routeDisposition(book,plan,true),/already held/);
 plan.account.excludedProducts=[];assert.match(routeDisposition(book,plan,true),/position limit/);
 const reduced={...book,executionEvidence:{profitRealizationFactor:0.1}};
 assert.equal(visibleRoutes([reduced,general],{sort:'profit'})[0],general);
});
