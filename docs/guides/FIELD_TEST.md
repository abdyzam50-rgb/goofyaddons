# Field test protocol — 1.3.8-BETA

Thirteen commits of engine changes have been verified by a 201-test suite and by reading the
code. **None of it has met a real Hypixel menu.** This protocol exists to close that gap in
stages, cheapest first, so each unknown is answered before the next one can cost anything.

Work through the stages in order. Each says what to set, what success looks like, what to
capture, and when to stop. Do not skip stage 1 — it costs nothing and rules out most of what
could go wrong later.

One thing to decide before you start: automating Bazaar menus is against Hypixel's rules. The
realistic risk is to the account, not only to the coins. That is your call, but make it before
stage 2 rather than during it.

---

## Stage 0 — it loads

No trading. Just confirm the mod is alive.

1. Drop the jar and Fabric API into `.minecraft/mods/`, launch, join SkyBlock.
2. Confirm `.minecraft/config/goofyaddons.json` was created.
3. Confirm the status panel renders, and run `/goofyprofit` and `/goofydebug`.

**Success:** the panel draws, shows `STOPPED`, and `/goofydebug` reports logging healthy.

**Look at the panel specifically.** Its layout was rebuilt and verified only as arithmetic —
nobody has seen it render. Check that rows are not cut off, the big profit number is not
clipped, and nothing overlaps. Try your actual GUI scale, and `/goofyprofit scale 0.75` and
`3.0` to see the extremes. If it looks wrong, that is a one-constant fix; send a screenshot.

**Do not press J yet.**

---

## Stage 1 — it runs without trading

A real dry run using config alone. The engine polls, thinks, and renders, but no flip can ever
qualify, so it cannot place an order.

Set in `goofyaddons.json`:

```json
"tradingMode": "BOTH",
"minNetProfit": 999999999,
"purseReserve": 999999999,
"general": { "minProfitPerBatch": 999999999 }
```

Press `\` (backslash) while stopped to reload, then press **J**.

**Success, after a few minutes:**

- the panel shows `RUNNING`
- "Price data" reads `Fresh`, not `Waiting / stale`
- "Spendable" reads `0` and the activity line says it found nothing eligible
- `/goofydebug` shows `api.fetch_succeeded` events
- **no `menu.click` events at all**

**What this proves:** the mod loads and ticks, the Bazaar API works through your network, your
purse parses off the scoreboard, quotes arrive fresh, diagnostics write to disk, and the HUD
updates live. That is four of the unknowns gone for free.

**Stop and report if:** any `menu.click` appears (it should be impossible), "Price data" stays
stale for over two minutes, "Spendable" is non-zero, or the purse row shows `--` (scoreboard
parsing failed — a real bug, and worth a report on its own).

Press **K** to stop.

---

## Stage 2 — one order, watched

The first time it spends anything. Sit and watch the whole thing; do not walk away.

Set a single cheap route and a trivial budget:

```json
"tradingMode": "BOOKS",
"minNetProfit": 1000,
"maxTradingCapital": 2000000,
"purseReserve": <your purse minus about 2m>,
"books": [ { "id": "ENCHANTMENT_ULTIMATE_WISE", "level": 1, "sellLevel": 2,
             "name": "Ultimate Wise", "instaBuyPercentage": 0, "instaSellPercentage": 0 } ]
```

`sellLevel: 2` is deliberate — it needs only two books and one combine, so a full cycle
finishes in minutes instead of hours.

Reload, press **J**, and watch the task line move. The sequence should be: a buy order placed,
filled, claimed, a second one, both combined at an anvil, the result listed for sale.

**Capture regardless of outcome:** `/goofydebug export`, plus a note of roughly when anything
looked wrong.

**Success:** one full cycle completes and "Claims" increments.

**Expected and fine:** a safety pause. "Trading paused: …" means it refused to guess, which is
the designed behaviour and a *good* outcome for a first run. Export and send it — a pause tells
us precisely which check fired on real data, which is the most useful result this stage can
produce short of a clean cycle.

**Stop immediately and export if:**

- coins leave the purse with no order visible in `/bz` → orders
- a book disappears from storage and the engine carries on as if it had it (this is A08, the
  one known issue that can lose a book)
- it clicks faster than roughly twice a second
- the same menu opens and closes repeatedly without progress
- any order appears with an amount or price you did not expect

---

## Stage 3 — a cycle on its own

Only after stage 2 completed cleanly at least once. Same config, but let it run 30–60 minutes
while you check back.

**What this stage is actually testing** — the five loop fixes that have never run live:

| Watch for | Means |
|---|---|
| sales collected promptly, not left while new buys start | the task-ordering fix works |
| `books.order_recheck_due` then `books.order_rechecked` | a stalled order woke itself |
| `books.extra_exposure_resized` | leftover books stopped pinning capital |
| "Spendable" not drifting toward zero across cycles | no capital leak |
| storage never reopening the same full page | the storage dead-end is gone |

Raise `maxTradingCapital` only after a stage-3 run finishes with the books and coins accounted
for, and raise it gradually.

---

## Sending a report

`/goofydebug export` writes a ZIP under `.minecraft/logs/goofyaddons/bundles/`.

**Open it before sending.** It contains item names, prices and trade receipts. It does not
contain your account details, chat, or server addresses. Known credential patterns and your
home directory are redacted.

Useful with it: which stage, what you saw, roughly when, and a screenshot if the panel looked
wrong.

The single most valuable thing in a report is a **confirmation screen that failed to parse**.
`ConfirmationCheck` was built against synthetic fixtures plus one real Overload I tooltip; every
other Hypixel confirmation format is still guesswork. A `books.confirmation_check` event with
`previewMatches=false` names the exact format we got wrong, and that is a same-day fix.
