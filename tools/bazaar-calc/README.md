# Local Bazaar Calc companion

Runs the real Bazaar Calc shared engine beside GoofyAddons. The mod sends only a
public Bazaar snapshot and trading constraints to localhost; this process returns
recommendations and never clicks, submits trades or alters journals. It polls the public Hypixel Bazaar endpoint continuously and does not require a
Hypixel key or login.

## Run it

Requires Node **22.15 or newer**. No npm install is needed for the packaged companion.
Extract the companion ZIP and run from that folder:

```
node server.mjs
```

In the repository, the equivalent command is:

```
node tools/bazaar-calc/server.mjs
```

The default port is 8789. An optional first argument chooses a different port.
The process listens only on 127.0.0.1. Keep it running alongside Minecraft; Ctrl+C
stops it. It is not a remotely hosted service. `/health` reports the engine commit
and history timestamp, plus collector freshness, last accepted update and errors. No browser UI or public hosting is necessary.

Add this field to your existing `config/goofyaddons.json`, preserving your other
settings, and reload while the mod is stopped:

```json
"marketAnalysis": {
  "enabled": true,
  "dashboardEnabled": true,
  "endpoint": "http://127.0.0.1:8789/v1/recommendations",
  "refreshSeconds": 20,
  "maxHistoryAgeHours": 48,
  "maxRecommendations": 10
}
```

Start your usual trading mode. Recommendation requests run while trading is active;
market collection runs independently whenever the companion is running, including
while the trader is stopped or Minecraft is closed.
Existing files default to disabled. Enabling it is **shadow analysis only**:
manual allowlists, purchases, holdings and exits remain controlled by the current
traders. It cannot turn on automatic market discovery/execution.

## Account dashboard

Open **http://127.0.0.1:8789/** in your browser after starting the companion.
The dashboard adapts the original Bazaar Calc trading-desk styles and layout,
with responsive tables, an inventory grid, item search, order filters, a theme
switch and current/previous storage observations. No web build or npm install is
needed. `dashboard/` contains the page and assets; `dashboard-state.mjs` maintains
the received account view, and `collector.mjs` maintains public market history.

Set `marketAnalysis.dashboardEnabled` to `true`, stop with K, then run
`/goofyreload` (mod 1.3.26+) or press the Reload configuration key, backslash by
default and rebindable in Controls. The new reload message reports the exact file
path and dashboard/analysis ON/OFF status. It defaults to **false**. The mod then sends observed account state to
localhost approximately every two seconds, even while trading is stopped.
Dashboard publishing is independent of `marketAnalysis.enabled`, which controls
recommendation requests. Keep both enabled to see account data and predictions.

The page shows:

- Player name, connection/trader status, observed purse, committed capital and limits.
- Inventory item names, quantities and native slot numbers; equipment is listed separately.
- Last-inspected storage pages from the book engine, including previous observations.
  Unvisited pages are unknown; this is not a complete live scan of all storage.
- Mod-tracked book/general orders and positions with their actual engine states.
  Unrelated manual orders are not discovered. Actual book acquisition costs remain unknown where task records lack verified values.
- Confirmed session profit from the existing receipt ledger, including a count of
  settlements with unknown profit. Known profit is not a complete total when that
  count is nonzero. Ledger errors suppress the displayed profit value.
- Active-position profit estimates at the current offer minus 0.1 coins, after the
  configured tax. General positions use recorded costs; books use planned costs
  for the full combine cycle. Missing costs or quotes older than 60 seconds remain
  unknown. This excludes listing fees, future price changes and fill timing; it
  does not promise an executed sale. Requires mod and companion 1.3.27 or newer.
- A shared **Planned next** allocation preview (mod and companion 1.3.31+), comparing
  configured book/general routes against one spendable budget, conservative input
  capacity, occupied products and active-position limits. Deferred candidates show
  the limiting condition. In manual mode, unconfigured routes are research only. Automatic mode accepts supported discovered routes. This preview
  reserves no real money, places no orders and is rebuilt from current observations;
  it is not a persistent execution queue or a combined profit/hour guarantee.
