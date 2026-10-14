Drag splitting now distributes acknowledged cursor stacks across compatible partial recipe cells as well as empty cells, topping up equal deficits over multiple drags without exceeding the cell plan. Diagnostic events record drag targets, shares and remaining deficits. Instant sale price warnings are confirmed once only after an exact product/quantity/authorized-price match and the server countdown ends; unknown or below-floor warnings retain review. Purchase/sale receipts still require inventory and purse proof.

Verified instant-buy receipts immediately replan procurement and open the next ingredient GUI on the same tick. No intermediate menu close or extra scheduling intervals are required; inventory, purse, journal, price, space and ownership checks remain in place. Regression tests cover immediate handoff and staggered inventory/purse delivery.

Compactor production records an exact starting sack balance from the read-only Bazaar Sell Sacks Now tooltip or enchanted sack GUI. Paid conversions count only when the inventory plus verified sack increase reconciles against raw purchases. Outputs stay in sacks until crafting finishes, then /gfs retrieves only the owned increase and verifies the original sack balance remains. The sell-all-sacks control is never clicked; rounded/hidden counts are rejected and missing withdrawal receipts are not replayed.

Own compactor toggle/filter clicks now wait for transient vanilla-predicted cursor pickup to clear instead of immediately entering REVIEW. No additional click occurs while waiting; the existing eight-second acknowledgement deadline stops a persistent cursor. Foreign/unowned cursor states still stop immediately.

Compactor setup disables automatic conversion while installing the seed filter. After enabling, it closes configuration and opens a fresh Craft Item server container, waits for stable inventory, then reconciles the original raw-equivalent baseline before buying. A local menu close alone cannot refresh stale counts. Persistent shortages still retain the run for review with exact counts.

Intermediate preparation jobs retain up to 1024 requested batches, allowing an exact larger Quick Craft control (e.g. Blaze Powder) to execute early. Grid placement still obeys cell stack limits. The executor rechecks late Quick Craft controls only at acknowledged empty-grid/cursor boundaries and requires the offered amount to fit the remaining request and output slots.

Compactor filter installation now acknowledges the observed filter and closes its GUI before reconciling converted inventory counts. Allows up to eight seconds for staggered input/output updates; no further purchase occurs until the original raw-equivalent baseline and unrelated items reconcile. Persistent mismatch records expected/observed counts and remains REVIEW.

Compactor production now waits for the delegated seed craft while that child owns the cursor/grid. The parent checks cursor safety only after the craft finishes; it cannot open the compactor or issue clicks during the child. Regression reproduces an occupied Gold cursor across multiple child ticks. Fixes diagnostics-1791836767363.

Personal Compactor production supports single-input 160:1 enchanted recipes with instant-buy strategies and a readable hotbar device. Clears old filters, buys an inventory-sized seed lot, crafts one seed, installs/enables its filter once, proves later purchases by raw-equivalent input/output counts and purse debit, then clears the filter before sale. Mixed recipes and buy orders keep the existing path. Craft run/settings/pipeline limits are now 1–1024; child craft jobs remain at most 64 and purchases remain inventory-sized. Existing maximum-batch defaults are preserved. Live filter installation still requires in-game verification.

Legacy profile Worker error responses now identify Hypixel 401/403 credential rejection instead of displaying only a generic gateway 502. Only the fixed known public error is classified; arbitrary provider text is never displayed. Live curedmc lookup confirmed the deployed Worker is returning Hypixel 403. Update its HYPIXEL_API_KEY secret to restore lookup.

Craft inventory mismatch recovery returns owned cursor/grid ingredients one acknowledged transfer at a time, reopens Craft Item once, and reconciles the stable new container against the original input/output baseline. Verified partial outputs count toward completion; unresolved counts, unrelated items, missing room, or timeouts remain REVIEW. Recovery diagnostics include baseline and observed counts.

Storage scans now exclude GUI controls without a product ID from partial-stack detection. Regression covers an Ender Chest Close button alongside an owned partial input stack, then verifies the real stack still accepts a deposit. Fixes the PROCURE null-pointer failure in diagnostics-1791830123997.

Craft rounds now plan room for the largest complete input/output lot that fits, parking movable items before accepting a smaller round. Verify a storage-backed large craft request loads larger rounds, preserves hotbar tools, and retains verified transfer receipts. Explicit small test requests remain small.

### 0.2.112 Storage destination planning

Deposits prefer a known page that fits the whole lot instead of scattering it
over the first small gaps. An already open suitable page is preferred. Fresh
capacity observations skip full pages during scans and deposits, except when
compatible owned partial stacks can still accept the product. Withdrawals always
visit pages holding the run’s items regardless of their fullness.

Capacity is advisory for two minutes, then reobserved to account for manual
changes. Partial-product knowledge persists in the storage journal; old journals
remain readable and uncertain partial capacity is conservatively checked live.
Each shift-click still requires a verified movement and durable ownership update.

Regression simulations verify skipping a full page, choosing one larger page
over a two-slot gap for a three-stack lot, stale-capacity refresh, and merging
into a compatible partial stack on a full page after journal reload.
1364 Java tests passed; release build succeeded. Live verification remains
needed.

### 0.2.98 Storage page transition pacing

The export showed about 5.16 seconds between ec 1 and ec 2. The storage move
carried its old command retry deadline into a different page. Successful page
completion and skipped-page transitions now schedule the next page using the
configured navigation delay. Five-second backoff applies only to retries of
the same unopened page. Cursor checks, page identity, initial contents settle,
storage ownership and move receipts remain in place.

A regression simulation completes a two-page scan within two seconds with a
100 ms navigation range, verifies both capacity observations, and emits no item
clicks. Existing storage and forty-output production tests remain covered.
1361 Java tests passed and the release build succeeded. Live verification is
still needed.

### 0.2.97 Bazaar access during test sessions

The export queued a two-compactor production test but it remained at its first
Auto Smelter purchase with no debit. NPC navigation was intercepted but never
ticked in standalone test sessions. Shared access navigation now ticks for
production, book and general tests as well as normal trading.

A server-confirmed Booster Cookie consumption clears the cached AUTO-mode
command denial and cancels obsolete NPC routing; explicit NPC mode is retained.
The HUD reports TESTING and the actual test mode/activity instead of STOPPED
and Use the trading toggle while the test is active. Cookie-message regression
checks reject player-chat lookalikes. 1360 Java tests passed and the release
build succeeded. Live NPC/test-session verification remains needed.

### 0.2.96 Independent timing and asynchronous storage close

The book startup export paused because NPC Bazaar navigation observed a storage
menu that was still closing. Navigation now allows up to three seconds for the
old menu to disappear, sends no input to it, and still rejects a genuinely
unrelated menu or an occupied cursor.

