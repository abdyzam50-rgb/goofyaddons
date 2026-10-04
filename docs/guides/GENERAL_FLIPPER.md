# Books, General, and Both (1.3.21-BETA)

J starts the configured mode. K stops both engines. M cycles Books → General →
Both → Books. These keys can be changed in Minecraft Controls. Mode changes are
queued until the current menu transaction reaches a safe boundary and are saved
to `config/goofyaddons.json`.

Both mode monitors both engines' market tasks concurrently. A round-robin menu
scheduler allows only one engine to issue clicks and commands at a time. An
in-progress transaction retains ownership until it finishes; waiting orders do
not monopolize the menus. World changes pause both engines. J resumes and checks
existing inventory and orders. Disconnects stop trading.

## Shared budget

`maxTradingCapital` caps estimated committed input costs across both engines.
`purseReserve` keeps a minimum amount of coins free. Selected purchases reserve
coins before another engine can select them. Outstanding orders count toward the
capital cap, but their paid costs are not deducted from the purse twice. A brief
settlement interval allows server purse updates to arrive after purchases.

The ledger tracks this mod's positions. It does not discover or account for all
manual or other-mod positions. Existing legacy book orders still rely on the
book engine's startup reconciliation. Do not manage the same items through another
tool while these macros are running.

## General item selection

`general.items` is an editable allowlist of ordinary Bazaar product IDs and their
exact display names. Enchanted-book product IDs are excluded from this engine.
The starter list contains enchanted sugar, redstone, gold, lapis, coal, and iron.
It is not a verified best-profit list and the engine may skip every item when
margins do not meet the filters. Current net margin, historical execution flow,
available cash, and inventory capacity determine the quantity and ranking.

The default general limits are:

- 25m input cost per item and three active items.
- 256 units per order, further limited by free inventory and approximately one
  hour of historical execution volume.
- 25,000 coins estimated profit per batch and 2% estimated net margin.
- At least 10,000 weekly executed units on both sides.
- Quote refresh every 20 seconds, a 180-second buy-order timeout, and at least
  60 seconds between repricing checks, with at most three replacements.

Orders are placed at the quoted top order price. Quote and capital checks run
again at the price screen. Taxes are included; listing/cancellation fees, price
movement, and realized returns are not predicted. Profit and margin requirements
apply before new purchases. Acquired inventory can be listed or repriced at the
verified current sell-offer price, including at a smaller margin or a loss,
within the configured holding-age and drawdown limits. Live prices, quantities
and confirmation identity are checked before submission. New buys still require
fresh API quotes; existing inventory sales can use the verified live menu price.
A temporarily unreadable purse waits up to ten seconds before a purchase pauses.

## Position handling

The engine verifies that an order appears in the server menu before marking it
as placed. It can claim a partial buy fill, cancel the unfilled remainder, and
sell the acquired quantity. Empty cancelled buys can be replaced up to the
configured limit, then enter a cooldown. Sell offers can be cancelled and
replaced after an undercut while preserving the remaining inventory quantity.
Completed sales are claimed from the order menu. Acquired inventory is eligible
for its next work visit after two seconds instead of waiting for the quote-refresh
interval. Sale settlement allows ten seconds for the matching receipt and menu/
inventory updates; absence alone does not release ownership or record proceeds.

An unchanged claim can be retried up to three times after a quiet, stable menu
observation. Slowdown notices delay actions. Inventory changes, changed order
lore, a changed slot or a matching receipt prevent that claim from being replayed.
Order-placement and cancellation confirmations are not automatically replayed.

Intent and positions are saved in `config/goofyaddons-general-orders.json` before
order confirmation. Mode switches and stops retain submitted positions. Restart
checks server orders and inventory before placing a new order. Corrupt state files
are preserved and block startup. Unrelated existing orders or inventory for a
newly selected item pause the general engine instead of silently adopting them.
Stop with K, resolve the conflict, then restart with J. Keep the state file until
all its tracked orders and inventory have been reconciled.

## Verification and limits

The mod compiles for Minecraft 26.1.2 with Java 25. Automated tests cover the menu
scheduler, shared reservations, candidate margin/volume/capacity filtering, API
recovery, and config preservation. Engine simulations also cover consecutive
buy/claim/sell/settle cycles, partial fills, repricing, delayed receipts, bounded
claim retries and missing purse readings. Their synthetic menus and messages do
not establish that Hypixel's live labels or packet timings match the fixtures.

This is a draft beta requiring live validation with small orders. Exact menu and
lore labels, stacking behavior, and server confirmation timing need checking.
Use ordinary items initially. Unknown stack limits deliberately restrict buy
sizes until matching native item data is visible. The realized-profit dashboard
uses confirmed receipts; it does not guarantee earnings. Automatic market-wide selection and recipe discovery remain separate work.
The optional [Bazaar Calc companion](../../tools/bazaar-calc/README.md) now logs
read-only market-wide recommendations while the existing allowlist still controls
execution. Use order-based book transactions initially.

## Reloading settings (1.3.26+)

Stop with K and use `/goofyreload`, or the rebindable **Reload configuration** key
(default backslash). Chat shows the file read and current dashboard/analysis
settings. Invalid files produce a rejection message while retaining the working
config; running/paused/recovering trading must be stopped before reloading.
