# GoofyAddons file-by-file audit

Audited 2026-10-02: current workspace version 1.3.1-BETA. This report supersedes the narrower safety conclusions in SEQUENCE_AUDIT.md. Runtime fixes are deliberately deferred until the repair plan is reviewed.

## Scope and evidence

Reviewed client and main Java sources, all ten test sources, resources, configuration examples and build/release definitions. Binary assets and wrapper jar were inventoried rather than certified. Findings below distinguish confirmed implementation gaps (source evidence) from possible runtime failures needing replay/live fixtures. A confirmed code gap does not mean coin loss has been observed. This review cannot establish that every possible server-dependent bug has been found.

Priority: P0 = money/ownership or emergency-stop integrity; P1 = transaction/recovery correctness; P2 = reporting, usability, availability or engineering resilience. The original findings are retained below. Repair status is recorded in the dated status section; an entry is not closed unless its validation scope is stated.

Backlog: 33 confirmed implementation/coverage gaps and 15 investigation items. See FULL_REPAIR_PLAN.md for dependencies and acceptance gates.

## Confirmed findings

### A01 — P0: Safety pause depends on diagnostics succeeding

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/FeatureManager.java:119`. safetyPause builds detailedSnapshot before setting paused/statusReason. Snapshot calls engine activity and parses current candidate data. An exception here prevents the pause itself.

**Trigger / consequence:** Malformed product data while general activity computes candidates, followed by a transaction exception.

**Repair / acceptance:** Set the safety latch before observation; make diagnostics best effort. Inject snapshot failure and verify no further command or click.

### A02 — P1: Stop input is consumed after trading actions

**Evidence:** `src/client/java/com/goofy/goofyaddons/GoofyAddonsClient.java:37`. FeatureManager.onTick runs before stopKey.consumeClick. A requested stop can share a tick with an order confirmation.

**Trigger / consequence:** Press stop while confirmation is eligible.

**Repair / acceptance:** Consume emergency stop first; replay a queued stop and assert zero subsequent clicks that tick.

### A03 — P0: Book placement advances without server acceptance

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:659`. Buy confirmation clicks slot 13, immediately calls purchased and changes task state; sell confirmation similarly marks SELL_ORDER. There is no dedicated placement verification state.

**Trigger / consequence:** Server rejects, delays, or drops the confirmation.

**Repair / acceptance:** Persist submission intent; verify exact order or exact inventory/receipt outcome before advancing or freeing pending cash. Test rejected and delayed submissions.

### A04 — P0: Confirmation contents are incompletely verified

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:274`. General accepts a null preview quantity and does not validate preview product or price. Book confirmation guards side and quote age, but not product, quantity or price.

**Trigger / consequence:** A same-side confirmation contains an unexpected product/quantity/price or incomplete lore.

**Repair / acceptance:** Require supported complete preview identity and expected quantity/price before clicking; unknown previews pause. Test mismatches and unloaded previews in both engines.

### A05 — P1: Final confirmation does not repeat price and budget checks

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:638`. Price/profit and budget checks occur on the price screen. Confirmation checks freshness, rather than revalidating the intended transaction against the latest purse and quote. General follows the same pattern.

**Trigger / consequence:** Purse or market changes between choosing price and confirming.

**Repair / acceptance:** Repeat limits using the actual preview immediately before submission. Test a price drop and purse debit during that interval.

### A06 — P0: Book order matching omits quantity and price

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:323`. Book orders are checked for creator/duplicates, but subsequent selection matches display name without validating expected amount or reserved price.

**Trigger / consequence:** An existing own order has the same enchantment and a different amount or price.

**Repair / acceptance:** Use a validated order observation tied to tracked intent; reject ambiguous adoption. Test same-name orders with wrong quantities and prices.

### A07 — P1: Book order branches accept broad Bazaar titles

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:709`. OUTBID, SELL and REPLACE_SELL use containerNameCheck("Bazaar") and one occupied slot instead of requiring an orders screen. The strict orders-title guard only runs when the title already matches.

**Trigger / consequence:** A product/search screen remains open while an orders step executes.

**Repair / acceptance:** Every step requires a specific screen kind and complete expected controls; replay a wrong Bazaar screen and assert no order inference or click.

### A08 — P1: Storage disappearance is treated as successful movement

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:825`. STORE sets location to a storage page when no matching inventory slot exists; ANVIL sets location to inventory when the storage match is absent. These branches do not require a destination increase.

**Trigger / consequence:** An item is missing, menu contents are incomplete, or the wrong storage page opened.

**Repair / acceptance:** Require matched source decrease and destination increase in the same validated transfer context; missing evidence retains ownership and pauses.

### A09 — P1: Storage page identity is not verified

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java:780`. The engine accepts any Ender Chest/Jumbo/Greater Backpack title and associates it with usingSecondPage. It never verifies which configured page the server actually opened.

**Trigger / consequence:** A storage command fails, opens a different page, or an old storage menu remains.

**Repair / acceptance:** Capture and validate page identity; wrong page cannot update location. Use page-one/page-two fixtures and rejected-command replay.

### A10 — P1: Book instant modes are selectable but deliberately halt

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/FlipCalculator.java:94`. Calculator can select instaBuy/instaSell from valid percentage settings; the execution engine explicitly safety-halts on those paths.

**Trigger / consequence:** A valid route crosses its instant threshold.

**Repair / acceptance:** Until verified instant sequences exist, exclude them from executable candidates and explain unsupported settings. Test every supported configuration path.

### A11 — P1: Book recovery records cannot reconstruct a transaction

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BookJournal.java:11`. Journal records only Book and cost; task stage, quantities, locations, trade ID and pending claim identity are not persisted. Restart correctly blocks, but requires manual archiving rather than a supported reconciliation flow.