G → Macros → Limits includes independent fastest/slowest ranges for navigation,
storage, and crafting/anvil. New category fields set to zero inherit navigation
for compatibility. Both crafting and anvil use the crafting range, production
and book storage use the storage range. Book inventory observations scale with
the active ranges while keeping a 50 ms minimum stable observation and a 150 ms
upper bound. Server acknowledgements and unchanged-contents checks remain
mandatory; the production storage initial page settle remains in place.

Regression coverage checks category independence and inheritance, invalid pairs,
closing-storage navigation, rejection of persistent unrelated menus, and the
50 ms observation floor with reset on contents changes. 1360 Java tests
passed; release build succeeded. Live verification remains needed.

### 0.2.95 Instant book routes enter the execution pipeline

The export repeatedly selected Sunset I to V using instant buy and sell offer,
then returned to IDLE with no tasks. TradeBudget still rejected every candidate
using instant buying or selling, despite the receipt-verified instant executors
added previously. This obsolete planning filter is removed; capital and occupied
product constraints still apply.

Regression coverage checks order and instant modes through budget selection,
including insufficient budget, and verifies an instant-buy recommendation
creates a task and reserves capital instead of silently returning to empty IDLE.
Account exclusions are supplied by BookServices so this entry test needs no
live Fabric filesystem. Existing instant-buy/sale receipt tests remain active.
1357 Java tests passed; release build succeeded. Live verification remains
needed.

### 0.2.94 Book startup and quote-wait recovery

The supplied launcher log showed Started then Stopped with no book exception;
it did not identify the exact live wait. Inspection reproduced a separate book
startup/storage defect: storage was considered visible only when ordinary slot
8 was nonempty. Empty or sparse pages could stall. Startup and storage now use
the existing stable, complete inventory observations; combining likewise uses
confirmed anvil observations instead of slot 8. Loading placeholders remain
excluded from storage recognition.

Book selection and outbid-monitor requests now expire after 15 seconds and
retry without adopting a late response. HUD activity distinguishes fetching,
calculator selection, no configured routes and no feasible profitable routes.
Regression coverage verifies sparse-page startup and retrying stalled requests.
1356 Java tests passed; release build succeeded. Live verification is still
needed. The launcher texture-signature warnings were not treated as a trading
diagnosis.

### 0.2.93 Crafted-output space and sale lots

The export stalled after 29/40 Dwarven Compactors because storage space-making
excluded crafted outputs. Verified outputs belonging to the Bazaar craft run
can now be deposited to make room for the next inputs. Their storage ownership
remains retained after crafting until withdrawal and sale are verified.

Bazaar output selling handles inventory-sized lots, withdrawing stored output
as space permits. When storage has no output room, the process sells a verified
partial lot then continues crafting. Each lot has its own acquisition/sale
receipt ID and receives its proportional input cost; submitted sales are never
replayed and unsold units must match verified inventory plus owned storage.
AH listing behavior remains unchanged.

Regression simulations cover forty unstackable outputs with storage, full-storage
partial-sale fallback, all forty units crafted and sold, distinct receipts, and
no remaining owned output. Existing one-lot receipt/cost tests remain covered. 1353 Java tests passed;
release build succeeded. Live verification remains needed.

### 0.2.92 Inventory-sized instant purchases

Hypixel rejected the exported Enchanted Mithril instant purchase because its
amount exceeded inventory capacity. Storage-backed instant buys now cap a lot
to observed inventory capacity. A valid Fill my inventory quote is used only
when it fits that cap and the remaining requirement. Otherwise the exact capped
amount is entered. The price ceiling scales with the lot size.

After a verified lot the existing procurement planner counts inventory plus
owned storage, makes room, and buys only the remainder. Paid short deliveries
retain stash recovery and never cause a fresh purchase until their receipt is
resolved. Buy orders retain their existing behavior.

Regression coverage verifies 4096 units across two capacity-sized purchases,
no stash commands, total cost counted once per lot, a quoted fill purchase, an
overpriced fill rejection, partial stack space with no empty slots, and a final
remainder smaller than a fill quote. 1351 Java tests passed; release build
succeeded. Live Hypixel verification remains needed.

### 0.2.91 Buffered mixed crafts and native drag splitting

Recipes containing unstackable inputs can retain stackable cells between output
collections. Each cell loads up to its stack capacity and the remaining requested
batches, then the executor refills only consumed/short cells. Cumulative output
and exact ingredient conservation are checked through the entire batch. UUID
items are treated as individual items even when vanilla reports a stack limit.

Native left-drag distributes a cursor stack across empty matching recipe cells
only when its even distribution equals every selected cell target. The real
client sends the native QUICK_CRAFT start/add/end sequence. Other action ports
fall back to the existing splitter. Post-drag inventory/grid acknowledgement,
conservation checks, pacing and bounded retries apply. Unequal cells use the
existing exact splitter. No shift-click into the grid is assumed.

Regression coverage proves four mixed crafts load the stackable cell once,
refill four unstackable items, and leave the grid/cursor empty; it also proves an
eight-cell drag plus exact ingredient consumption and output. Live Hypixel drag
behavior remains to be verified. 1349 Java tests passed and the release build
succeeded.

### 0.2.90 Exact stack splitting

Crafting compares repeated grid halves plus remainder placements against
individual placements, including cursor-return clicks. Odd stacks correctly
leave floor(n/2) in the grid. Existing exact-half reuse across matching recipe
cells remains available. Within a compatible component group, source selection
prefers fewer placement clicks, so an exact stack beats splitting a larger one.

Regression coverage exercises every requested amount for stack sizes 17, 31,
47, 63 and 64, verifies exact output and all leftovers, checks the 64-to-20 click
budget, and confirms exact source-stack selection. Server acknowledgement and
configured pacing remain mandatory for every click.

1347 Java tests passed and the release build succeeded. Live Minecraft testing
remains to be performed.

### 0.2.89 Timing limits and fresh-install defaults

The fastest delay accepts 1 ms and the slowest accepts 2 ms. New config defaults
and pre-config startup fallback use 60–70 ms. Existing saved settings retain
their configured values. Receipt verification, client ticks and server-response
waits remain in effect. The shared delay covers ingredient pickup, placement,
right-click splitting, output collection and Quick Craft. Crafting keeps its
50 ms minimum server-settle check. Regression coverage verifies 1–2 ms validation and the
new-install and startup defaults.

### 0.2.88 Loading prices and storage contents

Captured failure: the Dwarven Super Compactor Sell Instantly lore contained
`Total: Loading...`; the executor classified it as malformed and the run reopened
the menu repeatedly. Instant selling and buying now wait in place for explicit
loading placeholders. A genuinely blocked instant sale has a 30-second retry
cooldown. Existing overall price-loading deadlines still apply.