- Fresh calculator route predictions, ranked individually by expected coins/hour.
  The leading figure is the best eligible route, not predicted profit on current
  orders or a sum of all opportunities. Analysis still requires active trading. Empty forecasts explain the limiting condition.

Account snapshots become stale after ten seconds without a fresh update. The
page labels retained observations and removes stale predictions. On a disconnect,
last observed inventory is retained for display. Account data is kept **in memory
only** by the companion, separately from its persisted public market recordings.
The feed excludes login credentials, player UUIDs, server addresses, chat and menu lore.
Existing diagnostics/journals on the Minecraft side keep their existing behavior.
No Hypixel player API key or account login is needed.

The server binds only to loopback, rejects foreign Host/Origin headers and
requires a dedicated header for account uploads. Browser assets use a restrictive
Content Security Policy; item names render as text. The dashboard has no trade
controls or config-writing endpoint. An unavailable companion cannot pause trading.

Optional browser verification (requires Chromium installed, or `CHROME` pointing
at its executable): `node tools/bazaar-calc/dashboard-browser-check.mjs`.
This uses synthetic account observations and no live Hypixel requests.

## What is calculated

- Same-item buy order → sell offer routes and free anvil book combines up to the
  upstream documented combining cap, limited to levels the book engine supports.
- All supported market products, with a marker for routes already in your lists.
- Exact configured tax, capital remaining after commitments, open-position limits,
  conservative inventory capacity, configured entry filters and occupied products.
- Expected profit/hour, cycle time, batch size, capital usage, price basis and
  measured/estimated fill-model basis. Serious upstream market warnings are excluded.
- A sequential rate cap: our engine buys, claims, processes and sells a position
  before its next buy. The upstream pipelined throughput is not treated as our
  executable rate. Poll intervals and click delays come from the mod settings;
  network/typing defaults and missing GUI/storage timings remain estimates.

Unknown products stay conservatively unstackable. Craft, forge, NPC, instant and
XP-requiring book routes are excluded. These recommendations do not constitute a
combined portfolio plan: each is evaluated individually within the available
budget. Sum-of-rows profit is not an earnings estimate. Every live trade still
needs its own confirmation checks when automatic selection is added later.

## Historical data and updates

The included gzip file contains derived price/competition/flow statistics and
order-top samples from the upstream contribution files. Its source timestamp is
**2026-10-03 10:24:08.535 UTC**, not the packaging time. The upstream offline builder
imported 114 polling hours and summarized 412,618 episodes into 3,483 item sides.
Old archive snapshots do not establish recent competition or fill times.

The companion fetches the current public Bazaar order books every **20 seconds**.
Requests have a 10-second timeout, never overlap, and back off to at most two
minutes after failures. Duplicate timestamps, quotes older than 60 seconds,
malformed products, major product-count drops and degraded zero-volume responses
are rejected. It measures actual consecutive book changes using upstream
`TopTracker`, `bookFlow`, competition and delisting formulas. Gaps longer than
150 seconds do not count as observed trading time.

Live history is saved atomically every minute and on Ctrl+C/SIGTERM to
`live-history.json.gz` in the persistent user-data folder, and loaded automatically on the
next launch. Keep that directory when upgrading the companion. It stores seven
days of hourly price observations and approximately 24 hours of competition/flow,
with the latest 128 completed time-on-top episodes per product side within that
24-hour window. Predictions use the upstream summary of those recent episodes.
Open episodes are censored on clean shutdown; after a restart, the collector does
not invent continuity with books it did not observe. An abrupt shutdown can lose
the most recent minute of observations.

On its first accepted live snapshot, live observations replace bootstrap
statistics entirely. This avoids giving old archived measurements a new timestamp.
Fill predictions warm up as genuine episodes accumulate and stay ESTIMATED until
the upstream model has enough observations. Products missing a fresh observation
for 60 seconds lose their live measurement inputs. Current quotes sent by the mod
still determine each recommendation's prices; collector samples enrich its history.

