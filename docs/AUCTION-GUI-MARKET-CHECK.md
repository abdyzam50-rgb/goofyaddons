# Auction GUI market check (0.2.24)

Auction discovery goes through `/ah`, the Auctions Browser, BIN Only, Lowest
Price, the search sign and matching visible results. Filter/sort selection must
be marked as current/selected in the observed control lore; merely mentioning
an inactive option does not prove it. The navigator preserves existing selected
filters, excludes inventory/category/control slots, accepts exact product IDs,
and reads `Buy it now:` or `BIN Price:` prices. Ordinary bids cannot select a BIN.
Navigation has bounded retries; purchase and publication controls are never
replayed as navigation. Missing or ambiguous controls stop with an inspect-menu
message rather than guessing slots.

Automatic production sales inspect matching visible listings first. Their price
is one coin under the observed lowest matching BIN. Coflnet is a price-only
cross-check through `/v1/ah/price`; neither it nor the companion supplies auction
IDs. A fresh matching quote (up to five minutes old) and agreement within 10%
are required before moving the owned item into auction creation. The existing
extreme-lowest/second-lowest outlier check remains. No market results, unsupported
quote, stale data or disagreement stops listing. Manage Auctions, Create BIN,
exact item transfer, exact entered price, duration, fee ceiling, publication and
receipt checks remain in the listing executor.

Explicit `auction sell`/`prepare` and production commands with a supplied price
retain that price, with the same GUI/API validation. Automatic `production test`
without an explicit price still obtains a Coflnet reference to budget the fee,
but the final sale price comes from the observed GUI. A changed price can exceed
the existing fee ceiling; the fee check stops publication instead of spending
more than authorized. Bazaar sales retain their existing flow.

The BIN purchase helper now opens `/ah` and browses/searches instead of issuing
`/viewauction`. It retains its existing exact intended-item UUID, price, account,
expiry and debit proof. A price-only Coflnet quote is now required. Its legacy
auction UUID is journal correlation only and is never put into a command. This
helper is not yet connected to production procurement: automatic AH ingredient
buying remains a separate integration task. Production still obtains missing
base inputs from the Bazaar or existing inventory.

Seller comparisons match product ID, stack count, enchantments and pet type/tier.
Attribute/reforge-specific valuation and pet-level comparison need richer item
contracts before those routes can be assumed comparable. The companion currently
supports ordinary product-ID price requests; unsupported pet-variant quotes stop
rather than adopting another rarity's price. Only visible matching browser
results are priced; this does not promise discovery beyond the first result page.

Tests cover filter/sort transitions, selected-option proof, search sign entry,
lowest exact-ID selection, bid/lookalike rejection, stale/mismatched/outlying
quotes and purchase completion through ordinary GUI navigation. Full Java and
calculator integration checks passed. Live Minecraft menu verification remains
outstanding. If a control is unrecognized, capture the screen with
`.a* goofyaddon auction inspect` and export diagnostics.