Storage scans, deposits and withdrawals wait for placeholders to disappear and
initial contents to remain stable for 400 ms before reading capacity or checking
ownership. Move receipt checks remain mandatory and loading during a receipt
never authorizes another click. Auction, craft, workstation, book and general
trade paths also wait for explicit loading contents with their existing or newly
bounded timeout. The configured fast action range remains unchanged.

1344 Java tests passed; release build succeeded. Live Minecraft verification
is still needed.

Regression simulations cover a delayed live sell total then exactly one verified
sale, delayed buy price, permanent loading timeout and delayed storage scanning.

### 0.2.87 Fast menu timing

The GUI and config now allow a 10–20 ms randomized action range. Navigation keeps
ownership while a destination window is empty, waits
for named controls to load, and never retries a source click against the new
window. Destination loading remains bounded by the existing 15-second timeout.
Crafting retains its minimum 50 ms acknowledgement settle and adaptive lag backoff;
a timing setting does not override server receipt verification or client tick rate.

Full Java regression suite: 1340 tests passed. Release build succeeded.
Live 10–20 ms Minecraft navigation has not been tested here.

Regression coverage includes delayed window contents, no duplicate navigation,
loading timeout, and validation of the 10–20 ms range.

# Testing the combined client

### 0.2.86 Shared action timing

Crafting and storage now use the existing shared randomized action delay, with
Fastest delay and Slowest delay controls in Macros → Limits. Tests verify a
one-second configured delay prevents a second click even after the server has
acknowledged the first, and permits it once that delay expires. Sampling tests
check configured bounds, actual variation and pre-configuration defaults.
Existing craft simulations explicitly inject their 50 ms test clock; runtime
construction uses the shared configured range. Storage page loading, inventory
proof, retry bounds and adaptive crafting slowdowns remain covered.

Validation: 1,337 Java tests passed with zero failures, errors or skips (1,210
client and 127 shared trading tests). The focused suites passed 87 tests. The
0.2.86-BETA release build succeeded; live Minecraft timing remains to be checked.

### 0.2.85 Full-inventory crafting handoff

The storage-backed round planner now makes room before crafting when no batch
fits. A regression recreates twenty Dwarven inputs across a completely full main
inventory and existing storage ownership, including a readable cleared Personal
Compactor. It verifies all twenty crafts finish, no Bazaar or stash purchase
command is issued, and the compactor/menu stay in inventory. Existing storage
and stash checks remain passing.

Validation: 1,334 Java tests passed with zero failures, errors or skips (1,207
client and 127 shared trading tests); the focused storage suite passed seven
tests. The 0.2.85-BETA release build succeeded. Live Minecraft validation remains
for the next user run. Saved interrupted jobs still require review after restart.

### 0.2.84 Pipeline purchase space and stash delivery

Validated on `claude/project-thread-wmox9w`: 1,333 Java tests passed with zero
failures, errors or skips (1,206 client and 127 shared trading tests), and the
release build succeeded. Focused storage/instant-buy/command coverage passed
44 tests before the final complete run.

A pipeline simulation purchases 4,096 inputs once, observes partial delivery,
stores verified units, collects two stash rounds and reaches processing with
the original quantity and one 40,960-coin cost record. A separate simulation
moves six loose inventory stacks into storage before submitting the funded
ingredient order, keeps the other twenty stacks in inventory, and verifies the
remaining storage-capacity reading. Unit coverage checks immutable whole-amount
pricing, multi-round unstackable stash delivery, compatible partial-stack space,
missing/unpaid stash outcomes and existing storage conservation/recovery.

The JAR version is 0.2.84-BETA. These are simulated menus and durable ledgers;
live Minecraft execution remains to be checked in the next user run.

Use Minecraft 26.3, Java 25, Fabric Loader 0.19.5 or newer and Fabric API
0.161.0+26.3. Install [the combined 0.2.112 JAR](dist/astar-client-0.2.112-BETA.jar)
and follow [installation and saved-data migration](docs/GOOFYADDONS.md).

Run `./gradlew -p client test calculatorIntegrationTest build` for the client
and `node --test tools/bazaar-calc/*.test.mjs tools/gameplay-collector/*.test.mjs`
for the calculator and collector.

The 0.2.27 craft pipeline update passed 1027 Java tests and 106 Node tests.
Coverage includes whole-batch depth/capital/volume/prerequisite gates, intermediate
craft costs, exact recipe pinning, profit rechecks before purchases, bounded sale
receipts, unknown historical cost basis, CRAFT-only engine composition, Coflnet
source validation and the local craft discovery endpoint.

A Chromium dashboard check passed at 1440px and 390px, including craft-plan
rendering, eligibility reasons, CRAFT-mode forecast separation, stale-data removal,
no overflow/XSS and no browser exceptions. Live Minecraft and Coflnet gateway
validation remain outstanding; see [the craft pipeline](docs/CRAFT-FLIP-PIPELINE.md).
Automatic compactor configuration, bulk receipts and cleanup remain unimplemented.
Automatic AH craft execution remains gated on sale/expiry/claim reconciliation.
See [the architecture review](docs/GOOFYADDONS-ARCHITECTURE-REVIEW.md) for wider follow-up work.

Older release notes under `docs/development/` describe the standalone trader.

The public craft-site update passed TypeScript/Vite builds and 113 Node tests,
including conservative depth/budget/requirements/volume gates, whole-batch sizing,
base preparation, fresh BIN validation, price-only fixed-destination Worker
requests, shared caching and failure handling. Run
`node tools/bazaar-calc/craft-planner-browser-check.mjs` for desktop/mobile profile
import, preparation steps, market filters, price refresh and stale-price removal.
Fixtures validate these flows; live owner deployment and real Coflnet access must
be verified separately after deploying the website assets and Worker together.

The 0.2.28 stacked-crafting tests simulate a 64-rod/128-powder bulk collection,
partial server collections using the same loaded grid, false outputs without
matching ingredient consumption, and capacity limits for unknown unstackable
outputs. Existing split, dropped-input, cursor, requirement, and journal tests
remain required. No test launches Minecraft or uses a real account inventory.
The final 0.2.28 client/test/calculatorIntegrationTest/build run passed 1032 Java
tests with no failures or errors. It also covers exact non-power-of-two loading
(47 items), the widened held-input craft command limit, and one-step intermediate
preparation before the final craft. The release JAR's version and crafting class
were checked. Live Hypixel behavior is not validated by these simulated tests.

