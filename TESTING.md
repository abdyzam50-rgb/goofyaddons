# Testing build 1.3.31-BETA

A jar built from the `claude/refactor-plan` branch is in [`dist/`](dist/). Download it from
the GitHub file view (**Raw** / the download button), not by copying the page.

```
dist/goofyaddons-1.3.31-BETA.jar
sha256 93850472c60242f2f11106c3ffeaac3742682ec16bf7b667e631d18a1edff2d2
```

## Shared pipeline preview

Update both the mod and companion to 1.3.31, keeping config/journal/profit files
and the companion's `data/` directory. Restart Minecraft and the companion.
Enable `marketAnalysis.enabled` and `marketAnalysis.dashboardEnabled`, start
trading, then open http://127.0.0.1:8789/ and inspect **Planned next**.

The preview compares configured book/general routes together, allocates a shared
spendable budget, respects inventory/position limits and excludes held or reserved
products. It explains deferred candidates, including unconfigured research routes.
No real capital is reserved and existing traders still choose executable work.
Uninspected storage and unrelated orders remain unknown. The planner expires old
forecasts and revalidates changing account reservations.
[Implementation, tests and remaining stages](docs/development/TRADING_PIPELINE.md).

## Truncated product menu titles

The general trader previously waited for the complete item name in a product
menu title. Hypixel truncates long names: `Compactors ➜ Super Compactor 30`
contains the correct Super Compactor 3000 icon and controls, but triggered a
30-second timeout before any buy order was submitted.

Product navigation now verifies the product icon's ID and the selected order
control's full-name lore. Shortened buy/sell titles proceed, while conflicting
identities are rejected. Existing full-title layouts remain supported. Restart
Minecraft with this jar and press J; keep all config and ownership files.
Calculator 1.3.27 remains compatible.
[Captured evidence and regression checks](docs/development/TRUNCATED_PRODUCT_MENU_FIX.md).

## Continue saved book sessions

Recovery now resumes supported verified book/order positions instead of pausing
because they exist. Partial buys use the existing claim/cancel flow, held books
continue combining/selling, and output offers continue monitoring/collecting.
Extra fully held cycles finish without buying new inputs. Original journals are
backed up; trade IDs persist and unknown legacy costs remain unknown.

Replace the mod, keep all config/journal/profit files, restart Minecraft and press J.
Duplicate, unreadable or unsupported orders still pause with a specific reason.
The 1.3.27 calculator remains compatible. [Details and limits](docs/development/BOOK_SESSION_RESUME.md).

## Spendable book selection

Book selection now uses the shared spendable budget before ranking affordable
purchases. A high-ranked route that exceeds the reserve/capital limit no longer
crowds out a cheaper eligible route. Occupied products are excluded before they
consume the provisional budget, and pending purchases are not subtracted twice.
The 1.3.27 companion remains compatible.

The last supplied config uses BOOKS mode, two active book routes, and a 250,000
minimum net profit per cycle. Available coins alone do not override these limits;
calculator recommendations remain advisory. See
[the selection fix](docs/development/SPENDABLE_BOOK_SELECTION.md).

## Dashboard profit visibility

Update both the mod and companion, keeping your config and the companion's `data/`
directory. Restart the companion and Minecraft. Active positions now have a
separate estimate at current offers after configured tax. General positions use
recorded input costs; books use planned full-cycle costs. Unknown costs or stale
quotes remain unknown. Estimates exclude listing fees and fill timing.

Confirmed session profit still requires recorded settlements. The best-opportunity
card now explains disabled analysis, unavailable quotes, exhausted capital and
filtered routes. Analysis requests fresh quotes instead of waiting indefinitely.
[Details](docs/development/DASHBOARD_PROFIT_VISIBILITY.md).

## Visible config reload

Stop with K, then run **`/goofyreload`** or press the **Reload configuration**
key (backslash by default, rebindable in Minecraft Controls). A successful reload
prints the exact config path and dashboard/analysis ON/OFF settings. Rejected
JSON or settings print the reason and preserve the last working config. Running,
paused or recovering trading refuses reload with a clear stop-first message.
Update the companion to 1.3.27 for the new profit display.

## Book recovery startup fix

