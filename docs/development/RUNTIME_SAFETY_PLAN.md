# Runtime safety repair

1. General orders: inspect only a loaded Manage Orders screen, reject pagination and duplicate orders, retain missing positions, and require an item-specific claim plus confirmed order removal before releasing capital. Cancellation requires verified intent and returned items, not an unrelated purse increase.
2. Quotes and inventory: validate the API source timestamp in both engines; reserve space conservatively for unknown stack limits and check product-specific space before buying/claiming.
3. Stalls and crash recovery: pause both engines on ambiguous transactions; add a book transaction watchdog; checkpoint outstanding book ownership before menu actions and preserve it across stop/disconnect. Cold-start book positions block purchases until manually reconciled, rather than guessing order ownership.
4. Held capital: add age and drawdown limits that pause for review. Do not automatically liquidate at a loss.
5. Regression tests for missing/duplicate/paged menus, unrelated purse/chat signals, stale/future prices, stack capacity, watchdog boundaries, and journal persistence/corruption. Build and update PR #1.

Live Hypixel UI behavior must still be checked in game. These fixes preserve uncertain positions instead of replaying financial actions.
