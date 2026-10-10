// Maintained reference pages for the hosted calculator and bundled companion.
import { useQuery } from "@tanstack/react-query";
import { ACTIONS, BAZAAR, BAZAAR_SOURCES, NOTICE, actionSeconds, msUntilLimitReset, orderSlots, taxRate } from "@bc/shared";
import type { Manifest } from "../static/backend";
import { Icon } from "../components/Icon";
import { api, ago, coins, num, utc } from "../lib";
import { useApp } from "../state";
import { CommunityStatus } from "../components/CommunityStatus";
const repo="https://github.com/abdyzam50-rgb/goofyaddons";
function Cadence(){return <><h2>Refresh and retention</h2><div className="tablewrap"><table><thead><tr><th className="l">Data</th><th className="l">Refresh / retention</th></tr></thead><tbody>{[
["Live Bazaar", "Refreshes about every 20 seconds while visible; recommendations require prices no older than 60 seconds."],
["Live AH BIN and demand", "BIN cache: 60 seconds. Provider sales summary: 5 minutes. Focused craft searches request fresh quotes; scheduled collection rotates through a bounded selection every 15 minutes."],
["Persistent AH history", "30-day daily aggregates; at most one observation per item/source per 15-minute bucket. Shared summaries update about hourly. Research references must be within seven days and require fresh BIN verification before trading."],
["Shared gameplay evidence", "Seven-day retention; bounded anonymized samples published every 15 minutes when changed."],
["Bundled charts and timing reference", "Fixed at the displayed build date. GitHub gameplay/AH commits do not rebuild these files."],
["Account unlocks and purse", "An API snapshot, not a live balance. Refresh after trading or unlocking requirements."]
].map(([a,b])=><tr key={a}><td className="l"><b>{a}</b></td><td className="l" style={{whiteSpace:"normal"}}>{b}</td></tr>)}</tbody></table></div></>}
export function Timing() {
  const { settings: s } = useApp();
  const m = useQuery({ queryKey: ["timing", s.pingMs, s.clickDelayMs, s.typingMs], queryFn: () => api<{ measuredByContributors: { action: string; n: number; median_ms: number; avg_ping: number }[] }>(`/api/v1/rules/timing?ping=${s.pingMs}&click=${s.clickDelayMs}&typing=${s.typingMs}`) });
  const reset = msUntilLimitReset();
  const max = Math.max(...Object.keys(ACTIONS).map(k => actionSeconds(k, s)));
  return (
    <>
      <div className="pagehead"><div><span className="eyebrow">Reference</span><h1>Timing &amp; limits</h1>
        <p className="lede">Calculator timing model (not a measured guarantee): every menu step is your ping ({s.pingMs} ms) plus a 50 ms server tick plus your click delay ({s.clickDelayMs} ms); commands and amount signs use your typing time ({s.typingMs} ms).</p></div></div>
      <Cadence />
      <p>The model below uses your calculator settings. The mod batches crafting actions and verifies menu transitions separately; lag and retries change actual completion time. Trade calibration does not rewrite this bundled timing table.</p>
      <div className="tablewrap"><table><thead><tr><th className="l">Action</th><th>Steps</th><th className="l" style={{ width: "34%" }}>Time</th><th className="l">Menus you go through</th></tr></thead><tbody>
        {Object.entries(ACTIONS).map(([k, a]) => { const sec = actionSeconds(k, s); return (
          <tr key={k}><td className="l"><b>{a.label}</b></td><td className="n">{a.steps.length}</td>
            <td className="l"><div className="row" style={{ flexWrap: "nowrap" }}><div className="meter" style={{ flex: 1 }}><div className="track"><div className="fill" style={{ width: `${(sec / max) * 100}%` }} /></div></div><b className="num">{sec.toFixed(1)} s</b></div></td>
            <td className="l small muted" style={{ whiteSpace: "normal" }}>{a.menus.join(" → ")}</td></tr>); })}
      </tbody></table></div>
      {m.data && m.data.measuredByContributors.length > 0 && <><h2>Bundled contributor timing reference</h2><div className="tablewrap"><table><tbody>
        {m.data.measuredByContributors.map(x => <tr key={x.action}><td className="l">{x.action}</td><td className="n">{num(x.median_ms)} ms median</td><td className="n muted">{x.n} samples · ping {num(x.avg_ping)} ms</td></tr>)}</tbody></table></div></>}

      <div className="grid cols-2" style={{ marginTop: 18 }}>
        <section className="card pad stack">
          <h2 style={{ margin: 0 }}>Daily bazaar limit assumption</h2><p>Community-derived rules; confirm your in-game limit. This public page does not track your existing orders or daily spend.</p>
          <div><span className="coin" style={{ fontSize: 26, fontWeight: 600 }}>{coins(s.dailyLimit)}</span> <span className="muted">per day · resets 00:00 UTC, in {num(reset / 3600_000, 1)} h</span></div>
          <ul className="small" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.7 }}>
            <li>Counts instant buys, instant sells (before tax) and the full value of every buy order and sell offer <b>when you create it</b>.</li>
            <li>Does not count orders filling, claiming, or flipping a filled order.</li>
            <li><b>Relisting counts again.</b> The calculators include this.</li>
            <li>One action counts at most {num(BAZAAR.dailyLimitPerActionCap)} coins.</li>
          </ul>
          <p className="small muted" style={{ margin: 0, overflowWrap: "anywhere" }}>{BAZAAR_SOURCES.dailyLimit}</p>
        </section>
        <section className="card pad stack">
          <h2 style={{ margin: 0 }}>Bazaar rules</h2>
          <div style={{ overflowX: "auto" }}><table className="small"><tbody>
            <tr><td className="l">Orders at once</td><td className="n">{[0, 1, 2].map(orderSlots).join(" / ")}</td><td className="l muted">Bazaar Flipper 0 / 1 / 2</td></tr>
            <tr><td className="l">Tax on sales</td><td className="n">{[0, 1, 2].map(l => `${(taxRate(l) * 100).toFixed(3).replace(/0+$/, "")}%`).join(" / ")}</td><td className="l muted">Bazaar Flipper 0 / 1 / 2</td></tr>
            <tr><td className="l">Units per order</td><td className="n">{num(BAZAAR.maxUnitsPerOrder)}</td><td className="l muted">{BAZAAR.maxUnitsPerOrderUnstackable} for unstackable items</td></tr>
            <tr><td className="l">On sell offer at once</td><td className="n">{coins(BAZAAR.maxSellOfferValue)}</td><td className="l muted">coins of items</td></tr>
            <tr><td className="l">Orders expire after</td><td className="n">{BAZAAR.orderExpiryDays} days</td><td className="l muted"></td></tr>
          </tbody></table></div>
          <p className="small muted" style={{ margin: 0, overflowWrap: "anywhere" }}>{BAZAAR_SOURCES.rules}</p>
        </section>
      </div>
    </>
  );
}