Inspect `http://127.0.0.1:8789/health`: `collector.fresh` should be true and
`collector.lastUpdated` should advance as Hypixel publishes new snapshots.
`error` exposes request failures, and `storageError` exposes save/load failures.
Collection requires internet access and stops when the companion process exits.
For offline tests only, pass `--no-collect` after the optional port argument.

Bootstrap history older than `maxHistoryAgeHours` is discarded for calculations. Live quotes
can still produce explicitly **ESTIMATED** recommendations; the stale history
never becomes fresh because a new quote arrived. Missing/cancelled orders cannot
be distinguished perfectly in public snapshots, so MEASURED still describes a
model based on observations, not guaranteed personal fills.

To replace the history with newly derived upstream statistics:

```
node update-history.mjs /path/to/bazaar-calc/site-data/market.json
```

Restart the companion afterward. Generate that file using the upstream
`scripts/data/build-site.mjs` from new contributions/recordings. This updates only
the bootstrap used before live collection begins; saved live history takes priority.
The companion does not yet learn from the mod's realized trades. Existing trade
events and shadow comparisons provide evidence for the later calibration stage.

## Diagnostics and verification

`market.shadow_recommendations` includes the report, legacy eligible route rankings,
capital/capacity and exclusions from the same snapshot. `market.shadow_unavailable`
records a bridge or validation problem; it does not pause trading. Detailed
`/goofydebug export` snapshots include the analysis status. Stopped/reloaded/mode-
changed requests are discarded; expired recommendations are not exposed as fresh.

```
node --test tools/bazaar-calc/*.test.mjs
./gradlew test
./gradlew calculatorIntegrationTest
```

The last command requires Node and exercises the real Java HTTP client, companion,
bundled upstream engine and Java response validator together. The normal Java
suite excludes this optional check and has no Node dependency.

## Rebuild and provenance

Engine pin: `51268005376496bf993e0c1934b8a7e44656b0bc` from
https://github.com/Goofythesecond/bazaar-calc. The adapter uses upstream market,
fill, pricing, timing, rule and evaluation functions; it does not substitute an
independent Java price/volume heuristic for the calculator.

From a checkout at that commit, install its frozen dependencies, build `@bc/shared`
and `@bc/server-core`, then run:

```
node scripts/data/build-site.mjs --offline --out /path/to/site-data
```

From GoofyAddons:

```
node tools/bazaar-calc/build-engine.mjs /path/to/bazaar-calc /path/to/site-data
```

The builder requires upstream esbuild 0.21.5 (present in its frozen dependency
set). Reviewed pin, source history date and build time are in `provenance.json`.
Upstream code is MIT; the enchantment rules are CC BY-NC-SA 3.0. See `licenses/`
for the original notices and the separately supplied rule source/attribution.

### Continuous collection on Windows

Keep the companion running to collect current public Bazaar data every 20 seconds,
including while Minecraft is closed. It stores rolling history outside the installation, in the persistent user-data folder described below. Install
Node.js 24 LTS, extract the companion to a permanent folder, stop any manually
running companion, then open PowerShell in its `bazaar-calc` folder and run:

```powershell
.\windows\install-task.ps1
```

This installs and immediately starts the current user's **GoofyAddons Bazaar
Companion** task, which starts again on Windows login and retries failures three
times. It needs no administrator privileges. Keep the PC awake and connected;
collection cannot run while it is asleep or powered off. Logs are in that same persistent folder.
Windows may block downloaded scripts: after reviewing these files, use
`Unblock-File .\windows\*.ps1` if needed. No execution-policy change is required.
Remove automatic startup with:

```powershell
Stop-ScheduledTask -TaskName 'GoofyAddons Bazaar Companion'
Unregister-ScheduledTask -TaskName 'GoofyAddons Bazaar Companion' -Confirm:$false
```