0.2.29 account/startup checks cover direct fixed-HTTPS profile lookup without a
localhost request, Ender Pearl VI eligibility, unknown/expired/wrong-profile
rejection, service error/backoff and cancellation on profile changes. Node tests
prove the health port opens without reading website assets, and requested files
are cached after one read. A shipped-bundle integration fixture delays Node
startup for eight seconds, beyond the former roughly six-second window, then
checks it reaches READY without a failed launch. No real Minecraft inventory or
live Hypixel profile is used by these tests.
The final 0.2.29 run passed 1035 Java tests, 114 Node tests, and desktop/mobile
calculator and dashboard Chromium checks. The shipped resource ZIP and direct
profile-service URL were checked in the 0.2.29 JAR. The user's original Node
startup failure still requires its current diagnostics to identify precisely;
these tests validate the removed dependency and cold-start fixes, not access to
the user's live account or Cloudflare deployment.

0.2.30 adds a same-container blaze-powder → enchanted-eye crafting simulation and
a parent/child menu-ownership test. Both check verified output and handoff without
closing or reopening Craft Item. Existing transaction and shipped-calculator
integration checks remain required.

0.2.30 validation: 1,038 Java tests passed, including the shipped-calculator
integration suite and the owned Craft Item → Auction House transition. The release
JAR built successfully. These are simulated menu checks; live Hypixel execution
was not available in this workspace.

0.2.31 validation: 1,048 Java tests passed with no failures, including
shipped-calculator integration checks. Two sanitized live Compactor 7000 menu
captures validate populated and empty numbered controls. Simulations verify
all twelve removals, disabled devices, multiple devices, empty-menu closing,
menu ownership, durable intent failures, missing acknowledgements, unexpected
filter/inventory changes, saved-metadata verification and a production purchase
blocked until clearance finishes. The release JAR built successfully. Live
Hypixel opening/removal execution was not available in this workspace; keep
compactors in the hotbar for automatic opening. Bulk compaction remains disabled.

0.2.32 validation: 1,055 Java tests passed with no failures, including
the shipped-calculator integration suite. Four sanitized user AH GUI captures
validate co-op root navigation, empty BIN item insertion, compact fee/name
confirmation bound to the exact previously verified form, and receipt at the
player's own BIN view. Negative cases retain price/name/inventory/identity and
fee-debit checks. Recipe tests verify Aspect of the End's exact sword grid and
Ender Pearl VIII requirement; AH test input rejects unknown or multi-output
recipes. The 0.2.32 mod JAR built and its version/classes were checked. Live
Minecraft GUI execution and Coflnet requests were not available here.

0.2.33 validation: 1,058 Java tests passed with no failures, including
shipped-calculator integration checks. Selling now validates fresh Coflnet prices
and delegates directly to the existing Create Auction executor. Price tests
reject mismatched products, stale quotes and an explicit price outside the band.
The supplied underlying browser controls reproduce the expected search-sign
transition; search and BIN price signs are written once despite a transient
carried control, while a cursor still occupied on return blocks selection or
publication. Co-op creation, compact confirmation, own-listing identity and fee
receipt checks remain covered. The 0.2.33 JAR built and its version was checked.
Live Hypixel execution and live Coflnet responses remain for user testing.


0.2.34 validation: 1,061 Java tests passed, zero failures/errors, with the full
test suite, shipped-calculator integration checks and release build. Added
regressions cover ignored Bazaar amount/sign navigation, delayed AH creation
and confirmation controls, delayed browser controls/results, and bounded waits
for transient cursor state. Existing duplicate-transfer/publication and wrong
item/price/fee checks pass. Actual Minecraft server lag remains for live testing.

0.2.35 validation: 1,063 Java tests, 114 Node tests and 5 Python importer tests
passed with zero failures. The full Java suite, shipped-calculator integration
checks and release build passed. Catalog coverage checks account for every
source grid; new variants/books/collection gates and saved keys are verified.
Importer tests cover quantities, identity exclusions, unknown gates, removed
recipes, numeric/Roman gate deduplication and repeatable generation. Reimporting
the real source snapshot produced identical bytes. Desktop/mobile Chromium
checks passed for profile import, filters, quote refresh and expiry; the new
exclusion list wraps long IDs without horizontal overflow. The mod resource,
embedded calculator ZIP, website files and public website deployment ZIP contain
identical catalogs. Live crafting of all imported routes was not possible here;
server GUI evidence and account/market checks remain necessary during execution.

0.2.36 validation: 1,069 Java tests, 116 Node tests and 6 Python importer tests
passed with zero failures/errors. Full Java tests, shipped-calculator integration
checks and the release build passed. Regressions cover raw Slayer codes, HotM
aliases and boundaries, invalid/unknown account facts, published Slayer reward
claims, pre-purchase Forge access/slot/recipe gates and confirmation controls.
Desktop/mobile Chromium checks passed for profile import, ingredient preparation,
Bazaar/AH filters and quote refresh/expiry. Live Hypixel crafting and Forge
submission remain for user testing.

0.2.37 validation: 1,074 Java tests, 120 Node tests and 6 Python importer tests
passed with no failures/errors. The complete Java suite, shipped-calculator
integration tests and release build passed. New cases cover Catacombs boundaries,
normal/Master floor identity, unknown/invalid API data, all 48 product gates,
website requirement parsing, essence rankings and pre-purchase ingredient locks.
Desktop/mobile Chromium checks passed. Packaged catalogs and profile modules
match the source files. Live Hypixel transactions remain for user testing;
mutation analysis/analyzer reward evidence is unavailable from the profile API
and remains blocked rather than assumed.

0.2.38 validation: 1,075 Java tests and 122 Node tests passed without
failures/errors. Full Java tests, bundled-calculator integration tests and the
release build passed. Profile regressions cover the current skill-tree/nested
Slayer layout, legacy fallback, modern-field precedence, empty published maps,
invalid/unpublished fields, analyzed-versus-discovered Garden crops and claimed
analyzer milestones. The mod verifies published Garden unlocks against profile
identity, age and value bounds. Desktop/mobile browser checks passed. The live
Worker reproduced curedmc's missing progression in its prior parser; corrected
raw Hypixel data could not be fetched here without its private deployment key.
The field mappings are verified against the current SkyCrypt backend/schema;
a live user lookup must be checked after redeployment.

Collector patch 0.2.39 validation: all 123 Node tests passed. Regressions verify
all six Slayer zero counters, missing/invalid whole sections, malformed explicit
XP, published empty Garden analysis, current/legacy field precedence and
removal of the irrelevant vanilla XP warning. This patch changes no mod code
or calculator UI; no additional Java test run is required for its final scope.

### Craft calculation coverage 0.2.40

- Node: 132 tests passed, covering every distinct shipped craft output, missing
  prices, AH input quote identity/expiry, recursive batch costs/unlocks, cycles,
  per-output listing fees, catalog quote rotation and search prioritization.
- Java/companion: `test calculatorIntegrationTest build` successful; 1,075 test
  results with no failures/errors/skips. Lazy website loading remains verified.
