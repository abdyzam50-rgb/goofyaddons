# Inventory and storage slot memory — 1.3.11-BETA

Follow-up: [LOST_FOUND_POLICY.md](LOST_FOUND_POLICY.md) changes confirmed physical
quantity deficits to permanent write-offs and adopts newly found books, replacing
the quantity-mismatch pause described below. Unknown transaction ownership remains
protected.

The book engine now keeps two confirmed layouts for each observed region:
the previous distinct layout and the latest layout. Repeated frames do not
overwrite the previous layout. All contents in the 36 main inventory slots and
the two configured Ender Chest menus are recorded, including empty slots and
items unrelated to the selected book routes. Cursor contents, anvil inputs and
the output preview have separate regions. Equipment/offhand are outside this
first map, since ordinary storage menus do not expose those inventory slots.

`InventoryMemory` is the observation store; `BookLocations` binds tracked route
books to actual slots. `BookList.location` remains the logical region for existing
scheduling code and now also has `slot`. Region 0 is main inventory, 1 and 2 are
the configured storage destinations, -1 is cursor, -2 is anvil inputs and -3 is
the anvil output/preview. As corrected in 1.3.13, region -3 records preview slot 13
and action/collection slot 22 separately. A preview is recorded without being counted as owned
inventory. Native item ID, enchantments, count and display name form an item
fingerprint. Identical books remain interchangeable; slot bindings are unique,
not a fabricated identity for each physical copy.

## Observation and transfer behavior

One live snapshot is read at the beginning of the book-engine tick and shared
with combine and transfer controllers. Complete region layouts must remain
unchanged for 150ms in the same container before becoming confirmed. A changed
or partially loaded menu cannot immediately erase the stored layout. Closed
pages retain their last observation and timestamp but lose visibility. A busy
cursor prevents reconciliation and new storage clicks.

Startup captures every tracked route in the same storage visit and binds its
book entries to slots. A storage transfer records its exact source slot, item
fingerprint and intended destination region **before** the server click. The
server chooses the destination slot for a shift-click; that slot is confirmed
from arrival rather than guessed. The original move intent remains pending
through source-only updates, cursor delays, context failures and failed sends.
Matching source decrease and destination increase on stable layouts acknowledge
the transfer. The arrived slot is assigned immediately if unique, or resolved
among identical copies in the next reconciliation pass.

## Unexpected movement

With no transaction in flight, the engine compares observed quantities and slot
bindings against all tracked books, grouped by native enchantment and level.
A simultaneously observed inventory/page pair with conserved quantity can
correct an unexpected return, deposit or rearrangement. Exact existing slot
bindings are preserved before unbound interchangeable copies are assigned.

If the source page is closed, the engine opens storage to check it. It does not
infer that a book left storage merely because a matching book appeared in
inventory. Storage-to-storage discrepancies inspect both pages; cached pages
must be refreshed after the relevant layout change before a cross-page
correction is accepted. Unexplained quantity differences get a five-second
settling window after inspection, with a 30-second bound on reconciliation.
If quantities still disagree, the engine retains its task ownership and history
and pauses. Missing and extra copies do not silently remove or manufacture a
task's books.

Active claims, combine operations, storage transfers and unverified order
submissions keep ownership of their transitions. Automatic location correction
does not compete with them. Books already committed to sell orders are excluded
from physical inventory reconciliation. Diagnostics include previous/current
layouts, visibility/timestamps, pending move intent and each task's slot bindings.

## Validation and scope

15 new tests cover previous-layout retention, immutable observations, transient
empty packets, container changes, closed pages, unrelated items, cursor/anvil
contents, unexpected inventory returns, deposits to the second page, identical
copies, missing/extra copies, storage-to-storage movement and the real transfer
controller's source binding, delayed arrival and intent before a failed send.
The existing transfer/combine regression tests also pass. Full build: 273 tests,
zero failures. No live-game verification was performed with this patch.

This is runtime memory during a book-engine run. It is not restored after a
client restart or a full stop; the existing ownership journal still requires
review rather than replaying slot history. Automatic movement recovery currently
covers the two configured Ender Chest destinations and route books. Other items
are observed but not adopted into trading tasks. Backpack identities, equipment,
durable slot-history recovery and recovering an unowned anvil/cursor operation
remain follow-up work. The earlier per-task startup Bazaar-order retries are a
separate remaining issue.
