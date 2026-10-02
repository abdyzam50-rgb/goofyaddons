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
