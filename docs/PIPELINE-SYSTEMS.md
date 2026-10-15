# Shared pipeline systems

The book, general and craft pipelines are separate state machines, but each system they
depend on has one owner. A pipeline calls that owner instead of keeping its own copy. When
you add a pipeline or change a rule, change the owner.

| System | Owner | Used by |
|---|---|---|
| Bazaar prices, order-book depth, instant-buy fee, weekly volume | `features/market/BazaarQuotes` | book, general, craft, order monitor, production runs |
| Buy/sell strategy (order or instant, each side) | `production/BazaarStrategy` | book, general, craft, market analysis |
| Which product a page or search result is | `transaction/ProductIdentity` (trading-core) | book, general, craft |
| Bazaar search result to click | `ProductIdentity.searchResult` | book (`BookBuy`, `BookOutbidFlow`), general, craft (`BazaarSearch`) |
| Order lore: fills, claimable units, unit prices | `generalflipper/OrderLore`, `profit/TradeReceipts` | book, general, profit |
| Claim evidence, retries, receipt grace, input restarts | `transaction/ClaimEvidence`, `ActionRetry`, `RecoveryRules` (trading-core) | book, general, craft |
| Freshness, order-menu titles, sale completion | `features/TradingSafety` | every pipeline |
| Instant buy and instant sell menus | `production/BazaarInstantBuy`, `BazaarInstantSell` | book, general, craft |
| Bazaar buy orders and sell offers for crafts | `GeneralFlipper` through `ProductionBazaarOrders` | craft |
| Exact book product page/level | `bookflipper/helper/BookProductPage` | book buy and sell navigation |
| Capital, positions, product locks, shared server order cooldown | `CapitalManager` | every pipeline |
| Start, pause, rest, reconnect, stop | `lifecycle/TradingLifecycle` (trading-core) | every pipeline |
| Navigation retries and menu settling | `menu/NavigationRetry`, `MenuSettle` | book, general, craft |
| Hypixel Bazaar snapshot | `bookflipper/helper/BazaarApi` | every pipeline |
| Craft ranking | `production/CraftFlipPlanner`, ported 1:1 to `website-src/craft-plan.mjs` and kept in step by `fixtures/craft-parity.json` | mod, website |

## Shared updates and remaining duplication

The search/product identity work below is already shared. The remaining menu
implementations should only be merged after the relevant stress checks in
[the in-game checklist](guides/IN_GAME_VALIDATION.md) pass on the current build.

1. **Book search results are now shared.** Both book paths use `ProductIdentity.searchResult`; book buy/sell product navigation shares `BookProductPage`. Full/truncated names never remove confirmation or receipt checks.
2. **Book cleanup instant sale.** `BookRetirement` runs its own "Sell Instantly" flow.
   `BookSell` already uses `BazaarInstantSell`, and cleanup should too.
3. **Order step machines.** Book (`BookBuy`/`BookCancel`/`BookClaim`/`BookSell`) and general
   (`GeneralOrderEntry`/`GeneralCancel`/`GeneralClaim`/`GeneralSell`) both place, cancel, claim
   and sell Bazaar orders. They share every rule listed above, but each walks the menus its
   own way. The long-term step is to run books through the general operations with a book
   product adapter, as crafts already do through `ProductionBazaarOrders`.
4. **The `/bz <name>` command.** It is built in five places. The book engine also strips
   "Ultimate" from book names. It belongs with `BazaarSearch` without undoing exact identity checks.