A saved book journal now triggers a read-only inventory/storage/order check instead
of the generic archive-and-restart warning. Verified stale records clear
with a backup and startup continues; an empty journal also clears a stale
same-process recovery flag. Actual matching holdings/orders stay reserved and resume when supported;
ambiguous observations pause with a specific reason. J reruns verification.
Incomplete, paginated or unreadable observations keep the journal.
[Implementation and limits](docs/development/BOOK_RECOVERY_VERIFICATION.md).

Recovery continuation is extended in 1.3.29 as described above.

## Bazaar Calc shadow integration

This build can run read-only market analysis alongside the traders using the
local [Bazaar Calc companion](tools/bazaar-calc/README.md). Download
[`dist/goofyaddons-bazaar-calc-1.3.31-BETA.zip`](dist/goofyaddons-bazaar-calc-1.3.31-BETA.zip)
for its dependency-free runtime. It compares supported
routes by estimated sequential profit/hour, capital and fill-model evidence, and
writes recommendations into diagnostics. It does not change the manual lists or
execute its recommendations. Existing configs default to analysis disabled.

This version adds a browser dashboard at **http://127.0.0.1:8789/**. Set
`marketAnalysis.dashboardEnabled` to `true` and reload with backslash while stopped.
The dashboard shows observed inventory/storage, mod-tracked orders and positions,
confirmed session profit and fresh route predictions. Uninspected storage and
unrelated manual orders remain unknown. Account observations stay in the local
companion's memory and expire as live data after ten seconds without updates.
Predicted coins/hour describes the leading eligible route, not a combined earnings
estimate or profit on existing orders. Desktop and phone layouts are checked in
real Chromium with synthetic observations.

Start the companion with Node 22.15+, add the `marketAnalysis` field shown in its
guide, and reload while stopped. The companion includes historical statistics as
of October 3, 2026 at 10:24 UTC; outdated history falls back to explicitly estimated
results. Live observations replace that bootstrap and persist automatically.
Keep the companion running on the same computer as Minecraft. Collection
continues while the companion runs, including while trading or
Minecraft is stopped. Check `/health` for current collector freshness. Its failure
does not pause the existing traders.

## General trader changes

This build repairs the automatic buy → claim → sell → collect loop. Entry profit
and margin targets apply to purchases; acquired items can sell at the verified
current offer price within the existing holding/drawdown limits. Inventory work
resumes after two seconds. Unchanged claims have bounded retries after lag or a
slowdown, and sale settlement waits for matching receipts and physical updates.
Temporary unreadable purse data waits before purchase instead of dropping a plan.

[Repair and verification](docs/development/GENERAL_LOOP_REPAIR.md) describes the
complete engine simulations and remaining live-menu/recovery limits. Select
General with M for an isolated trial, or Both to run with books. Existing item
allowlists and entry filters still determine whether any new trade qualifies.

## What you need

| | |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.2 or newer |
| Fabric API | 0.147.0+26.1.2 |
| Java | 25 |

