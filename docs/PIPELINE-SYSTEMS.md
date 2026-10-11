# Shared pipeline systems

The book, general and craft pipelines are separate state machines, but each system they
depend on has one owner. A pipeline calls that owner instead of keeping its own copy. When
you add a pipeline or change a rule, change the owner.

| System | Owner | Used by |
|---|---|---|
| Bazaar prices, order-book depth, instant-buy fee, weekly volume | `features/market/BazaarQuotes` | book, general, craft, order monitor, production runs |
| Buy/sell strategy (order or instant, each side) | `production/BazaarStrategy` | book, general, craft, market analysis |
| Which product a page or search result is | `transaction/ProductIdentity` (trading-core) | book, general, craft |
| Bazaar search result to click | `ProductIdentity.searchResult` | general, craft (`BazaarSearch`) |
| Order lore: fills, claimable units, unit prices | `generalflipper/OrderLore`, `profit/TradeReceipts` | book, general, profit |
| Claim evidence, retries, receipt grace, input restarts | `transaction/ClaimEvidence`, `ActionRetry`, `RecoveryRules` (trading-core) | book, general, craft |
| Freshness, order-menu titles, sale completion | `features/TradingSafety` | every pipeline |
| Instant buy and instant sell menus | `production/BazaarInstantBuy`, `BazaarInstantSell` | book, general, craft |
| Bazaar buy orders and sell offers for crafts | `GeneralFlipper` through `ProductionBazaarOrders` | craft |
| Capital, positions, product locks | `CapitalManager` | every pipeline |
| Start, pause, rest, reconnect, stop | `lifecycle/TradingLifecycle` (trading-core) | every pipeline |
| Navigation retries and menu settling | `menu/NavigationRetry`, `MenuSettle` | book, general, craft |
| Hypixel Bazaar snapshot | `bookflipper/helper/BazaarApi` | every pipeline |
| Craft ranking | `production/CraftFlipPlanner`, ported 1:1 to `website-src/craft-plan.mjs` and kept in step by `fixtures/craft-parity.json` | mod, website |

## Still duplicated

These still have a second copy. Each one changes what the book engine clicks, so merge it
only after section D (trading under stress) of `guides/IN_GAME_VALIDATION.md` passes on the current build.

1. **Book search results.** `BookBuy` and `BookOutbidFlow` pick a result by its custom name.
   Switch both to `ProductIdentity.searchResult(menu, book.getLevel(level), book.getRomanLevel(level))`
   once one in-game check confirms that book search results carry their enchantment data.
   The general engine already relies on that data for books.
2. **Book cleanup instant sale.** `BookRetirement` runs its own "Sell Instantly" flow.
   `BookSell` already uses `BazaarInstantSell`, and cleanup should too.
3. **Order step machines.** Book (`BookBuy`/`BookCancel`/`BookClaim`/`BookSell`) and general
   (`GeneralOrderEntry`/`GeneralCancel`/`GeneralClaim`/`GeneralSell`) both place, cancel, claim
   and sell Bazaar orders. They share every rule listed above, but each walks the menus its
   own way. The long-term step is to run books through the general operations with a book
   product adapter, as crafts already do through `ProductionBazaarOrders`.
4. **The `/bz <name>` command.** It is built in five places. The book engine also strips
   "Ultimate" from book names. It belongs with `BazaarSearch` once item 1 lands.