- Website TypeScript/Vite build passed. Chromium desktop/mobile checks passed
  for imported unlocks, base preparation, BZ/AH filtering, refresh and expiry.
- Mod/website ZIP inspection verified catalog/runtime contents and confirmed
  the public package preserves existing deployment configuration.
- Coflnet direct connectivity from this environment returned a proxy HTTP 403;
  AH price behavior was verified with deterministic provider fixtures, not a live
  purchase or listing. In-game execution was not exercised for this UI update.

### Public craft collector asset path 0.2.41

Live deployed `/v1/crafts/market` returned HTTP 503, `Craft catalog unavailable`,
while the external catalog URL returned HTTP 200. Corrected the direct asset
binding path to `/data/production-recipes.json`. The Worker test now uses a
binding fixture that returns HTTP 404 for other paths and verifies catalog-only
outputs and AH components receive quotes. Collector and craft market tests pass.
Redeploying the user's Worker is required; it was not deployed from this workspace.

### Cloudflare craft fetch invocation 0.2.42

Live endpoint returned HTTP 200 with an `Illegal invocation` discovery error,
while an individual Aspect of the End BIN quote was present. Corrected the method
receiver for injected global fetch. Added a strict receiver test and preserved
item-specific BIN HTTP failures in the response/UI. All 133 Node tests and the
TypeScript/Vite website build passed. Worker redeployment is still required to
verify the corrected discovery against the live provider.

### Independent AH demand 0.2.43

Live response confirmed discovery HTTP 404 and zero demand-bearing rows despite
fresh BIN quotes. Authoritative Coflnet gateway source specifies singular
`/api/craft/profit` and per-item `/api/item/price/{item}` sales summary fields.
Tests enforce those destinations and cover independent demand without profitable
craft discovery, five-minute cache/expiry, malformed values, upstream errors,
and preserved zero sales. All 136 Node tests passed. Live demand after deployment
cannot be verified until the owner's Worker is redeployed.
The final website build and desktop/mobile Chromium checks passed. Gradle
`calculatorIntegrationTest build` succeeded and the final JAR's bundled collector
and companion match the corrected sources.

### Searched BIN quote race 0.2.44

The deployed 0.2.40 frontend still focuses its catalog search correctly. Reproduced
missing focused quotes when a general collection ran immediately beforehand or
was still in flight. Fixed explicit item collection during that global cooldown
with per-item throttling and in-flight deduplication. All 139 Node tests passed,
including the public Worker endpoint's general-then-search sequence. No UI code
changed in this patch. The owner must redeploy to validate it against their page.

### Persistent AH collection 0.2.45

- All 147 Node tests passed, including real SQLite/D1-compatible queries for
  additive schema creation, persistence across store instances, 15-minute bucket
  deduplication, 30-day retention, zero sales, persistent rotation, scheduled
  collection without visitors/tokens, independent failure handling and separate
  GitHub exports that skip unchanged datasets.
- Calculator tests show that retained references produce labelled research
  estimates and cannot become feasible without fresh price checks. Live quotes
  take priority; stale historical references are excluded.
- TypeScript/Vite website build passed. Gradle `test calculatorIntegrationTest
  build` succeeded with the history-aware companion/UI bundle.
- The owner's Cloudflare deployment and GitHub market publication must be verified
  after deploying the full new package; fixture success is not a live cron run.
The final Chromium desktop/mobile run also verified a missing live AH quote falls
back to a visibly labelled historical estimate. Mod and public ZIP contents were
verified, including both new Worker runtime modules and config preservation.
Java result files report 1,075 tests with zero failures, errors or skips.

### 0.2.46 reference-page refresh

The Chromium craft planner check also navigates all five Reference pages at
1440px and 390px, checks current instructions and absence of obsolete
serverless/Discord/manual contribution claims, verifies page widths, and reads
the linked market/history/catalog/license endpoints. TypeScript/Vite compilation
and the bundled calculator integration build verify the maintained overlay.

0.2.47 adds a browser regression for searching Hephaestus Relic from the lower
research-table search: the complete catalog search mirrors it and exposes the
correct recipe and Minos Relic ingredients. Existing craft refresh, stale-price,
profile, historical AH and reference-page checks remain part of that run.

### Website 0.2.48 immediate publication check

151 Node tests pass. New tests prove owner-only authentication fails closed,
GET cannot publish, both datasets execute serially with sanitized results,
one GitHub failure does not suppress the other publisher, leases block
concurrent manual/cron writes, locks release after failures, and expired leases
recover. Existing scheduled AH collection/publishing and public route tests pass.

### 0.2.49 Bazaar status visibility

Chromium desktop/mobile checks assert LIVE Bazaar status with six fixture
products and bundled-history labels. A 90-second-old snapshot must show STALE
and hide fresh coverage counts. Existing craft searches, account imports,
price refresh, AH historical references and all five reference pages remain
covered. TypeScript/Vite and calculator integration/build checks validate the
public assets and bundled mod.

### 0.2.50 automatic craft pipeline foundation

Added regressions for complete-basket affordability, later ingredients lacking
quotes, essence unlocks during ranking, market changes before submission,
evidence-based cancellation, and a complete empty-inventory buy → pinned craft
→ Bazaar sale run. The full-loop fixture proves 200 coins of acquired input cost
and 978 coins of sale proceeds, records each callback exactly once, and continues
purchase verification after a post-submission market block. Existing crafting,
compactor clearance, lag/navigation, journal recovery, account requirements and
profit evidence tests remain enabled. No live Minecraft session is available in
this environment; receipt/menu fixtures validate orchestration before the user
runs the macro in game.

Final 0.2.50 validation: 1,081 Java tests, zero failures/errors/skips; Gradle
`test calculatorIntegrationTest build` succeeds. The open-amount-editor regression
also proves a market block causes no close or submit action until prices recover.

### 0.2.51 scheduled-rest rendering fix

The user's exported diagnostics reproduce the exact stack path:
AstarLoading.extractRenderState → ScheduledRestScreen.extractRenderState →
Screen.extractBackground → GuiRenderState.blurBeforeThisStratum, with
"Can only blur once per frame" during scheduled disconnect on B.
The rest screen no longer requests blur and instead submits a plain rectangle.
Compile/build and existing schedule/craft tests cover API compatibility and
unchanged scheduling behavior. Rendering under a real Minecraft loading overlay
still requires the user's in-game confirmation; no live graphics session is
available in this environment.

### 0.2.52 settings autosave

SettingsAutosaveTest exercises delayed typing, latest-value persistence, immediate
flush, invalid-input correction, external keybind preservation, deferral during
trading, and failure suppression with explicit retry. GUI wiring is compiled by
the full mod build; in-game input confirmation remains a user smoke test.

