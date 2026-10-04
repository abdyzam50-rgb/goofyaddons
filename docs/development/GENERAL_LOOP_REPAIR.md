# General trading loop — 1.3.21-BETA

General trading already had order-placement machinery, but its inventory exit
path reused the entry margin filter. Price movement could leave acquired stock
waiting indefinitely. Sale verification also reopened menus before asynchronous
receipts had a chance to arrive, and rejected claims had no bounded recovery.

## Execution changes

- Entry net-profit, margin, volume, freshness, capacity and budget checks still
  gate purchases, including the live price and confirmation screens.
- Already-owned stock uses the verified live sell-offer price. It may exit below
  the entry margin or at a loss, within the existing holding-age/drawdown limits.
  Live sell prices enforce those limits even if API quotes are unavailable.
- Inventory positions become eligible for work after two seconds; waiting
  orders retain their configured refresh interval.
- A completed sell offer may be claimed after a market decline or holding-age
  limit: its proceeds were already earned, so this action sells no more stock.
- Full sale settlement waits up to ten seconds for an exact item/quantity
  receipt, absent order and zero matching inventory. Completion records the sale
  once, removes the position and releases capital. Missing/conflicting evidence
  retains ownership and pauses instead of assuming completion.
- Claims use the same bounded quiet-window helper as book transfers. A retry
  requires the original title, slot, complete lore and inventory count, no
  receipt/proceeds, an empty cursor and unchanged full menu observations. There
  is a three-second inter-retry delay, 750 ms stability requirement and at most
  three retries. Slowdown messages impose a 1.5-second cooldown. Placement and
  cancellation confirmations are never replayed.
- If a partial buy finishes filling before the claim is acknowledged, the full
  observed position replaces the earlier expected partial quantity. Closed menus
  after claims are reopened for read-only verification.
- A missing purse waits up to ten seconds at buy pricing/confirmation; it is not
  mistaken for insufficient capital and never authorizes a purchase.
- Production services remain unchanged. A package-local service seam supplies
  quotes, purse, capital and accounting for complete engine simulations.

## Verification

Fourteen new engine tests run actual GeneralFlipper ticks with synthetic server
menus and acknowledgements. They cover two consecutive full flips, exactly one
submission per side, realized profit and capital release; partial buy claims;
partial sell proceeds and returned units; fill races; closed menus; claim retries
and their bound; slowdown/partial packets; delayed, missing and conflicting sale
evidence; small-loss sales with expired API data; live drawdown checks; claiming
already-completed sales after a market decline; and transient purse failures at
both purchase gates.

The full Gradle build passes: 346 tests, zero failures/errors/skips, including
existing candidate, ownership, confirmation, accounting and book regressions.
This release has not been exercised on a live server.

## Remaining limits

The allowlist and configured entry filters still control selection; nothing
bypasses a low-volume or unprofitable candidate. Unknown stack limits remain
conservative. Duplicate/paginated orders and untracked manual positions are not
automatically adopted. Interrupted partial settlements still require manual
reconciliation, and unresolved placement/cancellation outcomes pause rather than
replay an uncertain financial action. These simulations do not cover every live
menu, chat wording, packet sequence, travel event or storage/inventory drift.
Bazaar Calc market integration is not part of this change.
