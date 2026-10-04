# Closed GUI observation in outbid navigation — 1.3.18-BETA

The uploaded `diagnostics-1791042920668-b8b62197.zip` captures 1.3.17 session
`27a9709f-7611-49dc-8da7-14630ef5dbae`. It confirms the Duplex V sell offer
at event 250 and a new one-unit Overload I buy order at 299. Further Overload
merges and storage transfers succeed. At event 441 the engine enters OUTBID.
It sends no further command before event 449, where the progress watchdog pauses
with “Book transaction stopped making progress; positions preserved.”

The failure snapshot has screen none, container 0 and 46 inventory-menu slots.
Reconciliation is inactive, no combine/transfer/claim is pending, and the owner
is the book engine. The attached journal contains the corresponding Overload
and Duplex route/cost entries. Neither malformed journal data nor a recovery
startup block caused this pause.

`LiveMenu.read()` returns a snapshot whenever the player exists, even when no
screen is open. The title is null in that case. The item-first navigator added
in 1.3.15 instead assumed that a closed GUI meant a null snapshot; an inventory
snapshot therefore produced no command. Its tests also represented no screen
as a null snapshot, missing the actual live contract.

The navigator now recognizes both no snapshot and a snapshot with no title as
no GUI. It sends `/bz <book>` through the existing delay when starting the route,
or `/managebazaarorders` when the explicit fallback is active. Item-level
selection, management controls, cancellation intent and verification are
unchanged. `LiveMenu` continues observing inventory while the screen is closed.

Validation: Gradle build and all 314 tests pass, with zero failures, errors or
skips. Two new regressions supply a non-null, 46-slot container-0 snapshot with
a null title. They verify initial item-GUI opening, correct subsequent level
selection, fallback reopening, and reset back to item-first navigation. The
previous navigation tests continue to pass. The new build needs live verification;
uncaptured item management controls may still use the documented fallback.

The attached journal is evidence and has not been modified or cleared. The
separate cross-session recovery barrier remains in place.