CraftFlipPlannerTest also verifies that a profitable recipe below the configured
10,000 net-profit floor reports all 16 excluded batch sizes, and that an
unobserved budget produces its own reason instead of retaining prior counts.

Final 0.2.52 validation: 1,087 Java tests, zero failures/errors/skips;
`test calculatorIntegrationTest build` succeeds.

### 0.2.53 A* GUI integration

Import upstream pixel controls from c2b54c5 while preserving autosave, masked
contributor fields and keybind capture. Compile the reorganized settings tabs and
adaptive rows with the complete Java/calculator integration suite. UI smoke steps
are in docs/GUI-INTEGRATION.md; no display/Xvfb runtime is installed here, so real
Minecraft rendering and clicks are not claimed as tested.

Final 0.2.53 validation: 1,087 Java tests, zero failures/errors/skips;
`test calculatorIntegrationTest build` succeeds. Release JAR includes the Pixel
controls and the bundled calculator.

### 0.2.54 purchase proof and craft refresh

Regression replays cover preset purchase → unreadable purse (-1/NaN/infinity) →
readable debit with exactly one purchase intent; confirmation purchase → unknown
purse → genuinely excessive debit still enters review. A sale replay rejects an
unknown initial balance without a click, then confirms exactly one sale once both
inventory and purse support it. CraftRankingRefreshTest covers a late first
Bazaar response before the periodic deadline, new source timestamps, saved-setting
changes and account resets. ProductionReviewTest covers persistent evidence and
foreign/active/ambiguous/short-prefix rejection. Live Hypixel timing still needs
the user's in-game test.

Release validation: Java tests, calculator integration tests and the mod build
passed; 1,094 tests, zero failures, errors or skips. The 0.2.54 JAR includes the
bundled calculator and integrated GUI. No public Worker deployment is required.


## 0.2.55 multi-strategy Bazaar pipelines

ProductionRunTest drives order procurement → verified input handoff → crafting →
output offer → verified coin claim, checking persisted cost and exactly-once sale
accounting. An uncertain input order cannot queue crafting or fabricate basis.
ProductionOrderHandoffTest drives the reused ordinary order engine: claimed
ingredients never become sell offers, basket ceilings replace ordinary-item entry
profit filters, and pre-existing inventory/orders cannot be overwritten.
BookInstantRoutesTest drives the book engine through verified instant purchase
and sale, including unreadable purse updates and single transaction submission.
ProductIdentityTest rejects wrong-level and multi-enchantment book lookalikes.
CraftFlipPlannerTest covers a recipe profitable only with order inputs and an
output offer. The adapter test verifies strategy-aware book selection, one winning
strategy per route, arithmetic, budget and legacy order/offer compatibility.

Validation: mod build, Java suite and calculator integration checks passed;
1,103 Java tests with no failures/errors/skips. The adapter suite passed all 20
Node tests, including the local HTTP server checks. In-game server timing and
full GUI navigation still require player testing. Production offers currently
wait for full settlement without repricing; interrupted craft order journals are
retained for review. No public Worker deployment was performed.


The 0.2.56 recovery update passes 1,097 Java tests via
`./gradlew -p client test build`. New tests verify bulk REVIEW acknowledgement
preserves cost/reason evidence, leaves foreign accounts and non-REVIEW states
untouched, survives reload and rolls back all in-memory changes on a failed save.
Minecraft chat/HUD and live inventory reconciliation require an in-game check.


The 0.2.57 order-lore/recovery update passes 1,102 Java tests through
`./gradlew -p client test build`. Tests cover the reported 7/1.3k fill with an exact
1,280-unit order total, rounded numerator limits, missing/conflicting totals,
malformed suffixes, recovery refusal for unsubmitted buys/unknown costs/sells,
and live-style cancellation/refund verification before the durable child handoff.
In-game command/scheduler and partial claim/refund behavior still need live testing.


The 0.2.58 concurrent craft portfolio passes 1,115 Java tests via
`./gradlew -p client test build`. Regression coverage includes a craft completing
while a sibling waits, parent-scoped order persistence and claim consumption,
failure attribution, duplicate/legacy parent handling, whole-recipe capital versus
liquid-purse reservations, claimed versus future inventory slots, the shared claim
buffer, foreground menu leases and delegated craft priority. Old craft settings
default to three active jobs and reject concurrency limits outside 1–4.
Compilation covers the new Settings row and HUD/debug queue data. Multiple real
Hypixel orders and GUI timing remain untested in Minecraft; test with a small
capital limit and inspect the active queue in a debug export. Automatic AH craft
settlement remains gated, and interrupted legacy ownership still requires recovery.


The 0.2.59 ingredient outbid update passes 1,118 Java tests via
`./gradlew -p client test build`. New coverage drives cancellation of an outbid
unfilled ingredient order through rounded ownership and refund verification,
checks fresh-data/cooldown rules and manual recovery isolation, and verifies
zero/partial-fill handoff followed by whole-basket blocking and missing-only
replacement. The recovery test also exercises default nonzero general reprice
limits to ensure production never falls into standalone item repricing.
Real server fill races and menu latency still need in-game validation.


The 0.2.60 decorated gemstone order update passes 1,120 Java tests via
`./gradlew -p client test build`. Regressions cover strict gemstone aliases,
wrong tier/type/side refusal, mixed-alias duplicate detection, and the decorated
35/1.3k partial claim → options → cancel → verified refund/inventory handoff.
Menu fixtures supply readable item stack metadata; unknown stack limits remain
conservative. Actual server metadata and interrupted account state require a
fresh debug export if the order is still blocked in game.


The 0.2.61 craft batch/ranking update passes 1,123 Java tests via
`./gradlew -p client test build`. New tests use the catalog's actual 32-output
Hotspot Bait recipe to verify 64-operation selection, explicit strategy filtering,
old 16-operation limits, complete output stack reservations and reduction to 60
operations with four reserved slots. Separate coverage proves offer ranking changes
with observed sales activity while batch net remains unchanged, and checks new
strategy/batch defaults and invalid settings. The existing GUI executor still caps
child jobs at 64. Live fill rates are unverified; hourly values are estimates and
remain distinct from the public website's calibrated model.


The 0.2.62 crafting-menu/orders handoff fix passes 1,125 Java tests via
`./gradlew -p client test build`. New driving tests verify closing a yielded Craft
Item menu before requesting orders, preserving the tracked position without any
purchase clicks, and excluding delegated crafting time from an unstarted orders
navigation deadline while submitted verification still times out. Existing
unexpected-menu, order ownership, cancellation and strategy tests also pass.
The empty-portfolio stop guard compiles; actual profile transitions and server GUI
latency still require in-game validation. Previously retained orders are preserved.


