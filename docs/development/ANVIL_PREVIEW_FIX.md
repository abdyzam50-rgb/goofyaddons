# Anvil preview timeout repair — 1.3.13-BETA

Follow-up: [ANVIL_COLLECTION_FIX.md](ANVIL_COLLECTION_FIX.md) corrects the
post-submission result check retained in this build.

The uploaded `diagnostics-1791038788685-13066ba6.zip` captures session
`94b8c7e5-d268-493b-811f-1f1d3c199bdb`, running 1.3.11-BETA.
At event 923 (2026-10-03 14:44:33 UTC), `books.transaction_blocked` reports
“Anvil operation timed out; inputs and ownership retained.” The controller is
in `SECOND_INPUT`, with two original Overload I books now in anvil slots 29
and 33, and none in main inventory.

The same snapshot contains native Overload II in slot 13, with preview lore
“This is the item you will get.” and “Click the ANVIL BELOW to combine.”
Slot 22 instead contains a button named “Combine Items”, with “0 Exp Levels”
and “Click to combine!” in its lore. It has no native book identity.
The previous controller incorrectly required an output book in slot 22 before
submission. The original tests repeated that mistaken layout. This bug also
exists in 1.3.12. No combine submission occurred; after the safety pause closed
the menu, the two original Overload I books returned to inventory slots 0 and 1.

The controller now requires the correct native preview in slot 13 plus the
named, ready action button in slot 22 before clicking 22 once. Fixed quantity
baselines, cursor checks and input/output acknowledgement remain in place.
A stale preview with unconsumed inputs cannot trigger another submission.
Timeout reconciliation cannot discard ownership while a native book remains
in either output slot. Slot memory records 13 and 22 in the separate preview/
action region, without counting the preview as an owned inventory book.

The uploaded `goofyaddons-book-orders.json` contains Overload and Duplex route/
cost entries. It is the existing conservative capital journal, not task or slot
restoration data, and is not the cause of this anvil timeout. It is not modified
or cleared by this repair. Automatic cross-session task restoration remains
unimplemented.

Validation: the Gradle build passes with 288 tests, zero failures, errors or
skips. Regression fixtures use the captured preview/button layout and lore;
they verify once-only submission and direct inventory delivery, reject an
incorrect preview or unavailable button, record preview/input memory separately,
and replay all 15 merges from sixteen level-I books to one level-V book.

The new build has not been run live. This capture stops before submission, so
it does not establish the server's post-submission collection layout. The
existing guarded slot-22 collection path is retained and is exercised in the
simulation; direct delivery is also covered. A subsequent live diagnostic
capture is needed to verify that stage and the remaining Bazaar loop.
