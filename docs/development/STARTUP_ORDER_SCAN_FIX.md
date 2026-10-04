# Shared startup order scan — 1.3.19-BETA

Recent uploaded 1.3.16 and 1.3.17 sessions repeatedly report missing-buy-order
rechecks during STARTUP_BAZAAR_CHECK. In the 1.3.17 session
`27a9709f-7611-49dc-8da7-14630ef5dbae`, events 96, 104, 112 retry Duplex and
122, 130, 138 retry Overload. Each route gets three new orders-GUI visits.
Physical holdings mark a route exposed; the old startup logic treated that
exposure as a reason to expect a live buy order. Missing a live buy order is
normal when the route's books are already in inventory/storage or its order
has finished or been cancelled.

`BookStartupOrders` now observes the complete current orders menu for 750ms of
unchanged contents in the same container. The snapshot must have the accepted
order-list title, its loaded marker and a clear cursor. Changes, different menus
and busy cursors restart settling. Duplicates and pagination do not authorize
missing-order decisions. Existing engine creator/ownership checks remain ahead
of actions.

From that one settled observation, every task in BAZAAR_ORDER_CHECK without a
matching BUY entry is accounted for together. A fully supplied route proceeds
to anvil/retrieval or sale; a partial pair schedules combination/storage before
buying the remainder; a single inventory input schedules storage; a route with
no inputs schedules normal buying. No holdings or accounting entries are
changed by the missing-order decision. Existing BUY entries stay in the startup
check and continue through the existing verified claim and cancellation flow.
After those transactions change the menu, a new stable observation accounts for
all remaining absent orders, without reopening solely to prove absence again.

This does not collapse real order transactions into a read-only scan. A live
order may still open options/claim/cancellation screens and return to an updated
list. Unreadable creator/amount fields retain their bounded retries. The earlier
journal startup barrier is unchanged. `books.startup_order_accounted` records
each missing BUY route, remaining requirement, next state and shared container.

Validation: Gradle build and all 321 tests pass, with zero failures, errors or
skips. Seven new regressions cover two physically held routes in one visit,
retaining visible orders while batching absent ones, late-loaded entries and
container changes, duplicate/paginated lists, wrong/closed/unloaded/busy screens,
scheduling partial holdings, preserving normal waiting tasks and reset behavior.
The new build still needs a live startup run. Stable client contents cannot
prove that an arbitrarily delayed server packet will never arrive; the scan
uses the existing settled-menu observation contract without the redundant
per-route reopen loop.