### Scheduled public snapshots

The repository's `Collect public Bazaar market` workflow runs four times daily
(00:17, 06:17, 12:17, 18:17 UTC) for ten minutes each, polling every 20 seconds.
GitHub schedules can be delayed; these windows are sampled coverage, not a
continuous live service. The workflow begins after it is on the default branch.
It restores the most recent successful public history and uploads a
`public-bazaar-history` artifact with 30-day retention. About 1,200 Linux runner
minutes per month, plus setup, are needed; availability depends on the repository's
Actions plan. Manual runs are also available in Actions.

Download the artifact and place `live-history.json.gz` in this companion's
persistent user-data folder **while it is stopped** to seed a new PC installation. Do not replace
an established PC history with a less complete scheduled sample. Old observations
keep their source timestamps; the collector needs fresh quotes before using them.
No account data or gameplay history is uploaded by this workflow. The job fails
if it obtains no new observations or cannot save them. Continuous PC collection
provides better coverage and does not consume Actions minutes.

### Measured gameplay outcomes

The mod records confirmed whole-position outcomes in
`config/goofyaddons-execution.json`, separately from its profit ledger. With
`marketAnalysis.enabled` and `marketAnalysis.dashboardEnabled` enabled, the local
companion receives these samples and saves `execution-history.json` in the persistent user-data folder.
Records contain random trade/receipt identifiers, route IDs, quantities, observed
elapsed time, and confirmed proceeds/profit; account names and inventory are not
persisted in this history. Repeated snapshots deduplicate receipts.

Timing begins at a buy submission subsequently verified in the order GUI and ends
at a confirmed sale receipt. These are **observed whole-cycle durations**, including
checks, menus, lag, combining and repricing, not exact server fill times. Pauses,
disconnections, tick gaps over five seconds, partial settlements, losses, unknown
costs and holdings recovered from a previous session do not produce eligible timing
samples. Existing holdings still trade normally. Samples survive restart, but open
timers do not: offline time cannot masquerade as measured execution.

From 1.3.46, downside timing evidence starts after **three** eligible cycles in
24 hours. Matching input/output routes share timings across batches with the same
input/output ratio, normalized by output quantity. The 75th-percentile duration
adjusts current throughput; weight grows to full strength at ten slow cycles.
The timing factor can fall to 0.1; increases still need ten cycles, reach full
weight at thirty, and stay capped at 1.5 and the current market-volume ceiling.

New verified buys also record their original expected net batch profit. Once three
completed cycles have both forecast and known realized profit, repeated shortfalls
apply a separate profit-realization factor. It never boosts current margins and
grows to full strength at ten samples. Older records lack the original forecast;
they still contribute timing but do not get an invented profit comparison.
Current buy/sell quotes, tax, capital and entry checks remain authoritative.

Fresh open trades watched for at least three minutes can supply a duration lower
bound when overdue versus the forecast. They cannot increase a rate or create
confirmed profit. These snapshots expire after fifteen seconds and are not stored.
Explicit book retirement preserves a separate lower-bound timing sample when its
session was continuously observed; cleanup sales remain excluded from successful
cycle calibration. Pauses/offline sessions do not supply these bounds.

The collector reports trade-counter observations from sampled intervals in the
last 24 hours. After at least one observed hour, each buy/sell side uses a two-hour
weekly prior blended with that recent rate, capped by its weekly baseline. Quieter
markets therefore lower throughput earlier. Gaps, rollover and order cancellation
are not invented trades. The dashboard distinguishes weekly-average daily volume
from recent **daily-equivalent rates**, with observed-hour coverage; these are not
claims of an exact complete day's traded volume. Insufficient or stale evidence
falls back to the weekly baseline.

The dashboard separates market-only rate, adjusted rate, observed gameplay net
rate, timing evidence, profit realization, and daily volume. Cards, tables and
pipeline batch profits include the realization factor; inspection also shows the
raw market batch profit. Individual route rates are not additive portfolio
earnings. Automatic selection uses the adjusted ranking for subsequent purchases;
existing trades retain their transaction checks and retirement policy.

