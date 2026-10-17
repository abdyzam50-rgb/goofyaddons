# Trading inside A*

Current combined release: **0.2.132-BETA**, Minecraft **26.3**, Java **25**,
Fabric Loader **0.19.5+** and Fabric API **0.161.0+26.3**.
Download [the combined JAR](../dist/astar-client-0.2.132-BETA.jar).
Install it with Fabric API; remove duplicate standalone A*/GoofyAddons JARs.

The [command reference](COMMANDS.md) is the source for current user commands,
including tests, resets and recovery. Older standalone documents under
`integrations/goofyaddons/` preserve upstream context; their `/goofy...` commands,
J/K/M defaults and older Minecraft versions do not describe this combined build.

## First session

1. Join SkyBlock on the intended profile and wait for its name to be identified.
2. Press **G → Macros**. While stopped, review the capital limit, purse reserve,
   active positions, craft batch limit, storage pages and account prerequisites.
   Values are coins: `5000000` is 5M; `5` is five coins.
3. Choose a trading mode. Defaults are **F6** start/stop, **F7** debug export,
   **F8** reload. Custom bindings take precedence.
4. Start with small limits and let a complete purchase → craft/combine if needed
   → sale → claim finish. Check restart recovery before a long session.
5. If a step stops making progress, export diagnostics before manually moving
   items or cancelling orders. A pause retains ownership evidence for recovery.

Valid settings save automatically while stopped; text edits debounce for 750 ms.
Invalid values remain unsaved with their error. A failed write preserves working
settings. Reopen Settings if the active profile settings file changes while a
draft is open. Fresh/default config warnings must be reviewed before trading.
Reload reads the active settings file; check its success message.

## Modes and selection

| Mode | New work selected |
| --- | --- |
| `books` | Buy configured/selected input books, combine, sell and claim. |
| `general` | Ordinary Bazaar item flips. |
| `both` | Books + general; no automatic craft discovery. |
| `craft` | Automatic supported craft routes and their production work. |
| `all` | Compare craft, book and general entries by estimated net coins/hour. |

Existing positions remain managed. Shared capital, inventory reservations and
product locks constrain new entries. Up to four configured active craft jobs can
wait on orders concurrently; GUI actions remain sequential under one menu owner.
Requested batches are reduced to fit money, depth, volume and inventory limits.

With **automatic selection off**, books/general use configured presets; crafts
use the local craft ranker. `all` respects that switch and compares local preset
estimates. With automatic selection on, supported book/general proposals come
from the calculator and are checked again by the execution engine. No mode
silently enables the switch. Calculator **auto-start**, analysis **enabled** and
**automatic selection** are separate settings: disabling auto-start alone does
not disable a separately running calculator or select preset mode.

A ranking is an estimate, not a trade receipt. The website and mod can differ
because of settings, profile unlocks, evidence age, execution support, reserved
funds and inventory. Search the craft ranking/exclusion reason before changing
limits. Missing prices, insufficient volume or unverified unlocks can leave no
feasible route without indicating a frozen client.

## Same-day execution feedback

The local calculator's book/general rankings blend recent personal evidence with
historical estimates: **30%** after 1–2 completed cycles, **50%** after 3–5 and
**70%** after 6 or more. Evidence keeps full influence for two hours, then fades
smoothly over 24 hours. This is a rolling window, not a midnight reset. Timing
is matched by item/recipe and buy/sell method; upside needs six completed cycles.
Live prices and order-book depth still determine current costs and proceeds.

Unfinished trades lower estimated hourly returns as soon as their observed wait
exceeds the forecast. Outbid/reprice time stays in the original cycle. Retired
orders retain a duration lower bound; verified losses reduce profit realization.
Three market stalls within 15 minutes defer new entries for that route for five
minutes after the latest stall; existing positions still receive maintenance.
The mod sends authoritative personal-history snapshots: profile changes and
history resets remove stale private samples from the companion. Directly
observed waits can reduce throughput beyond 10×; borrowed peer corrections
remain bounded.
GUI/verification failures reduce engine/strategy reliability separately from
market timing. Pausing or disconnecting alone is not recorded as a market failure.
No failure record invents a sale, refund or realized profit.

These adjustments apply to automatic book/general calculator rankings, including
their comparison in All mode. Manual preset selection, the separate craft ranker
and the public website do not consume this local execution calibration.

## Budget and timing

After the holding-time limit expires, an outbid General sell offer can resume
repricing beyond the normal reprice cap. Ownership, quote freshness, cooldown,
inventory capacity and drawdown checks still apply. This does not force an
instant sale or guarantee a buyer.

`maxTradingCapital` caps estimated committed input costs. `purseReserve` leaves
coins free; it is not a batch budget. Craft batch capital is a per-run maximum;
zero uses spendable funds. Other active reservations can still reduce it. Bank
balance and unrelated/manual positions are not automatically spendable capital.

Navigation defaults are **60–70 ms**; configurable ranges can start at 1–2 ms.
Craft/anvil and storage can use their own delay ranges or inherit navigation.
Those delays schedule actions; they do not replace server acknowledgements.
Clicks, purchases, cursor/grid changes, claims and sales still need verification.

