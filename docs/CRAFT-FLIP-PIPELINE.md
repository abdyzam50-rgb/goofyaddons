# Craft flip discovery and execution — 0.2.27

## Shipped behavior

Select **CRAFT** in G → Macros → Mode and start with the existing trading toggle.
This mode selects one eligible Bazaar craft, buys its missing base materials,
prepares reviewed basic intermediates, crafts the exact ranked recipe, and
instant-sells verified output. It then recomputes the next route from live data.
Books/general order engines do not start in this mode. Existing lifecycle,
account/profile binding, stop, recovery and menu ownership rules still apply.

The craft plan is available while stopped too: local dashboard → **Craft production
plan**, or `.a* goofyaddon production flips`. GUI settings control sale market,
maximum batch capital (zero uses spendable capital), minimum net profit and batch
limit (1–16). AH-only selection currently produces previews and no automatic buys.

```mermaid
flowchart TD
    B[Live Hypixel Bazaar depth] --> P[Verified recipe and batch planner]
    C[Coflnet craft candidates and BIN quotes] --> P
    A[Account unlocks, inventory and spending limits] --> P
    P --> E[Eligible Bazaar craft]
    P --> H[AH preview with settlement blocker]
    E --> Q[Journal and bind exact recipe]
    Q --> I[Buy inputs and prepare intermediates]
    I --> K[Verify crafting output]
    K --> S[Verify instant sale]
    S --> L[Record actual or unknown profit]
    L --> P
```

## Sources and contracts

- Bazaar inputs/output prices come from the normal public Hypixel Bazaar API,
  refreshed through the existing collector/Bazaar API adapter. Quotes over 60
  seconds old are unusable.
