// Public aggregate state only: no contributor IDs, tokens, samples or player details.
export const INTERVAL=15*60*1000;
export class PublishingStatus {
 constructor(db){this.db=db;}
 async init(){await this.db.prepare('CREATE TABLE IF NOT EXISTS publisher_status (id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL)').bind().run();}
 async read(){await this.init();const rows=await this.db.prepare('SELECT state FROM publisher_status WHERE id=1').all();return rows.results?.[0]?JSON.parse(rows.results[0].state):{};}
 async write(state){await this.init();await this.db.prepare('INSERT INTO publisher_status(id,state) VALUES(1,?) ON CONFLICT(id) DO UPDATE SET state=excluded.state').bind(JSON.stringify(state)).run();}
 async start(now){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,result:'RUNNING',error:null});}
 async finish(now,result){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,lastSuccessAt:now,result:result.changed?'COMMITTED':'UNCHANGED',error:null,publishedSamples:result.samples,...(result.changed?{lastCommitAt:now,commitSha:result.commitSha??null,commitUrl:result.commitUrl??null}:{})});}
 async fail(now,error){const previous=await this.read();await this.write({...previous,lastAttemptAt:now,result:'FAILED',error:/HTTP \d{3}/.exec(error.message)?.[0]??'Publisher unavailable; check Worker logs and GitHub configuration'});}
 async public(now){const state=await this.read();const rows=await this.db.prepare('SELECT count(*) AS storedSamples, max(received_at) AS lastUploadAt, sum(CASE WHEN received_at>? THEN 1 ELSE 0 END) AS pendingSamples FROM samples WHERE completed_at>=?').bind(state.lastSuccessAt??0,now-7*86400000).all();return {...state,...rows.results?.[0],pendingSamples:rows.results?.[0]?.pendingSamples??0,checkedAt:now,intervalSeconds:INTERVAL/1000,nextScheduledAt:(Math.floor(now/INTERVAL)+1)*INTERVAL};}
}
