import test from 'node:test';
import assert from 'node:assert/strict';
import { DashboardForecast } from './dashboard-forecast.mjs';
import { createCompanion } from './server.mjs';
const now=Date.now(),provenance={commit:'a'.repeat(40)};
const product=(bid=100,ask=130)=>({sell_summary:[{pricePerUnit:bid,amount:1000,orders:10}],buy_summary:[{pricePerUnit:ask,amount:1000,orders:10}],
 quick_status:{buyMovingWeek:100000,sellMovingWeek:100000,buyVolume:1000,sellVolume:1000,buyOrders:10,sellOrders:10}});
const request=()=>({protocol:'goofy-bazaar-shadow/1',requestId:'portfolio-test',market:{success:true,lastUpdated:now,products:{ENCHANTED_COAL:product(),ENCHANTED_QUARTZ:product(),ENCHANTMENT_OVERLOAD_4:product(),ENCHANTMENT_OVERLOAD_5:product(250,280)}},
 constraints:{mode:'BOTH',automaticSelection:true,availableCapital:1000,inventoryCapacity:8,maxRecommendations:50,maxHistoryAgeHours:48,taxPercentage:1.25,bookMinProfit:0,
 checkSeconds:20,bookCheckSeconds:180,clickDelayMs:350,bookSlots:2,generalSlots:2,
 general:{maxCoinsPerItem:1000,maxItemsPerOrder:16,minProfitPerBatch:0,minMarginPercentage:0,minWeeklyVolume:0},excludedProducts:[],configuredBookRoutes:[],configuredGeneralItems:[]}});
const account=()=>({account:{connected:true,name:'Test'},status:{state:'RUNNING',mode:'BOTH',purse:1000,reserve:0,pending:0,committed:0,capitalLimit:1000},
 books:{tasks:[]},general:{positions:[]},analysis:{comparison:{rankingCapital:1000,rankingInventoryCapacity:8},pipeline:{account:{bookSlots:2,generalSlots:2}}}});
