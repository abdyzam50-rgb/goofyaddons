# Refactor plan: efficiency and failure-point reduction

Reading pass over `src/client` at `d50bb6f` (1.3.x-BETA). This plan is about
**structure and cost**: where work is repeated per tick/frame, and where state
shape makes defects easy to introduce. It is deliberately separate from
`FULL_AUDIT.md`, which tracks correctness findings (A01–A33, R01–R15); where a
structural fix closes an audit finding, that is noted, but nothing here renames
or retires an audit ID.

**Baseline caveat:** `./gradlew test` could not run in this session — Maven
Central returned HTTP 429 through the proxy and `--offline` has no cached
`fabric-loom` artifact. No test result in this plan is verified. Establishing a
green baseline is step 0 of any stage below.

---

## Part A — Per-tick / per-frame cost

### A1. The HUD recomputes the whole market every frame — *severe, small fix*

`ProfitHud.render` is registered on `HudElementRegistry` and
`ScreenEvents.afterExtract`, so it runs per frame (60–240 Hz). It calls
`FeatureManager.activity()`, which for the general engine reaches
`GeneralFlipper.activity()` (`GeneralFlipper.java:139`) and from there:

- `freshQuotes()` (`:670`) — **and this mutates `products` and `quotesAt`**,
  so engine state is being advanced from the render thread;
- `candidates()` (`:656`) — a full `GeneralCalculator.calculate` over every
  allowlisted item, plus a sort and a fresh list, against the multi-MB quote
  tree;
- `capital.available(new ScoreboardUtils().getPurse())` — a full scoreboard
  sweep with per-line string building.

`render` also calls `new ScoreboardUtils().getPurse()` directly, and
`ProfitTracker.INSTANCE.error()` six separate times.

**How to fix:** make `activity()`/`taskItem()` pure readers of a small status
record that the tick path publishes. Cache the candidate list and recompute it
only when `quotesAt` changes. Move the `freshQuotes()` quote-adoption side
effect out of the query and into `poll()`. Snapshot purse once per tick.

### A2. Three independent pollers fetch and parse the same endpoint — *severe, small fix*

`FlipCalculator.Refresh`, `BazaarMonitor.refresh` and `GeneralFlipper.poll` each
call `BazaarApi.fetch()` on their own schedule (20 s / 20 s / `refreshSeconds`).
In BOTH mode that is three downloads of the full bazaar payload, three
`JsonParser` passes, and up to three `root.deepCopy()` calls in
`BazaarQuoteCache.publish` — all for one shared snapshot that all three then
read back through the same cache.

**How to fix:** one owner polls (`BazaarApi` gains a scheduled refresh plus
in-flight coalescing so concurrent callers share one future); everyone else
reads `latestFresh()`. Drop the `deepCopy` in favour of documenting the
published tree as immutable — it already is treated that way — or copy once at
publish and never again.

### A3. Repeated identical container scans inside one tick — *moderate*

`InventoryScanner.findLoreInv` / `findLoreContainer` sweep every slot and run
`replaceAll("§.","")` per lore line. `BazaarFlipper` calls them 34 times, and
several branches call the same query 3× with identical arguments — e.g. the
STORE move-detection at `BazaarFlipper.java:865` and the ANVIL equivalents at
`:965` and `:1075` re-scan the container to build the log line and then again to
store the counter. `GeneralFlipper.findOrder` is called 8 times across
`inspectOrders`/`cancelDetail`/`verifyCancellation`, each a fresh `find()` sweep.

**How to fix:** a per-tick observation snapshot — one object built at the top of
the tick holding container slots, inventory slots, stripped names and parsed
lore — that every decision reads. This also removes the "the screen changed
between two reads in the same tick" class of hazard.

### A4. `replaceAll("§.", …)` compiles a `Pattern` on every call — *moderate, trivial fix*

25 call sites, several inside per-slot/per-line loops (`InventoryScanner` ×4,
`GeneralFlipper` ×5, `BazaarFlipper` ×5). `String.replaceAll` compiles the
pattern each time.

**How to fix:** one `Chat.strip(String)` utility over a precompiled static
`Pattern` (or a hand-rolled char scan, which is faster still), used everywhere.
Delete the ad-hoc copies. The inline
`Pattern.compile("(?m)^\\s*By:")` built per call in both engines
(`GeneralFlipper.java:739` and its twin in `BazaarFlipper`) goes the same way.

