# Spendable book selection — 1.3.28

Previously processData asked TradeBudget to select against the full purse, then
reserved the selected routes against the shared capital ledger. With a 30m purse
and only 7m available under the capital limit, candidates costing 20m and 8m
consumed the provisional budget. The affordable 5m candidate was omitted, then
both selected candidates failed reservation. Coins stayed idle.

The runtime now passes CapitalManager.available(purse) and occupiedProducts to
the selection pass. Reserves, commitments and pending purchases are accounted
for once. Unaffordable or occupied candidates do not consume the provisional
budget. The final reservation and buy-confirmation checks remain authoritative.
Legacy purse-only selection remains for existing isolated regression cases.

Two new regressions cover this affordable-route omission and shared pending/
occupied allocations. The full build passes 380 Java tests and the separate
Java-to-Node integration test. Companion 1.3.27 needs no update for this fix.

This fixes one code path, not a diagnosed live session. The last supplied config
also limits books to two active routes, requires 250k net profit, and uses BOOKS
mode. Shadow calculator recommendations do not execute additional routes.
