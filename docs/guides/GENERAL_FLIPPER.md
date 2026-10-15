# Book, general, craft and combined modes

Current command syntax and resets: [command reference](../COMMANDS.md).
**F6** toggles trading; **F7** exports diagnostics; **F8** reloads settings.
Select the mode in **G → Macros** while stopped, or use
`.a* goofyaddon mode <books|general|both|craft|all>`.
`both` selects book + general entries; `all` compares those with craft entries.
Calculator-off book/general selection uses configured presets. Calculator-on
selection uses supported proposals and repeats execution checks. Enabling All
mode does not change the calculator switches.

One scheduler owns GUI actions at a time. Orders can wait concurrently, but
submissions, claims and inventory changes still require server evidence.
After a profile change, restart Minecraft if trading files were already pinned.

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

Intent and positions are saved in `config/goofyaddons/accounts/<player>/<profile>/goofyaddons-general-orders.json` before
order confirmation. Mode switches and stops retain submitted positions. Restart
checks server orders and inventory before placing a new order. Corrupt state files
are preserved and block startup. Unrelated existing orders or inventory for an unsubmitted ordinary entry cause
that product to be skipped for 60 seconds; the mod does not adopt them or pause
unrelated pipelines. Submitted and craft ownership conflicts still need review. Keep the state file until
all its tracked orders and inventory have been reconciled.

## Verification and limits

The mod compiles for Minecraft 26.3 with Java 25. Automated tests cover the menu
scheduler, shared reservations, candidate margin/volume/capacity filtering, API
recovery, and config preservation. Engine simulations also cover consecutive
buy/claim/sell/settle cycles, partial fills, repricing, delayed receipts, bounded
claim retries and missing purse readings. Their synthetic menus and messages do
not establish that Hypixel's live labels or packet timings match the fixtures.

This is a draft beta requiring live validation with small orders. Exact menu and
lore labels, stacking behavior, and server confirmation timing need checking.
Use ordinary items initially. Unknown stack limits deliberately restrict buy
sizes until matching native item data is visible. The realized-profit dashboard
uses confirmed receipts; it does not guarantee earnings.

Supported automatic selection and craft discovery are available in the combined
mod. Unavailable requirements, prices or execution support still exclude routes.

## Reloading settings

Stop trading with F6 or `.a* goofyaddon stop`, then use F8 or
`.a* goofyaddon reload`. Chat identifies the active file and load result. Profile
settings live beside the profile journals. A rejected reload preserves the last
working config; an invalid newly selected profile file blocks entry rather than
borrowing another profile's capital settings.

## Manual cleanup

Use `.a* goofyaddon orders` to inspect ordinary records. After manually handling
all affected server orders/items, `orders release <unique-id>` forgets one record,
and `orders release all` forgets all ordinary records on that profile. Neither
records a sale/refund/profit. `profit clearhistory` retains these active records.
See the command reference before choosing between cleanup operations.
