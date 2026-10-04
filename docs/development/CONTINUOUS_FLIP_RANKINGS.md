# Continuous rankings and volume-peer calibration (1.3.48)

Best flips refresh on the configured market-analysis interval (normally 20 seconds),
even while stopped, paused, or fully allocated, while connected and receiving fresh
market/account observations. The dashboard continues to refresh every 2 seconds.

One companion request contains two constraint sets. The normal report retains the
actual free cash, inventory capacity, enabled engines, vacant position slots and
held/retired/requirement exclusions. Only that report feeds the execution pipeline.
The separate ranking report evaluates both engines using total trading capital
(committed + liquid funds after reserve and pending purchases, capped by the capital
limit), a standard 32 input slots, and requirement exclusions. Owned and temporarily
retired products can therefore remain in the ranking. Spending, per-item limits,
tax, minimum profit, mutation exclusions and supported route checks still apply.
All candidates are calibrated before sorting and truncating to the configured report
limit; the dashboard indicates the eligible count before that limit.

A transient unreadable purse or purchase settle window preserves the last observed
ranking budget, without enabling purchases. Without any usable budget yet, analysis
waits. Disconnected/stale telemetry or expired quotes still clear predictions. With
an older companion, the mod falls back to the normal report; update both components
to enable the independent ranking report.

New verified purchases record the original uncalibrated cycle estimate, input/output
daily flows and expected profit with the local execution history. Corrections compare
actual durations/profit with their starting forecast. They can transfer to untested
routes in the same engine when both daily input flow per output and daily output flow
are within a factor of four. Closer volumes and more recent trades carry more weight;
any one peer route contributes at most ten recent outcomes. The window is 24 hours.
Three completed, uninterrupted outcomes are required for a shared prior; retired,
paused, unknown-profit and resumed trades do not become peer successes. Profit losses
remain meaningful downside evidence. Sparse faster results cannot boost throughput,
and market volume caps remain in force.

Every route uses the same adjusted coins/hour calculation. Its own evidence gradually
replaces the similar-volume prior. Timing downside and profit realization can fully
replace that prior after ten own outcomes; throughput upside needs stronger evidence
(up to thirty outcomes). Overdue open and retired positions can still lower that
route's forecast. The UI separates own completed trades from similar-volume trades;
shared estimates never pretend the new item has completed trades or earned profit.

Existing history without a starting timing/volume forecast still calibrates its own
route. It is not retroactively assigned a forecast or used as a volume-peer sample.
No history reset is needed. More observations improve estimates, but changing market
conditions do not guarantee convergence or a particular profit/hour.

Install the 1.3.48 mod and companion, restart both, and retain configs, order journals
and the external history directory. Regression coverage includes full capacity,
occupied products, temporarily unreadable purse, independent request validation,
peer similarity on both volume sides, book recipe normalization, per-item learning,
expiry, persisted forecast metadata, protocol evidence checks and browser rendering.
