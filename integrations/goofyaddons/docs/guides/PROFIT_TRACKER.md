# Profit HUD and ledger (1.2.2-BETA)

A solid panel with rounded corners appears at the top right while in game. It shows confirmed trade profit, profit per active hour, book/general subtotals, active time, claim/incomplete counts, tracked positions, committed and spendable capital, price freshness, and trading activity. It follows GUI scaling and F1 hiding.

Client commands:

- `.a* goofyaddon profit`: print session totals.
- `.a* goofyaddon profit hud`: show/hide the panel (saved in config).
- `.a* goofyaddon profit left` or `.a* goofyaddon profit right`: move the panel to either top corner.
- `.a* goofyaddon profit reset`: reset session statistics and the timer. Open acquisition costs, receipt deduplication, historical claims, and order state remain intact.

## Accounting

Ordinary-item costs are recorded when purchased inventory is verified, using the unit price chosen in the purchase menu. Book costs are recorded when input books actually arrive, using the managed order's unit-price lore. Combining sums the cost of all required input books. Book inputs bought at different prices retain their individual cost lots.

Confirmed sale claims provide the credited coins. They are recorded only after the engine's ownership checks accept the sale/cancellation. Profit is claimed proceeds minus the consumed acquisition costs; the configured tax is not deducted a second time from claim proceeds. Refunds, deposits, rewards, changing purse balances, pending orders, and unsold inventory do not count as earnings. Negative outcomes stay negative. Partial sales consume only their share of purchase costs and repeat receipts cannot count twice.

Receipts and acquisition lots are persisted in `config/goofyaddons-profit.json`. Stats survive restarts, but the timer counts only active trading, including waiting for fills, and excludes stops, pauses, and offline time. The session continues until explicitly reset. Rate appears only after one active minute and at least one fully priced claim; it is a measured session average, not a prediction.

Pre-existing inventory and older saved positions may have no verifiable recorded cost. Missing prices or claim amounts are counted as incomplete settlements. Confirmed subtotal excludes those outcomes, and profit/hour stays blank until the session has no incomplete claims. Finish/reconcile old positions, then reset session stats to measure subsequent fully tracked trades. The tracker does not reconstruct historical costs from current market prices.

Claims are a settlement measure: sold-but-unclaimed orders have not yet increased confirmed profit. Extra charges outside recorded acquisition costs and the Bazaar claim amount are not separately captured (for example, costs of manually obtaining ingredients). Do not interpret the HUD as a full account-wide profit statement. In-game receipt and lore formats still need verification; unrecognized formats produce incomplete accounting or trigger existing transaction safeguards.

Malformed profit files are preserved. A file or write error disables reliable statistics and displays a warning; it does not replay a trade or clear ownership records. Archive the profit file only while Minecraft is closed if manually repairing reporting. Open order files have separate recovery rules in RUNTIME_SAFETY.md.

Existing configs, including the 80m purse config, are compatible. New fields default to `profitHudEnabled: true` and `profitHudSide: "RIGHT"`; the trading budget is not changed by this feature.