**Trigger / consequence:** Restart with outstanding books or a partial claim.

**Repair / acceptance:** Introduce versioned transaction records and explicit evidence-based reconciliation. Never automatically resume an uncertain journal; test restart at every action boundary.

### A12 — P2: Book recovery short-circuits loading general exposure

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/FeatureManager.java:52`. startConfigured returns on book recovery before general.restoreBudget. This blocks GENERAL mode and leaves the capital/HUD view incomplete until book recovery is resolved.

**Trigger / consequence:** Both journals contain positions and the book journal requires review.

**Repair / acceptance:** Load and validate all exposure before deciding mode eligibility; keep unresolved capital reserved and provide a complete recovery view.

### A13 — P1: General cancellation detail is not revalidated

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:467`. cancelDetail accepts any title containing Order and clicks Cancel Order without checking the detail product, quantity, creator or expected price again.

**Trigger / consequence:** The user/server replaces the detail menu after the original order selection.

**Repair / acceptance:** Bind cancellation to validated detail identity and container context. Wrong detail must never be cancelled.

### A14 — P2: General claim fallback checks absence in arbitrary menus

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:496`. The inventory-delta fallback uses findOrder(selling)<0 without ordersReady. An order detail or other screen can therefore be interpreted as absence and closed prematurely. Later verification is stricter, so this is a sequence/timeout defect rather than proven lost ownership.

**Trigger / consequence:** Inventory arrives while a detail menu is open.

**Repair / acceptance:** Only infer absence from a complete validated orders snapshot; replay delayed inventory during detail navigation.

### A15 — P1: General recovery can adopt returned sell inventory without settling sold units

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:450`. The no-order/inventory-present branch changes the tracked quantity and stage and records acquisition. A previous SELL_ORDER can enter it without verifying whether some units sold or where their proceeds went.

**Trigger / consequence:** An own sell offer is manually cancelled after a partial fill.

**Repair / acceptance:** Require a reconciliation path for prior sell positions; preserve original quantity/cost and account for confirmed sold units or flag the trade incomplete.

### A16 — P1: General state validation misses transaction fields

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:771`. Loader validates identity, quantity, unitCost and stage, but not sellPrice, timestamps, reprices, trade/sale IDs or legal flag/stage combinations.

**Trigger / consequence:** A malformed or partially migrated position contains impossible transaction state.

**Repair / acceptance:** Version schema and validate stage invariants; preserve and reject invalid records. Test each field and inconsistent combinations.

### A17 — P2: Pending settlement has no supported reconciliation command

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java:323`. Any persisted settlementPending immediately blocks selection; the commands expose no way to inspect and resolve this transaction safely.

**Trigger / consequence:** Restart after claim intent was persisted but before receipt completion was saved.

**Repair / acceptance:** Add a review workflow that displays evidence and resolves explicitly verified outcomes without deleting all positions.

### A18 — P0: Purse parser merges unrelated numeric fields

**Evidence:** `src/client/java/com/goofy/goofyaddons/utils/ScoreboardUtils.java:37`. Removing all characters except digits and dots from the entire purse line combines the purse with any additional numeric annotation. For example Purse: 80,000,000 (+123) parses as 80000000123. This is a source-level counterexample, not a confirmed server format.

**Trigger / consequence:** A purse scoreboard line includes another integer.

**Repair / acceptance:** Parse a bounded supported purse amount and reject ambiguity. Test annotated, abbreviated, malformed and missing scoreboard lines.

### A19 — P1: Shared latest quotes can regress on response order

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BazaarApi.java:37`. Every successful asynchronous fetch assigns latest without comparing source timestamps. A slower older response can overwrite a newer response.

**Trigger / consequence:** Concurrent engine requests complete out of order.

**Repair / acceptance:** Publish snapshots monotonically by source timestamp; test concurrent completions and equal timestamps.

### A20 — P2: Malformed product data breaks the whole calculation pass

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/FlipCalculator.java:84`. Calculators/monitor index nested JSON fields without per-product schema isolation. One malformed product can abort processing otherwise valid allowlist items.

**Trigger / consequence:** One configured product has missing or wrong-typed price/volume fields.

**Repair / acceptance:** Validate each product, reject it with a concise reason and continue valid products. Test malformed mixed batches and nonfinite volumes.

