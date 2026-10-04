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
  the limiting condition. Unconfigured routes are research only. This preview
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

Engine pin: `6dd0ae9565fd555dec9dbe5eca3a2f48ca3218cc` from
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

After ten eligible cycles with the **same input/output IDs and exact batch size**
in the preceding 24 hours, the calculator uses their 75th-percentile duration as
a conservative floor for its current market cycle estimate. This can reduce an
optimistic coins/hour forecast; it cannot accelerate the forecast or replace current
prices, tax, capital limits or entry filters. No samples means ordinary market
estimates. The dashboard shows collection counts and errors. Predictions remain
advisory; this does not turn the planned-next preview into an execution queue.

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
