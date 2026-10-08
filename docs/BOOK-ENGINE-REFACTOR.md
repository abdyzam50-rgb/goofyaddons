# Book engine foundation refactor

This continues the [trading foundation](../trading-core/README.md). The goal is to give the book engine the same explicit dependency boundary as the general trader, while preserving saved-position reconciliation and transaction evidence checks.

## Work notes

1. **Inspection complete:** the engine constructs Minecraft, monitor, calculator and scoreboard adapters; registers global chat handlers; reads settings and accounting singletons; and resolves its journal path itself. Several decisions also read raw game slots alongside captured snapshots.
2. **Implementation compiles:** added `BookSettings`, `BookServices`, `BookAccounting` and `BookOrderRepository`; `BookTraderFactory` owns live composition and chat subscriptions. Extracted `BookPosition` while preserving the JSON field contract. Engine slot reads use `MenuSnapshot`/`SlotView`; Minecraft screen/sign operations stay in adapters.
3. **Isolation/recovery checks pass:** added engine-level scenarios for construction without global config, independent budgets, invalid ownership batches, verified stale-record cleanup, recovery-write failure, intent-write failure and successful persist-before-click behavior. The engine, monitor and manual calculator receive an explicit clock; the shared action clock now lives in the core.
4. **Compatibility/build checks complete:** the literal legacy JSON document round-trips with the original fields. Full foundation, client and calculator integration checks pass, and the 0.2.16 client artifact includes one copy of the 0.1.1 core.

## Current ownership

| Component | Owns |
| --- | --- |
| `BookTraderFactory` | Minecraft adapters, config projection, global accounting adapter, manager callbacks, lazy journal path and chat subscriptions |
| `BazaarFlipper` | Transaction sequencing, captured inventory/menu evidence, safe-yield rules, recovery decisions and checkpoint gates |
| `BookSettings` | Immutable policy values projected from client settings; no config lookup |
| `BookServices` | Explicit market, capital, timing, requirement and lifecycle ports |
| `BookAccounting` | Required accounting effects; the executor cannot silently locate a profit singleton |
| `BookPosition` | Legacy saved-record fields and whole-batch validation before adoption |
| `BookOrderRepository` / `BookJournal` | Storage contract / existing JSON implementation, including backup and verified reconciliation |
| `GameWorld` / `GameActions` | Platform observation and effects; live implementations remain outside the trading core |

Slot lore and inventory decisions read the tick's captured `MenuSnapshot`. Screen-open/title flags remain separate platform observations so that a menu closed by the engine is immediately recognized as closed. Sign submission goes through `GameActions.writeSign`; the executor checks its result instead of manipulating the game screen itself.

## Boundaries to preserve

- Live adapters own Minecraft access, chat subscriptions, global services and the config-directory path.
- The engine owns transaction sequencing and reconciliation decisions; storage cannot issue game actions.
- Saved record field names and enum values remain compatible. This is not an account/profile migration.
- A rejected recovery plan preserves the original journal. A failed intent checkpoint prevents an irreversible click.
- Delayed or uncertain receipts retain ownership and require evidence; moving dependencies does not authorize replaying an uncertain purchase.
- Captured slot lore, item identity and inventory are the inputs to decisions. Avoid mixing those with new raw-slot reads midway through a tick.

## Verification

Validated for **0.2.16-BETA**: **875 tests** pass (71 foundation, 797 client, 7 calculator integration), with zero failures or skipped tests. The client JAR builds. Packaging checks confirm the action clock is in the bundled core, without a duplicate client class.

The new checks establish:

- Engine construction performs no storage reads, market requests or game actions; startup works with global config unavailable when policy is supplied explicitly.
- Invalid ownership batches reserve no capital, preserve the storage evidence and do not affect another engine's budget.
- A complete empty inventory/storage/order scan can retire stale records without inventing profit or clicking items.
- Failed recovery reconciliation preserves original records and reserved capital.
- Failed intent persistence blocks the buy-confirmation click; successful persistence occurs before its single click.
- Legacy JSON retains its original field contract, and action deadlines use the supplied clock without restarting on each poll.

The confirmation tests start from a prepared confirmation state and exercise real engine ticks; they are not a complete live buying-to-selling session. Existing combine, transfer, retirement, pricing and recovery suites also pass. In-game testing remains a separate step; this refactor does not diagnose the unresolved calculator-startup failure on the user's PC.

## Remaining structural work

- The executor still contains the large book transaction state machine. Extract individual operations into shared transaction primitives after preserving their scenarios.
- The executor remains outside the JDK-only core: quote/forecast types and several helpers still depend on the broader client integration.
- Legacy helper convenience constructors still provide global/live defaults for other callers; this engine uses their injected constructors. Some task initialization still uses wall-clock defaults.
- Live services still share global accounting and capital through the composition layer. Orders, profit and learning do not yet have one atomic persistence transaction.
- Account/profile scoping and journal schema migrations remain separate work. No ownership file is deleted or silently migrated in this pass.
