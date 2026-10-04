# Book session continuation — 1.3.29

Recovery no longer pauses merely because saved book holdings/orders are present.
A complete read-only scan supplies inventory, both configured Ender Chest pages,
and own Bazaar orders. BookRecoveryPlan validates that evidence before any order
or item action, then rebuilds one cycle per saved route. It uses saved routes even
when they no longer qualify for new entry or appear in the current manual list.

- Physical input/intermediate books reduce the remaining purchase requirement and
  retain their observed native inventory/storage slots.
- Complete held cycles enter retrieval/combination/sale. Additional fully held
  cycles are promoted after the preceding task completes, without new purchases
  or a new-entry profit requirement. Partial extras await an eligible route.
- A verified input buy enters the existing claim/cancel flow. A replacement is
  considered only after the normal live cancellation and claim checks.
- A one-unit output sell offer resumes monitoring or collection if filled. Its
  output is owned by the offer, not represented as an inventory book.
- Resumption waits for fresh shared Bazaar quotes before menu transactions.

The original journal is backed up as `.resumed-<uuid>.bak`. Updated records include
trade IDs; old `{book,cost}` records remain readable. Existing profit lots retain
known costs. Recovery adds only observed units absent from those lots, with unknown
cost. Legacy reservations are not acquisition evidence and do not produce profit
or a loss-limit comparison as though their cost were known. Holding-time limits
still apply from adoption; legacy records have no trustworthy previous holding age.
Extra cycles whose cost cannot be linked also keep unknown cost.

Plans are rejected before journal mutation for duplicate/mixed orders, unreadable
price/progress/quantity, input/output level mismatches, sell offers larger than one
output, incomplete or changing inventory, and matching offhand/stacked/mixed books.
Pagination, unknown co-op creators and unsupported/unreadable menus retain the
existing scan barrier. A repeated storage page cannot be counted twice. The bot
reports the specific reason and J retries. Storage outside the two configured
pages remains outside recovery coverage. Cursor-held/anvil-only items are not
inferred from a legacy journal; move them into readable inventory before retrying.

The controller resets previous task/claim/cancellation state only after validating
and saving the rebuilt plan. Pausing a rejected scan cannot overwrite its saved
records. Runtime reservations are restored as already committed, so old purchases
are not deducted from the purse a second time. New purchases retain normal entry,
capital, inventory and confirmation checks. Mode selection still controls which
engine runs after recovery.

Validation: full Gradle build, 398 Java tests and separate Java-to-Node integration.
New scenarios cover completed inventory books, both storage pages, partial buys,
partial holdings, filled/unfilled sells, extra cycles, duplicate/mixed/unsupported
orders, co-op ownership, changed inventory, offhand, repeated storage pages, legacy
JSON, journal backups, persistent trade IDs and known/unknown profit cost bases.
Actual Minecraft timing and server menu behavior require a field run.
