# GoofyAddons command reference

Checked against the combined mod **0.2.126-BETA** command registrations. Type these
in Minecraft chat with a **dot**, not a slash. `.astar goofyaddon ...` is an alias
for `.a* goofyaddon ...`. The old standalone `/goofy...` commands are not registered.
Use Tab completion; `<placeholders>` below must be replaced, without angle brackets.

Commands such as `/bz` and `/pickupstash` are server commands, separate from the
mod's dot commands. Test commands below perform real transactions, not simulations.

## Start, stop and inspect

| Command | Meaning |
| --- | --- |
| `.a* goofyaddon start` | Start the configured mode, including saved-position recovery. Scheduled rests can defer trading or log out instead of starting immediately. |
| `.a* goofyaddon stop` | Stop trading/tests and disarm the session schedule. Saved orders and uncertain transactions remain tracked. |
| `.a* goofyaddon toggle` | Stop if active, paused, recovering or armed; otherwise start. |
| `.a* goofyaddon status` | Show lifecycle, current activity and session-schedule status. |
| `.a* goofyaddon mode` | Show the current runtime mode. |
| `.a* goofyaddon mode <books\|general\|both\|craft\|all>` | Save a mode choice while stopped. `both` is books + general; `all` adds craft selection. |
| `.a* goofyaddon reload` | Reload the active settings file while stopped. A failed reload reports the problem; do not assume the edited file was accepted. |
| `.a* goofyaddon debug version` | Show mod/component versions and mismatches. |
| `.a* goofyaddon debug export` | Export the current GUI, journals, chat, market evidence and diagnostics. Also available with F7 while a container is open. |
| `.a* goofyaddon schedule` | Show session-schedule status. |
| `.a* goofyaddon schedule on` | Request a manual session start using the configured schedule. This does not edit the schedule's enabled setting. |
| `.a* goofyaddon schedule off` | Stop/disarm the session, retaining orders. This does not delete the saved schedule. |

Defaults: **G** opens A*; **F6** toggles trading; **F7** exports diagnostics;
**F8** reloads settings. Bindings can be changed. `.a* stop` is the pathfinder's
stop command; use `.a* goofyaddon stop` for trading.

## Reporting history versus retained trades

Run cleanup on the profile that owns the records, with trading and production
stopped. These operations do not cancel or claim anything on the server.

| Command | Changes | Keeps |
| --- | --- | --- |
| `.a* goofyaddon profit reset` | Starts a new reporting session/timer. | Historical receipts, open acquisition costs and order records. |
| `.a* goofyaddon profit clearhistory` | Clears completed profit/trade history and completed execution samples for the current profile. | Active orders, holdings, remaining cost basis, active execution evidence, settings and receipt deduplication. |
| `.a* goofyaddon orders` | Lists retained ordinary/general flip records and their IDs; changes nothing. | All records. |
| `.a* goofyaddon orders release <id>` | Forgets one ordinary flip record and releases its budget reservation. | Other records and reporting history. No sale, refund or profit is assumed. |
| `.a* goofyaddon orders release all` | Forgets every ordinary flip record for this profile. | Book/craft journals and reporting history. No server orders are changed. |

For `orders release`, use a unique ID prefix of at least four characters, preferably
the eight characters printed by `orders`. Ambiguous prefixes are refused. `all`
is an explicit bulk release, not a synonym for clearing completed history.
These commands do not erase other profiles, backup files or previously published
shared market/gameplay datasets.

Only release a record after you have checked the actual Bazaar orders, inventory,
storage and stash and have manually handled its items/coins. If a real order still
exists, forgetting its local record leaves that order unmanaged. A missing order
alone does not establish whether it sold, was cancelled or was manually claimed.

Example after manually finishing **one** ordinary trade (replace the example ID):

```text
.a* goofyaddon stop
.a* goofyaddon orders
.a* goofyaddon orders release abcd1234
.a* goofyaddon orders
```

Other profit controls: `profit` prints totals; `profit hud` toggles the HUD;
`profit left`, `profit right` and `profit scale <0.75..3.0>` change its appearance.

## Profiles and saved files

