# Books, General, and Both (1.2.0-BETA)

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
movement, and realized returns are not modeled. Repriced sell offers must retain
the configured unit margin. Unprofitable inventory is kept for later checking,
not automatically sold at a loss.

## Position handling

The engine verifies that an order appears in the server menu before marking it
as placed. It can claim a partial buy fill, cancel the unfilled remainder, and
sell the acquired quantity. Empty cancelled buys can be replaced up to the
configured limit, then enter a cooldown. Sell offers can be cancelled and
replaced after an undercut while preserving the remaining inventory quantity.
Completed sales are claimed from the order menu.

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
recovery, and config preservation. They do not reproduce Hypixel's live menus,
server messages, partial-fill packets, or travel.

This is a draft beta requiring live validation with small orders. Exact menu and
lore labels, stacking behavior, and server confirmation timing need checking.
Use stackable ordinary items initially. There is no realized-profit dashboard,
full market-wide auto-discovery, recipe discovery, or hourly earnings guarantee.
The previous book engine's instant transaction paths and general AntiStuck work
remain unfinished. Use order-based book transactions initially.