### A21 — P1: Persistent stores do not guarantee atomic replacement

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BookJournal.java:37`. Book journal, general state, config and profit ledger use temporary files plus REPLACE_EXISTING without an explicit atomic-move/fallback/backup durability policy. They are also independent stores.

**Trigger / consequence:** Crash, power loss or disk failure during replacement or between related store writes.

**Repair / acceptance:** Define atomic replacement and backup/recovery policy; persist a durable transaction event across stores. Fault-inject each write boundary and prove no automatic replay.

### A22 — P2: First-load invalid config permits default trading settings

**Evidence:** `src/client/java/com/goofy/goofyaddons/config/GoofyConfig.java:63`. Invalid configuration is preserved, but when no last-good instance exists loading falls back to defaults without a startup validation-error latch.

**Trigger / consequence:** User config is corrupt on first launch, then presses start expecting their capital settings.

**Repair / acceptance:** Keep a visible config-error state and block trading until a valid config is loaded; test first load separately from failed reload.

### A23 — P2: Reloaded key settings do not rebind registered keys

**Evidence:** `src/client/java/com/goofy/goofyaddons/keybinds/GoofyKeybinds.java:1`. Key mappings are registered once. Config reload updates key fields without updating the actual registered mappings.

**Trigger / consequence:** Change start/stop/mode keys in JSON and reload.

**Repair / acceptance:** Make rebinding explicit or disallow those changes during reload; test UI and effective bindings agree.

### A24 — P2: Settings and success messages do not match effective behavior

**Evidence:** `src/client/java/com/goofy/goofyaddons/config/GoofyConfig.java:49`. speedMode/speedModeDelay have no runtime references. General scheduling uses minActionDelay rather than the configured random range. Config.save returns void and swallows failure, while HUD/mode commands report success.

**Trigger / consequence:** Change speed settings or save to an unwritable directory.

**Repair / acceptance:** Document/remove unsupported fields, define consistent delay behavior, return persistence status and surface failed saves.

### A25 — P1: Travel recovery resumes on elapsed time rather than destination proof

**Evidence:** `src/client/java/com/goofy/goofyaddons/failsafes/ScheduledReboot.java:37`. Reboot recovery issues hub/is commands and resumes after fixed timers; it does not verify destination, command success, a ready menu context or available remote-access prerequisites.

**Trigger / consequence:** Warp is denied, delayed, or arrives somewhere unexpected.

**Repair / acceptance:** Wait for supported location/readiness evidence with bounded timeout; denied travel leaves trading paused.

### A26 — P1: Diagnostics queue drops critical events like ordinary progress

**Evidence:** `src/client/java/com/goofy/goofyaddons/diagnostics/Diagnostics.java:19`. The bounded queue has one rejection policy for every severity. Export shares the writer queue. A full queue can discard safety/receipt/failure events; DROPPED is reset after writing a warning so the command is not a cumulative loss count.

**Trigger / consequence:** A busy macro logs while disk/export work is slow.

**Repair / acceptance:** Reserve critical capacity or a durable critical channel, coalesce progress, track total and pending drops separately; saturate queue and verify critical evidence survives.

### A27 — P2: Diagnostic timestamps describe disk-write time

**Evidence:** `src/client/java/com/goofy/goofyaddons/diagnostics/DiagnosticLog.java:60`. Instant.now and sequence are assigned in append on the worker, after enqueue delay. Events do not retain original capture time.

**Trigger / consequence:** Disk/export queue backs up.

**Repair / acceptance:** Store capture wall/monotonic time and sequence at production, plus write time if useful; verify delayed writer preserves true event timing.

### A28 — P2: Diagnostics omit server item identity from inventory evidence

**Evidence:** `src/client/java/com/goofy/goofyaddons/diagnostics/Diagnostics.java:64`. Detailed inventory contains vanilla item type, slot and count, but not Hypixel custom ID, display name or book enchantment identity. Many distinct products share the same vanilla item.

**Trigger / consequence:** Investigate a wrong product, book or quantity claim.

**Repair / acceptance:** Include sanitized native IDs and relevant enchantment/level metadata; reproduce distinct products sharing a vanilla type.

### A29 — P2: Profit reporting has no durable acknowledgement to engine

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/profit/ProfitTracker.java:33`. acquire/sell return void and suppress further tracking after a persistence error. Engines can finish/remove ownership despite a reporting event never reaching durable storage. Error state warns stats are incomplete, but there is no event retry/reconciliation outbox.

**Trigger / consequence:** Disk write fails during a confirmed trade or a crash occurs between position and ledger writes.

**Repair / acceptance:** Durably capture confirmed events with IDs, retry reporting safely and expose incomplete events; trading must never replay a server action to repair reporting.

