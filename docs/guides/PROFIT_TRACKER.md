# Profit HUD, history and retained records

Current combined-mod commands use `.a* goofyaddon profit`, not `/goofyprofit`.
See [the command reference](../COMMANDS.md#reporting-history-versus-retained-trades).

| Command | Effect |
| --- | --- |
| `.a* goofyaddon profit` | Print confirmed session totals. |
| `.a* goofyaddon profit hud` | Toggle the HUD. |
| `.a* goofyaddon profit left` / `right` | Choose its corner. |
| `.a* goofyaddon profit scale <size>` | Set scale from 0.75 to 3.0. |
| `.a* goofyaddon profit reset` | Start a new reporting session/timer; keep historical receipts and open costs/orders. |
| `.a* goofyaddon profit clearhistory` | While stopped, clear completed profit/trade history and completed execution samples for the current profile. Active holdings, costs, execution evidence, orders and deduplication remain. |

Clearing history is not forgetting an active trade. If you manually handled a
retained ordinary trade, inspect `.a* goofyaddon orders`, then use
`.a* goofyaddon orders release <unique-id>` while stopped. This only releases
local ownership/budget; it does not claim/cancel server orders or infer profit.
Book and craft recovery have separate rules in the command reference. Never
remove a live order record merely to make a profit total disappear.

## Accounting

Ordinary-item costs are recorded when purchased inventory is verified, using the unit price chosen in the purchase menu. Book costs are recorded when input books actually arrive, using the managed order's unit-price lore. Combining sums the cost of all required input books. Book inputs bought at different prices retain their individual cost lots.

Confirmed sale claims provide the credited coins. They are recorded only after the engine's ownership checks accept the sale/cancellation. Profit is claimed proceeds minus the consumed acquisition costs; the configured tax is not deducted a second time from claim proceeds. Refunds, deposits, rewards, changing purse balances, pending orders, and unsold inventory do not count as earnings. Negative outcomes stay negative. Partial sales consume only their share of purchase costs and repeat receipts cannot count twice.

Receipts and acquisition lots are persisted in `config/goofyaddons/accounts/<player>/<profile>/goofyaddons-profit.json`. Stats survive restarts, but the timer counts only active trading, including waiting for fills, and excludes stops, pauses, and offline time. The session continues until explicitly reset. Rate appears only after one active minute and at least one fully priced claim; it is a measured session average, not a prediction.

Pre-existing inventory and older saved positions may have no verifiable recorded cost. Missing prices or claim amounts are counted as incomplete settlements. Confirmed subtotal excludes those outcomes, and profit/hour stays blank until the session has no incomplete claims. Finish/reconcile old positions, then reset session stats to measure subsequent fully tracked trades. The tracker does not reconstruct historical costs from current market prices.

Claims are a settlement measure: sold-but-unclaimed orders have not yet increased confirmed profit. Extra charges outside recorded acquisition costs and the Bazaar claim amount are not separately captured (for example, costs of manually obtaining ingredients). Do not interpret the HUD as a full account-wide profit statement. In-game receipt and lore formats still need verification; unrecognized formats produce incomplete accounting or trigger existing transaction safeguards.

Malformed profit files are preserved. A file or write error disables reliable statistics and displays a warning; it does not replay a trade or clear ownership records. Archive the profit file only while Minecraft is closed if manually repairing reporting. Open order files have separate [recovery rules](../COMMANDS.md#production-recovery-choose-the-matching-operation).

Existing configs, including the 80m purse config, are compatible. New fields default to `profitHudEnabled: true` and `profitHudSide: "RIGHT"`; the trading budget is not changed by this feature.