| Command | Meaning |
| --- | --- |
| `.a* goofyaddon profiles` | Show observed profile, pinned trading profile and legacy-file status. |
| `.a* goofyaddon profiles adopt` | While stopped, copy unclaimed legacy files into the current profile. Use only on their owning profile; normal live recovery follows before trading. |
| `.a* goofyaddon profiles setaside` | Mark unclaimed legacy files as set aside without copying them into the profile. Original files remain. |

`adopt` and `setaside` are legacy migration commands, not resets or profile switches.
Settings, access restrictions and journals use
`config/goofyaddons/accounts/<player>/<profile>/`. The global
`config/goofyaddons.json` seeds new profile preferences; later saves use that
profile's file. Old global access denials are not imported into every profile.

The mod clears observed skills, API unlocks and forecasts when profiles change.
Once trading files are pinned, **restart Minecraft before trading on a different
profile**. Stopping/starting the macro alone does not switch those loaded journals.
Do not copy another profile's order/history files to get around this check.

## Craft discovery and queued work

| Command | Meaning |
| --- | --- |
| `.a* goofyaddon production flips` | Show the current craft ranking and exclusion reasons. It does not start a trade. |
| `.a* goofyaddon production requirements` | Show current profile prerequisite status and selected unlock observations. |
| `.a* goofyaddon production recipes [ITEM_ID]` | Show catalog coverage, or recipes/requirements for an output. |
| `.a* goofyaddon production status` | Show production activity. |
| `.a* goofyaddon production jobs` | List unresolved jobs and REVIEW IDs. |
| `.a* goofyaddon craft ITEM_ID [batches]` | Queue inventory-backed crafting; default one recipe batch. Open Craft Item and start the trading toggle to service queued work. It does not buy missing inputs. |
| `.a* goofyaddon production run ITEM_ID <batches> [binPrice maximumFee]` | Queue a craft production run. Start it with the trading toggle. Missing inputs are bought only if the production-buy setting allows it; an optional BIN price and fee ceiling request output listing. |
| `.a* goofyaddon production forge ITEM_ID <slot> [binPrice maximumFee]` | Queue one Forge recipe in slot 1–7. Open the Forge and run queued work; duration/Quick Forge and unlock checks apply. |
| `.a* goofyaddon production kat ITEM_ID` | Queue a supported Kat recipe using your matching pet/materials. Open Kat and run queued work. |
| `.a* goofyaddon production claim <job> [binPrice maximumFee]` | Queue claiming exactly one waiting Forge/Kat job; open its workstation and service the queued run. An optional BIN listing requires both price and fee ceiling. |
| `.a* goofyaddon production compactor` | Report detected Personal Compactors and readable configuration. Read its status; this is not a command to enable bulk compaction. |
| `.a* goofyaddon production compactor inspect` | Capture the open Personal Compactor GUI controls for diagnostics/configuration. |

Batch counts are **recipe executions**, not necessarily output item counts.
The command limit is 1–1024; budget, inventory, recipe and market limits can reduce
an executable round. Automatic Craft mode has its own maximum-batches setting.
Quick Crafting is used when a matching usable server control is verified; it is
not granted by issuing a test command. The server may require VIP and Carpentry III.

## Live pipeline tests

Stop normal trading first. These commands start their requested test immediately;
use `.a* goofyaddon stop` to end it. Capital, identity, inventory and unlock checks
still apply. Some test paths deliberately allow entry without the normal profit
target, so a successful test can lose coins. `instant` buys at available asks or
sells into bids; `order` places a buy order; `offer` places a sell offer.

