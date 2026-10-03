# A tuned goofyaddons.json

Built from what the 2026-10-02/03 field bundles actually showed, not from guesses. Copy the
JSON below over your `goofyaddons.json`, then press `\` in game to reload it.

First, though: **make sure you are editing the file the game reads.** The last run loaded
`capital: 300000000, reserve: 50000000` — the built-in defaults — while the file you have says
65m and 15m. The mod had written a fresh default file in a config directory you were not
looking at, almost certainly a different launcher instance. From 1.3.8 it refuses to trade on a
default file it just wrote and tells you the full path; use that path.

## What changed and why

| Setting | Was | Now | Why |
| --- | --- | --- | --- |
| `maxTradingCapital` | 65,000,000 | 35,000,000 | Your purse is ~55m and the reserve is 15m, so only ~40m can ever be spent. A 65m limit never binds, which is how one run committed 53.5m against a 49.8m purse and then blocked at a confirmation. Keep the limit under `purse − reserve`. |
| `maxActiveBooks` | *(absent)* | 2 | New in 1.3.8. The book engine had no equivalent of `general.maxActiveItems`, so it opened an order for **every** eligible route at once — five of them in one run. Two routes at a time keeps each one funded. |
| `bookRepriceCooldownSeconds` | *(absent)* | 180 | New in 1.3.8. An outbid route used to re-place instantly, forever. Three cancel/re-place laps in 26 minutes, each at about 0.2 coins more, is churn with no fill. |
| `maxBookReprices` | *(absent)* | 3 | New in 1.3.8. After three re-placements the route is parked instead of fighting whoever outbid it. Set it to `0` to never re-place after being outbid, or raise it if you would rather keep chasing. |
| `bookOrderRecheckSeconds` | *(absent, defaulted to 180)* | 180 | Stated explicitly so it is visible rather than implied. |
| `minActionDelay` / `maxActionDelay` | 125 / 225 | 150 / 400 | 125–225ms is a tight, very regular band. The extra spread costs little and looks far less mechanical. |
| `books` | *(your list)* | unchanged | Keep whatever routes you had; the list above the snippet was cut off in what you sent, so paste yours back in. |

Everything else is left exactly as you had it. `general.*` is already well tuned — the three new
book settings above are deliberately modelled on `general.maxActiveItems`,
`general.repriceCooldownSeconds` and `general.maxReprices`, which your file already sets sensibly.

## Still worth knowing

- `tradingMode` is `BOOKS`, so none of the 25 `general.items` are being traded. That is fine if
  it is deliberate; switch to `BOTH` when you want both engines sharing the capital ledger.
- `minNetProfit: 250000` is a per-route floor on a full combine. On the cheap Ultimate Wise
  route that is a high bar; on the 11–15m routes it is modest. If the engine seems to ignore a
  book, this is the first thing to check.
- No sale has ever completed in any recorded session, so the sell half of the cycle is still
  unproven in the field. Expect to find something there.

## The file

```json
{
  "books": [ ... keep your existing routes here ... ],
  "tradingMode": "BOOKS",
  "modeKey": 77,
  "maxTradingCapital": 35000000.0,
  "purseReserve": 15000000.0,
  "maxActiveBooks": 2,
  "bookRepriceCooldownSeconds": 180,
  "maxBookReprices": 3,
  "bookOrderRecheckSeconds": 180,
  "general": {
    "items": [ ... keep your existing 25 items here ... ],
    "maxCoinsPerItem": 8000000.0,
    "maxItemsPerOrder": 64,
    "maxActiveItems": 3,
    "minProfitPerBatch": 75000.0,
    "minMarginPercentage": 3.0,
    "minWeeklyVolume": 10000.0,
    "refreshSeconds": 20,
    "orderTimeoutSeconds": 600,
    "repriceCooldownSeconds": 120,
    "maxReprices": 3,
    "maxHoldingSeconds": 21600,
    "maxDrawdownPercentage": 15.0
  },
  "startKey": 74,
  "stopKey": 75,
  "speedMode": false,
  "speedModeDelay": 100,
  "minActionDelay": 150,
  "maxActionDelay": 400,
  "bazaarTaxPercentage": 1.25,
  "minNetProfit": 250000.0,
  "maxBookHoldingSeconds": 86400,
  "profitHudEnabled": true,
  "profitHudSide": "LEFT",
  "profitHudScale": 1.0,
  "maxBookDrawdownPercentage": 15.0,
  "firstPage": "ec",
  "secondPage": "ec 2"
}
```

A route held back by one of the new limits logs `books.order_deferred` once a minute with the
reason (`max-active-books`, `reprice-cooldown`, `reprice-budget-spent`), so the next bundle will
show whether these values are right for you.