Drop the jar and Fabric API into `.minecraft/mods/`. The mod writes its config to
`.minecraft/config/goofyaddons.json` on first launch; edit it, then press `\` (backslash)
while stopped to reload it.

Keys: **J** start, **K** stop, **M** cycle mode (Books / General / Both).

## Please read this before running it

This build has **never been run against a live server by its authors.** The test suite
passes (411 Java tests; 24 companion tests and the separate real Java → Node
integration check also pass) and it compiles and builds, but nothing here proves the menus, order
timings or tooltip formats behave the way the code assumes. Treat it as a first live trial,
not a release.

Specifically:

- **Start with an amount you would not mind losing.** Set `maxTradingCapital` low and
  `purseReserve` to most of your purse, and use one cheap book route.
- **Expect it to pause and ask you to reconcile.** That is the designed behaviour when
  anything is unclear, and it is the good outcome. "Trading paused: ..." means it stopped
  rather than guessed.
- Storage transfers now require both disappearance from the source and arrival at the
  destination on the verified Ender Chest page. Use `ec` / `ec N` storage commands;
  backpack commands pause until their page identity has captured fixtures.
- **Automated Bazaar trading is against Hypixel's rules.** This clicks menus for you. The
  realistic risk is to your account, not just your coins. That is your decision to make,
  but make it knowingly.

## What changed in this build

Book queries now reuse the shared tick snapshot, avoiding repeated native item
reads. Initial and replacement sale-price selection share one path. Storage moves
now retry rejected shift-clicks after validating the exact original source, unchanged
quantities, page, cursor and destination space; the original move intent is retained.
Confirmed sale claims settle without the three missing-order reopens, while delayed
receipts and partial menu updates still wait for verification.
[Behavior, regression coverage and limits](docs/development/BOOK_LOOP_STRENGTHENING.md).


Startup accounts for all tracked routes from one stable order-list observation.
A book with no live buy order no longer triggers three missing-order reopens just
because that route has physical holdings. Existing orders still use claim and
cancellation screens; unreadable fields still use the bounded observation retries.
[Shared startup scan and validation](docs/development/STARTUP_ORDER_SCAN_FIX.md).


Outbid navigation now recognizes the inventory snapshot with a null title as a
closed GUI. That fixes the idle wait before `/bz <book>` caused by the item-first
navigation change. The uploaded 1.3.17 log verifies the Duplex sell offer and the
subsequent Overload buy order, then shows this navigation stall.
[Evidence and regression checks](docs/development/OUTBID_CLOSED_GUI_FIX.md).


For books, `minNetProfit` now gates new input buying, including final buy
confirmation. A completed held book may be listed or repriced at the verified
current offer price even if its estimated profit is below the target or negative.
Price readability, fresh quotes, item/quantity/confirmation checks and configured
holding age/drawdown limits remain. New `books.price_check` diagnostics show the
estimated input cost, exit price, estimated net, target and decision reason.
[Captured pause and updated policy](docs/development/BOOK_EXIT_POLICY_FIX.md).


Anvil input moves, combination and result claims now retry when the exact expected
pre-action state remains unchanged. Completion is checked first, including during
slowdown waits. Retry requires a clear cursor, the same menu, correct native items
and quantities, at least three seconds since the click and 750ms of stable contents.
Recognized slowdown notices add a 1.5-second cooldown. There are at most three
retries per step within the existing 30-second operation deadline. The latest log
also confirms the slot-22 claim button's lore, which is now checked before claiming.
[Evidence, replay coverage and limits](docs/development/ANVIL_RETRY_FIX.md).


Outbid buy orders now enter through `/bz <book>` and the exact book level, then
use an explicit item-menu order-management control when recognized. The existing
claim/cancel/replace sequence remains: replacement waits for removal of the old
order and uses the remaining required quantity. Cancellation is clicked once;
a fresh settled list showing its removal skips the three redundant reopens.
Unrecognized item controls fall back to the working orders GUI and produce a
`books.outbid_navigation_fallback` diagnostic with the item menu contents.
[Evidence and navigation limits](docs/development/OUTBID_NAVIGATION_FIX.md).


After combining, the result remains in slot 13 above the claim sign in slot 22.
Collection now verifies that displayed book and that both original inputs were
consumed, then clicks the sign once. It waits for inventory arrival before changing
ownership. This fixes the result remaining in the anvil in 1.3.13.
[Collection regression and limits](docs/development/ANVIL_COLLECTION_FIX.md).


The captured 1.3.11 anvil menu has its result preview in slot 13 and its
“Combine Items” button in slot 22. The combine controller now verifies both before
submitting once, fixing the 30-second timeout with two valid books in the anvil.
Slot memory now records the preview and button separately from owned inputs.
[Evidence, regression tests and remaining limits](docs/development/ANVIL_PREVIEW_FIX.md).


Confirmed physical losses are permanently written off after storage inspection and a
five-second settling window. Unexpected matching books are adopted as newly found
holdings or retained as extras. Changed tasks reconcile live orders before replacement
buying. [Policy, accounting and continuation limits](docs/development/LOST_FOUND_POLICY.md).
Supported unfinished book positions now continue after live verification in 1.3.29;
see [Book session continuation](docs/development/BOOK_SESSION_RESUME.md) for limits.

The book engine now keeps previous/current slot layouts for main inventory and
configured Ender Chest pages, plus cursor/anvil observations. Transfers record intent
before clicking and bind confirmed arrivals to slots. Unexpected movement triggers
storage inspection and corrects the model when observed quantities account for it.
[Implementation, tests and limits](docs/development/SLOT_MEMORY.md). This memory lasts
for the current run; it is not restored after a full stop or client restart.

Unreadable purse readings now wait up to 10 seconds before pausing with a specific
reason. Price selection and final purchase confirmation still require a current valid
balance; no previous balance is used. [Evidence and limits](docs/development/PURSE_OBSERVATION_FIX.md).

The uploaded 1.3.8 log exposed completed anvil merges that the model never recorded.
This build keeps fixed baselines through cursor delays, acknowledges input consumption
and output arrival before starting another merge, and waits for outstanding buy inputs
instead of cancelling orders to repair a stale model. Storage moves and buy claims also
enter the model only after verified arrival. Unpaired stored inputs wait rather than
cycling between retrieval and combining.

[Repair evidence and validation](docs/development/BOOK_LOOP_REPAIR.md) include the
remaining limits: retry behavior still needs live verification, and the full
Bazaar transaction sequence is not yet covered by an engine replay.

Fixes inherited from the previous build:

- Task ordering was inverted, so collecting a finished sale ranked below everything and new
  buy orders ranked near the top. Sales should now be collected promptly instead of the
  engine drifting into fresh positions.
- A placed order that nothing looked at used to park forever if its chat notice was missed.
  A read-only re-check now re-reads it after 3 minutes (`bookOrderRecheckSeconds`).
- A completed flip left its whole cost committed against any leftover book, which slowly
  drained available capital until nothing could be bought.
- Full storage on both pages, and a store/retrieve cycle at the anvil, both used to spin
  until a watchdog noticed. Both now stop with a reason.
- Stopping used to leave claim state behind, which could silently wedge the engine on the
  next start.

The status panel was also rebuilt: one card instead of two, confirmed profit as the single
large number, signed so a loss reads as a loss without relying on colour, and rows that are
dropped rather than silently clipped when the window is short or the HUD scale is high.

## Running a first test

Do not just press J and hope. [docs/guides/FIELD_TEST.md](docs/guides/FIELD_TEST.md) is a staged
protocol: stage 1 is a genuine dry run using config alone, where no flip can qualify so nothing
can be ordered, and it rules out most of what could go wrong before anything is spent.

## Reporting a problem

Run `/goofydebug export`, which writes a ZIP under `.minecraft/logs/goofyaddons/bundles/`,
and send it with roughly when the problem happened. **Look inside the ZIP first** — it
contains item names, prices and trade receipts. It does not contain your account details,
chat, or server addresses.

Useful events to look for if you want to check the new behaviour yourself:
`books.order_recheck_due`, `books.order_rechecked`, `books.extra_exposure_resized`.

## Building it yourself instead

```
./gradlew build      # needs JDK 25; jar lands in build/libs/
```

1.3.32: execution history round-trip and receipt deduplication; paused, partial,
unknown-cost, lost and resumed outcomes cannot calibrate; local history restart,
corrupt-file preservation, exact batch/route/recency matching, minimum ten samples,
conservative timing floor, and bounded collection success/failure. Node HTTP,
Java trading regressions, Java-to-calculator integration, and Chromium dashboard
checks cover the updated distribution. Windows Scheduled Tasks and hosted Actions
execution require their respective platforms and are not verified by Linux tests.

1.3.33: companion data-path defaults and absolute overrides, legacy migration,
original preservation, destination conflict handling, repeatable migration,
installation removal with persistent history retained, same-folder portable mode,
and migration failure preservation. Hosted collector keeps an explicit artifact
workspace override. Windows Scheduled Tasks need verification on Windows.

1.3.34: display-only search/engine/scope/favorite filtering, immutable sorting,
plan membership and deferred explanations, exact work-stage grouping. Chromium
at 1440px and 390px exercises route inspection, filters, persistent favorites and
theme across reloads, empty results, allocation progress and manual refresh. Stale
account data removes top cards, selected details and allocations. Existing XSS,
overflow, storage and position-profit assertions remain enabled.
Ender Chest grids additionally verify exact current/previous item coordinates,
confirmed-empty slot metadata from Java, legacy unknown-slot handling, and desktop/
mobile layout. The browser exercises both snapshot choices and storage search.

1.3.35: regression for the observed empty report with two book and three general
positions, 11.3m available, and zero evaluated/filtered routes. Full enabled-engine
slots and unreadable purse observations take precedence over cached forecasts;
freeing a slot restores usable reports. Mode-specific headroom and inventory
constraints retain distinct explanations. Pricing/execution limits are unchanged.

1.3.36: real generic buy-cancellation refund parsing with rounded exact escrow,
wrong-side/amount rejection, durable refund proof and restart verification; partial
buys wait for complete batches while timed-out partial claims retain their prior
coverage. Forecast ranking uses only fresh configured exact-batch candidates and
never creates routes. Measured profit/hour rejects incomplete or insufficient timing.
