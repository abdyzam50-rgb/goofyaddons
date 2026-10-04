// Account snapshots remain in memory and are never mixed into public market recordings.
import { positionEstimates, predictionReason, measuredProfitRate } from './dashboard-profit.mjs';
export const DASHBOARD_PROTOCOL = 'goofy-dashboard/1';
export class DashboardState {
  constructor(now = Date.now) { this.now=now;this.account=null;this.receivedAt=0; }
  accept(body) {
    const obj=v=>v && typeof v==='object' && !Array.isArray(v);
    if (!obj(body) || body.protocol !== DASHBOARD_PROTOCOL || typeof body.sessionId !== 'string' || body.sessionId.length>100
      || !Number.isFinite(body.sentAt) || body.sentAt<this.now()-15000 || body.sentAt>this.now()+5000
      || !obj(body.account) || typeof body.account.connected!=='boolean' || typeof body.account.name!=='string' || body.account.name.length>100
      || !obj(body.status) || !Array.isArray(body.inventory) || body.inventory.length>100
      || !['books','general','analysis','profit'].every(k=>obj(body[k]))) throw new Error('Invalid account snapshot');
    if(body.inventory.some(r=>!obj(r)||!Number.isInteger(r.slot)||r.slot<0||r.slot>100||!Number.isInteger(r.count)||r.count<1||typeof r.name!=='string'||r.name.length>1000)) throw new Error('Invalid inventory');
    for (const [key,owner] of [['tasks',body.books],['positions',body.general]]) {
      if (!Array.isArray(owner[key]) || owner[key].length>1000) throw new Error('Invalid tracked positions');
    }
    if (this.account?.sessionId===body.sessionId && body.sentAt<=this.account.sentAt) return false;
    this.account={protocol:body.protocol,sessionId:body.sessionId,sentAt:body.sentAt,account:body.account,status:body.status,
      inventory:body.inventory,books:body.books,general:body.general,analysis:body.analysis,profit:body.profit,profitError:body.profitError ?? null,executionError:body.executionError ?? null};
    if(!body.account.connected && this.previousConnected?.sessionId===body.sessionId) {
      this.account.account={...body.account,name:this.previousConnected.account.name};
      this.account.inventory=this.previousConnected.inventory;
    }
    if(body.account.connected)this.previousConnected=this.account;
    this.receivedAt=this.now();return true;
  }
  view(collector) {
    const fresh=this.account!==null && this.now()-this.receivedAt<=10000 && this.now()-this.account.sentAt<=10000;
    const report=this.account?.analysis?.report;
    const predictionsFresh=fresh && this.account.account.connected && Number.isFinite(report?.marketAt) && this.now()-report.marketAt<=60000 && report.marketAt<=this.now()+5000;
    const view={protocol:DASHBOARD_PROTOCOL,generatedAt:this.now(),receivedAt:this.receivedAt,fresh,account:this.account,
      predictions:predictionsFresh?report:null,collector:collector?.status() ?? {enabled:false},
      coverage:'Observed inventory and storage; mod-tracked orders and positions. Uninspected storage and unrelated orders are not discovered.'};
    view.positionProfit=fresh && this.account?.account.connected ? positionEstimates(this.account,collector) : {rows:[],total:null,known:0,unknown:0,reason:'Waiting for a fresh account snapshot'};
    view.measuredProfitPerHour=fresh && this.account?.account.connected && !this.account.profitError?measuredProfitRate(this.account.profit):null;
    view.predictionReason=predictionReason(view);
    const plan=this.account?.analysis?.pipeline;
    view.pipeline=fresh && this.account?.account.connected && plan && (plan.status!=='READY' ||
      predictionsFresh && Number.isFinite(plan.expiresAt) && this.now()<=plan.expiresAt && plan.expiresAt<=this.now()+65000) ? plan : null;
    return view;
  }
}
