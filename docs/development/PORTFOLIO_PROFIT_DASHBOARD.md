# Companion-only portfolio and active-trade forecasts (1.3.49)

The hourly overview previously showed one ranked route. `DashboardForecast` now
retains the last successfully validated market request in memory and produces a
separate whole-budget portfolio and running-position estimate for `/v1/dashboard`.
There are no Java/mod changes and no trading or external-data writes.

The portfolio restores conservatively inferred position limits from free slots plus
tracked positions, respects the configured engine mode and permanent exclusions,
and uses total committed plus liquid capital after reserve/pending funds, capped by
the capital limit. The standard ranking input capacity is used after hypothetical
redeployment. Five bounded quantity alternatives are re-evaluated with the adapter;
a 64-state search compares combinations without repeating a market/book family.
Every allocation stays within capital, input capacity and book/general slots. GUI
action times cap combined rates. Leftover coins are shown rather than inventing
additional opportunities or relaxing limits. The allocation is advisory and may
differ from the mod's actual next-action pipeline.

Running-position forecasts match exact input/output routes, use actual quantities
and current fresh market quotes, recorded general costs or planned book input cost,
and actual listed general sell price where present. A continuously observed open
cycle's elapsed age floors its cycle duration. Missing metadata is labeled unknown;
retiring and recovery positions are not treated as successful repeating flips.
Positive forecast profit retains its realization discount; forecast losses stay
negative. GUI concurrency also limits the combined running rate. This estimates a
whole-cycle equivalent rate, not partial-fill progress or a precise completion ETA.

Receipt-confirmed measured profit and total session profit remain separate and
retain their existing unknown-profit and active-time rules. A cached forecast
requires fresh connected account data and fresh validated market input; the cache
expires/recomputes on input changes or ten-second time buckets. It is never persisted.

Regression coverage includes cash already committed, reserve/pending deductions,
engine/inventory/capital constraints, multiple compatible routes, the greedy
single-route trap, duplicate families, GUI saturation, overdue positions, actual
sell prices/losses, unknown costs, pauses, stale input and real local HTTP. Chromium
checks the allocation table and three hourly figures at desktop/mobile sizes.