### A30 — P2: Profit storage and rendering work grow without a bound

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/profit/ProfitLedger.java:1`. Closed history and dedup events remain in the same JSON document; summaries scan history and saves serialize the full ledger on the client thread. Shutdown has no dedicated final profit flush.

**Trigger / consequence:** Long-running sessions or closing between periodic active-time saves.

**Repair / acceptance:** Archive history, cache summaries, use immutable asynchronous persistence and lifecycle flush; measure a large ledger and test final active time.

### A31 — P2: HUD layout can clip required rows and leave render state unbalanced on error

**Evidence:** `src/client/java/com/goofy/goofyaddons/features/profit/ProfitHud.java:65`. Panels accept height as low as 80 while compact rows need substantially more space. Rows are not chosen to fit the available height. Pose/scissor cleanup is not in finally.

**Trigger / consequence:** Short window/high HUD scale, or an exception from activity/summary rendering.

**Repair / acceptance:** Use responsive row selection and guaranteed cleanup; test minimum dimensions and injected rendering-data failures.

### A32 — P1: Existing tests do not execute real transaction sequences

**Evidence:** `src/test/java/com/goofy/goofyaddons/features/RuntimeSafetyTest.java:1`. Safety/parser tests exercise helper predicates; CombinedTradingTest uses fake scheduler engines. There is no packet/menu/inventory/receipt replay that drives GeneralFlipper and BazaarFlipper through complete workflows.

**Trigger / consequence:** A correct parser is integrated with the wrong state transition or timing.

**Repair / acceptance:** Build an injectable observation/action harness and replay placement, partial/full claims, cancellation, combine, persistence failures, mode switching and recovery.

### A33 — P2: Build/release automation treats every branch push as a release

**Evidence:** `.github/workflows/build.yml:3`. Workflow runs on every branch push with contents write, release creation and Discord jobs. There is no pull-request-only test trigger or publication gate separating validation from release. Loom is configured as a SNAPSHOT and wrapper lacks distributionSha256Sum.

**Trigger / consequence:** An audit/docs or experimental branch is pushed.

**Repair / acceptance:** Separate PR checks from tagged/reviewed publication; pin build tooling and wrapper checksum. Verify docs-only work cannot create a release or notification.

## Possible bugs and validation questions

### R01 — P1: Purchase settlement uses a time window, not a purse-update acknowledgement

`src/client/java/com/goofy/goofyaddons/features/CapitalManager.java`. A stale scoreboard lasting beyond the settling window may allow reserve calculations from the old purse. Replay purse delays longer than one second across both engines.

### R02 — P1: New fills during cancellation can invalidate captured quantities

`src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java`. expectedClaim/cancelSoldUnits/return counts are sampled before later clicks. Additional fills may make exact receipts differ or make an options click claim again. Replay every intermediate fill; do not simply loosen quantity checks.

### R03 — P1: Same-name same-quantity replacement order is indistinguishable

`src/client/java/com/goofy/goofyaddons/features/generalflipper/GeneralFlipper.java`. Matching checks quantity and creator but has no price/time/context fingerprint. Replay a manual replacement with a different price; determine available server identity evidence.

### R04 — P1: Claim parsers may not cover every current server wording

`src/client/java/com/goofy/goofyaddons/features/generalflipper/OrderLore.java`. InventoryScanner uses the previouslyClaimed overload, requiring an explicit supported claim hint. Gather book buy/sell and cancellation fixtures, particularly singular/plural, formatted and cumulative-fill cases.

### R05 — P1: Extra book exposure can outlive its task reservation

`src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java`. Extra bookLists are retained by releaseMissing and journaled conservatively, but creating an extra list does not itself add/resize an allocation. Replay overfilled claims and task completion while extras remain.

### R06 — P1: Anvil input matching uses lore rather than full native identity

`src/client/java/com/goofy/goofyaddons/utils/InventoryScanner.java`. findLoreInv/findLoreContainer accept any enchanted book with a matching lore line; matchesBook is stricter and requires exactly one enchantment. Replay a multi-enchantment book sharing that line before combining.

### R07 — P2: Full second storage page loops without a distinct terminal state

`src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java`. Full storage sets usingSecondPage=true even when already on page two. The watchdog may eventually pause; replay both pages full and verify no repeated movement/click cycle.

### R08 — P2: Anvil capacity checks may cause store/retrieve cycling

`src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java`. Some checks compare total task.bookList size with empty slots rather than the next transfer count, including books already held. Replay near-full inventory and books distributed across two pages.

### R09 — P2: Book priority ordering needs an explicit policy

`src/client/java/com/goofy/goofyaddons/features/bookflipper/BazaarFlipper.java`. IDLE selects higher numeric STATE_PRIORITY. Confirm intended ordering and test that incoming OUTBID tasks cannot indefinitely delay sells or recovery.

### R10 — P1: Travel warning source may be ambiguous

`src/client/java/com/goofy/goofyaddons/event/ChatHook.java`. Hooks use text matching; verify whether player/system message routing can trigger a reboot warning from unrelated chat. Capture event source and authentic warning fixtures.

### R11 — P2: Screen reinitialization may duplicate HUD subscriptions

`src/client/java/com/goofy/goofyaddons/features/profit/ProfitHud.java`. AFTER_INIT registers an afterExtract listener each initialization. Inspect the exact installed Fabric implementation before classifying duplicate subscriptions as a bug; test resize/reinit.

### R12 — P2: Oversized events and retained exports need resource bounds

`src/client/java/com/goofy/goofyaddons/diagnostics/DiagnosticLog.java`. File rotation limits file count rather than individual event bytes; huge single events can exceed the nominal size. Load-test large stack traces/tooltips and verify bounded memory/disk and export ordering.

### R13 — P2: Redaction is best-effort and schema dependent

`src/client/java/com/goofy/goofyaddons/diagnostics/DiagnosticLog.java`. Known owner/vendor/token fields are redacted, but unexpected tooltip/chat formats may reveal other players. Fixture-test new formats without removing useful item/quantity evidence.

### R14 — P1: Global readiness coverage is incomplete

`src/client/java/com/goofy/goofyaddons/failsafes/FailsafeManager.java`. Only scheduled reboot has a dedicated failsafe. Confirm expected behavior for limbo, non-SkyBlock worlds, missing cookie/remote permissions, disconnect, cursor items and inventory desync using state-machine fixtures.

### R15 — P2: Multiple independent API clients can overlap polls

`src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/BazaarApi.java`. Calculator, monitor and general engine can issue concurrent requests without a shared in-flight cache/backoff. Verify traffic and 429 recovery; avoid claiming rate limiting has already occurred.

## Historical issues already addressed

Co-op Bazaar Orders recognition; formatted Order amount parsing; partial-buy claim/options navigation; Offer amount and zero-filled sell-offer lore. Existing regression fixtures cover the known examples. These are not counted as new open defects; new timing/format combinations still require sequence replay.

## Validation limits

Existing automated tests are rerun as the audit baseline; see AUDIT_COVERAGE.md. No live server orders were placed for this review. No state files were deleted or migrated. Source-level counterexamples and control-flow inspection support confirmed gaps; suspected races and server wording remain investigation items. Runtime source and configuration were not changed during this audit.

## Repair status — 1.3.2-BETA, stage 1

- A01: fixed at the safety boundary. SafetyActions latches pause before cleanup; failing cleanup/reporting does not skip remaining cleanup. Detailed diagnostic context has an unavailable fallback. SafetyActionsTest exercises simultaneous cleanup and logger failures.
- A02: fixed. Client tick consumes stop before failsafe/trading work and discards simultaneous queued starts/mode changes. The client uses the tested SafetyActions.tradingTick boundary.
- A26: ordinary saturation mitigated. DiagnosticQueue reserves 64 of 512 queued slots for critical events, and critical work can evict queued ordinary work. Total and critical drops are cumulative. Critical-only exhaustion or unavailable disk still cannot promise lossless logging; those losses remain explicit. DiagnosticQueueTest tests saturation, eviction and critical-only overflow.
- A27: fixed for queued event capture. Schema 2 records capture wall time, monotonic nanoseconds, sequence and separate writeTime; captured payload is detached from mutable caller data. DiagnosticLogTest covers capture identity and payload mutation.
- A28: fixed for inventory snapshots: Hypixel custom ID and enchantment levels accompany vanilla ID, slot and count. Only selected metadata is included, never the full custom tag.
- A32: groundwork only. An injectable client tick boundary supports stop/action replay. Whole-engine menu/packet/receipt replay is still required in later stages and is not claimed complete.
- R12/R13: larger resource-bound and redaction investigations remain open. Existing redaction regression tests pass; arbitrary unknown server fields are not certified.

Validation: 88 tests passed, zero failures/errors/skips; production build and git diff --check passed. No live server transaction was performed. Stage 2 and all other unlisted findings remain open.

## Repair status — 1.3.3-BETA, focused stage-2 batch

- A04: implemented strict supported confirmation side/item/quantity/price checks in both engines. Missing or conflicting evidence pauses. ConfirmationCheckTest exercises mismatch and incomplete/malformed fields. Live confirmation tooltip compatibility remains unverified; fixtures are synthetic.
- A05: implemented final profitability, inventory/capacity and shared-capital checks before confirmation. General checks the newest shared quote snapshot. Book ties confirmation to a recent matching price-selection task. R01 (delayed purse acknowledgement) remains open.
- A18: fixed the digit-concatenation parser. PurseParserTest verifies supported complete amounts and rejects extra numbers, abbreviations and invalid grouping. Multiple purse rows are unavailable.
- A19: fixed publication ordering with synchronized timestamp comparison; equal/older source snapshots cannot replace a newer one. BazaarQuoteCacheTest checks out-of-order publication, equal timestamps, input mutation and expiry.

Validation: 96 tests passed, zero failures/errors/skips; production build and git diff --check passed. See STAGE_2_REPAIR.md. Other stage-2 findings and whole-engine/live validation remain open.

## Live diagnostic follow-up — 1.3.4-BETA

The 1.3.3-BETA live Overload I confirmation pause revealed that book safetyHalt closed the menu before capturing detailed evidence. Fixed by recording books.transaction_blocked and books.confirmation_check before closure. Both engines now defer confirmation evaluation until the same loaded preview is unchanged for 750ms. Missing live tooltip compatibility remains unresolved because the uploaded bundle lost the tooltip; the patch preserves it for a subsequent failure. See CONFIRMATION_DIAGNOSTIC_FIX.md. 98 tests passed and production build succeeded.

## Live order-observation follow-up — 1.3.5-BETA

The uploaded Ectoplasm setup run confirmed server acceptance after the engine opened its initial orders list; the same stale container then remained open until timeout. Added a shared bounded close/reopen observation policy to general placement/claim/cancellation checks and corresponding book order paths, preserving transaction intent and deadlines. Book placement now has a dedicated verification state and validates quantity/price before advancing; A03's immediate click-success transition is addressed, while full journal reconstruction (A11), legacy book reconciliation and full-engine replay remain open. A07 is tightened for book orders-only branches; A14's arbitrary-menu absence fallback now requires an orders snapshot. See ORDER_RECHECK_FIX.md. 103 tests passed and production build succeeded; live verification remains outstanding.

## Observed confirmation compatibility — 1.3.6-BETA

The subsequent BOOKS-mode bundle preserved the actual Overload I confirmation: Order: 16x Overload I with matching unit/total prices and profitAllowed=true. The parser did not support Order as an identity/quantity field. Added the exact observed field and a sanitized regression fixture; the valid-preview test failed before the fix and passed afterward, while conflicting/malformed variants remain rejected. This resolves that specific live format under A04, not all possible tooltip variants. See BOOK_CONFIRMATION_FORMAT_FIX.md. 105 tests passed, production build succeeded and git diff --check passed.

## Structure and cost batch — 2026-10-02, branch `claude/refactor-plan`

Scope: structure and per-tick/per-frame cost, planned in REFACTOR_PLAN.md. This was
not a re-audit, so a finding absent below is untouched rather than checked.

**The suite now runs.** Earlier parts of this batch were reviewed by inspection
because `./gradlew test` could not resolve dependencies; that is resolved. Gradle's
`mavenCentral()` resolves to `repo.maven.apache.org`, which this environment's proxy
rate-limits, while `repo1.maven.org` serves the same artifacts; and the review
container had only JDK 21 against a Java 25 target. With a Central mirror and a
provisioned JDK 25 (neither committed — see REFACTOR_PLAN.md for the recipe):

- **141 tests, 0 failures, 0 errors, 0 skipped**, across 22 classes.
- `compileJava`, `compileClientJava` and `compileTestJava` all succeed, so every
  change in this batch compiles against Minecraft 26.1.2 — which inspection alone
  could not establish.
- `./gradlew build` succeeds and produces `goofyaddons-1.3.7-BETA.jar`.

What this does *not* cover: no test can construct either engine (A32), so the
engine-level state and control-flow changes in this batch are still exercised only
by inspection, and no live server order was placed.

### Findings touched

- **R15: partially addressed.** `BazaarApi.fetch()` now shares one outstanding
  request between callers, handing each a dependent future so one engine's
  `cancel(true)` cannot kill a request another is waiting on (verified by
  execution). Backoff and a single polling cadence are **not** implemented: the
  calculator, monitor and general engine still each decide when to ask.
  Consolidating them moves how stale a snapshot can be when a money decision
  reads it, which needs fixtures. R15 stays open.
- **A06: partially addressed.** 1.3.5 covered orders this engine *submits*
  (`verifyBookPlacement` plus `TradingSafety.orderMatchesIntent`). The *adoption*
  path was not: STARTUP_BAZAAR_CHECK and OUTBID located an existing `BUY <name>` by
  display name and claimed it without reading its amount. Both now go through
  `bookOrderAdoptable`, which requires a readable total that this route could
  actually have ordered; an unreadable total is retried as an observation through
  the existing recheck policy and then retained rather than claimed.
  `TradingSafety.adoptableOrderTotal` is pure and tested.

  **Not closed, and why.** The check is an upper bound, not equality: a route can
  never have ordered more than its full requirement, so a *larger* same-name order
  is rejected, but a smaller one is indistinguishable from a partially claimed
  order and is still accepted. Equality needs the original order size, which the
  journal cannot reconstruct — that is A11. A price check is also not possible yet
  for the same reason: `reservedUnitCost` is rebuilt from the current flip
  calculation on restart, not from the order, so comparing it to an order placed
  earlier would halt on legitimate positions. Sell-side adoption in SELL and
  REPLACE_SELL is also still unvalidated. **A06 depends on A11.**
- **A08: unchanged.** Repeated-scan memoisation in STORE/ANVIL/COMBINE reduced
  query count but did not touch the rule, which is still "the source no longer
  shows it". The replacement transfer model is specified as B2 in
  REFACTOR_PLAN.md and is the highest-value remaining work.
- **A21: unchanged.** Write frequency for both stores is now much lower (below),
  which narrows the crash-during-replace window, but no atomic replacement or
  backup/recovery policy is defined and the stores remain independent.
- **A30: unchanged.** `ProfitHud` now reads `ProfitTracker.error()` once per frame
  instead of six times, but `ProfitLedger.summary()` still scans session history
  per frame and the ledger is still serialised whole on the client thread.
- **A32: first increment landed; still the blocker overall.** The observation half of
  the seam now exists and is tested. `SlotView` and `MenuSnapshot` model an open menu
  as plain data with no Minecraft dependency, carrying every observation query the
  engines rely on; `LiveMenu` is the single adapter that builds one from the live game.
  `MenuSnapshotTest` covers 19 cases, chosen because they are awkward or expensive to
  reach on a server: a lookalike two-enchantment book, a zero or unreadable
  enchantment level, an item that is not a Hypixel book but is named like an order, an
  order entry sitting in the player inventory, a present-but-empty lore against an
  absent one, an unloaded anvil, an anvil holding the wrong level, and a snapshot with
  no slots at all.

  The translation is deliberately faithful rather than tidied, and the tests pin down
  three upstream asymmetries so a later migration cannot change them by accident:
  name matching reads the *custom* name while the engines' order sweeps read the
  *hover* name; lore scans bound themselves to the player's 36 main slots while book
  matching does not, so armour and offhand are included there; and
  `emptyContainerSlots` counts every non-inventory slot rather than the container
  region.

  **The effects half also landed.** `GameActions` covers clicks, menu closes, commands,
  player messages and sign writes; `LiveActions` is the only implementation that
  performs them; `RecordingActions` records them for tests, and its `serverEffects()`
  deliberately excludes closes and messages so a test can assert that an engine changed
  nothing on the server. `GeneralFlipper` now routes every effect through it and takes
  it by constructor. `EngineSeamProbeTest` constructs that engine in a plain JVM with
  injected effects and confirms construction performs none — which establishes that the
  rest of the migration needs no Minecraft bootstrap, only the replacement of direct
  `minecraft.` reads. `BazaarFlipper`'s 26 direct click sites are not migrated.

  **The migration landed for the general engine, and it can now be driven by a test.**
  `GameWorld`/`LiveWorld`/`FakeWorld` complete the seam, `GeneralFlipper` holds no
  reference to Minecraft at all, and its store path is injected so construction no longer
  depends on a running game. Two couplings had to be broken to get there, both found by
  probing rather than by reading: `GeneralFlipper.statePath()` resolved FabricLoader, which
  made `load()` fail and latch `blocked`; and `BazaarFlipper`'s journal field resolved
  FabricLoader at construction, which made `FeatureManager`'s static initialiser unusable
  in a test and so put every `safetyPause` path out of reach. The journal is now lazy.

  `GeneralFlipperDrivingTest` runs real ticks against a described menu and asserts what
  reached the server. It covers: an unexpected menu, an orders list missing its controls,
  a paginated list, an order whose amount differs from the tracked position, a co-op order
  owned by another player, and the one positive case — with nothing on screen the engine
  asks for its orders list and does nothing else.

  **Two of those assertions were worthless until a mutation test exposed them.** Disabling
  the order-quantity check left the suite green, twice. The first cause was a fixture whose
  timestamps were at the epoch, so the holding-age limit fired before the identity checks
  were reached. The second was subtler and is worth recording: refusing a bad order and
  quietly adopting it *both* perform no server action — accepting one merely closes the
  menu. "No clicks" therefore does not test the rule. Those cases now also assert that the
  engine warned the player, and the mutation fails them.

  **The observation model is not wired into the book engine, on purpose.** Having `InventoryScanner` delegate per call
  would rebuild a ~90-slot snapshot on each of the 16-plus scans per tick, which is a
  performance regression. The migration is to take one snapshot per tick and pass it
  down, which is also the A3 observation snapshot deferred earlier. Until that lands,
  no test can construct an engine, so the engine defects in this batch still have no
  regression test and B2/B3/B5 remain blocked. What this increment buys is that the
  observation rules are now executable and pinned, so the migration has a net under
  it.

### Defects found here that were not in the A/R backlog

IDs deliberately not assigned; these need the owner's numbering.

1. **P0 — `BazaarFlipper.stop()` leaked per-transaction state into a silent stall.**
   It discarded every task while leaving `pendingBuyClaim`, `pendingSaleClaim`,
   `submittedBookTask`, `confirmationTask`, the orders-menu observation and
   `heldSince` set. The `pendingBuyClaim` block runs at the top of `onTick` in
   every state, so after stop→start the engine returned early forever against a
   task that no longer existed — reporting RUNNING while doing nothing, and
   invisible to the watchdog because `canYield()` is true in START, which makes
   `stalled()` reset each tick. A later inventory arrival would also have recorded
   a `ProfitTracker.acquire` against a discarded trade id. Fixed in `cc7767c` by
   one `clearTransactionState()`, placed after the exposure checkpoint;
   `rememberObservedBooks` now also marks exposure for an issued claim or a
   submitted order.
2. **P1 — `BazaarFlipper.pause()` erased why the engine was paused.** It assigned
   `modePaused = false` before its own early-return guard, so a safety pause or
   travel pause on an already-paused engine cleared the marker. A later `resume()`
   could then take the mode fast path and skip the travel reconciliation that
   invalidates recorded inventory and menu positions. Fixed in `cc7767c`; mode
   state is now set only on a real pause transition.
3. **P2 — `GeneralFlipper.needsMenu()`/`activity()` were expensive and mutating.**
   Both recomputed the full candidate list, and the old `freshQuotes()` adopted new
   quotes, mutating `products`/`quotesAt`. `activity()` is called from
   `ProfitHud.render`, i.e. per frame on the render thread. Fixed in `b44574a`:
   adoption moved to the tick path, `freshQuotes()` is a pure predicate, and one
   snapshot per tick serves the scheduler and the HUD.
4. **P2 (latent) — `Book.getQtyAmount` could throw from bookkeeping and
   confirmation paths** (`checkpoint`, `checkHoldingLimits`, `bookPriceAllowed`,
   `verifyBookConfirmation`). Config and journal validation blocked the range, so
   this was unreachable rather than live. Fixed in `cc7767c` by establishing the
   combining invariant in `Book`'s constructor.

### Cost reductions (no finding ID)

- Market recompute moved off the render thread: per frame (60–240Hz) to once per tick.
- `DiagnosticLog.redact` compiled seven patterns per logged string, on a path that
  runs for every logged string while `debug()` logs many times per tick; now static.
- 25 per-call `replaceAll("§.", "")` sites on per-slot and per-lore-line paths
  replaced by one precompiled `Chat.strip`.
- `BazaarFlipper` `findLore*` call sites: 34 to 16, by reusing the result of
  identical queries within a block that moves nothing.
- `GeneralFlipper.save()` wrote the file at all 13 call sites; it now skips when
  the file already holds exactly this state, so persist-before-click still holds.
- `BookJournal.writeTracked` no longer serialises an unchanged journal, which
  `checkpoint()` provoked at least twice per tick. Value equality on records, never
  a hash, and a direct `write()` invalidates the cache.

### Deduplication

`OrderLore.creator` (the three-way co-op ownership decision both engines had
open-coded), `MenuSettle` (the 750ms container debounce), `MenuText.containerEnd`
(the "last 36 slots are the player inventory" assumption, previously open-coded in
five places) and `SignEntry` (identical reflection) are now shared.

Capacity handling remains divergent **on purpose**: `GeneralFlipper.capacityFor`
is stack-aware and reserves four slots while books uses bare
`getEmptyInventorySlots()`. Unifying them changes how many books the engine
believes it can claim, which is a live trading decision needing fixtures. That the
duplication has already drifted is the strongest argument for finishing the
extraction.

Two deliberate behaviour changes: `GeneralFlipper.menu(String)` now strips
formatting codes (a code inside the searched label made a known menu
unrecognisable, surfacing as a 30-second step timeout; the exact-match gates in
`TradingSafety.confirmationTitle` are untouched), and a failed sign write now halts
immediately with a specific reason instead of being retried until the watchdog
noticed.

### Book loop batch — closing the cycle

Aimed at the book engine completing a flip and starting another, rather than at the test
seam. All three findings here are the ones that describe a loop that cannot close.

- **R09: fixed.** The priority table's sense was inverted and unlabelled. IDLE picks the
  *highest* rank, so the effective order was OUTBID > SELECTED > STORE > SELL > COMBINE >
  ANVIL > BAZAAR_ORDER_CHECK > REPLACE_SELL. REPLACE_SELL is the only state that collects
  proceeds and removes a task, and it ranked below everything; SELECTED, which commits
  coins to a new order, ranked near the top. Since the outbid monitor re-queues work every
  20 seconds and filled buy orders also route through OUTBID, something almost always
  outranked completion, so finished books could sit unlisted and settled sales uncollected
  while the engine kept opening new positions. **This is the most likely reason the engine
  does not loop in practice.** The policy now reads "finish and realise value before
  starting new work" and lives in `BookSchedule`, which is pure and covered by 13 tests;
  six of them fail against the old ordering.
- **R07: fixed.** A full storage page set `usingSecondPage = true` even when already on
  page two, so the engine reopened the same full page indefinitely until the watchdog
  happened to notice. Being full on both pages is now an explicit stop with a reason.
- **R08: fixed.** The anvil retrieval check compared the whole `bookList` size against
  empty inventory slots, counting books that were already in the inventory holding the
  slots they need. That sent the task to STORE and straight back, cycling. It now counts
  only the books still in storage.

**The stalled-wait gap is now closed.** A task parked in IN_BUY_ORDER or SELL_ORDER used
to be woken only by a chat notice or by the outbid monitor. A missed fill notice parked it
forever: the scheduler reported no work, its capital stayed reserved, and the engine polled
flips indefinitely without progressing that position — a stall with no visible failure. The
general engine had a periodic per-position re-check; the book engine had none.

The OUTBID path could not serve as one, because after clicking into an order it clicks
Cancel Order, so re-reading a healthy unfilled order through it would cancel it and lose
queue position. A new `VERIFY_ORDER` state does the job instead, and is read-only by
construction: it issues `managebazaarorders`, reads the tracked entry's fill line, and
closes the menu. It contains no slot click of any kind, which is asserted structurally
rather than assumed. From the fill it routes to collection (REPLACE_SELL for a sale,
OUTBID for a buy) or back to the wait with its clock restarted. An absent entry or an
unreadable fill goes through the existing recheck policy and then retains the position.

`Task` now records when a wait began and which side it is on, `BookSchedule.staleOrder`
decides when one is due (pure, takes the clock as an argument), and
`bookOrderRecheckSeconds` configures the window at a default of 180s with a 30s floor. The
floor is deliberately not tied to `maxBookHoldingSeconds`, so an existing config with a
short holding limit is not rejected.

- **R05: fixed.** An extra book outlives the task that produced it, and `releaseMissing`
  keeps its allocation alive because the book id is still in `bookLists`. Nothing resized
  it, so a completed flip left the *whole* position's cost committed against a single
  leftover book. Extras accumulate from overfilled claims, so available capital drained
  and the engine eventually could not open any position — the loop starving rather than
  failing, with no error to see. On completion the allocation is now reduced to what the
  remaining extras are worth at the unit cost the position was bought at, or released when
  none remain. Only ever a reduction, so it cannot fail against the capital limit.
  `Book.baseUnits` carries the valuation, returns 0 for a level outside the route rather
  than throwing, and is tested.

### Findings with no status record anywhere in this document

A09, A13, A15, A16, A17, A23, A24, A25, A29, A31, A33, R02, R03, R04, R05, R06,
R07, R08, R09, R10, R11, R14 — twenty-two entries that are stated as neither open
nor closed by any status section. They should be treated as open until checked.

**Validation:** no live orders were placed. The suite runs green (141 tests, 0
failures) and the production build succeeds; see the note at the top of this section
for how. Pure helper logic was additionally verified by standalone execution: the `Book` invariant (98
assertions), `Chat.strip` against the expression it replaced (20,025 inputs
including the `§\n` case where `.` must not match the line terminator),
`OrderLore.creator`/`MenuSettle`/`MenuText` (37 assertions), and the shared
future's cancellation and failure propagation. New JUnit tests: `BookTest`,
`ChatTest`, `MenuTextTest`, `MenuSettleTest`, `OrderCreatorTest`, and three
`BookJournal` dedupe tests, and two `adoptableOrderTotal` tests in
`RuntimeSafetyTest`. A run on the maintainer's own toolchain is still worth doing,
since the result above used a mirror and a provisioned JDK.

## Logic cleanup batch — 2026-10-02

- A10: TradeBudget no longer plans instant buy/sell routes, so the book engine never reserves capital for a trade it will safety-halt on.
- A12: Starting loads both engines' persisted exposure before the book-recovery gate, so general positions stay reserved while book recovery is pending.
- A20: Book and general calculators and the outbid monitor isolate each product; one malformed or non-finite product is skipped instead of aborting the pass.
- A22: A config file rejected on first load latches GoofyConfig.loadError(); start is refused until a valid config loads. A rejected reload still keeps the last good config.
- CapitalManager.purchased only starts the one-second settle window when cash was actually pending, so routine order re-checks no longer delay other purchases.
- Book SELL waits for the product page to load before clicking Create Sell Offer, matching REPLACE_SELL. Fill notices for removed tasks are pruned.
- Removed unused helpers (BazaarFlipper.scheduler, InventoryScanner.findInv/getSellOrder/getName, Clock.returnState, FeatureManager.start(String)); claimReceipt now reuses TradeReceipts.saleProceeds.

Validation: 115 helper tests passed (8 new; the 5 calculator/budget/monitor tests fail on the previous code). The Minecraft-dependent classes could not be compiled in the review environment, so the production build still needs a local `./gradlew build`. No live orders were placed.