Back up both mod JSON files and the companion's persistent user-data folder to retain measured
history. The distribution ZIP deliberately excludes all runtime data.


### Update-safe data storage

Market history, gameplay outcome history, collection status and Windows task logs
now live outside the replaceable companion installation:

| System | Default folder |
| --- | --- |
| Windows | `%LOCALAPPDATA%\GoofyAddons\bazaar-calc` |
| macOS | `~/Library/Application Support/GoofyAddons/bazaar-calc` |
| Linux | `$XDG_DATA_HOME/GoofyAddons/bazaar-calc`, or `~/.local/share/GoofyAddons/bazaar-calc` |

Run `node data-paths.mjs` to print the actual folder. The server also prints it at
startup and exposes it through its local `/health` response. All collectors and
telemetry storage use this location regardless of the installation's directory.
An absolute `GOOFY_BAZAAR_DATA_DIR` environment variable overrides it. Scheduled
GitHub jobs explicitly use their runner workspace for artifact collection; your
PC data stays local.

**First upgrade:** keep the old installation's `data/` folder until migration has
finished. If it is still beside the new `server.mjs`, startup automatically copies
recognized files to the persistent folder. Alternatively, from the new companion
folder, import the old folder before deleting it:

```powershell
node data-paths.mjs --migrate-from 'C:\path\to\old\bazaar-calc\data'
```

Stop the old companion first. Migration preserves originals, publishes complete
copies, and never overwrites existing external files. Existing external history
wins when both locations have the same filename; histories are not blindly merged.
A migration failure stops startup with an error and preserves the originals.
History already deleted before migration cannot be recovered by this change.

After this first migration, updates may replace the whole companion installation
without removing history. Stop the companion or scheduled task before updating;
restart afterward. If you move its installation to a different path, rerun
`windows/install-task.ps1` to update the task's program location. The persistent
folder remains the same. The mod's configuration, order journals, profit and
execution files remain in Minecraft's `config/` folder; replacing the mod JAR
already preserves those files.

### Best flips and pipeline desk

The dashboard's **Best flips** section shows the top three routes for the selected
sort, plus a searchable report table. Filter books/general, configured routes or
favorites; sort by coins/hour, batch profit, required capital or cycle duration.
Inspect a route for native product IDs, input/output quantities, capital, estimated
profit, combine operations, limiting factor, quote age and market evidence. The
copy button copies its input product ID. Favorites, filters and theme are saved
only in this browser and do not grant execution permission.

The **Trading pipeline** shows observed buy/claim, storage/combine, sell/settlement
and verification/recovery positions. These counts represent work stages, not exact
fill percentages. The next-allocation preview shows spendable and pending capital,
allocated capital, remaining coins and inventory headroom, and deferred reasons.
It remains a preview: the traders still select their own work.

The report is the mod's current eligible, bounded calculator result, not a browser
scan of every market. Increasing `marketAnalysis.maxRecommendations` can expose more
reported candidates (up to 50). Research-only routes remain outside execution scope
until configured. Rates describe individual routes and must not be added into a
portfolio earnings promise. Stale or disconnected account data hides forecast
cards, route details and allocations. Refresh now requests a fresh dashboard view;
it does not force a Hypixel poll or change the mod's market request cadence.

Ender Chest pages use the same slot-grid presentation as the inventory, preserving
exact positions, item names and quantities. Switch current/previous to compare the
observations. Hover a slot for its index and native item ID; the shared inventory
search dims nonmatching storage items. Storage remains a last-inspected snapshot,
not a live scan. Observed menu controls are included. Mod 1.3.34 supplies empty-slot
positions; with older mods, slots absent from the occupied-only report are dashed
and labelled unavailable instead of being asserted empty.


