# Lost and found books — 1.3.12-BETA

This implements the requested policy: a confirmed missing physical book is
written off permanently. A copy appearing later is a newly found asset, not
a reversal of the loss.

Reconciliation still verifies complete, stable inventory observations and
refreshes both configured storage pages after discovering a quantity change.
Missing books get the existing five-second settling window for delayed packets.
After that window, the engine removes the absent copies from its working model,
restores their equivalent input requirement and continues. It does not retain
a ghost holding, chase the missing copy indefinitely or pause just because a
confirmed physical quantity was lost. Loss of a level-IV book restores eight
level-I inputs, rather than one.

Unexpected matching books are assigned to a route that still needs them, or
stored as extras for a later route. Newly appearing levels are detected even
when no model entry at that level exists. Exact slot bindings preserve existing
identical copies; a visible copy is not adopted twice. Old cached storage contents
must be refreshed before becoming found holdings. Items outside supported route
levels are not silently adopted into a trade.

For a task with recorded acquisitions, a write-off consumes the lost base units
with zero recovery proceeds. A newly found book is acquired at zero additional
cost. The prior loss remains in reporting, including after the found book sells.
Unknown original acquisition costs remain incomplete rather than being invented.
Unassigned extras without a known task cost basis are removed and logged; the
existing extra-item bookkeeping does not provide a separate durable cost lot for
every such copy. Gift extras receive zero-additional-cost acquisition entries when
adopted by a later task.

Changed tasks first reconcile live Bazaar orders before submitting replacement
inputs. This policy does not declare an uncertain monetary submission or a missing
sale receipt lost. Pending inventory transfers retain ownership until observation
or timeout; a readable timeout/quantity discrepancy then returns to physical
reconciliation. Timed-out combines may do the same only when anvil inputs, output
book and cursor are clear. A known stranded anvil/cursor item still requires review.

Diagnostics: `books.book_written_off`, `books.book_found`, `trade.written_off`,
`books.transfer_reconciliation` and `books.combine_reconciliation`.

## Session continuation

Automatic continuation across client sessions remains **unimplemented**.
`BookJournal` persists route identities and committed capital as a recovery
barrier. It does not persist task states, stable trade IDs, individual slot
bindings, pending action phases or lost/found inventory history. A saved
outstanding route blocks startup for review rather than resuming the loop.
The profit ledger does persist acquired lots and write-off/settlement events.
Saving a reporting ledger is not sufficient to reconstruct a trading task.

## Validation

Twelve new regression tests cover permanent loss then return as a new copy,
fresh storage requirements, unseen levels, interchangeable extra copies,
higher-level input equivalents, duplicate loss removal, full-task overflow,
cursor/pending transfer exclusion, mixed route levels, anvil timeout eligibility,
and permanent loss-cost accounting. Full build: 285 tests, zero failures.
No live-game verification was performed with this patch.
