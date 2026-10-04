# Book loop improvements — 1.3.20-BETA

The user confirmed that 1.3.19 now loops correctly and requested a stronger,
more efficient and cleaner loop. This pass improves the existing flow without
changing book selection, prices, entry profit targets or configured holding
limits.

## Shared observations and simpler selection

`InventoryScanner` previously read live Minecraft slots and copied native item
data on each query, even though the book engine already read a complete menu
snapshot per tick for slot memory and controllers. It now delegates its name,
lore, level, quantity and empty-slot queries to `MenuSnapshot`. The engine
supplies `observedMenu`, so repeated queries within one tick use the same
observation. Native item data is extracted once for these queries. The default
scanner constructor remains available; callers can supply observations without
Minecraft for JVM tests.

The existing distinctions remain: exact custom names for menu entries, native
book identity for lore searches, single-enchantment matching for route adoption,
36 main slots for lore/space counts, and broader player-owned slot matching for
route scans. Null observations yield no loaded/actionable menu. Existing
`MenuSnapshot` query coverage and new scanner tests check these boundaries.
Not every direct game read in the engine is migrated yet.

Initial and replacement sale price selection now share one method. It parses
the chosen price once, verifies policy, updates monitoring and records the same
price as confirmation intent before clicking. Entry-only profit gating and
item/quantity/price confirmation checks remain intact.

## Rejected storage moves

The physical retry helper is now named `BookActionRetry`, shared by anvil and
storage controllers. Storage retries retain the original source slot, source/
destination counts and `InventoryMemory.Move` intent. Before another shift-click,
the same source slot must still contain the matching native single-enchantment
book, both region quantities must remain at their original values, the cursor
must be clear, the context/page must match, and destination space must exist.
The production path additionally requires current stable inventory/page memory.

A source-only or destination-only packet update cannot authorize a retry.
Confirmed arrival is acknowledged before another click; when a fresh arrival
is visible at the deadline, it wins over timeout. The destination slot is bound
only after that acknowledgement, and the original move intent survives every
retry. A changed source slot is not silently substituted during an operation.

The existing three-second retry delay, 750ms unchanged-content window, 1.5-second
slowdown cooldown, three-retry cap and 30-second operation deadline apply.
`books.transfer_slowdown` and `books.transfer_action_retried` provide evidence.
Timed-out/misplaced physical transfers still use the existing reconciliation
path. Monetary order submissions do not acquire automatic retries.

## Confirmed sale settlement

A pending sale claim now waits in the current orders GUI for its matching receipt
and observed absence of both the order and book. Once all three agree, it records
the sale, removes the task and resizes remaining exposure immediately. It no
longer runs the three generic missing-order reopens after a verified claim.

Stale list entries or an inventory book keep the sale pending, even after a
receipt. Absence without the matching receipt never records a sale. The existing
10-second settlement window remains; unresolved settlement pauses with ownership
retained and no repeated claim or accidental resale. Missing orders without a
pending claim retain their existing observation retry behavior.

## Validation and limits

Gradle build and all 332 tests pass with zero failures, errors or skips. Eleven
new regressions cover supplied snapshot queries and boundaries, rejected storage
moves in both directions, partial updates, preservation of the original memory
intent and confirmed destination binding, delayed/missing receipts, stale order
packets and inventory discrepancies.

The new tests were also checked by temporarily removing two critical guards.
Removing the receipt requirement caused two sale-settlement regressions to fail;
removing original-source-slot verification caused the storage regression to fail.
Both guards were restored and the full build/suite rerun before packaging.

This is not a full Bazaar transaction replay or a live verification of 1.3.20.
The anvil fifteen-merge replay and startup/navigation regressions continue to
pass. Item-management menu variants may still use the documented fallback;
automatic restart restoration remains unimplemented. Native-read reduction is
structural evidence of less repeated work, not a measured FPS/performance claim.
