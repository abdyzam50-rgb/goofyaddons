// Supported order-only route adapter. Predictions never authorize a Minecraft action.
import { assembleMarket, quotesFromBazaar, buyLeg, sellLeg, evaluate, DEFAULT_SETTINGS, DEFAULT_PROFILE,
  enchantRules, booksNeeded, combineXpCost, parseBookId, actionSeconds, curve, at, seriousFlags } from './engine.mjs';
import mutationCatalog from './mutation-products.json' with { type: 'json' };
import automaticCatalog from './automatic-products.json' with { type: 'json' };
export const PROTOCOL = 'goofy-bazaar-shadow/1';
const finite = (v, min, max, name) => {
  if (!Number.isFinite(v) || v < min || v > max) throw new Error(`Invalid ${name}`);
  return v;
};
const integer = (v, min, max, name) => {
  finite(v, min, max, name); if (!Number.isInteger(v)) throw new Error(`Invalid ${name}`); return v;
};
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);

export function recommend(body, history, provenance, now = Date.now(), executions = null, allowedRoutes = null) {
  if (!object(body) || body.protocol !== PROTOCOL || typeof body.requestId !== 'string' || body.requestId.length > 100)
    throw new Error('Invalid protocol/request');
  const root = body.market, c = body.constraints;
  if (!object(root) || root.success !== true || !object(root.products) || !object(c)) throw new Error('Invalid market/constraints');
  if(c.automaticSelection!==undefined && typeof c.automaticSelection!=='boolean')throw new Error('Invalid automatic selection');
  const skills=c.accountSkills??{};
  if(!object(skills)||Object.values(skills).some(v=>!Number.isInteger(v)||v<0||v>60))throw new Error('Invalid account skills');
  const marketAt = finite(root.lastUpdated, now - 60000, now + 5000, 'market timestamp');
  if (!['BOOKS', 'GENERAL', 'BOTH'].includes(c.mode)) throw new Error('Invalid trading mode');
  const coins = finite(c.availableCapital, 0, 1e13, 'capital');
  const capacity = integer(c.inventoryCapacity, 0, 4096, 'inventory capacity');
  const limit = integer(c.maxRecommendations, 1, 50, 'recommendation limit');
  const maxHistoryAgeHours = integer(c.maxHistoryAgeHours, 1, 168, 'history age');
  const tax = finite(c.taxPercentage, 0, 99.999, 'tax') / 100;
  const bookMinProfit = finite(c.bookMinProfit, 0, 1e13, 'book profit');
  const check = finite(c.checkSeconds, 10, 14400, 'check interval');
  const bookCheck = finite(c.bookCheckSeconds, 30, 14400, 'book check interval');
  const click = finite(c.clickDelayMs, 51, 60000, 'click delay');
  const bookSlots = integer(c.bookSlots, 0, 10, 'book slots'), generalSlots = integer(c.generalSlots, 0, 10, 'general slots');
  const g = c.general;
  if (!object(g)) throw new Error('Missing general limits');
  finite(g.maxCoinsPerItem, 0.01, 1e13, 'item budget'); integer(g.maxItemsPerOrder, 1, 4096, 'item order quantity');
  finite(g.minProfitPerBatch, 0, 1e13, 'batch profit'); finite(g.minMarginPercentage, 0, 100, 'margin');
  finite(g.minWeeklyVolume, 0, 1e15, 'weekly volume');
  for (const key of ['excludedProducts', 'configuredBookRoutes', 'configuredGeneralItems'])
    if (!Array.isArray(c[key]) || c[key].length > 4096 || c[key].some(x => typeof x !== 'string' || x.length > 160))
      throw new Error(`Invalid ${key}`);
  const excluded = new Set([...c.excludedProducts,...mutationCatalog.products]), configuredBooks = new Set(c.configuredBookRoutes), configuredGeneral = new Set(c.configuredGeneralItems);
  const dataAt = Number.isFinite(history?.asOf) ? history.asOf : 0;
  const historyUsed = dataAt > 0 && dataAt <= now + 5000 && now - dataAt <= maxHistoryAgeHours * 3600000;
  const historyStatus = historyUsed ? 'FRESH' : dataAt > 0 ? 'STALE' : 'MISSING';
  const counts = { evaluated: 0, warnings: 0, unsupported: 0, filtered: 0, malformedProducts: 0 };
  // A malformed product must not suppress all the other markets. Ignore AH/NPC/crafting entirely.
  const products = {};
  for (const [id, p] of Object.entries(root.products)) {
    const levelsValid = xs => Array.isArray(xs) && xs.every(x => object(x) && Number.isFinite(x.pricePerUnit) && x.pricePerUnit > 0
      && Number.isFinite(x.amount) && x.amount >= 0 && Number.isFinite(x.orders) && x.orders >= 0);
    if (!/^[A-Z0-9_]+$/.test(id) || !object(p) || !object(p.quick_status) || !levelsValid(p.sell_summary) || !levelsValid(p.buy_summary)
      || !['buyMovingWeek','sellMovingWeek'].every(k => Number.isFinite(p.quick_status[k]) && p.quick_status[k] >= 0)) {
      counts.malformedProducts++; continue;
    }
    products[id] = p;
  }
  const market = assembleMarket({ quotes: quotesFromBazaar({ lastUpdated: marketAt, products }),
    stats: new Map(Object.entries(historyUsed ? history.stats ?? {} : {})),
    hold: new Map(Object.entries(historyUsed ? history.hold ?? {} : {})),
    names: new Map(Object.entries(history?.names ?? {})), ah: new Map(), now });
  const volumes=new Map();
  for(const m of market.values()) {
    const stat=historyUsed?history.stats?.[m.id]:null;
    const valid=stat && Number.isFinite(stat.observedAt) && stat.observedAt>=now-60000 && stat.observedAt<=now+5000
      && Number.isFinite(stat.recentTradeHours) && stat.recentTradeHours>=1 && stat.recentTradeHours<=24
      && [stat.recentBuyFlowH,stat.recentSellFlowH].every(v=>Number.isFinite(v)&&v>=0);
    const hours=valid?stat.recentTradeHours:0;
    const buyWeek=m.isellWeek/168,sellWeek=m.ibuyWeek/168;
    // A two-hour weekly prior smooths sparse observations but reacts to today's quieter market.
    const buy=valid?Math.min(buyWeek,(stat.recentBuyFlowH*hours+buyWeek*2)/(hours+2)):buyWeek;
    const sell=valid?Math.min(sellWeek,(stat.recentSellFlowH*hours+sellWeek*2)/(hours+2)):sellWeek;
    volumes.set(m.id,{buyWeek,sellWeek,buy,sell,hours,recentBuy:valid?stat.recentBuyFlowH:0,recentSell:valid?stat.recentSellFlowH:0});
    m.isellWeek=buy*168;m.ibuyWeek=sell*168;
  }
  const baseSettings = { ...DEFAULT_SETTINGS, coins, clickDelayMs: click, includeFlagged: false };
  const rows = [];
  const eligible = m => m && m.bid > 0 && m.ask > 0 && !excluded.has(m.id) && !excluded.has(parseBookId(m.id)?.enchant);
  const add = (kind, source, target, n, level = 0, sellLevel = 0) => {
    if (!eligible(source) || !eligible(target)) return;
    if(kind==='BOOK') {
      const minimum=enchantRules()[parseBookId(source.id)?.enchant]?.enchanting_req;
      if(!Number.isInteger(minimum) || minimum>0 && (!Number.isInteger(skills.enchanting) || skills.enchanting<minimum)){counts.filtered++;return;}
    }
    const selectionKey=kind==='BOOK'?`${parseBookId(source.id).enchant}:${level}:${sellLevel}`:source.id;
    if(allowedRoutes && !allowedRoutes.has(`${kind}:${selectionKey}`))return;
    if(c.automaticSelection && (kind==='GENERAL' ? !automaticCatalog.products[source.id] :
      !automaticCatalog.books[parseBookId(source.id)?.enchant]?.routes.some(([a,b])=>a===level&&b===sellLevel))) {
      counts.unsupported++;return;
    }
    if (seriousFlags(source).length || seriousFlags(target).length) { counts.warnings++; return; }
    counts.evaluated++;
    const budget = kind === 'GENERAL' ? Math.min(coins, g.maxCoinsPerItem) : coins;
    const weekly = Math.min(source.isellWeek / n, target.ibuyWeek);
    const maxBatch = kind === 'BOOK' ? 1 : Math.min(capacity, g.maxItemsPerOrder, Math.floor(weekly / 168), Math.floor(budget / (source.bid + 0.1)));
    if (!coins || n > capacity || maxBatch < 1 || (kind === 'GENERAL' && weekly < g.minWeeklyVolume)) { counts.filtered++; return; }
    const settings = { ...baseSettings, checkIntervalMin: (kind === 'BOOK' ? bookCheck : check) / 60 };
    const ctx = { market, recipes: new Map(), settings, profile: { ...DEFAULT_PROFILE, ignoreRequirements: false } };
    const buy = buyLeg(ctx, source, n, 'order'), sell = sellLeg(ctx, target, 'offer');
    buy.maxQty = n * maxBatch; sell.maxQty = maxBatch;
    // Keep the user's exact tax (which need not equal one of the calculator's three perk tiers).
    sell.netPrice = sell.grossPrice * (1 - tax);
    const operations = n - 1;
    const route = { kind: kind === 'BOOK' ? 'book' : 'bazaar', key: `${kind}:${source.id}:${target.id}`, title: `${source.name} → ${target.name}`,
      outputId: target.id, buys: [buy], sell,
      steps: kind === 'BOOK' ? [{ type: 'combine', label: `Combine ${n} books`, opsPerUnit: operations, outputPerOp: 1, requirements: [] }] : [],
      requirements: [], flags: [], notes: [] };
    // Upstream can reserve buy and sell lots together. This trader completes one
    // buy -> sell cycle; its peak commitment is the input batch, not both lots.
    // Give the optimizer room for both modeled legs, then enforce actual input cash.
    const o = evaluate(route, settings, ctx.profile, budget * 2);
    const profitPerOutput = sell.netPrice - o.costPerUnit;
    const batch = o.batch;
    const sequentialCapital=o.costPerUnit*batch;
    if (!(profitPerOutput > 0) || o.unmet.length || o.ordersUsed > 2 || batch > maxBatch || sequentialCapital > budget + 1e-6
      || (kind === 'BOOK' ? profitPerOutput < bookMinProfit : profitPerOutput * batch < g.minProfitPerBatch || profitPerOutput / o.costPerUnit * 100 < g.minMarginPercentage)) {
      counts.filtered++; return;
    }
    const buyRate = at(curve(buy.fill, settings.checkIntervalMin), n * batch).unitsH;
    const sellRate = at(curve(sell.fill, settings.checkIntervalMin), batch).unitsH;
    // Current traders complete each position before starting its next buy. Cap the upstream
    // pipelined rate by the sum of the two sequential fill times and anvil work.
    const cycleHours = n * batch / buyRate + batch / sellRate + operations * batch * actionSeconds('anvil_combine', settings) / 3600;
    const outputsPerHour = Math.min(o.unitsH, batch / cycleHours, source.isellWeek/n/168, target.ibuyWeek/168);
    if (!(outputsPerHour > 0) || !Number.isFinite(outputsPerHour)) { counts.filtered++; return; }
    const measured = historyUsed && buy.fill.basis === 'measured' && sell.fill.basis === 'measured';
    const routeKey = kind === 'BOOK' ? `${parseBookId(source.id).enchant}:${level}:${sellLevel}` : source.id;
    rows.push({ kind, routeKey, inputId: source.id, outputId: target.id, inputName: source.name, outputName: target.name,
      level, sellLevel, inputsPerOutput: n, batch, inputUnits: n * batch,
      buyPrice: buy.price, sellPrice: sell.grossPrice, costPerOutput: o.costPerUnit, profitPerOutput,
      profitPerBatch: profitPerOutput * batch, capitalUsed: sequentialCapital,
      outputsPerHour, coinsPerHour: outputsPerHour * profitPerOutput, cycleSeconds: batch / outputsPerHour * 3600,
      confidence: measured ? 'MEASURED' : 'ESTIMATED', buyBasis: buy.fill.basis, sellBasis: sell.fill.basis,
      configured: c.automaticSelection===true || (kind === 'BOOK' ? configuredBooks.has(routeKey) : configuredGeneral.has(routeKey)),
      volumeEvidence:{inputWeeklyAveragePerDay:volumes.get(source.id).buyWeek*24,outputWeeklyAveragePerDay:volumes.get(target.id).sellWeek*24,
        inputRecentPerDay:volumes.get(source.id).recentBuy*24,outputRecentPerDay:volumes.get(target.id).recentSell*24,
        inputObservationHours:volumes.get(source.id).hours,outputObservationHours:volumes.get(target.id).hours,
        inputEffectivePerDay:volumes.get(source.id).buy*24,outputEffectivePerDay:volumes.get(target.id).sell*24},
      maxOutputsPerHour:Math.min(volumes.get(source.id).buy/n,volumes.get(target.id).sell),
      limitedBy: o.limitedBy, priceBasis: sell.priceBasis ?? 'current offer',
      assumptions: ['market-wide observations, not guaranteed personal fills', 'sequential position cycle', 'GUI overhead/lag estimates'] });
  };
  if (generalSlots > 0 && c.mode !== 'BOOKS') for (const m of market.values()) {
    if (!m.id.startsWith('ENCHANTMENT_')) add('GENERAL', m, m, 1);
  }
  if (bookSlots > 0 && c.mode !== 'GENERAL') for (const rule of Object.values(enchantRules())) {
    if (rule.combine_status !== 'combinable' || !rule.combine_cap) continue;
    const cap = Math.min(10, rule.combine_cap);
    for (let from = 1; from < cap; from++) for (let to = from + 1; to <= cap; to++) {
      const n = booksNeeded(rule, from, to);
      if (!n) continue;
      if (Array.from({ length: to - from }, (_, i) => combineXpCost(rule, from + i)).some(x => x > 0)) { counts.unsupported++; continue; }
      add('BOOK', market.get(`${rule.id}_${from}`), market.get(`${rule.id}_${to}`), n, from, to);
    }
  }
  for(const row of rows)executions?.calibrate(row);
  rows.sort((a, b) => b.coinsPerHour - a.coinsPerHour || a.capitalUsed - b.capitalUsed || a.routeKey.localeCompare(b.routeKey));
  const report={ protocol: PROTOCOL, requestId: body.requestId, marketAt, dataAt, generatedAt: now,
    historyUsed, historyStatus, upstreamCommit: provenance.commit, counts, total: rows.length, rows: rows.slice(0, limit) };
  if(body.rankingConstraints!==undefined) {
    report.rankingReport=recommend({...body,constraints:body.rankingConstraints,rankingConstraints:undefined},history,provenance,now,executions);
  }
  return report;
}
