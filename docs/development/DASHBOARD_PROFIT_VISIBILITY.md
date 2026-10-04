# Dashboard profit visibility — 1.3.27

The two original cards represented settled session receipts and the best eligible
new route. Neither represented profit on active positions. Zero settlements means
no settlement was recorded; it does not prove whether an in-game sale occurred.

The new active-position card prices tracked outputs from accepted collector quotes
at most 60 seconds old, subtracts 0.1 coins from the offer, applies configured tax,
and subtracts known general input cost or planned full-cycle book cost. Unknown
costs remain unknown; negative estimates remain visible. Listing fees, future
input-price changes and fill timing are excluded. Book estimates are planned
cycle margins, not realized returns or verified historical acquisition costs.
Stale account snapshots suppress estimates. This changes no trading decisions.

Forecasts explain disabled analysis, stopped trading, unavailable/expired quotes,
companion errors, exhausted capital and filtered routes. Shadow analysis requests
fresh quotes using the existing shared Bazaar fetch rather than waiting for
another trader to populate the cache. Confirmed receipt accounting is unchanged.

Validation: 378 Java tests, 23 Node tests, separate Java-to-Node integration, and
real Chromium checks at 1440 and 390 pixels using synthetic account observations.
Public market network access and an actual Minecraft session were not validated.