A recognised Hypixel order-placement cooldown defers new orders for 61 seconds
across pipelines. Claims/management can continue; rejected buys retain unspent
reservations and rejected sales retain their output ownership. An unexplained
missing order is not treated as a successful purchase, sale or safe retry.

## Profiles and files

Each account/profile uses:

```text
config/goofyaddons/accounts/<player>/<profile>/
  storage.json
  goofyaddons.json
  goofyaddons-bazaar-access.json
  goofyaddons-book-orders.json
  goofyaddons-general-orders.json
  goofyaddons-craft-orders.json
  goofyaddons-production-jobs.json
  goofyaddons-production-storage.json
  goofyaddons-profit.json
  goofyaddons-execution.json
  goofyaddons-transactions.jsonl
```

Files are created when their feature saves data; not every file must exist on a
new profile. Global `config/goofyaddons.json` seeds a new profile's preferences.
Existing profile settings load automatically. Access denials are profile-local;
old global denials are not copied into every profile. Legacy order files retain
their originals and require an ownership decision before migration when positions
are open; see `profiles adopt` and `profiles setaside` in the command reference.

Skills, API unlocks and forecasts clear on profile changes. After journals have
been pinned for trading, **restart Minecraft to trade on another profile**.
The macro deliberately does not hot-swap loaded orders into a different folder.
A profile folder with conflicting identity metadata is not silently reused.
Back up the whole instance `config/` folder when moving installations; never
merge two profiles' journals to make a recovery warning disappear.

## Unlocks and service ownership

The mod looks up the observed profile through the public Worker. End users do
**not** need their own Hypixel API key. Only the Worker owner configures its
`HYPIXEL_API_KEY`; contributor uploads are separate and optional. Profile lookups
are not written into shared gameplay telemetry.

Missing/private progression remains unknown. Book Enchanting gates and supported
craft collection/Slayer/HotM/skill/reputation/mutation requirements are checked
before entry; server controls remain authoritative and catalogs can lag updates.
Enchanted Red Sand now requires a verified current-profile recipe unlock before
procurement. An older Worker or unavailable unlock metadata leaves it excluded,
including when another profile can craft it. [Profile access](PROFILE-ACCESS.md)
explains service errors and the owner deployment step.

## Crafting and scope limits

Automatic crafts procure supported inputs, monitor orders, craft and perform the
supported Bazaar/AH output path. Grid placement and Quick Crafting use exact
recipe/output checks; quick craft is used only when a usable matching control is
observed. Storage and stash handling are bounded by observed capacity and item
storage rules. A recipe barrier is not assumed to be a successful craft.

Personal Compactor detection/configuration safeguards are distinct from trading
a Dwarven Compactor. Check `production compactor` and
[the compactor guide](PERSONAL-COMPACTOR.md) for the implemented boundary; do not
assume a detected accessory means every recipe supports automatic compaction.
Forge and Kat commands queue supported workstation operations; they are not a
market-wide autonomous Forge/Kat selector. BIN listings remain unsold positions
until settlement evidence is available.

## History and recovery

- `profit reset`: new reporting session; retains historical and active records.
- `profit clearhistory`: clears completed reporting/execution history; retains
  open costs, orders, active evidence and deduplication identities.
- `orders release <id>`: deliberately forgets an ordinary retained record you
  have handled manually. It neither cancels a server order nor records profit.
- Production recovery, review acknowledgement and manual release are separate
  operations. Use the [recovery decision table](COMMANDS.md#production-recovery-choose-the-matching-operation).

Manually selling, moving, listing a different book level or claiming outside the
tracked flow can require reconciliation. Export first and inspect the real items
and orders. A missing order does not prove how it was closed.

## Diagnostics, calculator and deployment

`debug version` reports component versions. `debug export` works from a GUI and
writes a bundle under the active instance's `logs/goofyaddons/bundles/`; chat shows
the path. It includes current menu/cursor, profile status, retained journals,
selection reasons, relevant prices and bounded logs. Share it with context about
any manual changes. Keep credentials private.

The bundled calculator can use installed Node 22+ or provision its verified
private runtime. Its background collector does not start trading. Owner-side
Cloudflare schedules run without visitors or the owner's PC staying online.
Updating only the JAR does not redeploy that Worker. Use the
[owner update instructions](../tools/gameplay-collector/README.md#update-an-existing-deployment)
from the cloned repository, with the existing D1/Worker configuration.

Build from the repository root with `./gradlew -p client test build` (Windows:
`.\gradlew.bat -p client test build`). Output:
`client/build/libs/astar-client-0.2.132-BETA.jar`. Automated checks cover simulated
transactions and regression cases; [in-game validation](guides/IN_GAME_VALIDATION.md)
still needs the actual server's menus, latency and account state.

Further guides: [full calculator](ACCOUNT-CALCULATOR.md),
[public website](PUBLIC-WEBSITE.md), [Discord companion](DISCORD.md),
[profit accounting](guides/PROFIT_TRACKER.md) and
[shared pipeline architecture](PIPELINE-SYSTEMS.md). Use the command reference
for current command syntax if an older design note shows a different interface.