Garden Mutations are excluded from recommendations and new automated purchases
(1.3.37+), even when allowlisted. `mutation-products.json` identifies the 40 catalogued
crops, using item identifiers and mutation classifications from the attributed NEU
catalog revision. This does not exclude ordinary Farming/Garden products such as
Fine Flour or Designer Coffee Beans. Newly encountered mutation item labels and
explicit unmet unlock requirements in a verified product GUI are skipped before
submission; learned denials persist in Minecraft's config directory as
`goofyaddons-bazaar-access.json`. After fulfilling another item's requirement, close
Minecraft, remove that item's entry and restart. The mutation category remains
excluded. Existing holdings/orders still use recovery and exit checks. Public Bazaar
quotes cannot establish which account requirements have been fulfilled.


## Automatic route selection (1.3.38+)

Set `marketAnalysis.enabled` and `marketAnalysis.automaticSelection` to true, run
the matching companion, and start trading. Legacy configs default to manual
selection. Manual `books` and `general.items` arrays may be empty in automatic mode;
spending, profit, margin, volume, inventory and per-engine position limits remain.
The client uses the shared planner's highest-priority eligible proposal for the
next new position, then rebuilds the plan after reservations/holdings change.

Known native product names and independently supported free-combine book routes
come from `automatic-products.json`, sourced from the attributed NEU catalog and
bundled calculator rules. Mutation crops, learned missing requirements, unknown
product names, instant/crafting/NPC/forge and unsupported book combines are excluded.
Automatic traders wait when the companion/report is unavailable or stale; existing
positions keep their ordinary recovery and exit logic. Eligibility on the dashboard
means a route can be considered, not that it will immediately get a slot or capital.
No position-limit or pricing restrictions are removed.


Sequential trader capital (1.3.39+): a route reserves the input batch's acquisition
cost. The upstream optimizer can model simultaneous buy and sell lots; its
combined capital estimate is not used as the commitment for our sequential
buy/claim/process/sell position. The optimizer receives space for both modeled
legs, then the adapter independently enforces input batch cost against available
capital and the general per-item cap. Live trader confirmation checks still apply.
This removes double counting, without increasing configured spending limits.
Inventory capacity still treats unknown products as potentially unstackable;
large stacked orders require a separate verified stack-capacity implementation.

## Upstream refresh (1.3.40)

The bundled shared engine and public bootstrap history now use reviewed upstream
commit `51268005376496bf993e0c1934b8a7e44656b0bc`. Bootstrap observations end at
2026-10-04 12:29:42 UTC. The companion continues polling current Bazaar quotes every
20 seconds and retains private market/gameplay history in the existing external
data directory; replacing the companion does not reset it.

The collector now records increases in Hypixel's weekly instant-trade counters.
After at least one hour of valid consecutive observations, the upstream statistics
use those trades instead of order-book removals for flow estimates. Removed orders
can include cancellations. Counter decreases, gaps over 150 seconds and restart
boundaries contribute no inferred trades. Older seven-field saved flow rows remain
readable and use the existing book-based estimate until enough new trade evidence
has accumulated. Exact same-window cancellation comparisons require three hours.

Upstream also added confidence scores, paper/manual order tracking, notifications,
NPC routes and mayor perks. This release updates the engine, history and scanner
logic; it does not add those execution modes or change our dashboard's evidence
labels into a confidence probability. Supported automatic routes remain ordinary
buy-order/sell-offer flips and verified free book combines. The engine rebuild now
regenerates the native book-route catalog from the same pinned combining rules.


## Stalled-book cleanup (mod 1.3.43+)

`liquidateStaleBooks` defaults to true and `bookStaleSeconds` defaults to 900. A
tracked book flip retires after fifteen minutes without acquiring or combining
books, or when its existing holding-age/drawdown limit is reached. Repricing and
quote refreshes do not reset this clock; changing market rank alone does not
retire a progressing flip. A cleaned-up product is excluded from new orders for
thirty minutes in the current client session, so the planner considers other
routes instead of immediately reopening the same stalled flip. Set `liquidateStaleBooks` to false to disable new
automatic retirement and keep the previous hold-limit behavior.

