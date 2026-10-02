# Trading sequence audit — 1.3.0-BETA

This review follows the observed menu failures through both trading engines,
menu scheduling, travel recovery, capital reservations, persistent ownership,
profit accounting, API monitoring and diagnostics. It is a source review with
regression tests, not a live Hypixel end-to-end certification.

## Corrections

- Partial buy fills now subtract already claimed inventory when the tooltip lacks
  an explicit claimable count. Previously any existing inventory hid new fills.
- Partial sell claims now wait for their exact credited-coins receipt before
  reopening order options to cancel the remaining offer. Buy and sell cancellation
  no longer assume that the initial click always opens a detail screen.
- Sale-claim intent is saved before the irreversible click. Interrupted settlements
  remain flagged in the position journal and require review; restarting does not
  discard receipt state and blindly replay a claim. Existing journals retain
  compatibility; older files cannot reconstruct missing historical receipts.
- Legacy profit trade/event identities are persisted before recording ledger
  events, reducing duplicate accounting across interruptions.
- Co-op orders must identify the current player's username in their creator line
  before either engine acts on a matching order. Matching quantities alone do not
  establish ownership. Missing/ambiguous creators pause for review.
- General confirmations must match the buy/sell side. A readable preview quantity
  must match the tracked position, and sale inventory must still match at confirm.
  Book confirmations also validate their transaction side.
- Book anvil input disappearance is no longer treated as successful combination.
  A new output must appear in inventory with an empty cursor before bookkeeping
  replaces the two inputs with the combined book.
- Book inventory matches require the actual ENCHANTED_BOOK ID and expected single
  enchantment, instead of a substring in an equipment tooltip. Generic book lore
  scans exclude equipment, and formatted names/lore are normalized.
- Book price and claim readers use the shared parsers rather than concatenating
  every digit in a tooltip. Both known unit-price labels are supported.
- Main-inventory capacity excludes armor/offhand slots. Extra stored books remain
  in the capital ownership set instead of being released when no task references
  their product ID.
- A safety pause during engine polling stops the rest of that trading tick.
  Scheduled travel recovery cannot automatically clear a later safety block.
- Book order monitoring rejects stale/future source timestamps before signalling
  repricing. Failed requests remain retryable; stale data cannot authorize confirms.
- Diagnostic redaction covers creator/vendor lines even in raw tooltip evidence.
  A flaky test that searched session UUIDs for a short secret substring is corrected.

## Reviewed invariants retained

One engine owns a menu transaction at a time; busy engines cannot be switched out.
Loaded orders settle before absence checks and duplicate/paginated orders halt.
Unconfirmed positions remain owned across stop, pause and disconnect. New general
positions cannot adopt pre-existing same-item inventory/orders. Sales require
matching receipts plus verified absence; missing records do not manufacture profit.
FIFO costs, partial sales, ledger deduplication and session resets preserve open
trade costs. Atomic-replacement journals preserve rejected files. Quotes must be
fresh at irreversible confirmation points; shared reservations enforce the purse
reserve and capital limit.

## Verification and remaining limits

Regression coverage includes co-op ownership, partial buy and sell claim decisions,
confirmation-side mismatch, anvil output evidence, stale monitor recovery, menu
serialization/redaction, scheduler ownership, shared budgets, journal preservation,
and FIFO profit settlement/deduplication. The production JAR builds offline.

Live testing is still required for buy cancellation/refund receipts, partial sell
cancellation, full-sale claims, book anvil/storage screens and reconnects. Unknown
receipt/title formats, malformed ownership lore, partial-fill races and interrupted
settlements deliberately pause with diagnostic evidence instead of guessing.
There is no automatic reconstruction of receipts lost before this version.
Never delete a journal for an outstanding position to bypass these checks.