The 0.2.63 mixed craft recovery update passes 1,127 Java tests via
`./gradlew -p client test build`. New driving coverage accepts mixed ingredient and
unsubmitted output records, hands off only verified stock, persists verification
until archival, and produces no purchase/sale clicks or proceeds. Mismatched
inventory and existing sell orders refuse output recovery. Existing cancellation,
refund, unknown-cost and submitted-sell refusal tests remain passing. The recovery
controller's archival path compiles; live mixed queue recovery still needs testing.


The 0.2.64 unsubmitted ingredient rejection fix passes 1,129 Java tests via
`./gradlew -p client test build`. Coverage verifies no acquired cost or REVIEW pause
for a known pre-submission rejection, a 30-second retry delay, fresh whole-basket
profit blocking before the next attempt, and preserved reconciliation requirements
for submitted positions. Existing price-ceiling coverage now asserts durable
rejection rather than a false transaction failure. Actual provider/GUI mismatch
causes remain unverified; price ceilings are not relaxed.

## 0.2.65 craft scheduler audit

`./gradlew -p client test build --console=plain` passed 1132 Java tests with no failures or errors. Added regressions for blocked procurement yielding and waking, compactor verification during market changes, and child order status visibility. Live Minecraft execution remains unverified.

## 0.2.66 debug evidence

The final `./gradlew -p client test build --console=plain` run passed 1137 Java tests with zero failures/errors. Coverage includes persistent incident retention, redaction/oversize handling, read-only run export, unchanged-state monitoring, and per-output exclusions with ranking equivalence. An oversized-message regression exposed expensive email matching; messages without an at-sign now bypass that scan. Live in-game export remains unverified.

## 0.2.67 verified empty craft reselection

`./gradlew -p client test build --console=plain` passed 1139 Java tests with zero failures/errors. Added regressions for fully refunded zero-input handoff allowing stale-plan cancellation, and partial fills/unexpected held ingredients preventing it. Existing unknown-intent and submitted-purchase guards remain covered. In-game execution remains unverified.

## 0.2.68 A* status GUI and logo

`./gradlew -p client test build --console=plain` passed 1140 Java tests with zero failures/errors. Existing responsive layout checks plus a new short-screen task-priority regression passed. The packaged animated logo exactly matches upstream 4bf09c3 (24,659 bytes). In-game rendering and button interaction remain unverified.

## 0.2.69 rejected ingredient plan reselection

Added regressions for an acknowledged zero-input preflight rejection immediately
reaching safe cancellation, and a rejection after partial acquisition preserving
cost, input ownership and the 30-second delay. The existing delayed-retry and
fresh-basket blocking coverage remains applicable to retained/manual runs.

`./gradlew -p client test build --console=plain` passed 1142 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

## 0.2.70 partial ingredient replacement

Regressions cover a parent-scoped replacement with existing stock, durable
baseline persistence, full-fill detection requiring additional units, partial
and zero-fill cancellations preserving existing stock, and full claims using
inventory deltas. Legacy unscoped inventory refusal remains covered.

`./gradlew -p client test build --console=plain` passed 1145 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

## 0.2.71 partial-fill allocation expansion

New regressions use the exported 28,222.8 spent/910,188.752 remaining basket,
verify configured and zero-cap behavior, refuse profit/capital/sibling-reservation
violations and invalid quotes, and restrict run expansion to the interval after
verified partial handoff and before queueing another order.

`./gradlew -p client test build --console=plain` passed 1148 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

## 0.2.72 product navigation settling

Regression coverage verifies the 750 ms product-control settling interval,
reversible named create-order retries despite changing price lore, and refusal
to retry changed actions or anonymous confirmation controls. Existing bounded
retry, exact product identity and unsafe-cursor tests remain applicable.

`./gradlew -p client test build --console=plain` passed 1151 Java tests with zero failures/errors. Existing product-navigation fixtures now wait through settling without changing their identity/submission assertions. Live Minecraft execution remains unverified.

## 0.2.73 rounding-safe craft procurement

Regressions reproduce the exported 4,482,805.552 versus 4,482,805.551999999
comparison, reject a genuine 0.01-coin overrun and invalid funds, and verify
order procurement proceeds at a one-bit budget difference while refusing a
real shortfall. Existing price-ceiling and portfolio allocation tests remain.

`./gradlew -p client test build --console=plain` passed 1153 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

## 0.2.74 submitted sell-offer continuation

Regressions verify original parent ID/cost retention, a SELL-only continuation
that queues no purchases/crafts/acquisition, verified-proceeds completion,
rejection of mismatched quantity/output and genuine uncertain reviews, and
refusal to reattach pending settlement/cancellation, buys, unsubmitted positions
or missing parent identity.

`./gradlew -p client test build --console=plain` passed 1156 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

## 0.2.75 funded ingredient-order baskets

Regressions verify every required ingredient for three craft batches queues
before any fill, combined verified cost transfers once, complete basket journals
survive validation, premature handoff preserves all children, unfunded baskets
release reservations and queue nothing, and rejected children do not count as
acquired stock or hide a sibling's cost. Existing cross-parent handoff, partial
claim, baseline, recovery and journal-integrity coverage remains passing.

`./gradlew -p client test build --console=plain` passed 1160 Java tests with zero failures/errors. Live Minecraft execution remains unverified.

### 0.2.76 Quick Crafting

Added regression coverage for the exported 15-batch Hotspot Bait quick slot (one shift-click, 480 verified output), excessive craftable amounts falling back to the grid, mismatched ingredient lore, exact partial receipts, and delayed acknowledgements with invalid output-only changes. Existing grid crafting, menu reuse, conservation checks, procurement baskets, and sale recovery remain covered. No live Minecraft GUI was available.

Validation: client test/build succeeded; 1,165 tests passed with zero failures or skips. Release jar metadata verifies 0.2.76-BETA.

### 0.2.77 Review acknowledgement ownership

Regression coverage verifies that unrelated retained sell offers allow a specific reviewed job to be acknowledged, while its own orders (including completed but unreconciled children) and unknown legacy ownership block it. A journal test checks the full resolved ID reaches the ownership predicate, blocked acknowledgement does not write, unrelated reviews stay unchanged, and the Bait parent’s original cost evidence remains intact. No live Minecraft GUI was available.

Validation: client test/build succeeded; 1,167 tests passed with zero failures or skips. Release jar metadata verifies 0.2.77-BETA.

### 0.2.78 Sell repricing and pipeline gaps

Regression checks cover production undercut detection with zero general reprice allowance, cooldown, minimum-profit waiting, and returned-stock waiting; a complete 3-of-10 partial claim/cancel/relist of only the remaining 7/final coin-claim cycle; aggregate receipt idempotency and unknown proceeds; partially sold and completed receipt-backed restart handoffs; tax and original quote floors; consumed versus leftover ingredient locks at the verified sell boundary; new sibling purchases staying independent; concurrent input wait ranking; and the 64/65 batch boundary. Initial focused suites passed before the full release run. No live Minecraft GUI was available.