| Command | Work performed |
| --- | --- |
| `.a* goofyaddon test buy ITEM_ID <units> <instant\|order>` | Production trade path: buy only, 1–4096 units. |
| `.a* goofyaddon test sell ITEM_ID <instant\|offer>` | Production trade path: sell owned units; no quantity argument. |
| `.a* goofyaddon test trade ITEM_ID <units> <instant\|order> <instant\|offer>` | Production trade path: buy then sell. |
| `.a* goofyaddon test general buy ITEM_ID <units> <instant\|order>` | Test the ordinary/general engine's buy path. |
| `.a* goofyaddon test general sell ITEM_ID <instant\|offer>` | Test the general engine's sale of owned units. |
| `.a* goofyaddon test general flip ITEM_ID <units> <instant\|order> <instant\|offer>` | Test a general buy/sell cycle. |
| `.a* goofyaddon test book <configured-book> <instant\|order> <instant\|offer>` | Test the first matching configured book route, using its saved input/output levels. Recover existing book positions first. |
| `.a* goofyaddon test pipeline ITEM_ID <batches> <instant\|order> <instant\|offer>` | Craft pipeline: procure inputs, craft and sell on Bazaar; 1–1024 requested batches. |
| `.a* goofyaddon production pipeline ITEM_ID <batches> [strategy [sell]]` | Same craft pipeline test. With no strategy the planner picks; see accepted forms below. |
| `.a* goofyaddon production test ITEM_ID [binPrice]` | One-batch end-to-end craft test. Buys missing inputs for this run; default Bazaar instant exit or an AH BIN listing for an AH output. An explicit BIN price requests AH listing. |
| `.a* goofyaddon production testah ITEM_ID [binPrice]` | One-batch AH-output test. Without a price, uses the supported auction acquisition/pricing path; AH recipes must produce one output item per batch. |
| `.a* goofyaddon test compactor` | **Buys and sells one Dwarven Compactor.** This tests an item trade, not Personal Compactor recipe setup. |

A configured book can be referenced by its configured name or enchantment base ID.
Quote names containing spaces, for example `"Ultimate Wise"`. There is no level
argument to `test book`: configure the desired route in Settings first. If several
configured routes share the name, the first matching route is used.

`production pipeline` accepts `order` (= `ORDER_OFFER`), `instant`
(= `INSTANT_INSTANT`), `ORDER_OFFER`, `INSTANT_OFFER`, `ORDER_INSTANT`,
`INSTANT_INSTANT`, or two words such as `order instant`. In two-word form the
sell mode is `instant` or `offer`, **not** `order`. The separate `test pipeline`
form requires both buy and sell words.

Example syntax (real trades, only when you intend to spend/use these items):

```text
.a* goofyaddon test general flip ENCHANTED_COAL 16 order offer
.a* goofyaddon test book "Ultimate Wise" order offer
.a* goofyaddon test pipeline ENCHANTED_GOLD 2 instant offer
.a* goofyaddon production pipeline ENCHANTED_GOLD 2 ORDER_OFFER
```

AH helpers: `auction inspect` reads the open auction menu;
`auction prepare ITEM_ID <price>` queues preparation without final creation;
`auction sell ITEM_ID <price> <maximumFee>` requests a BIN listing. Prices/fees
are coins. These are not sale receipts: listing an item is not confirmed profit.

## Production recovery: choose the matching operation

Export diagnostics **before** manually changing a stuck transaction. Stop trading
and inspect the actual inventory, grid/cursor, storage/stash, Bazaar orders and AH
listings. A REVIEW job means the outcome needs inspection; it is not a completed sale.

| Command | When it applies |
| --- | --- |
| `.a* goofyaddon production recoverorders` | Start recovery of retained craft Bazaar ingredient orders. It can claim fills and cancel unfilled buys. Submitted output sell offers stay tracked. Follow the resulting messages before acknowledging jobs. |
| `.a* goofyaddon production reconcileorder <child-id> external` | You already manually handled a submitted ingredient buy. Uses a unique child-order prefix of at least eight characters, checks fresh live orders/inventory and archives external-handling evidence before releasing ownership. A child ID is not its parent production job ID. |
| `.a* goofyaddon production acknowledge <job-id>` | After inspection, acknowledge one REVIEW job with no retained order ownership. Journal evidence remains; this does not replay the job or infer items/profit. Storage tracking associated with the acknowledged job is released. |
| `.a* goofyaddon production acknowledge all` | Acknowledge current-profile REVIEW jobs after inspection. Refused if retained craft orders still own items/coins. Active jobs are unchanged. |
| `.a* goofyaddon production release <job-id>` | You have already claimed/cancelled a reviewed job's orders manually: forget its craft-order records and acknowledge that REVIEW job. This does not claim/refund/cancel server orders or record profit. |
| `.a* goofyaddon production release all` | Apply that manual-release operation to all matching REVIEW jobs. Inspect every affected job first. |

Use job IDs from `production jobs`; use at least eight characters for release and
external reconciliation, and prefer the full ID when in doubt. Child order IDs
are available in diagnostic/order evidence. Neither `orders release` nor
`profit clearhistory` repairs a book route whose live order has a different level.
Do not delete journals or use an acknowledgement to declare an unknown trade successful.
