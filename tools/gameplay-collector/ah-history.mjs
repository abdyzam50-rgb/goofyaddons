// Public market observations only. No accounts, contributors, auction IDs or trade receipts.
export const AH_DAY=86400000,AH_INTERVAL=15*60000;
export const AH_SCHEMA=[
 `CREATE TABLE IF NOT EXISTS ah_daily(item TEXT NOT NULL,source TEXT NOT NULL,day INTEGER NOT NULL,n INTEGER NOT NULL,sum_price REAL NOT NULL,min_price REAL NOT NULL,max_price REAL NOT NULL,last_price REAL NOT NULL,volume REAL,last_at INTEGER NOT NULL,last_bucket INTEGER NOT NULL,PRIMARY KEY(item,source,day))`,
 `CREATE INDEX IF NOT EXISTS ah_daily_day ON ah_daily(day)`,
 `CREATE TABLE IF NOT EXISTS ah_state(name TEXT PRIMARY KEY,state TEXT NOT NULL)`
];
const ID=/^[A-Z0-9_]{1,64}$/;
export function observations(view,now) {
 const rows=[];
 const add=(item,source,price,volume,at,ttl)=>{
  if(!ID.test(item??'')||!Number.isFinite(price)||price<0||(price===0&&source!=='SALES')||price>1e13||!Number.isFinite(at)||at>now+5000||now-at>ttl)return;
  rows.push({item,source,price,volume:Number.isFinite(volume)&&volume>=0?volume:null,at,day:Math.floor(at/AH_DAY)*AH_DAY,bucket:Math.floor(at/AH_INTERVAL)});
 };
 for(const r of (view.rows??[]).slice(0,6000)) {
  if(r.demandSource!=='coflnet-sales-summary')add(r.item,'DISCOVERY',r.median,r.volume,r.sourceAt,300000);
  if(r.quote?.item===r.item)add(r.item,'BIN',r.quote.lowest,null,r.quote.fetchedAt,60000);
  if(r.demandSource==='coflnet-sales-summary')add(r.item,'SALES',r.median,r.volume,r.demandAt,300000);
 }
 return rows;
}
export class AHHistory {
 constructor(db){this.db=db;this.ready=null;}
 async init(){this.ready??=this.db.batch(AH_SCHEMA.map(sql=>this.db.prepare(sql).bind())).catch(e=>{this.ready=null;throw e;});await this.ready;}
 async state(name){await this.init();const r=await this.db.prepare('SELECT state FROM ah_state WHERE name=?').bind(name).all();return r.results?.[0]?JSON.parse(r.results[0].state):{};}
 async setState(name,state){await this.init();await this.db.prepare('INSERT INTO ah_state(name,state) VALUES(?,?) ON CONFLICT(name) DO UPDATE SET state=excluded.state').bind(name,JSON.stringify(state)).run();}
 async record(view,now=Date.now()) {
  await this.init();const rows=observations(view,now);
  if(rows.length)await this.db.prepare(`INSERT INTO ah_daily(item,source,day,n,sum_price,min_price,max_price,last_price,volume,last_at,last_bucket)
   SELECT json_extract(value,'$.item'),json_extract(value,'$.source'),json_extract(value,'$.day'),1,json_extract(value,'$.price'),json_extract(value,'$.price'),json_extract(value,'$.price'),json_extract(value,'$.price'),json_extract(value,'$.volume'),json_extract(value,'$.at'),json_extract(value,'$.bucket') FROM json_each(?) WHERE 1
   ON CONFLICT(item,source,day) DO UPDATE SET
   n=n+CASE WHEN excluded.last_bucket>last_bucket THEN 1 ELSE 0 END,
   sum_price=sum_price+CASE WHEN excluded.last_bucket>last_bucket THEN excluded.last_price ELSE 0 END,
   min_price=min(min_price,excluded.min_price),max_price=max(max_price,excluded.max_price),
   last_price=CASE WHEN excluded.last_at>=last_at THEN excluded.last_price ELSE last_price END,
   volume=CASE WHEN excluded.last_at>=last_at THEN excluded.volume ELSE volume END,
   last_at=max(last_at,excluded.last_at),last_bucket=max(last_bucket,excluded.last_bucket) WHERE excluded.last_bucket>last_bucket`).bind(JSON.stringify(rows)).run();
  await this.db.prepare('DELETE FROM ah_daily WHERE day<?').bind(Math.floor((now-30*AH_DAY)/AH_DAY)*AH_DAY).run();
  return rows.length;
 }
 async dataset(now=Date.now()) {
  const saved=await this.state('snapshot');
  if(saved.protocol==='goofy-ah-history/1'&&saved.rows?.length&&saved.generatedAt<=now+5000&&now-saved.generatedAt<3600000)return saved;
  const data=await this.buildDataset(now);await this.setState('snapshot',data);return data;
 }
 async buildDataset(now=Date.now()) {
  await this.init();
  const data=await this.db.prepare(`SELECT a.item,a.source,sum(a.n) AS observations,sum(a.sum_price)/sum(a.n) AS average,min(a.min_price) AS minimum,max(a.max_price) AS maximum,max(a.last_at) AS lastAt,min(a.day) AS firstDay,
   (SELECT b.last_price FROM ah_daily b WHERE b.item=a.item AND b.source=a.source ORDER BY b.day DESC LIMIT 1) AS latest,
   (SELECT b.volume FROM ah_daily b WHERE b.item=a.item AND b.source=a.source ORDER BY b.day DESC LIMIT 1) AS volume
   FROM ah_daily a WHERE a.day>=? GROUP BY a.item,a.source ORDER BY a.item,a.source LIMIT 18000`).bind(Math.floor((now-30*AH_DAY)/AH_DAY)*AH_DAY).all();
  const grouped=new Map();
  for(const row of data.results??[]){const list=grouped.get(row.item)??[];list.push(row);grouped.set(row.item,list);}
  const rows=[...grouped].map(([item,sources])=>{
   const price=sources.find(r=>r.source==='BIN')??sources.find(r=>r.source==='SALES')??sources[0];
   const demand=sources.filter(r=>r.volume!==null).sort((a,b)=>b.lastAt-a.lastAt)[0];
   return {item,price:price.latest,average:price.average,minimum:price.minimum,maximum:price.maximum,observations:price.observations,firstDay:price.firstDay,lastAt:price.lastAt,priceSource:price.source,volume:demand?.volume??null,demandAt:demand?.lastAt??null};
  });
  return {protocol:'goofy-ah-history/1',generatedAt:now,retentionDays:30,rows};
 }
 async status(now=Date.now()) {
  const [collection,publishing,data]=await Promise.all([this.state('collector'),this.state('publisher'),this.dataset(now)]);
  return {protocol:'goofy-ah-status/1',checkedAt:now,intervalSeconds:AH_INTERVAL/1000,nextScheduledAt:(Math.floor(now/AH_INTERVAL)+1)*AH_INTERVAL,retentionDays:30,items:data.rows.length,observations:data.rows.reduce((n,r)=>n+r.observations,0),collection,publishing};
 }
}
