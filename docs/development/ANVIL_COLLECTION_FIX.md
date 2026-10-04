# Anvil collection repair — 1.3.14-BETA

Follow-up: [ANVIL_RETRY_FIX.md](ANVIL_RETRY_FIX.md) records live claim-button
lore and replaces unconditional once-only waiting with validated, bounded retries.

The screenshot supplied after the 1.3.13 update shows a result book in slot 13,
a sign in slot 22, and empty input slots 29 and 33. The prior repair corrected
submission but retained the mistaken requirement for a native result book in
slot 22 after submission. That prevented the collection action.

Collection now verifies the expected native single-enchantment book in slot 13,
empty input slots, and a total input count exactly two below the fixed baseline.
If inventory has not received the output, it clicks the claim control in slot 22
once, preserving the original engine's claim action. Ownership changes only
when the expected output arrives in inventory with a clear cursor. Stale display
packets never repeat the claim; returned inputs cannot authorize collection.

The screenshot establishes the visible layout, but supplies no tooltip or native
item metadata. Tests use the corresponding native book fixture and a separate
sign control. The capture does not independently prove the claim control's
behavior; successful collection still needs live verification.

Validation: Gradle build passes with 290 tests, no failures, errors or skips.
New regressions exercise the displayed result above the sign, once-only claim,
confirmed inventory arrival, and returned inputs with a stale output display.
The full sixteen-input, fifteen-merge replay now uses slot 13 for both preview
and post-combine result instead of simulating a result book in slot 22.