### A5. Persistence runs on the tick path, and one of the two does real I/O every call — *moderate*

- `BazaarFlipper.checkpoint()` (`:1614`) is invoked 9 times, including at the
  top of `onTick` *and* in its `finally`, so ≥2 per tick. It rebuilds a map,
  sorts, and Gson-serialises every position each time. `BookJournal.write`
  dedupes by string compare, so the disk write is usually skipped — but the
  serialisation is not.
- `GeneralFlipper.save()` (`:870`) has **no** dedupe: every one of its 13 call
  sites does `createTempFile` + `writeString` + atomic `move`. Synchronous disk
  I/O on the client thread, several times per work cycle.

**How to fix:** separate the two kinds of write. *Barrier* writes (before an
irreversible click — `recordBookSubmission`, the pre-confirm `save()`) stay
synchronous and must keep failing closed. Everything else becomes a dirty-flag
write coalesced to at most one per tick, with `GeneralFlipper` gaining
`BookJournal`'s content-hash skip. Do **not** move barrier writes to the
existing `DiagnosticQueue` background thread — the whole point is that they
land before the click.

---

## Part B — Failure-point clusters (structure)

### B1. Two engines reimplement the same Bazaar protocol — *the root cause of most of the rest*

`BazaarFlipper` (1789 lines) and `GeneralFlipper` (889 lines) both implement:
open orders → locate order → read lore → cancel/claim → open product → quantity
→ sign → price → confirm → verify placement. Independently duplicated on both
sides:

| Concern | Books | General |
|---|---|---|
| Orders-menu settle debounce | `ordersContainer`/`ordersSeenAt` inline in `onTick` | `ordersReady()` `:711` |
| Title match | `containerNameCheck` (41 calls) | `menu(String)` |
| Slot search | `InventoryScanner.findContainer` | `find(text, exact)` |
| Lore read | inline `DataComponents.LORE` + join, 6 sites | `lore(slot)` |
| Co-op owner check | inline in `onTick` | `orderMatchesPosition` |
| Observation retry | `recheckBookOrders` | `recheckOrders` |
| Ambiguity guard | `TradingSafety.ambiguousOrders` inline | `ambiguousOrders()` |
| Capacity | `getEmptyInventorySlots` | `capacityFor(id)` (stack-aware) |
| Sign write | `handleSign` (reflection) | `Step.SIGN` (same reflection) |

Every row is a place a fix can land on one side only. The capacity row already
diverges: general is stack-aware and reserves slots, books counts bare empty
slots.

**How to fix:** extract a `BazaarMenus` layer — screen identification, settled
observation, slot/lore queries, order lookup, confirmation gate, sign entry —
with both engines as clients. Do this *before* the deeper cleanups; it is what
makes them affordable. Mechanical and individually test-coverable: start with
the pure/readable pieces (strip, lore, find, titles), then the stateful ones
(settle debounce, recheck), leaving click sequencing alone initially.

### B2. Book movement is inferred from count deltas across ticks — *highest-risk cluster*

Six `int`s with `-1`/`0` sentinel semantics — `store_Counter`, `store_Counter_2`,
`anvil_Counter`, `anvil_Counter_2`, `combine_Counter`, `combine_Counter_2` —
encode "did the item move" by comparing inventory/container counts between
ticks (`:865`, `:965`, `:1075`, `:1144`). Problems compound:

- the sentinel is inconsistent — `combine_Counter_2` resets to `0`, the other
  five to `-1`, and `combine_Counter_2` doubles as a two-state toggle (`:1147`);
- reset sites are scattered across `stop()`, each state's no-task exit, and the
  page-switch branches, so a new exit path silently inherits stale counters;
