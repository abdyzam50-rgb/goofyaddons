# Stage 1: stop/pause reliability and diagnostic evidence

Version 1.3.2-BETA. This is the first focused repair batch after FULL_AUDIT.md, not completion of the seven-stage plan.

## Behavior

Stop input is handled before failsafe or trading work in the client tick. A stop also clears simultaneous queued start/mode requests. Safety pause sets the manager latch and reason before engine cleanup, diagnostic snapshots or user feedback. Each cleanup runs even if another fails, and unavailable detailed context does not prevent the safety event.

DiagnosticQueue keeps 64 of 512 queued slots unavailable to ordinary work. Critical safety, order, trade, warning/error and lifecycle events can use that reserve and evict queued ordinary work. Both total and critical dropped-event counts persist for the session. Critical-only overflow is explicitly counted and reported through the standard logger; disk failure and a queue full of critical work still cannot guarantee complete evidence. Diagnostic errors clear after successful writes.

Diagnostic events capture wall time, monotonic time, sequence and an immutable JSON payload before entering the queue. JSONL schema 2 retains capture time in time and adds writeTime. Detailed inventory includes selected Hypixel ID/enchantment metadata; whole custom tags are excluded.

SafetyActions.tradingTick is an injectable action boundary used by the real client. Tests replay a queued stop versus eligible warp/confirmation work and injected cleanup/observation failures. It is groundwork for subsequent full-engine replay, not a simulated Hypixel transaction suite.

## Validation

88 tests passed; production build succeeded; git diff --check passed. Six new tests cover safety cleanup failure, stop priority, client tick replay, ordinary saturation/critical eviction, explicit critical-only overflow, and immutable capture-time events. Existing parser, profit, config and redaction regressions continue to pass. Live server testing remains outstanding.

## Next batch

Stage 2: typed menu/order/confirmation observations, strict purse parsing, monotonic API snapshots and final price/budget checks. Transaction/journal schemas and complete engine replay follow in their own stages. Trading settings and tracked ownership files were not deleted or migrated by this batch.
