# Empty reports when position slots are full

The diagnosed session had two book cycles and three general positions, saturating
the configured 2/3 concurrent-position limits despite 11.3m of unused capital.
Requests therefore had bookSlots=0 and generalSlots=0, which deliberately prevents
candidate evaluation. When a general slot freed, the same settings yielded 47
eligible routes. Pricing thresholds did not cause the zero-evaluated report.

PipelinePlanner now identifies saturated enabled engines, exhausted spendable
capital and zero inventory headroom before treating an empty report as absent market
opportunities. Shadow comparisons include requested per-engine slot headroom.
Dashboard entryBlockReason checks current account usability and mode-specific slots;
these also invalidate cached forecasts immediately. Unreadable purse observations
remain explicit rather than being replaced with guessed balances. No execution
limits or price filters are loosened by the reporting fix.