- the inference is "it disappeared, therefore it moved", which `FULL_AUDIT.md`
  already calls out for the book pipeline (stage 5 gate: *"No book is marked
  moved or combined from disappearance alone"*).

**How to fix:** replace all six with one `TransferObservation` value object per
in-flight move — `{ item, fromLocation, toLocation, beforeSource, beforeDest,
startedAt }` — that is created when a move is clicked, interrogated by a single
`completed(snapshot)` predicate requiring *both* a source decrease and a matching
destination increase, and dropped on completion or timeout. One field to reset,
one place the rule lives, and a unit-testable predicate with no Minecraft types.
This is the change most likely to prevent a real loss, and should carry fixtures
for: partial arrival, wrong page, full storage, cursor-held output, lookalike
multi-enchantment books.

### B3. `BazaarFlipper`'s ~35 mutable fields have no documented legal combinations

`running`, `paused`, `modePaused`, `journalLoaded`, `recoveryRequired`,
`needToStoreExcessBook`, `usingSecondPage`, `isStartUpCheckCompleted`,
`inventoryIsFull`, `checkedFirstPage`, `attemptedToClaim`, `didReceiveItems`,
`overFlowProt`, plus 6 counters, 5 `submitted*`, 4 `confirmation*`, 5
`buyClaim*`, 2 `saleClaim*`. `stop()` (`:186`) resets 20 of them by hand and
misses the rest — see C1 for the live consequence.

**How to fix:** group the co-varying fields into small records that are
assigned and cleared as a unit — `PendingBuyClaim`, `PendingSaleClaim`,
`SubmissionIntent` (`submitted*`), `ConfirmationIntent` (`confirmation*`),
`StoragePosition` (`usingSecondPage`/`checkedFirstPage`). Each becomes one
nullable field, so `stop()` nulls five references instead of enumerating
twenty-plus primitives, and "is a claim pending" stops being derivable from
four separately-mutated fields.

### B4. The `clock.start(randomizer())` / `clock.shouldFire()` idiom — *34 duplicated pairs*

The shape throughout `BazaarFlipper` is:

```java
if (containerNameCheck("X")) clock.start(randomizer());
if (containerNameCheck("X") && inventoryScanner.isMenuLoaded(35) && clock.shouldFire()) { … }
```

Three issues: the condition is evaluated twice (each a regex strip of the
title); `Clock.start` silently no-ops while running, so the first line is also
an implicit latch — load-bearing behaviour that reads like a bug; and the blocks
in a state are not mutually exclusive, so two can fire in one tick where the
first lacks a `return` (e.g. STARTUP_BAZAAR_CHECK's `"Order"` block after the
orders block).

**How to fix:** one `step(Predicate<Screen> when, int loadedSlot, Runnable action)`
helper that evaluates the guard once, owns the delay, and returns whether it
fired so the caller can stop. The 34 pairs collapse to 34 single calls and the
"two actions in one tick" hazard disappears structurally.

### B5. The two engines have different failure contracts

`BazaarFlipper.onTick` uses `try { … } finally { checkpoint(); }` with **no
catch** — exceptions escape to `FeatureManager.onTick`, which catches
`RuntimeException` and calls `safetyPause`. `GeneralFlipper.onTick` catches
`Exception` itself and calls its own `fail()`. So a checked-exception-free
failure in books is handled one level up with a generic message, while general
produces a specific one; and a non-`RuntimeException` `Throwable` in books
escapes `FeatureManager` entirely. `safetyHalt` also sets `paused = true` twice
(lines bracketing the diagnostics capture) — harmless, but it signals that the
ordering rule there is unclear.

**How to fix:** one `TransactionGuard` wrapping each engine tick, with a single
documented ordering: latch → persist → capture evidence → close menu →
`safetyPause`. `SafetyActions.latch` already encodes this; use it on both paths.

### B6. Dead and inconsistent configuration

`speedMode` and `speedModeDelay` are declared in `GoofyConfig` and read nowhere.
Separately, books randomises its action delay (`randomizer()` →
`nextInt(minActionDelay, maxActionDelay)`) while general uses the bare
`minActionDelay` for every step, so the two engines have different timing
signatures in BOTH mode.

**How to fix:** delete the dead fields (or implement them); route both engines'
delays through one randomised source.

---

## Part C — Correctness defects found while reading

These are not refactor opportunities; they are live bugs. They are cheap to fix
now and each becomes invisible after the restructuring above.

### C1. `stop()` leaks claim/intent state → silent permanent stall

`stop()` (`:186`) does not clear `pendingBuyClaim`, `pendingSaleClaim`,
`submittedBookTask`, `confirmationTask`, `ordersContainer`, `heldSince`, or the
`buyClaim*`/`saleClaim*` fields. The `pendingBuyClaim` block at `:334` runs at
the top of `onTick` in **every** state, so after stop → start:

1. `taskList` is empty but `pendingBuyClaim` still points at a discarded task;
2. `inputBooksInInventory(...) < buyClaimBefore + buyClaimExpected` → `return`,
   every tick, forever;
3. the watchdog cannot catch it, because `canYield()` is true in `START`, so
   `stalled(...)` is called with `idle = true` and resets.

The engine reports RUNNING and does nothing, with no diagnostic. If the books
*do* later appear in inventory, it records a `ProfitTracker.acquire` against a
dead trade ID. Fix: clear them in `stop()` (B3's records make this structural).

### C2. `pause()` clears `modePaused` before its own guard

```java
public void pause() {
    modePaused = false;
    if (!running || paused) return;
```

`FeatureManager.safetyPause` and `FeatureManager.pause` both call
`books::pause`. If books was mode-paused (BOTH → GENERAL), either call clears
`modePaused` and returns early. A later `resume()` then fails the
`modePaused && !recoveryRequired` fast path, and with tasks retained falls
through to `safetyHalt("Book menus changed during travel…")` — a spurious
recovery block from an ordinary travel pause. Fix: move the assignment after the
guard, or make mode-pausing a distinct state rather than a flag set by the same
method.

### C3. `needsMenu()` is expensive and mutating, but called as a predicate

`MenuScheduler.select` calls `needsMenu()` on every engine every tick.
`GeneralFlipper.needsMenu` (`:219`) calls `freshQuotes()` (which mutates
`products`/`quotesAt`) and `candidates()` (full recompute). A scheduler query
should be cheap and side-effect free. Same root as A1; fixing A1 fixes this.

### C4. `getQtyAmount` throws from inside the tick path

`Book.getQtyAmount` throws `IllegalArgumentException` on out-of-range levels and
is called from `checkpoint()`, `checkHoldingLimits()`, `bookPriceAllowed()` and
`verifyBookConfirmation()`. Config validation currently makes the bad range
unreachable, so this is latent rather than live — but it means a future config
path can turn a bookkeeping call into an escaped exception during a
confirmation. Fix: return a sentinel and let callers fail closed, or validate at
`Task` construction so the invariant is established once.

---

## Sequencing

Each stage is one commit/PR with its own fixtures, mirroring
`FULL_REPAIR_PLAN.md`'s completion rules. Stages 0–2 are low-risk and pay for
themselves; stage 4 is the one that needs careful fixtures.

| # | Stage | Contents | Risk | Gate | Status |
|---|---|---|---|---|---|
| 0 | Baseline | Get `./gradlew test` green and recorded | none | Suite runs; result captured | **blocked** — Maven Central 429s through this proxy; no cached `fabric-loom` offline |
| 1 | Cheap bugs | C1, C2, C4, B6 | low | Regression test per bug: stop→start leaves no pending claim; safety-pause then resume in BOTH does not halt | **done** (`cc7767c`), engine changes by inspection only |
| 2 | Hot paths | A1, A4, A2 (coalescing only) + C3 | low | No market recompute or quote mutation off the tick path; HUD frame cost flat w.r.t. allowlist size | **done**, see note below |
| 3 | Shared menu layer | B1 (pure pieces first, then settle/recheck), A3 | medium | Both engines drive the same observation code; existing menu/confirmation tests pass unchanged | **partly done**, see note below |
| 4 | Movement model | B2, and A5 on top of it | **high** | Replay buy → partial claim → cancel → store across both pages → retrieve → combine → sell → claim, with wrong page, full storage, lookalike books, cursor-held output; no move recorded from disappearance alone |
| 5 | State shape | B3, B4, B5 | medium | `stop()` provably resets everything; no state has two actions in one tick; one failure contract |

### Stage 2 as shipped, and what was deliberately left out

A1 and A4 landed in full. A2 landed **only as in-flight request coalescing**:
`BazaarApi.fetch()` now shares one outstanding request between callers, handing
each a dependent future so one engine's `cancel(true)` cannot kill the request
another is waiting on. That removes duplicate work whenever the three pollers
overlap, but it does **not** consolidate their cadences — `FlipCalculator`
(20 s), `BazaarMonitor` (20 s) and `GeneralFlipper` (`refreshSeconds`) still
each decide when to ask.

Full consolidation to a single owner was deferred on purpose. It changes how
old a snapshot can be at the moment a money decision reads it, and
`TradingSafety.fresh` allows up to 60 s. Shifting that boundary without being
able to run the suite is not a trade worth making, so it becomes its own stage
with its own fixtures.

Also deliberately kept: the `deepCopy` in `BazaarQuoteCache.publish`. It is one
copy per accepted snapshot, not per tick, and it is the isolation guarantee that
makes "readers treat the published JSON as immutable" true rather than merely
observed.

One more cost found while doing stage 2 and fixed with it:
`DiagnosticLog.redact` compiled seven patterns on every call, on a path that
runs for every logged string — and `BazaarFlipper.debug()` logs many times per
tick. Patterns are now static. The eager string concatenation in those `debug()`
arguments is still paid whether or not the event is kept; making those lazy is a
stage 5 item, since it touches every call site.

### Stage 3 as shipped, and what is still duplicated

Extracted and now shared by both engines:

- **`OrderLore.creator`** - the three-way co-op ownership decision (OWN / OTHER /
  UNREADABLE). Both engines had open-coded the same `Pattern.compile("(?m)^\\s*By:")`
  plus `ownOrder` combination inline. Only UNREADABLE is retryable, because a
  missing creator field can mean the menu had not finished loading, while a
  readable name that is not ours is a final answer. This was the most valuable
  row in the B1 table: duplicated *safety* logic, and now pure and tested.
- **`MenuSettle`** - the "same container open for 750ms" debounce, previously
  inline in `BazaarFlipper.onTick` and inside `GeneralFlipper.ordersReady()`.
- **`MenuText`** - `containerEnd(slotCount)`, single-sourcing the "the last 36
  slots are the player inventory" assumption that was open-coded in five places,
  and `titleContains`, which strips formatting codes.
- **`SignEntry`** - the identical reflection into `AbstractSignEditScreen.messages`.

Two behaviour changes came with it, both deliberate:

1. `GeneralFlipper.menu(String)` now strips formatting codes, matching
   `BazaarFlipper.containerNameCheck`. A code *inside* the searched label
   (`"Confirm §aBuy Order"` against `"Confirm Buy"`) used to make a known menu
   unrecognisable, which surfaced as a 30-second step timeout. The exact-match
   gates in `TradingSafety.confirmationTitle` are unchanged, so this widens
   recognition without widening what may be clicked.
2. A failed sign write now halts immediately with a specific reason. Previously
   it was logged and retried, and the engine only stopped when the watchdog
   noticed 60 seconds later - or, worse, proceeded with whatever amount the sign
   already held.

A3 landed as local memoisation rather than an observation snapshot: the STORE,
ANVIL and COMBINE branches re-ran the *same* container/inventory query up to five
times per tick, including twice just to build a log line. Since nothing between
those calls moves an item, each block now scans once. 34 `findLore*` call sites
became 16.

**Still duplicated, deliberately:**

- **Capacity has genuinely diverged** and was left alone. `GeneralFlipper.capacityFor`
  is stack-aware and reserves four slots; books uses bare
  `getEmptyInventorySlots()`. Unifying them changes how many books the engine
  believes it can claim, which is a live trading decision - it needs stage 4's
  fixtures, not a mechanical merge. This is the clearest evidence for B1's
  argument: the duplication has already drifted.
- **Slot search stays separate.** `InventoryScanner.findContainer` matches on
  `getCustomName()` and `GeneralFlipper.find` on `getHoverName()`. These are not
  the same predicate, so sharing them would silently change what each engine
  finds. Only the region bound is shared.
- **The recheck wrappers stay separate.** `recheckBookOrders` closes the
  container; `recheckOrders` sets `reopeningOrders` and re-issues the command.
  The difference is real behaviour, not duplication to collapse.
- **No observation snapshot yet.** Building one and rewiring both engines onto it
  is the part that needs a runnable suite.

Ordering rationale: 1 and 2 are independent and shippable immediately. 3 must
precede 4 and 5, because both need the observation snapshot to express their
rules. 4 before 5 because B2's records are the biggest consumers of B3's
grouping.

## Explicitly out of scope

- The click *sequences* themselves (which slot, in what order) — those encode
  server behaviour that this session cannot verify against a live Hypixel menu.
- The fail-closed checks in `TradingSafety`, `ConfirmationCheck` and `OrderLore`.
  They are the most careful code in the repo and the refactor should move them
  unchanged, not "simplify" them.
- Anything that would make an unverified outcome resolve optimistically. Every
  change above must preserve "unknown stays blocked".
- Instant buy/sell routes, which currently `safetyHalt` by design.
