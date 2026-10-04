# Bazaar Calc shadow integration — 1.3.24-BETA

The user wants market-driven, repeatable trading and eventual replacement of
manual route lists. This first integration supplies real upstream calculations
alongside existing selection; it does not delegate trade execution or ownership
to a prediction service.

## Components

- `tools/bazaar-calc/engine.mjs`: bundled exports of the reviewed upstream shared
  engine at `51268005376496bf993e0c1934b8a7e44656b0bc`. Original notices and the
  CC BY-NC-SA enchantment rule source accompany the bundle.
- `history.json.gz`: derived statistics from the upstream offline contribution
  builder, refreshed in 1.3.40 to 2026-10-04 12:29:42.721 UTC. The build imported 125 polling hours
  and summarized 182,730 episodes into 3,434 item-side summaries. Build time does
  not replace the recorded source time. Auction references are omitted.
- `adapter.mjs`: constructs supported buy-order/sell-offer routes using upstream
  pricing, fill models, combining rules, warning flags and evaluation. It enforces
  current mod limits and exact tax; removes craft/forge/NPC/instant/XP routes;
  and caps pipelined predictions for our sequential buy/process/sell loop.
- `server.mjs`: bounded, loopback-only HTTP companion. The mod supplies its shared
  Bazaar quote snapshot for recommendation pricing.
- `collector.mjs`: independent 20-second public Bazaar poller, with bounded replies,
  timeouts, single-flight requests and failure backoff. It persists seven days of
  hourly prices and a rolling day of flow/competition, retaining the latest 128
  completed episodes per item side. Upstream TopTracker, bookFlow, summarizeTop,
  competition and delists functions provide the observed fill inputs. As of 1.3.40,
  counterTrades also records actual instant-trade deltas; flow uses these after
  one observed hour, excluding weekly counter decreases and disconnected gaps. Live history
  replaces the bootstrap without re-dating it, and unobserved/stale products lose
  measurement inputs. Atomic minute checkpoints survive restarts; restarted order
  books do not manufacture continuity. `/health` reports freshness and failures.
  Collection continues while Minecraft or trading is stopped, as long as the
  companion process is running.
- `MarketAnalysisProtocol` and `MarketAnalysisClient`: bounded asynchronous client
  and strict response validation for request/quote timestamps, provenance, route
  shape, quantities, budget, entry filters, confidence and arithmetic.
- `ShadowMarketAnalysis`: no GameActions dependency. It logs top recommendations
  and legacy eligible rankings from the same snapshot, alongside budget/capacity
  and occupied products. Stop/pause, reload and mode changes discard in-flight
  responses. Analysis errors and diagnostics-sink errors cannot escape into the
  trading scheduler's safety-pause boundary.

## Existing trading and settings

The `marketAnalysis` config object defaults to disabled for legacy files. Its
only operational state is shadow analysis; there is no switch for execution.
Enabled analysis runs while normal trading is active. It uses remaining shared
capital and position slots, excludes already-owned products/inventory, and leaves
book/general allowlists and journals unchanged. General batches remain limited
by conservative inventory sizing, per-item budget, maximum order size and one
hour of historical volume. Books remain one output per cycle within native
combining levels and available inventory.

Recommendations are individually evaluated opportunities, not a portfolio.
Unmeasured storage/GUI overhead and latency mean rates remain predictions.
After expiry, history is ignored and results are labelled ESTIMATED; sufficiently
supported, fresh upstream fill samples produce MEASURED model labels. Public
order-book removals include cancellations, so these labels do not guarantee our
own fills. The Java boundary rejects stale history advertised as fresh/measured.

## Validation and remaining work

The Java suite covers protocol arithmetic, supported routes, timestamp mismatch,
entry limits, stale-history confidence, occupied positions, config compatibility,
bounded HTTP responses, throttling, lifecycle cancellation and isolation from
trading state. Node tests exercise the actual bundled upstream engine and local
HTTP server. `calculatorIntegrationTest` separately verifies the complete real
Java client → companion → upstream engine → Java parser path using a synthetic
unconfigured item. This optional task requires Node; normal Java tests do not.

The full build passes with 365 Java tests and zero failures/errors/skips; all
20 companion tests and the separate Java-to-Node integration test pass.
No live Minecraft session has been run with this integration. Live collection updates history automatically; `update-history.mjs` replaces only
the bootstrap. The most recent minute and open episodes may be lost on an abrupt
process exit. This runtime has not been verified against live Hypixel responses
in the cloud environment (its network cannot resolve the API host; earlier
proxy requests returned HTTP 403). Prediction/outcome calibration and
portfolio allocation are not implemented. A later selector can adopt verified
recommendations only after live shadow evidence, while existing positions stay
owned and managed independently of changing rankings.

[Setup, updates and provenance](../../tools/bazaar-calc/README.md) provide the
runtime and reproducible build commands.

## Account dashboard (1.3.24)

The companion serves a dashboard on `/`, adapting the pinned upstream MIT CSS.
`LocalDashboard` projects observed player name, inventory, capital/status, existing
engine records and the receipt ledger on the client thread. `DashboardTelemetry`
sends a bounded asynchronous snapshot every two seconds independently of trader
state, only when `marketAnalysis.dashboardEnabled` is explicitly enabled. It has
no game actions and its errors cannot enter the trading safety boundary.

`POST /v1/account` requires a dedicated header and bounded, fresh protocol data;
`GET /v1/dashboard` returns the in-memory observation and collector status. Host
and Origin checks, CSP, explicit static-asset paths and text-node rendering protect
the local UI. Account snapshots do not enter persisted market data. State expires
after ten seconds and recommendation timestamps after sixty; expired predictions
are hidden. Disconnects retain last observed inventory for display.

The UI distinguishes confirmed ledger profit, unknown settlements and individual
route forecasts. It does not estimate portfolio returns or invent purchase costs.
Coverage is limited to mod-tracked positions and inspected storage pages; manual
orders and unvisited pages are unknown. There are no browser trading controls.

Verification includes publisher opt-in/single-flight/throttling/isolation, Node
protocol expiry/limits/Host/Origin checks, real Java-to-Node account publishing, and
Chromium checks at 1440px and 390px for data rendering, filters, previous storage,
expiry, safe item-name rendering and horizontal overflow. Live Minecraft capture
has not been verified.


## Consecutive outbid task handling (1.3.41)

Diagnostics from 2026-10-04 showed a verified Green Thumb cancellation followed
by a Last Stand task inheriting the previous task's cancellation-sent flag. The
outer book engine remained OUTBID, so its state-transition reset never ran; the
second task waited without clicking until the first cancellation's deadline.
BookOutbidFlow now selects the task's persisted trade identity before navigation
and timeout checks. Changing trades resets cancellation and navigation state;
reselecting the same trade retains once-only clicks and the verification deadline.
The regression tests cover both the task switch and a still-pending cancellation.
Existing order journals and config do not need clearing or migration.
