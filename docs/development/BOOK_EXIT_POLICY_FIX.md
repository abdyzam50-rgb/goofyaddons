# Profit targets before entry, verified exits afterward — 1.3.17-BETA

The uploaded `diagnostics-1791042082775-23b10539.zip` captures 1.3.16 session
`ea673dfc-7c6b-452e-ad4e-f09585867594`. Startup adopts existing inventory/storage
books, claims eight Duplex I books, and completes eight acknowledged merges to
Duplex V. At event 295 it stops in SELL with “Book sale price no longer meets the
minimum net profit.” This is a sale-policy rejection, not the journal startup
barrier or an anvil timeout.

The captured Best Offer -0.1 control contains “Selling: 1x” and “Unit price:
16,265,381.3 coins”. The run's selected full Duplex input estimate was
11,185,289.6 coins. The old diagnostics omit the parsed price, configured tax,
minimum profit and estimated net at rejection. We cannot establish which old
check failed from this capture alone; the captured price format parses correctly
in a regression. At the default 1.25% tax, these values produce about 4.88m
estimated profit, so the rejection cannot be ascribed to a negative margin
without additional configuration evidence.

The user explicitly chose to apply the target before buying and sell completed
books at the current offer price. `BookPricePolicy` now applies positive net
profit and `minNetProfit` only to new input buying, both at selection and final
confirmation. Already-held book sales and replacements may proceed below the
profit target, including at a loss. Price must still be finite and positive,
quotes fresh, cost/policy inputs valid and calculated values finite. The existing
confirmation item, quantity, price, inventory and configured holding age/drawdown
checks remain. This does not add instant selling or change the general flipper.

Every book price check records `books.price_check`: parsed price, quote freshness,
expected exit, estimated input cost/net, tax, minimum target, allowed flag and
reason. Estimates are explicitly labelled; they do not manufacture an actual
cost basis for books inherited from earlier sessions. Sale failures now describe
unverified price/market data instead of claiming every rejection is insufficient
profit.

Validation: Gradle build and all 312 tests pass, with zero failures, errors or
skips. Six regressions cover the captured comma/decimal price, a completed exit
below the target, the same target rejecting new inputs, loss exits versus loss
entries, tax/quantity/boundary calculations, unreadable prices, stale quotes and
invalid policy inputs. The new build has not been run live. It does not implement
automatic cross-session task restoration or remove the journal recovery barrier.