- Coflnet supplies AH craft candidates through `/api/crafts/profit`; public
  SkyCrafts controller path `/crafts/profit` and `ProfitableCraft` fields are
  documented in [Coflnet/SkyCrafts](https://github.com/Coflnet/SkyCrafts/tree/1d25352d31cfa94f04a797bd7c120e4d0bc3b565).
  Coflnet's [official crafting command](https://github.com/Coflnet/SkyModCommands/blob/8f8e8e95db043b87ac0e0fb53a4085511c4e3b87/Commands/State/CraftsCommand.cs)
  calls `GetProfitableAsync`. Rows must identify a crafting route, contain positive
  cost/price/volume/median values, and be updated within five minutes.
- The local endpoint `/v1/crafts/market` returns `goofy-craft-market/1`, sanitized
  candidates and separately refreshed `goofy-ah-price/1` lowest BIN quotes. No
  auction UUID is returned or used for navigation. Bazaar output IDs are excluded
  from AH discovery when the companion has a fresh Bazaar snapshot.
- Discovery refreshes at most once a minute; bounded quote rotation runs at most
  every 20 seconds. Four leading candidates plus four rotating candidates are
  considered per update. Quotes expire after 60 seconds. Discovery failures do
  not prevent Bazaar ranking and never supply invented prices.
- Use the existing private `COFLNET_TOKEN` environment variable or
  `coflnet-token.txt` in the companion data directory if the API needs access.
  Credentials remain private and are sent only to Coflnet.

External candidates must match the mod's bundled, validated NEU-derived crafting
grids. Unknown recipes are never converted into guessed clicks. Forge, Kat, NPC
shop and non-crafting rows cannot enter this automatic craft loop.

## Feasibility and ranking

Each candidate evaluates whole batches, not arbitrary scaled top prices:

1. Expand reviewed basic intermediates (rods → powder, logs → planks → sticks,
   etc.) using the same preparation planner as execution. An input without fresh
   Bazaar depth is unsupported for automatic procurement.
2. Walk the full ask-side input depth; include the existing observed 4% instant-buy
   surcharge and a 3% total procurement allowance.
3. Walk bid-side output depth for the entire Bazaar batch; subtract configured
   tax and a 3% sale-price movement allowance. AH previews use a fresh validated
   lowest BIN, the conservative listing fee ceiling, a 10% GUI-price allowance
   and a 3.5% claim-tax allowance. These are estimates, not paid receipts.
4. Fit the batch inside spendable funds, configured batch capital, empty inventory
   space, grid/output/intermediate capacity and the 1–16 batch executor limit.
5. Require observed collection/skill/slayer unlocks. Skip conflicting positions,
   held outputs and active/unreadable Personal Compactor recipes.
6. Limit a Bazaar batch to 5% of daily volume estimated from the smaller weekly
   buy/sell counter. Rank using conservative net profit, estimated GUI work and
   liquidity. Coflnet's reported volume affects AH confidence without assuming
   its unspecified time window is daily volume.

The ranking score is an ordering aid, **not realized coins/hour**. Craft timings
are estimates; order-flip gameplay corrections are not applied to craft routes.
A shared per-craft timing/profit learning contract remains future work.

Before queueing, the mod recomputes eligibility from current inventory and budget.
The production run binds the selected recipe key through final crafting. Before
further purchases, fresh output depth must still clear the minimum-profit target.
The run's aggregate spending envelope shrinks by receipt-confirmed purchases.
Completed output is sold at the then-current verified quote; sale is not blocked
by the original profit target after crafting.

## Receipts and profit

A completed output and an instant sale produce idempotent `craft` profit-ledger
events. Cost is receipt-confirmed input spending. Using previously held inputs
makes cost basis unknown; the ledger counts that settlement as incomplete rather
than treating old materials as free. All bought material costs are conservatively
charged to this run, including any preparation surplus; surplus cost lots are
not yet allocated across later craft runs.

A production instant sale requires the expected items to disappear and a purse
increase within the quoted range. An unrelated large purse change is uncertain
and cannot create confirmed profit. Publication of an AH listing never counts as
a completed sale. Saved interrupted transactions continue to require review.

## Remaining AH pipeline work

AH crafts appear in the plan with an explicit settlement blocker. The existing
`.a* goofyaddon production test ITEM_ID` can individually buy supported Bazaar
components, craft, validate normal /ah GUI prices against Coflnet, and publish
one verified BIN listing. This does not implement AH-component procurement or
sale/expiry/claim reconciliation. Multi-output AH batches remain unsupported.

Before enabling automatic AH loops, implement tracked listing capacity/capital,
exact seller-listing reconciliation, sold/expired state handling, claim receipts,
actual listing/claim fees and recovery after reconnect. Arbitrary attributes,
reforges and pet variants also need their own pricing/identity contracts.

## Validation limits

Automated Java/Node tests and a Chromium dashboard check cover planning, source
validation, fee/depth/capacity gates, menu composition, recipe pinning, receipts,
redaction and stale-data behavior. Live Minecraft execution and the deployed
Coflnet gateway still need real-session validation; direct gateway access from
this development environment returned HTTP 403. The controller/model contract
was verified from Coflnet's public source instead.

## Stacked crafting (0.2.28)

The executor now loads ingredients for several recipe uses before collecting the
output. Each grid cell is bounded by its ingredient's observed stack limit,
available ingredients, remaining requested batches and conservative output space.
Known basic stackable outputs reserve space at 64 units per slot; unknown outputs
reserve one slot per unit. Large recipes are split into chunks that fit the grid.
Intermediate preparation child jobs now accept up to 64 recipe uses instead of 16.
The existing final-production investment and batch limits remain unchanged.

For 64 blaze rods, the grid is filled once and the 128 powder is checked against
64 consumed rods. If the server's shift click produces just one recipe use, the
remaining ingredients stay loaded and the executor collects subsequent outputs.
No refill occurs between those partial collections. Progress advances only after
exact ingredient/output conservation and an empty grid/cursor are verified; the
journal saves the verified chunk's actual completed recipe uses in one write.

Normal acknowledged actions settle for 50 ms (one game tick) rather than 100 ms.
Slowdown messages increase this delay and impose the existing one-second cooldown.
Unchanged/rejected inputs retain bounded retries; unexplained inventory changes
pause execution with owned items retained. This does not batch unacknowledged
clicks or assume shift-click output succeeded. Live server behavior still needs
validation with the downloadable mod.

`.a* goofyaddon craft OUTPUT_ID 64` can queue up to 64 uses from held ingredients;
start it with the usual trading toggle. Smaller requested quantities stay exact.

## Account lookup and cold startup (0.2.29)

Craft prerequisites no longer depend on the localhost calculator being available.
The mod calls the same fixed HTTPS `/v1/profiles?username=...` service as the public
website, using the normal Java HTTP proxy policy rather than the loopback-only
client. It needs no player API key or contributor key. Account/profile identity,
live-skill agreement, unknown collection handling and the five-minute freshness
limit remain enforced before spending. An unavailable service, a missing unlock,
wrong profile or expired response still blocks purchases. Service failures retain
bounded backoff and clear error codes; diagnostics identify `PUBLIC_WEBSITE` as
the source. No account profile is uploaded to the shared gameplay dataset.

This separates progression verification from the calculator's market/feed/upload
process. Automatic market selection, the local dashboard and shared learning still
need that process. The calculator now registers its bundled website allowlist but
reads each static file only on first request, rather than reading more than 5,000
files before opening its port. The supervisor allows up to 45 seconds for cold
startup, off the game thread, while still noticing an immediate Node exit. It
continues to preserve pairing keys, saved history and existing configuration.

The screenshot's `Local calculator connection failed` proves the old prerequisite
request could not reach the local server; it does not identify the Node process's
original startup error. If the dashboard/feed still waits after this update,
export F7 diagnostics. Its supervisor state, last exit code and redacted
`companion-error.log` tail are needed to identify that separate runtime failure.
No public website redeployment is necessary for this mod-side account fix.
The shared-learning Sync status now includes the supervisor's reason when the
calculator is unavailable (auto-start off, port conflict, preparing or failed),
rather than displaying an indefinite generic wait with no explanation.
