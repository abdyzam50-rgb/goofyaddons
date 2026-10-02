# Staged GoofyAddons repair plan

Baseline: 1.3.1-BETA audit dated 2026-10-02. Findings: FULL_AUDIT.md; file coverage: AUDIT_COVERAGE.md. Do not bundle the entire backlog into one release. Each stage gets its own focused commit/PR, regression fixtures and validation result. Preserve ownership files and diagnostics throughout.

## 1. Make stop and evidence trustworthy

Address A01, A02, A26–A28; investigate R12–R13. Set the safety latch before diagnostics; handle stop before actions. Capture event-time identity, protect critical events, add native item metadata and accurate cumulative drop counters. Build a minimal injectable observation/action seam for A32 in the same stage, then extend it per subsystem.

Gate: injected logging/snapshot failures cannot allow another action; a queued stop prevents a confirmation; queue saturation preserves critical failures/receipts and reports losses honestly. No trading-path changes outside the seam and stop/pause ordering.

## 2. Validate shared transaction observations and money

Address A04–A07, A13–A14, A18–A20; investigate R01, R03–R04, R06, R15. Model supported screen kinds, loaded controls, item identity, quantity, price, creator and container context explicitly. Share parsers without making unknown formats permissive. Publish quotes monotonically. Treat ambiguous purse data as unavailable. Recheck actual confirmation against current limits.

Gate: wrong-side, wrong-item, wrong-count, wrong-price, unreadable, stale and wrong-container fixtures produce zero unsafe clicks. Out-of-order API completions never regress the cache. Both engines obey one purse/reserve under delayed scoreboard updates. Fixtures include the uploaded diagnostic examples.

## 3. Durable ownership and reconciliation

Address A11–A12, A16–A17, A21; establish the event/outbox contract needed by A29. Version journals and validate stage invariants. Persist intent before external actions and durable outcome afterward. Load every engine's exposure before eligibility decisions. Add a supported inspection/reconciliation flow. Unknown outcomes remain blocked; never infer a successful trade solely from absence, time, or deleting a journal.

Gate: crash simulation before/after each disk write and server action reconstructs exposure without duplicate submission/claim. Corrupt state is preserved, a previous recoverable snapshot remains available, and unresolved positions remain reserved. Legacy migration fails closed when evidence is insufficient.

## 4. Repair the general flipper end to end

Address A15 plus general portions of A03–A05/A13–A17; investigate R02–R03. Drive placement, partial buy claims, cancellation/refunds, partial sell claims, repricing and full settlement through the observation model. Bind receipts to persisted intents. Refresh quantities when fills race with cancellation using reconciled evidence rather than relaxed checks.

Gate: replay zero/partial/full fills at every transition, repeated/late/missing receipts, manual intervention, denied commands and lost menus. Every outcome is either an exactly accounted position or an explicit retained reconciliation block. Profit reporting cannot cause a repeat server trade.

## 5. Repair the book pipeline end to end

Address A03, A06–A11; investigate R04–R09. Verify placement instead of marking success on click. Track book input quantities/native identity, claim deltas, exact storage pages and source/destination transfers. Verify anvil input pair, output and cost/permissions. Exclude unsupported instant routes until separately implemented and tested. Define scheduling priorities and excess-book capital treatment.

Gate: replay buy → partial claim → cancel → storage across both pages → retrieve → combine → sell → claim. Include wrong pages, full storage, multi-enchantment lookalikes, insufficient anvil prerequisites, missing inputs/output, cursor-held output, rejected placement and crash at every boundary. No book is marked moved or combined from disappearance alone.

## 6. Combined-mode and travel integration

Address A25, remaining A12; investigate R01/R10/R14. Replay both engines with shared capital, exclusive menu ownership and mode switches during each busy state. Add location/readiness evidence to travel recovery and explicit timeout reasons. Define behavior for disconnect/rejoin, non-SkyBlock worlds, remote-access denial and user intervention.

Gate: no overlapping menu actions; retained inactive-engine exposure stays reserved; safe stops/reloads do not replay intents; denied/delayed travel remains paused until a verified ready state.

## 7. Profit, settings, HUD and reproducible distribution

Address A22–A24, A29–A31, A33; investigate R11–R13. Retry confirmed reporting events from the durable outbox, archive old history and cache summaries. Flush final active time. Block invalid first-load config, align effective settings/key bindings and acknowledge persistence failures. Fit HUD content to available dimensions and always restore render state. Separate PR tests from releases/notifications, pin toolchain and verify wrapper checksum.

Gate: confirmed profit survives restart and write failures without double-counting; unknown cost/proceeds stay incomplete; large ledgers do not stall ticks. All supported GUI dimensions render readable task/status without row overlap. Docs/experimental branch pushes cannot publish builds. Clean Windows build instructions match supported pinned dependencies.

## Completion rules for every stage

1. Turn each confirmed trigger into a failing regression or deterministic replay before changing behavior.
2. Make the smallest coherent fix; retain fail-closed checks and ownership evidence.
3. Run focused tests, then the full suite and build once. Record which server-dependent cases remain unverified.
4. Update finding IDs to fixed/verified/investigation with commit and test references; do not silently remove them.
5. Validate the built jar in controlled use with a small exposed amount before raising trading limits. Profit targets are measurements, not guaranteed outcomes.

The audit document stage made no runtime fixes. Stage 1 now has a separate implementation batch documented in STAGE_1_REPAIR.md; whole-engine replay remains an explicit follow-up. Investigation entries become fixes only after their actual failure and intended behavior are established.

## Progress checkpoint

The focused stage-2 confirmation/purse/quote batch is implemented in 1.3.3-BETA; see STAGE_2_REPAIR.md. This is not closure of every stage-2 item. Complete menu/order observation, cancellation identity, product-schema isolation and delayed-purse acknowledgement remain open.