Unassigned, observed leftovers are queued immediately. The cleanup first reads a
settled, complete order list, claims any filled inputs, cancels an unfilled order
and verifies that it disappears. Filled sell offers return to normal proceeds
collection. Books in the configured Ender Chest pages are retrieved with the
existing observed-transfer checks. Each exact enchantment/level is then sold
using its item GUI's Sell Instantly control. This uses the current bid and can
realize a loss: entry minimum-profit thresholds do not block cleanup exits.

A matching item/quantity Sold receipt and observed inventory disappearance are
both required before updating ownership/profit. Cleanup never uses Sell
Inventory, touches another co-op owner's order, or cancels a live order belonging
to an unassigned-leftover record. Ambiguous quantities, pagination, unexpected
GUI variants or missing acknowledgment preserve the position and pause for
inspection; an uncertain sale is not replayed. Retirement intent and the
no-progress clock survive restarts in the existing book journal. Unknown legacy
acquisition costs remain unknown in profit reporting. Cleanup sales are excluded
from ordinary route-throughput training. Keep the existing journals and
companion; replacing the mod JAR is sufficient.

## Adaptive gameplay ranking update (1.3.46)

Replace the mod with `goofyaddons-1.3.46-BETA.jar` and update the local companion
using the matching `goofyaddons-bazaar-calc-1.3.46-BETA.zip`, then restart both.
Keep existing Minecraft configs/journals and the external companion data directory.
Keep `marketAnalysis.dashboardEnabled` enabled so the opt-in local telemetry sends
completed and currently observed executions. No config reset is required. The
upstream engine pin and existing public/bootstrap history remain unchanged.

## Continuous Best flips and similar-volume learning (1.3.48)

Update both the mod and companion to 1.3.48 and restart them; retain your configs,
order journals and external market/execution history. Best flips now updates even
when position slots, inventory or liquid capital are full, and while trading is
stopped or paused. The ranking budget includes committed trading capital; the
execution pipeline still uses currently available cash and slots. Fresh market and
connected account data remain necessary.

Untested routes inherit a correction from recent completed trades in the same engine
with similar input/output daily volumes, with closer volumes weighted more heavily.
Own results gradually replace this shared correction. Original forecasts are recorded
for new trades so the comparison survives changes in the market model. Legacy history
still refines its own routes and requires no reset. All candidates are adjusted before
ranking; shared trades are labeled separately from the item's own completed cycles.
See [continuous ranking details](../../docs/development/CONTINUOUS_FLIP_RANKINGS.md).

## Portfolio and live-trade profit cards (companion 1.3.49)

This is a **companion-only** update: keep mod 1.3.48, replace the companion files
with `goofyaddons-bazaar-calc-1.3.49-BETA.zip`, and restart its Node terminal.
Preserve configs, order journals and the external history directory.

The overview now distinguishes three hourly figures:

- **Full-budget portfolio:** a hypothetical allocation of total trading capital,
  including funds currently committed to positions, after those funds are released.
  Alternative batch sizes are evaluated using the calculator's real order curves.
  A bounded search compares combinations within budget, per-item/order limits,
  supported route/requirement rules, engine position limits and conservative input
  capacity. It excludes duplicate markets/book families and caps combined workload
  by a single shared GUI. The allocation table shows its selected batches and
  unallocated money. This is the best allocation found among modeled candidates,
  not a claim of an exact global optimum or a change to the bot's execution plan.
- **Active trades:** a prediction for currently tracked running positions at their
  observed quantities, recorded general costs/planned book costs and current offers
  (or the actual listed general sell price). Overdue, continuously observed cycle
  age reduces the rate; gameplay calibration and a shared GUI ceiling still apply.
  The figure is a full-cycle equivalent run rate, not a precise remaining-time ETA.
  Unknown costs, retiring/recovery positions and unavailable fill models are labeled
  unknown; a displayed partial estimate states how many positions are missing.
  Stopped/paused trading predicts zero, and stale account/market inputs show a dash.