const view=a=>({fresh:true,account:a,predictions:{marketAt:now,rows:[{}]}});
const history={asOf:now,stats:{},hold:{},names:{}};
const executions=()=>({active:[],calibrate(){},status:()=>({samples:0})});
test('full-budget search selects compatible routes within capital, inventory and engine slots without mutating requests',()=>{
 const f=new DashboardForecast({provenance,now:()=>now}),b=request(),a=account();f.acceptRequest(b);
 const before=JSON.stringify(b),result=f.view(view(a),history,executions());
 assert.ok(result.portfolio.rows.length>=2);assert.ok(result.portfolio.coinsPerHour>0);
 assert.ok(result.portfolio.allocated<=1000);assert.equal(result.portfolio.budget,1000);
 assert.ok(result.portfolio.inventoryUsed<=8);assert.equal(result.portfolio.allocated+result.portfolio.remaining,1000);
 const families=result.portfolio.rows.map(r=>r.kind==='BOOK'?r.inputId.replace(/_\d+$/,''):r.inputId);
 assert.equal(new Set(families).size,families.length);assert.ok(result.portfolio.rows.filter(r=>r.kind==='GENERAL').length<=2);
 assert.equal(JSON.stringify(b),before);assert.equal(result.live.coinsPerHour,0);
 // All cash committed is counted once, and active positions restore the hypothetical slot limit.
 a.status.purse=0;a.status.committed=1000;a.general.positions=[{item:'ENCHANTED_COAL',units:1,purchasePriceKnown:true,cost:100,stage:'BUY_ORDER'}];
 a.analysis.pipeline.account.generalSlots=1;const full=f.view(view(a),history,executions());assert.equal(full.portfolio.budget,1000);assert.ok(full.portfolio.rows.length>=2);
 a.status.purse=300;a.status.committed=500;a.status.pending=100;a.status.reserve=100;
 assert.equal(f.view(view(a),history,executions()).portfolio.budget,600);
 a.analysis.pipeline.account.bookSlots=0;a.analysis.pipeline.account.generalSlots=0;a.general.positions=[];
 const blocked=f.view(view(a),history,executions());assert.equal(blocked.portfolio.rows.length,0);assert.equal(blocked.portfolio.coinsPerHour,0);
});
test('active prediction uses owned quantities, known costs and actual listed offers; overdue timers lower its rate',()=>{
 const f=new DashboardForecast({provenance,now:()=>now}),b=request(),a=account(),e=executions();f.acceptRequest(b);
 a.general.positions=[{trade:'trade',item:'ENCHANTED_COAL',units:2,purchasePriceKnown:true,cost:200,stage:'SELL_ORDER',sellPrice:130}];
 const first=f.live(a,b,history,e,now);assert.equal(first.known,1);assert.equal(first.unknown,0);assert.ok(first.coinsPerHour>0);
 assert.equal(first.rows[0].profitPerBatch,130*2*0.9875-200);
 e.active=[{tradeId:'trade',startedAt:now-3600000,observedAt:now}];
 const delayed=f.live(a,b,history,e,now);assert.ok(delayed.coinsPerHour<first.coinsPerHour);assert.ok(delayed.rows[0].cycleSeconds>3600);
 a.general.positions[0].sellPrice=80;const loss=f.live(a,b,history,e,now);assert.ok(loss.coinsPerHour<0);
 a.general.positions[0].purchasePriceKnown=false;const unknown=f.live(a,b,history,e,now);assert.equal(unknown.coinsPerHour,null);assert.equal(unknown.unknown,1);
 a.general.positions[0].purchasePriceKnown=true;a.analysis.pipeline.account.mode='BOOKS';
 assert.equal(f.live(a,b,history,e,now).coinsPerHour,null);
 a.analysis.pipeline.account.mode='BOTH';a.general.paused=true;assert.equal(f.live(a,b,history,e,now).coinsPerHour,null);
 a.status.state='PAUSED';assert.equal(f.live(a,b,history,e,now).coinsPerHour,0);
});
test('shared GUI workload caps a multi-route estimate and beam search avoids the single-route greedy trap',()=>{
 const f=new DashboardForecast({provenance,now:()=>now}),b=request(),a=account();
 b.constraints.inventoryCapacity=8;b.constraints.bookSlots=0;a.analysis.pipeline.account.bookSlots=0;
 f.calculate=(_b,c)=>({rows:[{kind:'GENERAL',routeKey:'A',inputId:'A',outputId:'A',inputUnits:8,batch:8,capitalUsed:800,coinsPerHour:150,cycleSeconds:3600,configured:true},
  ...['B','C'].map(id=>({kind:'GENERAL',routeKey:id,inputId:id,outputId:id,inputUnits:4,batch:4,capitalUsed:400,coinsPerHour:100,cycleSeconds:3600,configured:true}))]});
 const result=f.portfolio(a,b,history,executions(),now);assert.deepEqual(result.rows.map(r=>r.inputId).sort(),['B','C']);assert.equal(result.coinsPerHour,200);
 f.calculate=(_b,c)=>({rows:['B','C'].map(id=>({kind:'GENERAL',routeKey:id,inputId:id,outputId:id,inputUnits:1,batch:1,capitalUsed:100,coinsPerHour:100000,cycleSeconds:0.1,configured:true}))});
 const busy=f.portfolio(a,b,history,executions(),now);assert.ok(busy.guiFactor<1);assert.ok(busy.coinsPerHour<200000);
});
test('stale inputs hide both forecasts and unsupported configuration limits remain unknown',()=>{
 let clock=now;const f=new DashboardForecast({provenance,now:()=>clock}),b=request(),a=account();f.acceptRequest(b);
 assert.equal(f.view({...view(a),fresh:false},history,executions()).portfolio.coinsPerHour,null);
 clock+=61000;assert.equal(f.view(view(a),history,executions()).live.coinsPerHour,null);
 clock=now;delete a.analysis.pipeline.account.generalSlots;assert.equal(f.view(view(a),history,executions()).portfolio.coinsPerHour,null);
});
test('real HTTP dashboard combines full-budget, active-trade and measured rates using companion-only inputs',async()=>{
 const server=createCompanion({collector:{history:()=>history,status:()=>({enabled:false})}});await new Promise(r=>server.listen(0,'127.0.0.1',r));const url=`http://127.0.0.1:${server.address().port}`;
 try {
  const b=request(),response=await fetch(`${url}/v1/recommendations`,{method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Analysis':'shadow-v1'},body:JSON.stringify(b)});
  assert.equal(response.status,200);const report=await response.json(),a=account();
  a.general.positions=[{trade:'live',item:'ENCHANTED_COAL',units:1,purchasePriceKnown:true,cost:100,stage:'BUY_ORDER'}];a.analysis.pipeline.account.generalSlots=1;
  const body={...a,protocol:'goofy-dashboard/1',sessionId:'portfolio-http',sentAt:Date.now(),inventory:[],analysis:{...a.analysis,report},profit:{profit:10000,settlements:1,incomplete:0,activeMillis:600000}};
  const sent=await fetch(`${url}/v1/account`,{method:'POST',headers:{'Content-Type':'application/json','X-Goofy-Dashboard':'local-v1'},body:JSON.stringify(body)});
  assert.equal(sent.status,200);const state=await(await fetch(`${url}/v1/dashboard`)).json();
  assert.ok(state.portfolio.rows.length>=2);assert.ok(state.live.coinsPerHour>0);assert.equal(state.measuredProfitPerHour,60000);
 }finally{await new Promise(r=>{server.close(r);server.closeAllConnections();});}
});
