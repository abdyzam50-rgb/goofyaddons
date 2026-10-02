# Stage 2 batch: confirmation checks, purse parsing, quote consistency

Version 1.3.3-BETA. Focused follow-up to the stop/pause batch; this does not complete every item in the broader stage-2 audit plan.

## Confirmation behavior

Both engines now require a matching side-specific confirmation title, exact supported product-name evidence, an explicit quantity and a matching unit price or total amount. Conflicting supported fields, malformed amounts and incomplete previews block submission. Product identity can come from the exact preview name, Item/Product fields, or Buying/Selling fields; arbitrary name substrings are not sufficient. Unit prices must match within floating-point epsilon; explicit total amounts allow only a 0.011-coin display-rounding difference.

General buy confirmation repeats net-profit, margin, batch minimum, item limit and shared-capital checks using the current purse. It refreshes quote observations from the newest shared snapshot before checking. General sales repeat inventory and net-margin checks. Book confirmation is tied to a recorded price-selection intent with a 30-second lifetime, repeats net-profit/capital checks and verifies inventory capacity or the single output book. Unknown formats pause and retain diagnostic evidence rather than silently submitting. Exact native book-input identification remains part of the later book-pipeline repair.

The supplied bundles did not provide a complete confirmation tooltip fixture. New parser fixtures are synthetic supported-field examples, not certification of all current Hypixel formats. If the live tooltip uses other labels or abbreviated values, the engine deliberately pauses; add that observed format with exact regression evidence before allowing it.

## Purse behavior

PurseParser accepts a single complete nonnegative numeric purse amount, with correctly grouped commas, optional decimals/colors and optional coins suffix. Additional numeric annotations, abbreviations, invalid grouping and multiple purse rows return unavailable (-1). The capital manager cannot fund a new buy from an unavailable purse. Purse: 80,000,000 (+123) can no longer become 80,000,000,123.

## Quote behavior

BazaarQuoteCache synchronizes publication and compares server timestamps. Late older responses and equal-timestamp replacements keep the existing newest snapshot. Source data is copied on publication; callers treat cached JSON as read-only. Expiry uses the server timestamp. API consumers receive the monotonic snapshot, and general polling also guards against local timestamp regression.

## Validation and remaining work

96 tests passed with zero failures/errors/skips; production build and git diff --check passed. Eight added tests cover confirmation identity/quantity/price conflicts and missing evidence, side/action formats, final profitability limits, purse formatting/ambiguity, late/equal quote publication, snapshot mutation and source-age expiry. No live orders were placed.

A04, A05, A18 and A19 now have this implementation batch; live confirmation compatibility remains unverified. Other stage-2 items (complete typed menu/order observations, cancellation detail identity, per-product schema isolation and delayed-purse acknowledgement) remain open. Placement verification, journals/reconciliation and full transaction replay remain later batches. No ownership journals were deleted or migrated.