- **Actual profit:** receipt-confirmed session net profit divided by tracked active
  trading time, with paused time excluded. At least 60 seconds and known-profit
  settlements are needed; unknown settlements keep the hourly figure unavailable.
  The separate confirmed session-profit counter remains visible.

These forecasts use the most recent validated mod market request and opt-in account
snapshot. They update with market requests, position changes and fresh gameplay
feedback, without writing orders or altering configuration. All local calculations
and request caches stay private to the companion process.

### Capital reporting (mod and companion 1.3.50)

The overview's funded-position figure is owned input cost plus confirmed live buy
escrow. A book route's full reserved budget also includes inputs not bought yet;
that reservation is a spending constraint, not account wealth. Verified partial
order cancellation reduces funded exposure to the acquired lots, while retaining
the full reservation for capital-limit checks. Pending purchases still belong to
the purse until placement is verified. The portfolio uses purse minus reserve plus
funded positions, capped by the capital limit; it excludes future input reservations
and unrealized profit. Unknown book costs, unverified recovered orders, stopped
trading and the purchase settle window leave the full-budget forecast unavailable
rather than treating planned expenses as assets. Rankings can use a lower bound
of known funded positions while recovery is pending.

Confirmed session profit counts sale proceeds minus acquired cost. Cancel refunds,
open orders, and estimated future sales are not realized profit. For example, a 1,600-coin book budget with only one 100-coin input acquired
retains a 1,600-coin reservation after cancellation, but just 100 coins of funded
position cost. Adding the full reservation to the refunded purse double counts
1,500 coins. The reserved figure must not be added to the purse to infer earnings.

Update both the JAR and companion ZIP, restart Minecraft and the Node server,
and use the existing saved-position recheck before resuming. Keep configs, order
journals, profit/execution history and external market recordings.

### Gameplay learning fixes (mod and companion 1.3.51)

Newly selected routes capture their original uncalibrated timing and two-sided
volume forecast before GUI navigation. Order verification uses that captured
forecast even if the route is later occupied, filtered out of the report, or the
report expires. This preserves the evidence needed to transfer corrections to
other routes with similar volumes.

Partial sell receipts remain part of the same timing record until all original
inputs have settled. One final eligible cycle contains total proceeds/profit and
time from first placement to last claim; duplicate partial receipts do not add
units twice. Unknown-cost or interrupted cycles remain ineligible. Closing a
partially filled/cancelled general position retires any unfinished original cycle
as a duration lower bound, rather than leaving a phantom active cycle.

Fresh overdue cycles now reduce the ranking timing estimate directly to their
observed-age bound, within the existing 0.1–1.5 correction range. Previously one
very late trade could still retain about half of its optimistic predicted rate.
The dashboard reports how many eligible outcomes retain original forecasts and
volumes, making shared-learning coverage visible. Existing history is preserved;
missing historical forecasts cannot be reconstructed by this update.

Finish any comparison run before installing the paired update: restarting during
a timed run interrupts its timing evidence. Update both components, retain all
configs and histories, then start a new measurement run.

### Live product-navigation retry (mod 1.3.52)

Reversible navigation retries now compare the selected product's ID, name,
enchantment identity, quantity and slot rather than its changing price lore.
Other updating menu entries no longer reset the selected product's quiet window.
Controls without a product ID still require exact identity; changed products,
occupied cursors and unloaded/closed menus cannot authorize a retry. The retry
count and timeout remain bounded. Claims, cancellations and submissions do not
use this retry helper. Keep companion 1.3.51; only the mod changes in this release.

Diagnostic exports in mod 1.3.52 also include version metadata, session profit and
active trading time, completed gameplay outcomes, and current timing records.
These are captured only when exporting, so they do not enlarge routine heartbeat
logs or change trading. Full-session totals remain available even after older
events rotate out. Exported account/trade information stays in the local private
bundle and is never part of packaged public market data.