Final validation: client test/build succeeded; 1,188 tests passed with zero failures or skips. Release jar metadata verifies 0.2.78-BETA.

### 0.2.79 Exact production book variants

Regression coverage uses raw `ENCHANTED_BOOK` stacks carrying Quick Bite I
metadata through the real grid executor, its shift-click result, Quick Crafting,
book ingredient cursor/grid accounting and unchanged foreign-level stock. Wrong
levels and multi-enchantment results never click; a delayed correct result can
recover before the eight-second bounded result wait. Search/receipt names use
the bundled enchantment rules, including hyphenated names and Ultimate Wise;
ordinary Enchantment Table naming is preserved. Production order tests cover
scoped journal round trips, invalid levels/identities, funded book ingredient
baskets, exact sale ownership, replacement baselines, actual book claims, exact
search metadata and wrong/multi-book product/confirmation refusal. Core tests
cover canonical identity without changing raw book-flipper behavior, exact
counts/capacity versus aggregate books, workspace ownership and book-family
capital conflicts while preserving exact costs, quantities and release owners.

The uploaded 0.2.78 session independently confirms three repriced offers and
coin receipts, totaling approximately 1.57M coins of confirmed profit. Its
historical Quick Bite result-slot metadata was not retained; the catalog/raw
book mismatch is demonstrated by the regression fixture. No live Minecraft
GUI was available for this update.

### 0.2.83 External ingredient handling and failure diagnostics

Nine engine regressions cover explicit reconciliation of the original Mycelium
child, unchanged ownership before review archival, preserved unrelated Fuming
positions, fresh stable menu verification including live captures without packet
counters, account/capture changes, owned buy/sell orders, co-op creators,
pagination, inventory/cursor blockers, stopped-state and prefix validation,
bounded navigation, cancellation on stop and journal-removal rollback. No
acquisition, sale or settlement is generated by external handling.

Five durable handoff regressions verify that REVIEW evidence is saved before
the release callback, failed archival never invokes removal, failed removal
retains the review, same-ID retries do not duplicate evidence, and conflicting
records or wrong-account/unreviewed parents prevent release. Three diagnostic
regressions preserve pre-close menus and target counts, saved refund availability
and partial sell receipt/cancellation state.

Validation: client test/build succeeded; 1,263 Java tests passed with zero
failures, errors or skips (1,139 client and 124 shared trading tests). The focused
suite passed 29 tests. Release jar metadata verifies 0.2.83-BETA. No live
Minecraft GUI was available; the next user run must verify actual menu behavior.

### 0.2.82 Rejected cancellation recovery and initial budget headroom

Nine new cancellation regressions exercise an explicit server rejection followed
by fresh exact-owned-order inspection, new buy fills before retry, delayed refund
and named cancellation receipts before and during retry review, preservation of
partial sell proceeds, foreign/duplicate/repriced order rejection, unknown-outcome
retention, stopped recovery, and three-attempt exhaustion. Submitted sibling sell
offers and their capital remain untouched. Existing ordinary driving and mixed
production recovery suites pass alongside this coverage.

Funding regressions use the exported Crimsonite and Moondew quote changes and
verify bounded initial price-movement room under the profit floor, per-craft cap
and live fair share. Four near-share jobs retain their full allocations. The
planner accepts double-rounding differences while rejecting a real coin overrun.

Account tests preserve Hypixel authorization failures, safe upstream service and
HTTP status facts, invalid/unavailable responses, and HTML gateway errors without
granting unknown unlocks. Worker lookup, public-site routing and community
integration tests pass. A live public lookup for `curedmc` confirmed the deployed
Worker wraps a Hypixel HTTP 403 rejection in HTTP 502; restoring that external
authorization requires a valid Worker API key and application permissions.

Validation: client test/build succeeded; 1,246 Java tests passed with zero
failures, errors or skips, plus 35 Worker/public-site/community checks. Release
jar metadata verifies 0.2.82-BETA. No live Minecraft GUI was available for this
update, and the Worker authorization has not been changed or redeployed here.

Limited-inventory planner regressions also verify book input/output batches and intermediate workspace shrink before any orders are placed.

Final validation: client test/build succeeded; 1,212 tests passed with zero failures, errors or skips. Release jar metadata verifies 0.2.79-BETA.

### 0.2.80 Craft menu loading and mixed-position recovery

The exported Hotspot Bait race is reproduced with sixteen ingredient pairs,
seven occupied inventory slots and an initially unreadable Quick Crafting
control. Tests verify a no-click wait followed by one shift-click and 512
verified outputs; excessive craftable amounts or mismatched recipe lore still
use the exact grid recipe. Missing metadata times out after eight seconds with
ingredients intact, unrelated icons cannot supply stack capacity, and a changed
container cannot adopt the pending wait. A parent-run test verifies that the
capacity wait reaches the HUD without repeated craft queueing.

Mixed-position tests exercise actual sale receipts with a retained ingredient
buy and actual buy refunds with a retained sell offer. They verify scoped menu
ownership, unchanged sibling timing and costs, preserved capital reservations,
recovery completion with a sell offer still present, and rejection of unsettled
or interrupted cancellation journals.

Validation: client test/build succeeded; 1,223 tests passed with zero failures,
errors or skips. Release jar metadata verifies 0.2.80-BETA. No live Minecraft
GUI was available; gameplay timing still needs verification.

### 0.2.81 Component-aware craft batches and progress bounds

Regression fixtures now preserve item metadata during real simulated pickup,
splitting, returns and consumption, and swap incompatible stacks instead of
merging them by product ID. Four Dwarven Super Compactor crafts complete in
separate rounds for UUID-distinct upgrades and for distinct component hashes
without UUIDs, preserving the 1,110,533-coin journal basis. Compatible fragmented
inputs still craft a whole batch. A forced merge-swap loop with unrelated metadata
churn stops after three repeated attempts, retaining its inputs and sending no
output click. Allocation tests verify unequal cells of 3/4/5 from component
groups of 5/7, and equal cells of 2/2 from groups of 3/3 with both surplus items
returned under their original identities.

Detailed-menu coverage checks stack limits, selected item UUIDs, vanilla IDs,
component hashes and accessible inventory while excluding raw components and
other item metadata. Existing Quick Crafting, book variants, packet retries,
conservation, procurement, recovery and trading tests remain passing.

Validation: client test/build succeeded; 1,230 tests passed with zero failures,
errors or skips. Release jar metadata verifies 0.2.81-BETA. No live Minecraft
GUI was available for this update.