export function StatusStatic() {
  const q = useQuery({ queryKey: ["status"], queryFn: () => api<{ manifest: Manifest; dataAt: number | null; statsAt: number; statsUsed: boolean | null }>("/api/v1/status"), refetchInterval: 60_000 });
  const m = q.data?.manifest;
  return (
    <>
      <div className="pagehead"><div><span className="eyebrow">Reference</span><h1>Data status</h1><p className="lede">Live Bazaar prices refresh from Hypixel through the collector. Persistent AH collection and gameplay publishing run independently. Bundled Bazaar charts remain a reference snapshot until the website is rebuilt and deployed.</p></div></div>
      {q.error && <div className="note"><Icon name="warn" />{(q.error as Error).message}</div>}
      <CommunityStatus />
      {m && q.data && <>
        <div className="grid cols-3">
          <div className="card tile"><div className="label">Live prices</div><div className="value">{q.data.dataAt ? new Date(q.data.dataAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "–"}</div><div className="sub">live Hypixel prices; refresh about every 20 seconds while visible</div></div>
          <div className="card tile"><div className="label">Bundled Bazaar history up to</div><div className="value">{ago(m.asOf)}</div><div className="sub">{utc(m.asOf)}{q.data.statsUsed === false ? " · too old, not used" : ""}</div></div>
          <div className="card tile"><div className="label">Site built</div><div className="value">{ago(m.builtAt)}</div><div className="sub">bundled history snapshot; changes when the website is rebuilt and deployed · recipes: NEU {m.recipesVersion?.slice(0, 7) ?? "–"}</div></div>
        </div>
        <h2>Bundled Bazaar snapshot coverage</h2>
        <div className="tablewrap"><table><thead><tr><th className="l">Day (UTC)</th><th>Polls</th><th className="l" style={{ width: "50%" }}>Share of the day</th></tr></thead><tbody>
          {m.daily.slice(-30).reverse().map(d => <tr key={d.day}><td className="l">{d.day}</td><td className="n">{num(d.polls)}</td>
            <td className="l"><div className="meter"><div className="track"><div className="fill" style={{ width: `${Math.min(100, (d.polls / 4320) * 100)}%` }} /></div></div></td></tr>)}
        </tbody></table></div>
        <h2>Bundled reference files</h2>
        <p className="small muted">{(m.fileCount ?? m.files.length) > 200 ? `The newest 200 of ${num(m.fileCount ?? m.files.length)} files.` : `${num(m.files.length)} files.`}</p>
        <div className="tablewrap"><table><thead><tr><th className="l">File</th><th>From</th><th>To</th><th>Hours used</th><th className="l">Notes</th></tr></thead><tbody>
          {[...m.files].reverse().slice(0, 200).map(f => <tr key={f.label}><td className="l mono small">{f.label}</td><td className="n">{utc(f.from)}</td><td className="n">{utc(f.to)}</td><td className="n">{f.picked}/{f.hours}</td>
            <td className="l small muted" style={{ whiteSpace: "normal" }}>{f.source === "wayback" ? "Internet Archive copy" : f.kind}{f.warnings.length ? ` · ${f.warnings.join("; ")}` : ""}</td></tr>)}
          {m.rejected.map(r => <tr key={r.label}><td className="l mono small">{r.label}</td><td colSpan={3} className="l"><span className="pill crit">skipped</span></td><td className="l small" style={{ whiteSpace: "normal" }}>{r.error}</td></tr>)}
        </tbody></table></div>
      </>}
    </>
  );
}


export function ContributeStatic(){return <>
<div className="pagehead"><div><span className="eyebrow">Community</span><h1>Contribute data</h1><p className="lede">The hosted collector gathers public market prices automatically. Approved mod users can also contribute anonymized evidence from completed trades.</p></div></div>
<section className="card pad stack"><h2>From the mod</h2><ol><li>Ask the collector owner for a private contributor key.</li><li>Open the mod settings → Shared gameplay learning. Set the upload service to <code>https://goofy-gameplay-collector.abdyzam50.workers.dev</code> and save your key.</li><li>Enable gameplay sharing and use the trader. Eligible settled trade samples upload automatically; check Sync status and Trade feed.</li></ol><p>Contributors do not need Cloudflare accounts or GitHub write access. Reading the calculator, market history and shared dataset requires no contributor key.</p></section>
<h2>What is shared</h2><section className="card pad stack"><p>Validated route evidence supports shared calibration across items with comparable volumes, then refines predictions using item-specific evidence. Published samples exclude player names, chat, keys and exact profit receipts. Account lookups are never added to the gameplay dataset.</p><p>The publisher caps the seven-day dataset at 2,000 samples with per-route and contributor limits. An upload does not guarantee a new commit: unchanged or ineligible evidence may produce none.</p><a href="/calculator/status">Check accepted uploads and GitHub publishing</a></section>
<h2>Collector owners</h2><p>Enroll contributors privately and configure hashed allowlists in Worker secrets. Keep owner tokens and raw contributor keys out of the public repository. Revocation is managed by the owner, not by a public sign-up form.</p><a href={`${repo}/blob/codex/craft-prerequisites-full-stacks/docs/PUBLIC-WEBSITE.md`}>Setup and publishing documentation</a>
</>}
export function ApiDocsStatic(){return <>
<div className="pagehead"><div><span className="eyebrow">Reference</span><h1>Data files</h1><p className="lede">Live data comes through the hosted Worker. Calculations run in the browser with your budget and unlocks. Bundled JSON files supply catalog and historical reference data.</p></div></div>
<h2>Hosted endpoints</h2><div className="tablewrap"><table><thead><tr><th className="l">Path</th><th className="l">Purpose / access</th></tr></thead><tbody>{[
["/v1/market","GET · public live Hypixel Bazaar prices."],
["/v1/items","GET · public Hypixel item metadata, cached for one hour."],
["/v1/crafts/market","GET · live Coflnet craft discovery, BIN quotes and provider sales summaries; bounded catalog collection."],
["/v1/crafts/history","GET · public retained AH aggregates; goofy-ah-history/1."],
["/v1/crafts/status","GET · AH collection coverage and independent publishing status."],
["/v1/publishing-status","GET · gameplay upload counts, publishing attempts and last GitHub commit."],
["/v1/profiles?username=curedmc","GET · selected SkyBlock profile, purse and published unlocks. Rate limited; unavailable requirements remain unconfirmed. No account login needed."],
["/v1/gameplay","POST · validated gameplay evidence; approved private contributor key required. Not a public read endpoint."]
].map(([path,description])=><tr key={path}><td className="l mono small" style={{whiteSpace:"normal",overflowWrap:"anywhere"}}>{path==="/v1/gameplay"?<code>{path}</code>:<a href={path}>{path}</a>}</td><td className="l" style={{whiteSpace:"normal"}}>{description}</td></tr>)}</tbody></table></div>
<h2>Bundled reference files</h2><div className="tablewrap"><table><tbody>{[
["manifest.json","Build date, source coverage and accepted/skipped reference files."],
["market.json","Bundled historical market summaries; not the live Bazaar feed."],
["items.json","Bundled item metadata; live metadata can supplement it."],
["recipes.json","Reference crafting, Forge, Kat and NPC recipes."],
["production-recipes.json","Craft production catalog, intermediate recipes and requirement gates used by live craft plans."],
["mayors.json","Bundled mayor and election reference data."]
].map(([file,description])=><tr key={file}><td className="l"><a href={`/calculator/data/${file}`}>{file}</a></td><td className="l" style={{whiteSpace:"normal"}}>{description}</td></tr>)}</tbody></table></div>
<p>Per-item chart files under <code>/calculator/data/item/</code> and legacy auction chart files under <code>/calculator/data/ah/</code> belong to the bundled snapshot. They are separate from the persistent AH dataset.</p>
<h2>Shared GitHub datasets</h2><p><a href={`${repo}/blob/gameplay-data/community-history.json`}>community-history.json</a> contains bounded anonymized gameplay evidence. <a href={`${repo}/blob/gameplay-data/ah-market-history.json`}>ah-market-history.json</a> contains retained public AH market aggregates. Changed summaries create commits; refreshes do not imply commits.</p><Cadence />
</>}
export function About(){return <>
<div className="pagehead"><div><span className="eyebrow">Reference</span><h1>Sources</h1><p className="lede">{NOTICE.affiliation}</p></div></div>
<div className="grid cols-2">
<section className="card pad stack"><h2>Live market and accounts</h2><p><a href="https://api.hypixel.net/">Hypixel Public API</a> supplies live Bazaar prices, item metadata and account profiles. The owner's Hypixel API key stays in Worker secrets. Imported purse and unlocks are snapshots; bank balances and existing orders are excluded from the public budget.</p></section>
<section className="card pad stack"><h2>Auction House prices</h2><p><a href="https://github.com/Coflnet/SkyApi">Coflnet SkyApi</a> supplies craft discovery, lowest BIN quotes and sales summaries. BIN, sales and discovery observations are retained separately. Provider volume is rolling activity, not a confirmed daily volume; it is never added across polls. Lowest BIN does not prove enough listing depth or guarantee a buyer.</p></section>
<section className="card pad stack"><h2>Recipes and requirements</h2><p><a href="https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO">NotEnoughUpdates catalog</a>: MIT License, © 2020 Moulberry. <a href="/calculator/licenses/NEU-CATALOG-LICENSE.txt">Bundled license</a>. Catalog requirements are supplemented by audited collection, skill, Slayer, HotM, dungeon, essence and mutation gates. Unknown account requirements remain blocked unless confirmed in Settings &amp; unlocks.</p></section>
<section className="card pad stack"><h2>Game rules</h2><p><a href="https://hypixelskyblock.minecraft.wiki/">Hypixel SkyBlock Wiki</a> (CC BY-NC-SA 3.0) and <a href="https://wiki.hypixel.net/">official wiki</a> provide recipe and menu references. Daily Bazaar limit assumptions also use community findings from SkyHanni and Bazaar Utils; confirm current in-game limits.</p></section>
<section className="card pad stack"><h2>Gameplay calibration and history</h2><p>Approved mod uploads provide anonymized settled-trade evidence for shared and item-specific calibration. Persistent AH aggregates collect independently of visitors. Older Bazaar charts and contributor timing samples are bundled reference data, including contributed polls and Internet Archive copies; they change on rebuild and deployment.</p></section>
<section className="card pad stack"><h2>Calculator code and interpretation</h2><p><a href={repo}>GoofyAddons source</a> builds on the MIT <a href="https://github.com/Goofythesecond/bazaar-calc">bazaar-calc</a> calculator. <a href="/calculator/provenance.json">Build provenance</a>. This site includes Bazaar, book, craft, Forge, Kat and NPC research. Public craft rows are individual route estimates, not a summed portfolio. Historical AH prices are research only until fresh quotes verify them. Predictions and confirmed trade profit are separate; this public page never starts the mod's trader.</p></section>
</div></>}
